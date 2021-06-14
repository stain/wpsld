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
package no.s11.wpsld.path;

import java.nio.file.Path;

/**
 * The base name of a path, e.g.
 * "file.txt" from "/home/foo/file.txt"
 */
public final class BaseName implements CharSequence {
	private final String name;
	public BaseName(Path path) {
		if (path.getNameCount() == 0) { 
			this.name = path.toString();
		} else {
			this.name = path.getName(path.getNameCount()-1).toString();
		}
	}
	@Override
	public int length() {
		return name.length();
	}
	@Override
	public char charAt(int index) {
		return name.charAt(index);
	}
	@Override
	public CharSequence subSequence(int start, int end) {
		return name.subSequence(start, end);
	}
	@Override
	public String toString() {
		return name;
	}
}
