/* 
 * polymap.org
 * Copyright (C) 2014, Falko Bräutigam. All rights reserved.
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
package org.polymap.rhei.fulltext.model2;

import static com.google.common.collect.Lists.newArrayList;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import org.polymap.rhei.fulltext.FulltextIndex;
import org.polymap.rhei.fulltext.indexing.FeatureTransformer;
import org.polymap.rhei.fulltext.indexing.ToStringTransformer;
import org.polymap.rhei.fulltext.update.UpdateableFulltextIndex;
import org.polymap.rhei.fulltext.update.UpdateableFulltextIndex.Updater;

import org.polymap.model2.Entity;
import org.polymap.model2.query.Expressions;
import org.polymap.model2.query.Query;
import org.polymap.model2.query.grammar.BooleanExpression;
import org.polymap.model2.runtime.EntityRuntimeContext.EntityStatus;
import org.polymap.model2.store.CloneCompositeStateSupport;
import org.polymap.model2.store.CompositeState;
import org.polymap.model2.store.StoreDecorator;
import org.polymap.model2.store.StoreSPI;
import org.polymap.model2.store.StoreUnitOfWork;

/**
 * Provides a decorator for an underlying store. This decorator tracks modifications and
 * feed them into an {@link UpdateableFulltextIndex}. 
 *
 * @author <a href="http://www.polymap.de">Falko Bräutigam</a>
 */
