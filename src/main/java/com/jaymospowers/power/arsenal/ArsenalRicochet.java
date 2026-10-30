package com.jaymospowers.power.arsenal;

import com.jaymospowers.core.LockOn;
import com.jaymospowers.power.arsenal.entity.FrostLance;
import com.jaymospowers.power.arsenal.entity.RiftBlade;
import com.jaymospowers.power.zfighter.divine.FlyingSword;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.48, Weapon Forms patch 6) Gate Ricochet: a Void Arsenal ({@link ArsenalGates}) player's own thrown or fired
 * projectile (arrows, tridents, javelins, cannonballs, a Barrage blade in flight) that flies through one of their gates comes
 * out of the next gate along, turned toward the target: the lock-on, else what they last struck, else the nearest foe near that
 * gate, else it carries straight on the way it was going. Out of the second gate it may fly through a third and so on, gate to
 * gate, up to {@link #MAX_BOUNCES} times. Needs two gates in the same world; nothing to press, the gates do it.
 */
final class ArsenalRicochet {
	/** How near a gate's heart the projectile's path must pass to go through it. */
	static final double GATE_RADIUS = 1.7;
	/** How many gates one projectile may be passed on through. */
	static final int MAX_BOUNCES = 3;
	/** The slowest projectile that ricochets (blocks a tick): a stuck or dropped one is left alone. */
	static final double MIN_SPEED = 0.6;
	/** Ticks a projectile can't go through a gate again after coming out of one (the exit gate stays shut to it much longer). */
	static final int REARM = 4;
	/** How far (blocks) from its gate a target is looked for, and how far the owner's lock-on may be. */
	static final double TARGET_RANGE = 28.0, LOCK_RANGE = 64.0;
	/** The least speed a ricocheted projectile leaves at, and how it is nudged up per block to the target (the drop of a shot). */
	static final double MIN_EXIT_SPEED = 1.2, LIFT_PER_BLOCK = 0.012;
	/** How far in front of the exit gate it appears. */
	static final double EXIT_OFFSET = 0.9;
	/** How far round the owner projectiles are looked for. */
	static final double SCAN = 96.0;

	private static final class Bounce {
		int count;
		long readyAt;
		/** The gate (index into the owner's list) it last came out of, which it can't re-enter at once. */
		int from = -1;
		long lastSeen;
	}

	private static final Map<Integer, Bounce> BOUNCES = new HashMap<>();

	private ArsenalRicochet() {
	}

	/** From {@link ArsenalGates#tick}: every tick, for a corrupted owner. */
	static void tick(ServerPlayer player) {
		List<ArsenalGates.Gate> all = ArsenalGates.gates(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now % 100 == 0) {
			BOUNCES.values().removeIf(b -> b.lastSeen + 200 < now);
		}
		List<Integer> here = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).dimension() == level.dimension()) {
				here.add(i);
			}
		}
		if (here.size() < 2) {
			return;
		}
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(SCAN),
			p -> p.getOwner() == player && p.isAlive() && eligible(p))) {
			Vec3 motion = projectile.getDeltaMovement();
			if (motion.length() < MIN_SPEED) {
				continue;
			}
			Bounce bounce = BOUNCES.computeIfAbsent(projectile.getId(), id -> new Bounce());
			bounce.lastSeen = now;
			if (bounce.count >= MAX_BOUNCES || now < bounce.readyAt) {
				continue;
			}
			Vec3 from = new Vec3(projectile.xo, projectile.yo, projectile.zo);
			Vec3 to = projectile.position();
			for (int index : here) {
				if (index == bounce.from) {
					continue;
				}
				Vec3 heart = all.get(index).pos().add(0.0, 0.9, 0.0);
				if (distanceToSegment(heart, from, to) <= GATE_RADIUS) {
					go(player, level, all, here, index, projectile, bounce, motion, now);
					break;
				}
			}
		}
	}

	private static boolean eligible(Projectile projectile) {
		if (projectile instanceof FrostLance || projectile instanceof FlyingSword) {
			// these steer themselves
			return false;
		}
		return !(projectile instanceof RiftBlade blade && blade.isHovering());
	}

	/** Through gate {@code entry}; out of the next one in the owner's list (in this world). */
	private static void go(ServerPlayer player, ServerLevel level, List<ArsenalGates.Gate> all, List<Integer> here, int entry, Projectile projectile,
		Bounce bounce, Vec3 motion, long now) {
		int exit = here.get((here.indexOf(entry) + 1) % here.size());
		Vec3 in = all.get(entry).pos().add(0.0, 0.9, 0.0);
		Vec3 out = all.get(exit).pos().add(0.0, 0.9, 0.0);
		double speed = Math.max(MIN_EXIT_SPEED, motion.length());
		Vec3 dir = motion.normalize();
		LivingEntity target = target(player, level, out, projectile);
		if (target != null) {
			Vec3 at = target.getBoundingBox().getCenter();
			Vec3 to = at.subtract(out);
			double flat = to.horizontalDistance();
			Vec3 aimed = new Vec3(to.x, to.y + flat * LIFT_PER_BLOCK, to.z);
			if (aimed.lengthSqr() > 1.0E-4) {
				dir = aimed.normalize();
			}
		}
		Vec3 arrive = out.add(dir.scale(EXIT_OFFSET));
		puff(level, in);
		projectile.setPos(arrive);
		projectile.setDeltaMovement(dir.scale(speed));
		projectile.setYRot((float)(Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG));
		projectile.setXRot((float)(Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG));
		projectile.setOldPosAndRot();
		projectile.needsSync = true;
		projectile.syncVelocity = true;
		bounce.count++;
		bounce.from = exit;
		bounce.readyAt = now + REARM;
		puff(level, out);
		level.playSound(null, out.x, out.y, out.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F + bounce.count * 0.15F);
	}

	private static void puff(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 12, 0.25, 0.4, 0.25, 0.08);
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 10, 0.2, 0.4, 0.2, 0.4);
	}

	/** The lock-on, else what the owner last struck, else the nearest foe near the exit gate; null if none. */
	private static @Nullable LivingEntity target(ServerPlayer player, ServerLevel level, Vec3 out, Projectile projectile) {
		LivingEntity locked = LockOn.target(player);
		if (locked != null && locked.isAlive() && locked.position().distanceTo(out) <= LOCK_RANGE) {
			return locked;
		}
		LivingEntity last = player.getLastHurtMob();
		if (last != null && last.isAlive() && last != player && last.level() == level && last.position().distanceTo(out) <= TARGET_RANGE
			&& player.tickCount - player.getLastHurtMobTimestamp() < 200) {
			return last;
		}
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(out, out).inflate(TARGET_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && e instanceof Enemy && !e.isAlliedTo(player))) {
			double d = foe.position().distanceToSqr(out);
			if (d < bestDist) {
				bestDist = d;
				best = foe;
			}
		}
		return best;
	}

	/** How far {@code point} is from the segment a to b. */
	private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		if (len2 < 1.0E-6) {
			return point.distanceTo(a);
		}
		double t = Mth.clamp(point.subtract(a).dot(ab) / len2, 0.0, 1.0);
		return point.distanceTo(a.add(ab.scale(t)));
	}

	/** The server is stopping. */
	static void clear() {
		BOUNCES.clear();
	}
}
