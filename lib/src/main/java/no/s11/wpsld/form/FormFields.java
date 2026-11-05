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
package no.s11.wpsld.form;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Decides which fields an editor shows for an entity.
 * <p>
 * {@link SchemaOrgFormFields} offers every schema.org property of the types;
 * a profile (e.g. RO-Crate MASP, see MASP.md) can narrow that down to the
 * properties it specifies, with required ones and cardinalities.
 */
public interface FormFields {

	/**
	 * Fields for an entity with the given <code>@type</code>s, in the order
	 * they should be shown.
	 */
	List<FormField> fieldsFor(Set<URI> types);
}
