/* 
 * polymap.org
 * Copyright (C) 2015, Falko Bräutigam. All rights reserved.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3.0 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package org.polymap.rhei.batik.engine;

import static org.polymap.rhei.batik.IPanelSite.PanelStatus.CREATED;
import static org.polymap.rhei.batik.IPanelSite.PanelStatus.INITIALIZED;
import static org.polymap.rhei.batik.IPanelSite.PanelStatus.VISIBLE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.jface.action.IAction;

import org.eclipse.ui.IMemento;
import org.eclipse.ui.XMLMemento;

import org.eclipse.core.runtime.IPath;

import org.polymap.core.runtime.Lazy;
import org.polymap.core.runtime.PlainLazyInit;
import org.polymap.core.runtime.UIThreadExecutor;
import org.polymap.core.runtime.config.Concern;
import org.polymap.core.runtime.config.Config2;
import org.polymap.core.ui.StatusDispatcher;

import org.polymap.rhei.batik.BatikApplication;
import org.polymap.rhei.batik.BatikPlugin;
import org.polymap.rhei.batik.FireEventType;
import org.polymap.rhei.batik.IAppContext;
import org.polymap.rhei.batik.IPanel;
import org.polymap.rhei.batik.IPanelFilter;
import org.polymap.rhei.batik.IPanelSite.PanelStatus;
import org.polymap.rhei.batik.Memento;
import org.polymap.rhei.batik.PanelChangeEvent.EventType;
import org.polymap.rhei.batik.PanelIdentifier;
import org.polymap.rhei.batik.PanelPath;
import org.polymap.rhei.batik.PanelSite;
import org.polymap.rhei.batik.app.IAppDesign;
import org.polymap.rhei.batik.app.IAppManager;
import org.polymap.rhei.batik.engine.BatikFactory.PanelExtension;
import org.polymap.rhei.batik.toolkit.IPanelToolkit;
import org.polymap.rhei.batik.toolkit.LayoutSupplier;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class DefaultAppManager
        implements IAppManager {

    private static final Log log = LogFactory.getLog( DefaultAppManager.class );

    protected DefaultAppContext         context = new AppContext();

    protected IAppDesign                design;

    /** Cache for panel sites */
    protected Map<PanelPath,PanelSite>  panelSites = new HashMap();
    
    protected PanelPath                 top = PanelPath.ROOT;
    
    /** The panel hierarchy. */
    private Map<PanelPath,IPanel>       panels = new HashMap();

    private XMLMemento                  memento;

    private File                        mementoFile;


    @Override
    public void init() {
        design = BatikApplication.instance().getAppDesign();

        IPath path = BatikPlugin.instance().getStateLocation();
        // FIXME state per user
        // FIXME multiple session for the same user
        mementoFile = new File( path.toFile(), "panels-memento.xml" );
        log.info( "State file: " +  mementoFile.getAbsolutePath() );

        if (mementoFile.exists()) {
            try (InputStream in = new BufferedInputStream( new FileInputStream( mementoFile ) )) {
                memento = XMLMemento.createReadRoot( new InputStreamReader( in, "utf-8" ) );
            }
            catch (Exception e) {
                throw new RuntimeException( e );
            }
        }
        else {
            memento = XMLMemento.createWriteRoot( "panel-memento" );
        }

        // open root panel / after main window is created
        UIThreadExecutor.async( 
                () -> context.openPanel( PanelPath.ROOT, new PanelIdentifier( "start" ) ),
                UIThreadExecutor.runtimeException() );
    }


    @Override
    public void close() {
        disposePanels( PanelPath.ROOT );
    }    

    
