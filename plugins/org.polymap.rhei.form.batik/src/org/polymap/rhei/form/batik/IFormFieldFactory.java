/* 
 * polymap.org
 * Copyright (C) 2014-2016, Falko Bräutigam. All rights reserved.
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
package org.polymap.rhei.form.batik;

import org.opengis.feature.Property;

import org.polymap.rhei.field.IFormField;
import org.polymap.rhei.field.LabelFormField;
import org.polymap.rhei.field.StringFormField;

/**
 * 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public interface IFormFieldFactory {
    
    /**
     * Produces {@link StringFormField} instances only.
     */
    public static final IFormFieldFactory LABEL_FIELD_FACTORY = new IFormFieldFactory() {
        public IFormField createField( Property prop ) {
            return new LabelFormField();
        }
    };

    // API ************************************************
    
    IFormField createField( Property prop );

}
