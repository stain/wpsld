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
package no.s11.wpsld.cli;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import no.s11.wpsld.masp.ProfileCache;
import picocli.CommandLine;

/** wpsld-cli's commands on crates made for each test, with the bundled profiles only and offline */
public class TestCli {
	/** A crate with problems: no licence, a type and an ORCID iD written otherwise, an object without @id */
	private static final String BROKEN = String.join("\r\n",
			"{\"@context\": \"https://w3id.org/ro/crate/1.2/context\", \"@graph\": [",
			" {\"@id\": \"ro-crate-metadata.json\", \"@type\": \"CreativeWork\", \"about\": {\"@id\": \"./\"},",
			"  \"conformsTo\": {\"@id\": \"https://w3id.org/ro/crate/1.2\"}},",
			" {\"@id\": \"./\", \"@type\": \"Dataset\", \"name\": \"Broken\", \"description\": \"On purpose\",",
			"  \"datePublished\": \"2026-11-16\",",
			"  \"conformsTo\": [{\"@id\": \"https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate\"},",
			"   {\"@id\": \"https://example.org/no-such-profile\"}],",
			"  \"author\": {\"@id\": \"http://orcid.org/0000-0002-1825-0097\"},",
			"  \"citation\": \"{\\\"@id\\\": \\\"https://doi.org/10.5281/zenodo.1234\\\"}\",",
			"  \"funder\": {\"@type\": \"Organization\", \"name\": \"A funder\"}},",
			" {\"@id\": \"http://orcid.org/0000-0002-1825-0097\", \"@type\": \"http://schema.org/Person\", \"name\": \"Josiah Carberry\"}",
			"]}", "");

	private Path folder;
	private Path metadata;
	private final StringWriter out = new StringWriter();
	private final StringWriter err = new StringWriter();

	@Before
	public void crate() throws Exception {
		folder = Files.createTempDirectory("wpsld-cli");
		metadata = Files.writeString(folder.resolve("ro-crate-metadata.json"), BROKEN);
	}

	private int run(String... args) {
		CommandLine line = Main.commandLine(new Main(ProfileCache::bundled));
		line.setOut(new PrintWriter(out));
		line.setErr(new PrintWriter(err));
		return line.execute(args);
	}

	/** Files in the folder that the crate doesn't describe: warnings, which don't fail the check */
	@Test
	public void filesNotInTheCrateWarned() throws Exception {
		assertEquals(0, run("repair", folder.toString()));
		Files.writeString(metadata, Files.readString(metadata).replace("\"datePublished\"",
				"\"license\": {\"@id\": \"http://spdx.org/licenses/CC0-1.0\"}, \"datePublished\""));
		out.getBuffer().setLength(0);
		assertEquals(0, run("check", "--offline", folder.toString()));
		assertFalse(out.toString(), out.toString().contains("Warning:"));
		Files.writeString(folder.resolve("notes.txt"), "Not in the crate");
		out.getBuffer().setLength(0);
		assertEquals(0, run("check", "--offline", folder.toString()));
		assertTrue(out.toString(), out.toString().contains("Warning: notes.txt is in the crate's folder, but not in the crate"));
		assertTrue(out.toString().contains("Passed. 1 warning."));
	}

	@Test
	public void checkFindsProblemsByProfile() throws Exception {
		assertEquals(1, run("check", "--offline", folder.toString()));
		String text = out.toString();
		// The bundled profile, though written without its trailing slash
		assertTrue(text, text.contains("Profiles: RO-Crate Machine Actionable Profile"));
		assertTrue(text, text.contains("Profile not found: https://example.org/no-such-profile"));
		assertTrue(text, text.contains("• The root data entity must have a license"));
		assertTrue(text, text.contains("Warning: ./ funder has an object written in it without an @id"));

		out.getBuffer().setLength(0);
		assertEquals(1, run("check", "--offline", "--json", metadata.toString()));
		// As rocrate-validator's JSON
		JsonNode report = new ObjectMapper().readTree(out.toString());
		assertEquals("wpsld-cli", report.at("/meta/generated_by").asText());
		assertFalse(report.get("passed").asBoolean());
		assertEquals("https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate/",
				report.at("/validation_settings/profile_identifiers/0").asText());
		assertTrue(report.get("unknown_profiles").has("https://example.org/no-such-profile"));
		JsonNode license = null;
		for (JsonNode issue : report.get("issues")) {
			if (issue.at("/check/identifier").asText().equals("rocrate:root-license")) {
				license = issue;
			}
		}
		assertNotNull(report.toString(), license);
		assertEquals("REQUIRED", license.get("severity").asText());
		assertEquals("./", license.get("violatingEntity").asText());
		assertEquals("http://schema.org/license", license.get("violatingProperty").asText());
		assertTrue(license.get("violatingPropertyValue").isNull());
		assertEquals("https://w3id.org/ro/crate/1.3", license.at("/check/requirement/profile/uri").asText());
		// Each issue once, though the profile requires a license too
		assertEquals(1, report.get("issues").findValues("message").stream()
				.filter(m -> m.asText().equals("The root data entity must have a license")).count());
		// Warnings don't fail
		JsonNode funder = report.get("issues").get(0);
		assertEquals("RECOMMENDED", funder.get("severity").asText());
		assertTrue(funder.get("message").asText().startsWith("./ funder has an object"));
	}

