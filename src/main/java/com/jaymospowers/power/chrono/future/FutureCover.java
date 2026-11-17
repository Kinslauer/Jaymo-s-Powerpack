package com.jaymospowers.power.chrono.future;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Cover for the future's gunners (the armed dead and the echoes, {@link FutureGuns}), Andy's Ragdolls and Cover picks: a gunner
 * that has just shot off a burst, or reloaded, goes down behind a wall or a block for as long as the gun takes to be ready, and
 * stands to lean out and shoot again a moment before; one that is shot at does the same for a short while. Where it stands
 * already is cover if something hides the crouching body from its foe but not the standing one; if not it looks a few blocks
 * round (a few lines cast, no wide scan), and keeps the spot it finds while the foe stays about where it was. The same craft as
 * Kingdoms' {@code RangedTactics} (the two mods don't share code).
 */
final class FutureCover {
	/** A gunner ducks at least this long (ticks) or not at all, and never longer than {@link #LONGEST}; it stands {@link #LEAN_OUT} ticks before its gun is ready. */
	static final int LEAST = 25, LONGEST = 160, LEAN_OUT = 10;
	/** Shot at, it keeps its head down this long (ticks). */
	static final int UNDER_FIRE = 60;
	/** It looks for cover no more than every {@link #COOLDOWN} ticks; a spot found is kept {@link #KEPT} ticks while the foe stays within {@link #MOVED} blocks of where it was. */
	static final int COOLDOWN = 40, KEPT = 200;
	static final double MOVED = 6.0;
	/** How far round it looks for cover (blocks), and how many spots it tries. */
	static final double REACH = 7.0;
	static final int TRIES = 8;
	/** The foe must be further than this (blocks) for a spot to count. */
	static final double FOE_MIN = 5.0;
	static final double SPEED = 1.1;

	private static final class Hide {
		long until, next, coverTick;
		@Nullable Vec3 spot, coverFoe;
		@Nullable BlockPos coverAt;
		boolean ducking;
	}

	private static final Map<Mob, Hide> HIDING = new WeakHashMap<>();

	private FutureCover() {
	}

	/** Its gun will be ready again in {@code ticks}: down behind cover for that, bar the moment it takes to lean out. */
	static void reload(ServerLevel level, Mob mob, @Nullable LivingEntity foe, int ticks) {
		if (foe != null && FutureGuns.held(mob) != null) {
			duck(level, mob, foe, Math.min(ticks - LEAN_OUT, LONGEST));
		}
	}

	/** Shot at (a projectile's hit still showing on it): down for a little. */
	static void underFire(ServerLevel level, Mob mob, LivingEntity foe) {
		if (mob.hurtTime > 0) {
			DamageSource hit = mob.getLastDamageSource();
			if (hit != null && hit.is(DamageTypeTags.IS_PROJECTILE)) {
				duck(level, mob, foe, UNDER_FIRE);
			}
		}
	}

	private static void duck(ServerLevel level, Mob mob, LivingEntity foe, int hold) {
		if (hold < LEAST) {
			return;
		}
		Hide h = HIDING.computeIfAbsent(mob, m -> new Hide());
		long now = level.getGameTime();
		if (now < h.until || now < h.next) {
			return;
		}
		h.next = now + COOLDOWN;
		BlockPos here = mob.blockPosition();
		BlockPos at = works(level, mob, foe, Vec3.atBottomCenterOf(here)) ? here : spot(level, mob, foe, h, now);
		if (at != null) {
			h.spot = Vec3.atBottomCenterOf(at);
			h.until = now + hold;
		}
	}

	/**
	 * Called every tick it fights, before it moves or shoots: true while it is hiding (walking to its spot, or crouched there), so
	 * it leaves its legs and its gun alone; it stands up when the time is out.
	 */
	static boolean hiding(Mob mob, LivingEntity foe) {
		Hide h = HIDING.get(mob);
		if (h == null || h.spot == null) {
			return false;
		}
		if (mob.level().getGameTime() >= h.until || !foe.isAlive()) {
			stand(mob, h);
			return false;
		}
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		Vec3 d = h.spot;
		if (mob.distanceToSqr(d.x, d.y, d.z) > 2.25) {
			if (mob.getNavigation().isDone() || mob.tickCount % 10 == 0) {
				mob.getNavigation().moveTo(d.x, d.y, d.z, SPEED);
			}
		} else {
			mob.getNavigation().stop();
			mob.setShiftKeyDown(true);
			h.ducking = true;
		}
		return true;
	}

	/** The fight is over, or its gun is gone: up on its feet. */
	static void release(Mob mob) {
		Hide h = HIDING.get(mob);
		if (h != null && h.spot != null) {
			stand(mob, h);
		}
	}

	/** It is no longer hiding (leaning out, or dead): up on its feet. */
	private static void stand(Mob mob, Hide h) {
		if (h.ducking) {
			mob.setShiftKeyDown(false);
			h.ducking = false;
		}
		h.spot = null;
	}

	private static @Nullable BlockPos spot(ServerLevel level, Mob mob, LivingEntity foe, Hide h, long now) {
		if (h.coverAt != null && h.coverFoe != null && now - h.coverTick < KEPT && foe.position().distanceToSqr(h.coverFoe) < MOVED * MOVED
			&& works(level, mob, foe, Vec3.atBottomCenterOf(h.coverAt))) {
			return h.coverAt;
		}
		h.coverAt = null;
		var random = mob.getRandom();
		for (int i = 0; i < TRIES && h.coverAt == null; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0, d = 2.0 + random.nextDouble() * (REACH - 2.0);
			BlockPos column = BlockPos.containing(mob.getX() + Math.cos(angle) * d, mob.getY(), mob.getZ() + Math.sin(angle) * d);
			for (int dy : new int[] {0, 1, -1}) {
				BlockPos p = column.offset(0, dy, 0);
				if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
					&& !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty() && level.getFluidState(p).isEmpty()) {
					if (works(level, mob, foe, Vec3.atBottomCenterOf(p))) {
						h.coverAt = p;
					}
					break;
				}
			}
		}
		h.coverFoe = foe.position();
		h.coverTick = now;
		return h.coverAt;
	}

	/** Crouching at {@code base} a wall stands between the gunner and its foe, and standing it does not (so it can lean out and shoot). */
	private static boolean works(ServerLevel level, Mob mob, LivingEntity foe, Vec3 base) {
		Vec3 standing = base.add(0.0, mob.getEyeHeight(), 0.0), ducking = base.add(0.0, mob.getBbHeight() * 0.72, 0.0);
		boolean leans = level.clip(new ClipContext(standing, foe.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
		boolean hidden = level.clip(new ClipContext(ducking, foe.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.BLOCK;
		return leans && hidden && base.distanceTo(foe.position()) > FOE_MIN;
	}
}
