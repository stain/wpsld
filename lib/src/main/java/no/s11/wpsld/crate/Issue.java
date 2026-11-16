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
package no.s11.wpsld.crate;

import java.util.Optional;

/**
 * What a crate lacks or has wrong by a requirement, as wpsld's checks find
 * it ({@link CrateChecks}, {@link CrateFolder}, and the profiles' required
 * properties), shaped as rocrate-validator reports its issues: how strong
 * the requirement is, which check found it and what it is of, the message,
 * and where.
 *
 * @param severity How strong the requirement is
 * @param check    wpsld's identifier of the check, e.g. rocrate:root-license
 * @param name     What the check is, e.g. "Root data entity: license"
 * @param source   What the requirement is of: RO-Crate, or a profile
 * @param message  What is wrong, for people
 * @param entity   The entity's @id, if it is about one
 * @param property The property (a term, e.g. license), if it is about one
 * @param value    The value, if it is about one
 */
public record Issue(Severity severity, String check, String name, Source source, String message,
		Optional<String> entity, Optional<String> property, Optional<String> value) {

	/** How strong a requirement is, as RFC 2119 and rocrate-validator word it */
	public enum Severity {
		/** MUST: the crate doesn't conform without it */
		REQUIRED,
		/** SHOULD: a warning, allowed with good reason */
		RECOMMENDED,
		/** MAY */
		OPTIONAL
	}

	/**
	 * What a requirement is of
	 *
	 * @param uri  Its identifier, e.g. a profile's, as crates give it in conformsTo
	 * @param name Its name
	 */
	public record Source(String uri, String name) {
	}

	/** RO-Crate's own requirements, as wpsld checks them (the latest version's) */
	public static final Source RO_CRATE = new Source(Crate.RO_CRATE_SPEC + "1.3",
			"RO-Crate Metadata Specification 1.3");

	/** Of RO-Crate, about an entity, perhaps a property of it and its value */
	static Issue of(Severity severity, String check, String name, String message, Entity entity,
			Optional<String> property, Optional<String> value) {
		return new Issue(severity, check, name, RO_CRATE, message, Optional.of(entity.getId()), property, value);
	}

	/** Whether the crate doesn't conform with it */
	public boolean isRequired() {
		return severity == Severity.REQUIRED;
	}
}
