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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.SafeSave;
import no.s11.wpsld.lookup.CrateRepair;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** wpsld-cli repair: Repair Crate, as the editor's Crate menu does it (see {@link CrateRepair}) */
@Command(name = "repair", mixinStandardHelpOptions = true,
		description = { "Repair a crate as wpsld's Crate > Repair Crate does, all offline, and say what was done:"
				+ " objects without @id made entities, types and Enumeration members written otherwise,"
				+ " SPDX licences, PRONOM formats, ORCID iDs, ROR identifiers and DOIs in their usual form,"
				+ " entities referred to but missing added, and the root's mentions.",
				"The crate is written back in place, safely and with its line ends, unless --dry-run or -o." })
final class RepairCommand implements Callable<Integer> {
	@ParentCommand
	private Main main;
	@Spec
	private CommandSpec spec;

	@Parameters(paramLabel = "CRATE", description = "The crate's folder, or its metadata file")
	private Path crate;

	@Option(names = "--dry-run", description = "Only say what would be repaired; write nothing")
	private boolean dryRun;

	@Option(names = { "-o", "--output" }, paramLabel = "FILE",
			description = "Write the repaired crate's metadata to this file rather than in place")
	private Path output;

	@Option(names = "--json", description = "Report as JSON")
	private boolean json;

	@Override
	public Integer call() throws IOException {
		PrintWriter out = spec.commandLine().getOut();
		PrintWriter err = spec.commandLine().getErr();
		OpenedCrate opened = OpenedCrate.open(crate);
		if (opened.document().crate().isEmpty()) {
			opened.document().problems().forEach(err::println);
			err.flush();
			return Main.UNUSABLE;
		}
		Crate repaired = opened.document().crate().get();
		List<CrateRepair.Repaired> done = CrateRepair.repair(repaired, main.terms());
		Path target = output != null ? output : opened.metadata();
		boolean write = !dryRun && (!done.isEmpty() || output != null);
		if (write) {
			SafeSave.write(target, Crate.writer(Crate.lineEnd(opened.content())).writeValueAsBytes(repaired.toJson()));
		}
		if (json) {
			var report = new ObjectMapper().createObjectNode();
			report.put("crate", opened.metadata().toString());
			if (write) {
				report.put("written", target.toString());
			}
			ArrayNode repairs = report.putArray("repaired");
			done.forEach(r -> {
				var kind = repairs.addObject().put("heading", r.heading());
				r.items().forEach(kind.putArray("items")::add);
				r.advice().ifPresent(a -> kind.put("advice", a));
			});
			out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
		} else {
			out.println(done.isEmpty() ? "Nothing to repair."
					: done.stream().map(CrateRepair.Repaired::toString).collect(Collectors.joining("\n\n")));
			if (write) {
				out.println();
				out.println("Written to " + target);
			} else if (dryRun && !done.isEmpty()) {
				out.println();
				out.println("Not written (--dry-run).");
			}
		}
		out.flush();
		return 0;
	}
}
