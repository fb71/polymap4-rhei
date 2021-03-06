/* 
 * polymap.org
 * Copyright (C) 2015-2016, Falko Bräutigam. All rights reserved.
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

import java.util.List;

/**
 * 
 * @param <T> The type of items which is allowed for this container.
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public interface ItemContainer<T extends Item> {
    
    /**
     * Internally add an item to this group. Client code should use item constructor.
     */
    public boolean addItem( T item );

    /**
     * Internally remove an item to this group. Client code should use
     * {@link Item#dispose()}.
     */
    public boolean removeItem( T item );

    public List<T> items();
    
}