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
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import no.s11.wpsld.form.CrateCheck;
import no.s11.wpsld.form.CrateProfiles;
import no.s11.wpsld.masp.Profile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** wpsld-cli check: a crate's problems, as the editor finds them before saving (see {@link CrateCheck}) */
@Command(name = "check", mixinStandardHelpOptions = true,
		description = { "Report a crate's problems, as wpsld finds them before saving: reading it, the properties"
				+ " its profiles require (by its conformsTo; else the core RO-Crate profile), RO-Crate's own"
				+ " requirements, and an attached crate's files and folders; files in its folder that it doesn't"
				+ " describe are warnings.",
				"Exit status 0 if it has none, 1 if it has, 2 if it can't be read." })
final class CheckCommand implements Callable<Integer> {
	@ParentCommand
	private Main main;
	@Spec
	private CommandSpec spec;

	@Parameters(paramLabel = "CRATE", description = "The crate's folder, or its metadata file")
	private Path crate;

	@Option(names = "--json", description = "Report as JSON")
	private boolean json;

	@Option(names = "--offline", description = "Don't load profiles from the web: only those bundled, or kept by"
			+ " the editor")
	private boolean offline;

	@Override
	public Integer call() throws IOException {
		PrintWriter out = spec.commandLine().getOut();
		OpenedCrate opened = OpenedCrate.open(crate);
		CrateProfiles profiles = opened.document().crate().isPresent()
				? CrateProfiles.of(opened.document(), main.profiles(), !offline)
				: new CrateProfiles(java.util.List.of(), java.util.List.of(), java.util.Map.of());
		CrateCheck check = CrateCheck.of(opened.document(), profiles, main.terms(), opened.folder());
		if (json) {
			ObjectNode report = new ObjectMapper().createObjectNode();
			report.put("crate", opened.metadata().toString());
			report.put("passed", check.passed());
			var checked = report.putArray("profiles");
			profiles.matched().forEach(p -> checked.addObject().put("id", p.identifiers().isEmpty() ? p.id() : p.identifiers().get(0))
					.put("name", p.name().orElse(p.id())));
			var unknown = report.putObject("unknownProfiles");
			profiles.unknown().forEach(unknown::put);
			var reading = report.putArray("reading");
			check.reading().forEach(p -> reading.addObject().put("severity", p.severity().toString())
					.put("message", p.message()));
			var problems = report.putArray("problems");
			check.problems().forEach(problems::add);
			var warnings = report.putArray("warnings");
			check.warnings().forEach(warnings::add);
			out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
		} else {
			out.println(opened.metadata());
			out.println("Profiles: " + (profiles.matched().isEmpty() ? "none"
					: String.join(", ", profiles.matched().stream().map(CheckCommand::describe).toList()))
					+ (profiles.declared().isEmpty() ? " (none declared in conformsTo)" : ""));
			profiles.unknown().forEach((id, why) -> out.println("Profile not found: " + id + " (" + why + ")"));
			check.reading().forEach(p -> out.println(p));
			check.problems().forEach(p -> out.println("• " + p));
			check.warnings().forEach(w -> out.println("Warning: " + w));
			out.println(opened.document().crate().isEmpty() ? "Couldn't be read."
					: check.passed() ? "Passed."
					: check.problems().size() + (check.problems().size() == 1 ? " problem." : " problems."));
		}
		out.flush();
		if (opened.document().crate().isEmpty()) {
			return Main.UNUSABLE;
		}
		return check.passed() ? 0 : Main.PROBLEMS;
	}

	private static String describe(Profile profile) {
		return profile.name().orElse(profile.id()) + profile.version().map(v -> " " + v).orElse("");
	}
}
