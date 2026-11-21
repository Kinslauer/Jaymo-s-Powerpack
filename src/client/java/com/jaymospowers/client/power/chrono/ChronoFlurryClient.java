package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoFlow;
import com.jaymospowers.power.chrono.ChronoFlurry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;

/**
 * Tells the server when the local Chrono's attack has been held {@link ChronoFlurry#HOLD_TICKS} while overclocked, and
 * when it's let go: the server throws the flurry, or swings the held weapon ({@link ChronoFlurry}). Held at a block it's mining, so it never starts
 * there; a click stays a plain attack.
 */
public final class ChronoFlurryClient {
	private static int held;
	private static boolean sent;

	private ChronoFlurryClient() {
	}

	public static void tick(LocalPlayer player, boolean overclocked) {
		Minecraft minecraft = Minecraft.getInstance();
		HitResult hit = minecraft.hitResult;
		boolean down = overclocked && minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null && !player.isSpectator()
			&& ChronoFlurry.inHand(player) && !Boolean.TRUE.equals(player.getAttached(ChronoFlow.ON)) && (hit == null || hit.getType() != HitResult.Type.BLOCK);
		held = down ? held + 1 : 0;
		send(held >= ChronoFlurry.HOLD_TICKS);
	}

	public static void reset() {
		held = 0;
		send(false);
	}

	private static void send(boolean on) {
		if (on != sent && ClientPlayNetworking.canSend(ChronoFlurry.Hold.TYPE)) {
			ClientPlayNetworking.send(new ChronoFlurry.Hold(on));
			sent = on;
		} else if (!ClientPlayNetworking.canSend(ChronoFlurry.Hold.TYPE)) {
			sent = false;
		}
	}
}
