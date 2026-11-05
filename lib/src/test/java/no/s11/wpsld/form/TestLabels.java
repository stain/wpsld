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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TestLabels {
	@Test
	public void fromTerm() {
		assertEquals("Name", Labels.fromTerm("name"));
		assertEquals("Date published", Labels.fromTerm("datePublished"));
		assertEquals("Is based on", Labels.fromTerm("isBasedOn"));
		assertEquals("URL", Labels.fromTerm("url"));
		assertEquals("Content URL", Labels.fromTerm("contentUrl"));
		assertEquals("ISBN", Labels.fromTerm("isbn"));
		assertEquals("GTIN 13", Labels.fromTerm("gtin13"));
		assertEquals("URL template", Labels.fromTerm("URLTemplate"));
		assertEquals("@id", Labels.fromTerm("@id"));
	}
}
