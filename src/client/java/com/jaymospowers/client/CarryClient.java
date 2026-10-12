package com.jaymospowers.client;

import com.jaymospowers.core.Carry;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Carrying, client side: every tick each carried body is put at its carrier's feet (from the carrier as this client
 * sees them, so the two move as one on every screen; a carried player's own client moves itself the same way and tells
 * the server), the loose limbs' sway is stepped, and a carried player's jump is sent as a struggle.
 */
public final class CarryClient {
	/**
	 * The carried body's limbs swing like damped pendulums against the carrier's motion: they're pulled back to
	 * hanging by {@code SWAY_STIFFNESS}, lose {@code 1 - SWAY_DAMPING} of their swing a tick, are shoved by changes of
	 * pace ({@code SWAY_SHOVE}) and turning ({@code SWAY_TURN}, per degree a tick), and trail behind the motion
	 * ({@code SWAY_TRAIL} per block/tick of speed). Every footstep jogs them ({@code SWAY_STEP}).
	 */
	static final float SWAY_STIFFNESS = 0.14F, SWAY_DAMPING = 0.88F, SWAY_SHOVE = 9.0F, SWAY_TURN = 0.012F;
	static final float SWAY_TRAIL = 1.6F, SWAY_STEP = 0.07F, SWAY_MAX = 1.3F;
	/** The wind trail in flight: 0 at rest, 1 by {@code TRAIL_FULL_SPEED} blocks/tick. */
	static final float TRAIL_FULL_SPEED = 1.2F;

	/** This player, if they're being carried: by whom (entity id), as the server last said; -1 when free. */
	private static int selfCarrier = -1;
	private static boolean jumpWasDown;

	/** Carried entity id → {back, left, backVel, leftVel, prevBack, prevLeft, lastVx, lastVz, lastYaw, trail, prevTrail}. */
	private static final Map<Integer, float[]> SWAY = new HashMap<>();

