/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.tui;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;

import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.soss.SchemaFromJsonLd;

public class ViewerTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void browseEntitiesAndReturnWithoutChangingCrate() throws Exception {
		String json = """
				{"@context":"https://w3id.org/ro/crate/1.1/context","@graph":[
				  {"@id":"ro-crate-metadata.json","@type":"CreativeWork",
				   "about":{"@id":"./"},"conformsTo":{"@id":"https://w3id.org/ro/crate/1.1"}},
				  {"@id":"./","@type":"Dataset","name":"Root"},
				  {"@id":"#alice","@type":"Person","name":"Alice",
				   "description":"A researcher","knows":[{"@id":"#bob"},{"@id":"#eve"}]},
				  {"@id":"#bob","@type":"https://schema.org/Person","name":"Bob","knows":{"@id":"#alice"}}
				]}
				""";
		Path file = temporary.newFile("ro-crate-metadata.json").toPath();
		Files.writeString(file, json);
		CrateSession session = CrateSession.open(file);
		CrateDocument document = session.document();
		var before = document.crate().orElseThrow().toJson().deepCopy();
		DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 24));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Screen screen = new TerminalScreen(terminal)) {
			screen.startScreen();
			Future<?> task = executor.submit(() -> new Viewer(screen, session).run());
			try {
				await(terminal, "ro-crate-metadata.json");
				awaitBottom(terminal, "[Enter] Open  [s] Save  [Esc] Quit");
				terminal.setTerminalSize(new TerminalSize(65, 12));
				awaitBottom(terminal, "[Enter] Open  [s] Save  [Esc] Quit");
				terminal.setTerminalSize(new TerminalSize(100, 24));
				awaitBottom(terminal, "[Enter] Open  [s] Save  [Esc] Quit");
				terminal.addInput(new KeyStroke('s', false, false));
				awaitBottom(terminal, "No changes to save.");
				press(terminal, KeyType.ArrowDown, 3);
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "#eve");
				awaitBottom(terminal, "[Enter] Open  [e] Edit  [s] Save  [Esc] Back");
				String properties = contents(terminal);
				assertTrue(properties.contains("#alice"));
				assertTrue(properties.contains("Person"));
				assertTrue(properties.contains("description"));
				assertTrue(properties.contains("#bob (Person: Bob)"));
				assertTrue(properties.contains("#eve"));
				assertFalse(properties.contains("{\"@id\""));
				assertTrue(properties.contains("Alice [#alice] | Behind: entity list"));
				press(terminal, KeyType.ArrowDown, 4);
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Bob [#bob] | Behind: Alice [#alice]");
				awaitBottom(terminal, "[Esc] Close");
				press(terminal, KeyType.ArrowDown, 3);
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Alice [#alice] | Behind: Bob [#bob]");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Bob [#bob] | Behind: Alice [#alice]");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Alice [#alice] | Behind: entity list");
				// Enter opens Bob again: going back restored the reference's row.
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Bob [#bob] | Behind: Alice [#alice]");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Alice [#alice] | Behind: entity list");
				// An unresolved reference cannot be opened.
				terminal.addInput(new KeyStroke(KeyType.ArrowDown));
				terminal.addInput(new KeyStroke(KeyType.Enter));
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "wpsld-tui | Entities");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				task.get(5, TimeUnit.SECONDS);
				assertEquals(before, document.crate().orElseThrow().toJson());
			} finally {
				task.cancel(true);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void pagesThroughMainListAndReturnsToCurrentEntity() throws Exception {
		Path file = temporary.newFile("paging.json").toPath();
		Files.writeString(file, """
				{"@context":"https://w3id.org/ro/crate/1.1/context","@graph":[
				  {"@id":"ro-crate-metadata.json","@type":"CreativeWork","about":{"@id":"./"}},
				  {"@id":"./","@type":"Dataset","name":"Root"},
				  {"@id":"#alice","@type":"Person","name":"Alice"},
				  {"@id":"#bob","@type":"Person","name":"Bob"}
				]}
				""");
		CrateSession session = CrateSession.open(file);
		DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 24));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Screen screen = new TerminalScreen(terminal)) {
			screen.startScreen();
			Future<?> task = executor.submit(() -> new Viewer(screen, session).run());
			try {
				await(terminal, "Alice [#alice]");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Root [./] | Behind: entity list");
				awaitBottom(terminal, "[PgUp/PgDn] Browse");
				terminal.addInput(new KeyStroke(KeyType.PageUp));
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				await(terminal, "ro-crate-metadata.json");
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				await(terminal, "Alice [#alice] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				await(terminal, "Bob [#bob] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				terminal.addInput(new KeyStroke(KeyType.PageUp));
				await(terminal, "Alice [#alice] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "wpsld-tui | Entities");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Alice [#alice] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				terminal.addInput(new KeyStroke(KeyType.Escape));
				task.get(5, TimeUnit.SECONDS);
			} finally {
				task.cancel(true);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void pagesThroughReferencesAndPromotesNestedEntity() throws Exception {
		Path file = temporary.newFile("references.json").toPath();
		Files.writeString(file, """
				{"@context":"https://w3id.org/ro/crate/1.1/context","@graph":[
				  {"@id":"./","@type":"Dataset","name":"Root",
				   "author":[{"@id":"#alice"},"literal",{"@id":"#missing"},
				     {"@id":"#bob"},{"@id":"#alice"},{"@id":"#carol"}]},
				  {"@id":"#alice","@type":"Person","name":"Alice","knows":{"@id":"#bob"}},
				  {"@id":"#bob","@type":"Person","name":"Bob"},
				  {"@id":"#carol","@type":"Person","name":"Carol"}
				]}
				""");
		CrateSession session = CrateSession.open(file);
		DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 24));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Screen screen = new TerminalScreen(terminal)) {
			screen.startScreen();
			Future<?> task = executor.submit(() -> new Viewer(screen, session).run());
			try {
				await(terminal, "Alice [#alice]");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Root [./] | Behind: entity list");
				press(terminal, KeyType.ArrowDown, 3);
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Alice [#alice] | Behind: Root [./]");
				awaitBottom(terminal, "[v] Top");
				press(terminal, KeyType.ArrowDown, 2);
				terminal.addInput(new KeyStroke('e', false, false));
				await(terminal, "Edit text; Tab to Apply or Cancel");
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				terminal.addInput(new KeyStroke('v', false, false));
				terminal.addInput(new KeyStroke(KeyType.Escape));
				awaitBottom(terminal, "[v] Top");
				await(terminal, "Alice [#alice] | Behind: Root [./]");
				terminal.addInput(new KeyStroke(KeyType.PageUp));
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				await(terminal, "Bob [#bob] | Behind: Root [./]");
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				await(terminal, "Alice [#alice] | Behind: Root [./]");
				terminal.addInput(new KeyStroke(KeyType.PageUp));
				await(terminal, "Bob [#bob] | Behind: Root [./]");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Root [./] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Bob [#bob] | Behind: Root [./]");
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				await(terminal, "Alice [#alice] | Behind: Root [./]");
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				await(terminal, "Carol [#carol] | Behind: Root [./]");
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				terminal.addInput(new KeyStroke(KeyType.PageUp));
				await(terminal, "Alice [#alice] | Behind: Root [./]");
				press(terminal, KeyType.ArrowDown, 3);
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Bob [#bob] | Behind: Alice [#alice]");
				terminal.addInput(new KeyStroke('v', false, false));
				await(terminal, "Bob [#bob] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.PageUp));
				await(terminal, "Alice [#alice] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.PageDown));
				await(terminal, "Bob [#bob] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "wpsld-tui | Entities");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Bob [#bob] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				terminal.addInput(new KeyStroke(KeyType.Escape));
				task.get(5, TimeUnit.SECONDS);
			} finally {
				task.cancel(true);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void listsByRootTypeAndNameAndFiltersByTypeHierarchy() throws Exception {
		CrateDocument document = new CrateReader().read(new ByteArrayInputStream("""
				{"@context":["https://w3id.org/ro/crate/1.1/context",
				  {"ex":"https://example.org/","human":"http://schema.org/Person"}],"@graph":[
				  {"@id":"./","@type":"Dataset","name":"Root"},
				  {"@id":"#z","@type":"human","name":"Zoe"},
				  {"@id":"#unknown","@type":"ex:Custom","name":"Other"},
				  {"@id":"#b","@type":"https://schema.org/Person","name":"Bob"},
				  {"@id":"#org","@type":"CollegeOrUniversity","name":"College"},
				  {"@id":"#file","@type":"File","name":"File"}
				]}
				""".getBytes(StandardCharsets.UTF_8)), "test");
		TypeFilter filter = new TypeFilter(document, new SchemaFromJsonLd());
		var all = document.crate().orElseThrow().getEntities();
		var sorted = filter.ordered(all);
		assertEquals("./", sorted.get(0).getId());
		assertTrue(sorted.indexOf(document.entity("#file").orElseThrow())
				< sorted.indexOf(document.entity("#b").orElseThrow()));
		assertTrue(sorted.indexOf(document.entity("#b").orElseThrow())
				< sorted.indexOf(document.entity("#z").orElseThrow()));
		var choices = filter.choices(all, "university");
		assertTrue(choices.stream().anyMatch(c -> c.type().name().equals("Thing") && c.depth() == 0));
		var university = choices.stream().filter(c -> c.type().name().equals("CollegeOrUniversity"))
				.findFirst().orElseThrow();
		assertTrue(university.depth() > 0);
		var organization = filter.choices(all, "Organization").stream()
				.filter(c -> c.type().name().equals("Organization")).findFirst().orElseThrow().type();
		assertTrue(filter.matches(document.entity("#org").orElseThrow(), organization));
		assertFalse(filter.matches(document.entity("#b").orElseThrow(), organization));
		var person = filter.choices(all, "Person").stream().filter(c -> c.type().name().equals("Person"))
				.findFirst().orElseThrow().type();
		assertTrue(filter.matches(document.entity("#z").orElseThrow(), person));
		assertTrue(filter.matches(document.entity("#b").orElseThrow(), person));
		var custom = filter.choices(all, "ex:custom").stream()
				.filter(c -> c.type().name().equals("ex:Custom")).findFirst().orElseThrow().type();
		assertTrue(filter.matches(document.entity("#unknown").orElseThrow(), custom));
	}

	@Test
	public void filtersMainListAndCanPromoteAReferenceOutsideFilter() throws Exception {
		Path file = temporary.newFile("filter.json").toPath();
		Files.writeString(file, """
				{"@context":"https://w3id.org/ro/crate/1.1/context","@graph":[
				  {"@id":"./","@type":"Dataset","name":"Root"},
				  {"@id":"#alice","@type":"Person","name":"Alice","affiliation":{"@id":"#org"}},
				  {"@id":"#bob","@type":"Person","name":"Bob"},
				  {"@id":"#org","@type":"Organization","name":"University"}
				]}
				""");
		CrateSession session = CrateSession.open(file);
		DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 24));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Screen screen = new TerminalScreen(terminal)) {
			screen.startScreen();
			Future<?> task = executor.submit(() -> new Viewer(screen, session).run());
			try {
				await(terminal, "Alice [#alice]");
				terminal.addInput(new KeyStroke('t', false, false));
				await(terminal, "Search types; Tab to list");
				awaitBottom(terminal, "[Tab] List  [Enter] Choose  [Esc] Cancel");
				type(terminal, "no-such-type");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Search types; Tab to list");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Type: All");
				terminal.addInput(new KeyStroke('t', false, false));
				await(terminal, "Search types; Tab to list");
				type(terminal, "Person");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Type: Person");
				assertFalse(contents(terminal).contains("University [#org]"));
				terminal.addInput(new KeyStroke('/', false, false));
				type(terminal, "missing");
				await(terminal, "missing");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Alice [#alice]");
				terminal.addInput(new KeyStroke('/', false, false));
				type(terminal, "alice");
				await(terminal, "alice");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Alice [#alice] | Behind: entity list");
				press(terminal, KeyType.ArrowDown, 3);
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "University [#org] | Behind: Alice [#alice]");
				terminal.addInput(new KeyStroke('v', false, false));
				await(terminal, "University [#org] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Type: All");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "University [#org] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Type: All");
				terminal.addInput(new KeyStroke('t', false, false));
				await(terminal, "Search types; Tab to list");
				type(terminal, "Organization");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Type: Organization");
				terminal.addInput(new KeyStroke('t', false, false));
				await(terminal, "Search types; Tab to list");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Type: All");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				task.get(5, TimeUnit.SECONDS);
			} finally {
				task.cancel(true);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void previewsAreSingleLineAndDoNotSplitUnicode() {
		assertEquals("line one line two", Values.preview("line one\nline two", 30));
		assertEquals("a😀...", Values.preview("a😀bcdef", 5));
	}

	@Test
	public void referencesAndLiteralsHaveReadablePreviews() throws Exception {
		String json = """
				{"@context":["https://w3id.org/ro/crate/1.1/context",
				  {"ex":"https://example.org/people/"}],"@graph":[
				  {"@id":"./","@type":"Dataset"},
				  {"@id":"https://example.org/people/alice","@type":["schema:Person","http://example.org/#Author"],"name":"Alice"},
				  {"@id":"https://example.org/people/unknown","@type":"Person"}
				]}
				""";
		CrateDocument document = new CrateReader().read(
				new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test");
		ObjectMapper mapper = new ObjectMapper();
		assertEquals("ex:alice (Person, Author: Alice)",
				Values.valueText(mapper.readTree("{\"@id\":\"ex:alice\"}"), document));
		assertEquals("unknown (Person)",
				Values.valueText(mapper.readTree("{\"@id\":\"https://example.org/people/unknown\"}"), document));
		assertEquals("missing", Values.valueText(mapper.readTree("{\"@id\":\"https://example.org/missing\"}"), document));
		assertEquals("Hello (en)", Values.valueText(mapper.readTree("{\"@value\":\"Hello\",\"@language\":\"en\"}"), document));
		assertEquals("one, 42", Values.valueText(mapper.readTree("[\"one\",42]"), document));
		assertEquals("code: abc", Values.valueText(mapper.readTree("{\"code\":\"abc\"}"), document));
	}

	@Test
	public void rejectsMissingOrUnreadableCrate() {
		assertEquals(2, Main.run(new String[0]));
		assertEquals(2, Main.run(new String[] { "this-crate-does-not-exist" }));
	}

	@Test
	public void opensLegacyMetadataInFolderAndPrefersCurrentName() throws Exception {
		Path folder = temporary.newFolder().toPath();
		String crate = "{\"@graph\":[{\"@id\":\"./\",\"@type\":\"Dataset\"}]}";
		Files.writeString(folder.resolve("ro-crate-metadata.jsonld"), crate);
		assertTrue(Main.read(folder).crate().isPresent());
		Files.writeString(folder.resolve("ro-crate-metadata.json"), "not JSON");
		assertTrue(Main.read(folder).crate().isEmpty());
	}

	@Test
	public void rangeIncludesSubclassesAndUnknownRangesOfferAll() throws Exception {
		String json = """
				{"@context":"https://w3id.org/ro/crate/1.1/context","@graph":[
				  {"@id":"./","@type":"Dataset","author":{"@id":"#alice"}},
				  {"@id":"#alice","@type":"Person","name":"Alice"},
				  {"@id":"#university","@type":"CollegeOrUniversity","name":"University"},
				  {"@id":"#event","@type":"Event","name":"Event"}
				]}
				""";
		CrateDocument document = new CrateReader().read(
				new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test");
		ReferencePicker picker = new ReferencePicker(document, new SchemaFromJsonLd());
		var authors = picker.candidates("author").stream().map(e -> e.getId()).toList();
		assertTrue(authors.toString(), authors.contains("#alice"));
		assertTrue(authors.toString(), authors.contains("#university"));
		assertFalse(authors.toString(), authors.contains("#event"));
		assertEquals(4, picker.candidates("unknownProperty").size());
	}

	@Test
	public void searchFindsIdentifiersAndNamesInMultipleLanguages() throws Exception {
		CrateDocument document = new CrateReader().read(new ByteArrayInputStream("""
				{"@graph":[{"@id":"./","@type":"Dataset","name":[
				  {"@value":"Hello","@language":"en"},{"@value":"Bonjour","@language":"fr"}]}]}
				""".getBytes(StandardCharsets.UTF_8)), "test");
		var entity = document.crate().orElseThrow().getRoot();
		assertTrue(ReferencePicker.matchesQuery(entity, "./"));
		assertTrue(ReferencePicker.matchesQuery(entity, "BONJOUR"));
		assertFalse(ReferencePicker.matchesQuery(entity, "missing"));
	}

	@Test
	public void cannotSavePartiallyReadCrate() throws Exception {
		Path file = temporary.newFile("partial.json").toPath();
		String json = """
				{"@context":"https://w3id.org/ro/crate/1.1/context","@graph":[
				  {"@id":"./","@type":"Dataset","name":"Root"}, 42
				]}
				""";
		Files.writeString(file, json);
		CrateSession session = CrateSession.open(file);
		assertTrue(session.document().hasErrors());
		session.document().crate().orElseThrow().getRoot().set("name",
				java.util.Optional.of(com.fasterxml.jackson.databind.node.TextNode.valueOf("changed")));
		try {
			session.save();
			fail("Saved a partially read crate");
		} catch (java.io.IOException expected) {
			assertTrue(expected.getMessage().contains("errors when read"));
		}
		assertEquals(json, Files.readString(file));
	}

	@Test
	public void editingAndSavingRequiresConfirmationAndPreservesConcurrentChanges() throws Exception {
		Path file = temporary.newFile("metadata.json").toPath();
		String json = """
				{"@context":"https://w3id.org/ro/crate/1.1/context","@graph":[
				  {"@id":"./","@type":"Dataset","name":"Root",
				   "author":[{"@id":"#alice"},{"@id":"#missing"},{"@id":"#event"}]},
				  {"@id":"#alice","@type":"Person","name":"Alice"},
				  {"@id":"#bob","@type":"Person","name":"Bob"},
				  {"@id":"#event","@type":"Event","name":"Event"}
				]}
				""";
		Files.writeString(file, json);
		CrateSession session = CrateSession.open(file);
		DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 30));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Screen screen = new TerminalScreen(terminal)) {
			screen.startScreen();
			Future<?> task = executor.submit(() -> new Viewer(screen, session).run());
			try {
				await(terminal, "Alice [#alice]");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Behind: entity list");
				press(terminal, KeyType.ArrowDown, 2);
				terminal.addInput(new KeyStroke('e', false, false));
				await(terminal, "Edit text; Tab to Apply or Cancel");
				awaitBottom(terminal, "[Tab] Buttons  [Enter] New line  [Esc] Cancel");
				terminal.setTerminalSize(new TerminalSize(80, 18));
				awaitBottom(terminal, "[Tab] Buttons  [Enter] New line  [Esc] Cancel");
				terminal.setTerminalSize(new TerminalSize(100, 30));
				awaitBottom(terminal, "[Tab] Buttons  [Enter] New line  [Esc] Cancel");
				terminal.addInput(new KeyStroke(KeyType.End));
				terminal.addInput(new KeyStroke('!', false, false));
				terminal.addInput(new KeyStroke(KeyType.Tab));
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Root!");
				assertEquals(json, Files.readString(file));
				terminal.addInput(new KeyStroke(KeyType.ArrowDown));
				terminal.addInput(new KeyStroke('e', false, false));
				await(terminal, "Search ID or name");
				awaitBottom(terminal, "[Tab] Focus  [Space] Select  [Enter] Apply  [Esc] Cancel");
				terminal.setTerminalSize(new TerminalSize(80, 18));
				awaitBottom(terminal, "[Tab] Focus  [Space] Select  [Enter] Apply  [Esc] Cancel");
				terminal.setTerminalSize(new TerminalSize(100, 30));
				awaitBottom(terminal, "[Tab] Focus  [Space] Select  [Enter] Apply  [Esc] Cancel");
				type(terminal, "Bob");
				await(terminal, "Bob [#bob]");
				terminal.addInput(new KeyStroke(KeyType.Tab));
				terminal.addInput(new KeyStroke(' ', false, false));
				press(terminal, KeyType.Backspace, 3);
				await(terminal, "Alice [#alice]");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "#bob (Person: Bob)");
				var authors = session.document().crate().orElseThrow().getRoot().getAll("author");
				assertEquals(4, authors.size());
				assertEquals(java.util.List.of("#alice", "#missing", "#event", "#bob"),
						authors.stream().map(v -> v.path("@id").asText()).toList());
				assertEquals(json, Files.readString(file));
				terminal.addInput(new KeyStroke('s', false, false));
				awaitBottom(terminal, "Saved to " + file);
				terminal.addInput(new KeyStroke(KeyType.ArrowDown));
				awaitBottom(terminal, "[Enter] Open  [e] Edit  [s] Save  [Esc] Back");
				terminal.addInput(new KeyStroke(KeyType.ArrowDown));
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Behind: Root! [./]");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Behind: entity list");
				String saved = Files.readString(file);
				assertTrue(saved.contains("Root!"));
				assertTrue(saved.contains("#alice"));
				assertTrue(saved.contains("#bob"));
				Files.writeString(file, "external change");
				session.document().crate().orElseThrow().getRoot().set("name",
						java.util.Optional.of(com.fasterxml.jackson.databind.node.TextNode.valueOf("changed")));
				try {
					session.save();
					fail("Overwrote an external change");
				} catch (java.io.IOException expected) {
					assertTrue(expected.getMessage().contains("changed on disk"));
				}
				assertEquals("external change", Files.readString(file));
				terminal.addInput(new KeyStroke('s', false, false));
				await(terminal, "changed on disk");
				awaitBottom(terminal, "[Tab] Select  [Enter] Confirm  [Esc] Cancel");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				awaitBottom(terminal, "[Esc] Back");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Entities *");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Discard unsaved changes?");
				awaitBottom(terminal, "[Tab] Select  [Enter] Confirm  [Esc] Cancel");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				task.get(5, TimeUnit.SECONDS);
			} finally {
				task.cancel(true);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void savesFromEntityListAndNextKeyStillMovesSelection() throws Exception {
		Path file = temporary.newFile("list-save.json").toPath();
		Files.writeString(file, """
				{"@context":"https://w3id.org/ro/crate/1.1/context","@graph":[
				  {"@id":"./","@type":"Dataset","name":"Root"},
				  {"@id":"#alice","@type":"Person","name":"Alice"}
				]}
				""");
		CrateSession session = CrateSession.open(file);
		DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 24));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Screen screen = new TerminalScreen(terminal)) {
			screen.startScreen();
			Future<?> task = executor.submit(() -> new Viewer(screen, session).run());
			try {
				await(terminal, "Alice [#alice]");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Behind: entity list");
				press(terminal, KeyType.ArrowDown, 2);
				terminal.addInput(new KeyStroke('e', false, false));
				awaitBottom(terminal, "[Enter] New line");
				terminal.addInput(new KeyStroke(KeyType.End));
				terminal.addInput(new KeyStroke('!', false, false));
				terminal.addInput(new KeyStroke(KeyType.Tab));
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Root!");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				await(terminal, "Entities *");
				terminal.addInput(new KeyStroke('s', false, false));
				awaitBottom(terminal, "Saved to " + file);
				assertTrue(Files.readString(file).contains("Root!"));
				terminal.addInput(new KeyStroke(KeyType.ArrowDown));
				awaitBottom(terminal, "[Enter] Open  [s] Save  [Esc] Quit");
				terminal.addInput(new KeyStroke(KeyType.Enter));
				await(terminal, "Alice [#alice] | Behind: entity list");
				terminal.addInput(new KeyStroke(KeyType.Escape));
				terminal.addInput(new KeyStroke(KeyType.Escape));
				task.get(5, TimeUnit.SECONDS);
			} finally {
				task.cancel(true);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	private interface Steps {
		void run(DefaultVirtualTerminal terminal) throws Exception;
	}

	/** Run the viewer on a crate, do the steps, and quit from the entity list */
	private void viewing(CrateSession session, Steps steps) throws Exception {
		DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 24));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Screen screen = new TerminalScreen(terminal)) {
			screen.startScreen();
			Future<?> task = executor.submit(() -> new Viewer(screen, session).run());
			try {
				awaitBottom(terminal, "[Enter] Open");
				steps.run(terminal);
				terminal.addInput(new KeyStroke(KeyType.Escape));
				task.get(5, TimeUnit.SECONDS);
			} finally {
				task.cancel(true);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	private CrateSession crate(String name, String graph) throws Exception {
		Path file = temporary.newFile(name).toPath();
		Files.writeString(file, """
				{"@context":"https://w3id.org/ro/crate/1.1/context","@graph":[
				  {"@id":"ro-crate-metadata.json","@type":"CreativeWork",
				   "about":{"@id":"./"},"conformsTo":{"@id":"https://w3id.org/ro/crate/1.1"}},
				""" + graph + "]}");
		return CrateSession.open(file);
	}

	@Test
	public void checkListsProblemsAndOpensTheEntityOfOne() throws Exception {
		CrateSession session = crate("ro-crate-metadata.json", """
				{"@id":"./","@type":"Dataset","name":"Root"},
				{"@id":"#alice","@type":"Person","name":"Alice"}
				""");
		viewing(session, terminal -> {
			terminal.addInput(new KeyStroke('c', false, false));
			await(terminal, "3 problems.");
			assertTrue(contents(terminal).contains("• The root data entity must have a license"));
			awaitBottom(terminal, "[Enter] Open the entity  [Esc] Close");
			terminal.addInput(new KeyStroke(KeyType.Escape));
			awaitBottom(terminal, "[Enter] Open  [s] Save  [Esc] Quit");
			assertFalse(contents(terminal).contains("3 problems."));
			terminal.addInput(new KeyStroke('c', false, false));
			await(terminal, "3 problems.");
			terminal.addInput(new KeyStroke(KeyType.Enter));
			await(terminal, "Root [./] | Behind: entity list");
			terminal.addInput(new KeyStroke(KeyType.Escape));
			awaitBottom(terminal, "[Enter] Open  [s] Save  [Esc] Quit");
		});
	}

	@Test
	public void checkOfACrateThatPassesSaysSo() throws Exception {
		CrateSession session = CrateSession.open(Path.of("..", "ui", "src", "test", "resources", "example-crate"));
		viewing(session, terminal -> {
			terminal.addInput(new KeyStroke('c', false, false));
			await(terminal, "Passed.");
			terminal.addInput(new KeyStroke(KeyType.Enter));
			awaitBottom(terminal, "[Enter] Open  [s] Save  [Esc] Quit");
			assertFalse(session.dirty());
		});
	}

	@Test
	public void repairShowsWhatItWouldDoAndAppliesItOnlyWhenAsked() throws Exception {
		CrateSession session = crate("ro-crate-metadata.json", """
				{"@id":"./","@type":"Dataset","name":"Root"},
				{"@id":"#alice","@type":"Person","name":"Alice","affiliation":{"name":"Example University"}}
				""");
		var before = session.document().crate().orElseThrow().toJson().deepCopy();
		viewing(session, terminal -> {
			terminal.addInput(new KeyStroke('r', false, false));
			await(terminal, "Repair Crate would do this");
			awaitBottom(terminal, "[Esc] Cancel");
			terminal.addInput(new KeyStroke(KeyType.Escape));
			awaitBottom(terminal, "[Enter] Open  [s] Save  [Esc] Quit");
			assertEquals(before, session.document().crate().orElseThrow().toJson());
			assertFalse(session.dirty());
			terminal.addInput(new KeyStroke('r', false, false));
			await(terminal, "Repair Crate would do this");
			assertEquals(before, session.document().crate().orElseThrow().toJson());
			terminal.addInput(new KeyStroke(KeyType.Tab));
			terminal.addInput(new KeyStroke(KeyType.Enter));
			awaitBottom(terminal, "Repaired; [s] to save");
			await(terminal, "Entities *");
			assertTrue(session.dirty());
			assertFalse(session.document().crate().orElseThrow().toJson().equals(before));
			assertFalse(session.document().crate().orElseThrow().toJson().toString().contains("\"affiliation\":{\"name\""));
			terminal.addInput(new KeyStroke('r', false, false));
			await(terminal, "Nothing to repair.");
			terminal.addInput(new KeyStroke(KeyType.Enter));
			awaitBottom(terminal, "[Enter] Open  [s] Save  [Esc] Quit");
			terminal.addInput(new KeyStroke('s', false, false));
			awaitBottom(terminal, "Saved to ");
			assertFalse(session.dirty());
		});
	}

	private static void type(DefaultVirtualTerminal terminal, String text) {
		for (char c : text.toCharArray()) terminal.addInput(new KeyStroke(c, false, false));
	}

	private static void await(DefaultVirtualTerminal terminal, String text) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		do {
			if (contents(terminal).contains(text)) {
				return;
			}
			Thread.sleep(20);
		} while (System.nanoTime() < deadline);
		fail("Did not see " + text + " in terminal:\n" + contents(terminal));
	}

	private static void awaitBottom(DefaultVirtualTerminal terminal, String text) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		do {
			String[] lines = contents(terminal).split("\n", -1);
			if (lines[lines.length - 2].contains(text)) return;
			Thread.sleep(20);
		} while (System.nanoTime() < deadline);
		fail("Did not see " + text + " on bottom row:\n" + contents(terminal));
	}

	private static void press(DefaultVirtualTerminal terminal, KeyType key, int times) {
		for (int i = 0; i < times; i++) {
			terminal.addInput(new KeyStroke(key));
		}
	}

	private static String contents(DefaultVirtualTerminal terminal) {
		StringBuilder text = new StringBuilder();
		TerminalSize size = terminal.getTerminalSize();
		for (int row = 0; row < size.getRows(); row++) {
			for (int col = 0; col < size.getColumns(); col++) {
				text.append(terminal.getCharacter(col, row).getCharacterString());
			}
			text.append('\n');
		}
		return text.toString();
	}
}
