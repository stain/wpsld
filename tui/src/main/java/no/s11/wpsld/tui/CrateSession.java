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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.SafeSave;

final class CrateSession {
	private final Path file;
	private byte[] content;
	private final CrateDocument document;
	private JsonNode saved;
	private final String lineEnd;

	private CrateSession(Path file, byte[] content, CrateDocument document) {
		this.file = file;
		this.content = content;
		this.document = document;
		this.saved = document.crate().map(Crate::toJson).orElse(null);
		this.lineEnd = Crate.lineEnd(content);
	}

	static CrateSession open(Path path) throws IOException {
		Path file = Main.metadataPath(path);
		byte[] content = Files.readAllBytes(file);
		CrateDocument document = new CrateReader().read(new ByteArrayInputStream(content), file.toString());
		return new CrateSession(file, content, document);
	}

	CrateDocument document() {
		return document;
	}

	Path file() {
		return file;
	}

	boolean dirty() {
		return !document.crate().orElseThrow().toJson().equals(saved);
	}

	void save() throws IOException {
		if (document.hasErrors()) {
			throw new IOException("The crate had errors when read; saving could discard data.");
		}
		if (!Arrays.equals(content, Files.readAllBytes(file))) {
			throw new IOException("The metadata file changed on disk; reopen it before saving.");
		}
		JsonNode current = document.crate().orElseThrow().toJson();
		byte[] written = Crate.writer(lineEnd).writeValueAsBytes(current);
		SafeSave.write(file, written);
		content = written;
		saved = current;
	}
}