	private CarryClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Carry.Self.TYPE, (payload, context) -> selfCarrier = payload.carrier());
		ClientTickEvents.END_CLIENT_TICK.register(CarryClient::tick);
	}

	/** Whether the local player is being carried right now (and by whom: their entity), or null. */
	public static @Nullable Player localCarrier(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || selfCarrier < 0) {
			return null;
		}
		Integer by = player.getAttached(Carry.CARRIED_BY);
		if (by == null || by != selfCarrier) {
			return null;
		}
		return player.level().getEntity(by) instanceof Player carrier ? carrier : null;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			SWAY.clear();
			selfCarrier = -1;
			return;
		}
		for (AbstractClientPlayer carrier : level.players()) {
			Carry.Carrying carrying = carrier.getAttached(Carry.CARRYING);
			if (carrying == null) {
				continue;
			}
			Entity carried = level.getEntity(carrying.target());
			if (carried == null) {
				continue;
			}
			if (carried == minecraft.player && localCarrier(minecraft) != carrier) {
				// let go (the server said so first): it's putting us down, don't pull back
				continue;
			}
			follow(carried, carrier);
			sway(carried, carrier);
		}
		SWAY.keySet().removeIf(id -> !(level.getEntity(id) instanceof Entity e) || !Carry.isCarried(e));
		// one jump to get free
		boolean jump = minecraft.options.keyJump.isDown();
		if (jump && !jumpWasDown && localCarrier(minecraft) != null && minecraft.gui.screen() == null) {
			ClientPlayNetworking.send(new Carry.Struggle());
		}
		jumpWasDown = jump;
	}

	/** Puts the carried body at its carrier's feet, this tick and last (so it's drawn moving with them). */
	private static void follow(Entity carried, Player carrier) {
		carried.setPos(carrier.getX(), carrier.getY(), carrier.getZ());
		carried.setOldPosAndRot(new Vec3(carrier.xo, carrier.yo, carrier.zo), carried.getYRot(), carried.getXRot());
		carried.setDeltaMovement(Vec3.ZERO);
		carried.resetFallDistance();
		if (carried instanceof LivingEntity living && !(carried instanceof Player)) {
			living.yBodyRot = carrier.yBodyRot;
			living.yBodyRotO = carrier.yBodyRotO;
		} else if (!(carried instanceof LivingEntity)) {
			// a carried block turns with the carrier's body, held out in front
			carried.setOldPosAndRot(new Vec3(carrier.xo, carrier.yo, carrier.zo), carrier.yBodyRotO, 0.0F);
			carried.setYRot(carrier.yBodyRot);
			carried.setXRot(0.0F);
		}
	}

	/** Steps the loose limbs' swing from how the carrier moves and turns. */
	private static void sway(Entity carried, Player carrier) {
		float[] s = SWAY.computeIfAbsent(carried.getId(), id -> new float[] {0, 0, 0, 0, 0, 0, 0, 0, carrier.yBodyRot, 0, 0});
		s[4] = s[0];
		s[5] = s[1];
		s[10] = s[9];
		double vx = carrier.getX() - carrier.xo, vz = carrier.getZ() - carrier.zo, vy = carrier.getY() - carrier.yo;
		float ax = (float)vx - s[6], az = (float)vz - s[7];
		s[6] = (float)vx;
		s[7] = (float)vz;
		float yaw = carrier.yBodyRot * Mth.DEG_TO_RAD;
		float forwardX = -Mth.sin(yaw), forwardZ = Mth.cos(yaw);
		float rightX = -Mth.cos(yaw), rightZ = -Mth.sin(yaw);
		float ahead = ax * forwardX + az * forwardZ;
		float right = ax * rightX + az * rightZ;
		float speedAhead = (float)(vx * forwardX + vz * forwardZ);
		float speedRight = (float)(vx * rightX + vz * rightZ);
		float turn = Mth.wrapDegrees(carrier.yBodyRot - s[8]);
		s[8] = carrier.yBodyRot;
		float stepBack = 0.0F, stepSide = 0.0F;
		if (carrier.onGround()) {
			float phase = carrier.walkAnimation.position() * 0.6662F;
			float stride = Math.min(1.2F, carrier.walkAnimation.speed());
			stepBack = Mth.sin(phase * 2.0F) * SWAY_STEP * stride;
			stepSide = Mth.sin(phase) * SWAY_STEP * 0.7F * stride;
		}
		// back: toward the carrier's back; left: toward their left. They trail behind the motion, lag against every
		// change of pace, and swing out of a turn
		float trailBack = Mth.clamp(speedAhead * SWAY_TRAIL, -SWAY_MAX, SWAY_MAX);
		float trailLeft = Mth.clamp(-speedRight * SWAY_TRAIL, -SWAY_MAX, SWAY_MAX);
		s[2] += -SWAY_STIFFNESS * (s[0] - trailBack) + ahead * SWAY_SHOVE + stepBack;
		s[3] += -SWAY_STIFFNESS * (s[1] - trailLeft) - right * SWAY_SHOVE + turn * SWAY_TURN + stepSide;
		s[2] *= SWAY_DAMPING;
		s[3] *= SWAY_DAMPING;
		s[0] = Mth.clamp(s[0] + s[2], -SWAY_MAX, SWAY_MAX);
		s[1] = Mth.clamp(s[1] + s[3], -SWAY_MAX, SWAY_MAX);
		float speed = (float)Math.sqrt(vx * vx + vy * vy + vz * vz);
		float trail = Carry.flying(carrier) ? Mth.clamp(speed / TRAIL_FULL_SPEED, 0.0F, 1.0F) : 0.0F;
		s[9] += (trail - s[9]) * 0.2F;
	}

	/** This frame's swing of a carried body's limbs: {back, left, trail}, or zeros. */
	static float[] swayOf(Entity carried, float partialTicks) {
		float[] s = SWAY.get(carried.getId());
		if (s == null) {
			return new float[3];
		}
		return new float[] {Mth.lerp(partialTicks, s[4], s[0]), Mth.lerp(partialTicks, s[5], s[1]), Mth.lerp(partialTicks, s[10], s[9])};
	}
}
