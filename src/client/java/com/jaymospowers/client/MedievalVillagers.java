package com.jaymospowers.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * "MCA - Default Medieval" (by DE4THR4SH, CurseForge 667175) dresses MCA's villagers in vanilla-style medieval clothes.
 * The CurseForge import puts it in resourcepacks/; once per install it's switched on here. After that, players' own
 * choice in the resource pack screen is never touched.
 *
 * <p>(0.8.106) If it isn't in resourcepacks/ (the jars were copied into a profile by hand, or the import skipped it),
 * it's fetched from CurseForge's CDN first, the same file Build Modpack downloads, and checked against its SHA-1. It's
 * all rights reserved, so it's never shipped in our jar or the pack's overrides.
 *
 * <p>Its newest file says 1.21.1 (pack_format 55), so 26.3 calls it "made for an older version" and would drop it from
 * the options on load; it's listed as an accepted incompatible pack too, as the confirm button in the screen does. It
 * only swaps MCA's skin textures, which haven't moved since.
 */
final class MedievalVillagers {
	/**
	 * Bump to switch it on again (for a new pack file in a later pack version). v2 (0.8.106): installs where v1 found no
	 * file, or the game later dropped it from the options while the file was missing, get it once more.
	 */
	private static final String MARKER = "jaymospowers-medieval-villagers-v2";
	/** The start of its file names on CurseForge (MCAR_VanillaMedieval_Universal_...zip). */
	private static final String FILE_PREFIX = "MCAR_VanillaMedieval";
	/** The file build.gradle's packResourcePacks names (CurseForge file 8868890), its CDN address and SHA-1. */
	private static final String FILE = "MCAR_VanillaMedieval_Universal_7.7.36-beta.3+1.21.1.zip";
	private static final String URL = "https://mediafilez.forgecdn.net/files/8868/890/MCAR_VanillaMedieval_Universal_7.7.36-beta.3%2B1.21.1.zip";
	private static final String SHA1 = "63400dbf8cd26a8b3309c3625c1f5b971b636f57";

	private MedievalVillagers() {
	}

	static void enableOnce(Minecraft minecraft) {
		Path marker = FabricLoader.getInstance().getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path dir = minecraft.getResourcePackDirectory();
		String pack = find(dir);
		if (pack != null) {
			enable(minecraft, pack, marker);
			return;
		}
		// not there: fetch it in the background, then switch it on (a failed download tries again next launch)
		CompletableFuture.supplyAsync(() -> download(dir), Util.nonCriticalIoPool()).thenAcceptAsync(file -> {
			if (file != null) {
				minecraft.getResourcePackRepository().reload();
				enable(minecraft, file, marker);
			}
		}, minecraft);
	}

	private static void enable(Minecraft minecraft, String pack, Path marker) {
		String id = "file/" + pack;
		if (minecraft.getResourcePackRepository().getPack(id) == null) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Resource pack {} is in resourcepacks/ but the game didn't load it", pack);
			return;
		}
		if (!minecraft.options.incompatibleResourcePacks.contains(id)) {
			minecraft.options.incompatibleResourcePacks.add(id);
		}
		if (!minecraft.options.resourcePacks.contains(id)) {
			minecraft.options.resourcePacks.add(id);
		}
		minecraft.options.loadSelectedResourcePacks(minecraft.getResourcePackRepository());
		minecraft.options.save();
		minecraft.reloadResourcePacks();
		try {
			Files.writeString(marker, "Jaymo's Powers switched on " + pack + " (medieval MCA villagers). Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't write {}", marker, e);
		}
	}

	private static @Nullable String download(Path dir) {
		Path target = dir.resolve(FILE);
		Path part = dir.resolve(FILE + ".part");
		try {
			Files.createDirectories(dir);
			URLConnection connection = URI.create(URL).toURL().openConnection();
			connection.setConnectTimeout(15_000);
			connection.setReadTimeout(30_000);
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			try (InputStream in = connection.getInputStream()) {
				Files.copy(new DigestInputStream(in, sha1), part, StandardCopyOption.REPLACE_EXISTING);
			}
			String got = HexFormat.of().formatHex(sha1.digest());
			if (!got.equals(SHA1)) {
				com.jaymospowers.JaymosPowers.LOGGER.warn("Downloaded {} but its SHA-1 is {}, not {}; not using it", FILE, got, SHA1);
				Files.deleteIfExists(part);
				return null;
			}
			Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
			com.jaymospowers.JaymosPowers.LOGGER.info("Downloaded {} (medieval MCA villagers) into resourcepacks/", FILE);
			return FILE;
		} catch (IOException | NoSuchAlgorithmException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't download {} (CurseForge project 667175); trying again next launch", FILE, e);
			try {
				Files.deleteIfExists(part);
			} catch (IOException ignored) {
			}
			return null;
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
