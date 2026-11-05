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
import java.io.OutputStream;
import java.lang.System;
import java.lang.System.Logger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import no.s11.wpsld.WPSLDPath;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class WrittenMetadata implements WPSLDPath {
	public static final String RO_CRATE_METADATA_JSON = "ro-crate-metadata.json";
	public static final String RO_CRATE_1_3 = "https://w3id.org/ro/crate/1.3";
	public static final String RO_CRATE_1_3_CONTEXT = RO_CRATE_1_3 + "/context";

	private static Logger logger = System.getLogger(WrittenMetadata.class.getName());
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private WPSLDPath root;

	public WrittenMetadata(WPSLDPath root) throws IOException {
		this.root = root;
		writeMetadata();
	}

	private void writeMetadata() throws IOException {
		// Flattened, compacted JSON-LD as required by RO-Crate
		ObjectNode crate = MAPPER.createObjectNode();
		crate.put("@context", RO_CRATE_1_3_CONTEXT);
		ArrayNode graph = crate.putArray("@graph");
		ObjectNode descriptor = graph.addObject();
		descriptor.put("@id", RO_CRATE_METADATA_JSON);
		descriptor.put("@type", "CreativeWork");
		descriptor.putObject("about").put("@id", "./");
		descriptor.putObject("conformsTo").put("@id", RO_CRATE_1_3);
		ObjectNode rootDataEntity = graph.addObject();
		rootDataEntity.put("@id", "./");
		rootDataEntity.put("@type", "Dataset");
		rootDataEntity.put("name", "name");
		rootDataEntity.put("description", "");

		if (! Files.getFileStore(getPath()).supportsFileAttributeView(UserDefinedFileAttributeView.class)) {
			throw new IOException("File system does not support extended attributes (UserDefinedFileAttributeView): " + getPath());
		}
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
				logger.log(Logger.Level.WARNING, "Can't read file attribute {0} from path {1}: {2}", key, getPath(), e);
			}
			rootDataEntity.put(key.replaceFirst("^wpsld\\.", ""), 
			// TODO: Support { objects } and numbers etc.
				new String(buff.array(), StandardCharsets.UTF_8));
		});
		try (OutputStream out = Files.newOutputStream(getPath().resolve(RO_CRATE_METADATA_JSON))) {
			MAPPER.writeValue(out, crate);
		}
	}

	@Override
	public Path getPath() {
		return root.getPath();
	}
	

}
