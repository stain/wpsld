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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

import no.s11.wpsld.Settings;
import no.s11.wpsld.util.AppDirectories;

/**
 * The application's log, in the user's log folder
 * ({@link AppDirectories#logs()}, e.g. <code>~/.local/state/wpsld/logs</code>,
 * <code>%LOCALAPPDATA%\wpsld\Logs</code> or <code>~/Library/Logs/wpsld</code>),
 * as <code>wpsld.0.log</code> (the latest) to <code>wpsld.2.log</code>, as
 * the Windows launcher has no console to write to; and on the console, when
 * started from one. How much is logged is a preference
 * ({@link Settings#logLevel()}), and the logs can be cleared. When two run
 * at once, the second logs to <code>wpsld.0.log.1</code> and so on, as
 * {@link FileHandler} does when another has locked a log (its
 * <code>.lck</code> file, also on Windows); clearing empties all. All logs through the JDK's own logging
 * (<code>java.util.logging</code>): wpsld and JavaFX through
 * {@link System.Logger}, Tika's SLF4J through slf4j-jdk14, PDFBox's Commons
 * Logging by itself, and the JDK's HTTP client its errors. The levels are
 * in <code>logging.properties</code> beside this class; a configuration
 * given with <code>-Djava.util.logging.config.file</code> or
 * <code>.class</code> is used instead.
 */
final class Logs {
	/**
	 * The most of each log file, in bytes, after which it is renamed
	 * <code>wpsld.1.log</code> (and so on) and a new one started
	 */
	private static final int LIMIT = 20 << 20;
	/** How many log files to keep, so at most 60 MB for each running */
	private static final int COUNT = 3;
	/**
	 * Log files, as {@link FileHandler} names them: the generation, and
	 * which set, if another had the first locked
	 */
	private static final Pattern FILES = Pattern.compile("wpsld\\.\\d+\\.log(\\.\\d+)?");

	/** The folder logged to, if any */
	private static Path folder;
	private static FileHandler file;

	private Logs() {
	}

	/**
	 * Log to the user's log folder, if it can be made, and log what is
	 * thrown and not caught. Called first, before anything logs.
	 *
	 * @return The log folder, if logged to
	 */
	static Optional<Path> setUp() {
		// What the JDK's HTTP client logs (through System.Logger): its
		// errors, not each request, as their addresses may carry searches
		// and GeoNames' username
		if (System.getProperty("jdk.httpclient.HttpClient.log") == null) {
			System.setProperty("jdk.httpclient.HttpClient.log", "errors");
		}
		Logger root = Logger.getLogger("");
		Optional<Path> logged = Optional.empty();
		if (System.getProperty("java.util.logging.config.file") == null
				&& System.getProperty("java.util.logging.config.class") == null) {
			try (InputStream in = Logs.class.getResourceAsStream("logging.properties")) {
				LogManager.getLogManager().readConfiguration(in);
			} catch (IOException | RuntimeException e) {
				root.log(Level.WARNING, "Logging not configured", e);
			}
			Path dir = AppDirectories.system().logs();
			try {
				open(dir);
				logged = Optional.of(dir);
			} catch (IOException | RuntimeException e) {
				root.log(Level.WARNING, "Can't log to " + dir, e);
			}
			level(Settings.load(Settings.userFile()).logLevel());
		}
		Thread.setDefaultUncaughtExceptionHandler((thread, e) -> Logger.getLogger(CrateEditor.class.getName())
				.log(Level.SEVERE, "Uncaught in thread " + thread.getName(), e));
		Logger.getLogger(CrateEditor.class.getName()).info(() -> "wpsld " + AboutDialog.version() + " on Java "
				+ Runtime.version() + " (" + System.getProperty("java.vendor") + "), " + System.getProperty("os.name")
				+ " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
		return logged;
	}

	/** Log to a folder, as well as to anything else */
	static synchronized void open(Path dir) throws IOException {
		AppDirectories.create(dir);
		file = handler(dir);
		Logger.getLogger("").addHandler(file);
		folder = dir;
	}

	/** Stop logging to the folder */
	static synchronized void close() {
		if (file != null) {
			Logger.getLogger("").removeHandler(file);
			file.close();
		}
		file = null;
		folder = null;
	}

	/**
	 * A handler writing to the logs in a folder: the first set of logs no
	 * other has locked
	 */
	static FileHandler handler(Path dir) throws IOException {
		// Forward slashes, as FileHandler's patterns have; the folder's % as %%
		FileHandler handler = new FileHandler(
				dir.toAbsolutePath().toString().replace("%", "%%").replace('\\', '/') + "/wpsld.%g.log", LIMIT, COUNT,
				true);
		handler.setEncoding(StandardCharsets.UTF_8.name());
		handler.setFormatter(new SimpleFormatter());
		return handler;
	}

	/** The folder logged to, if any */
	static synchronized Optional<Path> folder() {
		return Optional.ofNullable(folder);
	}

	/**
	 * Log this much, e.g. "WARNING" for warnings and errors only: a level of
	 * java.util.logging, as {@link Settings#logLevel()}
	 */
	static void level(String level) {
		try {
			Logger.getLogger("").setLevel(Level.parse(level));
		} catch (IllegalArgumentException e) {
			Logger.getLogger(Logs.class.getName()).warning(() -> "No log level " + level);
		}
	}

	/** The log files there are, also those of others running, by name */
	static synchronized List<Path> files() {
		List<Path> files = new ArrayList<>();
		if (folder != null && Files.isDirectory(folder)) {
			try (DirectoryStream<Path> found = Files.newDirectoryStream(folder,
					f -> FILES.matcher(f.getFileName().toString()).matches())) {
				found.forEach(files::add);
			} catch (IOException e) {
				Logger.getLogger(Logs.class.getName()).log(Level.WARNING, "Can't list the logs in " + folder, e);
			}
		}
		files.sort(null);
		return files;
	}

	/**
	 * Empty the log files, also those of another wpsld running at the same
	 * time: truncated rather than deleted, as a log may be open, by this or
	 * another process, which then writes on at its new end (logs are
	 * appended to, and opened to be shared, also on Windows). Lock files are
	 * left, as deleting one another has locked would let a third write to
	 * its logs.
	 *
	 * @return How many had something in them
	 */
	static synchronized int clear() throws IOException {
		int cleared = 0;
		IOException failed = null;
		for (Path log : files()) {
			try (FileChannel channel = FileChannel.open(log, StandardOpenOption.WRITE)) {
				if (channel.size() > 0) {
					channel.truncate(0);
					cleared++;
				}
			} catch (IOException e) {
				Logger.getLogger(Logs.class.getName()).log(Level.WARNING, "Can't clear " + log, e);
				failed = failed == null ? e : failed;
			}
		}
		Logger.getLogger(Logs.class.getName()).info("Logs cleared");
		if (failed != null && cleared == 0) {
			throw failed;
		}
		return cleared;
	}
}
