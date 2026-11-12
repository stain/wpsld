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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Where on Earth a place is, as RO-Crate describes places: a
 * <code>Place</code> whose <code>geo</code> is a <code>Geometry</code> with
 * Well-Known Text in <code>asWKT</code> (RO-Crate 1.2 and later), or, as
 * earlier versions showed, <code>GeoCoordinates</code> or the place's own
 * <code>latitude</code> and <code>longitude</code>.
 *
 * @see <a href="https://w3id.org/ro/crate/1.3/contextual-entities#places">RO-Crate 1.3: Places</a>
 */
public final class Places {
	/** How many <code>geo</code> links to follow, e.g. Place → Geometry */
	private static final int MAX_DEPTH = 3;
	/** An optional coordinate reference system, then the geometry */
	private static final Pattern WKT = Pattern.compile("\\s*(?:<([^>]*)>)?\\s*([A-Za-z]+)\\s*(?:ZM|Z|M)?\\s*(.*)",
			Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
	private static final List<String> WKT_TYPES = List.of("POINT", "LINESTRING", "POLYGON", "MULTIPOINT",
			"MULTILINESTRING", "MULTIPOLYGON", "GEOMETRYCOLLECTION", "TRIANGLE", "TIN", "POLYHEDRALSURFACE");
	private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

	private Places() {
	}

	/**
	 * A box on the Earth, in degrees of WGS 84; a point if it has no size.
	 */
	public record Area(double south, double west, double north, double east) {
		public static Area point(double latitude, double longitude) {
			return new Area(latitude, longitude, latitude, longitude);
		}

		public boolean isPoint() {
			return south == north && west == east;
		}

		public double latitude() {
			return (south + north) / 2;
		}

		public double longitude() {
			return (west + east) / 2;
		}

		Area union(Area other) {
			return new Area(Math.min(south, other.south), Math.min(west, other.west), Math.max(north, other.north),
					Math.max(east, other.east));
		}

		/**
		 * The area on OpenStreetMap: a marker on a point, or the map zoomed
		 * to fit a larger area.
		 */
		public String openStreetMap() {
			String lat = String.format(Locale.ROOT, "%.6f", latitude());
			String lon = String.format(Locale.ROOT, "%.6f", longitude());
			if (isPoint()) {
				return "https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lon + "#map=15/" + lat + "/" + lon;
			}
			double size = Math.max(north - south, east - west);
			int zoom = (int) Math.max(1, Math.min(18, Math.floor(Math.log(360 / size) / Math.log(2))));
			return "https://www.openstreetmap.org/#map=" + zoom + "/" + lat + "/" + lon;
		}
	}

	/**
	 * Where an entity is: its own <code>asWKT</code>, or
	 * <code>latitude</code> and <code>longitude</code>, or those of the
	 * entities its <code>geo</code> refers to in the crate, all together.
	 * Empty if none can be read, e.g. no geometry, a coordinate reference
	 * system other than WGS 84, or coordinates out of range.
	 */
	public static Optional<Area> of(Entity entity, Crate crate) {
		return of(entity, crate, 0);
	}

	private static Optional<Area> of(Entity entity, Crate crate, int depth) {
		List<Area> areas = new ArrayList<>();
		entity.getAll("asWKT").stream().filter(JsonNode::isValueNode).map(JsonNode::asText).map(Places::fromWkt)
				.flatMap(Optional::stream).forEach(areas::add);
		coordinates(entity).ifPresent(areas::add);
		if (depth < MAX_DEPTH) {
			entity.getAll("geo").stream().filter(v -> v.path("@id").isTextual())
					.flatMap(v -> crate.get(v.get("@id").asText()).stream()).filter(geo -> geo != entity)
					.flatMap(geo -> of(geo, crate, depth + 1).stream()).forEach(areas::add);
		}
		return areas.stream().reduce(Area::union);
	}

	private static Optional<Area> coordinates(Entity entity) {
		Optional<Double> latitude = entity.get("latitude").flatMap(Places::number);
		Optional<Double> longitude = entity.get("longitude").flatMap(Places::number);
		if (latitude.isEmpty() || longitude.isEmpty() || !valid(latitude.get(), longitude.get())) {
			return Optional.empty();
		}
		return Optional.of(Area.point(latitude.get(), longitude.get()));
	}

	private static Optional<Double> number(JsonNode value) {
		if (value.isNumber()) {
			return Optional.of(value.asDouble());
		}
		if (value.isTextual() && NUMBER.matcher(value.asText().strip()).matches()) {
			return Optional.of(Double.parseDouble(value.asText().strip()));
		}
		return Optional.empty();
	}

	/**
	 * The area of a geometry in Well-Known Text, e.g.
	 * <code>POINT (150.301195 -33.7152)</code>, longitude first, optionally
	 * after its coordinate reference system: WGS 84 as
	 * <code>&lt;http://www.opengis.net/def/crs/OGC/1.3/CRS84&gt;</code>
	 * (longitude first, the default) or
	 * <code>&lt;http://www.opengis.net/def/crs/EPSG/0/4326&gt;</code>
	 * (latitude first). Coordinates with height (<code>Z</code>) or measures
	 * (<code>M</code>) are read for their position only.
	 */
	public static Optional<Area> fromWkt(String wkt) {
		Matcher matcher = WKT.matcher(wkt);
		if (!matcher.matches() || !WKT_TYPES.contains(matcher.group(2).toUpperCase(Locale.ROOT))) {
			return Optional.empty();
		}
		boolean latitudeFirst;
		String crs = Optional.ofNullable(matcher.group(1)).orElse("").strip();
		if (crs.isEmpty() || crs.matches("https?://www\\.opengis\\.net/def/crs/OGC/[^/]+/CRS84")) {
			latitudeFirst = false;
		} else if (crs.matches("https?://www\\.opengis\\.net/def/crs/EPSG/[^/]+/4326")) {
			latitudeFirst = true;
		} else {
			return Optional.empty();
		}
		String body = matcher.group(3).strip();
		if (!body.startsWith("(")) {
			// e.g. POINT EMPTY
			return Optional.empty();
		}
		Area area = null;
		// Each position is one or more numbers between brackets and commas
		for (String position : body.split("[(),]")) {
			if (position.isBlank()) {
				continue;
			}
			String[] numbers = position.strip().split("\\s+");
			if (Stream.of(numbers).map(n -> n.toUpperCase(Locale.ROOT))
					.allMatch(word -> WKT_TYPES.contains(word) || word.matches("ZM|Z|M"))) {
				// A type inside a GEOMETRYCOLLECTION, e.g. POINT Z
				continue;
			}
			if (numbers.length < 2 || !NUMBER.matcher(numbers[0]).matches()
					|| !NUMBER.matcher(numbers[1]).matches()) {
				return Optional.empty();
			}
			double first = Double.parseDouble(numbers[0]);
			double second = Double.parseDouble(numbers[1]);
			double latitude = latitudeFirst ? first : second;
			double longitude = latitudeFirst ? second : first;
			if (!valid(latitude, longitude)) {
				return Optional.empty();
			}
			Area point = Area.point(latitude, longitude);
			area = area == null ? point : area.union(point);
		}
		return Optional.ofNullable(area);
	}

	private static boolean valid(double latitude, double longitude) {
		return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
	}
}
