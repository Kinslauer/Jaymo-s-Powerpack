package com.jaymospowers.client.tome;

import com.jaymospowers.tome.Tome;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.player.LocalPlayer;

/**
 * The spell tome's left click. Vanilla sends nothing to the server for a click on air, so this watches the attack key while a
 * tome is in the main hand and sends its press and release ({@link Tome.Attack}); the server times the hold and fires.
 * While a tome is held the attack key neither swings nor digs ({@link com.jaymospowers.client.HeldInput#locksAttack}). The
 * bolt has no model, only the server's particles, so it gets a renderer that draws nothing.
 */
public final class TomeClient {
	private static boolean down;

	private TomeClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(TomeClient::tick);
		EntityRendererRegistry.register(Tome.BOLT, NoopRenderer::new);
	}

	/** Whether the attack key belongs to the tome in the hand now ({@code HeldInput}'s lock on swings and digging). */
	public static boolean holdsAttack(Minecraft minecraft) {
		return minecraft.player != null && Tome.holdsTome(minecraft.player);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean tome = player != null && Tome.holdsTome(player) && !player.isSpectator() && !player.isUsingItem();
		boolean key = minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null && minecraft.mouseHandler.isMouseGrabbed();
		if (down) {
			if (!tome || !key) {
				// let go: fire; the item changed or a screen opened: drop the charge
				ClientPlayNetworking.send(new Tome.Attack(tome && minecraft.gui.screen() == null ? 0 : 2));
				down = false;
			}
		} else if (tome && key) {
			ClientPlayNetworking.send(new Tome.Attack(1));
			down = true;
		}
	}
}
