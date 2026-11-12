package com.jaymospowers.client.collide;

import com.jaymospowers.client.LedgeClient;
import com.jaymospowers.client.freerun.FreerunClient;
import com.jaymospowers.client.freerun.WallSlideClient;
import com.jaymospowers.core.collide.Collisions;
import com.jaymospowers.power.web.KitchenCompat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/**
 * Collisions ({@link Collisions}) of this client's own player and the mount they steer: their moves are worked out here,
 * so here is where a wall slam, a sprint into a wall, a head bump or a crash is seen. The server does the hurting.
 */
public final class CollisionsClient {
	/** The wall must stand at least this high above the feet (blocks) to bump a sprinter: a step or a kerb doesn't. */
	static final double WALL_ABOVE = 1.0;
	/** No kind is sent again within this many ticks. */
	static final int RESEND = 5;

	private static final long[] SENT = {-99, -99, -99, -99};

	private CollisionsClient() {
	}

	public static void init() {
		Collisions.setClientMoves(CollisionsClient::moved);
	}

	private static void moved(Entity entity, Vec3 movement, double dx, double dy, double dz) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		if (entity == player) {
			own(player, movement, dx, dy, dz);
		} else if (entity instanceof AbstractHorse mount && mount.getControllingPassenger() == player && entity.horizontalCollision
			&& !entity.minorHorizontalCollision && mount.onGround() && Collisions.crashes(mount)) {
			double impact = Math.hypot(movement.x - dx, movement.z - dz);
			if (impact >= Collisions.CRASH_MIN) {
				send(player, Collisions.CRASH, impact, movement);
			}
		}
	}

	private static void own(LocalPlayer player, Vec3 movement, double dx, double dy, double dz) {
		if (player.isPassenger() || player.isFallFlying() || player.getAbilities().flying || KitchenCompat.limp(player)
			|| FreerunClient.active() || LedgeClient.active() || WallSlideClient.sliding()) {
			return;
		}
		boolean free = !player.isInWater() && !player.onClimbable() && !player.isCrouching() && !player.isVisuallyCrawling();
		if (player.verticalCollision && !player.verticalCollisionBelow && movement.y > 0.0 && free) {
			double impact = movement.y - dy;
			if (impact >= Collisions.HEAD_MIN) {
				send(player, Collisions.HEAD, impact, movement);
			}
		}
		if (!player.horizontalCollision) {
			return;
		}
		double impact = Math.hypot(movement.x - dx, movement.z - dz);
		if (player.hurtTime > 0 && impact >= Collisions.SLAM_MIN) {
			send(player, Collisions.SLAM, impact, movement);
		} else if (player.isSprinting() && player.onGround() && free && !player.minorHorizontalCollision && impact >= Collisions.SPRINT_MIN
			&& wallAhead(player, movement)) {
			send(player, Collisions.SPRINT, impact, movement);
		}
	}

	/** Whether there's wall in the way at body height, not just a step at the feet. */
	private static boolean wallAhead(LocalPlayer player, Vec3 movement) {
		double flat = Math.hypot(movement.x, movement.z);
		if (flat < 1.0E-4) {
			return false;
		}
		var box = player.getBoundingBox().move(movement.x / flat * 0.1, 0.0, movement.z / flat * 0.1).setMinY(player.getY() + WALL_ABOVE);
		return !player.level().noCollision(player, box);
	}

	private static void send(LocalPlayer player, byte kind, double impact, Vec3 movement) {
		long now = player.level().getGameTime();
		if (now - SENT[kind] < RESEND) {
			return;
		}
		SENT[kind] = now;
		ClientPlayNetworking.send(new Collisions.Bump(kind, (float) impact, (float) movement.x, (float) movement.z));
	}
}
