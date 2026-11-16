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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The pack's resource packs (build.gradle's packResourcePacks). The CurseForge import puts them in resourcepacks/; once
 * per install each is switched on here. After that, players' own choice in the resource pack screen is never touched.
 *
 * <p>(0.8.106) If one isn't in resourcepacks/ (the jars were copied into a profile by hand, or the import skipped it),
 * it's fetched from CurseForge's CDN first, the same file Build Modpack downloads, and checked against its SHA-1. They're
 * all rights reserved (modpacks may use them, not redistribute them), so they're never shipped in our jar or the pack's
 * overrides.
 */
final class PackResourcePacks {
	/**
	 * One pack. {@code marker}: the config file that says it was switched on (bump its version to switch it on again,
	 * for a new pack file in a later pack version). {@code prefix}: the start of its file names on CurseForge.
	 * {@code olderFormat}: its file is made for an older game version, so 26.3 calls it incompatible and would drop it
	 * from the options on load; it's listed as an accepted incompatible pack too, as the confirm button in the screen does.
	 * {@code place}: where it goes in the pack order. {@code null}: on top, like any pack switched on in the screen.
	 * {@link #BASE}: at the bottom, just above the game's and mods' own packs, so every other pack draws over it (a
	 * full retexture other packs build on). Anything else: the prefix of a resource pack file it sits just above
	 * (an add-on to that pack), or on top when that one isn't switched on.
	 */
	record Pack(String what, String marker, String prefix, String file, String url, String sha1, boolean olderFormat,
			@Nullable String place, boolean keptOn) {
		Pack(String what, String marker, String prefix, String file, String url, String sha1, boolean olderFormat) {
			this(what, marker, prefix, file, url, sha1, olderFormat, null, false);
		}

		Pack(String what, String marker, String prefix, String file, String url, String sha1, boolean olderFormat,
				@Nullable String place) {
			this(what, marker, prefix, file, url, sha1, olderFormat, place, false);
		}
	}

	/**
	 * (0.20.40) A pack {@code keptOn} is switched on at every launch it's found off, not just once: its marker is then
	 * the file that turns that off ({@code <marker>-off} in config/), for a player who'd rather not have it.
	 */
	static final String OFF = "-off";

	static final String BASE = "<base>";

