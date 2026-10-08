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

	/** Draw this power's HUD (cooldowns, meters). The core already draws the power badge in the top-left corner. */
	default void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
	}

	/** The local player just gained (true) or lost (false) this power. Reset client state here. */
	default void onActiveChanged(LocalPlayer player, boolean active) {
	}
}
