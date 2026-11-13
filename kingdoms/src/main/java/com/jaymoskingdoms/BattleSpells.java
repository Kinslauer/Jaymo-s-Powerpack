package com.jaymoskingdoms;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The battlefield spells (fifth edition's web, grease, fog cloud, darkness, silence and spike growth, Andy's pick H8): each
 * lays a {@link Fields field} where the caster looks, and the field does its work on whoever stands in it.
 * <ul>
 * <li><b>Web</b>: whoever is in it, bar the caster, is caught as in a net ({@link Nets}) unless a Might save holds, and
 * crawls. Fire burns it off.</li>
 * <li><b>Grease</b>: a slick; a body moving on it may slip and fall ({@link PowersCompat#trip}: prone, so the upper hand),
 * unless an Agility save holds. Fire burns it off.</li>
 * <li><b>Fog</b>: a cloud that blinds whoever is in it; creatures lose a foe through it.</li>
 * <li><b>Darkness</b>: a sphere nobody can see through, darkvision and all.</li>
 * <li><b>Silence</b>: no spoken spell is cast inside ({@link SpellParts}), and every sound goes dull ({@link Deafened}).</li>
 * <li><b>Spike growth</b>: thorns that cut anyone who moves in them, a little for each step.</li>
 * </ul>
 * Web, fog, darkness, silence and thorns are held ({@link Concentration}): hit the caster hard and they go.
 */
public final class BattleSpells {
	/** How far the caster can set one (blocks). */
	static final double REACH = 20.0;
	/** Radius (blocks) and lifetime (ticks) of each; the lifetime grows with the casting's power. */
	static final double WEB_RADIUS = 3.0, GREASE_RADIUS = 2.5, FOG_RADIUS = 4.0, DARKNESS_RADIUS = 3.5, SILENCE_RADIUS = 5.0, THORNS_RADIUS = 3.5;
	static final int WEB_TICKS = 20 * 20, GREASE_TICKS = 20 * 30, FOG_TICKS = 20 * 25, DARKNESS_TICKS = 20 * 30, SILENCE_TICKS = 20 * 30, THORNS_TICKS = 20 * 40;
	/** How often (ticks) fields are worked on. */
	static final int EVERY = 5;
	/** A body already caught, slipped or cut is left alone this long (ticks). */
	static final int WEB_AGAIN = 60, GREASE_AGAIN = 30;
	/** Webbed bodies crawl at this slowness level while inside. */
	static final int WEB_SLOW = 2;
	/** Grease: how long a fall pins them (ticks); moving slower than this (blocks a tick) does not slip. */
	static final int SLIP_TICKS = 50;
	static final double SLIDING = 0.04;
	/** Fog and darkness: how long the blindness lasts after a look in (ticks), and the sight a creature keeps in fog (blocks). */
	static final int BLIND_TICKS = 30;
	static final double FOG_SIGHT = 2.5;
	/** Thorns: damage for each block moved, the most from one look (every {@link #EVERY} ticks), the least step that counts (blocks), and the share of cuts that open a wound. */
	static final float THORN_PER_BLOCK = 0.9F, THORN_MOST = 2.5F;
	static final double THORN_STEP = 0.12;
	static final float THORN_CUTS = 0.25F;
	/** Thorns and difficult ground slow whoever walks them (slowness level). */
	static final int THORN_SLOW = 1;

	private BattleSpells() {
	}

	/** Casts {@code spell} for {@code caster}: the field goes where they look. */
	static boolean cast(String spell, ServerLevel level, Player caster, float power) {
		Vec3 ground = NewSpells.aim(level, caster, REACH);
		Fields.Kind kind;
		double radius, lift;
		int ticks;
		switch (spell) {
			case "web" -> {
				kind = Fields.Kind.WEB;
				radius = WEB_RADIUS;
				lift = 1.0;
				ticks = WEB_TICKS;
			}
			case "grease" -> {
				kind = Fields.Kind.GREASE;
				radius = GREASE_RADIUS;
				lift = 0.0;
				ticks = GREASE_TICKS;
			}
			case "fog" -> {
				kind = Fields.Kind.FOG;
				radius = FOG_RADIUS;
				lift = FOG_RADIUS * 0.4;
				ticks = FOG_TICKS;
			}
			case "darkness" -> {
				kind = Fields.Kind.DARKNESS;
				radius = DARKNESS_RADIUS;
				lift = DARKNESS_RADIUS * 0.4;
				ticks = DARKNESS_TICKS;
			}
			case "silence" -> {
				kind = Fields.Kind.SILENCE;
				radius = SILENCE_RADIUS;
				lift = SILENCE_RADIUS * 0.4;
				ticks = SILENCE_TICKS;
			}
			case "spike_growth" -> {
				kind = Fields.Kind.THORNS;
				radius = THORNS_RADIUS;
				lift = 0.0;
				ticks = THORNS_TICKS;
			}
			default -> {
				return false;
			}
		}
		int lasts = Math.round(ticks * Math.max(0.5F, power));
		Fields.Field field = Fields.add(kind, level, ground, radius, lift, lasts, caster.getUUID(), null);
		if (kind != Fields.Kind.GREASE) {
			// a held spell: strike the caster and it goes
			Concentration.hold(caster, spell, lasts, field::end);
		}
		Fx.blast(level, field.at, (float) radius, kind.colour);
		level.playSound(null, ground.x, ground.y, ground.z, switch (kind) {
			case WEB -> SoundEvents.COBWEB_PLACE;
			case GREASE -> SoundEvents.SLIME_BLOCK_PLACE;
			case FOG -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value();
			case DARKNESS -> SoundEvents.SCULK_SHRIEKER_SHRIEK;
			case SILENCE -> SoundEvents.AMETHYST_BLOCK_RESONATE;
			default -> SoundEvents.SWEET_BERRY_BUSH_PLACE;
		}, SoundSource.PLAYERS, 0.9F, 0.9F);
		return true;
	}

	/** Whether this is one of ours. */
	static boolean ours(String spell) {
		return switch (spell) {
			case "web", "grease", "fog", "darkness", "silence", "spike_growth" -> true;
			default -> false;
		};
	}

	/** One tick of a field of the fight's kinds (they are worked on every {@link #EVERY} ticks). */
	static void tick(Fields.Field f, long now) {
		if ((now + f.made) % EVERY != 0L) {
			return;
		}
		ServerLevel level = f.level;
		LivingEntity owner = level.getEntity(f.owner) instanceof LivingEntity l ? l : null;
		AABB box = new AABB(f.at, f.at).inflate(f.radius + 1.0);
		List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, box, e -> Fields.body(e) && f.contains(e.getBoundingBox().getCenter()));
		switch (f.kind) {
			case WEB -> web(f, now, owner, inside);
			case GREASE -> grease(f, now, owner, inside);
			case FOG, DARKNESS -> murk(f, inside);
			case SILENCE -> inside.forEach(e -> {
				if (e instanceof Player) {
					Deafened.ring(e, 30);
				}
			});
			case THORNS -> thorns(f, now, owner, inside);
			default -> {
			}
		}
		if (f.kind == Fields.Kind.FOG || f.kind == Fields.Kind.DARKNESS) {
			hide(f, level);
		}
	}

	private static boolean burning(List<LivingEntity> inside) {
		for (LivingEntity e : inside) {
			if (e.isOnFire()) {
				return true;
			}
		}
		return false;
	}

	private static void web(Fields.Field f, long now, LivingEntity owner, List<LivingEntity> inside) {
		ServerLevel level = f.level;
		if (burning(inside)) {
			// fire eats a web
			level.sendParticles(ParticleTypes.FLAME, f.at.x, f.at.y, f.at.z, 30, f.radius * 0.5, 0.6, f.radius * 0.5, 0.02);
			level.playSound(null, f.at.x, f.at.y, f.at.z, SoundEvents.FIRE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.2F);
			f.end();
			return;
		}
		for (LivingEntity e : inside) {
			if (e.getUUID().equals(f.owner)) {
				continue;
			}
			if (!Nets.netted(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, WEB_SLOW, true, false, false));
			}
			if (now - f.acted.getOrDefault(e.getUUID(), -1000L) < WEB_AGAIN || Nets.netted(e)) {
				continue;
			}
			f.acted.put(e.getUUID(), now);
			if (!Saves.resist(e, Saves.Kind.GRAB)) {
				Nets.catchIn(e, owner);
			}
		}
	}

	private static void grease(Fields.Field f, long now, LivingEntity owner, List<LivingEntity> inside) {
		ServerLevel level = f.level;
		if (burning(inside)) {
			level.sendParticles(ParticleTypes.FLAME, f.at.x, f.at.y + 0.2, f.at.z, 24, f.radius * 0.5, 0.2, f.radius * 0.5, 0.03);
			for (LivingEntity e : inside) {
				e.igniteForSeconds(2.0F);
			}
			f.end();
			return;
		}
		for (LivingEntity e : inside) {
			if (!e.onGround() || e.getUUID().equals(f.owner) || Fields.body(e) && PowersCompat.tripped(e)) {
				continue;
			}
			Vec3 v = e.getDeltaMovement();
			double speed = Math.sqrt(v.x * v.x + v.z * v.z);
			if (speed < SLIDING) {
				continue;
			}
			// it slides on a little farther than it meant to
			e.setDeltaMovement(v.x * 1.25, v.y, v.z * 1.25);
			e.syncVelocity = true;
			if (now - f.acted.getOrDefault(e.getUUID(), -1000L) < GREASE_AGAIN) {
				continue;
			}
			f.acted.put(e.getUUID(), now);
			if (Saves.resist(e, Saves.Kind.BLAST)) {
				continue;
			}
			if (!PowersCompat.trip(e, owner, SLIP_TICKS)) {
				// not built to be tripped (a beast): it goes down on its side for a moment
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLIP_TICKS, 3), owner);
			}
			level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.NEUTRAL, 0.8F, 0.8F);
		}
	}

	/** Fog and darkness: whoever is inside is blinded; creatures inside lose their foe through it. */
	private static void murk(Fields.Field f, List<LivingEntity> inside) {
		boolean dark = f.kind == Fields.Kind.DARKNESS;
		for (LivingEntity e : inside) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, true, false, false));
			if (dark && e instanceof Player) {
				e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BLIND_TICKS, 0, true, false, false));
			}
			if (e instanceof Mob mob && mob.getTarget() != null && (dark || mob.distanceTo(mob.getTarget()) > FOG_SIGHT)) {
				mob.setTarget(null);
			}
		}
	}

	/** Creatures outside that have a foe on the far side of the cloud lose it: they cannot see through. */
	private static void hide(Fields.Field f, ServerLevel level) {
		AABB wide = new AABB(f.at, f.at).inflate(f.radius + 24.0);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, wide, m -> m.isAlive() && m.getTarget() != null)) {
			LivingEntity target = mob.getTarget();
			Vec3 a = mob.getEyePosition(), b = target.getEyePosition();
			boolean aIn = a.distanceToSqr(f.at) <= f.radius * f.radius, bIn = b.distanceToSqr(f.at) <= f.radius * f.radius;
			if (aIn || bIn && f.kind == Fields.Kind.FOG && a.distanceTo(b) <= FOG_SIGHT * 2.0) {
				continue;
			}
			if (cuts(a, b, f.at, f.radius * (f.kind == Fields.Kind.FOG ? 0.8 : 1.0))) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
		}
	}

	/** Whether the line from {@code a} to {@code b} passes through the sphere at {@code c}. */
	static boolean cuts(Vec3 a, Vec3 b, Vec3 c, double r) {
		Vec3 d = b.subtract(a);
		double len2 = d.lengthSqr();
		if (len2 < 1.0E-6) {
			return a.distanceToSqr(c) <= r * r;
		}
		double t = Math.clamp(c.subtract(a).dot(d) / len2, 0.0, 1.0);
		return a.add(d.scale(t)).distanceToSqr(c) <= r * r;
	}

	private static void thorns(Fields.Field f, long now, LivingEntity owner, List<LivingEntity> inside) {
		ServerLevel level = f.level;
		for (LivingEntity e : inside) {
			UUID id = e.getUUID();
			if (id.equals(f.owner) || !e.onGround() && e.getY() - f.at.y > 1.2) {
				f.last.remove(id);
				continue;
			}
			Vec3 here = e.position();
			Vec3 was = f.last.put(id, here);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, THORN_SLOW, true, false, false));
			if (was == null) {
				continue;
			}
			double moved = Math.sqrt((here.x - was.x) * (here.x - was.x) + (here.z - was.z) * (here.z - was.z));
			if (moved < THORN_STEP) {
				continue;
			}
			DamageSource source = owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().cactus();
			if (e.hurtServer(level, source, (float) Math.min(THORN_MOST, THORN_PER_BLOCK * moved))) {
				level.sendParticles(Fx.mote(Fields.Kind.THORNS.colour, 0.8F), e.getX(), e.getY(0.3), e.getZ(), 4, 0.2, 0.1, 0.2, 0.0);
				if (e.getRandom().nextFloat() < THORN_CUTS) {
					PowersCompat.cut(e);
				}
			}
		}
		f.last.keySet().removeIf(id -> inside.stream().noneMatch(e -> e.getUUID().equals(id)));
	}

	/** Whether {@code e} is a body inside a field of {@code kind} just now. */
	static boolean inside(Entity e, Fields.Kind kind) {
		return e.level() instanceof ServerLevel level && !Fields.at(level, e.getBoundingBox().getCenter(), kind).isEmpty();
	}
}
