package com.jaymospowers.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Combat Roll (optional): double-tapping Sneak rolls. R and Sneak + R belong to the powers, so the roll gets its own
 * gesture. Two Sneak presses within {@code DOUBLE_TAP_TICKS} hold Combat Roll's key down for one tick; Combat Roll
 * reads it the next tick and rolls if it can (its own cooldown and rules apply). Its own key still works if bound.
 */
final class CombatRollTaps {
	/** The most ticks between two Sneak presses that count as a double tap. */
	private static final int DOUBLE_TAP_TICKS = 6;

	private static @Nullable KeyMapping rollKey;
	private static boolean sneakWasDown;
	private static int sinceLastTap = Integer.MAX_VALUE;
	/** True while we're the ones holding the roll key down (so a player's own binding is never released by us). */
	private static boolean pressing;

	private CombatRollTaps() {
	}

	static void init() {
		if (!FabricLoader.getInstance().isModLoaded("combat_roll")) {
			return;
		}
		try {
			Object key = Class.forName("net.combat_roll.client.Keybindings").getField("roll").get(null);
			if (key instanceof KeyMapping mapping) {
				rollKey = mapping;
				ClientTickEvents.END_CLIENT_TICK.register(CombatRollTaps::tick);
			}
		} catch (ReflectiveOperationException | LinkageError e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Combat Roll is loaded but its roll key wasn't found; double-tap Sneak won't roll", e);
		}
	}

	private static void tick(Minecraft minecraft) {
		KeyMapping roll = rollKey;
		if (roll == null) {
			return;
		}
		if (pressing) {
			roll.setDown(false);
			pressing = false;
		}
		if (minecraft.player == null || minecraft.gui.screen() != null) {
			sneakWasDown = false;
			sinceLastTap = Integer.MAX_VALUE;
			return;
		}
		boolean sneak = minecraft.options.keyShift.isDown();
		if (sinceLastTap < Integer.MAX_VALUE) {
			sinceLastTap++;
		}
		// Jump + Sneak held together is the crawl (CrawlTaps), and double Sneak with two webs on small things is Web's yank: not a roll
		if (minecraft.options.keyJump.isDown()
			|| minecraft.player.tickCount - com.jaymospowers.client.power.web.WebSwing.reelReadyTick <= 1) {
			sinceLastTap = Integer.MAX_VALUE;
			sneakWasDown = sneak;
			return;
		}
		if (sneak && !sneakWasDown) {
			if (sinceLastTap <= DOUBLE_TAP_TICKS) {
				roll.setDown(true);
				pressing = true;
				sinceLastTap = Integer.MAX_VALUE;
			} else {
				sinceLastTap = 0;
			}
		}
		sneakWasDown = sneak;
	}
}
