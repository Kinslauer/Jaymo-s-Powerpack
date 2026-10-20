package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.horse.CartEntity;
import com.jaymoskitchen.ragdoll.LevelRagdollWorld;
import com.jaymoskitchen.ragdoll.LivingRagdolls;
import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollNet;
import com.jaymoskitchen.ragdoll.RagdollRig;
import com.jaymoskitchen.ragdoll.RagdollShapes;
import com.jaymoskitchen.ragdoll.RagdollSnapshot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.0) The local player going limp: a crash, a hard landing, a blast or big shove, a boat, cart or charging rider
 * running them over, or the server's say-so. Movement is the client's, so the ragdoll is simulated here: the player
 * is moved after the body each tick (so the server still sees the fall and the crash), the pose goes out to everyone
 * (see {@link LivingRagdolls}), and the camera pulls back to third person until they're up again.
 */
public final class LimpController {
	/** Crash: going at least this fast (blocks a tick) at the start of a tick and losing this much of it against a wall. */
	private static final double CRASH_SPEED = 0.6, CRASH_LOSS = 0.45;
	/** Landing: falling at least this fast (blocks a tick) as you hit the ground. */
	private static final double LAND_SPEED = 1.0;
	/** Blast or shove: the velocity jumped by this much (blocks a tick) between ticks. */
	private static final double KICK_SPEED = 0.9;
	/** Run over: a vehicle or ridden mob moving at least this fast (blocks a tick) toward you, flinging you its speed times this, and up. */
	private static final double RUN_OVER_SPEED = 0.35, RUN_OVER_FLING = 1.3, RUN_OVER_LIFT = 0.25;
	/** How far past your box a runner-over counts. */
	private static final double RUN_OVER_REACH = 0.1;
	/** Ticks limp at the least and the most, and how still (blocks a tick, for how many ticks) counts as stopped. */
	private static final int MIN_LIMP = 30, MAX_LIMP = 140;
	private static final double STILL_SPEED = 0.02;
	private static final int STILL_TICKS = 10;
	/** After getting up, no going limp again for this many ticks. */
	private static final int GETUP_COOLDOWN = 40;
	/** The player's standing height (blocks, before their scale). */
	private static final float STANDING_HEIGHT = 1.8F;
	/** The player's feet hang this far below the body's middle while limp (their box is 0.6 tall). */
	private static final double MIDDLE_TO_FEET = 0.3;
	/** The most the player is moved toward the body in a tick (blocks). */
	private static final double MAX_FOLLOW = 4.0;
	/** A topple: the impact's share the head takes, and the share the rest of the body feels. */
	private static final double TOPPLE = 0.35;
	private static final float TOPPLE_SHARE = 0.15F;
	/** How long (ticks) before the server's word that they're not limp (a power took hold) counts. */
	private static final int SYNC_GRACE = 10;

	private static @Nullable LocalPlayer owner;
	private static @Nullable ClientLevel level;
	private static @Nullable RagdollBody body;
	private static int ticks, still, cooldown;
	private static @Nullable CameraType restoreCamera;
	/** The velocity at the start of this tick, and at the end of the last. */
	private static Vec3 startVelocity = Vec3.ZERO, lastEndVelocity = Vec3.ZERO;
	private static boolean startOnGround;
	/** A shove seen at the start of this tick, and a knock the server sent. */
	private static @Nullable Vec3 kick, knock;

	private LimpController() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(LimpController::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(LimpController::endTick);
		ClientPlayNetworking.registerGlobalReceiver(RagdollNet.Knock.TYPE,
			(payload, ctx) -> knock = new Vec3(payload.vx(), payload.vy(), payload.vz()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(client, false));
		LivingRagdolls.setLocal(LimpController::isLocalLimp);
	}

	/** Whether the local player is lying limp right now (for the mixins that cancel their input). */
	public static boolean active() {
		return body != null;
	}

	private static boolean isLocalLimp(Player player) {
		return body != null && player == owner;
	}

	// ---------------------------------------------------------------- ticking

	private static void startTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		startVelocity = player.getDeltaMovement();
		startOnGround = player.onGround();
		Vec3 jump = startVelocity.subtract(lastEndVelocity);
		kick = jump.length() >= KICK_SPEED ? jump : null;
		if (body != null && kick != null && player == owner) {
			// a blast or shove while down throws the body too
			body.shove(kick.x, kick.y, kick.z);
			kick = null;
		}
	}

