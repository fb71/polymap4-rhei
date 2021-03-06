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
package org.polymap.rhei.batik.toolkit;

import static org.apache.commons.lang3.ArrayUtils.contains;

import java.util.ArrayList;
import java.util.concurrent.Callable;

import org.pegdown.FastEncoder;
import org.pegdown.LinkRenderer;
import org.pegdown.LinkRenderer.Rendering;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.AutoLinkNode;
import org.pegdown.ast.ExpImageNode;
import org.pegdown.ast.ExpLinkNode;
import org.pegdown.ast.RefLinkNode;
import org.pegdown.ast.WikiLinkNode;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.internal.widgets.MarkupValidator;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.ui.forms.FormColors;
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.Section;

import org.eclipse.rap.rwt.RWT;

import org.polymap.core.runtime.Lazy;
import org.polymap.core.runtime.LockedLazyInit;
import org.polymap.core.runtime.Polymap;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.batik.PanelPath;
import org.polymap.rhei.batik.engine.PageStack;

/**
 *
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
@SuppressWarnings("restriction")
public class DefaultToolkit
        implements IPanelToolkit {

    public static final String  CSS_PREFIX = "batik-panel";
    public static final String  CSS_SECTION = CSS_PREFIX + "-section";
    public static final String  CSS_SECTION_EXPANDABLE = CSS_SECTION + "-expandable";
    public static final String  CSS_SECTION_TITLE = CSS_PREFIX + "-section-title";
    public static final String  CSS_SECTION_TITLE_EXPANDABLE = CSS_PREFIX + "-section-title-expandable";
    public static final String  CSS_SECTION_SEPARATOR = CSS_PREFIX + "-section-separator";
    public static final String  CSS_SECTION_CLIENT = CSS_PREFIX + "-section-client";
    
    public final Lazy<Color>    COLOR_SECTION_TITLE_FG = new LockedLazyInit( () -> new Color( null, 0x4c, 0x85, 0xbc ) );
    public final Lazy<Color>    COLOR_SECTION_TITLE_BG = new LockedLazyInit( () -> new Color( null, 0xbc, 0xe1, 0xf4 ) );
    public final Lazy<Color>    COLOR_SECTION_TITLE_BORDER = new LockedLazyInit( () -> new Color( null, 0x80, 0x80, 0xa0 ) );

    private static ArrayList<Callable<IMarkdownRenderer>> mdRendererFactories = new ArrayList();
    
    /**
     * Static init: register standard Markdown renderer
     */
    static {
        registerMarkdownRenderer( new Callable<IMarkdownRenderer>() {
            @Override
            public PageLinkRenderer call() throws Exception {
                return new PageLinkRenderer();
            }
        });
    }
    
    public static void registerMarkdownRenderer( Callable<IMarkdownRenderer> factory ) {
        mdRendererFactories.add( factory );
    }

    /**
     * The toolkit of the given control.
     * <p/>
     * XXX Current implementation return a toolkit with <b>no</b> connection to
     * the given control or its panel. Panel related methods do not work properly.
     */
    public static final DefaultToolkit of( Control control ) {
        return new DefaultToolkit( null, null ); 
    }
    
    // instance *******************************************

    protected PanelPath                 panelPath;
    
    protected PageStack<PanelPath>.Page panelPage;
    
    protected FormColors                colors;
    
    protected boolean                   closed;
    
    
    public DefaultToolkit( PanelPath panelPath, PageStack<PanelPath>.Page panelPage ) {
        this.panelPath = panelPath;
        this.panelPage = panelPage;
    }
    
    public PanelPath getPanelPath() {
        return panelPath;
    }

    @Override
    public void close() {
        if (!isClosed()) {
            if (COLOR_SECTION_TITLE_BG.isInitialized()) {
                COLOR_SECTION_TITLE_BG.get().dispose();
            }
            if (COLOR_SECTION_TITLE_FG.isInitialized()) {
                COLOR_SECTION_TITLE_FG.get().dispose();
            }
            if (COLOR_SECTION_TITLE_BORDER.isInitialized()) {
                COLOR_SECTION_TITLE_BORDER.get().dispose();
            }
        }
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed || 
                panelPage != null && panelPage.control != null && panelPage.control.isDisposed();
    }

    @Override
    public Label createLabel( Composite parent, String text, int... styles ) {
        Label result = adapt( new Label( parent, stylebits( styles ) ), false, false );
        if (text != null) {
            result.setText( text );
        }
        return result;
    }

    @Override
    public Label createFlowText( Composite parent, String text, int... styles ) {
        Label result = new Label( parent, stylebits( styles ) | SWT.WRAP ) {
//            private String callbackId;
            @Override
            public void setText( String _text ) {
                String html = markdownToHtml( _text, this );
                super.setText( html );
                
                // LinkActionServiceHandler cannot trigger UIThread events, so this ensures that
                // there is a UI callback active; However, this leads to activate UICallbacks almost
                // all the time; commented out in favour of a simple timeout handled by BatikApplication 

//                if (html.contains( "<a " )) {
//                    callbackId = "FlowText-" + hashCode();
//                    UIUtils.activateCallback( callbackId );
//                    log.warn( "ACTIVATE: " + callbackId );
//                }
            }

//            @Override
//            public void dispose() {
//                super.dispose();
//                if (callbackId != null) {
//                    UIUtils.deactivateCallback( callbackId );                    
//                }
//            }            
        };
        adapt( result, false, false );

        if (text != null) {
            result.setText( text );
        }
        return result;
    }

    @Override
    public String markdownToHtml( String markdown, Widget widget ) {
        LinkRenderer linkRenderer = new DelegatingLinkRenderer( widget );
        String processed = new PegDownProcessor().markdownToHtml( markdown, linkRenderer );
        return processed;
    }
    
    @Override
    public Label createFlowText( Composite parent, String text, ILinkAction[] linkActions, int... styles ) {
        throw new RuntimeException( "not yet implemented" );
    }

    /**
     * 
     */
    protected static class PegDownRenderOutput
            extends MarkdownRenderOutput {

        public Rendering createRendering() {
            Rendering rendering = new Rendering( url.get(), text.get() );
            title.ifPresent( v -> rendering.withAttribute( "title", FastEncoder.encode( v ) ) );
            id.ifPresent( v -> rendering.withAttribute( "id", FastEncoder.encode( v ) ) );
            clazz.ifPresent( v -> rendering.withAttribute( "class", FastEncoder.encode( v ) ) );
            target.ifPresent( v -> rendering.withAttribute( "target", FastEncoder.encode( v ) ) );
            return rendering;
        }
    }
    
    
    /**
     * Delegates link and image rendering to the
     * {@link DefaultToolkit#registerMarkdownRenderer(IPanelToolkit.IMarkdownRenderer)
     * registered} {@link IMarkdownRenderer}s.
     */
    protected class DelegatingLinkRenderer
            extends LinkRenderer {
        
        private Widget              widget;
        
        public DelegatingLinkRenderer( Widget widget ) {
            this.widget = widget;
        }

        protected Rendering render( IMarkdownNode node ) {
            Rendering result = null;
            // check registered factories
            for (Callable<IMarkdownRenderer> factory : mdRendererFactories) {
                PegDownRenderOutput out = new PegDownRenderOutput();
                try {
                    if (factory.call().render( DefaultToolkit.this, node, out, widget )) {
                        result = out.createRendering();
                        break;
                    }
                }
                catch (Exception e) {
                    throw Throwables.propagate( e );
                }
            }
            // default: simple external link
            if (result == null) {
                PegDownRenderOutput out = new PegDownRenderOutput();
                if (new ExternalLinkRenderer().render( DefaultToolkit.this, node, out, widget )) {
                    result = out.createRendering();
                }
            }
            return result;
        }
        
        @Override
        public Rendering render( final ExpImageNode imageNode, final String text ) {
            Rendering result = render( new IMarkdownNode() {
                @Override
                public IMarkdownNode.Type type() {
                    return IMarkdownNode.Type.ExpImage;
                }
                @Override
                public String url() {
                    return imageNode.url;
                }
                @Override
                public String title() {
                    return imageNode.title;
                }
                @Override
                public String text() {
                    return text;
                }
            });
            return result != null ? result : super.render( imageNode, text );
        }

        @Override
        public Rendering render( final ExpLinkNode linkNode, final String linktext ) {
            Rendering result = render( new IMarkdownNode() {
                @Override
                public IMarkdownNode.Type type() {
                    return IMarkdownNode.Type.ExpLink;
                }
                @Override
                public String url() {
                    return linkNode.url;
                }
                @Override
                public String title() {
                    return linkNode.title;
                }
                @Override
                public String text() {
                    return linktext;
                }
            });
            return result != null ? result : super.render( linkNode, linktext );
        }

        @Override
        public Rendering render( AutoLinkNode node ) {
            return super.render( node );
        }

        @Override
        public Rendering render( RefLinkNode node, String url, String title, String text ) {
            return super.render( node, url, title, text );
        }

        @Override
        public Rendering render( WikiLinkNode node ) {
            return super.render( node );
        }
    };

    @Override
    public Link createLink( Composite parent, String text, int... styles ) {
        Link result = adapt( new Link( parent, stylebits( styles ) ), false, false );
        if (text != null) {
            result.setText( text.contains( "<a>" ) ? text : Joiner.on( "" ).join( "<a>", text, "</a>" ) );
        }
//        Label result = createLabel( parent, text, styles | SWT.L );
//        result.setCursor( new Cursor( Polymap.getSessionDisplay(), SWT.CURSOR_HAND ) );
//        result.setForeground( Graphics.getColor( 0x00, 0x00, 0xff ) );
        return result;
    }

    @Override
    public Button createButton( Composite parent, String text, int... styles ) {
        Button control = new Button( parent, stylebits( styles ) ) {
            @Override public void setText( String _text ) {
                super.setText( StringUtils.upperCase( _text, Polymap.getSessionLocale() ) );
            }
        };
        if (text != null) {
            control.setText( text );
        }
        return adapt( control, true, true );
    }
    
    @Override
    public Text createText( Composite parent, String defaultText, int... styles ) {
        Text control = adapt( new Text( parent, stylebits( styles ) ), true, true );
        if (defaultText != null) {
            control.setText( defaultText );
        }
        return control;
    }

    @Override
    public ActionText createActionText( Composite parent, String defaultText, int... styles ) {
        ActionText result = new ActionText( parent, defaultText, styles );
        adapt( result.getText(), true, true );
        return result;
    }

    @Override
    public Composite createComposite( Composite parent, int... styles ) {
        assert !contains( styles, SWT.V_SCROLL ) && !contains( styles, SWT.H_SCROLL ) : "Use createScrolledComposite()!";        
        Composite result = new Composite( parent, stylebits( styles ) );
        result.setLayout( new FillLayout( SWT.HORIZONTAL ) );
        return adapt( result );
    }

    
    @Override
    public ScrolledComposite createScrolledComposite( Composite parent, int... styles ) {
        assert contains( styles, SWT.V_SCROLL ) || contains( styles, SWT.H_SCROLL );
        
        ScrolledComposite scrolled = new ScrolledComposite( parent, stylebits( styles ) );
        scrolled.setExpandHorizontal( true );
        scrolled.setExpandVertical( true );

        Composite body = createComposite( scrolled );
        body.setLayout( new FillLayout( SWT.VERTICAL ) );
        scrolled.setContent( body );

        scrolled.addControlListener( new ControlAdapter() {
            @Override
            public void controlResized( ControlEvent ev ) {
                Rectangle clientArea = scrolled.getClientArea();
                int scrollbarWidth = scrolled.getVerticalBar() != null ? scrolled.getVerticalBar().getSize().x : 0; 
                Point preferred = scrolled.getContent().computeSize( clientArea.width-scrollbarWidth, SWT.DEFAULT );
                scrolled.setMinSize( preferred );
            }
        });
        return adapt( scrolled );
    }

    
    @Override
    public Section createSection( Composite parent, String title, int... styles ) {
        Section result = adapt( new Section( parent, stylebits( styles ) | SWT.NO_FOCUS ) );
        result.setBackgroundMode( SWT.INHERIT_NONE );
        result.setText( title );
        result.setExpanded( true );

        result.addExpansionListener( new ExpansionAdapter() {
            @Override
            public void expansionStateChanged( ExpansionEvent ev ) {
                // trigger resize handler of the ScrollableComposite of the panel
                Point panelSize = panelPage.control.getSize();
                panelPage.control.setSize( panelSize.x, panelSize.y+1 );
            }
        });
        
        result.setMenu( parent.getMenu() );
//        if (result.toggle != null) {
//            section.toggle.setHoverDecorationColor(colors
//                    .getColor(IFormColors.TB_TOGGLE_HOVER));
//            section.toggle.setDecorationColor(colors
//                    .getColor(IFormColors.TB_TOGGLE));
//        }

//        result.setFont( boldFontHolder.getBoldFont(parent.getFont()));

//        if ((sectionStyle & Section.TITLE_BAR) != 0
//                || (sectionStyle & Section.SHORT_TITLE_BAR) != 0) {
//            colors.initializeSectionToolBarColors();
//            result.setTitleBarBackground( colors.getColor( IFormColors.TB_BG ) );
//            result.setTitleBarBorderColor( colors.getColor( IFormColors.TB_BORDER ) );
//        }
        // call setTitleBarForeground regardless as it also sets the label color
//        result.setTitleBarForeground( colors.getColor( IFormColors.TB_TOGGLE ) );

//        FontData[] defaultFont = parent.getFont().getFontData();
//        FontData bold = new FontData(defaultFont[0].getName(), defaultFont[0].getHeight(), SWT.BOLD);
//        result.setFont( Graphics.getFont( bold ) );
        
        result.setTitleBarForeground( COLOR_SECTION_TITLE_FG.get() );
        result.setTitleBarBackground( COLOR_SECTION_TITLE_BG.get() );
        result.setTitleBarBorderColor( COLOR_SECTION_TITLE_BORDER.get() );

        Composite client = createComposite( result );
        result.setClient( client );

        FillLayout layout = new FillLayout( SWT.VERTICAL );
        layout.spacing = 1;
        layout.marginWidth = 2;
        layout.marginHeight = 2;
        client.setLayout( layout );
        return result;
    }

    
    @Override
    public IPanelSection createPanelSection( Composite parent, String title, int... styles ) {
        PanelSection result = new PanelSection( this, parent, styles );
        if (title != null) {
            result.setTitle( title );
        }
        return result;
    }

    
    @Override
    public List createList( Composite parent, int... styles ) {
        List result = adapt( new List( parent, stylebits( styles ) ), false, false );
        return result;
    }

    
    @Override
    public IBusyIndicator busyIndicator( Composite parent ) {
        return new BusyIndicator( parent );
    }

    
    @Override
    public SimpleDialog createSimpleDialog( String title ) {
        return new SimpleDialog().title.put( title );
    }


    public Snackbar createSnackbar( Snackbar.Appearance appearance, String message, Item... actions ) {
        return new Snackbar( this, panelPage.control )
                .appearance.put( appearance )
                .message.put( message )
                .actions.put( actions );
    }

    
    public <T extends Composite> T adapt( T composite ) {
        UIUtils.setVariant( composite, CSS_PREFIX );

//        composite.setBackground( colors.getBackground() );
//        composite.addMouseListener( new MouseAdapter() {
//            public void mouseDown( MouseEvent e ) {
//                ((Control)e.widget).setFocus();
//            }
//        } );
//        if (composite.getParent() != null) {
//            composite.setMenu( composite.getParent().getMenu() );
//        }
        return composite;
    }


    @Override
    public <T extends Control> T adapt( T control, boolean trackFocus, boolean trackKeyboard ) {
        return _adapt( control, trackFocus, trackKeyboard );
    }

    
    public static <T extends Control> T _adapt( T control, boolean trackFocus, boolean trackKeyboard) {
        UIUtils.setVariant( control, CSS_PREFIX );

        control.setData( RWT.MARKUP_ENABLED, Boolean.TRUE );
        control.setData( RWT.TOOLTIP_MARKUP_ENABLED, Boolean.TRUE );
        control.setData( MarkupValidator.MARKUP_VALIDATION_DISABLED, Boolean.TRUE );

//        control.setBackground( colors.getBackground() );
//        control.setForeground( colors.getForeground() );

//        if (control instanceof ExpandableComposite) {
//            ExpandableComposite ec = (ExpandableComposite)control;
//            if (ec.toggle != null) {
//                if (trackFocus)
//                    ec.toggle.addFocusListener( visibilityHandler );
//                if (trackKeyboard)
//                    ec.toggle.addKeyListener( keyboardHandler );
//            }
//            if (ec.textLabel != null) {
//                if (trackFocus)
//                    ec.textLabel.addFocusListener( visibilityHandler );
//                if (trackKeyboard)
//                    ec.textLabel.addKeyListener( keyboardHandler );
//            }
//            return;
//        }

//        if (trackFocus) {
//            control.addFocusListener( visibilityHandler );
//        }
//        if (trackKeyboard) {
//            control.addKeyListener( keyboardHandler );
//        }
        return control;
    }


    protected int stylebits( int... styles ) {
        int result = SWT.NONE;
        for (int style : styles) {
            result |= style;
        }
        return result;
    }

    protected int styleHas( int[] styles, int search ) {
        return ArrayUtils.contains( styles, search ) ? search : SWT.NONE;
    }

}
