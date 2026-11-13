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
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;

import javafx.animation.PauseTransition;
import javafx.application.Platform;

/**
 * Watches an attached crate's folder, and its folders, for files added,
 * changed or removed, e.g. copied in by Insert Files or in Explorer, and
 * tells once it has been quiet for a moment, so that the view isn't made
 * again for each of many files copied; while changes keep coming, at most
 * every few seconds. Not hidden folders (.git), nor more than a thousand
 * folders. Where the operating system's watching isn't available (on
 * macOS, Java polls every few seconds), View &gt; Refresh is there.
 */
final class FolderWatch {
	/** Quiet this long before telling */
	private static final javafx.util.Duration QUIET = javafx.util.Duration.millis(500);
	/** While changes keep coming, told at least this often */
	private static final java.time.Duration AT_LEAST = java.time.Duration.ofSeconds(2);
	private static final int MAX_FOLDERS = 1000;

	private final Runnable changed;
	private final PauseTransition quiet = new PauseTransition(QUIET);
	private Optional<Path> folder = Optional.empty();
	private WatchService watcher;
	private Thread thread;
	private int folders;
	/** When the first change not told yet came, in nanoseconds */
	private long pendingSince = -1;

	/**
	 * @param changed run on the JavaFX thread after changes
	 */
	FolderWatch(Runnable changed) {
		this.changed = Objects.requireNonNull(changed);
		quiet.setOnFinished(e -> tell());
	}

	/** Watch this folder from now on, or none; on the JavaFX thread */
	void watch(Optional<Path> folder) {
		if (folder.equals(this.folder)) {
			return;
		}
		stop();
		this.folder = folder;
		if (folder.isEmpty()) {
			return;
		}
		try {
			watcher = folder.get().getFileSystem().newWatchService();
		} catch (IOException | UnsupportedOperationException e) {
			// Refreshed by hand, then
			return;
		}
		WatchService service = watcher;
		folders = 0;
		register(service, folder.get());
		thread = new Thread(() -> run(service), "Folder watch");
		thread.setDaemon(true);
		thread.start();
	}

	/** Stop watching */
	void stop() {
		quiet.stop();
		pendingSince = -1;
		if (watcher != null) {
			try {
				watcher.close();
			} catch (IOException e) {
				// Closed as far as it goes
			}
			watcher = null;
		}
		folder = Optional.empty();
	}

	private void run(WatchService service) {
		try {
			while (true) {
				WatchKey key = service.take();
				for (WatchEvent<?> event : key.pollEvents()) {
					// A folder made, e.g. copied in: watched too
					if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && key.watchable() instanceof Path dir
							&& event.context() instanceof Path name && Files.isDirectory(dir.resolve(name))) {
						register(service, dir.resolve(name));
					}
				}
				key.reset();
				Platform.runLater(() -> {
					if (watcher == service) {
						changedNow();
					}
				});
			}
		} catch (InterruptedException | ClosedWatchServiceException e) {
			// Stopped
		}
	}

	/** A change: told once quiet, or now if changes kept coming a while */
	private void changedNow() {
		long now = System.nanoTime();
		if (pendingSince < 0) {
			pendingSince = now;
		}
		if (now - pendingSince >= AT_LEAST.toNanos()) {
			quiet.stop();
			tell();
		} else {
			quiet.playFromStart();
		}
	}

	private void tell() {
		pendingSince = -1;
		changed.run();
	}

	/** Watch a folder and those in it, not hidden ones */
	private void register(WatchService service, Path start) {
		try {
			Files.walkFileTree(start, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					if (!dir.equals(start) && dir.getFileName().toString().startsWith(".")) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					if (folders >= MAX_FOLDERS) {
						return FileVisitResult.TERMINATE;
					}
					dir.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
							StandardWatchEventKinds.ENTRY_MODIFY);
					folders++;
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e) {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException | ClosedWatchServiceException e) {
			// Watched as far as it went
		}
	}
}
