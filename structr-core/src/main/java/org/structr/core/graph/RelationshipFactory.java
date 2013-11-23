/**
 * Copyright (C) 2010-2013 Axel Morgner, structr <structr@structr.org>
 *
 * This file is part of structr <http://structr.org>.
 *
 * structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */


package org.structr.core.graph;

import org.neo4j.graphdb.Relationship;

import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;

//~--- JDK imports ------------------------------------------------------------

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.GenericRelationship;

//~--- classes ----------------------------------------------------------------

/**
 * A factory for structr relationships. This class exists because we need a fast
 * way to instantiate and initialize structr relationships, as this is the most-
 * used operation.
 *
 * @author Axel Morgner
 */
public class RelationshipFactory<T extends RelationshipInterface> extends Factory<Relationship, T> {

	private static final Logger logger = Logger.getLogger(RelationshipFactory.class.getName());

	// private Map<String, Class> nodeTypeCache = new ConcurrentHashMap<String, Class>();
	public RelationshipFactory(SecurityContext securityContext) {
		super(securityContext);
	}

	@Override
	public T instantiate(final Relationship relationship) throws FrameworkException {
		return (T) instantiateWithType(relationship, factoryDefinition.determineRelationshipType(relationship), false);
	}

	@Override
	public T instantiateWithType(Relationship relationship, Class<T> relClass, boolean isCreation) throws FrameworkException {

		logger.log(Level.FINEST, "Instantiate relationship with type {0}", relClass.getName());
		
		SecurityContext securityContext = factoryProfile.getSecurityContext();
		T newRel          = null;

		try {

			try {
				newRel = relClass.newInstance();

			} catch (Throwable t2) {
				newRel = null;
			}

		} catch (Throwable t) { }

		if (newRel == null) {
			newRel = (T)StructrApp.getConfiguration().getFactoryDefinition().createGenericRelationship();
		}

		newRel.init(securityContext, relationship);
		newRel.onRelationshipInstantiation();

		String newRelType = newRel.getProperty(GraphObject.type);
		if (newRelType == null && !(relClass.equals(GenericRelationship.class))) { //  || (newNodeType != null && !newNodeType.equals(nodeType))) {

			final App app = StructrApp.getInstance();
			
			try {
				
				app.beginTx();
				
				newRel.unlockReadOnlyPropertiesOnce();
				newRel.setProperty(GraphObject.type, relClass.getSimpleName());
				
				app.commitTx();
				
			} catch (Throwable t) {

				logger.log(Level.SEVERE, "Unable to set type property {0} on relationship {1}: {2}", new Object[] { relClass, newRel, t.getMessage() } );
			} finally {
				
				app.finishTx();
				
			}
		}
			
		return newRel;
	}

	@Override
	public T adapt(Relationship relationship) {

		try {
			return instantiate(relationship);
			
		} catch (FrameworkException fex) {
			
			logger.log(Level.WARNING, "Unable to adapt relationship", fex);
		}

		return null;
	}

	/**
	 * Create structr relationship from all given underlying database rels
	 *
	 * @param input
	 * @return
	 */
	public List<T> instantiate(final Iterable<Relationship> input) throws FrameworkException {

		List<T> rels = new LinkedList<>();

		if ((input != null) && input.iterator().hasNext()) {

			for (Relationship rel : input) {

				T n = instantiate(rel);

				rels.add(n);

			}

		}

		return rels;
	}

	@Override
	public T instantiate(Relationship obj, boolean includeDeletedAndHidden, boolean publicOnly) throws FrameworkException {

		factoryProfile.setIncludeDeletedAndHidden(includeDeletedAndHidden);
		factoryProfile.setPublicOnly(publicOnly);

		return instantiate(obj);
	}

	@Override
	public T instantiateDummy(final Relationship entity, final String entityType) throws FrameworkException {

		Map<String, Class<? extends RelationshipInterface>> entities = StructrApp.getConfiguration().getRelationshipEntities();
		Class<T> relClass                                            = (Class<T>)entities.get(entityType);
		T newRel                                                     = null;

		if (relClass != null) {

			try {

				newRel = relClass.newInstance();
				newRel.init(factoryProfile.getSecurityContext(), entity);
				
				// let rel. know of its instantiation so it can cache its start- and end node ID.
				newRel.onRelationshipInstantiation();

			} catch (Throwable t) {

				newRel = null;

			}

		}

		return newRel;

	}
}
