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
 * outline over its filled shape: the stylesheet colours them by their
 * family's <code>icon-</code> style class, and shows the fill only in the
 * OS/2 Warp style; in the others the outline is coloured, and as the text
 * of a selected row.
 */
final class EntityIcons {
	/** Kinds of things, each icon coloured as its family */
	enum Family {
		CRATE, DATA, WORK, SOFTWARE, WEB, PERSON, ORGANIZATION, PLACE, EVENT, NATURE, PRODUCT, TERM, THING;

		String styleClass() {
			return "icon-" + name().toLowerCase();
		}
	}

	/** An icon: its outline, its filled shape, and its family */
	record Glyph(Ikon line, Ikon fill, Family family) {
		/** The outline, with the filled shape of the same name */
		static Glyph of(Ikon line, Family family) {
			String name = ((Enum<?>) line).name();
			return new Glyph(line,
					line instanceof FluentUiRegularAL ? FluentUiFilledAL.valueOf(name) : FluentUiFilledMZ.valueOf(name),
					family);
		}
	}

	static final Glyph CRATE = Glyph.of(FluentUiRegularAL.ARCHIVE_20, Family.CRATE);
	static final Glyph FOLDER = Glyph.of(FluentUiRegularAL.FOLDER_20, Family.DATA);
	static final Glyph DOCUMENT = Glyph.of(FluentUiRegularAL.DOCUMENT_20, Family.WORK);
	static final Glyph EXTERNAL = Glyph.of(FluentUiRegularAL.GLOBE_20, Family.WEB);
	static final Glyph RESOURCE = Glyph.of(FluentUiRegularAL.LINK_20, Family.WEB);
	static final Glyph THING = Glyph.of(FluentUiRegularAL.CUBE_20, Family.THING);

	/**
	 * The icon of an entity of a type: of the type, a subtype, or by name,
	 * for types not in schema.org, e.g. Bioschemas' ComputationalWorkflow
	 */
	private record Kind(String type, Glyph glyph) {
	}

	private static Kind kind(String type, Ikon line, Family family) {
		return new Kind(type, Glyph.of(line, family));
	}

