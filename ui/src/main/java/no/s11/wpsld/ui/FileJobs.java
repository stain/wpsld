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

import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import no.s11.wpsld.lookup.FileMetadata;

/**
 * The background jobs reading what files say about themselves (see
 * {@link FileMetadata}), each a JavaFX {@link Task} of the crate opened
 * when it started, on a thread of its own that mostly waits; the files
 * themselves read two at a time, by all jobs together, on a pool of two
 * threads. Jobs only read: what they read is given to the crate only when
 * the user asks, so that a job finishing never changes a crate while it is
 * being edited. A crate's jobs are cancelled when another is opened (see
 * {@link #cancelAll()}), asking first about those the user started (see
 * {@link #asked}). Called on the JavaFX thread.
 */
final class FileJobs {
	/** Two files at a time, for all jobs */
	private final ExecutorService pool = Executors.newFixedThreadPool(2,
			r -> Thread.ofPlatform().daemon().name("Reading files").unstarted(r));
	private final List<Job> jobs = new ArrayList<>();

	/**
	 * @param opened The crate opened when it started (see CrateEditor)
	 * @param asked  What it is, if the user started it, to ask whether to
	 *               stop it, e.g. "Reading what the files say about
	 *               themselves"
	 */
	private record Job(Task<?> task, Object opened, Optional<String> asked) {
	}

	/** Start a job, on a thread of its own, until done, failed or cancelled */
	<T> Task<T> start(Task<T> task, Object opened, Optional<String> asked, String name) {
		Job job = new Job(task, opened, asked);
		jobs.add(job);
		task.stateProperty().addListener((o, was, state) -> {
			if (state == Worker.State.SUCCEEDED || state == Worker.State.FAILED
					|| state == Worker.State.CANCELLED) {
				jobs.remove(job);
			}
		});
		Thread.ofPlatform().daemon().name(name).start(task);
		return task;
	}

	/** The jobs the user started still running, by what they are, with their progress */
	List<String> asked() {
		return jobs.stream().filter(j -> j.asked().isPresent())
				.map(j -> j.asked().get() + (j.task().getMessage().isBlank() ? "" : " (" + j.task().getMessage() + ")"))
				.toList();
	}

	/** Whether any job is running, e.g. in tests */
	boolean running() {
		return !jobs.isEmpty();
	}

	/** Cancel all jobs: their threads are interrupted, and what they read is dropped */
	void cancelAll() {
		List.copyOf(jobs).forEach(j -> j.task().cancel(true));
		jobs.clear();
	}

	/**
	 * A job reading files, two at a time with those of other jobs, with its
	 * progress: each reading put in the map given as read, so that what was
	 * read before it was cancelled is there
	 *
	 * @param files By @id
	 * @param into  Where to put each reading, by @id (synchronized)
	 * @return All readings, by @id
	 */
	Task<Map<String, FileMetadata.Reading>> reading(Map<String, Path> files, Map<String, FileMetadata.Reading> into) {
		return new Task<>() {
			@Override
			protected Map<String, FileMetadata.Reading> call() throws InterruptedException {
				CompletionService<Map.Entry<String, FileMetadata.Reading>> done = new ExecutorCompletionService<>(pool);
				List<Future<Map.Entry<String, FileMetadata.Reading>>> reads = new ArrayList<>();
				try {
					files.forEach((id, path) -> reads
							.add(done.submit(() -> Map.entry(id, FileMetadata.installed().read(path)))));
					for (int i = 0; i < files.size(); i++) {
						updateProgress(i, files.size());
						updateMessage(i + " of " + files.size() + " files read");
						Map.Entry<String, FileMetadata.Reading> reading = done.take().get();
						into.put(reading.getKey(), reading.getValue());
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
