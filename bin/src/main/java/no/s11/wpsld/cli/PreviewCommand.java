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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.SafeSave;
import no.s11.wpsld.form.CratePreview;
import no.s11.wpsld.form.CrateProfiles;
import no.s11.wpsld.form.SchemaOrgFormFields;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** wpsld-cli preview: a crate's HTML preview, as the editor writes it on saving (see {@link CratePreview}) */
@Command(name = "preview", mixinStandardHelpOptions = true,
		description = { "Write a crate's HTML preview, as wpsld writes it on saving: ro-crate-preview.html beside an"
				+ " attached crate's metadata, or the file given with -o (as a detached crate needs).",
				"Another tool's preview is not written over, unless --replace-other, which moves it aside into"
						+ " ro-crate-preview_files/." })
final class PreviewCommand implements Callable<Integer> {
	@ParentCommand
	private Main main;
	@Spec
	private CommandSpec spec;

	@Parameters(paramLabel = "CRATE", description = "The crate's folder, or its metadata file")
	private Path crate;

	@Option(names = { "-o", "--output" }, paramLabel = "FILE",
			description = "Write the preview to this file, rather than beside the crate's metadata")
	private Path output;

	@Option(names = "--replace-other", description = "Replace another tool's preview, moving it aside")
	private boolean replaceOther;

	@Option(names = "--describe", description = "Also describe the preview in the crate's metadata, as RO-Crate"
			+ " shows (a CreativeWork about the root), as the editor does on saving; writes the metadata as wpsld"
			+ " writes it")
	private boolean describe;

	@Option(names = "--offline", description = "Don't load the crate's profile from the web, for its labels")
	private boolean offline;

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
		Crate previewed = opened.document().crate().get();
		if (output == null && opened.folder().isEmpty()) {
			err.println("A detached crate's preview has no folder to go in: give a file with -o");
			err.flush();
			return Main.UNUSABLE;
		}
		Path target = output != null ? output : opened.folder().get().resolve(CratePreview.FILE);
		if (Files.isRegularFile(target) && !CratePreview.isOurs(target)) {
			if (!replaceOther) {
				err.println(target + " is another tool's preview: not written over (--replace-other to replace it,"
						+ " moving it aside)");
				err.flush();
				return Main.PROBLEMS;
			}
			out.println("Moved another tool's preview aside, to " + CratePreview.moveAside(target));
		}
		boolean beside = opened.folder().isPresent()
				&& target.toAbsolutePath().normalize().equals(opened.folder().get().resolve(CratePreview.FILE));
		if (describe && beside) {
			String before = Crate.WRITER.writeValueAsString(previewed.toJson());
			previewed.describePreview();
			byte[] after = Crate.writer(Crate.lineEnd(opened.content())).writeValueAsBytes(previewed.toJson());
			if (!before.equals(Crate.WRITER.writeValueAsString(previewed.toJson()))) {
				SafeSave.write(opened.metadata(), after);
				out.println("Described " + CratePreview.FILE + " in " + opened.metadata());
			}
		} else if (describe) {
			err.println("Not described in the crate: only the preview beside an attached crate's metadata is");
		}
		CrateProfiles profiles = CrateProfiles.of(opened.document(), main.profiles(), !offline);
		CratePreview.write(target, previewed,
				CrateProfiles.labels(previewed, profiles.fields(new SchemaOrgFormFields(main.terms())), main.terms()),
				Main.generator());
		out.println("Written " + target);
		out.flush();
		err.flush();
		return 0;
	}
}