	private static final List<Pack> PACKS = List.of(
		// "MCA - Default Medieval" (by DE4THR4SH, CurseForge 667175) dresses MCA's villagers in vanilla-style medieval
		// clothes. Its newest file says 1.21.1 (pack_format 55); it only swaps MCA's skin textures, which haven't moved
		// since. v2 (0.8.106): installs where v1 found no file, or the game later dropped it while the file was missing,
		// get it once more. (0.20.40) Andy (2026-11-04): "the outfits seem to be broken again, they arent wearing medieval
		// attire anymore": his clip showed MCA's own green plaid shirt (neutral/mason/0), which this pack draws as a tan
		// tunic, so the pack had dropped out of his selected packs while its v2 marker said it was done. Kept on now:
		// switched back on at any launch it's found off (config/jaymospowers-medieval-villagers-off keeps it off).
		new Pack("medieval MCA villagers", "jaymospowers-medieval-villagers", "MCAR_VanillaMedieval",
			"MCAR_VanillaMedieval_Universal_7.7.36-beta.3+1.21.1.zip",
			"https://mediafilez.forgecdn.net/files/8868/890/MCAR_VanillaMedieval_Universal_7.7.36-beta.3%2B1.21.1.zip",
			"63400dbf8cd26a8b3309c3625c1f5b971b636f57", true, null, true),
		// "Fresh Animations: Player Extension" (by FreshLX, CurseForge 1281029, file 8245230; the same file as Modrinth's
		// 1.1): livelier player animations, run by Entity Model Features. It stands alone (no base Fresh Animations
		// needed) and supports every format up to 26.3. Our own poses pause it while they play (EmfCompat).
		new Pack("Fresh Animations' player", "jaymospowers-fa-player-v1", "FA+Player", "FA+Player-v1.1.zip",
			"https://mediafilez.forgecdn.net/files/8245/230/FA%2BPlayer-v1.1.zip",
			"122e397af111e3dc5fb6b60004aad9464980f13a", false),
		// (0.13.31) "Canoe Boats" (by CanineGray, CC BY-NC 4.0; CurseForge 1086847, file 8944107, the same file as
		// Modrinth's 26.3 version, fetched from there): every boat a canoe, through Entity Model Features.
		new Pack("canoe boats", "jaymospowers-canoe-boats-v1", "Canoe boats", "Canoe boats 26.3.zip",
			"https://cdn.modrinth.com/data/5NzqnXzh/versions/ZGZQqb3K/Canoe%20boats%2026.3.zip",
			"b324c1097659ecf79a0d29fe057ed363f4914823", false),
		// (Motion patch 6) "Fresh Animations" itself (by FreshLX; its terms let modpacks include it, never redistribute
		// or edit it): livelier walks and idles for most vanilla mobs, run by Entity Model Features, the base the Player
		// Extension builds on. Version 1.11.1, made for 26.1+, fetched from Modrinth (project 50dA9Sha). Our mob poses
		// (downed, tripped, carried, pets) pause it for that mob while they play (PoseTakeover#mobPosed).
		new Pack("Fresh Animations", "jaymospowers-fresh-animations-v1", "FreshAnimations", "FreshAnimations_v1.11.1_26.1+.zip",
			"https://cdn.modrinth.com/data/50dA9Sha/versions/tvGVYMD4/FreshAnimations_v1.11.1_26.1%2B.zip",
			"ae9175983c5109162c86daae1acff34ccf623faf", false),
		// (0.18.124, Havens patch 8) "Better Horses" (by CanineGray, CC BY-NC 4.0: shipped unchanged, credit to its author,
		// never edited or copied from): redrawn donkeys and mules through Entity Model Features and Entity Texture
		// Features, with coat variants. Version 4.3 for 1.21.11 (formats 22-94, older than 26.3's), fetched from Modrinth
		// (project alMAK8J4). Horses, skeleton horses and zombie horses are Jaymo's Kingdoms' breed horses
		// (BreedHorseRenderer), so only donkeys and mules take its look. Listed after Fresh Animations so it wins there.
		new Pack("Better Horses", "jaymospowers-better-horses-v1", "Better Horses", "Better Horses v4.3 1.21.11.zip",
			"https://cdn.modrinth.com/data/alMAK8J4/versions/EfH2muyG/Better%20Horses%20v4.3%201.21.11.zip",
			"fea54f680dc959e122189b2005d11adebcd18b5b", true),
		// "Tissou's Zombie Pack" (by Tissou, CurseForge 312822, file 4750306; free to use with credit, so fetched unchanged,
		// never copied): over 1600 modern-dress zombie skins through Entity Texture Features. Andy (2026-11-15): "All can go
		// into the future". Jaymo's Kingdoms keeps it on Chrono's future dead only (TissouPack): the present's dead wear our
		// own skins, and the textures it repaints outright are read back from the game's own. Format 15 (1.20.1).
		new Pack("Tissou's zombies", "jaymospowers-tissou-zombies-v1", "TZP_", "TZP_1.20.1_2.7.zip",
			"https://mediafilez.forgecdn.net/files/4750/306/TZP_1.20.1_2.7.zip",
			"0f24fbc99e9a6f4d1b9ceb628f9389a901137ec1", true),
		// Sensible MC picks (Andy, 2026-11-16). Each fetched unchanged from Modrinth, never shipped, edited or copied.
		// Fresh Animations' own add-ons (by FreshLX, under its terms): extra mob details and a new look for creepers,
		// just above Fresh Animations. Details 2.4 (project lctgpCsu), Creepers 2.2 (project AhPf2E1u).
		new Pack("Fresh Animations: Details", "jaymospowers-fa-details-v1", "FA+Details", "FA+Details-v2.4.zip",
			"https://cdn.modrinth.com/data/lctgpCsu/versions/XM8VyQLk/FA%2BDetails-v2.4.zip",
			"b98a11e783b79851e6f3c72e4b1eaf9b401d7599", false, "FreshAnimations"),
		new Pack("Fresh Animations: Creepers", "jaymospowers-fa-creepers-v1", "FA+Creepers", "FA+Creepers-v2.2.zip",
			"https://cdn.modrinth.com/data/AhPf2E1u/versions/b90eVujh/FA%2BCreepers-v2.2.zip",
			"c65e34ee299998d39bec9178f6793af1099733fa", false, "FreshAnimations"),
		// "Armored Legacy" (CC BY-NC-ND 4.0): 3D armour models through Entity Model Features. 1.6.0 (project zwmalH7z).
		new Pack("Armored Legacy", "jaymospowers-armored-legacy-v1", "Armored Legacy", "Armored Legacy 1.6.0+26.3.zip",
			"https://cdn.modrinth.com/data/zwmalH7z/versions/bwCz6yGQ/Armored%20Legacy%201.6.0%2B26.3.zip",
			"b33560a6a80ef09d15e8b9b74c65ff18e6022008", false),
		// "3D Ladders" (all rights reserved), "Remodeled Doors 3D" (all rights reserved), "Fabulous Fences" (CC BY 4.0) and
		// "(Bee's) Fancy Crops" (all rights reserved): small model packs (projects ti9eZ3CP, emO4C1kf, KeTozie3, UGEVQ6t9).
		new Pack("3D ladders", "jaymospowers-3d-ladders-v1", "\u00a7l3D\u00a7f Ladders", "\u00a7l3D\u00a7f Ladders v1.2.2 - 26.3.zip",
			"https://cdn.modrinth.com/data/ti9eZ3CP/versions/2FzklfBI/%C2%A7l3D%C2%A7f%20Ladders%20v1.2.2%20-%2026.3.zip",
			"a055c6187bff48e16098b9be9735b69b28f64aab", false),
		new Pack("3D doors", "jaymospowers-remodeled-doors-v1", "\u00a7aRemodeled-Doors", "\u00a7aRemodeled-Doors\u00a78_\u00a762.3.1.zip",
			"https://cdn.modrinth.com/data/emO4C1kf/versions/Opp78wgT/%C2%A7aRemodeled-Doors%C2%A78_%C2%A762.3.1.zip",
			"cb0fa5781cd8b3bbe4e3ef86fb8a98cc7c6f78a9", false),
		new Pack("Fabulous Fences", "jaymospowers-fabulous-fences-v1", "FabulousFences", "FabulousFences_2026.2a_RP.zip",
			"https://cdn.modrinth.com/data/KeTozie3/versions/IWewGyev/FabulousFences_2026.2a_RP.zip",
			"3028b6714a000343d17bb6c2bd76724bbad76383", false),
		new Pack("Fancy Crops", "jaymospowers-fancy-crops-v1", "Fancy Crops", "Fancy Crops v1.4.zip",
			"https://cdn.modrinth.com/data/UGEVQ6t9/versions/I3d6FlUX/Fancy%20Crops%20v1.4.zip",
			"a247188ff93c19045cda4344d80e3cb6e1b00494", false),
		// "Enchanted Books: Re-covered" (CC BY-NC-ND 4.0): each enchanted book its own cover. 4.0 (project 6gN7YVi7).
		new Pack("Re-covered enchanted books", "jaymospowers-recovered-books-v1", "Re-covered", "Re-covered.zip",
			"https://cdn.modrinth.com/data/6gN7YVi7/versions/6JpG9oNx/Re-covered.zip",
			"59571d3e7f626fe7ef00b39bfa32400fdff4d7e3", false),
		// "KSPACK" (MIT): the textures Tiered Shields (ks-shields, build.gradle) draws its shields with. 26.3-1.
		new Pack("Tiered Shields' textures", "jaymospowers-kspack-v1", "KSPACK", "KSPACK_26.3-1.zip",
			"https://cdn.modrinth.com/data/1NDTwAcs/versions/4gUjKYHi/KSPACK_26.3-1.zip",
			"ab93778ed41546c8bccd46d518e83a745531235e", false));

