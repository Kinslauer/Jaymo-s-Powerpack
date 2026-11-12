package com.jaymospowers.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sets a few keys in another mod's TOML config before that mod reads it (the presets: {@link CameraPreset},
 * {@link SoundModsPreset}). Keys already in the file are changed where they are; missing ones go under their table
 * (a new table at the end). Everything else in the file is left as it was, and a mod using Forge Config API Port
 * fills in the keys we don't write with its defaults.
 */
final class TomlPreset {
	private TomlPreset() {
	}

	/**
	 * {@code preset}: table header as written between the brackets (e.g. {@code camera.offset} or
	 * {@code "Sound Control"."Sound Options"}) → key as written before the {@code =} (quoted if it has spaces) → value.
	 */
	static void apply(Path config, Map<String, Map<String, String>> preset) throws IOException {
		List<String> lines = Files.exists(config) ? new ArrayList<>(Files.readAllLines(config)) : new ArrayList<>();
		Map<String, Map<String, String>> left = new LinkedHashMap<>();
		preset.forEach((table, keys) -> left.put(table, new LinkedHashMap<>(keys)));
		// set the keys already in the file where they are
		String table = "";
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.startsWith("[") && line.endsWith("]")) {
				table = line.substring(1, line.length() - 1).trim();
				continue;
			}
			int eq = line.indexOf('=');
			Map<String, String> keys = left.get(table);
			if (keys != null && !line.startsWith("#") && eq > 0) {
				String value = keys.remove(line.substring(0, eq).trim());
				if (value != null) {
					lines.set(i, lines.get(i).substring(0, lines.get(i).indexOf('=')) + "= " + value);
				}
			}
		}
		// the rest go at the end, each under its own table header (TOML allows a table once, so only new ones)
		for (Map.Entry<String, Map<String, String>> entry : left.entrySet()) {
			if (entry.getValue().isEmpty()) {
				continue;
			}
			if (lines.stream().anyMatch(l -> l.trim().equals("[" + entry.getKey() + "]"))) {
				// the table is there but lacks the key: put it straight after the header
				int at = lines.indexOf(lines.stream().filter(l -> l.trim().equals("[" + entry.getKey() + "]")).findFirst().orElseThrow()) + 1;
				for (Map.Entry<String, String> key : entry.getValue().entrySet()) {
					lines.add(at++, "\t" + key.getKey() + " = " + key.getValue());
				}
				continue;
			}
			lines.add("[" + entry.getKey() + "]");
			entry.getValue().forEach((key, value) -> lines.add("\t" + key + " = " + value));
		}
		Files.createDirectories(config.getParent());
		Files.write(config, lines);
	}
}
