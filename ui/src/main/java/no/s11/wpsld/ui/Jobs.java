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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import no.s11.wpsld.lookup.FileMetadata;

/**
 * The editor's background jobs, e.g. reading what files say about
 * themselves, checking web resources and copying files in: each a JavaFX
 * {@link Task} with what it is and its status lines, of the crate opened
 * when it started (see CrateEditor), shown at the foot of the window and
 * in a window of their own (see {@link JobsView}). A crate's jobs are
 * cancelled when another is opened (see {@link #cancelAll()}), asking
 * first about those the user started (see {@link #asked}). Jobs reading
 * files mostly wait, on threads of their own, as the files themselves are
 * read two at a time, by all jobs together, on a pool of two threads. Jobs
 * give the crate nothing themselves: what they found is given when the
 * user asks, so that a job finishing never changes a crate while it is
 * being edited. Called on the JavaFX thread, but for {@link Job#say}.
 */
final class Jobs {
	/** Two files at a time, for all jobs */
	private final ExecutorService pool = Executors.newFixedThreadPool(2,
			r -> Thread.ofPlatform().daemon().name("Reading files").unstarted(r));
	private final ObservableList<Job> running = FXCollections.observableArrayList();

	/** A background job: its task, what it is, and what it has to say */
	static final class Job {
		private final String title;
		private final Object opened;
		private final boolean asked;
		private final ObservableList<String> lines = FXCollections.observableArrayList();
		private final javafx.beans.property.StringProperty item = new javafx.beans.property.SimpleStringProperty("");
		private Task<?> task;
		private Runnable cancel;
		private Optional<Runnable> show = Optional.empty();

		private Job(String title, Object opened, boolean asked) {
			this.title = title;
			this.opened = opened;
			this.asked = asked;
		}

		/** What it is, e.g. "Reading what the files say about themselves" */
		String title() {
			return title;
		}

		Task<?> task() {
			return task;
		}

		/** Its status lines, e.g. the files that couldn't be read */
		ObservableList<String> lines() {
			return lines;
		}

		/** Bring up its own window, if it has one rather than the jobs' window */
		Optional<Runnable> show() {
			return show;
		}

		/** What it is at, e.g. the file being read; empty if nothing in particular */
		javafx.beans.property.ReadOnlyStringProperty item() {
			return item;
		}

		/** Say what it is at, from any thread */
		void at(String what) {
			Platform.runLater(() -> item.set(what));
		}

		/** Add a status line, from any thread */
		void say(String line) {
			Platform.runLater(() -> lines.add(line));
		}

		void cancel() {
			cancel.run();
		}
	}

	/**
	 * A job, not started yet
	 *
	 * @param opened The crate opened when it starts (see CrateEditor)
	 * @param asked  Whether the user started it, to ask whether to stop it
	 *               when another crate is opened
	 */
	Job job(String title, Object opened, boolean asked) {
		return new Job(title, opened, asked);
	}

	/** Start a job's task, on a thread of its own, until done, failed or cancelled */
	<T> Task<T> start(Job job, Task<T> task) {
		track(job, task, () -> task.cancel(true), null);
		Thread.ofPlatform().daemon().name(job.title()).start(task);
		return task;
	}

	/**
	 * Show a task started elsewhere as a job, e.g. copying files, which has
	 * its own window
	 *
	 * @param cancel How to cancel it
	 * @param show   How to bring up its own window, if any
	 */
	<T> Task<T> track(Job job, Task<T> task, Runnable cancel, Runnable show) {
		job.task = task;
		job.cancel = cancel;
		job.show = Optional.ofNullable(show);
		if (done(task.getState())) {
			return task;
		}
		running.add(job);
		task.stateProperty().addListener((o, was, state) -> {
			if (done(state)) {
				running.remove(job);
			}
		});
		return task;
	}

	private static boolean done(Worker.State state) {
		return state == Worker.State.SUCCEEDED || state == Worker.State.FAILED || state == Worker.State.CANCELLED;
	}

	/** The jobs running, the latest last */
	ObservableList<Job> running() {
		return running;
	}

	/** The jobs the user started still running, by what they are, with their progress */
	List<String> asked() {
		return running.stream().filter(j -> j.asked)
				.map(j -> j.title() + (j.task().getMessage().isBlank() ? "" : " (" + j.task().getMessage() + ")"))
				.toList();
	}

	/**
	 * Cancel all jobs: their threads are interrupted (copying stops at its
	 * next block), and what they found is dropped
	 */
	void cancelAll() {
		List.copyOf(running).forEach(Job::cancel);
	}

	/**
	 * A job reading what files say, two at a time with those of other jobs,
	 * with its progress, and a status line for each that couldn't be read
	 * fully: each reading put in the map given as read, so that what was
	 * read before it was cancelled is there
	 *
	 * @param files By @id
	 * @param into  Where to put each reading, by @id (synchronized)
	 * @return All readings, by @id
	 */
	Task<Map<String, FileMetadata.Reading>> reading(Job job, Map<String, Path> files,
			Map<String, FileMetadata.Reading> into) {
		return new Task<>() {
			@Override
			protected Map<String, FileMetadata.Reading> call() throws InterruptedException {
				CompletionService<Map.Entry<String, FileMetadata.Reading>> done = new ExecutorCompletionService<>(pool);
				List<Future<Map.Entry<String, FileMetadata.Reading>>> reads = new ArrayList<>();
				try {
					files.forEach((id, path) -> reads.add(done.submit(() -> {
						job.at(id);
						return Map.entry(id, FileMetadata.installed().read(path));
					})));
					for (int i = 0; i < files.size(); i++) {
						updateProgress(i, files.size());
						updateMessage(i + " of " + files.size() + " files read");
						Map.Entry<String, FileMetadata.Reading> reading = done.take().get();
						into.put(reading.getKey(), reading.getValue());
						reading.getValue().problems().forEach(p -> job.say(reading.getKey() + ": " + p));
					}
				} catch (ExecutionException e) {
					throw new IllegalStateException(e.getCause());
				} catch (CancellationException e) {
					throw new InterruptedException("Cancelled");
				} finally {
					// Those not read yet, or being read: interrupted, as the
					// pool is all jobs'
					reads.forEach(f -> f.cancel(true));
				}
				synchronized (into) {
					return new LinkedHashMap<>(into);
				}
			}
		};
	}
}
