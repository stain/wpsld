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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where the application keeps its files for the user, following the
 * platform's conventions, each with a <code>wpsld</code> folder. Nothing is
 * created until {@link #create(Path)} is called.
 *
 * @param cache    Non-essential files, like downloaded copies, that may be
 *                 deleted at any time
 * @param data     Data kept for the user, like profiles they add
 * @param config   The user's settings
 * @param state    State to keep between runs that is not worth backing up,
 *                 like recent files
 * @param dataDirs Folders to look for data in, most important first: the
 *                 user's {@link #data()}, then those installed for all users
 */
public record AppDirectories(Path cache, Path data, Path config, Path state, List<Path> dataDirs) {
	public static final String APP = "wpsld";

	public AppDirectories {
		dataDirs = List.copyOf(dataDirs);
	}

	/**
	 * For the running application: its environment, OS and home folder.
	 */
	public static AppDirectories system() {
		return forPlatform(System.getenv(), System.getProperty("os.name", ""), System.getProperty("user.home", ""));
	}

	/**
	 * @param env    Environment variables
	 * @param osName As the <code>os.name</code> system property, e.g. "Linux",
	 *               "Windows 11" or "Mac OS X"
	 * @param home   The user's home folder
	 */
	public static AppDirectories forPlatform(Map<String, String> env, String osName, String home) {
		String os = osName.toLowerCase(Locale.ROOT);
		if (os.startsWith("windows")) {
			return windows(env, home);
		}
		if (os.startsWith("mac") || os.startsWith("darwin")) {
			return macOS(home);
		}
		return xdg(env, home);
	}

	/**
	 * Linux and other Unix: the
	 * <a href="https://specifications.freedesktop.org/basedir-spec/latest/">XDG Base
	 * Directory Specification</a>: <code>$XDG_CACHE_HOME</code> (else
	 * <code>~/.cache</code>), <code>$XDG_DATA_HOME</code> (else
	 * <code>~/.local/share</code>), <code>$XDG_CONFIG_HOME</code> (else
	 * <code>~/.config</code>), <code>$XDG_STATE_HOME</code> (else
	 * <code>~/.local/state</code>), and the search path
	 * <code>$XDG_DATA_DIRS</code> (else <code>/usr/local/share/:/usr/share/</code>).
	 * Values that are not absolute paths are ignored, as the specification says.
	 */
	public static AppDirectories xdg(Map<String, String> env, String home) {
		Path data = xdgHome(env, "XDG_DATA_HOME", home, ".local/share");
		List<Path> dataDirs = new ArrayList<>(List.of(data));
		String value = env.getOrDefault("XDG_DATA_DIRS", "");
		List<String> system = value.isBlank() ? List.of("/usr/local/share/", "/usr/share/") : List.of(value.split(":"));
		system.stream().filter(p -> p.startsWith("/")).map(p -> Path.of(p, APP)).forEach(dataDirs::add);
		return new AppDirectories(xdgHome(env, "XDG_CACHE_HOME", home, ".cache"), data,
				xdgHome(env, "XDG_CONFIG_HOME", home, ".config"), xdgHome(env, "XDG_STATE_HOME", home, ".local/state"),
				dataDirs);
	}

	private static Path xdgHome(Map<String, String> env, String variable, String home, String fallback) {
		String value = env.getOrDefault(variable, "");
		// Unset, empty or relative values are ignored
		return (value.startsWith("/") ? Path.of(value) : Path.of(home, fallback)).resolve(APP);
	}

	/**
	 * Windows: <code>%LOCALAPPDATA%\wpsld\Cache</code> and <code>...\State</code>,
	 * <code>%APPDATA%\wpsld</code> for data and settings (roaming with the user),
	 * and <code>%PROGRAMDATA%\wpsld</code> for data installed for all users.
	 * Paths are built as strings, so they can be tested on any platform.
	 */
	public static AppDirectories windows(Map<String, String> env, String home) {
		String local = windowsVariable(env, "LOCALAPPDATA").orElse(home + "\\AppData\\Local") + "\\" + APP;
		String roaming = windowsVariable(env, "APPDATA").orElse(home + "\\AppData\\Roaming") + "\\" + APP;
		List<Path> dataDirs = new ArrayList<>(List.of(Path.of(roaming)));
		windowsVariable(env, "PROGRAMDATA").ifPresent(p -> dataDirs.add(Path.of(p + "\\" + APP)));
		return new AppDirectories(Path.of(local + "\\Cache"), Path.of(roaming), Path.of(roaming),
				Path.of(local + "\\State"), dataDirs);
	}

	/**
	 * An absolute Windows path from a variable (drive or UNC), without a
	 * trailing separator.
	 */
	private static Optional<String> windowsVariable(Map<String, String> env, String variable) {
		String value = env.get(variable);
		if (value == null || !(value.matches("^[A-Za-z]:[\\\\/].*") || value.startsWith("\\\\"))) {
			return Optional.empty();
		}
		return Optional.of(value.replaceAll("[\\\\/]+$", ""));
	}

	/**
	 * macOS: <code>~/Library/Caches/wpsld</code>, and
	 * <code>~/Library/Application Support/wpsld</code> for data, settings and
	 * state (<code>~/Library/Preferences</code> is for property lists), and
	 * <code>/Library/Application Support/wpsld</code> for data installed for
	 * all users.
	 */
	public static AppDirectories macOS(String home) {
		Path support = Path.of(home, "Library", "Application Support", APP);
		return new AppDirectories(Path.of(home, "Library", "Caches", APP), support, support, support.resolve("State"),
				List.of(support, Path.of("/Library", "Application Support", APP)));
	}

	/**
	 * Create a folder (and its parents) if needed, readable only by the user
	 * where the file system supports that, as the XDG specification asks.
	 */
	public static Path create(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			Files.createDirectories(dir);
			if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
				Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
			}
		}
		return dir;
	}
}
