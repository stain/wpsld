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
import org.kordamp.ikonli.fluentui.FluentUiFilledAL;
import org.kordamp.ikonli.fluentui.FluentUiFilledMZ;
import org.kordamp.ikonli.fluentui.FluentUiRegularAL;
import org.kordamp.ikonli.fluentui.FluentUiRegularMZ;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.control.Cell;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.EntityListing;
import no.s11.wpsld.form.EntityTree;

/**
 * Small icons for entities by their types, and for the folders of the
 * entity tree, as a file manager has for its files and folders. Each is an
 * outline over its filled shape: the stylesheet colours them, by
 * <code>icon-</code> style class, and shows the fill only in the OS/2 Warp
 * style; in the others the outline is coloured, and as the text of a
 * selected row.
 */
final class EntityIcons {
	/** An icon: its outline and its filled shape, and its style class */
	enum Glyph {
		ARCHIVE(FluentUiRegularAL.ARCHIVE_20, FluentUiFilledAL.ARCHIVE_20),
		FOLDER(FluentUiRegularAL.FOLDER_20, FluentUiFilledAL.FOLDER_20),
		PERSON(FluentUiRegularMZ.PERSON_20, FluentUiFilledMZ.PERSON_20),
		ORGANIZATION(FluentUiRegularAL.BUILDING_BANK_20, FluentUiFilledAL.BUILDING_BANK_20),
		PLACE(FluentUiRegularAL.LOCATION_20, FluentUiFilledAL.LOCATION_20),
		DOCUMENT(FluentUiRegularAL.DOCUMENT_20, FluentUiFilledAL.DOCUMENT_20),
		GLOBE(FluentUiRegularAL.GLOBE_20, FluentUiFilledAL.GLOBE_20),
		LINK(FluentUiRegularAL.LINK_20, FluentUiFilledAL.LINK_20),
		THING(FluentUiRegularAL.CUBE_20, FluentUiFilledAL.CUBE_20);

		final Ikon line;
		final Ikon fill;

		Glyph(Ikon line, Ikon fill) {
			this.line = line;
			this.fill = fill;
		}

		String styleClass() {
			return "icon-" + name().toLowerCase();
		}
	}

	/** The icon of the first of these types an entity is of, subtypes included */
	private record Kind(String type, Glyph glyph) {
	}

	private static final List<Kind> KINDS = List.of(new Kind("Dataset", Glyph.FOLDER),
			new Kind("Person", Glyph.PERSON), new Kind("Organization", Glyph.ORGANIZATION),
			new Kind("Place", Glyph.PLACE), new Kind("CreativeWork", Glyph.DOCUMENT));
	static final int SIZE = 16;

	private EntityIcons() {
	}

	/** The icon of an entity, by its types */
	static Glyph of(Entity entity, EntityListing listing) {
		return KINDS.stream().filter(k -> listing.isA(entity, k.type())).map(Kind::glyph).findFirst()
				.orElse(Glyph.THING);
	}

	/** The icon of a node of the tree */
	static Glyph of(EntityTree.Node node, EntityListing listing) {
		return switch (node.kind()) {
		case ROOT -> Glyph.ARCHIVE;
		case FOLDER, TYPE -> Glyph.FOLDER;
		case EXTERNAL -> Glyph.GLOBE;
		case RESOURCE -> Glyph.LINK;
		case FILE, CONTEXTUAL -> node.entity().map(e -> of(e, listing)).orElse(Glyph.DOCUMENT);
		};
	}

	/**
	 * The icon of a cell, to {@link Icon#set} as the cell is reused. The
	 * stylesheet knows the cell's text colour as <code>-icon-text</code>.
	 */
	static Icon forCell(Cell<?> cell) {
		Icon icon = new Icon();
		cell.textFillProperty().subscribe(fill -> icon.setStyle("-icon-text: " + css(fill) + ";"));
		return icon;
	}

	private static String css(Paint paint) {
		if (!(paint instanceof Color colour)) {
			return "-fx-text-background-color";
		}
		return String.format("rgba(%d,%d,%d,%s)", Math.round(colour.getRed() * 255),
				Math.round(colour.getGreen() * 255), Math.round(colour.getBlue() * 255), colour.getOpacity());
	}

	/** An icon: the filled shape under the outline */
	static final class Icon extends StackPane {
		private final FontIcon fill = new FontIcon(Glyph.THING.fill);
		private final FontIcon line = new FontIcon(Glyph.THING.line);
		private Glyph glyph;

		Icon() {
			getStyleClass().add("entity-icon");
			fill.getStyleClass().add("entity-icon-fill");
			line.getStyleClass().add("entity-icon-line");
			fill.setIconSize(SIZE);
			line.setIconSize(SIZE);
			getChildren().addAll(fill, line);
			set(Glyph.THING);
		}

		void set(Glyph glyph) {
			if (glyph == this.glyph) {
				return;
			}
			if (this.glyph != null) {
				getStyleClass().remove(this.glyph.styleClass());
			}
			this.glyph = glyph;
			getStyleClass().add(glyph.styleClass());
			fill.setIconCode(glyph.fill);
			line.setIconCode(glyph.line);
		}

		Glyph glyph() {
			return glyph;
		}
	}
}
