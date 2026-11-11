package com.jaymospowers.client.divine;

import com.jaymospowers.divine.DivineSense;
import com.jaymospowers.stamina.Feathers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Divine-born's motion on this side (see {@link DivineSense}): held a little off the ground while Alight lasts, and
 * the hover, Jump pressed again in the air and held.
 */
public final class DivineSenseClient {
	/** Alight: ticks of rising, how fast (blocks/tick), and the bob after (blocks/tick at its fastest). */
	static final int RISE_TICKS = 8;
	static final double RISE = 0.11, BOB = 0.012;
	/** Alight: the share of sideways speed kept each tick. */
	static final double STILL = 0.5;

	private static int alightTicks;
	private static boolean hovering;
	/** Jump was let go in the air: the next press hovers. */
	private static boolean armed;

	private DivineSenseClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DivineSenseClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused()) {
			return;
		}
		if (player.hasAttached(DivineSense.ALIGHT)) {
			alight(player);
			return;
		}
		alightTicks = 0;
		boolean jump = minecraft.options.keyJump.isDown();
		boolean can = player.hasAttached(DivineSense.DIVINE) && !player.onGround() && !player.getAbilities().flying && !player.isFallFlying()
			&& !player.isInWater() && !player.onClimbable() && !player.isPassenger() && player.isAlive();
		if (!can) {
			armed = false;
		} else if (!jump) {
			armed = true;
		}
		boolean now = can && armed && jump && Feathers.view(player).spendable() > 0;
		if (now) {
			Vec3 v = player.getDeltaMovement();
			if (v.y < DivineSense.HOVER_FALL) {
				player.setDeltaMovement(v.x, DivineSense.HOVER_FALL, v.z);
				player.resetFallDistance();
			} else {
				// still rising: not hovering yet
				now = hovering;
			}
		}
		if (now != hovering) {
			hovering = now;
			ClientPlayNetworking.send(new DivineSense.Act(now ? DivineSense.HOVER_ON : DivineSense.HOVER_OFF));
		}
	}

	private static void alight(LocalPlayer player) {
		hovering = false;
		armed = false;
		alightTicks++;
		Vec3 v = player.getDeltaMovement();
		double y = alightTicks <= RISE_TICKS ? RISE : Math.sin(alightTicks * 0.15) * BOB;
		player.setDeltaMovement(v.x * STILL, y, v.z * STILL);
		player.resetFallDistance();
	}
}
