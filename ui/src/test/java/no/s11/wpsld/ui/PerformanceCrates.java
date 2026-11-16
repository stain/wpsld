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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The crates {@link TestEditorPerformance} times the editor on, made in
 * advance so that every run times the same crates, each hard on the editor
 * in its own way (see each method). Made again, the same, by
 * <code>./gradlew :ui:performanceCrates</code> into
 * <code>ui/src/test/resources/performance/</code>; only their metadata, as
 * their files need not be there to be listed, selected and edited. All
 * names, people and places are made up.
 */
public final class PerformanceCrates {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final String[] GIVEN = { "Ada", "Bo", "Chen", "Dara", "Eli", "Femi", "Gro", "Hana", "Ivo", "Juno",
			"Kai", "Lea", "Mio", "Nils", "Oda", "Pia", "Quinn", "Ravi", "Siri", "Tove" };
	private static final String[] FAMILY = { "Aalto", "Berg", "Costa", "Dahl", "Eze", "Fjell", "Garcia", "Holm",
			"Ito", "Jensen", "Kowalski", "Lund", "Moreau", "Nakamura", "Olsen", "Park", "Quist", "Rossi", "Strand",
			"Tanaka" };
	private static final String[] FORMATS = { "text/csv", "image/tiff", "application/pdf", "text/plain",
			"application/json" };
	private static final String[] EXTENSIONS = { "csv", "tif", "pdf", "txt", "json" };

	private PerformanceCrates() {
	}

	public static void main(String[] args) throws IOException {
		Path dir = Path.of(args[0]);
		write(dir.resolve("large"), large());
		write(dir.resolve("wide"), wide());
		write(dir.resolve("deep"), deep());
		write(dir.resolve("variables"), variables());
	}

	private static void write(Path dir, List<ObjectNode> graph) throws IOException {
		Files.createDirectories(dir);
		ObjectNode document = JSON.objectNode();
		document.put("@context", "https://w3id.org/ro/crate/1.2/context");
		document.putArray("@graph").addAll(graph);
		new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(
				dir.resolve("ro-crate-metadata.json").toFile(), document);
	}

	private static ObjectNode entity(List<ObjectNode> graph, String id, String type) {
		ObjectNode entity = JSON.objectNode();
		entity.put("@id", id);
		entity.put("@type", type);
		graph.add(entity);
		return entity;
	}

	private static ObjectNode ref(String id) {
		return JSON.objectNode().put("@id", id);
	}

	private static ObjectNode root(List<ObjectNode> graph, String name, String description) {
		ObjectNode descriptor = entity(graph, "ro-crate-metadata.json", "CreativeWork");
		descriptor.set("conformsTo", ref("https://w3id.org/ro/crate/1.2"));
		descriptor.set("about", ref("./"));
		ObjectNode root = entity(graph, "./", "Dataset");
		root.put("name", name);
		root.put("description", description);
		root.put("datePublished", "2026-11-16");
		root.set("license", ref("https://spdx.org/licenses/CC-BY-4.0"));
		ObjectNode license = entity(graph, "https://spdx.org/licenses/CC-BY-4.0", "CreativeWork");
		license.put("name", "Creative Commons Attribution 4.0 International");
		return root;
	}

	/** A long description in Markdown, as the preview renders it */
	private static String markdown(Random random, int paragraphs) {
		StringBuilder text = new StringBuilder("# About this crate\n\n");
		for (int p = 0; p < paragraphs; p++) {
			if (p % 5 == 0) {
				text.append("## Part ").append(p / 5 + 1).append("\n\n");
			}
			for (int s = 0; s < 6; s++) {
				text.append(GIVEN[random.nextInt(GIVEN.length)]).append(" measured *")
						.append(FAMILY[random.nextInt(FAMILY.length)].toLowerCase()).append("* samples on day ")
						.append(random.nextInt(365) + 1).append(", see **table ").append(random.nextInt(99) + 1)
						.append("**. ");
			}
			text.append("\n\n");
			if (p % 7 == 3) {
				text.append("- first item\n- second item, with `code`\n- third item\n\n");
			}
		}
		return text.toString();
	}

