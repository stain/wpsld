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
package no.s11.wpsld.util;

import static org.junit.Assert.*;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Test;

public class TestAppDirectories {

	private static List<String> strings(List<Path> paths) {
		return paths.stream().map(Path::toString).toList();
	}

	/**
	 * As Paths, which compare equal also on Windows, where they print with \
	 */
	private static List<Path> paths(String... paths) {
		return Stream.of(paths).map(Path::of).toList();
	}

	@Test
	public void xdgDefaults() {
		AppDirectories dirs = AppDirectories.forPlatform(Map.of(), "Linux", "/home/alice");
		assertEquals(Path.of("/home/alice/.cache/wpsld"), dirs.cache());
		assertEquals(Path.of("/home/alice/.local/share/wpsld"), dirs.data());
		assertEquals(Path.of("/home/alice/.config/wpsld"), dirs.config());
		assertEquals(Path.of("/home/alice/.local/state/wpsld"), dirs.state());
		assertEquals(paths("/home/alice/.local/share/wpsld", "/usr/local/share/wpsld", "/usr/share/wpsld"),
				dirs.dataDirs());
	}

	@Test
	public void xdgVariables() {
		AppDirectories dirs = AppDirectories.forPlatform(Map.of("XDG_CACHE_HOME", "/tmp/cache", "XDG_DATA_HOME",
				"/data", "XDG_CONFIG_HOME", "/cfg", "XDG_STATE_HOME", "/state", "XDG_DATA_DIRS", "/opt/share:/usr/share"),
				"Linux", "/home/alice");
		assertEquals(Path.of("/tmp/cache/wpsld"), dirs.cache());
		assertEquals(Path.of("/data/wpsld"), dirs.data());
		assertEquals(Path.of("/cfg/wpsld"), dirs.config());
		assertEquals(Path.of("/state/wpsld"), dirs.state());
		assertEquals(paths("/data/wpsld", "/opt/share/wpsld", "/usr/share/wpsld"), dirs.dataDirs());
	}

	/**
	 * Empty and relative values are ignored, as the XDG specification says.
	 */
	@Test
	public void xdgInvalidValuesIgnored() {
		AppDirectories dirs = AppDirectories.forPlatform(
				Map.of("XDG_CACHE_HOME", "", "XDG_DATA_HOME", "relative/data", "XDG_DATA_DIRS", "relative:/ok"),
				"FreeBSD", "/home/alice");
		assertEquals(Path.of("/home/alice/.cache/wpsld"), dirs.cache());
		assertEquals(Path.of("/home/alice/.local/share/wpsld"), dirs.data());
		assertEquals(paths("/home/alice/.local/share/wpsld", "/ok/wpsld"), dirs.dataDirs());
	}

	@Test
	public void windows() {
		AppDirectories dirs = AppDirectories.forPlatform(Map.of("LOCALAPPDATA", "C:\\Users\\alice\\AppData\\Local",
				"APPDATA", "C:\\Users\\alice\\AppData\\Roaming\\", "PROGRAMDATA", "C:\\ProgramData"), "Windows 11",
				"C:\\Users\\alice");
		assertEquals("C:\\Users\\alice\\AppData\\Local\\wpsld\\Cache", dirs.cache().toString());
		assertEquals("C:\\Users\\alice\\AppData\\Local\\wpsld\\State", dirs.state().toString());
		assertEquals("C:\\Users\\alice\\AppData\\Roaming\\wpsld", dirs.data().toString());
		assertEquals("C:\\Users\\alice\\AppData\\Roaming\\wpsld", dirs.config().toString());
		assertEquals(List.of("C:\\Users\\alice\\AppData\\Roaming\\wpsld", "C:\\ProgramData\\wpsld"),
				strings(dirs.dataDirs()));
	}

	@Test
	public void windowsFallbacks() {
		// Unset, or not an absolute path: from the home folder
		AppDirectories dirs = AppDirectories.forPlatform(Map.of("LOCALAPPDATA", "relative"), "Windows Server 2025",
				"C:\\Users\\bob");
		assertEquals("C:\\Users\\bob\\AppData\\Local\\wpsld\\Cache", dirs.cache().toString());
		assertEquals("C:\\Users\\bob\\AppData\\Roaming\\wpsld", dirs.data().toString());
		assertEquals(1, dirs.dataDirs().size());
		// UNC paths are absolute
		assertEquals("\\\\server\\share\\wpsld", AppDirectories
				.forPlatform(Map.of("APPDATA", "\\\\server\\share"), "Windows 10", "C:\\Users\\bob").data().toString());
	}

	@Test
	public void macOS() {
		AppDirectories dirs = AppDirectories.forPlatform(Map.of("XDG_CACHE_HOME", "/ignored"), "Mac OS X",
				"/Users/alice");
		assertEquals(Path.of("/Users/alice/Library/Caches/wpsld"), dirs.cache());
		assertEquals(Path.of("/Users/alice/Library/Application Support/wpsld"), dirs.data());
		assertEquals(dirs.data(), dirs.config());
		assertEquals(Path.of("/Users/alice/Library/Application Support/wpsld/State"), dirs.state());
		assertEquals(paths("/Users/alice/Library/Application Support/wpsld", "/Library/Application Support/wpsld"),
				dirs.dataDirs());
	}

	@Test
	public void createdForUserOnly() throws Exception {
		// Not on Windows, which has ACLs
		Assume.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		Path dir = Files.createTempDirectory("dirs").resolve("a/b/wpsld");
		assertEquals(dir, AppDirectories.create(dir));
		assertTrue(Files.isDirectory(dir));
		assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
		// Already there: fine
		AppDirectories.create(dir);
	}

	@Test
	public void system() {
		AppDirectories dirs = AppDirectories.system();
		assertTrue(dirs.cache().endsWith(AppDirectories.APP) || dirs.cache().endsWith("Cache"));
		assertFalse(dirs.dataDirs().isEmpty());
	}
}
