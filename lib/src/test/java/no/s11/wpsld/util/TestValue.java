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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class TestValue {

	@Test
	public void lazyComputedOnFirstGetOnly() {
		AtomicInteger calls = new AtomicInteger();
		Value<String> value = Value.lazy(() -> "computed " + calls.incrementAndGet());
		assertEquals("not computed before get()", 0, calls.get());
		assertEquals("computed 1", value.get());
		assertEquals("computed 1", value.get());
		assertEquals(1, calls.get());
	}

	@Test(expected = NullPointerException.class)
	public void lazyNullNotAllowed() {
		Value.lazy(() -> null).get();
	}

	@Test(expected = NullPointerException.class)
	public void ofNullNotAllowed() {
		Value.of(null);
	}

	/**
	 * A failing computation must not be lost: the exception reaches the
	 * caller, and the next get() tries again rather than returning null.
	 */
	@Test
	public void lazyFailureReportedAndRetried() {
		AtomicInteger calls = new AtomicInteger();
		Value<String> value = Value.lazy(() -> {
			if (calls.incrementAndGet() == 1) {
				throw new IllegalStateException("first attempt fails");
			}
			return "second attempt";
		});
		try {
			value.get();
			fail("Expected the supplier's exception");
		} catch (IllegalStateException e) {
			assertEquals("first attempt fails", e.getMessage());
		}
		assertEquals("second attempt", value.get());
		assertEquals("second attempt", value.get());
		assertEquals(2, calls.get());
	}

	/**
	 * Several threads asking at once (as in the parallel schema loading)
	 * compute the value once and all get the same object.
	 */
	@Test
	public void lazyComputedOnceAcrossThreads() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		Value<Object> value = Value.lazy(() -> {
			calls.incrementAndGet();
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return new Object();
		});
		int threads = 16;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Object>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return value.get();
				}));
			}
			start.countDown();
			Object first = results.get(0).get(10, TimeUnit.SECONDS);
			for (Future<Object> result : results) {
				assertSame(first, result.get(10, TimeUnit.SECONDS));
			}
			assertEquals(1, calls.get());
		} finally {
			executor.shutdownNow();
		}
	}
}
