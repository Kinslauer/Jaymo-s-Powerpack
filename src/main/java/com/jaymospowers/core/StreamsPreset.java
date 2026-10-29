package com.jaymospowers.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * (0.17.199) Andy's load-time ask: once per install, Streams Reflowing's terrain accuracy goes from its MEDIUM to LOW,
 * so it plans streams on a coarser read of the land and each new chunk costs less (streams hug the terrain a little
 * less closely). Its "fast terrain mode" was no help: it's on by default and Streams switches it off itself where its
 * estimates don't fit our terrain. It's a world-side option, so the server's file is what counts; this runs on both
 * sides, before Streams reads {@code config/streamsreflowing.properties}. A new file gets just that line (Streams fills
 * in the rest with its defaults). After that, a player's or the server's own change is never touched.
 */
public final class StreamsPreset implements PreLaunchEntrypoint {
	/** Bump to set the accuracy again. */
	private static final String MARKER = "jaymospowers-streams-preset-v1";
	private static final String ACCURACY = "LOW";
	private static final Pattern LINE = Pattern.compile("(?m)^(\\s*terrainAccuracyLevel\\s*=\\s*).*$");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("streamsreflowing")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("streamsreflowing.properties");
		try {
			if (Files.exists(config)) {
				String text = Files.readString(config);
				Matcher matcher = LINE.matcher(text);
				Files.writeString(config, matcher.find() ? matcher.replaceFirst("$1" + ACCURACY) : text + "\nterrainAccuracyLevel=" + ACCURACY + "\n");
			} else {
				Files.createDirectories(config.getParent());
				Files.writeString(config, "terrainAccuracyLevel=" + ACCURACY + "\n");
			}
			Files.writeString(marker, "Jaymo's Powers set Streams Reflowing's terrain accuracy to LOW. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't set Streams Reflowing's terrain accuracy in {}", config, e);
		}
	}
}
