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
package no.s11.wpsld.test;

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Assume;
import org.junit.Test;

/**
 * The tests run offline (see {@link OfflineResolverProvider}), but this
 * machine's own names still resolve, for the tests' local web servers.
 */
public class TestOffline {
	@Test
	public void offline() throws Exception {
		Assume.assumeTrue("Run by ./gradlew test", Boolean.getBoolean("wpsld.test.offline"));
		UnknownHostException refused = assertThrows(UnknownHostException.class,
				() -> InetAddress.getByName("api.ror.org"));
		assertTrue(refused.getMessage().contains("@Category(Online.class)"));
		assertTrue(InetAddress.getByName("localhost").isLoopbackAddress());
		assertNotNull(InetAddress.getLocalHost());
	}
}
