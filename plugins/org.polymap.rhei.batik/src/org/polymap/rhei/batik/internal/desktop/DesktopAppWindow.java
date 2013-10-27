/*
 * polymap.org
 * Copyright 2013, Falko Bräutigam. All rights reserved.
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
package org.polymap.rhei.batik.internal.desktop;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.rwt.lifecycle.UICallBack;
import org.eclipse.rwt.lifecycle.WidgetUtil;

import org.eclipse.jface.window.ApplicationWindow;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import org.polymap.core.runtime.event.EventFilter;
import org.polymap.core.runtime.event.EventHandler;
import org.polymap.core.ui.FormDataFactory;

import org.polymap.rhei.batik.BatikPlugin;
import org.polymap.rhei.batik.PanelChangeEvent;
import org.polymap.rhei.batik.PanelChangeEvent.TYPE;
import org.polymap.rhei.batik.internal.desktop.DesktopAppManager.DesktopPanelSite;

/**
 * The main application window for the desktop.
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
abstract class DesktopAppWindow
        extends ApplicationWindow {

    private static Log log = LogFactory.getLog( DesktopAppWindow.class );

    private DesktopAppManager       appManager;


    public DesktopAppWindow( DesktopAppManager appManager ) {
        super( null );
        this.appManager = appManager;
        addStatusLine();
        //addCoolBar( SWT.HORIZONTAL );
    }


    protected abstract Composite fillNavigationArea( Composite parent );

    protected abstract Composite fillPanelArea( Composite parent );


    @Override
    protected Control createContents( Composite parent ) {
        Composite contents = (Composite)super.createContents( parent );
        contents.setLayout( new FormLayout() );

        // statusLine
        setStatus( "Status..." );
        Composite statusLine = (Composite)getStatusLineManager().getControl();
        statusLine.setData( WidgetUtil.CUSTOM_VARIANT, "atlas-status"  );
        statusLine.setBackground( null );
        for (Control child : statusLine.getChildren()) {
            child.setData( WidgetUtil.CUSTOM_VARIANT, "atlas-status"  );
            child.setBackground( null );
        }

        // navi
        Composite navi = fillNavigationArea( contents );
        navi.setLayoutData( FormDataFactory.filled().bottom( -1 ).height( 30 ).create() );
        navi.setData( WidgetUtil.CUSTOM_VARIANT, "atlas-navi"  );

        Composite panels = fillPanelArea( contents );
        panels.setLayoutData( FormDataFactory.filled().top( navi, 10 ).create() );
        panels.setData( WidgetUtil.CUSTOM_VARIANT, "atlas-panels"  );

        appManager.getContext().addEventHandler( this, new EventFilter<PanelChangeEvent>() {
            public boolean apply( PanelChangeEvent input ) {
                return input.getType() == TYPE.ACTIVATED;
            }
        });
        return contents;
    }


    @EventHandler(display=true)
    protected void panelChanged( PanelChangeEvent ev ) {
        DesktopPanelSite panelSite = (DesktopPanelSite)ev.getSource().getSite();
        getShell().setText( "Mosaic - " + panelSite.getTitle() );
        getShell().layout();
    }


    public void setStatus( IStatus status ) {
        assert status != null;
        switch (status.getSeverity()) {
            case IStatus.OK: {
                getStatusLineManager().setErrorMessage( null ); 
                getStatusLineManager().setMessage( null );
                if (status != Status.OK_STATUS && status.getMessage() != null) {
                    getStatusLineManager().setMessage( BatikPlugin.instance().imageForName( "resources/icons/ok-status.gif" ), 
                            status.getMessage() );
                }
                break;
            }
            case IStatus.ERROR: {
                getStatusLineManager().setErrorMessage( 
                        BatikPlugin.instance().imageForName( "resources/icons/errorstate.gif" ), status.getMessage() );
                break;
            }
            case IStatus.WARNING: {
                getStatusLineManager().setMessage( 
                        BatikPlugin.instance().imageForName( "resources/icons/warningstate.gif" ), status.getMessage() );
                break;
            }
            default: {
                getStatusLineManager().setMessage( status.getMessage() );
            }
        }
    }


    @Override
    protected void configureShell( final Shell shell ) {
        super.configureShell( shell );
        shell.setText( "Mosaic" );
        shell.setTouchEnabled( true );

//        shell.setMaximized( true );

        final Listener resizeListener = new Listener() {
            private Rectangle   prev;
            public void handleEvent( Event ev ) {
                Rectangle bounds = Display.getCurrent().getBounds();
                if (!bounds.equals( prev )) {
                    log.info( "layout..." );
                    shell.setBounds( 0, 60, bounds.width, bounds.height - 60 );
                    shell.layout( true );
                    prev = bounds;
                }
            }
        };
        resizeListener.handleEvent( null );
//        Display.getCurrent().addListener( SWT.Resize, resizeListener );

//        new Job( "Display bounds refresher" ) {
//            protected IStatus run( IProgressMonitor monitor ) {
//                shell.getDisplay().asyncExec( new Runnable() {
//                    public void run() {
//                        resizeListener.handleEvent( null );
//                    }
//                });
//                this.schedule( 1000 );
//                return Status.OK_STATUS;
//            }
//        }.schedule( 1000 );
        
//        delayedRefresh( shell );
    }

    private int refreshCount = 1;
    
    public void delayedRefresh( final Shell shell ) {
        final Shell s = shell != null ? shell : getShell();
        UICallBack.activate( "layout" );
        Job job = new Job( "Layout" ) {
            protected IStatus run( IProgressMonitor monitor ) {
                s.getDisplay().asyncExec( new Runnable() {
                    public void run() {
                        log.info( "layout..." );
                        Rectangle bounds = Display.getCurrent().getBounds();
                        int random = refreshCount++ % 3;
                        s.setBounds( 0, 60, bounds.width, bounds.height - 60 - random );
                    }
                });
                return Status.OK_STATUS;
            }
        };
        //job.setUser( true );
        job.schedule( 1000 );
    }

    
    @Override
    protected int getShellStyle() {
        // no border, no title
        return SWT.NO_TRIM;
    }


    @Override
    protected boolean showTopSeperator() {
        return false;
    }

}
