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
package no.s11.wpsld.crate;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.test.Online;

/**
 * Retrieving a crate from a Dataverse that has RO-Crate switched on, as it
 * answers today: KU Leuven's research data repository, which gives a
 * dataset's crate as the export <code>rocrate_json</code>, advertised only in
 * the landing page's FAIR Signposting <code>Link</code> header. From the
 * dataset's DOI, and the same as a Handle (a DOI is one), through the
 * redirects to the landing page, its Signposting, and the crate. Not part of
 * the build: <code>./gradlew onlineTest</code>.
 * <p>
 * The server sends its whole chain, signed by GEANT TLS RSA 1 under the
 * HARICA TLS RSA Root CA 2021; a JDK without that root (e.g. OpenJDK 25.0.2
 * from conda-forge, which has HARICA's 2015 roots only) fails with
 * <code>PKIX path building failed</code>. Add the root (a copy of the JDK's
 * <code>cacerts</code> with it, then
 * <code>JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=… -Djavax.net.ssl.trustStorePassword=changeit"</code>).
 */
@Category(Online.class)
public class TestCrateRetrieverOnline {
	private static final Duration TIMEOUT = Duration.ofSeconds(30);
	private static final HttpClient HTTP = ProfileCache.httpClient(TIMEOUT);
	/** A published dataset, version 1.0, that should not change */
	private static final String DOI = "10.48804/XES6PY";
	private static final String EXPORT = "https://rdr.kuleuven.be/api/datasets/export?exporter=rocrate_json&persistentId=doi:"
			+ DOI;

	/**
	 * The DOI's address redirects to the landing page, whose headers have
	 * what the retriever reads: the citation, and the crate (not alone: the
	 * other export formats too, as <code>describedby</code> of the same type),
	 * and a linkset (FAIR Signposting level 2).
	 */
	@Test
	public void doiLeadsToLandingPageWithSignposting() throws Exception {
		HttpResponse<Void> landing = HTTP.send(HttpRequest.newBuilder(URI.create("https://doi.org/" + DOI))
				.timeout(TIMEOUT).header("Accept", "text/html,*/*;q=0.8").GET().build(), HttpResponse.BodyHandlers.discarding());
		assertEquals(200, landing.statusCode());
		assertEquals("rdr.kuleuven.be", landing.uri().getHost());
		String links = String.join(", ", landing.headers().allValues("Link"));
		assertTrue(links, links.contains("<https://doi.org/" + DOI + ">;rel=\"cite-as\""));
		assertTrue(links, links.contains("<" + EXPORT + ">;rel=\"describedby\";type=\"application/json\""));
		assertTrue(links, links.contains("exporter=OAI_ORE") && links.contains("exporter=schema.org"));
		assertTrue(links, links.contains("rel=\"linkset\";type=\"application/linkset+json\""));
		// What the retriever takes from it: a JSON export, here the crate's
		assertEquals(URI.create(EXPORT), CrateRetriever.linkHeader(landing.headers(), landing.uri()).orElseThrow());
	}

	@Test
	public void retrieveByDoi() throws Exception {
		checkCrate(retrieve("https://doi.org/" + DOI));
	}

	@Test
	public void retrieveByHandle() throws Exception {
		checkCrate(retrieve("https://hdl.handle.net/" + DOI));
	}

	private static CrateRetriever.Retrieved retrieve(String identifier) throws Exception {
		return new CrateRetriever(HTTP, TIMEOUT).retrieve(URI.create(identifier));
	}

	/** Found through the Signposting of the landing page, and a crate of RO-Crate 1.1 for the dataset */
	private static void checkCrate(CrateRetriever.Retrieved retrieved) {
		assertEquals(URI.create(EXPORT), retrieved.source());
		assertEquals(java.util.List.of("Link header"), retrieved.steps());
		CrateDocument document = new CrateReader().read(new ByteArrayInputStream(retrieved.content()),
				retrieved.source().toString());
		// The export's one fault, as of Dataverse 6.7: a folder named with a space has an @id that is an
		// array of its name encoded and not (an error, as RO-Crate and JSON-LD want a string), so the
		// reader drops that entity and the editor will not open it as it is. If this fails with no
		// errors, KU Leuven has fixed it: assert none.
		java.util.List<CrateDocument.Problem> errors = document.problems().stream()
				.filter(p -> p.severity() == CrateDocument.Severity.ERROR).toList();
		assertTrue(errors.toString(), errors.stream().allMatch(p -> p.message().contains("@id")));
		Crate crate = document.crate().orElseThrow();
		assertTrue(document.conformsTo().toString(), document.conformsTo().stream()
				.anyMatch(c -> c.startsWith("https://w3id.org/ro/crate/")));
		assertEquals("./", crate.getRoot().getId());
		assertTrue(crate.getRoot().getAll("identifier").stream()
				.anyMatch(v -> v.asText().equals("https://doi.org/" + DOI)));
		assertFalse(crate.getRoot().getAll("name").isEmpty());
		assertTrue(crate.getEntities().size() > 1);
		// An export with a query, not a metadata file in a folder: detached, relative ids lead nowhere on the web
		WebCrate web = WebCrate.of(retrieved.source(), document.context().getBase()).orElseThrow();
		assertFalse(web.attached());
	}
}
