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

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.lookup.DOI.Work;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

public class TestDOI {
	/** As Crossref's REST API has RO-Crate's example citation, abbreviated */
	private static final String CROSSREF = """
			{"message": {"items": [
			 {"DOI": "10.1109/tcyb.2014.2386282", "type": "journal-article", "title": ["Topic Model for Graph Mining"],
			  "author": [{"given": "Junyu", "family": "Xuan"}, {"given": "Jie", "family": "Lu"},
			             {"given": "Guangquan", "family": "Zhang"}, {"given": "Xiangfeng", "family": "Luo"}],
			  "issued": {"date-parts": [[2015, 12]]}, "container-title": ["IEEE Transactions on Cybernetics"],
			  "ISSN": ["2168-2267", "2168-2275"], "volume": "45", "issue": "12", "page": "2792-2803",
			  "publisher": "Institute of Electrical and Electronics Engineers (IEEE)"}
			]}}""";
	/** As DataCite's REST API has RO-Crate 1.0's specification, abbreviated */
	private static final String DATACITE = """
			{"data": [{"id": "10.5281/zenodo.3541888", "attributes": {
			 "doi": "10.5281/zenodo.3541888", "titles": [{"title": "RO-Crate Metadata Specification 1.0"}],
			 "creators": [
			  {"name": "Sefton, Peter", "givenName": "Peter", "familyName": "Sefton", "nameIdentifiers": [
			   {"nameIdentifier": "https://orcid.org/0000-0002-3545-944X", "nameIdentifierScheme": "ORCID"}]},
			  {"name": "Soiland-Reyes, Stian", "givenName": "Stian", "familyName": "Soiland-Reyes", "nameIdentifiers": [
			   {"nameIdentifier": "https://orcid.org/0000-0001-9842-9718", "nameIdentifierScheme": "ORCID"}]}],
			 "publisher": "Zenodo", "publicationYear": 2019,
			 "dates": [{"date": "2019-11-15", "dateType": "Issued"}],
			 "descriptions": [{"description": "<strong>Web-version:</strong> https://w3id.org/ro/crate/1.0 <p>This document specifies a method, known as <em>RO-Crate</em>.</p>", "descriptionType": "Abstract"},
			                  {"description": "Recommendation", "descriptionType": "Other"}],
			 "types": {"schemaOrg": "ScholarlyArticle", "resourceTypeGeneral": "Text"}}}]}""";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	public void doiOf() {
		assertEquals(Optional.of("10.5281/zenodo.3541888"), DOI.doiOf("https://doi.org/10.5281/zenodo.3541888"));
		assertEquals(Optional.of("10.5281/zenodo.3541888"), DOI.doiOf("http://dx.doi.org/10.5281/zenodo.3541888"));
		assertEquals(Optional.of("10.5281/zenodo.3541888"), DOI.doiOf("doi:10.5281/zenodo.3541888"));
		assertEquals(Optional.of("10.5281/zenodo.3541888"), DOI.doiOf("DOI: 10.5281/zenodo.3541888"));
		assertEquals(Optional.of("10.5281/zenodo.3541888"), DOI.doiOf("https://doi.org/10.5281%2Fzenodo.3541888"));
		assertEquals(Optional.of("10.1109/TCYB.2014.2386282"), DOI.doiOf("10.1109/TCYB.2014.2386282"));
		assertEquals(Optional.empty(), DOI.doiOf("https://zenodo.org/record/3541888"));
		assertEquals(Optional.empty(), DOI.doiOf("Topic Model for Graph Mining"));
		assertEquals("https://doi.org/10.1000/a%23b", DOI.iri("10.1000/a#b"));
	}

	@Test
	public void crossref() throws Exception {
		Work work = DOI.parseCrossref(MAPPER.readTree(CROSSREF)).get(0);
		assertEquals("ScholarlyArticle", work.type());
		assertEquals("https://doi.org/10.1109/tcyb.2014.2386282", work.iri());
		assertEquals(Optional.of("2015-12"), work.issued());
		assertEquals(List.of("2168-2267", "2168-2275"), work.issn());
		// With the journal it is in
		assertEquals("Xuan et al. (2015): Topic Model for Graph Mining. IEEE Transactions on Cybernetics",
				work.creditText());
	}

