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
package org.polymap.rhei.batik.contribution;

import org.polymap.rhei.batik.IPanel;
import org.polymap.rhei.batik.toolkit.md.MdToolbar2;

/**
 * Provides the logic to actually create and/or modify UI elements for a particular
 * {@link IContributionProvider} type.
 *
 * @param <T> The type of the target of the {@link IContributionProvider}.
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public abstract class ContributionHandler<T, C extends IContributionProvider> {
    
    public abstract boolean test( C contrib, T target );


    public abstract void handle( IContributionSite site, C contrib, T target );


    /**
     * 
     */
    public static class PanelFabHandler
            extends ContributionHandler<IPanel, IFabContribution> {

        @Override
        public boolean test( IFabContribution contrib, IPanel target ) {
            throw new RuntimeException( "not yet implemented." );
        }

        @Override
        public void handle( IContributionSite site, IFabContribution contrib, IPanel target ) {
        }
    }

    
    /**
     * 
     */
    public static class ToolbarHandler
            extends ContributionHandler<MdToolbar2, IToolbarContribution> {

        @Override
        public boolean test( IToolbarContribution contrib, MdToolbar2 target ) {
            throw new RuntimeException( "not yet implemented." );
        }

        @Override
        public void handle( IContributionSite site, IToolbarContribution contrib, MdToolbar2 target ) {
            contrib.fillToolbar( site, target );
        }
    }
    
}
