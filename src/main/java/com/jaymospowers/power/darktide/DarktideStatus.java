package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Wounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Darktide's own hold on bodies (Chronokinesis' stun is its own and stays untouched):
 * <ul>
 * <li><b>Stun</b>: a stunned mob thinks nothing ({@code DarktideMobStunMixin}); a stunned player's own client takes their
 * movement keys away. Synced to everyone.</li>
 * <li><b>Crippled</b>: a humanoid hit in the legs (the aim point in the lower {@link #CRIPPLE_LOW_SHARE} of its hitbox) drops
 * into a crawl (the vanilla swimming pose on land) for {@link #CRIPPLE_TICKS}: ×{@link #CRIPPLE_SPEED} movement, no jumping.
 * Synced so everyone sees the crawl. (0.14.2) Anything with a person's body in Wounds is wounded in that leg instead
 * ({@link #LEG_WOUND} of it, {@code Wounds.hurtPart}): two strikes, or one on a leg already hurt, break it, and the break
 * is Wounds' (a limp; both legs, a crawl) until it's set. The timed crawl is left for bodies Wounds doesn't part.</li>
 * <li><b>Reel</b>: a short reeling-back move on the body, plus a shove.</li>
 * </ul>
 */
public final class DarktideStatus {
	/** The creatures that reel and stagger like people (players, villagers, illagers, piglins, zombies, skeletons). */
	public static final TagKey<EntityType<?>> HUMANOID = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("humanoid"));

	/** Crippled: how long (ticks), what share of the hitbox from the feet up counts as "the legs", speed and jump left. */
	public static final int CRIPPLE_TICKS = 100;
	public static final double CRIPPLE_LOW_SHARE = 0.4;
	public static final double CRIPPLE_SPEED = 0.3;
	/** (0.14.2) A leg strike on a person: this share of the leg's health, on top of the blow (Wounds). */
	public static final float LEG_WOUND = 0.5F;
	/** How far a creature reeling back is shoved. */
	static final double REEL_PUSH = 0.45;

	private static final Identifier CRIPPLE_SPEED_ID = JaymosPowers.id("darktide_cripple_speed");
	private static final Identifier CRIPPLE_JUMP_ID = JaymosPowers.id("darktide_cripple_jump");

	private static final List<LivingEntity> STUNNED = new ArrayList<>();
	private static final List<LivingEntity> CRIPPLED = new ArrayList<>();

	private DarktideStatus() {
	}

	public static boolean humanoid(LivingEntity entity) {
		return entity instanceof Player || entity.getType().builtInRegistryHolder().is(HUMANOID);
	}

	// ---- stun -------------------------------------------------------------------------------------------------------

	public static boolean stunned(LivingEntity entity) {
		Long until = entity.getAttached(DarktideState.STUN);
		return until != null && entity.level().getGameTime() < until;
	}

	/** Stuns {@code target} for {@code ticks} (or keeps a longer stun). */
	public static void stun(LivingEntity target, int ticks) {
		long until = target.level().getGameTime() + ticks;
		Long now = target.getAttached(DarktideState.STUN);
		if (now != null && now >= until) {
			return;
		}
		if (now == null) {
			STUNNED.add(target);
		}
		target.setAttached(DarktideState.STUN, until);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	// ---- cripple ----------------------------------------------------------------------------------------------------

	public static boolean crippled(LivingEntity entity) {
		Long until = entity.getAttached(DarktideState.CRIPPLE);
		return until != null && entity.level().getGameTime() < until;
	}

	/** (0.14.2) Crippled either way: the timed crawl, or a broken (or lost) leg in Wounds. Server side, for Darktide's own moves. */
	static boolean hobbled(LivingEntity entity) {
		return crippled(entity) || Wounds.isBroken(entity, BodyPart.LEFT_LEG) || Wounds.isBroken(entity, BodyPart.RIGHT_LEG)
			|| (Dismember.missing(entity) & (Dismember.LEFT_LEG | Dismember.RIGHT_LEG)) != 0;
	}

	/**
	 * A Darktide hit by {@code attacker} landed on {@code target}: if it's a humanoid and the attacker's aim meets it low
	 * (the look ray against its hitbox; without a ray through it, the height the ray passes at its distance), it's crippled.
	 */
	static void legHit(Player attacker, LivingEntity target) {
		if (!humanoid(target) || !target.isAlive()) {
			return;
		}
		AABB box = target.getBoundingBox();
		Vec3 eye = attacker.getEyePosition();
		Vec3 look = attacker.getLookAngle();
		double y;
		Optional<Vec3> hit = box.inflate(0.05).clip(eye, eye.add(look.scale(16.0)));
		if (hit.isPresent()) {
			y = hit.get().y;
		} else {
			double flat = Math.max(0.01, Math.sqrt(look.x * look.x + look.z * look.z));
			double dist = Math.sqrt(Math.pow(target.getX() - eye.x, 2) + Math.pow(target.getZ() - eye.z, 2));
			y = eye.y + look.y / flat * dist;
		}
		if (y <= box.minY + box.getYsize() * CRIPPLE_LOW_SHARE) {
			cripple(target, attacker);
		}
	}

	/**
	 * (0.14.2) {@code target}'s legs are struck by {@code attacker}: a person's leg (the side aimed at) is wounded in Wounds,
	 * {@link #LEG_WOUND} of it, and breaks at zero; anything else is crippled for {@link #CRIPPLE_TICKS} as before.
	 */
	static void cripple(LivingEntity target, Player attacker) {
		BodyPlan plan = Wounds.plan(target);
		if (plan == BodyPlan.HUMANOID) {
			int aimed = Dismember.aimedPart(attacker, target);
			BodyPart leg = aimed == Dismember.RIGHT_LEG ? BodyPart.RIGHT_LEG : aimed == Dismember.LEFT_LEG ? BodyPart.LEFT_LEG
				: target.getRandom().nextBoolean() ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
			int index = plan.index(leg);
			Wounds.hurtPart(target, leg, LEG_WOUND * Wounds.cap(target, plan, index), attacker);
			return;
		}
		long until = target.level().getGameTime() + CRIPPLE_TICKS;
		if (target.getAttached(DarktideState.CRIPPLE) == null) {
			CRIPPLED.add(target);
		}
		target.setAttached(DarktideState.CRIPPLE, until);
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(CRIPPLE_SPEED_ID, CRIPPLE_SPEED - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.addOrUpdateTransientModifier(new AttributeModifier(CRIPPLE_JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		target.setSprinting(false);
		if (target instanceof Player) {
			// players crawl in the vanilla pose (DarktidePlayerPoseMixin); mobs have no crawl of their own (their swim
			// pose is arms flung out, standing), so clients lay them on the ground instead (DarktidePose.crawl)
			target.setPose(Pose.SWIMMING);
		}
	}

	private static void uncripple(LivingEntity target) {
		target.removeAttached(DarktideState.CRIPPLE);
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(CRIPPLE_SPEED_ID);
		}
		AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(CRIPPLE_JUMP_ID);
		}
		if (!(target instanceof Player) && target.hasPose(Pose.SWIMMING) && !target.isInWater()) {
			target.setPose(Pose.STANDING);
		}
	}

	// ---- reel -------------------------------------------------------------------------------------------------------

	/** {@code target} reels back from {@code from}: the move, and a shove. */
	static void reel(LivingEntity target, Vec3 from) {
		DarktideMove.play(target, DarktideMove.REEL, DarktideMove.REEL_TICKS, 0);
		Vec3 away = target.position().subtract(from).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() > 1.0E-4) {
			away = away.normalize().scale(REEL_PUSH);
			target.setDeltaMovement(target.getDeltaMovement().add(away.x, 0.1, away.z));
			target.syncVelocity = true;
		}
	}

	static void tick(MinecraftServer server) {
		for (Iterator<LivingEntity> it = STUNNED.iterator(); it.hasNext(); ) {
			LivingEntity entity = it.next();
			Long until = entity.getAttached(DarktideState.STUN);
			if (until == null || entity.isRemoved() || !entity.isAlive() || entity.level().getGameTime() >= until) {
				it.remove();
				entity.removeAttached(DarktideState.STUN);
			}
		}
		for (Iterator<LivingEntity> it = CRIPPLED.iterator(); it.hasNext(); ) {
			LivingEntity entity = it.next();
			Long until = entity.getAttached(DarktideState.CRIPPLE);
			if (until == null || entity.isRemoved() || !entity.isAlive() || entity.level().getGameTime() >= until) {
				it.remove();
				uncripple(entity);
				continue;
			}
			// a mob's pose is never refreshed on its own: hold it in the crawl (players: DarktidePlayerPoseMixin)
			if (!(entity instanceof Player) && !entity.hasPose(Pose.SWIMMING)) {
				entity.setPose(Pose.SWIMMING);
			}
			entity.setSprinting(false);
		}
	}

	static void clear() {
		STUNNED.clear();
		CRIPPLED.clear();
	}
}
