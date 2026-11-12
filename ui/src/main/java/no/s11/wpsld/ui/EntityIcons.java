/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.ui;

import java.util.List;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fluentui.FluentUiRegularAL;
import org.kordamp.ikonli.fluentui.FluentUiRegularMZ;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.control.Cell;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.EntityListing;
import no.s11.wpsld.form.EntityTree;

/**
 * Small icons for entities by their types, and for the folders of the
 * entity tree, as a file manager has for its files and folders. Coloured
 * as the text of their cell, also when selected.
 */
final class EntityIcons {
	/** The icon of the first of these types an entity is of, subtypes included */
	private record Kind(String type, Ikon icon) {
	}

	private static final List<Kind> KINDS = List.of(new Kind("Dataset", FluentUiRegularAL.FOLDER_20),
			new Kind("Person", FluentUiRegularMZ.PERSON_20), new Kind("Organization", FluentUiRegularAL.BUILDING_BANK_20),
			new Kind("Place", FluentUiRegularAL.LOCATION_20), new Kind("CreativeWork", FluentUiRegularAL.DOCUMENT_20));
	private static final Ikon THING = FluentUiRegularAL.CUBE_20;
	static final int SIZE = 16;

	private EntityIcons() {
	}

	/** The icon of an entity, by its types */
	static Ikon of(Entity entity, EntityListing listing) {
		return KINDS.stream().filter(k -> listing.isA(entity, k.type())).map(Kind::icon).findFirst().orElse(THING);
	}

	/** The icon of a node of the tree */
	static Ikon of(EntityTree.Node node, EntityListing listing) {
		return switch (node.kind()) {
		case ROOT -> FluentUiRegularAL.ARCHIVE_20;
		case FOLDER, TYPE -> FluentUiRegularAL.FOLDER_20;
		case EXTERNAL -> FluentUiRegularAL.GLOBE_20;
		case RESOURCE -> FluentUiRegularAL.LINK_20;
		case FILE, CONTEXTUAL -> node.entity().map(e -> of(e, listing)).orElse(FluentUiRegularAL.DOCUMENT_20);
		};
	}

	/**
	 * The icon of a cell, coloured as its text, to set the icon of with
	 * {@link FontIcon#setIconCode} as the cell is reused
	 */
	static FontIcon forCell(Cell<?> cell) {
		FontIcon icon = new FontIcon(THING);
		icon.setIconSize(SIZE);
		icon.getStyleClass().add("entity-icon");
		icon.iconColorProperty().bind(cell.textFillProperty());
		return icon;
	}
}
