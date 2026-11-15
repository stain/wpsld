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
package no.s11.wpsld.readers.image;

import java.util.List;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import no.s11.wpsld.test.Damage;
import no.s11.wpsld.test.Damaged;

/** metadata-extractor on damaged images (see {@link Damage}) */
@Category(Damaged.class)
public class TestDamagedImages {
	private static byte[] resource(String name) throws Exception {
		return java.nio.file.Files.readAllBytes(TestImageMetadata.resource(name));
	}

	@Test
	public void jpeg() throws Exception {
		Damage.readAll(List.of(new ImageMetadata()), "photo.jpg", resource("photo.jpg"), 1);
	}

	@Test
	public void png() throws Exception {
		Damage.readAll(List.of(new ImageMetadata()), "picture.png", resource("picture.png"), 2);
	}

	@Test
	public void heic() throws Exception {
		Damage.readAll(List.of(new ImageMetadata()), "photo.heic", resource("photo.heic"), 3);
	}

	@Test
	public void gif() throws Exception {
		Damage.readAll(List.of(new ImageMetadata()), "picture.gif", resource("picture.gif"), 4);
	}

	@Test
	public void mp4() throws Exception {
		Damage.readAll(List.of(new ImageMetadata()), "clip.mp4", resource("clip.mp4"), 5);
	}

	@Test
	public void mov() throws Exception {
		Damage.readAll(List.of(new ImageMetadata()), "clip.mov", resource("clip.mov"), 6);
	}
}
