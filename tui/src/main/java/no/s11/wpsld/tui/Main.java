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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateReader;

/** A terminal viewer and editor for a crate. */
public final class Main {
	/** RO-Crate 1.0's name for the metadata file */
	static final String RO_CRATE_METADATA_JSONLD = "ro-crate-metadata.jsonld";

	private Main() {
	}

	public static void main(String[] args) {
		int status = run(args);
		if (status != 0) {
			System.exit(status);
		}
	}

	static int run(String[] args) {
		if (args.length != 1 || args[0].equals("--help") || args[0].equals("-h")) {
			System.err.println("Usage: wpsld-tui CRATE (folder or metadata file)");
			return args.length == 1 ? 0 : 2;
		}
		CrateSession session;
		try {
			session = CrateSession.open(Path.of(args[0]));
		} catch (IOException e) {
			System.err.println("wpsld-tui: Can't read crate: " + e.getMessage());
			return 2;
		}
		CrateDocument document = session.document();
		document.problems().forEach(p -> System.err.println(p));
		if (document.crate().isEmpty()) {
			return 2;
		}
		try (Screen screen = new DefaultTerminalFactory().setForceTextTerminal(true).createScreen()) {
			screen.startScreen();
			new Viewer(screen, session).run();
			return 0;
		} catch (IOException e) {
			System.err.println("wpsld-tui: " + e.getMessage());
			return 2;
		}
	}

	static CrateDocument read(Path path) {
		return new CrateReader().read(metadataPath(path));
	}

	static Path metadataPath(Path path) {
		if (Files.isDirectory(path) && !Files.exists(path.resolve(Crate.RO_CRATE_METADATA_JSON))
				&& Files.exists(path.resolve(RO_CRATE_METADATA_JSONLD))) {
			path = path.resolve(RO_CRATE_METADATA_JSONLD);
		} else if (Files.isDirectory(path)) {
			path = path.resolve(Crate.RO_CRATE_METADATA_JSON);
		}
		return path;
	}
}
