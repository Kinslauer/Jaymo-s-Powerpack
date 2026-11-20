package com.jaymospowers.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Peterwolf's Forest Fire &amp; Firefighting (MIT, by Peterwolf; in the pack with its Minecraft lock lifted) with the
 * options Andy's forest-fire picks need, set once per install in its {@code config/peterwolfs_forestfire.json} (plain
 * Gson: a key left out keeps its default, and it writes the rest back itself):
 * <ul>
 * <li>ff-villagers: its villager panic off (it shoves villagers away from the heat). Ours answer fires their own way
 * (Jaymo's Kingdoms' Firefighting): some fight it, some lead the children off, the rest run.</li>
 * <li>ff-heat: its heat damage off ({@code heatDamageMultiplier} 0; Kingdoms counts its heat into Kitchen's body heat
 * instead, so standing by a big fire overheats you the one way). Its smoke stays: it hurts, blinds and weakens.</li>
 * <li>ff-lock: limits for the Deck (a Steam Deck runs the friends' server): fewer burning blocks, spread checks, embers,
 * fires at once, chunks kept loaded for them and smoke particles than its defaults.</li>
 * </ul>
 * After that, a player's or the server's own change is never touched. It runs on both sides; the server's file is what
 * counts for the fire.
 */
public final class ForestFirePreset implements PreLaunchEntrypoint {
	/** Bump to set these again. */
	private static final String MARKER = "jaymospowers-forestfire-preset-v1";
	/** Its keys and our values (its defaults in the comments). */
	private static final Map<String, Number> NUMBERS = Map.of(
		"heatDamageMultiplier", 0.0F,     // 1.0
		"maxBurningBlocksPerWorld", 3000, // 8000
		"maxSpreadChecksPerTick", 200,    // 400
		"maxEmbersPerTick", 8,            // 12
		"maxActiveIncidents", 4,          // 8
		"maxFireChunkTickets", 48,        // 128
		"chunkUnloadRetentionRadius", 1,  // 2
		"maxSmokeParticles", 120);        // 200
	private static final Map<String, Boolean> SWITCHES = Map.of(
		"villagerPanicEnabled", false);   // true

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("peterwolfs_forestfire")) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("peterwolfs_forestfire.json");
		try {
			JsonObject json = new JsonObject();
			if (Files.exists(config)) {
				try {
					json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
				} catch (RuntimeException broken) {
					// a file it can't read either: it falls back to its defaults, and ours go over them
					json = new JsonObject();
				}
			}
			for (Map.Entry<String, Number> e : NUMBERS.entrySet()) {
				json.addProperty(e.getKey(), e.getValue());
			}
			for (Map.Entry<String, Boolean> e : SWITCHES.entrySet()) {
				json.addProperty(e.getKey(), e.getValue());
			}
			Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
			Files.createDirectories(config.getParent());
			Files.writeString(config, gson.toJson(json));
			Files.writeString(marker, "Jaymo's Powers set Peterwolf's Forest Fire options (villager panic and heat damage off, "
				+ "the Deck's limits). Delete this file to set them again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't set Peterwolf's Forest Fire options in {}", config, e);
		}
	}
}