	private static String person(Random random, List<ObjectNode> graph, int n, int organizations) {
		String id = "#person-" + n;
		ObjectNode person = entity(graph, id, "Person");
		String given = GIVEN[n % GIVEN.length];
		String family = FAMILY[(n / GIVEN.length) % FAMILY.length];
		person.put("name", given + " " + family + (n >= 400 ? " " + n : ""));
		person.put("givenName", given);
		person.put("familyName", family);
		if (organizations > 0) {
			person.set("affiliation", ref("#org-" + random.nextInt(organizations)));
		}
		return id;
	}

	/**
	 * Some 3 200 entities of many types referring to each other, as a large
	 * real crate (e.g. a collection or a workflow run): 120 folders of 1 500
	 * files, 800 people in 80 organisations, 300 places with coordinates,
	 * 100 actions; each file by one to three people, some about a place
	 */
	static List<ObjectNode> large() {
		Random random = new Random(1);
		List<ObjectNode> graph = new ArrayList<>();
		ObjectNode root = root(graph, "Large crate", markdown(random, 20));
		ArrayNode keywords = root.putArray("keywords");
		for (int k = 0; k < 200; k++) {
			keywords.add("keyword " + k);
		}
		for (int o = 0; o < 80; o++) {
			ObjectNode org = entity(graph, "#org-" + o, "Organization");
			org.put("name", FAMILY[o % FAMILY.length] + " Institute " + o);
			org.put("url", "https://example.org/institute-" + o);
		}
		for (int p = 0; p < 800; p++) {
			person(random, graph, p, 80);
		}
		ArrayNode authors = root.putArray("author");
		for (int p = 0; p < 5; p++) {
			authors.add(ref("#person-" + p));
		}
		for (int p = 0; p < 300; p++) {
			ObjectNode place = entity(graph, "#place-" + p, "Place");
			place.put("name", "Site " + p);
			place.set("geo", ref("#geo-" + p));
			ObjectNode geo = entity(graph, "#geo-" + p, "GeoCoordinates");
			geo.put("latitude", Math.round((random.nextDouble() * 140 - 70) * 1e5) / 1e5);
			geo.put("longitude", Math.round((random.nextDouble() * 360 - 180) * 1e5) / 1e5);
		}
		ArrayNode rootParts = root.putArray("hasPart");
		List<String> files = new ArrayList<>();
		for (int d = 0; d < 30; d++) {
			String top = "data/d" + d + "/";
			rootParts.add(ref(top));
			ObjectNode folder = entity(graph, top, "Dataset");
			folder.put("name", "Data " + d);
			ArrayNode folderParts = folder.putArray("hasPart");
			for (int s = 0; s < 3; s++) {
				String sub = top + "s" + s + "/";
				folderParts.add(ref(sub));
				ObjectNode subfolder = entity(graph, sub, "Dataset");
				subfolder.put("name", "Data " + d + "." + s);
				ArrayNode parts = subfolder.putArray("hasPart");
				for (int f = 0; f < 17; f++) {
					int kind = random.nextInt(FORMATS.length);
					String id = sub + "file-" + f + "." + EXTENSIONS[kind];
					parts.add(ref(id));
					files.add(id);
					ObjectNode file = entity(graph, id, "File");
					file.put("name", "File " + d + "." + s + "." + f);
					file.put("description", "Measurements of batch " + random.nextInt(1000));
					file.put("encodingFormat", FORMATS[kind]);
					file.put("contentSize", String.valueOf(random.nextInt(10_000_000)));
					file.put("dateModified", "2026-0" + (random.nextInt(9) + 1) + "-1" + random.nextInt(10));
					ArrayNode by = file.putArray("author");
					for (int a = 0, n = random.nextInt(3) + 1; a < n; a++) {
						by.add(ref("#person-" + random.nextInt(800)));
					}
					if (random.nextInt(3) == 0) {
						file.set("about", ref("#place-" + random.nextInt(300)));
					}
				}
			}
		}
		for (int s = 0; s < 10; s++) {
			ObjectNode software = entity(graph, "#software-" + s, "SoftwareApplication");
			software.put("name", "Tool " + s);
			software.put("version", "1." + s);
		}
		ArrayNode mentions = root.putArray("mentions");
		for (int a = 0; a < 100; a++) {
			String id = "#action-" + a;
			mentions.add(ref(id));
			ObjectNode action = entity(graph, id, "CreateAction");
			action.put("name", "Run " + a);
			action.set("instrument", ref("#software-" + random.nextInt(10)));
			action.set("agent", ref("#person-" + random.nextInt(800)));
			action.putArray("object").add(ref(files.get(random.nextInt(files.size()))))
					.add(ref(files.get(random.nextInt(files.size()))));
			action.set("result", ref(files.get(random.nextInt(files.size()))));
			action.put("endTime", "2026-10-0" + (random.nextInt(9) + 1) + "T12:00:00Z");
		}
		return graph;
	}