//    protected <T> void fireEvent( IPanel panel, EventType eventType, T newValue, T previousValue  ) {
//        // XXX avoid race conditions; EventManager does not seem to always handle display events properly
//        EventManager.instance().syncPublish( new PanelChangeEvent( panel, eventType, newValue, previousValue ) );        
//    }

    
    @Override
    public IPanel getPanel( PanelPath path ) {
        return panels.get( path );
    }


    @Override
    public List<IPanel> findPanels( Predicate<IPanel> filter ) {
        // make a copy so that contents is stable while iterating (remove)
        return panels.values().stream().filter( filter ).collect( Collectors.toList() );
    }

    
    public PanelPath topPanel() {
        return top;
    }

    
    @Override
    public IAppContext getContext() {
        return context;
    }

    
    protected PanelSite getOrCreatePanelSite( PanelPath path, int stackPriority ) {
        return panelSites.computeIfAbsent( path, key -> new PanelSiteImpl( path, stackPriority ) );
    }
    
    
    protected IPanel createPanel( PanelExtension ep, PanelPath parentPath ) {
        try {
            IPanel panel = ep.createPanel();
            new PanelContextInjector( panel, context ).run();
            PanelPath path = parentPath.append( panel.id() );
            PanelSite panelSite = getOrCreatePanelSite( path, ep.getStackPriority() );
            panel.setSite( panelSite, context );
            if (panel.site() == null) {
                throw new IllegalStateException( "Panel.getSite() == null after setSite()!");
            }
            return panel; //panel.wantsToBeShown() ? panel : null;
        }
        catch (Exception e) {
            StatusDispatcher.handleError( "Error while initializing panel", e );
            return null;
        }         
    }
    
    
    protected IPanel createPanel( PanelPath parentPath, PanelIdentifier panelId ) {
        IPanelFilter filters = BatikFactory.instance().allPanelFilters();
        for (PanelExtension ext : BatikFactory.instance().allPanelExtensions()) {
            IPanel panel = createPanel( ext, parentPath );
            if (panel.id().equals( panelId )
                    && filters.apply( panel )) {
                log.debug( "CREATE panel: " + panel.site().path() );
                panels.put( panel.site().path(), panel );
                ((PanelSiteImpl)panel.site()).panelStatus.set( PanelStatus.CREATED );
                return panel;
            }
        }
        throw new IllegalStateException( "No such panel: " + panelId );
    }


    protected Iterable<IPanel> wantToBeShown( PanelPath parentPath ) {
        List<IPanel> result = new ArrayList();
        IPanelFilter filters = BatikFactory.instance().allPanelFilters();
        for (PanelExtension ext : BatikFactory.instance().allPanelExtensions()) {
            IPanel panel = createPanel( ext, parentPath );
            if (filters.apply( panel )
                   && panel.beforeInit()) {
                result.add( panel );
            }
        }
        return result;
    }

    
    /**
     * Provides the default logic for opening a panel.
     */
    protected <P extends IPanel> Optional<P> openPanel( PanelPath parentPath, PanelIdentifier panelId ) {
        assert parentPath != null : "parentPath must not be null";
        assert panelId != null : "panelId must not be null";
        if (!disposePanels( parentPath )) {
            return Optional.empty();
        }
        else {
            P panel = (P)createPanel( parentPath, panelId );
            raisePanelStatus( panel, PanelStatus.VISIBLE );
            top = panel.site().path();
            return Optional.of( panel );
        }
    }


    /**
     * 
     *
     * @param parentPath
     * @return False if one of the panels vetoed via {@link IPanel#beforeDispose()}
     *         or a {@link RuntimeException} was thrown by beforeClose().
     */
    protected boolean disposePanels( PanelPath parentPath ) {
        int pathSize = parentPath.size();
        // beforeDispose()
        List<IPanel> disposable = new ArrayList();  // stable while remove
        for (IPanel panel : panels.values()) {
            if (panel.site().path().size() > pathSize) {
                try {
                    if (!panel.beforeDispose()) {
                        return false;
                    }
                    disposable.add( panel );
                }
                catch (Exception e) {
                    StatusDispatcher.handleError( "Error while initializing panel", e );
                    return false;
                }         
            }
        }
        // disposePanel()
        for (IPanel panel : disposable) {
            if (panel.site().path().size() > pathSize) {
                disposePanel( panel );
            }
        }
        return true;
    }
    
    
    protected void disposePanel( IPanel panel ) {
        log.debug( "DISPOSE panel: " + panel.site().path() );
        try {
            panel.dispose();
            saveMemento();
        }
        catch (Exception e) {
            log.warn( "", e );
        }
        PanelPath panelPath = panel.site().path();
        if (panels.remove( panelPath ) == null) {
            throw new IllegalStateException( "No Panel exists at: " + panelPath );
        }
        
        // re-use the site for next panel; keep size settings made in beforeInit();
        // settings things in beforeInit() is not allowed, however I'm not sure if
        // site should be re-used anyway (maybe later holding any context), as it is
        // done between #wantsToBeShown() and #createPanel()
        // #5: Preferred panel width (http://github.com/Polymap4/polymap4-rhei/issues/issue/5)
        panelSites.remove( panelPath );
        
        // throws event to be handled by DefaultAppDesign
        ((PanelSiteImpl)panel.site()).panelStatus.set( null );
    }
        

    /**
     * Disposes all panels in the given panelPath by calling
     * {@link #disposePanels(PanelPath)}. Makes parentPath the top panel and raises
     * its status to {@link PanelStatus#FOCUSED}.
     *
     * @param panelPath
     * @return False if one of the panels vetoed via {@link IPanel#beforeDispose()}
     *         or a {@link RuntimeException} was thrown by beforeClose().
     */
    protected boolean closePanel( PanelPath panelPath ) {
        // close all children and siblings
        PanelPath parentPath = panelPath.removeLast( 1 );
        if (disposePanels( parentPath )) {
            // set top 
            top = parentPath;

            // raise top's panel status
            raisePanelStatus( getPanel( top ), PanelStatus.VISIBLE );
            return true;
        }
        return false;
    }

    
    protected void raisePanelStatus( IPanel panel, PanelStatus targetStatus ) {
        // initialize
        if (panel.site().panelStatus() == CREATED && targetStatus.ge( INITIALIZED )) {
            panel.init();
            ((PanelSiteImpl)panel.site()).panelStatus.set( INITIALIZED );
        }
        // make visible
        if (panel.site().panelStatus() == INITIALIZED && targetStatus.ge( VISIBLE )) {
            ((PanelSiteImpl)panel.site()).panelStatus.set( VISIBLE );
        }
    }
    
    
    protected void updatePanelStatus( IPanel panel, PanelStatus panelStatus ) {
        ((PanelSiteImpl)panel.site()).panelStatus.set( panelStatus );
    }


    protected void saveMemento() {
        try (
            OutputStream out = new BufferedOutputStream( new FileOutputStream( mementoFile ) )
        ){
            memento.save( new OutputStreamWriter( out, "utf-8" ) );
        }
        catch (IOException e) {
            log.warn( "", e );
        }
    }

    
    /**
     * Facade of the global {@link DefaultAppManager#context}.
     */
    protected class AppContext
            extends DefaultAppContext {
        
        @Override
        public void setUserName( String username ) {
            log.warn( "setUserName(): not implemented yet." );
        }

        @Override
        public void addPreferencesAction( IAction action ) {
            log.warn( "addPreferencesAction(): not implemented yet." );
        }

        @Override
        public PanelPath topPanel() {
            return DefaultAppManager.this.topPanel();
        }

        @Override
        public <P extends IPanel> Optional<P> openPanel( PanelPath panelPath, PanelIdentifier panelId ) {
            return DefaultAppManager.this.openPanel( panelPath, panelId );
        }

        @Override
        public boolean closePanel( final PanelPath panelPath ) {
            return DefaultAppManager.this.closePanel( panelPath );
        }
        
        @Override
        public IPanel getPanel( PanelPath path ) {
            return DefaultAppManager.this.getPanel( path );
        }

        @Override
        public List<IPanel> findPanels( Predicate<IPanel> filter ) {
            return DefaultAppManager.this.findPanels( filter );
        }
        
        @Override
        public Iterable<IPanel> wantToBeShown( PanelPath parent ) {
            return DefaultAppManager.this.wantToBeShown( parent );
        }
    }
    
    
    /**
     * 
     */
    protected class PanelSiteImpl
            extends PanelSite {
        
        private PanelPath                       path;
        
        /**
         * This must be lazily initialized as MdAppDesign has no page for the panel yet.
         */
        private Lazy<IPanelToolkit>             toolkit = new PlainLazyInit( () -> design.createToolkit( path ) );
        
        @Concern( FireEvent.class )
        @FireEventType( EventType.LIFECYCLE )
        protected Config2<PanelSite,PanelStatus> panelStatus;
        

        protected PanelSiteImpl( PanelPath path, Integer stackPriority ) {
            assert path != null;
            this.path = path;
            this.stackPriority.set( stackPriority );
        }
        
        @Override
        protected void finalize() throws Throwable {
            if (toolkit.isInitialized()) {
                toolkit.get().close();
            }
        }
        
        @Override
        public PanelPath path() {
            return path;
        }

        @Override
        public PanelStatus panelStatus() {
            return panelStatus.get();
        }
        
        @Override
        public IPanelToolkit toolkit() {
            return toolkit.get();
        }

        @Override
        public void layout( boolean changed ) {
            design.delayedRefresh();
        }

        @Override
        public Memento memento() {
            // "/" is not allowed as key
            String key = path.stream()
                    .map( id -> id.id() )
                    .reduce( "", (lhs, rhs) -> lhs + "_" + rhs );
            IMemento result = memento.getChild( key );
            if (result == null) {
                result = memento.createChild( key );
            }
            return new Memento( result ) {
                @Override
                public void save() {
                    saveMemento();
                }
            };
        }

        @Override
        public LayoutSupplier layoutPreferences() {
            return design.getPanelLayoutPreferences();
        }
    }

}
