package com.jaymospowers.client;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * "MCA - Default Medieval" (by DE4THR4SH, CurseForge 667175) dresses MCA's villagers in vanilla-style medieval clothes.
 * The CurseForge import puts it in resourcepacks/; once per install it's switched on here. After that, players' own
 * choice in the resource pack screen is never touched.
 *
 * <p>Its newest file says 1.21.1 (pack_format 55), so 26.3 calls it "made for an older version" and would drop it from
 * the options on load; it's listed as an accepted incompatible pack too, as the confirm button in the screen does. It
 * only swaps MCA's skin textures, which haven't moved since.
 */
final class MedievalVillagers {
	/** Bump to switch it on again (for a new pack file in a later pack version). */
	private static final String MARKER = "jaymospowers-medieval-villagers-v1";
	/** The start of its file names on CurseForge (MCAR_VanillaMedieval_Universal_...zip). */
	private static final String FILE_PREFIX = "MCAR_VanillaMedieval";

	private MedievalVillagers() {
	}

	static void enableOnce(Minecraft minecraft) {
		Path marker = FabricLoader.getInstance().getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		String pack = find(minecraft.getResourcePackDirectory());
		if (pack == null) {
			// not downloaded (yet): try again next launch
			return;
		}
		String id = "file/" + pack;
		if (!minecraft.options.resourcePacks.contains(id)) {
			minecraft.options.resourcePacks.add(id);
			if (!minecraft.options.incompatibleResourcePacks.contains(id)) {
				minecraft.options.incompatibleResourcePacks.add(id);
			}
			minecraft.options.loadSelectedResourcePacks(minecraft.getResourcePackRepository());
			minecraft.options.save();
			minecraft.reloadResourcePacks();
		}
		try {
			Files.writeString(marker, "Jaymo's Powers switched on " + pack + " (medieval MCA villagers). Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't write {}", marker, e);
		}
	}

	private static @Nullable String find(Path dir) {
		if (!Files.isDirectory(dir)) {
			return null;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, FILE_PREFIX + "*.zip")) {
			for (Path file : files) {
				return file.getFileName().toString();
			}
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't look in {}", dir, e);
		}
		return null;
	}
}
