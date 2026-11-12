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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.stream.Stream;

/**
 * Keeps tests off the network: with the system property
 * <code>wpsld.test.offline</code> true, as <code>./gradlew test</code> sets
 * it, host names other than this machine's don't resolve, so a test that
 * would reach a web service fails at once, saying why. Tests of web services
 * belong in the {@link Online} category instead. Found by the JDK through
 * <code>META-INF/services</code> on the tests' class path.
 */
public class OfflineResolverProvider extends InetAddressResolverProvider {
	@Override
	public InetAddressResolver get(Configuration configuration) {
		InetAddressResolver builtin = configuration.builtinResolver();
		if (!Boolean.getBoolean("wpsld.test.offline")) {
			return builtin;
		}
		String local = configuration.lookupLocalHostName();
		return new InetAddressResolver() {
			@Override
			public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
				if (host.equalsIgnoreCase("localhost") || host.equalsIgnoreCase(local)) {
					return builtin.lookupByName(host, policy);
				}
				throw new UnknownHostException(host + ": tests run offline; a test of a web service belongs in"
						+ " @Category(Online.class), run by ./gradlew onlineTest");
			}

			@Override
			public String lookupByAddress(byte[] address) throws UnknownHostException {
				return builtin.lookupByAddress(address);
			}
		};
	}

	@Override
	public String name() {
		return "wpsld offline tests";
	}
}
