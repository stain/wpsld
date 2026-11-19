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
package no.s11.wpsld.lookup;

import static org.junit.Assert.*;

import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.test.Online;

/**
 * Check Web Availability against hosts that are wrong on purpose (badssl.com's), and the JDK it runs on,
 * as they answer today. Not part of the build: <code>./gradlew onlineTest</code>.
 */
@Category(Online.class)
public class TestWebAvailabilityOnline {
	private static final HttpClient HTTP = ProfileCache.httpClient(Duration.ofSeconds(15));

	private static WebAvailability.Result check(String address) throws Exception {
		return new WebAvailability(HTTP, Duration.ofSeconds(15)).check(address);
	}

	/** A certificate that is not accepted is said in words, once, not tried again, and not "did not answer" */
	@Test
	public void certificateProblemsAreSaidInWords() throws Exception {
		long t = System.nanoTime();
		assertEquals("Not available: the secure connection failed: its certificate has expired",
				check("https://expired.badssl.com/").problem().orElseThrow());
		String untrusted = check("https://self-signed.badssl.com/").problem().orElseThrow();
		assertTrue(untrusted, untrusted.startsWith("Not available: the secure connection failed: its certificate was issued by an authority this Java does not trust"));
		assertEquals("Not available: the secure connection failed: its certificate is for another host name",
				check("https://wrong.host.badssl.com/").problem().orElseThrow());
		// Not three tries each with pauses (which took 4 s for each)
		assertTrue("took " + (System.nanoTime() - t) / 1_000_000 + " ms", (System.nanoTime() - t) < 6_000_000_000L);
		// A host whose certificate is not accepted is not asked for its other addresses
		List<WebAvailability.Result> results = new WebAvailability(HTTP, Duration.ofSeconds(15))
				.check(List.of("https://expired.badssl.com/a", "https://expired.badssl.com/b", "https://expired.badssl.com/c"));
		assertTrue(results.toString(), results.stream().noneMatch(WebAvailability.Result::available));
		assertTrue(results.toString(), results.stream().skip(1).allMatch(r -> r.problem().get().contains("not asked any more")
				|| r.problem().get().contains("its certificate has expired")));
	}

	@Test
	public void aGoodCertificateIsAvailable() throws Exception {
		assertTrue(check("https://badssl.com/").available());
	}

	/**
	 * The JDK this runs on trusts the roots that the repositories wpsld reads crates from are issued under:
	 * HARICA's 2021 roots, under which GEANT TLS issues (KU Leuven's research data repository), which JDKs
	 * of early 2026 lacked (conda-forge's OpenJDK 25.0.2 has the 2015 roots only), and so does the Temurin
	 * that wpsld is released with, so a release built without them would fail for such sites.
	 */
	@Test
	public void theJdkTrustsHaricasRoots() throws Exception {
		TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		factory.init((KeyStore) null);
		List<String> issuers = Arrays.stream(factory.getTrustManagers()).filter(X509TrustManager.class::isInstance)
				.flatMap(m -> Arrays.stream(((X509TrustManager) m).getAcceptedIssuers())).map(X509Certificate::getSubjectX500Principal)
				.map(p -> p.getName()).toList();
		assertTrue("java " + System.getProperty("java.version"), issuers.stream().anyMatch(n -> n.contains("CN=HARICA TLS RSA Root CA 2021")));
		assertTrue(issuers.stream().anyMatch(n -> n.contains("CN=HARICA TLS ECC Root CA 2021")));
	}
}
