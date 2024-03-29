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
import java.nio.file.Files;

import org.junit.Test;

import no.s11.wpsld.WPSLD;
import no.s11.wpsld.WPSLDPath;

public class TestWPSLD {
    @Test 
    public void newRootIsDirectory() throws IOException {
    	WPSLD wpsld = WPSLD.Factory.create();
        WPSLDPath root = wpsld.newRoot();
        assertTrue("New root should be a directory", 
        		Files.isDirectory(root.getPath()));
    }
}
