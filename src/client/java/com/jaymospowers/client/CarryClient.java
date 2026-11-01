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
 * <p>
 * (0.19.55) A body dragged by the shoulder ({@link Carry#DRAG}) is laid on the ground behind the carrier's hand instead
 * ({@link Carry#dragStep}, as the server does it), and its legs ragdoll as they drag ({@link Drag}): each one a sprung
 * limb kicked up off the ground by the bumps of the ground going by (more the faster it goes), by the body dropping or
 * rising over a step, and swung aside as the body is turned, falling back flat and bouncing off the ground; the head
 * lolls and jolts with them.
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

	/**
	 * (0.19.55) The dragged legs' ragdoll: the ground's bumps kick them up (radians a tick, per block/tick of speed, on an
	 * uneven beat that many times a block), a drop or rise of the body kicks them (per block), a turn swings them aside (per
	 * degree); they're sprung back flat (lifted) and to lying a little apart (aside), losing this much of their swing a
	 * tick, and bounce off the ground keeping this much. Lifted at most this far, swung aside at most this far.
	 */
	static final float BUMP_KICK = 0.9F, BUMP_BEAT = 7.0F, DROP_KICK = 1.6F, TURN_SWING = 0.012F;
	static final float LEG_STIFFNESS = 0.22F, LEG_ASIDE_STIFFNESS = 0.08F, LEG_DAMPING = 0.82F, GROUND_BOUNCE = 0.3F;
	static final float LEG_LIFT_MAX = 0.7F, LEG_ASIDE_MAX = 0.7F, LEG_SPLAY = 0.14F;
	/** (0.19.55) The head: jolted this much by the bumps (share of a leg's kick), sprung back this hard, and its damping. */
	static final float HEAD_JOLT = 0.5F, HEAD_STIFFNESS = 0.18F, HEAD_DAMPING = 0.8F;

	/**
	 * (0.19.55) One dragged body: where its shoulders and hips lie (and last tick's), its yaw, and its limbs' ragdoll:
	 * {right leg lifted, right leg aside, left lifted, left aside, head} with their speeds, now and last tick.
	 */
	static final class Drag {
		@Nullable Vec3 shoulders, hips;
		Vec3 shouldersO = Vec3.ZERO;
		float yaw, yawO;
		final float[] limbs = new float[5], limbsO = new float[5], speed = new float[5];
		float travelled;
		boolean fresh = true;
	}

	/** Dragged entity id → its drag. */
	private static final Map<Integer, Drag> DRAGS = new HashMap<>();

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
			if (carrying.hold() == Carry.DRAG) {
				drag(carried, carrier);
				continue;
			}
			follow(carried, carrier);
			sway(carried, carrier);
		}
		SWAY.keySet().removeIf(id -> !(level.getEntity(id) instanceof Entity e) || !Carry.isCarried(e));
		DRAGS.keySet().removeIf(id -> !(level.getEntity(id) instanceof Entity e) || !Carry.isDragged(e));
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

	/** (0.19.55) Lays a dragged body along the ground behind the carrier's hand, and steps its legs' ragdoll. */
	private static void drag(Entity carried, Player carrier) {
		Drag d = DRAGS.computeIfAbsent(carried.getId(), id -> new Drag());
		Vec3[] lay = Carry.dragStep(carrier.position(), carrier.yBodyRot, d.shoulders, d.hips);
		d.shouldersO = d.shoulders == null ? lay[0] : d.shoulders;
		d.shoulders = lay[0];
		d.hips = lay[1];
		d.yawO = d.yaw;
		d.yaw = Carry.dragYaw(lay[0], lay[1]);
		if (d.fresh) {
			d.fresh = false;
			d.yawO = d.yaw;
			d.limbs[0] = 0.0F;
			d.limbs[1] = LEG_SPLAY;
			d.limbs[3] = -LEG_SPLAY;
		}
		carried.setPos(lay[0].x, lay[0].y, lay[0].z);
		carried.setOldPosAndRot(d.shouldersO, carried.getYRot(), carried.getXRot());
		carried.setDeltaMovement(Vec3.ZERO);
		carried.resetFallDistance();
		if (carried instanceof LivingEntity living && !(carried instanceof Player)) {
			living.yBodyRot = d.yaw;
			living.yBodyRotO = d.yawO;
			living.setYRot(d.yaw);
		}
		System.arraycopy(d.limbs, 0, d.limbsO, 0, d.limbs.length);
		double dx = lay[0].x - d.shouldersO.x, dz = lay[0].z - d.shouldersO.z;
		float moved = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz));
		float dy = (float) Mth.clamp(lay[0].y - d.shouldersO.y, -1.0, 1.0);
		float turn = Mth.wrapDegrees(d.yaw - d.yawO);
		d.travelled += moved;
		for (int leg = 0; leg < 2; leg++) {
			int lift = leg * 2, aside = lift + 1;
			float side = leg == 0 ? 1.0F : -1.0F;
			// the ground going by: an uneven beat of bumps, each leg on its own
			float beat = d.travelled * BUMP_BEAT + leg * 2.3F;
			float bump = Math.max(0.0F, Mth.sin(beat) * Mth.sin(beat * 2.31F + 1.1F));
			d.speed[lift] -= bump * BUMP_KICK * moved + Math.abs(dy) * DROP_KICK * (dy > 0.0F ? 1.0F : 0.6F);
			d.speed[lift] -= d.limbs[lift] * LEG_STIFFNESS;
			d.speed[aside] += -turn * TURN_SWING - (d.limbs[aside] - side * LEG_SPLAY) * LEG_ASIDE_STIFFNESS;
			d.speed[lift] *= LEG_DAMPING;
			d.speed[aside] *= LEG_DAMPING;
			d.limbs[lift] += d.speed[lift];
			d.limbs[aside] = Mth.clamp(d.limbs[aside] + d.speed[aside], -LEG_ASIDE_MAX, LEG_ASIDE_MAX);
			if (d.limbs[lift] > 0.0F) {
				// back down flat: it bounces off the ground
				d.limbs[lift] = 0.0F;
				d.speed[lift] = -d.speed[lift] * GROUND_BOUNCE;
			} else if (d.limbs[lift] < -LEG_LIFT_MAX) {
				d.limbs[lift] = -LEG_LIFT_MAX;
				d.speed[lift] = 0.0F;
			}
		}
		// the head lolls back, jolted by the same bumps
		float jolt = Math.max(0.0F, Mth.sin(d.travelled * BUMP_BEAT * 1.3F)) * BUMP_KICK * HEAD_JOLT * moved;
		d.speed[4] += jolt - d.limbs[4] * HEAD_STIFFNESS;
		d.speed[4] *= HEAD_DAMPING;
		d.limbs[4] = Mth.clamp(d.limbs[4] + d.speed[4], -0.6F, 0.6F);
	}

	/**
	 * (0.19.55) This frame's drag of a dragged body: {yaw (degrees), right leg lifted, right leg aside, left lifted, left
	 * aside, head}, or null if it isn't being dragged (yet).
	 */
	static float @Nullable [] dragOf(Entity carried, float partialTicks) {
		Drag d = DRAGS.get(carried.getId());
		if (d == null || d.shoulders == null) {
			return null;
		}
		float[] out = new float[6];
		out[0] = Mth.rotLerp(partialTicks, d.yawO, d.yaw);
		for (int i = 0; i < 5; i++) {
			out[i + 1] = Mth.lerp(partialTicks, d.limbsO[i], d.limbs[i]);
		}
		return out;
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
