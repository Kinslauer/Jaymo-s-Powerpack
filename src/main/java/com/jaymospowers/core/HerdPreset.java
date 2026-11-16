package com.jaymospowers.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Herd Mentality (Darkhax, LGPL-2.1) for hostile mobs only (Andy, Sensible MC picks: "Limit to hostiles"): once per
 * install its {@code affects_neutral_mobs} option is switched off, so only a hit on a hostile mob calls its kind nearby.
 * Herd animals keep fleeing a killing nearby, as Kingdoms already has them do. It's a server-side option, so the
 * server's file is what counts; this runs on both sides. Written before Herd Mentality reads
 * {@code config/herdmentality.json} (Prickle, lenient JSON; a key it doesn't find keeps its default). After that, a
 * player's or the server's own change is never touched.
 */
public final class HerdPreset implements PreLaunchEntrypoint {
	/** Bump to switch neutral mobs off again. (v2: v1 wrote options as bare values, which Prickle refuses.) */
	private static final String MARKER = "jaymospowers-herd-preset-v2";
	/** Herd Mentality's own defaults, with neutral mobs off; Prickle keeps each option as {@code {"value": ...}}. */
	private static final String DEFAULTS = """
		{
			"affects_neutral_mobs": {
				"value": false
			},
			"range": {
				"value": 8.0
			}
		}
		""";
	private static final Pattern NEUTRAL = Pattern.compile("(\"affects_neutral_mobs\"\\s*:\\s*\\{\\s*\"value\"\\s*:\\s*)true");
	/** v1's file: an option as a bare value, which stopped the game starting. */
	private static final Pattern BARE = Pattern.compile("\"affects_neutral_mobs\"\\s*:\\s*(true|false)");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("herdmentality")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("herdmentality.json");
		try {
			if (Files.exists(config) && BARE.matcher(Files.readString(config)).find()) {
				Files.writeString(config, DEFAULTS);
			} else if (Files.exists(config)) {
				Matcher matcher = NEUTRAL.matcher(Files.readString(config));
				if (matcher.find()) {
					Files.writeString(config, matcher.replaceFirst("$1false"));
				}
			} else {
				Files.createDirectories(config.getParent());
				Files.writeString(config, DEFAULTS);
			}
			Files.writeString(marker, "Jaymo's Powers switched Herd Mentality to hostile mobs only. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't switch Herd Mentality to hostile mobs only in {}", config, e);
		}
	}
}
