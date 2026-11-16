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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The log files: two wpsld running at once, each a process of its own,
 * each log to their own, and clearing empties both, each logging on.
 */
public class TestLogs {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@After
	public void close() {
		Logs.close();
	}

	@Test
	public void twoAtOnce() throws Exception {
		Path dir = temp.getRoot().toPath().resolve("logs");
		Files.createDirectories(dir);
		Process other = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(), "-cp",
				System.getProperty("java.class.path"), Other.class.getName(), dir.toString())
				.redirectError(ProcessBuilder.Redirect.INHERIT).start();
		try (BufferedReader out = new BufferedReader(
				new InputStreamReader(other.getInputStream(), StandardCharsets.UTF_8))) {
			assertEquals("logging", out.readLine());

			Logs.open(dir);
			Logger logger = Logger.getLogger(TestLogs.class.getName());
			logger.warning("from this one");
			Path others = dir.resolve("wpsld.0.log");
			Path ours = dir.resolve("wpsld.0.log.1");
			assertTrue(Files.readString(others).contains("from the other"));
			assertTrue(Files.readString(ours).contains("from this one"));
			assertFalse(Files.readString(others).contains("from this one"));
			assertEquals(List.of(others, ours), Logs.files());

			// Both emptied, each logging on
			assertEquals(2, Logs.clear());
			assertEquals(List.of(others, ours), Logs.files());
			logger.warning("after clearing");
			String now = Files.readString(ours);
			assertFalse(now, now.contains("from this one"));
			assertTrue(now, now.contains("after clearing"));
			assertEquals("", Files.readString(others));
			other.getOutputStream().close();
			assertEquals(0, other.waitFor());
			String theirs = Files.readString(others);
			assertTrue(theirs, theirs.contains("from the other, again"));
			assertFalse(theirs, theirs.contains("from the other\n"));
			assertFalse(theirs, theirs.contains("\0"));
		} finally {
			other.destroyForcibly();
		}
	}

	@Test
	public void level() {
		Logger root = Logger.getLogger("");
		Level was = root.getLevel();
		try {
			Logs.level("WARNING");
			assertFalse(Logger.getLogger(TestLogs.class.getName()).isLoggable(Level.INFO));
			Logs.level("FINE");
			assertTrue(Logger.getLogger(TestLogs.class.getName()).isLoggable(Level.FINE));
		} finally {
			root.setLevel(was);
		}
	}

	/** Another wpsld: logs until its input ends */
	public static final class Other {
		public static void main(String[] args) throws Exception {
			FileHandler handler = Logs.handler(Path.of(args[0]));
			handler.publish(new LogRecord(Level.WARNING, "from the other"));
			handler.flush();
			System.out.println("logging");
			System.out.flush();
			System.in.readAllBytes();
			handler.publish(new LogRecord(Level.WARNING, "from the other, again"));
			handler.close();
		}
	}
}
