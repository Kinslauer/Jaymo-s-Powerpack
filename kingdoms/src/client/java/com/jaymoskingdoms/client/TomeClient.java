package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Tomes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The spell tomes' left click ({@link Tomes}). Vanilla sends nothing to the server for a click on air, so while a tome is in the
 * main hand this watches the attack key and sends its press and release; the server times the hold and flies the bolt.
 * While a tome is held the attack key neither swings nor digs (see {@code TomeAttackMixin}).
 */
final class TomeClient {
	private static boolean down, wasKey;

	private TomeClient() {
	}

	/** Whether the attack key belongs to the tome in the hand now. */
	static boolean holdsAttack(Minecraft minecraft) {
		return minecraft.player != null && Tomes.isTome(minecraft.player.getMainHandItem());
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TomeClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean tome = player != null && holdsAttack(minecraft) && !player.isSpectator() && !player.isUsingItem();
		boolean key = minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null && minecraft.mouseHandler.isMouseGrabbed();
		if (!ClientPlayNetworking.canSend(Tomes.Attack.TYPE)) {
			down = false;
			wasKey = key;
			return;
		}
		if (down) {
			if (!tome || !key) {
				// let go: fire; the book changed or a screen opened: drop the charge
				ClientPlayNetworking.send(new Tomes.Attack(tome && minecraft.gui.screen() == null ? 0 : 2));
				down = false;
			}
		} else if (tome && key && !wasKey) {
			// only a fresh press starts one (not a button still down from before the book was drawn)
			ClientPlayNetworking.send(new Tomes.Attack(1));
			down = true;
		}
		wasKey = key;
	}
}
