package com.jaymospowers.core.wounds;

import com.jaymospowers.core.Carry;
import com.jaymospowers.mixin.LivingEntityVoiceAccessor;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.142) A downed mob's signs of life, server side ({@link Downed#step}). Andy (2026-10-23): "Mobs should flail around
 * or crawl or emit noise, when downed ... Humanoids should especially have a tendency to crawl away from the fight and
 * ideally to an ally so they can heal instead of lay and wait for death."
 * <p>
 * It groans now and then (its own hurt cry, low and weak). A crawler ({@link Downed#crawls}: most people, half of the
 * rest) drags itself to a friend who can see to it, if one is about and not past the fight; else away from the fight (whoever
 * felled it or last hurt it, or for a monster any player near), along a path. A friend at its side with hands and no
 * fight of its own (a fellow raider, a piglin, a teammate) kneels to it, binds it and gets it up after
 * {@link #ALLY_TEND_TICKS}. A downed villager crawls to the other villagers: Kingdoms' tending does the rest. A downed
 * dog or horse crawls to its owner. The flailing itself is drawn ({@code DownedPose}).
 */
public final class DownedCrawl {
	/** A groan: about every this many ticks (half to twice that), quieter and lower than its hurt cry (volume, pitch). */
	static final int GROAN_EVERY = 100;
	static final float GROAN_VOLUME = 0.75F, GROAN_PITCH = 0.75F;
	/** How near a fight is felt (blocks). */
	static final double THREAT_RANGE = 14.0;
	/** How far it looks for a friend, and how close is at their side (blocks). */
	static final double ALLY_RANGE = 32.0, ALLY_REACH = 2.0;
	/** How often it thinks again: who's after it, who'd help, where to crawl (ticks). */
	static final int PLAN_TICKS = 20;
	/** How far it tries to get from the fight each time, across and up (blocks). */
	static final int FLEE_DISTANCE = 10, FLEE_HEIGHT = 4;
	/** A friend at its side binds it and gets it up after this long (ticks); a hand at the work every so often. */
	static final int ALLY_TEND_TICKS = 200, ALLY_WORK_TICKS = 25;

	/** One downed mob's thinking. Not saved: after a reload it thinks again. */
	private static final class State {
		@Nullable LivingEntity threat, ally;
		@Nullable Vec3 away;
		/** The friend kneeling to it just now, and how long they've been at it (ticks). */
		@Nullable Mob kneeling;
		int tended;
		long nextGroan, nextPlan;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private DownedCrawl() {
	}

	/** Up again, or dead: its thinking goes. */
	static void forget(Entity entity) {
		State s = entity instanceof Mob mob ? STATES.remove(mob) : null;
		if (s != null) {
			stand(s);
		}
	}

	/** A player's blow: whoever was binding it starts over. */
	static void hit(Entity entity) {
		State s = entity instanceof Mob mob ? STATES.get(mob) : null;
		if (s != null) {
			s.tended = 0;
		}
	}

	/**
	 * Each tick a downed mob lies on the ground, free (not carried, not under a finisher): it groans, and a crawler crawls.
	 * {@code felledBy}: who put it down, if known.
	 */
	static void tick(ServerLevel level, Mob mob, @Nullable Entity felledBy) {
		if (Malfunction.is(mob)) {
			// (0.19.32) a machine doesn't groan or crawl: it sparks and shorts out where it lies
			mob.getNavigation().stop();
			Malfunction.tick(level, mob);
			return;
		}
		State s = STATES.computeIfAbsent(mob, m -> new State());
		long now = level.getGameTime();
		boolean out = Downed.passedOut(mob);
		if (!out) {
			groan(level, mob, s, now);
		}
		if (!Downed.crawls(mob) || out) {
			stand(s);
			mob.getNavigation().stop();
			return;
		}
		if (now >= s.nextPlan) {
			s.nextPlan = now + PLAN_TICKS;
			plan(level, mob, s, felledBy);
		}
		LivingEntity ally = s.ally;
		if (ally != null) {
			if (mob.distanceToSqr(ally) > ALLY_REACH * ALLY_REACH) {
				stand(s);
				crawl(mob);
				return;
			}
			// at their side: lying still, looking up at them
			mob.getNavigation().stop();
			face(mob, ally.getX(), ally.getZ(), true);
			if (ally instanceof Mob helper && !(ally instanceof Villager)) {
				// ((Kingdoms tends the villagers' own))
				tend(level, mob, helper, s);
			} else {
				stand(s);
			}
			return;
		}
		stand(s);
		LivingEntity threat = s.threat;
		if (s.away != null) {
			crawl(mob);
		} else if (threat != null && !(mob instanceof PathfinderMob)) {
			// (no way to path: straight away from it, as before 0.15.142)
			face(mob, threat.getX(), threat.getZ(), false);
			mob.setSpeed((float) (mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * Downed.CRAWL_SPEED));
		} else {
			mob.getNavigation().stop();
		}
	}

	/** Now and then, a groan: its own hurt cry, weak and low. The first a moment after it hits the ground. */
	private static void groan(ServerLevel level, Mob mob, State s, long now) {
		if (s.nextGroan == 0L) {
			s.nextGroan = now + 15 + level.getRandom().nextInt(30);
			return;
		}
		if (now < s.nextGroan) {
			return;
		}
		s.nextGroan = now + GROAN_EVERY / 2 + level.getRandom().nextInt(GROAN_EVERY * 3 / 2);
		SoundEvent cry = ((LivingEntityVoiceAccessor) mob).jaymospowers$hurtSound(level.damageSources().generic());
		if (cry != null) {
			float pitch = GROAN_PITCH * mob.getVoicePitch() * (0.9F + level.getRandom().nextFloat() * 0.2F);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), cry, mob.getSoundSource(), GROAN_VOLUME, pitch);
		}
	}

	/** Who's after it, who'd help, and which way to crawl. */
	private static void plan(ServerLevel level, Mob mob, State s, @Nullable Entity felledBy) {
		LivingEntity threat = threat(level, mob, felledBy);
		LivingEntity ally = ally(level, mob, threat);
		s.threat = threat;
		s.ally = ally;
		s.away = null;
		if (ally != null) {
			if (mob.distanceToSqr(ally) > ALLY_REACH * ALLY_REACH) {
				mob.getNavigation().moveTo(ally, Downed.CRAWL_SPEED);
			}
		} else if (threat != null && mob instanceof PathfinderMob crawler) {
			Vec3 away = DefaultRandomPos.getPosAway(crawler, FLEE_DISTANCE, FLEE_HEIGHT, threat.position());
			if (away != null && mob.getNavigation().moveTo(away.x, away.y, away.z, Downed.CRAWL_SPEED)) {
				s.away = away;
			}
		}
	}

	/** One tick of crawling along the path it has: the path, then its legs (the AI that would do this is off). */
	private static void crawl(Mob mob) {
		mob.getNavigation().tick();
		mob.getMoveControl().tick();
	}

	/** Turns the body toward ({@code to}) or away from a point. */
	private static void face(Mob mob, double x, double z, boolean to) {
		double dx = to ? x - mob.getX() : mob.getX() - x, dz = to ? z - mob.getZ() : mob.getZ() - z;
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.yBodyRot = yaw;
	}

	/**
	 * The fight it's in: whoever felled it or last hurt it, still up and near; or, for a monster, the nearest player near
	 * (not in creative or spectator).
	 */
	private static @Nullable LivingEntity threat(ServerLevel level, Mob mob, @Nullable Entity felledBy) {
		if (felledBy instanceof LivingEntity by && near(mob, by, THREAT_RANGE)) {
			return by;
		}
		LivingEntity last = mob.getLastHurtByMob();
		if (last != null && near(mob, last, THREAT_RANGE)) {
			return last;
		}
		if (mob instanceof Enemy) {
			Player nearest = level.getNearestPlayer(mob, THREAT_RANGE);
			if (nearest != null && !nearest.isCreative() && !nearest.isSpectator() && !Downed.isDowned(nearest)) {
				return nearest;
			}
		}
		return null;
	}

	private static boolean near(Mob mob, LivingEntity other, double range) {
		return other.isAlive() && !other.isRemoved() && other.level() == mob.level() && !Downed.isDowned(other) && mob.distanceToSqr(other) < range * range;
	}

	/** The nearest friend who could see to it, not on the far side of the fight (nearer to it than this one is). */
	private static @Nullable LivingEntity ally(ServerLevel level, Mob mob, @Nullable LivingEntity threat) {
		if (mob instanceof OwnableEntity owned && owned.getOwner() instanceof Player owner && owner != threat && near(mob, owner, ALLY_RANGE)
			&& !owner.isSpectator()) {
			// (a dog or a horse: its own master)
			return owner;
		}
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(ALLY_RANGE), other -> friend(mob, other))) {
			if (threat != null && (other == threat || other.distanceToSqr(threat) < mob.distanceToSqr(threat))) {
				continue;
			}
			double d = mob.distanceToSqr(other);
			if (d < bestDistance && d < ALLY_RANGE * ALLY_RANGE) {
				bestDistance = d;
				best = other;
			}
		}
		return best;
	}

	/** Who'd see to a downed one: a villager's fellows, a raider's, a piglin's, or anyone on its team. */
	private static boolean friend(Mob mob, Mob other) {
		if (other == mob || !other.isAlive() || other.isBaby() || Downed.isDowned(other) || Carry.isCarried(other) || other.isNoAi()) {
			return false;
		}
		if (mob instanceof Villager) {
			return other instanceof Villager;
		}
		if (mob instanceof Raider) {
			return other instanceof Raider;
		}
		if (mob instanceof AbstractPiglin) {
			return other instanceof AbstractPiglin;
		}
		return mob.getTeam() != null && mob.isAlliedTo(other);
	}

	/** Whoever was kneeling to it gets up off their knees; the binding starts over. */
	private static void stand(State s) {
		s.tended = 0;
		if (s.kneeling != null) {
			s.kneeling.setShiftKeyDown(false);
			s.kneeling = null;
		}
	}

	/** A friend at its side and free (no fight of its own) kneels to it, binds it and, in time, gets it up. */
	private static void tend(ServerLevel level, Mob body, Mob helper, State s) {
		if (helper.getTarget() != null || helper.isPassenger()) {
			stand(s);
			return;
		}
		if (s.kneeling != helper) {
			stand(s);
			s.kneeling = helper;
		}
		helper.getNavigation().stop();
		helper.getLookControl().setLookAt(body.getX(), body.getY() + 0.2, body.getZ());
		helper.setShiftKeyDown(true);
		if (++s.tended % ALLY_WORK_TICKS == 0) {
			helper.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.sendParticles(ParticleTypes.HEART, body.getX(), body.getY() + 0.6, body.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
		if (s.tended >= ALLY_TEND_TICKS) {
			stand(s);
			// (bound up: Bleeding stops it and gives a bandage's heal, then Downed gets it up, as for Kingdoms' tending)
			body.addTag(Bleeding.BANDAGED_TAG);
			body.addTag(Downed.REVIVE_TAG);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY() + 0.8, body.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
		}
	}
}
