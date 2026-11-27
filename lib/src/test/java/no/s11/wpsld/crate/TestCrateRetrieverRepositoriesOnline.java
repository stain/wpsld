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
import java.time.Duration;
import java.util.List;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.test.Online;

/**
 * Opening crates from other repositories, as they answer today (see also
 * {@link TestCrateRetrieverOnline} for KU Leuven's Dataverse): to tell when
 * one changes what wpsld reads from it. Not part of the build:
 * <code>./gradlew onlineTest</code>. Not tested here, as they fail by the
 * repositories' own choice, not for wpsld to get round: RADAR answers HTTP 403
 * to its crate export, and HUN-REN's Dataverse is behind a bot wall (Anubis);
 * ROHub's crate download times out at its own gateway (HTTP 504) and is only
 * advertised in a linkset, which {@link CrateRetriever} does not follow yet.
 */
@Category(Online.class)
public class TestCrateRetrieverRepositoriesOnline {
	private static final Duration TIMEOUT = Duration.ofSeconds(40);
	private static final HttpClient HTTP = ProfileCache.httpClient(TIMEOUT);

	private static CrateRetriever.Retrieved retrieve(String address) throws Exception {
		return new CrateRetriever(HTTP, TIMEOUT).retrieve(URI.create(address));
	}

	private static CrateDocument read(CrateRetriever.Retrieved retrieved) {
		return new CrateReader().read(new ByteArrayInputStream(retrieved.content()), retrieved.source().toString());
	}

	/**
	 * OneStop4WildLIVE: a camera trap observation, from the page's Signposting
	 * <code>Link</code> header, to an API call: a detached crate (an address with
	 * a query) with absolute @ids, of RO-Crate 1.2's draft, whose context is not
	 * one wpsld knows yet, so its terms give warnings.
	 */
	@Test
	public void wildlive() throws Exception {
		String page = "https://wildlive.senckenberg.de/captureevent/wildlive/963d905b946c6119ed5f";
		CrateRetriever.Retrieved retrieved = retrieve(page);
		assertEquals(List.of("Link header"), retrieved.steps());
		assertEquals(java.util.Optional.of(URI.create(page)), retrieved.landing());
		assertTrue(retrieved.source().toString(), retrieved.source().getQuery().contains("method=getAsROCrate"));
		CrateDocument document = read(retrieved);
		assertFalse(document.problems().toString(), document.hasErrors());
		assertEquals(List.of("https://w3id.org/ro/crate/1.2-DRAFT"), document.conformsTo());
		Crate crate = document.crate().orElseThrow();
		assertEquals(page, crate.getRoot().getId());
		WebCrate web = WebCrate.of(retrieved, document.context().getBase()).orElseThrow();
		assertFalse(web.attached());
		assertEquals(List.of(), crate.relativeEntities());
		// Its files say where they can be downloaded
		assertTrue(crate.dataEntities().stream().anyMatch(e -> Crate.downloadAddress(e).isPresent()));
	}

	/**
	 * MLentory: a machine learning model, found from a <code>&lt;link&gt;</code> in
	 * the page's HTML to a <code>ro-crate-metadata.json</code> in a folder, so an
	 * attached crate on the web; with faults of its own (an invalid
	 * <code>@dcterms</code> keyword in its context, an entity nested in a value),
	 * which are warnings.
	 */
	@Test
	public void mlentory() throws Exception {
		String page = "https://mlentory.zbmed.de/mlentory_graph/bb2388219e466a163cfc4a608f084b6faedbaa9ee330d44d056d0bde6e34582a";
		CrateRetriever.Retrieved retrieved = retrieve(page);
		assertEquals(List.of("HTML link"), retrieved.steps());
		assertEquals(URI.create(page + "/ro-crate-metadata.json"), retrieved.source());
		CrateDocument document = read(retrieved);
		assertFalse(document.problems().toString(), document.hasErrors());
		assertEquals(List.of("https://w3id.org/ro/crate/1.1"), document.conformsTo());
		Crate crate = document.crate().orElseThrow();
		assertTrue(crate.getRoot().getAll("name").get(0).asText().startsWith("RO-Crate for machine learning model"));
		WebCrate web = WebCrate.of(retrieved, document.context().getBase()).orElseThrow();
		assertTrue(web.attached());
		assertEquals(java.util.Optional.of(URI.create(page + "/")), web.base());
	}

	/**
	 * UK CEH's catalogue: its content negotiation answers
	 * <code>application/ld+json;profile="https://w3id.org/ro/crate"</code>, but
	 * with its own metadata, which is not an RO-Crate (no <code>@graph</code>); it
	 * has no Signposting and no other way to a crate. Until retrieving probes what
	 * it is given, {@link CrateRetriever} hands it back as the crate, and
	 * {@link CrateReader} says what is wrong: then this should be that there is no
	 * RO-Crate to be had.
	 */
	@Test
	public void cehClaimsTheProfileButIsNotACrate() throws Exception {
		CrateRetriever.Retrieved retrieved = retrieve("https://doi.org/10.5285/3970a138-c035-40ac-bf1d-a2f8a464644a");
		assertEquals(List.of("content negotiation"), retrieved.steps());
		assertEquals(URI.create("https://catalogue.ceh.ac.uk/documents/3970a138-c035-40ac-bf1d-a2f8a464644a"), retrieved.source());
		CrateDocument document = read(retrieved);
		assertTrue(document.crate().isEmpty());
		assertTrue(document.problems().toString(), document.problems().stream().anyMatch(p -> p.message().startsWith("No @graph array")));
	}
}
