package com.jaymospowers.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * (0.14.8) Picking things up is a choice: once per install, Interactic Renewed's auto pickup is switched off, so items
 * on the ground are picked up by right-clicking them (or its pickup key) instead of by walking over them. It's one of
 * Interactic's server-side options, so the server's file is what counts; this runs on both sides. Written before
 * Interactic reads its config (owo-lib fills any key we don't write with its default). After that, a player's or the
 * server's own change is never touched.
 */
public final class PickupPreset implements PreLaunchEntrypoint {
	/** Bump to switch auto pickup off again. */
	private static final String MARKER = "jaymospowers-pickup-preset-v1";
	private static final Pattern AUTO_PICKUP = Pattern.compile("(\"autoPickup\"\\s*:\\s*)true");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("interactic")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("interactic.json5");
		try {
			if (Files.exists(config)) {
				Matcher matcher = AUTO_PICKUP.matcher(Files.readString(config));
				if (matcher.find()) {
					Files.writeString(config, matcher.replaceFirst("$1false"));
				}
			} else {
				Files.createDirectories(config.getParent());
				Files.writeString(config, "{\n\t\"autoPickup\": false\n}\n");
			}
			Files.writeString(marker, "Jaymo's Powers switched Interactic's auto pickup off. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't switch Interactic's auto pickup off in {}", config, e);
		}
	}
}
