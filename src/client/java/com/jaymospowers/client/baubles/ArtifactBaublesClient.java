package com.jaymospowers.client.baubles;

import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.baubles.artifacts.ArtifactEffects;
import com.jaymospowers.baubles.artifacts.ArtifactNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * (Artifacts) The moves the Artifacts baubles give, made here since a player's movement is their own client's: the cloud
 * in a bottle's second jump, the wind bladder's float (press jump again in the air and hold it, up to 8 seconds, back
 * in 15 on the ground), and the charm of sinking pulling you down through water. The server is told so it can check and
 * clear the fall ({@link ArtifactNet}).
 */
public final class ArtifactBaublesClient {
	/** Cloud in a bottle: the second jump's strength against a normal jump, and its push the way you steer. */
	static final double CLOUD_POWER = 1.1, CLOUD_PUSH = 0.25, BASE_JUMP = 0.42;
	/** Wind bladder: rise speed (blocks a tick), how long it lasts and how long it takes to fill again (ticks). */
	static final double FLOAT_RISE = 0.14;
	static final int FLOAT_TICKS = 8 * 20, FLOAT_REFILL = 15 * 20;
	/** Charm of sinking: extra pull down in water each tick, unless you hold jump. */
	static final double SINK = 0.03;

	private static boolean spent, wasJump, floating;
	private static float air = FLOAT_TICKS;

	private ArtifactBaublesClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ArtifactBaublesClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			wasJump = false;
			floating = false;
			return;
		}
		boolean jump = minecraft.options.keyJump.isDown();
		boolean pressed = jump && !wasJump;
		wasJump = jump;
		boolean grounded = player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying;
		boolean free = !player.isSpectator() && !player.isPassenger() && !player.isFallFlying() && !player.onClimbable();
		if (grounded) {
			spent = false;
			floating = false;
			air = Math.min(FLOAT_TICKS, air + (float) FLOAT_TICKS / FLOAT_REFILL);
		} else if (free && pressed && !spent && ArtifactEffects.wears(player, ArtifactBaubles.CLOUD_IN_A_BOTTLE)) {
			spent = true;
			double yaw = Math.toRadians(player.getYRot());
			double dx = -Math.sin(yaw) * player.zza + Math.cos(yaw) * player.xxa;
			double dz = Math.cos(yaw) * player.zza + Math.sin(yaw) * player.xxa;
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length > 1.0) {
				dx /= length;
				dz /= length;
			}
			Vec3 v = player.getDeltaMovement();
			double push = player.isSprinting() ? CLOUD_PUSH : CLOUD_PUSH * 0.5;
			player.setDeltaMovement(v.x + dx * push, BASE_JUMP * CLOUD_POWER, v.z + dz * push);
			player.resetFallDistance();
			ClientPlayNetworking.send(new ArtifactNet.Move(ArtifactNet.CLOUD_JUMP));
		} else if (free && ArtifactEffects.wears(player, ArtifactBaubles.WIND_BLADDER)) {
			if (pressed) {
				floating = true;
			}
			if (floating && jump && air > 0) {
				air--;
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(v.x, Math.max(v.y, FLOAT_RISE), v.z);
				player.resetFallDistance();
				if (player.tickCount % 5 == 0) {
					ClientPlayNetworking.send(new ArtifactNet.Move(ArtifactNet.FLOAT));
				}
			} else if (!jump) {
				floating = false;
			}
		}
		if (player.isInWater() && !jump && !player.getAbilities().flying && ArtifactEffects.wears(player, ArtifactBaubles.CHARM_OF_SINKING)) {
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x, v.y - SINK, v.z);
		}
	}
}
