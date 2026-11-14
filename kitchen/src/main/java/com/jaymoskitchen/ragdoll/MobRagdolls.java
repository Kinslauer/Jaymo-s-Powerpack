package com.jaymoskitchen.ragdoll;

import com.jaymoskitchen.JaymosKitchen;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "our ragdolls should activate upon taking a large amount of damage, or being hit with a large enough blade".
 * Players already went limp (LivingRagdolls); now mobs do too, on the same hits ({@link HeavyHits}). A mob knocked
 * limp is a ragdoll worked out here on the server (as a corpse's is) and sent to everyone watching: it falls where the
 * blow put it, its mind stops (no goals, no walking, no attacking), and once it lies still (or after
 * {@link #MAX_LIMP} ticks) it gets back up where the body lies and goes on. Blows while it's down shove the body. One
 * that dies limp leaves exactly that body as its corpse (Corpses#deathBody).
 * <p>
 * Never: bosses, anything bigger than {@link #MAX_SIZE}, mobs in water or lava, mobs with no mind of their own
 * (statues, NoAI), anything carrying a rider, or one downed or being finished by Jaymo's Powers (that draws its own
 * lying pose). A rider knocked limp falls off its mount.
 */
public final class MobRagdolls {
	/** Ticks limp at the least and the most, and how still (blocks a tick, for how many ticks) counts as stopped (as players). */
	private static final int MIN_LIMP = 30, MAX_LIMP = 140;
	private static final double STILL_SPEED = 0.02;
	private static final int STILL_TICKS = 10;
	/** After getting up, no going limp again for this many ticks. */
	private static final int GETUP_COOLDOWN = 40;
	/** The blow's own push away from where it came from, and its lift (blocks a tick): only a topple (as players). */
	private static final double HIT_FLING = 0.1, HIT_LIFT = 0.04;
	/** This share of the speed the blow gave it (its knockback) carries the body on. */
	private static final double KNOCK_CARRY = 0.7;
	/** The most a shove while it's down lifts the body (blocks a tick). */
	private static final double SHOVE_LIFT = 0.1;
	/** A topple: the share of the fling the head takes, and the share the rest of the body feels. */
	private static final double TOPPLE = 0.35;
	private static final float TOPPLE_SHARE = 0.15F;
	/** A body bigger than this (blocks, standing, either way) is never knocked limp: a ghast, a dragon. */
	private static final float MAX_SIZE = 4.0F;
	/** Differences smaller than this (blocks a tick) are the game's own noise, not a shove. */
	private static final double SHOVE_EPSILON = 1.0E-4;
	/** Moved further than this by something else at once (blocks: a teleport, a power's yank): it's let up there. */
	private static final double LET_GO_DISTANCE = 4.0;
	/** Lying still, the points are sent again this often (ticks), for anyone who has just come into range. */
	private static final int RESEND_EVERY = 40;
	/** Jaymo's Powers' tags on a downed body, and one being finished (Downed.DOWNED_TAG, FINISHING_TAG). */
	private static final String DOWNED_TAG = "jaymospowers_downed", FINISHING_TAG = "jaymospowers.finishing";

	/** On a mob lying limp (synced to everyone, not saved): everyone draws it as the ragdoll sent for it. */
	public static final AttachmentType<Boolean> LIMP = AttachmentRegistry.create(JaymosKitchen.id("limp_mob"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL.cast(), AttachmentSyncPredicate.all()));

	private static final class Limp {
		RagdollBody body;
		final LevelRagdollWorld world;
		int ticks, still, sinceSent;
		boolean wasAsleep;
		Vec3 wrotePos = Vec3.ZERO, wroteVel = Vec3.ZERO;

		Limp(Mob mob, RagdollBody body) {
			this.body = body;
			this.world = new LevelRagdollWorld(mob.level(), mob);
		}
	}

	private static final Map<Mob, Limp> LIMPS = new WeakHashMap<>();
	/** The game tick each mob that got up may be knocked limp again from. */
	private static final Map<Mob, Long> READY_AT = new WeakHashMap<>();

	private MobRagdolls() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof Mob mob) || blocked || damageTaken <= 0.0F || !mob.isAlive()) {
				return;
			}
			if (isLimp(mob) || !HeavyHits.heavy(mob, source, baseDamage, damageTaken) || !may(mob)) {
				return;
			}
			knock(mob, source);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Map.Entry<Mob, Limp>> it = LIMPS.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Mob, Limp> e = it.next();
				Mob mob = e.getKey();
				if (mob.isRemoved() || !mob.isAlive()) {
					// dead: its corpse took the body already (Corpses#deathBody)
					mob.removeAttached(LIMP);
					it.remove();
				} else if (!tick(mob, e.getValue())) {
					getUp(mob, e.getValue());
					it.remove();
				}
			}
		});
	}

	/** True while {@code entity} is a mob lying limp (on either side). */
	public static boolean isLimp(Entity entity) {
		return entity instanceof Mob && entity.getAttached(LIMP) != null;
	}

	/** The body {@code mob} lies in now if it's limp (a copy, moving as it moves), else null: what its corpse starts as. */
	public static @Nullable RagdollBody body(Entity mob) {
		Limp limp = mob instanceof Mob m ? LIMPS.get(m) : null;
		if (limp == null) {
			return null;
		}
		RagdollBody copy = limp.body.copy();
		// the killing blow's knockback, not yet taken in
		Vec3 pushed = mob.getDeltaMovement().subtract(limp.wroteVel);
		if (pushed.lengthSqr() > SHOVE_EPSILON * SHOVE_EPSILON) {
			takeIn(copy, pushed);
		}
		return copy;
	}

	/** Whether {@code mob} can be knocked limp at all just now. */
	private static boolean may(Mob mob) {
		if (mob.isNoAi() || mob.isVehicle() || mob.isInWater() || mob.isInLava() || mob.isSpectator()) {
			return false;
		}
		if (mob.getType().builtInRegistryHolder().is(ConventionalEntityTypeTags.BOSSES)) {
			return false;
		}
		if (mob.entityTags().contains(DOWNED_TAG) || mob.entityTags().contains(FINISHING_TAG)) {
			return false;
		}
		EntityDimensions size = mob.getDimensions(Pose.STANDING);
		if (size.width() > MAX_SIZE || size.height() > MAX_SIZE) {
			return false;
		}
		Long ready = READY_AT.get(mob);
		return ready == null || mob.level().getGameTime() >= ready;
	}

	/** Knocks {@code mob} limp: standing as it stood, toppled away from the blow, carried on by its knockback. */
	private static void knock(Mob mob, DamageSource source) {
		if (mob.isPassenger()) {
			mob.stopRiding();
		}
		EntityDimensions size = mob.getDimensions(Pose.STANDING);
		RagdollRig rig = RagdollShapes.rig(mob.getType(), size.width(), size.height());
		RagdollBody body = RagdollBody.standing(rig, mob.getX(), mob.getY(), mob.getZ(), mob.yBodyRot);
		Vec3 from = source.getSourcePosition();
		double dx = from == null ? 0.0 : mob.getX() - from.x, dz = from == null ? 0.0 : mob.getZ() - from.z;
		double flat = Math.hypot(dx, dz);
		double fx = 0.0, fz = 0.0;
		if (flat >= 1.0E-4) {
			fx = dx / flat * HIT_FLING;
			fz = dz / flat * HIT_FLING;
		} else {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
			fx = Math.cos(angle) * HIT_FLING;
			fz = Math.sin(angle) * HIT_FLING;
		}
		Vec3 v = mob.getDeltaMovement();
		double vx = fx + v.x * KNOCK_CARRY, vy = HIT_LIFT + Math.max(0.0, Math.min(v.y, HIT_LIFT)), vz = fz + v.z * KNOCK_CARRY;
		body.setSpeed(vx, vy, vz);
		int head = switch (rig.shape) {
			case HUMANOID -> RagdollRig.HEAD;
			case QUADRUPED -> RagdollRig.SNOUT;
			case BOX, CHAIN -> RagdollRig.TOP_RIGHT;
		};
		body.hit(body.x[head], body.y[head], body.z[head], vx * TOPPLE, 0.0, vz * TOPPLE, TOPPLE_SHARE);
		Limp limp = new Limp(mob, body);
		mob.getNavigation().stop();
		mob.setSprinting(false);
		if (mob.isUsingItem()) {
			mob.stopUsingItem();
		}
		mob.setAttached(LIMP, true);
		LIMPS.put(mob, limp);
		write(mob, limp);
		send(mob, limp);
	}

	/** One tick limp: take in what shoved it, step the body, put the mob where it lies. False once it gets up. */
	private static boolean tick(Mob mob, Limp limp) {
		if (!(mob.level() instanceof ServerLevel)) {
			return false;
		}
		if (mob.entityTags().contains(DOWNED_TAG) || mob.entityTags().contains(FINISHING_TAG) || mob.isInWater() || mob.isInLava()
			|| mob.isPassenger()) {
			// downed (Powers draws it lying its own way), in water, or picked up: let go of the ragdoll
			return false;
		}
		RagdollBody body = limp.body;
		Vec3 moved = mob.position().subtract(limp.wrotePos);
		if (moved.lengthSqr() > LET_GO_DISTANCE * LET_GO_DISTANCE) {
			return false;
		}
		Vec3 pushed = mob.getDeltaMovement().subtract(limp.wroteVel);
		if (pushed.lengthSqr() > SHOVE_EPSILON * SHOVE_EPSILON) {
			// a blow while it's down (its knockback), a blast, a power's push
			takeIn(body, pushed);
		}
		if (moved.lengthSqr() > SHOVE_EPSILON * SHOVE_EPSILON) {
			body.translate(moved.x, moved.y, moved.z);
			body.wake();
		}
		body.step(limp.world, mob.isNoGravity() ? 0.0F : RagdollBody.GRAVITY);
		if (!body.sane()) {
			return false;
		}
		limp.ticks++;
		double fastest = 0.0;
		for (int i = 0; i < body.rig.count; i++) {
			double ddx = body.x[i] - body.px[i], ddy = body.y[i] - body.py[i], ddz = body.z[i] - body.pz[i];
			fastest = Math.max(fastest, Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz));
		}
		limp.still = fastest < STILL_SPEED ? limp.still + 1 : 0;
		boolean asleep = body.asleep();
		write(mob, limp);
		limp.sinceSent++;
		if (!asleep || !limp.wasAsleep || limp.sinceSent >= RESEND_EVERY) {
			send(mob, limp);
		}
		limp.wasAsleep = asleep;
		boolean settled = asleep || limp.still >= STILL_TICKS;
		return limp.ticks < MAX_LIMP && !(limp.ticks >= MIN_LIMP && settled);
	}

	/** Puts the mob where the body lies, at rest (its own walking is held while it's limp: LimpMobMixin). */
	private static void write(Mob mob, Limp limp) {
		double[] m = limp.body.middle();
		mob.setPos(m[0], limp.body.bottom(), m[2]);
		double[] v = limp.body.speed();
		mob.setDeltaMovement(v[0], v[1], v[2]);
		mob.setOnGround(limp.body.grounded());
		mob.fallDistance = 0.0;
		limp.wrotePos = mob.position();
		limp.wroteVel = mob.getDeltaMovement();
	}

	/**
	 * A shove from outside, as a body down takes it: the share {@link #KNOCK_CARRY} of it along the ground, and none of
	 * the hop the game gives every knockback past {@link #SHOVE_LIFT} (Andy: "fall where you stand or a bit further").
	 */
	private static void takeIn(RagdollBody body, Vec3 pushed) {
		body.shove(pushed.x * KNOCK_CARRY, Math.min(pushed.y, SHOVE_LIFT), pushed.z * KNOCK_CARRY);
	}

	private static void send(Mob mob, Limp limp) {
		limp.sinceSent = 0;
		RagdollNet.sendAround(mob, RagdollSnapshot.of(limp.body), null);
	}

	/** Back on its feet where the body lies, facing the way the body does. */
	private static void getUp(Mob mob, Limp limp) {
		mob.removeAttached(LIMP);
		if (mob.isAlive()) {
			double[] m = limp.body.middle();
			float yaw = limp.body.yaw();
			mob.snapTo(m[0], limp.body.bottom(), m[2], yaw, mob.getXRot());
			mob.setYBodyRot(yaw);
			mob.setYHeadRot(yaw);
			mob.setDeltaMovement(Vec3.ZERO);
			READY_AT.put(mob, mob.level().getGameTime() + GETUP_COOLDOWN);
		}
	}
}
