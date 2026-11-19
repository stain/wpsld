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
package no.s11.wpsld.ui;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * What a crate's relative <code>@id</code>s are relative to, as the user
 * says with Crate &gt; Attach…: a folder on disk, a web address, or nothing
 * (detached). It is how the crate is looked at, not part of it: the
 * <code>@id</code>s are not changed.
 *
 * @param folder A folder on disk, e.g. the crate's files downloaded and
 *               unpacked by hand
 * @param web    A web address, a folder or a page, that they are relative to
 */
record Attachment(Optional<Path> folder, Optional<URI> web) {
	Attachment {
		if (folder.isPresent() && web.isPresent()) {
			throw new IllegalArgumentException("Attached to a folder or a web address, not both");
		}
	}

	static Attachment none() {
		return new Attachment(Optional.empty(), Optional.empty());
	}

	static Attachment toFolder(Path folder) {
		return new Attachment(Optional.of(folder.toAbsolutePath().normalize()), Optional.empty());
	}

	static Attachment toWeb(URI address) {
		return new Attachment(Optional.empty(), Optional.of(address));
	}

	boolean isNone() {
		return folder.isEmpty() && web.isEmpty();
	}

	/** For messages: where it is attached, or that it is detached */
	@Override
	public String toString() {
		return folder.map(f -> "the folder " + f).or(() -> web.map(w -> "the web address " + w)).orElse("nothing");
	}
}
