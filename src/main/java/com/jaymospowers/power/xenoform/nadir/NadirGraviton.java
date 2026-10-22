package com.jaymospowers.power.xenoform.nadir;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Nadir's Graviton (V): weight, Nadir's to give and take. A from-scratch take on the ideas of The Awakening's Graviton
 * power (an all-rights-reserved mod: nothing of its code or art is used).
 * <ul>
 * <li>At a creature (within {@link #HAND_REACH}): Heavy Hand. Tap: it's yanked to arm's length; sneak + tap: shoved
 * away. Hold {@link #HAND_HOLD} ticks: slammed into the ground and pinned; sneak + hold: thrown high into the air.</li>
 * <li>At nothing, tap: Weightless, a zone where you look; whatever wanders in floats up helpless, then comes down hard,
 * and arrows through it drift.</li>
 * <li>At nothing, hold {@link #HORIZON_HOLD} ticks: Event Horizon, a black hole where you look. It drags everything in
 * and hurts what's close; the small things that reach the core are gone; then it collapses and throws the rest out.</li>
 * </ul>
 */
public final class NadirGraviton {
	// ---- tuning ----
	public static final double HAND_REACH = 24.0;
	public static final int HAND_HOLD = 8, TAP_COOLDOWN = 20, HOLD_COOLDOWN = 60;
	public static final float YANK_DAMAGE = 4.0F, SHOVE_DAMAGE = 2.0F, SLAM_DAMAGE = 9.0F;
	public static final int PIN_TICKS = 30;
	/** Sneak + hold throws a creature this fast upward (about 12 blocks). */
	public static final double LAUNCH = 1.6;

	public static final double ZONE_REACH = 24.0, ZONE_RADIUS = 5.0;
	public static final int ZONE_TICKS = 60, ZONE_COOLDOWN = 160;
	/** How long the lifted float (mobs, players), and how hard they're brought down after. */
	public static final int FLOAT_MOB = 60, FLOAT_PLAYER = 30;
	public static final double SLAM_DOWN = 1.8;

	public static final int HORIZON_HOLD = 20;
	public static final double HORIZON_REACH = 32.0, HORIZON_PULL = 16.0, HORIZON_CORE = 4.0;
	/** It opens over this many ticks, holds this long, then collapses. */
	public static final int HORIZON_OPEN = 20, HORIZON_HOLD_TICKS = 120, HORIZON_COOLDOWN = 900;
	public static final float HORIZON_DAMAGE = 1.5F, COLLAPSE_DAMAGE = 12.0F;
	public static final double COLLAPSE_RADIUS = 8.0;
	/** A creature with no more health than this that reaches the core is erased (never a player). */
	public static final float ERASE_HEALTH = 30.0F;

	private NadirGraviton() {
	}

	// ---- Heavy Hand ----

	/** Tap: yank (or, sneaking, shove). */
	public static void tap(ServerPlayer player, LivingEntity target, boolean sneaking) {
		ServerLevel level = player.level();
		if (sneaking) {
			push(target, target.position().subtract(player.position()), 2.2, 0.35);
			target.hurtServer(level, player.damageSources().playerAttack(player), SHOVE_DAMAGE);
		} else {
			Vec3 dest = player.position().add(player.getLookAngle().horizontal().normalize().scale(1.6));
			Vec3 pull = dest.subtract(target.position());
			target.setDeltaMovement(pull.scale(0.32).add(0.0, 0.25 + Math.max(0.0, pull.y) * 0.08, 0.0));
			target.syncVelocity = true;
			target.hurtServer(level, player.damageSources().playerAttack(player), YANK_DAMAGE);
		}
		trail(level, player, target);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.4F, 1.8F);
	}

	/** Hold: slam and pin (or, sneaking, launch). */
	public static void hold(ServerPlayer player, NadirState st, LivingEntity target, boolean sneaking) {
		ServerLevel level = player.level();
		if (sneaking) {
			target.setDeltaMovement(target.getDeltaMovement().x * 0.2, LAUNCH, target.getDeltaMovement().z * 0.2);
			target.syncVelocity = true;
			target.removeEffect(MobEffects.LEVITATION);
			target.removeEffect(MobEffects.SLOW_FALLING);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY(), target.getZ(), 30, 0.4, 0.1, 0.4, 0.3);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.6F);
		} else {
			target.setDeltaMovement(0.0, -3.0, 0.0);
			target.syncVelocity = true;
			target.hurtServer(level, player.damageSources().playerAttack(player), SLAM_DAMAGE);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PIN_TICKS, 6, false, false, true));
			st.pinned.put(target.getId(), level.getGameTime() + PIN_TICKS);
			level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 0.1, target.getZ(), 40, 0.8, 0.05, 0.8, 0.05);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
		trail(level, player, target);
	}

	/** A dotted line of the void from Nadir's hand to what it grips. */
	private static void trail(ServerLevel level, ServerPlayer player, LivingEntity target) {
		Vec3 a = player.getEyePosition().subtract(0.0, 0.4, 0.0);
		Vec3 b = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		int steps = (int)Math.min(24, a.distanceTo(b) * 1.5);
		for (int i = 1; i <= steps; i++) {
			Vec3 p = a.lerp(b, i / (double)steps);
			level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	// ---- Weightless ----

	/** Opens the zone where the player looks. */
	public static void weightless(ServerPlayer player, NadirState st) {
		ServerLevel level = player.level();
		BlockHitResult hit = NadirAim.block(player, ZONE_REACH);
		Vec3 at = hit.getLocation();
		st.zone = at;
		st.zoneUntil = level.getGameTime() + ZONE_TICKS;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
	}

	// ---- Event Horizon ----

	/** Opens the black hole where the player looks: hung a few blocks over the ground, else in the air at full reach. */
	public static void horizon(ServerPlayer player, NadirState st) {
		ServerLevel level = player.level();
		BlockHitResult hit = NadirAim.block(player, HORIZON_REACH);
		Vec3 at = NadirAim.missed(hit) ? hit.getLocation() : hit.getLocation().add(0.0, 4.0, 0.0);
		st.horizon = at;
		st.horizonAt = level.getGameTime();
		st.horizonNext = level.getGameTime() + HORIZON_COOLDOWN;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5F, 0.4F);
	}

	// ---- every tick ----

	public static void tick(ServerPlayer player, NadirState st) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		tickPins(level, st, now);
		tickZone(player, level, st, now);
		tickFloaters(level, st, now);
		tickHorizon(player, level, st, now);
	}

	private static void tickPins(ServerLevel level, NadirState st, long now) {
		for (Iterator<Map.Entry<Integer, Long>> it = st.pinned.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> e = it.next();
			Entity entity = level.getEntity(e.getKey());
			if (entity == null || !entity.isAlive() || now >= e.getValue()) {
				it.remove();
				continue;
			}
			// held to the ground: no jumping out of it
			Vec3 v = entity.getDeltaMovement();
			entity.setDeltaMovement(0.0, Math.min(v.y, -0.2), 0.0);
			entity.syncVelocity = true;
		}
	}

	private static void tickZone(ServerPlayer player, ServerLevel level, NadirState st, long now) {
		if (st.zone == null) {
			return;
		}
		if (now >= st.zoneUntil) {
			st.zone = null;
			return;
		}
		Vec3 c = st.zone;
		if (now % 2 == 0) {
			for (int i = 0; i < 16; i++) {
				double a = i / 16.0 * Math.PI * 2.0 + now * 0.05;
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * ZONE_RADIUS, c.y + 0.2, c.z + Math.sin(a) * ZONE_RADIUS, 1, 0.0, 0.3, 0.0, 0.02);
			}
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 3, ZONE_RADIUS * 0.5, 1.0, ZONE_RADIUS * 0.5, 0.01);
		}
		for (LivingEntity e : NadirAim.around(player, c, ZONE_RADIUS)) {
			if (!st.floaters.containsKey(e.getId())) {
				int ticks = e instanceof Player ? FLOAT_PLAYER : FLOAT_MOB;
				e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, ticks, 2, false, false, true));
				// helpless while it floats
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, false, true));
				st.floaters.put(e.getId(), now + ticks);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SHULKER_BULLET_HIT, SoundSource.PLAYERS, 0.6F, 1.4F);
			}
		}
		// arrows and the like thrown through it drift and slow
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(ZONE_RADIUS), p -> p.getOwner() != player)) {
			p.setDeltaMovement(p.getDeltaMovement().scale(0.8).add(0.0, 0.03, 0.0));
			p.syncVelocity = true;
		}
	}

	private static void tickFloaters(ServerLevel level, NadirState st, long now) {
		for (Iterator<Map.Entry<Integer, Long>> it = st.floaters.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> e = it.next();
			if (now < e.getValue()) {
				continue;
			}
			// the weight comes back all at once
			if (level.getEntity(e.getKey()) instanceof LivingEntity floater && floater.isAlive()) {
				floater.removeEffect(MobEffects.LEVITATION);
				floater.setDeltaMovement(0.0, -SLAM_DOWN, 0.0);
				floater.syncVelocity = true;
			}
			if (st.zone == null || now > e.getValue() + 40) {
				it.remove();
			}
		}
	}

	private static void tickHorizon(ServerPlayer player, ServerLevel level, NadirState st, long now) {
		if (st.horizon == null) {
			return;
		}
		Vec3 c = st.horizon;
		long age = now - st.horizonAt;
		float open = Math.min(1.0F, age / (float)HORIZON_OPEN);
		if (age >= HORIZON_OPEN + HORIZON_HOLD_TICKS) {
			collapse(player, st, true);
			return;
		}
		// the core: a knot of dark with matter spiralling into it
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 4, 0.25 * open, 0.25 * open, 0.25 * open, 0.0);
		for (int i = 0; i < 6; i++) {
			double a = (now * 0.35 + i * Math.PI / 3.0) % (Math.PI * 2.0);
			double r = 1.0 + 2.5 * open;
			level.sendParticles(ParticleTypes.PORTAL, c.x + Math.cos(a) * r, c.y + Math.sin(a * 2.0) * 0.3, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 6, HORIZON_PULL * 0.3 * open, 2.0, HORIZON_PULL * 0.3 * open, 0.0);
		if (now % 20 == 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.3F);
		}
		if (age < HORIZON_OPEN / 2) {
			return;
		}
		double reach = HORIZON_PULL * open;
		for (Entity e : level.getEntities(player, new AABB(c, c).inflate(reach), e -> e.isAlive() && !e.isSpectator())) {
			boolean living = e instanceof LivingEntity;
			if (living && !NadirAim.fair(player, e) || !living && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb)) {
				continue;
			}
			Vec3 to = c.subtract(e.position().add(0.0, e.getBbHeight() * 0.5, 0.0));
			double d = Math.max(0.5, to.length());
			if (d > reach) {
				continue;
			}
			Vec3 in = to.scale(1.0 / d);
			// a spiral, not a straight line: a little sideways for every bit inward
			Vec3 side = new Vec3(-in.z, 0.0, in.x).scale(0.35);
			double strength = (e instanceof Player ? 0.05 : 0.14) * (1.0 + (reach - d) / reach * 2.0);
			if (!living && d < 1.2) {
				// held at the core until the collapse
				e.setDeltaMovement(Vec3.ZERO);
				e.setNoGravity(true);
			} else {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.85).add(in.add(side).scale(strength)));
			}
			e.syncVelocity = true;
			if (e instanceof LivingEntity target) {
				target.resetFallDistance();
				if (d <= HORIZON_CORE && now % 10 == 0) {
					target.hurtServer(level, player.damageSources().playerAttack(player), HORIZON_DAMAGE);
				}
				if (d <= 1.4 && !(target instanceof Player) && target.getMaxHealth() <= ERASE_HEALTH) {
					// erased: what it carried comes out at the collapse, with the rest
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 0.5, target.getZ(), 30, 0.3, 0.3, 0.3, 0.3);
					target.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
				}
			}
		}
	}

	/** The black hole gives way: with {@code blast}, it hurts what's near and throws everything out; else it just closes. */
	public static void collapse(ServerPlayer player, NadirState st, boolean blast) {
		if (st.horizon == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 c = st.horizon;
		st.horizon = null;
		List<Entity> caught = level.getEntities(player, new AABB(c, c).inflate(HORIZON_PULL), e -> e.isAlive() && !e.isSpectator());
		for (Entity e : caught) {
			if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
				e.setNoGravity(false);
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 60, 0.6, 0.6, 0.6, 0.3);
		if (!blast) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.5F);
			return;
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 200, 3.0, 3.0, 3.0, 1.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.6F);
		for (Entity e : caught) {
			boolean living = e instanceof LivingEntity;
			if (living && !NadirAim.fair(player, e)) {
				continue;
			}
			Vec3 out = e.position().subtract(c);
			double d = Math.max(0.5, out.length());
			if (living && d <= COLLAPSE_RADIUS) {
				((LivingEntity)e).hurtServer(level, player.damageSources().playerAttack(player), COLLAPSE_DAMAGE);
			}
			double fling = 2.4 * (1.0 - d / (HORIZON_PULL + 1.0)) + 0.3;
			e.setDeltaMovement(out.scale(1.0 / d).scale(fling).add(0.0, 0.5, 0.0));
			e.syncVelocity = true;
		}
	}

	private static void push(LivingEntity target, Vec3 dir, double out, double up) {
		Vec3 flat = dir.horizontal();
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		target.setDeltaMovement(target.getDeltaMovement().add(flat.scale(out)).add(0.0, up, 0.0));
		target.syncVelocity = true;
	}

	/** Nadir is going: the black hole closes quietly, everything let go. */
	public static void end(ServerPlayer player, NadirState st) {
		collapse(player, st, false);
		ServerLevel level = player.level();
		for (int id : st.floaters.keySet()) {
			if (level.getEntity(id) instanceof LivingEntity floater) {
				floater.removeEffect(MobEffects.LEVITATION);
			}
		}
		if (st.dropTarget >= 0 && level.getEntity(st.dropTarget) != null) {
			level.getEntity(st.dropTarget).resetFallDistance();
		}
	}
}