	/** The first of these an entity is of wins: the more specific first */
	private static final List<Kind> KINDS = List.of(
			kind("Dataset", FluentUiRegularAL.FOLDER_20, Family.DATA),
			kind("ComputationalWorkflow", FluentUiRegularMZ.ORGANIZATION_20, Family.SOFTWARE),
			kind("FormalParameter", FluentUiRegularMZ.TEXTBOX_20, Family.SOFTWARE),
			kind("SoftwareSourceCode", FluentUiRegularAL.CODE_20, Family.SOFTWARE),
			kind("SoftwareApplication", FluentUiRegularAL.APPS_20, Family.SOFTWARE),
			kind("ComputerLanguage", FluentUiRegularMZ.WINDOW_DEV_TOOLS_20, Family.SOFTWARE),
			kind("Profile", FluentUiRegularMZ.RIBBON_STAR_20, Family.WORK),
			kind("DataDownload", FluentUiRegularAL.ARROW_DOWNLOAD_20, Family.DATA),
			kind("ImageObject", FluentUiRegularAL.IMAGE_20, Family.WORK),
			kind("Photograph", FluentUiRegularAL.CAMERA_20, Family.WORK),
			kind("VideoObject", FluentUiRegularMZ.VIDEO_20, Family.WORK),
			kind("AudioObject", FluentUiRegularMZ.SPEAKER_20, Family.WORK),
			kind("SpreadsheetDigitalDocument", FluentUiRegularMZ.TABLE_20, Family.WORK),
			kind("Table", FluentUiRegularMZ.TABLE_20, Family.WORK),
			kind("PresentationDigitalDocument", FluentUiRegularMZ.SLIDE_TEXT_20, Family.WORK),
			kind("Book", FluentUiRegularAL.BOOK_20, Family.WORK),
			kind("Article", FluentUiRegularMZ.NEWS_20, Family.WORK),
			kind("WebPage", FluentUiRegularMZ.WINDOW_20, Family.WEB),
			kind("WebSite", FluentUiRegularMZ.WINDOW_20, Family.WEB),
			kind("Comment", FluentUiRegularAL.COMMENT_20, Family.WORK),
			kind("Review", FluentUiRegularAL.COMMENT_20, Family.WORK),
			kind("Message", FluentUiRegularMZ.MAIL_20, Family.WORK),
			kind("Certification", FluentUiRegularAL.CERTIFICATE_20, Family.WORK),
			kind("Credential", FluentUiRegularAL.CERTIFICATE_20, Family.WORK),
			kind("LearningResource", FluentUiRegularAL.HAT_GRADUATION_20, Family.WORK),
			kind("Course", FluentUiRegularAL.HAT_GRADUATION_20, Family.WORK),
			kind("HowTo", FluentUiRegularMZ.TASK_LIST_20, Family.WORK),
			kind("DefinedTermSet", FluentUiRegularAL.BOOK_OPEN_20, Family.TERM),
			kind("Collection", FluentUiRegularAL.COLLECTIONS_20, Family.WORK),
			kind("CreativeWork", FluentUiRegularAL.DOCUMENT_20, Family.WORK),
			kind("Person", FluentUiRegularMZ.PERSON_20, Family.PERSON),
			kind("Audience", FluentUiRegularMZ.PEOPLE_COMMUNITY_20, Family.PERSON),
			kind("ContactPoint", FluentUiRegularAL.CONTACT_CARD_20, Family.PERSON),
			kind("EducationalOrganization", FluentUiRegularAL.HAT_GRADUATION_20, Family.ORGANIZATION),
			kind("Project", FluentUiRegularAL.BRIEFCASE_20, Family.ORGANIZATION),
			kind("LocalBusiness", FluentUiRegularAL.BUILDING_SHOP_20, Family.ORGANIZATION),
			kind("Organization", FluentUiRegularAL.BUILDING_BANK_20, Family.ORGANIZATION),
			kind("Grant", FluentUiRegularMZ.MONEY_20, Family.ORGANIZATION),
			kind("AdministrativeArea", FluentUiRegularAL.FLAG_20, Family.PLACE),
			kind("Residence", FluentUiRegularAL.HOME_20, Family.PLACE),
			kind("GeoShape", FluentUiRegularMZ.SHAPES_20, Family.PLACE),
			kind("Geometry", FluentUiRegularMZ.SHAPES_20, Family.PLACE),
			kind("GeoCoordinates", FluentUiRegularAL.LOCATION_20, Family.PLACE),
			kind("Place", FluentUiRegularAL.LOCATION_20, Family.PLACE),
			kind("Event", FluentUiRegularAL.CALENDAR_DATE_20, Family.EVENT),
			kind("Action", FluentUiRegularAL.FLASH_ON_20, Family.EVENT),
			kind("Taxon", FluentUiRegularAL.LEAF_20, Family.NATURE),
			kind("BioChemEntity", FluentUiRegularAL.BEAKER_20, Family.NATURE),
			kind("MedicalEntity", FluentUiRegularMZ.STETHOSCOPE_20, Family.NATURE),
			kind("Product", FluentUiRegularAL.CART_20, Family.PRODUCT),
			kind("DefinedTerm", FluentUiRegularMZ.TAG_20, Family.TERM),
			kind("PropertyValue", FluentUiRegularMZ.NUMBER_SYMBOL_20, Family.TERM),
			kind("QuantitativeValue", FluentUiRegularMZ.RULER_20, Family.TERM),
			kind("Language", FluentUiRegularAL.LOCAL_LANGUAGE_20, Family.TERM),
			kind("Rating", FluentUiRegularMZ.STAR_20, Family.TERM),
			kind("ItemList", FluentUiRegularMZ.TEXT_BULLET_LIST_20, Family.TERM));
	static final int SIZE = 16;

	private EntityIcons() {
	}

	/** The icon of an entity, by its types */
	static Glyph of(Entity entity, EntityListing listing) {
		return KINDS.stream().filter(k -> entity.getTypes().contains(k.type()) || listing.isA(entity, k.type()))
				.map(Kind::glyph).findFirst().orElse(THING);
	}

	/** The icon of a node of the tree */
	static Glyph of(EntityTree.Node node, EntityListing listing) {
		return switch (node.kind()) {
		case ROOT -> CRATE;
		case FOLDER, TYPE -> FOLDER;
		case EXTERNAL -> EXTERNAL;
		case RESOURCE -> RESOURCE;
		case FILE, CONTEXTUAL -> node.entity().map(e -> of(e, listing)).orElse(DOCUMENT);
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
		private final FontIcon fill = new FontIcon(THING.fill());
		private final FontIcon line = new FontIcon(THING.line());
		private Glyph glyph;

		Icon() {
			getStyleClass().add("entity-icon");
			fill.getStyleClass().add("entity-icon-fill");
			line.getStyleClass().add("entity-icon-line");
			fill.setIconSize(SIZE);
			line.setIconSize(SIZE);
			getChildren().addAll(fill, line);
			set(THING);
		}

		void set(Glyph glyph) {
			if (glyph == this.glyph) {
				return;
			}
			if (this.glyph != null) {
				getStyleClass().remove(this.glyph.family().styleClass());
			}
			this.glyph = glyph;
			getStyleClass().add(glyph.family().styleClass());
			fill.setIconCode(glyph.fill());
			line.setIconCode(glyph.line());
		}

		Glyph glyph() {
			return glyph;
		}
	}
}