	/**
	 * File name prefixes of packs that left the pack: switched off at launch if found on. "Fluid & Foolish" held only
	 * Punchy! Moves' moves, and Punchy! Moves left with Punchy! (Andy, 2026-11-16: they lagged the game).
	 */
	private static final List<String> RETIRED = List.of("Fluid & Foolish");

	/** (0.19.72) Andy: Excalibur and its additions optional and off by default. Fetched, never switched on here. */
	static final List<Pack> OPTIONAL = List.of(
		// (0.19.65; optional and off by default since 0.19.72) "Excalibur" (by Matt "Maffhew" Dillow; CC BY-NC-ND 3.0:
		// shipped unchanged, never edited or built on, with a link to its official page on the pack's page): the whole game redrawn in its darker, earthier medieval
		// style. Version 26.3_01, fetched from Modrinth (project hJAzl1Bs). Downloaded into resourcepacks/ but never
		// switched on by us: /excalibur on (ExcaliburLook) puts it at the bottom of the pack order, so Fresh Animations,
		// its patch below, our 3D items and every other pack draw over it. Our own textures' graded copies are the
		// built-in excalibur_style pack (tools/excalibur_style, docs/Excalibur-Style.md); none of its pixels are in them.
		new Pack("Excalibur", "jaymospowers-excalibur-v1", "Excalibur", "Excalibur_V26.3_01.zip",
			"https://cdn.modrinth.com/data/hJAzl1Bs/versions/lk2oxwrP/Excalibur_V26.3_01.zip",
			"97348d602be6def05fdfa9eee339ac64be5badd6", false, BASE),
		// (0.19.65) "Excalibur | Fresh Animations" (by Ruseki and GrandPappyJay; all rights reserved, but its page lets
		// modpacks include it unchanged, never reupload or alter it): Fresh Animations' mobs drawn in Excalibur's style.
		// Version 1.10.5 Hotfix 1 for 26.3, fetched from Modrinth (project yiTthr0O). It's made against Fresh
		// Animations 1.10.5 and we ship 1.11.1; the mobs it covers bring their own models, so they still match. It sits
		// just above Fresh Animations, under Better Horses, so donkeys and mules keep Better Horses' look. Also only switched
		// on by /excalibur on.
		new Pack("Excalibur's Fresh Animations", "jaymospowers-excalibur-fa-v1", "EFA_", "EFA_1.10.5 Hotfix 1.zip",
			"https://cdn.modrinth.com/data/yiTthr0O/versions/UmkPWet8/EFA_1.10.5%20Hotfix%201.zip",
			"0380c78d4e0086b6cade7597a4b57f14d06616ec", false, "FreshAnimations"));

