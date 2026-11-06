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

/**
 * Which kind of property a form field is, with a profile: see MASP.md.
 */
public enum FieldTier {
	/** Required by the profile (<code>sh:minCount</code> of 1 or more) */
	REQUIRED,
	/** Named by the profile, but optional: what it suggests describing */
	SUGGESTED,
	/** Other properties the vocabulary allows for the type */
	OTHER
}
