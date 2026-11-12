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
 * Shoulder Surfing Reloaded, if it's installed (0.10.65; it replaced Camera Utils and our own power-only shoulder view):
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
			TomlPreset.apply(config, PRESET);
			Files.writeString(marker, "Jaymo's Powers set Shoulder Surfing's view to the pack's preset. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't set Shoulder Surfing's preset in {}", config, e);
		}
	}
}
