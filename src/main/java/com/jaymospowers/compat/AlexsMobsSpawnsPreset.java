package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Alex's Mobs Continued (LGPL-3.0, shipped unchanged) keeps each animal's biomes in {@code config/alexsmobs/*.json},
 * written once and read on every start. Installs that had the old Alex's Mobs port (taken out in 0.8.107) still have
 * that port's files, and the new port reads them as they are. They name climate tags the game doesn't have
 * ({@code c:is_hot_overworld}, not Fabric's {@code c:is_hot/overworld}), so an animal whose only biomes are behind one of
 * those never spawns (orcas, hammerheads, mimic octopuses, gusters, jerboas, rain frogs, tarantula hawks, triops) and
 * others lose a biome (seals and cachalots in cold seas, mantis shrimp in warm seas, desert rattlesnakes and
 * roadrunners), while a "not in the cold" or "not in the heat" rule stops working (flying fish in frozen seas).
 *
 * <p>Before Alex's Mobs reads them, this renames those tags to the ones the new port's own files use, on both sides
 * (spawning is the server's). Only the bad names are touched, so a file someone edited keeps everything else.
 *
 * <p>It also lowers the blobfish's depth limit by the higher sea's rise: Alex's Mobs spawns blobfish only below its
 * {@code blobfishSpawnHeight} (25), and with the land and sea 8 blocks higher than the game's the deep sea floor sits
 * above that almost everywhere. Once per install (the marker), and only while it's still Alex's own 25.
 */
public final class AlexsMobsSpawnsPreset implements PreLaunchEntrypoint {
	private static final String MOD_ID = "alexsmobs";
	/** The old port's tag names and the game's (Fabric's convention tags, which the new port's own files use). */
	private static final Map<String, String> TAGS = Map.of(
		"c:is_hot_overworld", "c:is_hot/overworld",
		"c:is_cold_overworld", "c:is_cold/overworld",
		"c:is_dry_overworld", "c:is_dry/overworld",
		"c:is_dense_overworld", "c:is_dense_vegetation/overworld",
		"c:is_coniferous", "c:is_tree/coniferous");
	private static final Pattern BAD_TAG = Pattern.compile("\"(c:is_(?:hot|cold|dry|dense)_overworld|c:is_coniferous)\"");

	/** Bump to lower the blobfish's depth limit again. */
	private static final String BLOBFISH_MARKER = "jaymospowers-alexsmobs-blobfish-v1";
	/** Alex's own depth limit for blobfish, and ours: 8 blocks higher, as the sea is ({@code HighSea.RISE}). */
	private static final int BLOBFISH_HEIGHT = 25, BLOBFISH_OURS = 25 + 8;
	private static final Pattern BLOBFISH = Pattern.compile("(\"blobfishSpawnHeight\"\\s*:\\s*)" + BLOBFISH_HEIGHT + "(?![0-9])");

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded(MOD_ID)) {
			return;
		}
		renameTags(loader.getConfigDir().resolve(MOD_ID));
		deeperBlobfish(loader.getConfigDir());
	}

	private static void renameTags(Path folder) {
		if (!Files.isDirectory(folder)) {
			return;
		}
		int fixed = 0;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.json")) {
			for (Path file : files) {
				String text = Files.readString(file);
				Matcher matcher = BAD_TAG.matcher(text);
				if (!matcher.find()) {
					continue;
				}
				Files.writeString(file, matcher.replaceAll(match -> Matcher.quoteReplacement("\"" + TAGS.get(match.group(1)) + "\"")));
				fixed++;
			}
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't mend Alex's Mobs' biome tags in {}", folder, e);
			return;
		}
		if (fixed > 0) {
			JaymosPowers.LOGGER.info("Renamed old climate tags in {} of Alex's Mobs' spawn files", fixed);
		}
	}

	private static void deeperBlobfish(Path configDir) {
		Path marker = configDir.resolve(BLOBFISH_MARKER);
		Path config = configDir.resolve("amc.json");
		if (Files.exists(marker) || !Files.exists(config)) {
			return;
		}
		try {
			Matcher matcher = BLOBFISH.matcher(Files.readString(config));
			if (matcher.find()) {
				Files.writeString(config, matcher.replaceFirst("$1" + BLOBFISH_OURS));
			}
			Files.writeString(marker, "Jaymo's Powers moved Alex's Mobs' blobfish depth up with the higher sea. Delete this file to do it again.\n");
		} catch (IOException e) {
			JaymosPowers.LOGGER.warn("Couldn't move Alex's Mobs' blobfish depth in {}", config, e);
		}
	}
}
