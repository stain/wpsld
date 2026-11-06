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
 */
public record Settings(Set<String> disabledRegistries) {
	public static final String FILE = "settings.json";
	public static final Settings DEFAULT = new Settings(Set.of());
	private static final ObjectMapper MAPPER = new ObjectMapper();

	public Settings {
		disabledRegistries = Set.copyOf(disabledRegistries);
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
			return new Settings(disabled);
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
		Path temp = Files.createTempFile(dir, "settings-", ".tmp");
		MAPPER.writer(SerializationFeature.INDENT_OUTPUT).writeValue(temp.toFile(), json);
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	public boolean isRegistryEnabled(String name) {
		return !disabledRegistries.contains(name);
	}
}
