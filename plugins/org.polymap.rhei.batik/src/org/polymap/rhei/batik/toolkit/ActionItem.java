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
package org.polymap.rhei.batik.toolkit;

import java.util.function.Consumer;

import org.eclipse.swt.events.SelectionEvent;

import org.polymap.core.runtime.config.Concern;
import org.polymap.core.runtime.config.Config2;
import org.polymap.core.runtime.config.Mandatory;

/**
 * Performs a single action when the item is pressed.
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class ActionItem
        extends Item {
    
    public ActionItem( ItemContainer container ) {
        super( container );
    }
    
    /**
     * The action to be performed when the item is pressed.
     */
    @Mandatory
    @Concern( NotDisposed.class )
    @Concern( ItemEvent.Fire.class )
    public Config2<ActionItem,Consumer<SelectionEvent>> action;
    
}