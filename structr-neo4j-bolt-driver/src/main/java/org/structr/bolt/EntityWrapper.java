/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.bolt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;
import org.neo4j.driver.v1.types.Entity;
import org.structr.api.NotFoundException;
import org.structr.api.NotInTransactionException;
import org.structr.api.graph.PropertyContainer;

/**
 *
 * @author Christian Morgner
 */
public abstract class EntityWrapper<T extends Entity> implements PropertyContainer {

	protected BoltDatabaseService db = null;
	protected boolean stale          = false;
	protected T entity               = null;

	public EntityWrapper(final BoltDatabaseService db, final T entity) {

		this.entity = entity;
		this.db   = db;
	}

	protected abstract String getQueryPrefix();

	@Override
	public long getId() {
		return entity.id();
	}

	@Override
	public boolean hasProperty(final String name) {

		assertNotStale();

		return !entity.get(name).isNull();
	}

	@Override
	public Object getProperty(final String name) {

		assertNotStale();

		final Value src = entity.get(name);
		if (src.isNull()) {

			return null;
		}

		final Object value = src.asObject();
		if (value instanceof List) {

			// convert list to array
			return ((List)value).toArray(new String[0]);
		}

		return value;
	}

	@Override
	public Object getProperty(String name, Object defaultValue) {

		assertNotStale();

		Object value = getProperty(name);
		if (value == null) {

			return defaultValue;
		}

		return value;
	}

	@Override
	public void setProperty(final String key, final Object value) {

		assertNotStale();

		final SessionTransaction tx   = db.getCurrentTransaction();
		final Map<String, Object> map = new HashMap<>();

		map.put("id", entity.id());
		map.put("value", value);

		// update entity handle
		entity = (T)tx.getEntity(getQueryPrefix() + " WHERE ID(n) = {id} SET n.`" + key + "` = {value} RETURN n", map);

		tx.modified(this);
	}

	@Override
	public void removeProperty(String key) {

		assertNotStale();

		final SessionTransaction tx   = db.getCurrentTransaction();
		final Map<String, Object> map = new HashMap<>();

		map.put("id", entity.id());

		// update entity handle
		entity = (T)tx.getEntity(getQueryPrefix() + " WHERE ID(n) = {id} SET n.`" + key + "` = Null RETURN n", map);

		tx.modified(this);
	}

	@Override
	public Iterable<String> getPropertyKeys() {

		assertNotStale();

		final SessionTransaction tx   = db.getCurrentTransaction();
		final Map<String, Object> map = new HashMap<>();

		map.put("id", entity.id());

		return tx.getStringList(getQueryPrefix() + " WHERE ID(n) = {id} RETURN keys(n)", map);
	}

	@Override
	public void delete() throws NotInTransactionException {

		assertNotStale();

		final SessionTransaction tx   = db.getCurrentTransaction();
		final Map<String, Object> map = new HashMap<>();

		map.put("id", entity.id());

		tx.set(getQueryPrefix() + " WHERE ID(n) = {id} DELETE n", map);
		tx.modified(this);

		stale = true;
	}

	@Override
	public boolean isSpatialEntity() {
		return false;
	}

	public void stale() {
		this.stale = true;
	}

	// ----- protected methods -----
	protected synchronized void assertNotStale() {

		if (stale) {

			// if a node/rel was deleted in a previous transaction but the caller keeps a
			// reference to this entity, we need to make sure that the reference is fresh.

			final SessionTransaction tx   = db.getCurrentTransaction();
			final Map<String, Object> map = new HashMap<>();

			map.put("id", entity.id());

			try {

				// check if entity has been deleted
				entity = (T)tx.getEntity(getQueryPrefix() + " WHERE ID(n) = {id} RETURN n", map);

			} catch (NoSuchRecordException nex) {
				throw new NotFoundException(nex);
			}

			stale  = false;
		}
	}
}
