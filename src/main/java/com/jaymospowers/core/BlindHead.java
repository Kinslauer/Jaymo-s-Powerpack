package com.jaymospowers.core;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Headless undead go blind (Andy's pick, from the idea of the PPs dismemberment mod; written from the description alone).
 * A zombie or skeleton that fights on without its head ({@link Dismember#HEADLESS_UNDEAD}) can no longer see: it picks no
 * target and tracks nobody. It hears instead. Footsteps and the like carry {@link #HEAR_QUIET} blocks, louder things
 * (a blow landing, a shot, digging, a blast) {@link #HEAR_LOUD}; it turns toward the last noise it heard, for
 * {@link #MEMORY} ticks, and shambles to where it came from swinging. Anything that bumps into it is swung at too (other
 * headless undead excepted). A blind skeleton with a bow stays put and looses an arrow every {@link #ARROW_EVERY} ticks at
 * the spot the noise came from, thrown off by up to {@link Dismember#BLIND_SPREAD}: never at where the player is now.
 */
public final class BlindHead {
	/** How far (blocks) a footstep or similar quiet sound is heard from, and a loud one. */
	public static final double HEAR_QUIET = 8.0, HEAR_LOUD = 16.0, HEAR_BLAST = 32.0;
	/** How long (ticks) it remembers the last noise it heard. */
	public static final int MEMORY = 100;
	/** It swings (and hurts what it reaches) this often (ticks), within this reach (blocks) of its body. */
	public static final int SWING_EVERY = 12;
	public static final double SWING_REACH = 2.0;
	/** Things this close to its body (blocks beyond its box) have bumped into it. */
	public static final double BUMP_MARGIN = 0.15;
	/** It stops walking this near (blocks) the noise's spot, and stays swinging at it. */
	public static final double ARRIVED = 1.4;
	/** A blind archer looses an arrow this often (ticks). */
	public static final int ARROW_EVERY = 50;

	private record Noise(Vec3 at, long until) {
	}

	/** Headless undead on the loose, to be told what is heard. */
	private static final Set<Mob> BLIND = Collections.newSetFromMap(new WeakHashMap<>());
	/** What each last heard. */
	private static final Map<Mob, Noise> HEARD = new WeakHashMap<>();
	/** Where the arrow being loosed is meant to go (set only around the shot). */
	private static @Nullable Vec3 aiming;

	private BlindHead() {
	}

	/** Whether {@code mob} is a headless undead that has lost its sight. */
	public static boolean blind(Mob mob) {
		return Dismember.headless(mob) && mob.getType().builtInRegistryHolder().is(Dismember.HEADLESS_UNDEAD);
	}

	/** {@code mob} has just lost its head, or come back from a reload without it. */
	public static void track(Mob mob) {
		if (blind(mob)) {
			BLIND.add(mob);
			mob.setTarget(null);
		}
	}

	private static double reach(Holder<GameEvent> event) {
		if (event.is(GameEvent.EXPLODE)) {
			return HEAR_BLAST;
		}
		if (event.is(GameEvent.STEP) || event.is(GameEvent.SWIM) || event.is(GameEvent.HIT_GROUND) || event.is(GameEvent.SPLASH)
			|| event.is(GameEvent.EAT) || event.is(GameEvent.DRINK)) {
			return HEAR_QUIET;
		}
		if (event.is(GameEvent.ENTITY_DAMAGE) || event.is(GameEvent.ENTITY_DIE) || event.is(GameEvent.PROJECTILE_SHOOT)
			|| event.is(GameEvent.BLOCK_DESTROY) || event.is(GameEvent.BLOCK_PLACE) || event.is(GameEvent.BLOCK_ACTIVATE)
			|| event.is(GameEvent.BLOCK_OPEN) || event.is(GameEvent.BLOCK_CLOSE)) {
			return HEAR_LOUD;
		}
		return 0.0;
	}

	/** A game event (the ones sculk sensors feel) went off at {@code at}: the headless undead that can hear it turn to it. */
	public static void heard(ServerLevel level, Holder<GameEvent> event, Vec3 at, GameEvent.@Nullable Context context) {
		if (BLIND.isEmpty()) {
			return;
		}
		double reach = reach(event);
		if (reach <= 0.0) {
			return;
		}
		Entity source = context == null ? null : context.sourceEntity();
		if (source instanceof Projectile projectile && projectile.getOwner() != null) {
			source = projectile.getOwner();
		}
		if (source instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		long until = level.getGameTime() + MEMORY;
		for (Mob mob : BLIND) {
			if (mob == source || mob.level() != level || !mob.isAlive() || mob.distanceToSqr(at) > reach * reach) {
				continue;
			}
			if (source != null && source.getType().builtInRegistryHolder().is(Dismember.HEADLESS_UNDEAD)) {
				// the dead don't startle each other
				continue;
			}
			HEARD.put(mob, new Noise(at, until));
		}
	}

	/**
	 * A blind undead's whole AI step (called in place of it): no target, no sight; it turns and walks to the last noise,
	 * swinging, and swings at whatever it's touching. False if {@code mob} isn't one.
	 */
	public static boolean step(Mob mob) {
		if (!blind(mob) || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		if (BLIND.add(mob)) {
			mob.setTarget(null);
		}
		mob.setTarget(null);
		mob.getNavigation().stop();
		long now = level.getGameTime();
		Noise noise = HEARD.get(mob);
		if (noise != null && now >= noise.until) {
			HEARD.remove(mob);
			noise = null;
		}
		LivingEntity bumped = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(BUMP_MARGIN),
			e -> e != mob && e != mob.getVehicle() && !mob.hasPassenger(e) && e.isAlive() && !e.isSpectator() && !e.getType().builtInRegistryHolder().is(Dismember.HEADLESS_UNDEAD)
				&& !(e instanceof Player p && p.isCreative()))) {
			double d = other.distanceToSqr(mob);
			if (d < best) {
				best = d;
				bumped = other;
			}
		}
		Vec3 at = bumped != null ? bumped.position() : noise != null ? noise.at : null;
		mob.setJumping(false);
		if (at == null) {
			mob.setZza(0.0F);
			mob.setXxa(0.0F);
			return true;
		}
		double dx = at.x - mob.getX(), dz = at.z - mob.getZ();
		float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
		double flat = Math.sqrt(dx * dx + dz * dz);
		boolean archer = mob instanceof AbstractSkeleton && mob.getMainHandItem().is(Items.BOW);
		if (archer && bumped == null) {
			// a blind archer stands where it is and looses arrows at the sound
			mob.setZza(0.0F);
			mob.setXxa(0.0F);
			if (flat > ARRIVED && mob.tickCount % ARROW_EVERY == 0 && mob instanceof RangedAttackMob ranged) {
				aiming = at.add(0.0, 1.0, 0.0);
				try {
					ranged.performRangedAttack(mob, 1.0F);
				} finally {
					aiming = null;
				}
			}
			return true;
		}
		if (flat > ARRIVED && bumped == null) {
			mob.setSpeed((float) mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
			mob.setZza(1.0F);
			mob.setJumping(mob.horizontalCollision && mob.onGround());
		} else {
			mob.setZza(0.0F);
		}
		mob.setXxa(0.0F);
		if (mob.tickCount % SWING_EVERY == 0 && (bumped != null || flat <= SWING_REACH + ARRIVED)) {
			mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
			LivingEntity victim = bumped;
			if (victim == null) {
				// swung at the spot the noise came from: whoever stands there takes it
				Vec3 spot = at;
				for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(SWING_REACH),
					e -> e != mob && e.isAlive() && !e.isSpectator() && !e.getType().builtInRegistryHolder().is(Dismember.HEADLESS_UNDEAD)
						&& !(e instanceof Player p && p.isCreative()) && e.position().distanceToSqr(spot) < 2.25)) {
					victim = other;
					break;
				}
			}
			if (victim != null) {
				mob.doHurtTarget(level, victim);
			}
		}
		return true;
	}

	/**
	 * A projectile a headless archer has just loosed ({@link #step}): turned toward the spot of the noise it heard, not at
	 * where anyone is. The caller scatters it after. True if it was.
	 */
	static @Nullable Vec3 aim(Projectile projectile, Vec3 velocity) {
		Vec3 spot = aiming;
		if (spot == null) {
			return null;
		}
		Vec3 to = spot.subtract(projectile.position());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		// a little loft for the drop, as an archer allows
		Vec3 dir = new Vec3(to.x, to.y + flat * 0.2, to.z);
		return dir.lengthSqr() < 1.0E-4 ? null : dir.normalize().scale(velocity.length());
	}
}
