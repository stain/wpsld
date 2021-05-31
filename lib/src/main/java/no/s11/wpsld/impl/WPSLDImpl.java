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
import java.nio.file.Files;
import java.nio.file.Path;

import no.s11.wpsld.WPSLD;
import no.s11.wpsld.WPSLDPath;

public class WPSLDImpl implements WPSLD {

	@Override
	public WPSLDPath newRoot() throws IOException {
		Path root = Files.createTempDirectory("wpsld");
		return new WPSLDPathImpl(root);
	}

}
