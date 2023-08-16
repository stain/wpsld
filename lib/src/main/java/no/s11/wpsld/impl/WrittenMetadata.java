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

import java.io.IOException;
import java.lang.System;
import java.lang.System.Logger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import no.s11.wpsld.WPSLDPath;

import edu.kit.datamanager.ro_crate.RoCrate;
import edu.kit.datamanager.ro_crate.RoCrate.RoCrateBuilder;
import edu.kit.datamanager.ro_crate.writer.RoCrateWriter;
import edu.kit.datamanager.ro_crate.writer.FolderWriter;

public class WrittenMetadata implements WPSLDPath {
	private static Logger logger = System.getLogger(WrittenMetadata.class.getName());
	private WPSLDPath root;

	public WrittenMetadata(WPSLDPath root) throws IOException {
		this.root = root;
		writeMetadata();		
	}

	private void writeMetadata() throws IOException {
		RoCrate roCrate = new RoCrateBuilder("name", "").build();
		UserDefinedFileAttributeView view = Files.getFileAttributeView(getPath(), UserDefinedFileAttributeView.class);
		// FIXME: Generalize for every path
		view.list().stream().filter(s -> s.startsWith("wpsld.")).forEach(key -> {
			ByteBuffer buff;
			try {
				buff = ByteBuffer.allocate(view.size(key));
			} catch (IOException e) {
				logger.log(Logger.Level.WARNING, "Can't determine size of file attribute {0} from path {1}: {2}", key, getPath(), e);
				return;
			}
			try {
				view.read(key, buff);
			} catch (IOException e) {
				logger.log(Logger.Level.WARNING, "Can't read of file attribute {0} from path {1}: {2}", key, getPath(), e);
			}
			roCrate.getRootDataEntity().addProperty(key.replaceFirst("^wpsld\\.", ""), 
			// TODO: Support { objects } and numbers etc.
			// FIXME: Don't assume default charset
				new String(buff.array()));
		});
		RoCrateWriter folderRoCrateWriter = new RoCrateWriter(new FolderWriter());
		// FIXME: Below assumes path is on local file system!
		folderRoCrateWriter.save(roCrate, root.getPath().toString());
	}

	@Override
	public Path getPath() {
		return root.getPath();
	}
	

}
