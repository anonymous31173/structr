/*
 *  Copyright (C) 2010-2013 Axel Morgner, structr <structr@structr.org>
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
package org.structr.core.module;

import java.util.Set;

/**
 * Returns a Set of all java packages that contain structr entities.
 *
 * @author Christian Morgner
 */
public class GetEntityPackagesCommand extends ModuleServiceCommand {

	public Set<String> execute() {
		
		ModuleService service = (ModuleService)getArgument("moduleService");
		if(service != null) {
			
			return service.getNodeEntityPackages();
		}

		return null;
	}
}
