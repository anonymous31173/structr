/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.common;

import java.util.Map;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.MaintenanceCommand;
import org.structr.core.graph.NodeFactory;
import org.structr.core.graph.NodeServiceCommand;
import org.structr.core.graph.RelationshipFactory;
import org.structr.core.graph.SyncCommand;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;
import org.structr.dynamic.File;
import org.structr.rest.resource.MaintenanceParameterResource;
import org.structr.web.entity.FileBase;

public class DumpDatabaseCommand extends NodeServiceCommand implements MaintenanceCommand {

	static {

		MaintenanceParameterResource.registerMaintenanceCommand("dumpDatabase", DumpDatabaseCommand.class);
	}

	@Override
	public void execute(Map<String, Object> attributes) throws FrameworkException {

		try {

			final NodeFactory nodeFactory        = new NodeFactory(SecurityContext.getSuperUserInstance());
			final RelationshipFactory relFactory = new RelationshipFactory(SecurityContext.getSuperUserInstance());
			final App app                        = StructrApp.getInstance();
			final String fileName                = (String)attributes.get("name");

			if (fileName == null || fileName.isEmpty()) {

				throw new FrameworkException(400, "Please specify name.");
			}

			try (final Tx tx = app.tx()) {

				final FileBase file = FileHelper.createFile(securityContext, new byte[0], "application/zip", File.class, fileName);

				// make file visible for auth. users
				file.setProperties(securityContext, new PropertyMap(File.visibleToAuthenticatedUsers, true));

				// Don't include files
				SyncCommand.exportToStream(
					file.getOutputStream(),
					nodeFactory.bulkInstantiate(app.getDatabaseService().getAllNodes()),
					relFactory.bulkInstantiate(app.getDatabaseService().getAllRelationships()),
					null,
					false
				);

				tx.success();
			}

		} catch (Throwable t) {

			throw new FrameworkException(500, t.getMessage());
		}
	}

	@Override
	public boolean requiresEnclosingTransaction() {
		return true;
	}
}
