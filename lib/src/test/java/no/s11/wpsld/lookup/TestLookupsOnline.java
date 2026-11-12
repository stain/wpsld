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
import java.time.Duration;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import no.s11.wpsld.test.Online;

/**
 * The web services as they answer today, with records that should not
 * change, to tell when a service changes what wpsld reads from it. Not part
 * of the build: <code>./gradlew onlineTest</code>, a request or two each.
 */
@Category(Online.class)
public class TestLookupsOnline {
	private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static final Duration TIMEOUT = Duration.ofSeconds(30);
	private static final String USER_AGENT = "wpsld-tests (+https://github.com/stain/wpsld)";

	/** ORCID's fictional researcher, by iD */
	@Test
	public void orcid() throws Exception {
		List<ORCID.Person> found = new ORCID(HTTP, TIMEOUT, USER_AGENT).search("0000-0002-1825-0097");
		assertEquals("Josiah Carberry", found.get(0).name());
	}

	@Test
	public void ror() throws Exception {
		ROR ror = new ROR(HTTP, TIMEOUT, USER_AGENT);
		ROR.Organization oslo = ror.search("https://ror.org/01xtthb56").get(0);
		assertEquals("University of Oslo", oslo.name());
		assertTrue(oslo.names().stream().anyMatch(n -> n.value().equals("Universitetet i Oslo")));
		assertEquals("Oslo", oslo.location().orElseThrow().name());
		assertTrue(ror.search("University of Oslo").stream().anyMatch(o -> o.id().equals("01xtthb56")));
	}

	/** The RO-Crate article, at Crossref */
	@Test
	public void crossref() throws Exception {
		DOI.Work work = new DOI(HTTP, TIMEOUT, USER_AGENT).lookUp("10.3233/DS-210053");
		assertEquals("Crossref", work.agency());
		assertEquals("Packaging research artefacts with RO-Crate", work.title());
		assertEquals("Data Science", work.container().orElseThrow());
		assertTrue(work.authors().size() > 10);
	}

	/** The RO-Crate 1.0 specification, at DataCite */
	@Test
	public void datacite() throws Exception {
		DOI.Work work = new DOI(HTTP, TIMEOUT, USER_AGENT).lookUp("https://doi.org/10.5281/zenodo.3541888");
		assertEquals("DataCite", work.agency());
		assertTrue(work.title(), work.title().contains("RO-Crate Metadata Specification 1.0"));
		assertTrue(work.authors().stream().anyMatch(a -> a.orcid().isPresent()));
	}

	@Test
	public void doiSearch() throws Exception {
		List<DOI.Work> found = new DOI(HTTP, TIMEOUT, USER_AGENT).search("Packaging research artefacts with RO-Crate");
		assertTrue(found.stream().anyMatch(w -> w.doi().equalsIgnoreCase("10.3233/DS-210053")));
	}

	/** Catalina Park, as in RO-Crate's example; needs GEONAMES_USERNAME */
	@Test
	public void geonames() throws Exception {
		String username = System.getenv("GEONAMES_USERNAME");
		Assume.assumeTrue("Set GEONAMES_USERNAME to a GeoNames username", username != null && !username.isBlank());
		List<GeoNames.Place> found = new GeoNames(HTTP, TIMEOUT, USER_AGENT).search("Catalina Park", username);
		assertTrue(found.stream().anyMatch(p -> p.id() == 8152662));
	}
}
