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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.function.Supplier;

import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * wpsld-cli: wpsld's checks, Repair Crate and HTML preview on the command
 * line, for scripts and CI, as the editor does them (the same code, in
 * lib). Exit status: 0 done (or the crate passed), 1 problems found, 2 the
 * command or the crate couldn't be used.
 */
@Command(name = "wpsld-cli", mixinStandardHelpOptions = true, versionProvider = Main.Version.class,
		description = "Check, repair and preview RO-Crates as the wpsld editor does.",
		subcommands = { CheckCommand.class, RepairCommand.class, PreviewCommand.class,
				CommandLine.HelpCommand.class },
		exitCodeListHeading = "%nExit status:%n",
		exitCodeList = { "0:Done, or the crate passed", "1:Problems found", "2:Usage error, or the crate couldn't be read" })
public final class Main implements Runnable {
	/** Exit status when problems were found */
	static final int PROBLEMS = 1;
	/** Exit status when the command or the crate couldn't be used */
	static final int UNUSABLE = 2;

	private final Supplier<ProfileCache> profiles;
	private CrateTerms terms;

	@Spec
	private CommandSpec spec;

	/** With the user's profiles, as the editor keeps them */
	public Main() {
		this(ProfileCache::forUser);
	}

	/** With these profiles, e.g. only the bundled ones in tests */
	Main(Supplier<ProfileCache> profiles) {
		this.profiles = profiles;
	}

	public static void main(String[] args) {
		System.exit(commandLine(new Main()).execute(args));
	}

	/**
	 * The command line of these commands: a file that can't be read or
	 * written said in a line, not a stack trace, with exit status 2
	 */
	static CommandLine commandLine(Main main) {
		CommandLine line = new CommandLine(main);
		line.setExecutionExceptionHandler((e, commandLine, parsed) -> {
			if (!(e instanceof IOException) && !(e instanceof UncheckedIOException)) {
				throw e;
			}
			Throwable cause = e instanceof UncheckedIOException u ? u.getCause() : e;
			commandLine.getErr().println("wpsld-cli: " + (cause instanceof java.nio.file.NoSuchFileException
					? "No such file or folder: " + cause.getMessage()
					: cause.getMessage()));
			commandLine.getErr().flush();
			return UNUSABLE;
		});
		return line;
	}

	@Override
	public void run() {
		throw new ParameterException(spec.commandLine(), "Missing command: check, repair or preview");
	}

	ProfileCache profiles() {
		return profiles.get();
	}

	/** schema.org and RO-Crate's terms, loaded once */
	CrateTerms terms() {
		if (terms == null) {
			terms = new CrateTerms(new SchemaFromJsonLd());
		}
		return terms;
	}

	/** What made a preview, as the editor writes it, e.g. "wpsld 0.7.0" */
	static String generator() {
		return "wpsld " + version();
	}

	static String version() {
		Properties about = new Properties();
		try (InputStream in = Main.class.getResourceAsStream("about.properties")) {
			if (in != null) {
				about.load(in);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		String version = about.getProperty("version", "");
		return version.isBlank() || version.contains("${") ? "(development)" : version;
	}

	static final class Version implements IVersionProvider {
		@Override
		public String[] getVersion() {
			return new String[] { "wpsld-cli " + version() };
		}
	}
}
