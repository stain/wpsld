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

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Theme works whichever of its classes is used first. Run in its own JVM
 * (forkEvery in ui/build.gradle), so that Style is set up before Theme.
 */
public class TestThemeSetUp {
	@Test
	public void styleUsedFirst() {
		assertEquals("warp", Theme.Style.WARP.id());
		assertEquals(Theme.Style.CLASSIC, Theme.style().get());
		assertEquals(Theme.Colours.LIGHT, Theme.colours());
	}
}
