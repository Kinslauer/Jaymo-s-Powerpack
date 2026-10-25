package com.jaymospowers.client;

import com.jaymospowers.api.PowerKey;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * The client-side half of a power. Only called while the local player holds the power. Movement powers
 * (flight, swinging) should move the player here: player motion is client-authoritative in Minecraft.
 */
public interface PowerClient {
	/** End of every client tick, while in a world and not paused. */
	default void tick(Minecraft minecraft, LocalPlayer player) {
	}

	/** An ability key went down or up. The server is told separately (it gets the same event via {@code Power.onKey}). */
	default void onKey(LocalPlayer player, PowerKey key, boolean down) {
	}

	/** The Power Mode key (Z by default) went down or up. The server hears it too ({@code Power.onMode}). */
	default void onMode(LocalPlayer player, boolean down) {
	}

	/** The ascended ability's key (C by default) went down or up. Only called while the power is ascended. */
	default void onUltimate(LocalPlayer player, boolean down) {
	}

	/** Draw this power's HUD (cooldowns, meters). The core already draws the power badge in the top-left corner. */
	default void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
	}

	/** (0.15.147) How much of {@code key}'s cooldown is left, 1 (just used) to 0 (ready), for the powers screen. */
	default float cooldown(LocalPlayer player, PowerKey key) {
		return 0.0F;
	}

	/**
	 * (0.16.6) The powers screen's Mode slot was pressed (the screen is already closing). True if this power does its own
	 * thing there (lang {@code power.<id>.toggle.mode} names it); false presses the Mode key as usual.
	 */
	default boolean screenMode(LocalPlayer player) {
		return false;
	}

	/** The local player just gained (true) or lost (false) this power. Reset client state here. */
	default void onActiveChanged(LocalPlayer player, boolean active) {
	}
}
