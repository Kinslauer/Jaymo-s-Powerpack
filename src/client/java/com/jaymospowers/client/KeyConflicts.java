package com.jaymospowers.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The power keys (R, G, V, B, C, Z, X, H) belong to the powers. Once per install, anything else in the pack still on
 * one of them (at its default) is moved to a free key, or unbound. After that, players' own rebinds are never touched.
 * JEI's hover keys are the exception: R over an item shows its recipe and A bookmarks it, set once per install whatever
 * they were on. They only act over an item in an open screen, so they never clash with R the power key.
 */
final class KeyConflicts {
	/**
	 * Bump to run the move again (for a new clash in a later pack version). v2 (0.9.23): Camera Utils' zoom, on Z (the
	 * pack zooms with OK Zoomer). v3 (0.10.65): Traveler Tool Belt on R; Shoulder Surfing's free look on Left Alt (OK
	 * Zoomer's zoom) and swap shoulder on U (Kingdoms' werewolf shift).
	 */
	private static final String MARKER = "jaymospowers-keys-v3";

	/** Key mapping name → where it goes (unknown = unbound), applied only while it's still on {@code from}. */
	private record Move(String from, String to) {
	}

	private static final Map<String, Move> MOVES = Map.ofEntries(
		Map.entry("gui.xaero_new_waypoint", new Move("key.keyboard.b", "key.keyboard.n")),
		Map.entry("gui.xaero_enlarge_map", new Move("key.keyboard.z", "key.keyboard.m")),
		Map.entry("key.pantryforblockheads.your_key", new Move("key.keyboard.b", "key.keyboard.k")),
		Map.entry("key.ok_zoomer.zoom", new Move("key.keyboard.c", "key.keyboard.left.alt")),
		Map.entry("key.quickActions", new Move("key.keyboard.g", "key.keyboard.j")),
		Map.entry("key.saveToolbarActivator", new Move("key.keyboard.c", "key.keyboard.unknown")),
		Map.entry("key.loadToolbarActivator", new Move("key.keyboard.x", "key.keyboard.unknown")),
		Map.entry("key.camerautils.zoom", new Move("key.keyboard.z", "key.keyboard.unknown")),
		Map.entry("key.travelertoolbelt.open_toolbelt", new Move("key.keyboard.r", "key.keyboard.caps.lock")),
		Map.entry("key.shouldersurfing.free_look", new Move("key.keyboard.left.alt", "key.keyboard.right.alt")),
		Map.entry("key.shouldersurfing.swap_shoulder", new Move("key.keyboard.u", "key.keyboard.o")));

	/** Bump to set JEI's hover keys again. v1 (0.11.15): showRecipe had come unbound in Andy's profile. */
	private static final String JEI_MARKER = "jaymospowers-jei-keys-v1";

	/** JEI key mapping name → its key, set once per install whatever it was. */
	private static final Map<String, String> JEI_KEYS = Map.of(
		"key.jei.showRecipe", "key.keyboard.r",
		"key.jei.bookmark", "key.keyboard.a");

	private KeyConflicts() {
	}

	static void resolveOnce(Minecraft minecraft) {
		setJeiKeysOnce(minecraft);
		Path marker = FabricLoader.getInstance().getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		int moved = 0;
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			Move move = MOVES.get(mapping.getName());
			if (move != null && mapping.saveString().equals(move.from())) {
				mapping.setKey(InputConstants.getKey(move.to()));
				moved++;
			}
		}
		if (moved > 0) {
			KeyMapping.resetMapping();
			minecraft.options.save();
		}
		try {
			Files.writeString(marker, "Jaymo's Powers moved " + moved + " clashing key binding(s) off the power keys. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't write {}", marker, e);
		}
	}

	private static void setJeiKeysOnce(Minecraft minecraft) {
		Path marker = FabricLoader.getInstance().getConfigDir().resolve(JEI_MARKER);
		if (!FabricLoader.getInstance().isModLoaded("jei") || Files.exists(marker)) {
			return;
		}
		int set = 0;
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			String key = JEI_KEYS.get(mapping.getName());
			if (key != null && !mapping.saveString().equals(key)) {
				mapping.setKey(InputConstants.getKey(key));
				set++;
			}
		}
		if (set > 0) {
			KeyMapping.resetMapping();
			minecraft.options.save();
		}
		try {
			Files.writeString(marker, "Jaymo's Powers put JEI's Show Recipe on R and Bookmark on A (" + set + " changed). Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't write {}", marker, e);
		}
	}
}
