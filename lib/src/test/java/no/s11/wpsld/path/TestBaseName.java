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
package no.s11.wpsld.path;

import static org.junit.Assert.*;

import java.nio.file.Path;

import org.junit.Test;

public class TestBaseName {

	@Test
	public void fileName() {
		assertEquals("file.txt", new BaseName(Path.of("/home/foo/file.txt")).toString());
		assertEquals("file.txt", new BaseName(Path.of("file.txt")).toString());
	}

	@Test
	public void directory() {
		assertEquals("foo", new BaseName(Path.of("/home/foo/")).toString());
	}

	@Test
	public void root() {
		assertEquals("/", new BaseName(Path.of("/")).toString());
	}

	@Test
	public void charSequence() {
		BaseName name = new BaseName(Path.of("data/file.txt"));
		assertEquals(8, name.length());
		assertEquals('f', name.charAt(0));
		assertEquals("file", name.subSequence(0, 4).toString());
	}
}
