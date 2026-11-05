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
package no.s11.wpsld.soss;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

public class TestClassDef {

	/**
	 * Like SchemaFromJsonLd: classes are created before the classes they
	 * refer to exist, and references are only resolved on first use.
	 */
	@Test
	public void forwardReferencesAndCycles() {
		URI a = URI.create("http://example.com/A");
		URI b = URI.create("http://example.com/B");
		URI c = URI.create("http://example.com/C");
		Map<URI, ClassDef> classes = new HashMap<>();
		// A refers to B before B is created; A and B are in a cycle
		classes.put(a, new ClassDef(a, Optional.of("A"), Optional.empty(), () -> List.of(classes.get(b))));
		classes.put(b, new ClassDef(b, Optional.of("B"), Optional.empty(),
				() -> List.of(classes.get(a), classes.get(c))));
		classes.put(c, new ClassDef(c, Optional.of("C"), Optional.empty(), List::of));

		ClassDef classA = classes.get(a);
		assertEquals(List.of(classes.get(b)), classA.getSubClassOf());
		// Terminates despite the cycle, each class once, most specific first
		assertEquals(List.of(classA, classes.get(b), classes.get(c)), List.copyOf(classA.getSelfAndSuperClasses()));
		assertEquals(List.of(classes.get(b), classA, classes.get(c)),
				List.copyOf(classes.get(b).getSelfAndSuperClasses()));
	}
}
