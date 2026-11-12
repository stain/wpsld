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

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.Places.Area;

public class TestPlaces {
	private static final double DELTA = 1e-9;

	@Test
	public void point() {
		// As in the RO-Crate 1.3 specification's example, longitude first
		Area area = Places.fromWkt("<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT (150.301195 -33.7152)")
				.orElseThrow();
		assertTrue(area.isPoint());
		assertEquals(-33.7152, area.latitude(), DELTA);
		assertEquals(150.301195, area.longitude(), DELTA);
		assertEquals("https://www.openstreetmap.org/?mlat=-33.715200&mlon=150.301195#map=15/-33.715200/150.301195",
				area.openStreetMap());
		// Without a reference system, CRS84 too
		assertEquals(Optional.of(area), Places.fromWkt("POINT(150.301195 -33.7152)"));
		assertEquals(Optional.of(area), Places.fromWkt("point z (150.301195 -33.7152 1043)"));
	}

	@Test
	public void latitudeFirst() {
		assertEquals(Optional.of(Area.point(53.4668, -2.2339)),
				Places.fromWkt("<http://www.opengis.net/def/crs/EPSG/0/4326> POINT (53.4668 -2.2339)"));
	}

	@Test
	public void shapes() {
		Area polygon = Places.fromWkt("POLYGON ((10 50, 12 50, 12 52.5, 10 52.5, 10 50))").orElseThrow();
		assertEquals(new Area(50, 10, 52.5, 12), polygon);
		assertFalse(polygon.isPoint());
		assertEquals("https://www.openstreetmap.org/#map=7/51.250000/11.000000", polygon.openStreetMap());
		assertEquals(Optional.of(new Area(-1, 1, 4, 3)),
				Places.fromWkt("GEOMETRYCOLLECTION (POINT Z (1 2 3), LINESTRING (3 4, 2 -1))"));
	}

	@Test
	public void notReadable() {
		assertEquals(Optional.empty(), Places.fromWkt(""));
		assertEquals(Optional.empty(), Places.fromWkt("POINT EMPTY"));
		assertEquals(Optional.empty(), Places.fromWkt("CIRCLE (1 2)"));
		assertEquals(Optional.empty(), Places.fromWkt("POINT (1)"));
		assertEquals(Optional.empty(), Places.fromWkt("POINT (200 10)"));
		// Not WGS 84, e.g. the British National Grid
		assertEquals(Optional.empty(),
				Places.fromWkt("<http://www.opengis.net/def/crs/EPSG/0/27700> POINT (383800 398000)"));
	}

	@Test
	public void placeWithGeometry() {
		Crate crate = new Crate();
		Entity place = crate.add("Place");
		Entity geometry = crate.add("Geometry");
		geometry.set("asWKT", Optional.of(TextNode.valueOf("POINT (150.301195 -33.7152)")));
		place.set("geo", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", geometry.getId())));
		assertEquals(Places.fromWkt("POINT (150.301195 -33.7152)"), Places.of(place, crate));
		assertEquals(Places.of(place, crate), Places.of(geometry, crate));
	}

	@Test
	public void coordinates() {
		Crate crate = new Crate();
		Entity coordinates = crate.add("GeoCoordinates");
		coordinates.set("latitude", Optional.of(DoubleNode.valueOf(53.4668)));
		coordinates.set("longitude", Optional.of(TextNode.valueOf("-2.2339")));
		Entity place = crate.add("Place");
		assertEquals(Optional.empty(), Places.of(place, crate));
		place.set("geo", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", coordinates.getId())));
		assertEquals(Optional.of(Area.point(53.4668, -2.2339)), Places.of(place, crate));
		// A place referring to itself doesn't loop
		place.set("geo", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", place.getId())));
		assertEquals(Optional.empty(), Places.of(place, crate));
	}

	@Test
	public void wkt() {
		assertEquals("<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT (150.301195 -33.7152)",
				Places.wkt(-33.7152, 150.301195));
		// Rounded to about 10 cm, no exponents
		assertEquals("<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT (0 0.000001)",
				Places.wkt(0.0000012345, -0.0000000001));
		assertEquals(Places.fromWkt("POINT (2.5 1)"), Places.fromWkt(Places.wkt(1, 2.5)));
	}

	@Test
	public void setPointAddsGeometry() {
		Crate crate = new Crate();
		Entity place = crate.add("Place");
		Entity changed = Places.setPoint(place, -33.7152, 150.301195, crate::get, () -> crate.add("Geometry"));
		assertEquals(List.of("Geometry"), changed.getTypes());
		assertEquals(changed.getId(), place.get("geo").orElseThrow().path("@id").asText());
		assertEquals(Places.wkt(-33.7152, 150.301195), changed.get("asWKT").orElseThrow().asText());
		// Moved again: the same geometry
		assertSame(changed, Places.setPoint(place, 1, 2, crate::get, () -> crate.add("Geometry")));
		assertEquals(Optional.of(Area.point(1, 2)), Places.of(place, crate));
		assertEquals(3, crate.getEntities().size());
	}

	@Test
	public void setPointKeepsCoordinates() {
		Crate crate = new Crate();
		Entity place = crate.add("Place");
		Entity coordinates = crate.add("GeoCoordinates");
		place.set("geo", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", coordinates.getId())));
		assertSame(coordinates, Places.setPoint(place, 53.4668, -2.2339, crate::get, () -> {
			throw new AssertionError("No new geometry");
		}));
		assertEquals(53.4668, coordinates.get("latitude").orElseThrow().asDouble(), DELTA);
		assertEquals(-2.2339, coordinates.get("longitude").orElseThrow().asDouble(), DELTA);
		// A place with its own coordinates keeps them
		Entity other = crate.add("Place");
		other.set("latitude", Optional.of(DoubleNode.valueOf(0)));
		other.set("longitude", Optional.of(DoubleNode.valueOf(0)));
		assertSame(other, Places.setPoint(other, 10, 20, crate::get, () -> {
			throw new AssertionError("No new geometry");
		}));
		assertEquals(Optional.of(Area.point(10, 20)), Places.of(other, crate));
	}

	@Test
	public void describedFromGeoNames() {
		Crate crate = new Crate();
		Entity place = crate.add("Place");
		GeoNames.Place found = new GeoNames.Place(8152662, "Catalina Park", Optional.of("New South Wales"),
				Optional.of("Australia"), Optional.of("park"), -33.7152, 150.30119);
		Places.describe(place, found, crate::get, () -> crate.add("Geometry"));
		assertEquals("Catalina Park", place.getLabel());
		assertEquals("http://sws.geonames.org/8152662/", place.get("identifier").orElseThrow().asText());
		assertEquals("https://www.geonames.org/8152662/", place.get("url").orElseThrow().asText());
		assertEquals(Optional.of(Area.point(-33.7152, 150.30119)), Places.of(place, crate));
	}
}
