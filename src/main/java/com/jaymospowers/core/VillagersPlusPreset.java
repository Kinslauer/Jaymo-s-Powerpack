package com.jaymospowers.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * VillagersPlus (LithiumPlus, GPL-3.0, shipped untouched) adds its job houses to the village house pools, the miner's
 * house at a weight of 25 in plains, taiga and savanna villages. Vanilla's commonest houses weigh 4 to 6, so the miner's
 * house was the likeliest building in every slot and big towns came out with a row of six to eight mine heads (Andy,
 * 2026-11-21, Morwick: "how does it justify having this many mines?"; he chose "just make them rarer"). Once per install
 * the miner's weight is set to {@link #MINER_WEIGHT}, like a common vanilla house; the other job houses keep theirs.
 * Its options are in {@code config/villagersplus/villagersplus-4.0-config.json5} (Gson over the config class, so a key
 * left out keeps its default). Village generation is the server's, so the server's file is what counts; this runs on
 * both sides. Villages already generated keep their mines. After this, a player's or the server's own change is kept.
 */
public final class VillagersPlusPreset implements PreLaunchEntrypoint {
	/** Bump to set the weight again. */
	private static final String MARKER = "jaymospowers-villagersplus-preset-v1";
	/** The miner's house weight, about a common vanilla house's. */
	static final int MINER_WEIGHT = 4;
	private static final String FILE = "villagersplus/villagersplus-4.0-config.json5";
	/** Only the miner's weights: VillagersPlus fills in the rest with its defaults and writes the whole file back. */
	private static final String DEFAULTS = """
		{
		  "plains_miner_weight": %1$d,
		  "taiga_miner_weight": %1$d,
		  "savanna_miner_weight": %1$d
		}
		""".formatted(MINER_WEIGHT);
	private static final Pattern MINER = Pattern.compile("(\"[a-z]+_miner_weight\"\\s*:\\s*)\\d+");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("villagersplus")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve(FILE);
		try {
			if (Files.exists(config)) {
				Files.writeString(config, rarer(Files.readString(config)));
			} else {
				Files.createDirectories(config.getParent());
				Files.writeString(config, DEFAULTS);
			}
			Files.writeString(marker, "Jaymo's Powers made VillagersPlus' miner's house rarer in villages. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't make VillagersPlus' miner's house rarer in {}", config, e);
		}
	}

	/** {@code text} with every miner's house weight set to {@link #MINER_WEIGHT}. */
	static String rarer(String text) {
		Matcher m = MINER.matcher(text);
		return m.replaceAll("$1" + MINER_WEIGHT);
	}
}
