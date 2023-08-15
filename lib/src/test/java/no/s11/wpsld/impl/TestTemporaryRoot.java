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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

import no.s11.wpsld.WPSLDPath;
import no.s11.wpsld.path.BaseName;

public class TestTemporaryRoot {
    @Test 
    public void newRootIsDirectory() throws IOException {
    	WPSLDPath root = new TemporaryRoot();
        assertTrue("Temporary root should be a directory: " + root, 
        		Files.isDirectory(root.getPath()));
        assertTrue("Basename should start with wpsld: " + root, 
        		new BaseName(root.getPath()).toString().startsWith("wpsld")); 
    }
}
