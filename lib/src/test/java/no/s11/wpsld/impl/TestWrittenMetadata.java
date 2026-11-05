/*
 * Copyright 2021-2023 The University of Manchester, UK
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

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import no.s11.wpsld.WPSLD;
import no.s11.wpsld.WPSLDPath;

public class TestWrittenMetadata {
    private static final String RO_CRATE_METADATA_JSON = "ro-crate-metadata.json";

    @Test 
    public void metadataExists() throws IOException {
    	WPSLD wpsld = WPSLD.Factory.create();
        WPSLDPath root = wpsld.newRoot();
        Path metadata = root.getPath().resolve(RO_CRATE_METADATA_JSON);
        assertFalse("metadata should not be written yet", 
        		Files.exists(metadata));
        UserDefinedFileAttributeView view = Files.getFileAttributeView(root.getPath(), UserDefinedFileAttributeView.class);
        // FIXME: Update for convention for URI references       
        view.write("wpsld.license", 
            ByteBuffer.wrap("http://spdx.org/licenses/CC0-1.0".getBytes("UTF-8")));
        WrittenMetadata written = new WrittenMetadata(root);
        Path writtenMetadata = written.getPath().resolve(RO_CRATE_METADATA_JSON);
        assertTrue("metadata should now be written", 
        		Files.exists(writtenMetadata));
    }

    @Test
    public void metadataContent() throws IOException {
        WPSLDPath root = WPSLD.Factory.create().newRoot();
        UserDefinedFileAttributeView view = Files.getFileAttributeView(root.getPath(), UserDefinedFileAttributeView.class);
        view.write("wpsld.name", ByteBuffer.wrap("Blåbærsyltetøy".getBytes("UTF-8")));
        view.write("unrelated.attribute", ByteBuffer.wrap("ignored".getBytes("UTF-8")));
        new WrittenMetadata(root);

        JsonNode crate = new ObjectMapper().readTree(root.getPath().resolve(RO_CRATE_METADATA_JSON).toFile());
        assertEquals("https://w3id.org/ro/crate/1.3/context", crate.get("@context").asText());
        JsonNode descriptor = crate.get("@graph").get(0);
        assertEquals(RO_CRATE_METADATA_JSON, descriptor.get("@id").asText());
        assertEquals("CreativeWork", descriptor.get("@type").asText());
        assertEquals("./", descriptor.get("about").get("@id").asText());
        assertEquals("https://w3id.org/ro/crate/1.3", descriptor.get("conformsTo").get("@id").asText());
        JsonNode rootDataEntity = crate.get("@graph").get(1);
        assertEquals("./", rootDataEntity.get("@id").asText());
        assertEquals("Dataset", rootDataEntity.get("@type").asText());
        assertEquals("Blåbærsyltetøy", rootDataEntity.get("name").asText());
        assertFalse(rootDataEntity.has("attribute"));
        assertFalse(rootDataEntity.has("unrelated.attribute"));
    }
}
