package com.jaymospowers.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Fantasy Armor's lore paragraphs stay off its tooltips (Andy, 2026-10-29: no screen text states lore): once per install
 * its {@code showDescriptions} is switched off in {@code config/fantasy_armor-common.json} before it reads the file. A
 * new file gets all of Fantasy Armor's own defaults but that one. After that, a player's or the server's own change is
 * never touched.
 */
public final class FantasyArmorPreset implements PreLaunchEntrypoint {
	/** Bump to switch the descriptions off again. */
	private static final String MARKER = "jaymospowers-fantasy-armor-preset-v1";
	/** Fantasy Armor 1.3's own defaults, with its descriptions off. */
	private static final String DEFAULTS = """
		{
		  "applyArmorEffects": true,
		  "applyModifiers": true,
		  "showDescriptions": false,
		  "showCapes": true,
		  "showParticles": false,
		  "effectsInterval": 241,
		  "descriptionsLength": 250,
		  "showEffectIcon": true,
		  "enableDurability": false
		}
		""";
	private static final Pattern DESCRIPTIONS = Pattern.compile("(\"showDescriptions\"\\s*:\\s*)true");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("fantasy_armor")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("fantasy_armor-common.json");
		try {
			if (Files.exists(config)) {
				Matcher matcher = DESCRIPTIONS.matcher(Files.readString(config));
				if (matcher.find()) {
					Files.writeString(config, matcher.replaceFirst("$1false"));
				}
			} else {
				Files.createDirectories(config.getParent());
				Files.writeString(config, DEFAULTS);
			}
			Files.writeString(marker, "Jaymo's Powers switched Fantasy Armor's tooltip descriptions off. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't switch Fantasy Armor's descriptions off in {}", config, e);
		}
	}
}
