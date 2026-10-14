package com.jaymospowers.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Shoulder Surfing Reloaded, if it's installed (0.10.57; it replaced Camera Utils and our own power-only shoulder view):
 * once per install, its view is set to Andy's old Camera Utils F7 preset ({@link #PRESET}: 3.4 blocks back, 0.1 up, 0.9
 * to the right), F5 goes straight to it instead of vanilla's view from behind, and the camera stays coupled to the
 * player, so powers that fire along your look (Web, Gravity, Z Fighter) still go where the crosshair points. Its
 * config is read by Forge Config API Port when it starts, so the file is written here, before any mod does; keys we
 * don't write are filled in with its defaults. After that, players' own changes are never touched.
 */
public final class CameraPreset implements PreLaunchEntrypoint {
	/** Bump to set the preset again (for a new preset in a later pack version). */
	private static final String MARKER = "jaymospowers-camera-preset-v2";
	/** TOML table → key → value, in Shoulder Surfing's client config. */
	private static final Map<String, Map<String, String>> PRESET = new LinkedHashMap<>();

	static {
		PRESET.put("camera.offset", new LinkedHashMap<>(Map.of("offset_x", "-0.9", "offset_y", "0.1", "offset_z", "3.4")));
		PRESET.put("camera", new LinkedHashMap<>(Map.of("decoupled_camera", "false")));
		PRESET.put("perspective", new LinkedHashMap<>(Map.of("replace_default_perspective", "true")));
	}

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("shouldersurfing") || loader.getEnvironmentType() != EnvType.CLIENT) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("shouldersurfing-client.toml");
		try {
			List<String> lines = Files.exists(config) ? new ArrayList<>(Files.readAllLines(config)) : new ArrayList<>();
			Map<String, Map<String, String>> left = new LinkedHashMap<>();
			PRESET.forEach((table, keys) -> left.put(table, new LinkedHashMap<>(keys)));
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
			Files.writeString(marker, "Jaymo's Powers set Shoulder Surfing's view to the pack's preset. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't set Shoulder Surfing's preset in {}", config, e);
		}
	}
}
