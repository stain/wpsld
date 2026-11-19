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
import java.util.Optional;

/**
 * What the user chose in Crate &gt; Detach…: the base the relative
 * <code>@id</code>s are made absolute against, and whether to keep it as the
 * <code>@base</code> of the crate's <code>@context</code>.
 *
 * @param base                None if the crate has no relative <code>@id</code>s to make absolute
 * @param recordBase          Whether to keep the base as <code>@base</code>
 * @param useDownloadAddresses Whether a file that says where it can be downloaded from, with its
 *                            <code>contentUrl</code> or <code>url</code>, has that as its <code>@id</code>
 * @param checkAvailability   Whether to check the web addresses of the crate afterwards
 */
record Detachment(Optional<URI> base, boolean recordBase, boolean useDownloadAddresses, boolean checkAvailability) {
	/** Just the base, and whether to keep it as @base */
	Detachment(Optional<URI> base, boolean recordBase) {
		this(base, recordBase, false, false);
	}
}
