package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterAirBurst;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterSensesNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.42, Andy's NOEA picks) The air burst, client half: double-tap forward (W) while flying and a burst of ki throws
 * you {@link #SPEED} blocks a tick along your look, fading over a few ticks (a few blocks in all). It is added on top of
 * the flight's own velocity each tick, as the barrel roll's slide is, so the flight carries on with its own momentum.
 * The server takes the ki and keeps the cooldown ({@link ZFighterAirBurst}); the client only fires one if the ki is
 * there. (A double-tap of W is also vanilla's sprint, which in flight is the cruise.) The barrel roll on a double-tap of
 * A or D is the shared {@code FlightRolls}.
 */
public final class ZFighterAirBurstClient {
	/** The most ticks between two presses of forward that count as a double tap. */
	static final int TAP_TICKS = 7;
	/** The burst's speed along the look (blocks a tick), what is left of it each tick after, and when it is spent. */
	static final double SPEED = 1.5;
	static final double DECAY = 0.72;
	static final double SPENT = 0.05;
	/** Ticks the client waits before another (the server's cooldown is the real one). */
	static final int COOLDOWN = 24;

	private static boolean forwardWasDown;
	private static int sinceTap = Integer.MAX_VALUE;
	private static long last = Long.MIN_VALUE / 2;
	private static Vec3 push = Vec3.ZERO;

	private ZFighterAirBurstClient() {
	}

	/** From the power's own tick, right after the flight set his velocity. */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		long now = player.level().getGameTime();
		boolean forward = minecraft.options.keyUp.isDown();
		if (sinceTap < Integer.MAX_VALUE) {
			sinceTap++;
		}
		boolean flying = ZFighterFlightClient.pose() > 0 && !player.onGround() && !player.isPassenger() && !player.isSpectator();
		if (!flying) {
			push = Vec3.ZERO;
			sinceTap = Integer.MAX_VALUE;
		} else if (minecraft.gui.screen() == null && forward && !forwardWasDown) {
			if (sinceTap <= TAP_TICKS) {
				sinceTap = Integer.MAX_VALUE;
				if (now - last >= COOLDOWN && (player.isCreative() || ZFighterPower.state(player).ki() >= ZFighterAirBurst.KI)) {
					last = now;
					push = player.getLookAngle().scale(SPEED);
					ClientPlayNetworking.send(new ZFighterSensesNet.AirBurst());
					player.level().playLocalSound(player, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 1.3F);
				}
			} else {
				sinceTap = 0;
			}
		}
		forwardWasDown = forward;
		if (push.lengthSqr() > SPENT * SPENT) {
			player.setDeltaMovement(player.getDeltaMovement().add(push));
			push = push.scale(DECAY);
		} else {
			push = Vec3.ZERO;
		}
	}
}