	private PackResourcePacks() {
	}

	static void enableOnce(Minecraft minecraft) {
		if (minecraft.options.resourcePacks.removeIf(id -> RETIRED.stream().anyMatch(prefix -> id.startsWith("file/" + prefix)))) {
			minecraft.options.loadSelectedResourcePacks(minecraft.getResourcePackRepository());
			minecraft.options.save();
			minecraft.reloadResourcePacks();
		}
		for (Pack pack : PACKS) {
			enableOnce(minecraft, pack);
		}
		ExcaliburLook.start(minecraft);
	}

	private static void enableOnce(Minecraft minecraft, Pack pack) {
		Path config = FabricLoader.getInstance().getConfigDir();
		Path marker = config.resolve(pack.marker());
		if (pack.keptOn() ? Files.exists(config.resolve(pack.marker() + OFF)) : Files.exists(marker)) {
			return;
		}
		Path dir = minecraft.getResourcePackDirectory();
		String file = find(dir, pack);
		if (file != null) {
			if (pack.keptOn() && minecraft.options.resourcePacks.contains("file/" + file)) {
				// on already: only make sure the game won't drop it as made for an older version at the next load
				if (pack.olderFormat() && !minecraft.options.incompatibleResourcePacks.contains("file/" + file)) {
					minecraft.options.incompatibleResourcePacks.add("file/" + file);
					minecraft.options.save();
				}
				return;
			}
			enable(minecraft, pack, file, marker);
			return;
		}
		// not there: fetch it in the background, then switch it on (a failed download tries again next launch)
		CompletableFuture.supplyAsync(() -> download(dir, pack), Util.nonCriticalIoPool()).thenAcceptAsync(got -> {
			if (got != null) {
				minecraft.getResourcePackRepository().reload();
				enable(minecraft, pack, got, marker);
			}
		}, minecraft);
	}

