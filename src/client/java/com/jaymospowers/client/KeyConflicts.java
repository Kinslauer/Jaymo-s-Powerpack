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
 * they were on. They only act over an item in an open screen, so they never clash with R the power key. YDM's Weapon
 * Master's settings key (Y, the Combat Arts key) is unbound once per install for everyone, and so is the narrator's
 * Ctrl+B hotkey (B is the fourth ability key).
 */
final class KeyConflicts {
	/**
	 * Bump to run the move again (for a new clash in a later pack version). v2 (0.9.23): Camera Utils' zoom, on Z (the
	 * pack zooms with OK Zoomer). v3 (0.10.65): Traveler Tool Belt on R; Shoulder Surfing's free look on Left Alt (OK
	 * Zoomer's zoom) and swap shoulder on U (Kingdoms' werewolf shift). v4 (0.11.74): Kingdoms' night gifts take O and
	 * P, so swap shoulder goes on to the full stop (from U or O) and the social interactions screen comes off P.
	 */
	private static final String MARKER = "jaymospowers-keys-v4";

	/** Key mapping name → where it goes (unknown = unbound), applied only while it's still on {@code from} (or one of several, split by |). */
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
		Map.entry("key.shouldersurfing.swap_shoulder", new Move("key.keyboard.u|key.keyboard.o", "key.keyboard.period")),
		Map.entry("key.socialInteractions", new Move("key.keyboard.p", "key.keyboard.unknown")),
		// (0.17.25) L is Kingdoms' mana spirit key; Advancements stays on the pause menu
		Map.entry("key.advancements", new Move("key.keyboard.l", "key.keyboard.unknown")));

	/**
	 * Bump to set JEI's hover keys again. v1 (0.11.15): showRecipe had come unbound in Andy's profile. v2 (0.11.31):
	 * a left click shows an item's recipe and a right click its uses, wherever JEI's mouse keys had got to.
	 */
	private static final String JEI_MARKER = "jaymospowers-jei-keys-v2";

	/** JEI key mapping name → its key, set once per install whatever it was. */
	private static final Map<String, String> JEI_KEYS = Map.of(
		"key.jei.showRecipe", "key.keyboard.r",
		"key.jei.showRecipe2", "key.mouse.left",
		"key.jei.showUses2", "key.mouse.right",
		"key.jei.bookmark", "key.keyboard.a");

	/** Bump to unbind YDM's Weapon Master again. v1 (0.11.22): its Open Settings key sat on Y. */
	private static final String WEAPON_MASTER_MARKER = "jaymospowers-weaponmaster-keys-v1";

	/** YDM's Weapon Master key mappings, unbound once per install whatever they were on. */
	private static final Map<String, String> WEAPON_MASTER_KEYS = Map.of(
		"key.weaponmaster_ydm.opengui", "key.keyboard.unknown");

	/** Bump to switch the narrator hotkey off again. v1 (0.11.22). */
	private static final String NARRATOR_MARKER = "jaymospowers-narrator-hotkey-v1";

	private KeyConflicts() {
	}

	static void resolveOnce(Minecraft minecraft) {
		setKeysOnce(minecraft, "jei", JEI_MARKER, JEI_KEYS, "put JEI's Show Recipe on R and left click, Show Uses on right click and Bookmark on A");
		setKeysOnce(minecraft, "weaponmaster_ydm", WEAPON_MASTER_MARKER, WEAPON_MASTER_KEYS, "unbound YDM's Weapon Master keys");
		narratorHotkeyOffOnce(minecraft);
		Path marker = FabricLoader.getInstance().getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		int moved = 0;
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			Move move = MOVES.get(mapping.getName());
			if (move != null && java.util.Arrays.asList(move.from().split("\\|")).contains(mapping.saveString())) {
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

	/** Switches off the narrator's Ctrl+B hotkey once per install (Accessibility Settings can turn it back on). */
	private static void narratorHotkeyOffOnce(Minecraft minecraft) {
		Path marker = FabricLoader.getInstance().getConfigDir().resolve(NARRATOR_MARKER);
		if (Files.exists(marker)) {
			return;
		}
		if (minecraft.options.narratorHotkey().get()) {
			minecraft.options.narratorHotkey().set(false);
			minecraft.options.save();
		}
		try {
			Files.writeString(marker, "Jaymo's Powers switched off the narrator hotkey (Ctrl+B). Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't write {}", marker, e);
		}
	}

	/** Sets each of {@code keys} once per install (marker file), whatever it was on, if {@code modId} is loaded. */
	private static void setKeysOnce(Minecraft minecraft, String modId, String markerName, Map<String, String> keys, String what) {
		Path marker = FabricLoader.getInstance().getConfigDir().resolve(markerName);
		if (!FabricLoader.getInstance().isModLoaded(modId) || Files.exists(marker)) {
			return;
		}
		int set = 0;
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			String key = keys.get(mapping.getName());
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
			Files.writeString(marker, "Jaymo's Powers " + what + " (" + set + " changed). Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't write {}", marker, e);
		}
	}
}
