/*
 * polymap.org
 * Copyright (C) 2011-2015, Falko Br�utigam. All rights reserved.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 */
package org.polymap.rhei.form;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.FormToolkit;

import org.polymap.core.ui.StatusDispatcher;
import org.polymap.core.ui.UIUtils;

import org.polymap.rhei.RheiFormPlugin;
import org.polymap.rhei.engine.form.BaseFieldComposite;
import org.polymap.rhei.engine.form.FormEditorToolkit;
import org.polymap.rhei.engine.form.FormPageController;
import org.polymap.rhei.field.FormFieldEvent;
import org.polymap.rhei.field.IFormFieldListener;

/**
 *
 * @author <a href="http://www.polymap.de">Falko Br�utigam</a>
 */
public class FormDialog
        extends TitleAreaDialog
        implements IFormFieldListener {

    private static Log log = LogFactory.getLog( FormDialog.class );

    private IFormPage               page;
    
    private Composite               pageBody;

    private DialogFormContainer     pageContainer;

    private IFormToolkit            toolkit;


    class DialogFormContainer
            extends FormPageContainer {
        
        public DialogFormContainer( IFormPage page ) {
            this.page = page;
            this.pageController = new FormPageController( page ) {
                @Override
                public Composite getPageBody() {
                    return pageBody;
                }
                @Override
                public IFormToolkit getToolkit() {
                    return toolkit;
                }
                @Override
                public void setPageTitle( String title ) {
                }
                @Override
                public void setEditorTitle( String title ) {
                }
                @Override
                public void setActivePage( String pageId ) {
                    throw new UnsupportedOperationException( "This is a single page container." );
                }
                @Override
                protected Object getEditor() {
                    return DialogFormContainer.this;
                }
                @Override
                protected Composite createFieldComposite( Composite parent ) {
                    return UIUtils.setVariant( toolkit.createComposite( parent ), BaseFieldComposite.CUSTOM_VARIANT_VALUE );
                }
            };
        }
    }
    
    public FormDialog( IFormPage page ) {
        super( PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell() );
        setShellStyle( getShellStyle() | SWT.RESIZE );

        this.page = page;
        this.pageContainer = new DialogFormContainer( page );
        
        pageContainer.addFieldListener( this );
        
        // init button state after fields and dialog have been initialized
        UIUtils.sessionDisplay().asyncExec( new Runnable() {
            public void run() {
                getButton( IDialogConstants.OK_ID ).setEnabled( pageContainer.isValid() && pageContainer.isValid() );
            }
        });
    }


    @Override
    public boolean close() {
        pageContainer.removeFieldListener( this );
        return super.close();
    }


    @Override
    public void fieldChange( FormFieldEvent ev ) {
        if (ev.getEventCode() == VALUE_CHANGE) {
            Button okBtn = getButton( IDialogConstants.OK_ID );
            if (okBtn != null) {
                okBtn.setEnabled( pageContainer.isValid() && pageContainer.isValid() );
            }
        }
    }


    @Override
    protected void okPressed() {
        log.debug( "okPressed() ..." );
        try {
            pageContainer.submit( null );
            pageContainer.dispose();

            super.okPressed();
        }
        catch (Exception e) {
            StatusDispatcher.handleError( RheiFormPlugin.PLUGIN_ID, this, "Werte konnten nicht gespeichert werden.", e );
        }
    }


    @Override
    protected void cancelPressed() {
        pageContainer.dispose();
        super.cancelPressed();
    }


    @Override
    protected Control createDialogArea( Composite parent ) {
        Composite result = (Composite)super.createDialogArea( parent );
        toolkit = new FormEditorToolkit( new FormToolkit( getParentShell().getDisplay() ) );

        // make margins
        Composite container = new Composite( parent, SWT.NONE );
        container.setLayoutData( new GridData( GridData.FILL_BOTH ) );
        GridLayout gl = new GridLayout();
        gl.marginWidth = 5;
        gl.marginHeight = 0;
        container.setLayout( gl );

        pageBody = new Composite( container, SWT.NONE );
        pageBody.setLayoutData( new GridData( GridData.FILL_BOTH ) );

        pageContainer.createContents( pageBody );
        try {
            pageContainer.reload( null );
        }
        catch (Exception e) {
            StatusDispatcher.handleError( RheiFormPlugin.PLUGIN_ID, this, e.getLocalizedMessage(), e );
        }

        // form.getToolkit().decorateFormHeading( form.getForm().getForm() );

//        // add page editor actions
//        Action[] pageActions = page.getEditorActions();
//        if (pageActions != null && pageActions.length > 0) {
//            form.getForm().getToolBarManager().add( new GroupMarker( "__pageActions__" ) );
//
//            for (Action action : pageActions) {
//                form.getForm().getToolBarManager().appendToGroup( "__pageActions__", action );
//            }
//            form.getForm().getToolBarManager().appendToGroup( "__pageActions__", new Separator() );
//        }
//
//        // add actions
//        form.getForm().getToolBarManager().add( new GroupMarker( "__standardPageActions__" ) );
//        for (Action action : ((FormEditor)getEditor()).standardPageActions) {
//            form.getForm().getToolBarManager().appendToGroup( "__standardPageActions__", action );
//        }
//        form.getForm().getToolBarManager().update( true );

        result.pack();
        return result;
    }


//    /**
//     *
//     */
//    class PageContainer
//            extends FormPageController {
//
//        public PageContainer( IFormPage page ) {
//            super( FormDialog.this, page, "_id_", "_title_" );
//        }
//
//        public void createContent() {
//            page.createFormContent( this );
//        }
//
//        @Override
//        public Composite getPageBody() {
//            return pageBody;
//        }
//
//        @Override
//        public IFormToolkit getToolkit() {
//            return toolkit;
//        }
//
//        @Override
//        public void setPageTitle( String title ) {
//            setMessage( title );
//        }
//
//        @Override
//        public void setEditorTitle( String title ) {
//            setTitle( title );
//        }
//
//        @Override
//        public void setActivePage( String pageId ) {
//            log.warn( "setActivePage() not supported." );
//        }
//
//    }

}
