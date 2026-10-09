package com.jaymospowers.client.power.gravity;

import com.jaymospowers.client.power.wind.WindPowerClient;
import com.jaymospowers.power.gravity.GravityPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Held in someone's Lift (you, the local player): stunned at first, no keys answer (a wind flyer tumbles out of control);
 * once it wears off, moving pushes against the hold, and far enough out you're free (the server lets go). A wind flyer
 * just flies out.
 */
public final class GravityHeldClient {
	/** How hard struggling pushes (blocks/tick), and jumping up. */
	private static final double STRUGGLE = 0.14, STRUGGLE_UP = 0.1;

	private GravityHeldClient() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(GravityHeldClient::tick);
	}

	/** Stunned in a Lift right now: movement keys do nothing (see the input mixin). */
	public static boolean stunned() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return false;
		}
		GravityPower.Held held = player.getAttached(GravityPower.HELD);
		return held != null && player.level().getGameTime() < held.stunUntil();
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused()) {
			return;
		}
		GravityPower.Held held = player.getAttached(GravityPower.HELD);
		if (held == null) {
			return;
		}
		long left = held.stunUntil() - player.level().getGameTime();
		if (left > 0) {
			if (held.wind()) {
				WindPowerClient.INSTANCE.flight().heldStun(minecraft, player, (int)left);
			}
			return;
		}
		if (WindPowerClient.INSTANCE.flight().state().flying()) {
			return; // the wind carries a flyer out by itself
		}
		// struggling: the keys push against the hold
		Vec2 move = player.input.getMoveVector();
		float sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
		float cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
		Vec3 push = new Vec3(move.x * cos - move.y * sin, player.input.keyPresses.jump() ? STRUGGLE_UP / STRUGGLE : 0.0, move.y * cos + move.x * sin);
		if (push.lengthSqr() > 1.0E-4) {
			player.setDeltaMovement(player.getDeltaMovement().add(push.scale(STRUGGLE)));
		}
	}
}