	@Test
	public void repairInPlaceKeepingLineEnds() throws Exception {
		assertEquals(0, run("repair", "--dry-run", folder.toString()));
		assertTrue(out.toString().contains("ORCID iDs:\n• http://orcid.org/0000-0002-1825-0097 is now"));
		assertTrue(out.toString().contains("References written as text, now references:\n"
				+ "• ./: citation https://doi.org/10.5281/zenodo.1234"));
		assertTrue(out.toString().contains("Not written (--dry-run)."));
		assertEquals(BROKEN, Files.readString(metadata));

		assertEquals(0, run("repair", folder.toString()));
		String repaired = Files.readString(metadata);
		assertTrue(repaired.contains("\"https://orcid.org/0000-0002-1825-0097\""));
		assertTrue(repaired.contains("\"#organization-1\""));
		assertFalse(repaired.contains("http://schema.org/Person"));
		// Every line ends as in the file read
		assertFalse(repaired.replace("\r\n", "").contains("\n"));

		out.getBuffer().setLength(0);
		assertEquals(0, run("repair", folder.toString()));
		assertEquals("Nothing to repair.", out.toString().strip());
	}

	@Test
	public void previewBesideOrWhereGiven() throws Exception {
		Path preview = folder.resolve("ro-crate-preview.html");
		Files.writeString(preview, "<html><meta name=\"generator\" content=\"rochtml\"></html>");
		assertEquals(1, run("preview", "--offline", folder.toString()));
		assertTrue(err.toString().contains("is another tool's preview"));

		assertEquals(0, run("preview", "--offline", "--replace-other", "--describe", folder.toString()));
		assertTrue(Files.readString(preview).contains("<title>Broken</title>"));
		// The profile's labels
		assertTrue(Files.readString(preview).contains("<th>Conforms to</th>"));
		assertTrue(Files.list(folder.resolve("ro-crate-preview_files")).findFirst().orElseThrow().getFileName()
				.toString().startsWith("old-ro-crate-preview-"));
		JsonNode graph = new ObjectMapper().readTree(metadata.toFile()).get("@graph");
		assertEquals(1, graph.findValues("@id").stream().filter(id -> id.asText().equals("ro-crate-preview.html"))
				.count());

		// A detached crate's needs a file to go in
		Path detached = Files.writeString(folder.resolve("other-ro-crate-metadata.json"), BROKEN);
		assertEquals(2, run("preview", "--offline", detached.toString()));
		Path elsewhere = folder.resolve("elsewhere.html");
		assertEquals(0, run("preview", "--offline", "-o", elsewhere.toString(), detached.toString()));
		assertTrue(Files.readString(elsewhere).contains("<title>Broken</title>"));
	}

	/** In a Windows console's code page, not UTF-8; what it lacks in ASCII */
	@Test
	public void consoleEncoding() throws Exception {
		String text = "• Cédric: Person → Person…";
		for (String encoding : List.of("IBM850", "UTF-8")) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			PrintWriter console = Main.console(new PrintStream(bytes, true, encoding));
			console.print(text);
			console.flush();
			assertEquals(encoding.equals("UTF-8") ? text : "* Cédric: Person -> Person...", bytes.toString(encoding));
		}
	}

	@Test
	public void unreadable() throws Exception {
		assertEquals(2, run("check", folder.resolve("missing").toString()));
		assertTrue(err.toString(), err.toString().startsWith("wpsld-cli: No such file or folder:"));
		Files.writeString(metadata, "not JSON");
		assertEquals(2, run("check", folder.toString()));
		assertTrue(out.toString().contains("Couldn't be read."));
		assertEquals(2, run("repair", folder.toString()));
		assertEquals(2, run());
	}
}