	@Test
	public void datacite() throws Exception {
		Work work = DOI.parseDatacite(MAPPER.readTree(DATACITE)).get(0);
		assertEquals("ScholarlyArticle", work.type());
		assertEquals(Optional.of("2019-11-15"), work.issued());
		assertEquals(Optional.of("0000-0002-3545-944X"), work.authors().get(0).orcid());
		assertEquals("Peter Sefton", work.authors().get(0).name());
		// The abstract as plain text, not the other descriptions
		assertEquals(Optional.of("Web-version: https://w3id.org/ro/crate/1.0\n\n"
				+ "This document specifies a method, known as RO-Crate."), work.summary());
		assertEquals("Sefton & Soiland-Reyes (2019): RO-Crate Metadata Specification 1.0", work.creditText());
		// One author; none
		assertEquals("Sefton (2019): RO-Crate Metadata Specification 1.0",
				new Work(work.doi(), work.type(), work.title(), work.authors().subList(0, 1), work.issued(),
						Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), Optional.empty(), "DataCite").creditText());
		assertEquals("RO-Crate Metadata Specification 1.0 (2019)",
				new Work(work.doi(), work.type(), work.title(), List.of(), work.issued(), Optional.empty(),
						List.of(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.empty(), "DataCite").creditText());
	}

	@Test
	public void plainText() {
		assertEquals("Presented at BOSC, Basel.\n\nAccepted & shown.", DOI.plainText(
				"<jats:p>Presented at <jats:ext-link ext-link-type=\"uri\">BOSC</jats:ext-link>,\n   Basel.</jats:p>"
						+ "<jats:p>Accepted &amp; shown.</jats:p>"));
	}

	@Test
	public void dataciteQuery() {
		assertEquals("sefton AND RO\\-Crate AND 1.0\\/2", DOI.dataciteQuery(" sefton  RO-Crate 1.0/2 "));
	}

	@Test
	public void describe() throws Exception {
		Crate crate = new Crate();
		// A co-author the crate has already, by name
		Entity lu = crate.add("Person");
		lu.set("name", Optional.of(TextNode.valueOf("Jie Lu")));
		Work work = DOI.parseCrossref(MAPPER.readTree(CROSSREF)).get(0);
		Entity article = crate.add("Thing");
		crate.rename(article, work.iri());
		DOI.describe(article, work, crate.getEntities(), (type, id) -> {
			Entity added = crate.add(type);
			if (id != null) {
				crate.rename(added, id);
			}
			return added;
		});
		assertEquals(List.of("ScholarlyArticle"), article.getTypes());
		assertEquals("Topic Model for Graph Mining", article.getLabel());
		assertEquals(work.iri(), article.get("identifier").orElseThrow().asText());
		assertEquals("2015-12", article.get("datePublished").orElseThrow().asText());
		assertEquals("Xuan et al. (2015): Topic Model for Graph Mining. IEEE Transactions on Cybernetics",
				article.get("creditText").orElseThrow().asText());
		List<JsonNode> authors = article.getAll("author");
		assertEquals(4, authors.size());
		assertEquals(lu.getId(), authors.get(1).path("@id").asText());
		Entity journal = crate.get("https://portal.issn.org/resource/ISSN/2168-2267").orElseThrow();
		assertEquals(List.of("Periodical"), journal.getTypes());
		assertEquals("IEEE Transactions on Cybernetics", journal.getLabel());
		assertEquals(journal.getId(), article.get("isPartOf").orElseThrow().path("@id").asText());

		// Authors by ORCID iD, from DataCite
		Work spec = DOI.parseDatacite(MAPPER.readTree(DATACITE)).get(0);
		Entity cited = crate.add("CreativeWork");
		DOI.describe(cited, spec, crate.getEntities(), (type, id) -> {
			Entity added = crate.add(type);
			if (id != null) {
				crate.rename(added, id);
			}
			return added;
		});
		assertEquals(List.of("CreativeWork", "ScholarlyArticle"), cited.getTypes());
		assertEquals("https://orcid.org/0000-0002-3545-944X", cited.getAll("author").get(0).path("@id").asText());
		assertTrue(cited.get("abstract").orElseThrow().asText().contains("RO-Crate"));
	}

