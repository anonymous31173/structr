/**
 * Copyright (C) 2010-2017 Structr GmbH
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
package org.structr.core.entity.relationship;

import org.structr.common.PropertyView;
import org.structr.common.View;
import org.structr.core.entity.LinkedTreeNode;
import org.structr.core.entity.OneToMany;
import org.structr.core.property.IntProperty;
import org.structr.core.property.Property;

/**
 *
 *
 */
public abstract class AbstractChildren<S extends LinkedTreeNode, T extends LinkedTreeNode> extends OneToMany<S, T> {

	public static final Property<Integer> position = new IntProperty("position").indexed();

	public static final View uiView = new View(AbstractChildren.class, PropertyView.Ui, position);

	@Override
	public String name() {
		return "CONTAINS";
	}

	@Override
	public boolean isInternal() {
		return true;
	}
}