public class FulltextIndexer
        extends StoreDecorator
        implements StoreSPI {

    private static Log log = LogFactory.getLog( FulltextIndexer.class );
    
    /** Alwasy true: allow all Entity types to be indexed. */
    public static final Predicate<Entity>   ALL = Predicates.alwaysTrue();
    
    
    /**
     * An {@link FulltextIndexer#setEntityFilter(Predicate) Entity filter} that filters
     * entities via a given list of class names. 
     */
    public static class NameFilter
            implements Predicate<Entity> {
        
        private String[]        classNames;

        public NameFilter( String... classNames ) {
            assert classNames != null && classNames.length > 0;
            this.classNames = classNames;
        }

        @Override
        public boolean apply( Entity input ) {
            for (String className : classNames) {
                if (input.getClass().getName().endsWith( className )) {
                    return true;
                }
            }
            return false;
        }
    }

    
    /**
     * An {@link FulltextIndexer#setEntityFilter(Predicate) Entity filter} that filters
     * entities via a given list of class names. 
     */
    public static class TypeFilter
            implements Predicate<Entity> {
        
        private Class<? extends Entity>[]   types;

        public TypeFilter( Class<? extends Entity>... types ) {
            assert types != null && types.length > 0;
            this.types = types;
        }

        @Override
        public boolean apply( Entity input ) {
            for (Class<? extends Entity> type : types) {
                if (type.equals( input.getClass() )) {
                    return true;
                }
            }
            return false;
        }
    }

    
    /**
     * Creates a Model2 {@link Query} for the given fulltext query. Assumes that
     * index is build using {@link EntityFeatureTransformer}.
     * @throws Exception 
     *
     * @see FulltextIndex#search(String, int)
     */
    public static BooleanExpression query( FulltextIndex index, String queryString, int maxResults ) throws Exception {
        Iterable<JSONObject> fulltextResults = index.search( queryString, maxResults );

        List<BooleanExpression> ids = new ArrayList( 256 );
        for (JSONObject record : fulltextResults) {
            if (record.optString( FulltextIndex.FIELD_ID ).length() > 0) {
                ids.add( Expressions.id( record.getString( FulltextIndex.FIELD_ID ) ) );
            }
            else {
                log.warn( "No FIELD_ID in record: " + record );
            }
        }
        log.info( "Fulltext result ids: " + ids );
        
        // query expression
        if (ids.isEmpty()) {
            return Expressions.FALSE;
        }
        else if (ids.size() == 1) {
            return ids.get( 0 );
        }
        else if (ids.size() == 2) {
            return Expressions.or( ids.get( 0 ), ids.get( 1 ) );
        }
        else {
            BooleanExpression[] more = ids.subList( 2, ids.size() ).toArray( new BooleanExpression[ids.size()-2] );
            return Expressions.or( ids.get( 0 ), ids.get( 1 ), more );
        }
    }
    
    
    // instance *******************************************
    
    private UpdateableFulltextIndex             index;
    
    private List<? extends FeatureTransformer>  transformers = newArrayList( 
            new EntityFeatureTransformer(), 
            new ToStringTransformer() );
    
    private Predicate<Entity>                   entityFilter;
    
    
    public FulltextIndexer( UpdateableFulltextIndex index, StoreSPI store ) {
        this( index, ALL, store );
    }

    
    public FulltextIndexer( UpdateableFulltextIndex index, Predicate<Entity> entityFilter, StoreSPI store ) {
        super( store );
        this.index = index;
        setEntityFilter( entityFilter );
    }

    
    public FulltextIndexer( UpdateableFulltextIndex index, Predicate<Entity> entityFilter,
            List<? extends FeatureTransformer> transformers, StoreSPI store ) {
        this( index, entityFilter, store );
        setTransformers( transformers );
    }

    
    public FulltextIndexer setEntityFilter( Predicate<Entity> filter ) {
        this.entityFilter = filter;
        return this;
    }
    
    
    public FulltextIndexer setTransformers( List<? extends FeatureTransformer> transformers ) {
        this.transformers = transformers;
        return this;
    }


    @Override
    public StoreUnitOfWork createUnitOfWork() {
        StoreUnitOfWork suow = store.createUnitOfWork();
        return suow instanceof CloneCompositeStateSupport
                ? new IndexerUnitOfWork2( suow )
                : new IndexerUnitOfWork( suow );
    }

    
    /**
     * See {@link #query(FulltextIndex, String, int)}. 
     */
    public BooleanExpression query( String queryString, int maxResults ) throws Exception {
        return query( index, queryString, maxResults );
    }

    
    protected JSONObject transform( Entity feature ) {
        Object transformed = feature;
        for (FeatureTransformer transformer : transformers) {
            transformed = transformer.apply( transformed );
        }
        assert ((JSONObject)transformed).opt( FulltextIndex.FIELD_ID ) != null;
        log.debug( "Transformed: " + transformed.toString() );
        return (JSONObject)transformed;
    }

    
    /**
     * 
     */
    protected class IndexerUnitOfWork
            extends UnitOfWorkDecorator
            implements StoreUnitOfWork {
        
        private Updater             updater;


        public IndexerUnitOfWork( StoreUnitOfWork suow ) {
            super( suow );
        }


        @Override
        public void prepareCommit( Iterable<Entity> modified ) throws Exception {
            // update fulltext index
            updater = index.prepareUpdate();
            for (Entity entity : modified) {
                if (entityFilter.apply( entity )) {
                    if (entity.status() == EntityStatus.CREATED) {
                        updater.store( transform( entity ), false );
                    }
                    else if (entity.status() == EntityStatus.MODIFIED) {
                        updater.store( transform( entity ), true );
                    }
                    else if (entity.status() == EntityStatus.REMOVED) {
                        updater.remove( entity.id().toString() );
                    }
                }
            }
            // call delegate
            suow.prepareCommit( modified );
        }

        
        @Override
        public void commit() {
            assert updater != null;
            updater.apply();
            updater = null;

            suow.commit();
        }


        @Override
        public void rollback( Iterable<Entity> modified ) {
            if (updater != null) {
                updater.close();
                updater = null;
            }
            suow.rollback( modified );
        }

    }

    
    /**
     * 
     */
    protected class IndexerUnitOfWork2
            extends IndexerUnitOfWork
            implements CloneCompositeStateSupport {

        public IndexerUnitOfWork2( StoreUnitOfWork suow ) {
            super( suow );
        }

        protected CloneCompositeStateSupport suow() {
            return (CloneCompositeStateSupport)suow;
        }
        
        @Override
        public CompositeState cloneEntityState( CompositeState state ) {
            return suow().cloneEntityState( state ); 
        }

        @Override
        public void reincorparateEntityState( CompositeState state, CompositeState clonedState ) {
            suow().reincorparateEntityState( state, clonedState );
        }
        
    }

}