	/**
	 * A root with 4 000 files as its parts at once, all by the same person
	 * (whose preview lists 4 000 referring to them), 500 keywords and a
	 * description of some 40 kB
	 */
	static List<ObjectNode> wide() {
		Random random = new Random(2);
		List<ObjectNode> graph = new ArrayList<>();
		ObjectNode root = root(graph, "Wide crate", markdown(random, 60));
		ArrayNode keywords = root.putArray("keywords");
		for (int k = 0; k < 500; k++) {
			keywords.add("keyword " + k);
		}
		person(random, graph, 0, 0);
		root.set("author", ref("#person-0"));
		ArrayNode parts = root.putArray("hasPart");
		for (int f = 0; f < 4000; f++) {
			String id = "file-" + f + ".txt";
			parts.add(ref(id));
			ObjectNode file = entity(graph, id, "File");
			file.put("name", "File " + f);
			file.put("encodingFormat", "text/plain");
			file.set("author", ref("#person-0"));
		}
		return graph;
	}

	/**
	 * Folders ten deep, two in each (2 047 folders), a file in each of the
	 * deepest, for the trees by folder and by part
	 */
	static List<ObjectNode> deep() {
		List<ObjectNode> graph = new ArrayList<>();
		ObjectNode root = root(graph, "Deep crate", "Folders ten deep.");
		deep(graph, root, "", 0);
		return graph;
	}

	private static void deep(List<ObjectNode> graph, ObjectNode parent, String path, int depth) {
		ArrayNode parts = parent.putArray("hasPart");
		if (depth == 10) {
			String id = path + "leaf.txt";
			parts.add(ref(id));
			entity(graph, id, "File").put("name", "Leaf " + path);
			return;
		}
		for (String branch : List.of("a", "b")) {
			String id = path + branch + depth + "/";
			parts.add(ref(id));
			ObjectNode folder = entity(graph, id, "Dataset");
			folder.put("name", "Folder " + id);
			deep(graph, folder, id, depth + 1);
		}
	}

	/**
	 * 1 000 variables measured (PropertyValue, as a table's columns), each
	 * referring to one of 20 units, for copying and pasting them all
	 */
	static List<ObjectNode> variables() {
		Random random = new Random(4);
		List<ObjectNode> graph = new ArrayList<>();
		ObjectNode root = root(graph, "Variables", "A table of 1000 columns.");
		for (int u = 0; u < 20; u++) {
			ObjectNode unit = entity(graph, "#unit-" + u, "DefinedTerm");
			unit.put("name", "Unit " + u);
			unit.put("termCode", "U" + u);
		}
		ArrayNode measured = root.putArray("variableMeasured");
		for (int v = 0; v < 1000; v++) {
			String id = "#variable-" + v;
			measured.add(ref(id));
			ObjectNode variable = entity(graph, id, "PropertyValue");
			variable.put("name", "column_" + v);
			variable.put("propertyID", "https://example.org/variables/" + v);
			variable.put("description", "Column " + v + " of the table, measured hourly");
			variable.put("minValue", random.nextInt(100));
			variable.put("maxValue", 100 + random.nextInt(1000));
			variable.set("unitCode", ref("#unit-" + random.nextInt(20)));
		}
		return graph;
	}
}
