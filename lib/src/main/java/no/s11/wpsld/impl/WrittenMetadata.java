/*
 * Copyright 2021 The University of Manchester, UK
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
package no.s11.wpsld.impl;

import java.nio.file.Path;

import no.s11.wpsld.WPSLDPath;

import edu.kit.datamanager.ro_crate.RoCrate;
import edu.kit.datamanager.ro_crate.RoCrateBuilder;
import edu.kit.datamanager.ro_crate.writer.RoCrateWriter;

public class WrittenMetadata implements WPSLDPath {

	private WPSLDPath root;

	public WrittenMetadata(WPSLDPath root) {
		this.root = root;
		writeMetadata();		
	}

	private void writeMetadata() {
		RoCrate roCrate = new RoCrateBuilder("name", "description").build();
		RoCrateWriter folderRoCrateWriter = new RoCrateWriter(new FolderWriter());
		// FIXME: Below assumes path is on local file system!
		folderRoCrateWriter.save(roCrate, this.root.toString());
	}

	@Override
	public Path getPath() {
		return root.getPath();
	}
	

}
