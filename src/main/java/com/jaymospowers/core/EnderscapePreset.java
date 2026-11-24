package com.jaymospowers.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Stellarity is the End and Enderscape lives under the Nether (Andy, 2026-11-19: "ship stellarity as the end and enderscape
 * underneath"). Enderscape's built-in data packs for its End terrain, End cities and strongholds are on by default and
 * replace the same files Stellarity does, so once per install, with both mods there, those three are switched off for new
 * worlds ({@code config/enderscape.json5}, YACL; a key it doesn't find keeps its default). Its biomes, blocks, mobs and
 * the rest stay; they fill the Beneath ({@code dimension/the_beneath.json}). After that, a player's own change is never
 * touched.
 */
public final class EnderscapePreset implements PreLaunchEntrypoint {
	/** Bump to switch them off again. */
	private static final String MARKER = "jaymospowers-enderscape-preset-v1";
	private static final String[] PACKS = {"defaultDataPackNewTerrain", "defaultDataPackNewEndCities", "defaultDataPackNewStrongholds"};

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("enderscape") || !loader.isModLoaded("stellarity")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("enderscape.json5");
		try {
			if (Files.exists(config)) {
				String text = Files.readString(config);
				for (String key : PACKS) {
					text = Pattern.compile("(\"?" + key + "\"?\\s*:\\s*)true").matcher(text).replaceFirst("$1false");
				}
				Files.writeString(config, text);
			} else {
				Files.createDirectories(config.getParent());
				StringBuilder text = new StringBuilder("{\n");
				for (int i = 0; i < PACKS.length; i++) {
					text.append("  \"").append(PACKS[i]).append("\": false").append(i < PACKS.length - 1 ? ",\n" : "\n");
				}
				Files.writeString(config, text.append("}\n"));
			}
			Files.writeString(marker, "Jaymo's Powers switched off Enderscape's End terrain, End city and stronghold packs (Stellarity is the End)."
				+ " Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't switch Enderscape's End packs off in {}", config, e);
		}
	}
}
