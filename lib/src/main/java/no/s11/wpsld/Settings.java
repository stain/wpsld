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
package no.s11.wpsld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import no.s11.wpsld.util.AppDirectories;

/**
 * The user's preferences, kept as JSON in <code>settings.json</code> of
 * their config folder ({@link AppDirectories#config()}).
 *
 * @param disabledRegistries Names of the profile registries not to use, see
 *                           {@link no.s11.wpsld.masp.ProfileRegistry#name()}
 * @param style              The name of the editor's style, e.g.
 *                           <code>win11</code>; the user interface decides
 *                           which it knows
 * @param colours            The name of its colours, e.g. <code>dark</code>
 * @param geonamesUsername   The user's GeoNames account, to look up places
 *                           with; empty if none
 */
public record Settings(Set<String> disabledRegistries, String style, String colours, String geonamesUsername) {
	public static final String FILE = "settings.json";
	public static final String DEFAULT_STYLE = "classic";
	public static final String DEFAULT_COLOURS = "light";
	public static final Settings DEFAULT = new Settings(Set.of());
	private static final ObjectMapper MAPPER = new ObjectMapper();

	public Settings {
		disabledRegistries = Set.copyOf(disabledRegistries);
		style = style == null || style.isBlank() ? DEFAULT_STYLE : style;
		colours = colours == null || colours.isBlank() ? DEFAULT_COLOURS : colours;
		geonamesUsername = geonamesUsername == null ? "" : geonamesUsername.strip();
	}

	public Settings(Set<String> disabledRegistries, String style, String colours) {
		this(disabledRegistries, style, colours, "");
	}

	public Settings(Set<String> disabledRegistries) {
		this(disabledRegistries, DEFAULT_STYLE, DEFAULT_COLOURS);
	}

	public Settings withDisabledRegistries(Set<String> disabledRegistries) {
		return new Settings(disabledRegistries, style, colours, geonamesUsername);
	}

	public Settings withStyle(String style) {
		return new Settings(disabledRegistries, style, colours, geonamesUsername);
	}

	public Settings withColours(String colours) {
		return new Settings(disabledRegistries, style, colours, geonamesUsername);
	}

	public Settings withGeonamesUsername(String geonamesUsername) {
		return new Settings(disabledRegistries, style, colours, geonamesUsername);
	}

	/**
	 * Where the user's settings are kept.
	 */
	public static Path userFile() {
		return AppDirectories.system().config().resolve(FILE);
	}

	/**
	 * Read settings; the defaults if there are none, or they can't be read,
	 * as a broken file must not stop the application.
	 */
	public static Settings load(Path file) {
		if (!Files.isRegularFile(file)) {
			return DEFAULT;
		}
		try {
			JsonNode json = MAPPER.readTree(file.toFile());
			Set<String> disabled = new TreeSet<>();
			json.path("disabledRegistries").forEach(n -> disabled.add(n.asText()));
			return new Settings(disabled, json.path("style").asText(DEFAULT_STYLE),
					json.path("colours").asText(DEFAULT_COLOURS), json.path("geonamesUsername").asText(""));
		} catch (IOException e) {
			return DEFAULT;
		}
	}

	/**
	 * Write the settings, replacing the file at once.
	 */
	public void save(Path file) throws IOException {
		Path dir = AppDirectories.create(file.toAbsolutePath().getParent());
		ObjectNode json = MAPPER.createObjectNode();
		new TreeSet<>(disabledRegistries).forEach(json.putArray("disabledRegistries")::add);
		json.put("style", style);
		json.put("colours", colours);
		if (!geonamesUsername.isEmpty()) {
			json.put("geonamesUsername", geonamesUsername);
		}
		Path temp = Files.createTempFile(dir, "settings-", ".tmp");
		MAPPER.writer(SerializationFeature.INDENT_OUTPUT).writeValue(temp.toFile(), json);
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	public boolean isRegistryEnabled(String name) {
		return !disabledRegistries.contains(name);
	}
}
