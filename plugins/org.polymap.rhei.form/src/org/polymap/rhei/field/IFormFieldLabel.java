/* 
 * polymap.org
 * Copyright (C) 2010-2016, Falko Br�utigam. All rights reserved.
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
package org.polymap.rhei.field;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.polymap.rhei.form.IFormToolkit;

/**
 * The basic interface of all form field labels. 
 *
 * @author <a href="http://www.polymap.de">Falko Br�utigam</a>
 */
public interface IFormFieldLabel {
    
    public void init( IFormFieldSite site );

    public void dispose();
    
    public Control createControl( Composite parent, IFormToolkit toolkit );
    
}