	@Test
	public void repair() {
		Crate crate = new Crate();
		Entity root = crate.getRoot();
		Entity old = crate.add("ScholarlyArticle");
		crate.rename(old, "http://dx.doi.org/10.1109/TCYB.2014.2386282");
		Entity again = crate.add("ScholarlyArticle");
		crate.rename(again, "https://doi.org/10.1109/tcyb.2014.2386282");
		root.setAll("citation", List.of(reference("http://dx.doi.org/10.1109/TCYB.2014.2386282"),
				TextNode.valueOf("doi:10.5281/zenodo.3541888")));
		root.set("identifier", Optional.of(TextNode.valueOf("doi:10.5281/zenodo.1313066")));
		Entity file = crate.add("File");
		crate.rename(file, "10.1000/data.csv");

		assertFalse(DOI.repair(crate).isEmpty());
		// The same DOI, whatever its case: one entity
		assertSame(again, crate.get("https://doi.org/10.1109/tcyb.2014.2386282").orElseThrow());
		assertFalse(crate.getEntities().contains(old));
		assertEquals(List.of(reference("https://doi.org/10.1109/tcyb.2014.2386282"),
				reference("https://doi.org/10.5281/zenodo.3541888")), root.getAll("citation"));
		assertEquals("https://doi.org/10.5281/zenodo.1313066", root.get("identifier").orElseThrow().asText());
		// A DOI alone as @id is a path
		assertSame(file, crate.get("10.1000/data.csv").orElseThrow());
		assertEquals(List.of(), DOI.repair(crate));
	}

	@Test
	public void lookUpAndSearch() throws Exception {
		List<String> asked = new ArrayList<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().toString();
			asked.add(path);
			String body = path.startsWith("/ra/10.5281") ? "[{\"DOI\": \"10.5281/zenodo.3541888\", \"RA\": \"DataCite\"}]"
					: path.startsWith("/ra/") ? "[{\"DOI\": \"10.1109/x\", \"RA\": \"Crossref\"}]"
					: path.startsWith("/works/") ? "{\"message\": " + MAPPER.readTree(CROSSREF).path("message")
							.path("items").get(0) + "}"
					: path.startsWith("/works?") ? CROSSREF
					: path.startsWith("/dois/") ? "{\"data\": " + MAPPER.readTree(DATACITE).path("data").get(0) + "}"
					: DATACITE;
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		try {
			URI local = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
			DOI dois = new DOI(HttpClient.newHttpClient(), Duration.ofSeconds(5), "wpsld/test",
					Map.of("crossref", local, "datacite", local, "resolver", local));
			// A DOI is looked up at its agency
			assertEquals("RO-Crate Metadata Specification 1.0", dois.search("doi:10.5281/zenodo.3541888").get(0).title());
			assertEquals(List.of("/ra/10.5281%2Fzenodo.3541888", "/dois/10.5281%2Fzenodo.3541888"), asked);
			asked.clear();
			assertEquals("Topic Model for Graph Mining", dois.lookUp("10.1109/x").title());
			// A bare DOI, and a DOI's address, are looked up too, not searched for
			for (String doi : List.of("10.5281/zenodo.3541888", "https://doi.org/10.5281/zenodo.3541888",
					" http://dx.doi.org/10.5281/zenodo.3541888 ")) {
				asked.clear();
				assertEquals(1, dois.search(doi).size());
				assertEquals(doi, List.of("/ra/10.5281%2Fzenodo.3541888", "/dois/10.5281%2Fzenodo.3541888"), asked);
			}
			// A title, at both, alternately
			asked.clear();
			List<Work> found = dois.search("topic graph");
			assertEquals(List.of("Crossref", "DataCite"), found.stream().map(Work::agency).toList());
			assertEquals(List.of("/works?query.bibliographic=topic+graph&rows=10",
					"/dois?query=topic+AND+graph&page%5Bsize%5D=10"), asked);
		} finally {
			server.stop(0);
		}
	}

	private static JsonNode reference(String id) {
		return JsonNodeFactory.instance.objectNode().put("@id", id);
	}
}
