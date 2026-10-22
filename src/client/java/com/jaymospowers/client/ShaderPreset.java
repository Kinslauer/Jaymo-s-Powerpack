package com.jaymospowers.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.115) The pack's shader pack, Complementary Unbound (by EminGT, Complementary Development; build.gradle's
 * packShaderPacks): once per install it's chosen and switched on in Iris, so a new player starts with shaders. After that,
 * players' own choice in the shader pack screen is never touched.
 *
 * <p>Its licence lets modpacks include it unchanged, added through CurseForge's own system (the import puts it in
 * shaderpacks/) and credited on the pack's page. So it's never shipped or fetched by us, and its settings are left at
 * its own defaults (no settings file: the licence counts "simple variable changes" as modifying it). If the file isn't
 * there yet, this tries again next launch.
 *
 * <p>Iris reads {@code config/iris.properties} when the game's options load, so the file is written here, before that.
 * Keys we don't set are kept as they are; a new file gets Iris's own defaults for the rest. Dark nights still work:
 * Complementary takes its sky light from the light levels Iris hands it, which Kingdoms' DarkNights dims.
 */
public final class ShaderPreset implements PreLaunchEntrypoint {
	/** Bump to choose the pack again (for a new shader pack in a later pack version). */
	private static final String MARKER = "jaymospowers-shader-preset-v1";
	/** The start of its file names (ComplementaryUnbound_r5.9.3.zip); the newest one there wins. */
	private static final String PREFIX = "ComplementaryUnbound_";

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("iris") || loader.getEnvironmentType() != EnvType.CLIENT) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		String pack = find(loader.getGameDir().resolve("shaderpacks"));
		if (pack == null) {
			return;
		}
		Path config = loader.getConfigDir().resolve("iris.properties");
		try {
			Properties properties = new Properties();
			if (Files.exists(config)) {
				try (InputStream in = Files.newInputStream(config)) {
					properties.load(in);
				}
			}
			properties.setProperty("shaderPack", pack);
			properties.setProperty("enableShaders", "true");
			Files.createDirectories(config.getParent());
			try (OutputStream out = Files.newOutputStream(config)) {
				properties.store(out, "This file stores configuration options for Iris, such as the currently active shaderpack");
			}
			Files.writeString(marker, "Jaymo's Powers switched on " + pack + " in Iris. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't switch on {} in {}", pack, config, e);
		}
	}

	private static @Nullable String find(Path dir) {
		if (!Files.isDirectory(dir)) {
			return null;
		}
		String newest = null;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, PREFIX + "*.zip")) {
			for (Path file : files) {
				String name = file.getFileName().toString();
				if (newest == null || name.compareTo(newest) > 0) {
					newest = name;
				}
			}
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't look in {}", dir, e);
		}
		return newest;
	}
}