	private static void endTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			reset(minecraft, false);
			return;
		}
		if (body != null) {
			if (player != owner || minecraft.level != level || !player.isAlive() || player.isRemoved()) {
				// died: the server already knows; respawned or changed level: let it know we're up (the new player object
				// after a portal isn't the owner, so it can't be told apart by that)
				finish(minecraft, player.isAlive());
			} else {
				limpTick(minecraft, player);
			}
		} else {
			if (cooldown > 0) {
				cooldown--;
			}
			check(minecraft, player);
		}
		forgetOthers(minecraft);
		lastEndVelocity = player.getDeltaMovement();
		kick = null;
	}

	/** Players who are no longer limp stop being drawn from their last pose. */
	private static void forgetOthers(Minecraft minecraft) {
		if (minecraft.level == null) {
			return;
		}
		for (AbstractClientPlayer other : minecraft.level.players()) {
			if (other != minecraft.player && !LivingRagdolls.isLimp(other) && ClientRagdolls.get(other.getId()) != null) {
				ClientRagdolls.forget(other.getId());
			}
		}
	}

	// ---------------------------------------------------------------- triggers

	/** Alive, in the game and held by nothing: someone who can go limp at all. */
	private static boolean able(LocalPlayer player) {
		return !player.isCreative() && !player.isSpectator() && !player.isPassenger() && !player.isSleeping() && player.isAlive()
			&& !LivingRagdolls.vetoed(player);
	}

	private static void check(Minecraft minecraft, LocalPlayer player) {
		Vec3 sent = knock;
		knock = null;
		if (!able(player)) {
			return;
		}
		if (sent != null) {
			begin(minecraft, player, sent);
			return;
		}
		if (cooldown > 0 || player.isInWater() || player.isSwimming()) {
			return;
		}
		Vec3 end = player.getDeltaMovement();
		double startFlat = Math.hypot(startVelocity.x, startVelocity.z), endFlat = Math.hypot(end.x, end.z);
		if (startFlat >= CRASH_SPEED && startFlat - endFlat >= CRASH_LOSS && player.horizontalCollision) {
			begin(minecraft, player, new Vec3(startVelocity.x, startVelocity.y * 0.5, startVelocity.z));
		} else if (player.onGround() && !startOnGround && startVelocity.y <= -LAND_SPEED) {
			begin(minecraft, player, new Vec3(startVelocity.x, 0.0, startVelocity.z));
		} else if (kick != null) {
			begin(minecraft, player, kick);
		} else {
			Vec3 run = runOver(player, end);
			if (run != null) {
				begin(minecraft, player, run);
			}
		}
	}

	/** The fling from a boat, minecart, cart or ridden mob running into the player, if one is. */
	private static @Nullable Vec3 runOver(LocalPlayer player, Vec3 own) {
		AABB area = player.getBoundingBox().inflate(RUN_OVER_REACH);
		for (Entity entity : player.level().getEntities(player, area, e -> runs(e, player))) {
			Vec3 v = new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
			Vec3 rel = v.subtract(own);
			if (Math.hypot(rel.x, rel.z) < RUN_OVER_SPEED) {
				continue;
			}
			// toward the player, not away
			double dx = player.getX() - entity.getX(), dz = player.getZ() - entity.getZ();
			if (rel.x * dx + rel.z * dz <= 0.0) {
				continue;
			}
			return new Vec3(v.x * RUN_OVER_FLING, RUN_OVER_LIFT, v.z * RUN_OVER_FLING);
		}
		return null;
	}

	private static boolean runs(Entity entity, LocalPlayer player) {
		if (!entity.isAlive() || entity.isSpectator() || entity == player.getVehicle() || entity.hasPassenger(player)) {
			return false;
		}
		return entity instanceof AbstractBoat || entity instanceof CartEntity
			|| BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().contains("minecart")
			|| entity instanceof LivingEntity && entity.getControllingPassenger() != null;
	}

	// ---------------------------------------------------------------- going limp and getting up

	private static void begin(Minecraft minecraft, LocalPlayer player, Vec3 fling) {
		float height = STANDING_HEIGHT * player.getScale();
		RagdollRig rig = RagdollShapes.rig(EntityTypes.PLAYER, player.getBbWidth(), height);
		RagdollBody made;
		if (player.getPose() == Pose.SWIMMING) {
			made = RagdollBody.lying(rig, player.getX(), player.getY(), player.getZ(), player.yBodyRot, false, player.tickCount);
		} else {
			made = RagdollBody.standing(rig, player.getX(), player.getY(), player.getZ(), player.yBodyRot);
		}
		Vec3 now = player.getDeltaMovement();
		made.setSpeed(now.x, now.y, now.z);
		made.shove(fling.x, fling.y, fling.z);
		// a blow to the head along the impact: it topples that way
		int head = RagdollRig.HEAD;
		made.hit(made.x[head], made.y[head], made.z[head], fling.x * TOPPLE, 0.0, fling.z * TOPPLE, TOPPLE_SHARE);
		body = made;
		owner = player;
		level = minecraft.level;
		ticks = 0;
		still = 0;
		player.setSprinting(false);
		player.setPose(Pose.SWIMMING);
		if (minecraft.options.getCameraType().isFirstPerson()) {
			restoreCamera = minecraft.options.getCameraType();
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		ClientPlayNetworking.send(new RagdollNet.Limp(true));
		publish(player);
	}

	private static void limpTick(Minecraft minecraft, LocalPlayer player) {
		RagdollBody b = body;
		ticks++;
		b.step(new LevelRagdollWorld(player.level(), player));
		if (!b.sane()) {
			finish(minecraft, true);
			return;
		}
		double[] m = b.middle();
		Vec3 delta = new Vec3(m[0] - player.getX(), m[1] - MIDDLE_TO_FEET - player.getY(), m[2] - player.getZ());
		if (delta.length() > MAX_FOLLOW) {
			delta = delta.scale(MAX_FOLLOW / delta.length());
		}
		player.move(MoverType.SELF, delta);
		double[] v = b.speed();
		player.setDeltaMovement(v[0], v[1], v[2]);
		player.setPose(Pose.SWIMMING);
		publish(player);

		double fastest = 0.0;
		for (int i = 0; i < b.rig.count; i++) {
			fastest = Math.max(fastest, Math.sqrt(sq(b.x[i] - b.px[i]) + sq(b.y[i] - b.py[i]) + sq(b.z[i] - b.pz[i])));
		}
		still = fastest < STILL_SPEED ? still + 1 : 0;
		boolean settled = b.asleep() || still >= STILL_TICKS;
		// the server lets go of anyone a power takes hold of (see LivingRagdolls.addVeto)
		boolean freed = ticks > SYNC_GRACE && player.getAttached(LivingRagdolls.LIMP) == null;
		if (ticks >= MAX_LIMP || ticks >= MIN_LIMP && settled || player.isUnderWater() || freed) {
			finish(minecraft, true);
		}
	}

	private static double sq(double d) {
		return d * d;
	}

	private static void publish(LocalPlayer player) {
		ClientRagdolls.put(player.getId(), body);
		ClientPlayNetworking.send(new RagdollNet.LimpPose(RagdollSnapshot.of(body)));
	}

	/** Gets back up, and can't be knocked down again for a moment. */
	private static void finish(Minecraft minecraft, boolean tell) {
		reset(minecraft, tell);
		cooldown = GETUP_COOLDOWN;
	}

	private static void reset(@Nullable Minecraft minecraft, boolean tell) {
		if (body != null && tell) {
			ClientPlayNetworking.send(new RagdollNet.Limp(false));
		}
		if (restoreCamera != null && minecraft != null && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
			minecraft.options.setCameraType(restoreCamera);
		}
		restoreCamera = null;
		if (owner != null) {
			ClientRagdolls.forget(owner.getId());
		}
		body = null;
		owner = null;
		level = null;
		ticks = 0;
		still = 0;
		kick = null;
		knock = null;
	}
}
