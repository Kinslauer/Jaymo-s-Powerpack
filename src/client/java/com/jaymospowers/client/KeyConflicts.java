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
 */
final class KeyConflicts {
	/**
	 * Bump to run the move again (for a new clash in a later pack version). v2 (0.9.21): Camera Utils' zoom, on Z (the
	 * pack zooms with OK Zoomer).
	 */
	private static final String MARKER = "jaymospowers-keys-v2";

	/** Key mapping name → where it goes (unknown = unbound), applied only while it's still on {@code from}. */
	private record Move(String from, String to) {
	}

	private static final Map<String, Move> MOVES = Map.of(
		"gui.xaero_new_waypoint", new Move("key.keyboard.b", "key.keyboard.n"),
		"gui.xaero_enlarge_map", new Move("key.keyboard.z", "key.keyboard.m"),
		"key.pantryforblockheads.your_key", new Move("key.keyboard.b", "key.keyboard.k"),
		"key.ok_zoomer.zoom", new Move("key.keyboard.c", "key.keyboard.left.alt"),
		"key.quickActions", new Move("key.keyboard.g", "key.keyboard.j"),
		"key.saveToolbarActivator", new Move("key.keyboard.c", "key.keyboard.unknown"),
		"key.loadToolbarActivator", new Move("key.keyboard.x", "key.keyboard.unknown"),
		"key.camerautils.zoom", new Move("key.keyboard.z", "key.keyboard.unknown"));

	private KeyConflicts() {
	}

	static void resolveOnce(Minecraft minecraft) {
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
}
