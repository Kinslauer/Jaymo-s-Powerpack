package com.jaymospowers.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * ImmersiveFX: Resurrected beside Sound Physics Remastered: only one of them may run reverb and muffling. Both put
 * their own echo and through-the-wall filters on every sound the game plays. ImmersiveFX means to stand down when a
 * sound physics mod is installed, but it looks for the old mod id ({@code soundphysics}) and Remastered's is
 * {@code sound_physics_remastered}, so both ran on the same sounds. Both on together is the likeliest reason hits and
 * pickups went silent and all sound dropped out while sprinting (Andy, 2026-11-12). Once per install, ImmersiveFX's
 * "Enable Enhanced Sounds" is switched off, the setting it switches off for itself when it does spot a sound physics
 * mod. Its ambience, footsteps and other sounds are unaffected; Sound Physics keeps the echoes and muffling.
 * Written before ImmersiveFX reads {@code config/immersivefx.toml}. After that, a player's own change is never touched.
 */
public final class SoundModsPreset implements PreLaunchEntrypoint {
	/** Bump to switch it off again. */
	private static final String MARKER = "jaymospowers-sound-mods-preset-v1";
	private static final Map<String, Map<String, String>> PRESET = new LinkedHashMap<>();

	static {
		PRESET.put("\"Sound Control\".\"Sound Options\"", new LinkedHashMap<>(Map.of("\"Enable Enhanced Sounds\"", "false")));
	}

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.getEnvironmentType() != EnvType.CLIENT || !loader.isModLoaded("immersivefx") || !loader.isModLoaded("sound_physics_remastered")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("immersivefx.toml");
		try {
			TomlPreset.apply(config, PRESET);
			Files.writeString(marker, "Jaymo's Powers switched ImmersiveFX's enhanced sounds off (Sound Physics Remastered does the echoes). "
				+ "Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't switch ImmersiveFX's enhanced sounds off in {}", config, e);
		}
	}
}
