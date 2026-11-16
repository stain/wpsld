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
module no.s11.wpsld {
    
    requires com.fasterxml.jackson.databind;
    // Loading MASP profiles by URL
    requires java.net.http;
    requires org.apache.tika.core;
    // Documents' own metadata (EmbeddedMetadata): their XML, in ZIP files;
    // the zip file system is a service, so jlink only adds it if required
    requires java.xml;
    requires jdk.zipfs;
    // Readers of files' own metadata in other modules (FileMetadata)
    uses no.s11.wpsld.lookup.MetadataReader;
    requires org.commonmark;
    requires org.commonmark.ext.gfm.tables;
    exports no.s11.wpsld;
    exports no.s11.wpsld.arcp;
    exports no.s11.wpsld.soss;
    exports no.s11.wpsld.form;
    exports no.s11.wpsld.crate;
    exports no.s11.wpsld.lookup;
    exports no.s11.wpsld.jsonld;
    exports no.s11.wpsld.masp;
    // Where the user interface keeps its log (AppDirectories)
    exports no.s11.wpsld.util to no.s11.wpsld.ui;
}
