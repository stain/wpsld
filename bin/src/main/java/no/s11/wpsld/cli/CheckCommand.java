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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import no.s11.wpsld.crate.Issue;
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
				"Exit status 0 if it has none (warnings allowed), 1 if it has, 2 if it can't be read." })
final class CheckCommand implements Callable<Integer> {
	@ParentCommand
	private Main main;
	@Spec
	private CommandSpec spec;

	@Parameters(paramLabel = "CRATE", description = "The crate's folder, or its metadata file")
	private Path crate;

	@Option(names = "--json", description = "Report as JSON, as rocrate-validator writes it (validate -f json)")
	private boolean json;

	@Option(names = "--offline", description = "Don't load profiles from the web: only those bundled, or kept by"
			+ " the editor")
	private boolean offline;

	@Override
	public Integer call() throws IOException {
		PrintWriter out = spec.commandLine().getOut();
		Instant started = Instant.now();
		OpenedCrate opened = OpenedCrate.open(crate);
		CrateProfiles profiles = opened.document().crate().isPresent()
				? CrateProfiles.of(opened.document(), main.profiles(), !offline)
				: new CrateProfiles(List.of(), List.of(), Map.of());
		CrateCheck check = CrateCheck.of(opened.document(), profiles, main.terms(), opened.folder());
		if (json) {
			out.println(new ObjectMapper().writerWithDefaultPrettyPrinter()
					.writeValueAsString(report(opened, check, started, Instant.now())));
		} else {
			out.println(opened.metadata());
			out.println("Profiles: " + (profiles.matched().isEmpty() ? "none"
					: String.join(", ", profiles.matched().stream().map(CheckCommand::describe).toList()))
					+ (profiles.declared().isEmpty() ? " (none declared in conformsTo)" : ""));
			profiles.unknown().forEach((id, why) -> out.println("Profile not found: " + id + " (" + why + ")"));
			check.issues().forEach(i -> out.println((i.isRequired() ? "• " : "Warning: ") + i.message()));
			long required = check.issues().stream().filter(Issue::isRequired).count();
			long warnings = check.issues().size() - required;
			out.println((!check.read() ? "Couldn't be read."
					: check.passed() ? "Passed."
					: required + (required == 1 ? " problem." : " problems."))
					+ (warnings == 0 ? "" : " " + warnings + (warnings == 1 ? " warning." : " warnings.")));
		}
		out.flush();
		if (!check.read()) {
			return Main.UNUSABLE;
		}
		return check.passed() ? 0 : Main.PROBLEMS;
	}

	/**
	 * The report as rocrate-validator writes its JSON (validate -f json), so
	 * that what reads its reports can read these: meta, validation_settings,
	 * passed, issues (each with its severity, message, violating entity,
	 * property and value, and the check, its requirement and profile) and
	 * statistics; and wpsld's own unknown_profiles
	 */
	private ObjectNode report(OpenedCrate opened, CrateCheck check, Instant started, Instant finished) {
		ObjectNode report = new ObjectMapper().createObjectNode();
		report.putObject("meta").put("generated_by", "wpsld-cli").put("version", Main.version());
		ObjectNode settings = report.putObject("validation_settings");
		settings.put("rocrate_uri", crate.toString());
		settings.put("metadata_file", opened.metadata().toString());
		settings.put("packaging_mode", opened.folder().isPresent() ? "attached" : "detached");
		settings.put("offline", offline);
		ArrayNode identifiers = settings.putArray("profile_identifiers");
		check.profiles().matched().forEach(p -> identifiers.add(identifier(p)));
		report.put("passed", check.passed());
		ArrayNode issues = report.putArray("issues");
		for (Issue issue : check.issues()) {
			ObjectNode shown = issues.addObject();
			shown.put("severity", issue.severity().toString());
			shown.put("message", issue.message());
			shown.put("violatingEntity", issue.entity().orElse(null));
			shown.put("violatingProperty", issue.property()
					.map(p -> p.startsWith("@") ? p : main.terms().iri(p).map(Object::toString).orElse(p)).orElse(null));
			shown.put("violatingPropertyValue", issue.value().orElse(null));
			ObjectNode checked = shown.putObject("check");
			checked.put("identifier", issue.check());
			checked.put("name", issue.name());
			checked.put("severity", issue.severity().toString());
			checked.put("profile", issue.source().uri());
			ObjectNode requirement = checked.putObject("requirement");
			requirement.put("identifier", issue.check());
			requirement.put("name", issue.name());
			requirement.putObject("profile").put("identifier", issue.source().uri()).put("uri", issue.source().uri())
					.put("name", issue.source().name());
		}
		ObjectNode unknown = report.putObject("unknown_profiles");
		check.profiles().unknown().forEach(unknown::put);
		ObjectNode statistics = report.putObject("statistics");
		statistics.put("started_at", started.toString());
		statistics.put("finished_at", finished.toString());
		statistics.put("duration", Duration.between(started, finished).toMillis() / 1000.0);
		ArrayNode checkedProfiles = statistics.putArray("profiles");
		check.profiles().matched().forEach(p -> checkedProfiles.add(identifier(p)));
		return report;
	}

	private static String identifier(Profile profile) {
		return profile.identifiers().isEmpty() ? profile.id() : profile.identifiers().get(0);
	}

	private static String describe(Profile profile) {
		return profile.name().orElse(profile.id()) + profile.version().map(v -> " " + v).orElse("");
	}
}
