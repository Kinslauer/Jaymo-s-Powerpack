package com.jaymospowers.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Inferno (MoriyaShiine, all rights reserved, shipped untouched) with its fire options as Andy picked them: once per
 * install its {@code scorchedEarth} option is switched off (burnt grass and podzol stay as they are, and the Shining Oak
 * and its plants, which only grow from scorched earth, don't appear); charred logs, copper fire, soul fire, torch
 * ignition, fire spreading from burning mobs, the lower fire overlay and the smoky fog stay on. Inferno's options are
 * in {@code config/inferno.json} (MidnightLib, lenient JSON; a key it doesn't find keeps its default). Some are
 * server-side, so the server's file is what counts; this runs on both sides. After that, a player's or the server's own
 * change is never touched.
 */
public final class InfernoPreset implements PreLaunchEntrypoint {
	/** Bump to switch scorched earth off again. */
	private static final String MARKER = "jaymospowers-inferno-preset-v1";
	/** Inferno's own defaults, with scorched earth off. */
	private static final String DEFAULTS = """
		{
			"charredLogs": true,
			"copperFire": true,
			"onSoulFire": true,
			"scorchedEarth": false,
			"torchIgnition": true,
			"entityFireSpreadChance": 0.5,
			"lowerHudFire": true,
			"smokyFog": true
		}
		""";
	private static final Pattern SCORCHED_EARTH = Pattern.compile("(\"scorchedEarth\"\\s*:\\s*)true");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("inferno")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("inferno.json");
		try {
			if (Files.exists(config)) {
				Matcher matcher = SCORCHED_EARTH.matcher(Files.readString(config));
				if (matcher.find()) {
					Files.writeString(config, matcher.replaceFirst("$1false"));
				}
			} else {
				Files.createDirectories(config.getParent());
				Files.writeString(config, DEFAULTS);
			}
			Files.writeString(marker, "Jaymo's Powers switched Inferno's scorched earth off. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't switch Inferno's scorched earth off in {}", config, e);
		}
	}
}
