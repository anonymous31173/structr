/*
 *  Copyright (C) 2010-2013 Axel Morgner
 * 
 *  This file is part of structr <http://structr.org>.
 * 
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.cypher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.structr.core.property.PropertyKey;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Services;
import org.structr.core.graph.CypherQueryCommand;

/**
 * Encapsulates a Cypher query.
 *
 * @author Christian Morgner
 */
public class CypherQuery {

	private Map<String, Object> parameters        = new LinkedHashMap<String, Object>();
	private boolean includeHiddenAndDeleted       = false;
	private CypherQueryCommand cypherQueryCommand = null;
	private boolean descending                    = false;
	private boolean publicOnly                    = false;
	private String indexQuery                     = null;
	private String indexName                      = null;
	private String sortKey                        = null;
	private long limit                            = -1;
	private long offset                           = -1;

	private CypherQuery() {}
	
	private CypherQuery(SecurityContext securityContext, String indexName) {

		this.cypherQueryCommand = Services.command(securityContext, CypherQueryCommand.class);
		this.indexName = indexName;
	}
	
	// builder methods
	public static CypherQuery query(SecurityContext securityContext, String indexName) {
		
		CypherQuery query = new CypherQuery(securityContext, indexName);
		
		return query;
	}
	
	// extension methods
	public CypherQuery sort(String sortKey) {
		return sort(sortKey, false);
	}
	
	public CypherQuery sort(String sortKey, boolean descending) {
		
		this.descending = descending;
		this.sortKey = sortKey;
		
		return this;
	}
	
	public CypherQuery limit(long limit) {
		
		this.limit = limit;
		
		return this;
	}
	
	public CypherQuery offset(long offset) {
		
		this.offset = offset;
		
		return this;
	}
	
	public CypherQuery includeHiddenAndDeleted(boolean includeHiddenAndDeleted) {
		
		this.includeHiddenAndDeleted = includeHiddenAndDeleted;
		
		return this;
	}
	
	public CypherQuery publicOnly(boolean publicOnly) {
		
		this.publicOnly = publicOnly;
		
		return this;
	}
	
	public CypherQuery search(String indexQuery) {
		
		this.indexQuery = indexQuery;
		
		return this;
	}
	
	public CypherQuery search(PropertyKey key, Object value) {
		
		this.indexQuery = key.dbName().concat(":").concat(value.toString());
		
		return this;
	}
	
	public List<GraphObject> execute() throws FrameworkException {
		return cypherQueryCommand.execute(toString(), getParameters(), includeHiddenAndDeleted, publicOnly);
	}
	
	@Override
	public String toString() {
		
		StringBuilder buf = new StringBuilder(100);
		
		buf.append("START n=node:");
		buf.append(indexName);
		buf.append("(\"");
		buf.append(indexQuery.trim());
		buf.append("\") ");
		buf.append("RETURN n");
		
		if(sortKey != null) {
			
			buf.append(" ORDER BY n.");
			buf.append(sortKey);
			
			if(descending) {
				buf.append(" DESC");
			}
		}
		
		if(offset >= 0) {
			buf.append(" SKIP ").append(offset);
		}
		
		if(limit >= 0) {
			buf.append(" LIMIT ").append(limit);
		}
		
		return buf.toString();
	}
	
	public Map<String, Object> getParameters() {
		return parameters;
	}
}