	private static void enable(Minecraft minecraft, Pack pack, String file, Path marker) {
		String id = "file/" + file;
		if (minecraft.getResourcePackRepository().getPack(id) == null) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Resource pack {} is in resourcepacks/ but the game didn't load it", file);
			return;
		}
		if (pack.olderFormat() && !minecraft.options.incompatibleResourcePacks.contains(id)) {
			minecraft.options.incompatibleResourcePacks.add(id);
		}
		if (!minecraft.options.resourcePacks.contains(id)) {
			minecraft.options.resourcePacks.add(placeAt(minecraft.options.resourcePacks, pack.place()), id);
		}
		// downloads finish in any order: an add-on switched on before the pack it belongs above moves up over it now
		for (Pack addOn : PACKS) {
			if (pack.prefix().equals(addOn.place())) {
				minecraft.options.resourcePacks.stream().filter(s -> s.startsWith("file/" + addOn.prefix())).findFirst().ifPresent(s -> {
					minecraft.options.resourcePacks.remove(s);
					minecraft.options.resourcePacks.add(placeAt(minecraft.options.resourcePacks, addOn.place()), s);
				});
			}
		}
		minecraft.options.loadSelectedResourcePacks(minecraft.getResourcePackRepository());
		minecraft.options.save();
		minecraft.reloadResourcePacks();
		com.jaymospowers.JaymosPowers.LOGGER.info("Switched on resource pack {} ({})", file, pack.what());
		try {
			Files.writeString(marker, "Jaymo's Powers switched on " + file + " (" + pack.what() + "). "
				+ (pack.keptOn() ? "It's switched on again whenever it's found off; create " + pack.marker() + OFF + " here to keep it off.\n"
					: "Delete this file to do it again.\n"));
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't write {}", marker, e);
		}
	}

	/**
	 * Where a pack goes in the selected list (first = bottom). On top by default; {@link #BASE} goes just under the
	 * lowest resource pack file or pack of ours, so the game's and mods' own packs stay beneath it; a file prefix goes
	 * just above that file.
	 */
	static int placeAt(List<String> selected, @Nullable String place) {
		if (place == null) {
			return selected.size();
		}
		for (int i = 0; i < selected.size(); i++) {
			String id = selected.get(i);
			if (BASE.equals(place) ? id.startsWith("file/") || id.contains(com.jaymospowers.JaymosPowers.MOD_ID)
					: id.startsWith("file/" + place)) {
				return BASE.equals(place) ? i : i + 1;
			}
		}
		return selected.size();
	}

	static @Nullable String download(Path dir, Pack pack) {
		Path target = dir.resolve(pack.file());
		Path part = dir.resolve(pack.file() + ".part");
		try {
			Files.createDirectories(dir);
			URLConnection connection = URI.create(pack.url()).toURL().openConnection();
			connection.setConnectTimeout(15_000);
			connection.setReadTimeout(30_000);
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			try (InputStream in = connection.getInputStream()) {
				Files.copy(new DigestInputStream(in, sha1), part, StandardCopyOption.REPLACE_EXISTING);
			}
			String got = HexFormat.of().formatHex(sha1.digest());
			if (!got.equals(pack.sha1())) {
				com.jaymospowers.JaymosPowers.LOGGER.warn("Downloaded {} but its SHA-1 is {}, not {}; not using it", pack.file(), got, pack.sha1());
				Files.deleteIfExists(part);
				return null;
			}
			Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
			com.jaymospowers.JaymosPowers.LOGGER.info("Downloaded {} ({}) into resourcepacks/", pack.file(), pack.what());
			return pack.file();
		} catch (IOException | NoSuchAlgorithmException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't download {} ({}); trying again next launch", pack.file(), pack.what(), e);
			try {
				Files.deleteIfExists(part);
			} catch (IOException ignored) {
			}
			return null;
		}
	}

	static @Nullable String find(Path dir, Pack pack) {
		if (!Files.isDirectory(dir)) {
			return null;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, pack.prefix() + "*.zip")) {
			for (Path file : files) {
				return file.getFileName().toString();
			}
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't look in {}", dir, e);
		}
		return null;
	}
}
