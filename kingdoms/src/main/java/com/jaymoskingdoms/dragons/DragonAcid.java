package com.jaymoskingdoms.dragons;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.137) Zephyrus' acid ({@link ZephyrusDragon}): it doesn't breathe a cone, it spits gobs, one every few ticks while
 * it breathes, along where it's aimed. Each gob is only a position and a velocity (no entity type): it's kept in this
 * breath's list, stepped every server tick with a little gravity, and drawn as particles along its path. A gob that
 * meets a creature (one the dragon doesn't spare, {@link DragonBreath#spared}) or a block bursts: everything alive
 * about the burst is hurt and poisoned, and it leaves a toxic puddle (an area effect cloud of poison, green, short-lived)
 * where it fell. Acid never changes blocks. One of these belongs to each Zephyrus.
 */
public final class DragonAcid {
	/** How far a gob flies at most (blocks, about): its speed (blocks a tick), its gravity (a tick), how long it lives (ticks). */
	public static final double RANGE = 16.0;
	static final double SPEED = 1.0, GRAVITY = 0.012;
	static final int MAX_AGE = 17;
	/** A gob is spat every this many ticks, scattered by about this much; it's aimed this far over the mark to make up for its fall. */
	static final int SPIT_EVERY = 6;
	static final double SCATTER = 0.025, LOB = 0.05;
	/** How near a creature must be to a gob's path to be hit, and how far a burst splashes (blocks). */
	static final double HIT_RADIUS = 0.6, SPLASH_RADIUS = 2.2;
	/** What a burst does to each creature in it: damage, and poison (ticks, level). */
	static final float DAMAGE = 6.0F;
	static final int POISON_TICKS = 120, POISON_LEVEL = 0;
	/** The puddle: its radius (blocks), how long it lasts (ticks), how long its poison lasts on whoever's in it (share of the usual). */
	static final float PUDDLE_RADIUS = 3.0F, PUDDLE_SCALE = 0.5F;
	static final int PUDDLE_TICKS = 140, PUDDLE_POISON_TICKS = 120;
	/** Acid green (ARGB), and its paler spray. */
	static final int GREEN = 0xFF7FCC2A, GREEN_PALE = 0xFFB6E84A;
	/** The most gobs in the air at once (a stuck breath can't pile them up). */
	static final int MAX_GOBS = 24;

	/** One gob in the air. */
	private static final class Gob {
		Vec3 pos, vel;
		int age;

		Gob(Vec3 pos, Vec3 vel) {
			this.pos = pos;
			this.vel = vel;
		}
	}

	private final List<Gob> gobs = new ArrayList<>();

	/** One tick of breath from {@code mouth} along {@code aim} (a unit vector): spits a gob every {@link #SPIT_EVERY} ticks. */
	public void breathe(ServerLevel level, LivingEntity dragon, Vec3 mouth, Vec3 aim, int tick) {
		RandomSource random = level.getRandom();
		// a little spray at the lips every tick
		level.sendParticles(ParticleTypes.ITEM_SLIME, mouth.x, mouth.y, mouth.z, 1, 0.1, 0.1, 0.1, 0.02);
		if (tick % SPIT_EVERY != 0 || this.gobs.size() >= MAX_GOBS) {
			return;
		}
		Vec3 lobbed = DragonBreath.spread(aim.add(0.0, LOB, 0.0), random, SCATTER);
		this.gobs.add(new Gob(mouth, lobbed.scale(SPEED)));
		level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.4F, 0.5F + random.nextFloat() * 0.2F);
	}

	/** Moves every gob on a tick (call it every server tick, breathing or not). */
	public void step(ServerLevel level, LivingEntity dragon) {
		if (this.gobs.isEmpty()) {
			return;
		}
		Iterator<Gob> it = this.gobs.iterator();
		while (it.hasNext()) {
			Gob gob = it.next();
			Vec3 from = gob.pos, to = from.add(gob.vel);
			gob.age++;
			// a block in the way (water doesn't stop it)
			BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
			Vec3 end = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : to;
			// a creature along the way: the nearest one
			LivingEntity struck = null;
			Vec3 strikeAt = null;
			double best = Double.MAX_VALUE;
			AABB path = new AABB(from, end).inflate(HIT_RADIUS + 0.5);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, path, c -> c.isAlive() && c != dragon && !c.isSpectator() && !DragonBreath.spared(dragon, c))) {
				Optional<Vec3> hit = e.getBoundingBox().inflate(HIT_RADIUS * 0.5).clip(from, end);
				if (hit.isPresent() && from.distanceToSqr(hit.get()) < best) {
					best = from.distanceToSqr(hit.get());
					struck = e;
					strikeAt = hit.get();
				}
			}
			trail(level, from, strikeAt != null ? strikeAt : end);
			if (struck != null) {
				burst(level, dragon, strikeAt);
				it.remove();
			} else if (block.getType() == HitResult.Type.BLOCK) {
				// (the puddle lies on what it hit: a hair out from the face)
				Vec3 face = new Vec3(block.getDirection().getStepX(), block.getDirection().getStepY(), block.getDirection().getStepZ());
				burst(level, dragon, block.getLocation().add(face.scale(0.1)));
				it.remove();
			} else if (gob.age >= MAX_AGE || to.y < level.getMinY()) {
				// it spent itself in the air: a last drip, no puddle
				level.sendParticles(ParticleTypes.ITEM_SLIME, end.x, end.y, end.z, 4, 0.2, 0.2, 0.2, 0.02);
				it.remove();
			} else {
				gob.pos = to;
				gob.vel = gob.vel.add(0.0, -GRAVITY, 0.0);
			}
		}
	}

	/** Whether a tamed dragon's riders, owner or the owner's pets are near enough {@code at} for a puddle there to reach them. */
	private static boolean nearFriends(LivingEntity dragon, Vec3 at) {
		if (!(dragon instanceof OwnableEntity ownable) || ownable.getOwner() == null && !dragon.isVehicle()) {
			return false;
		}
		double reach = PUDDLE_RADIUS + 1.0;
		AABB box = new AABB(at, at).inflate(reach, 2.0, reach);
		return !dragon.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != dragon && DragonBreath.spared(dragon, e)).isEmpty();
	}

	/** Draws a gob's flight from {@code from} to {@code to}: a fat green bead with drips. */
	private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		int n = Math.max(1, (int)Math.ceil(d.length() / 0.4));
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(d.scale((double)i / n));
			level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, GREEN), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		level.sendParticles(ParticleTypes.ITEM_SLIME, to.x, to.y, to.z, 1, 0.1, 0.1, 0.1, 0.0);
	}

	/** A gob bursts at {@code at}: it hurts and poisons what's near, and leaves a puddle. */
	private static void burst(ServerLevel level, LivingEntity dragon, Vec3 at) {
		level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, GREEN_PALE), at.x, at.y, at.z, 16, 0.5, 0.3, 0.5, 0.0);
		level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 12, 0.5, 0.3, 0.5, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.2F, 0.6F);
		Vec3 centre = at.add(0.0, 0.3, 0.0);
		AABB box = new AABB(at, at).inflate(SPLASH_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, c -> c.isAlive() && c != dragon && !c.isSpectator() && !DragonBreath.spared(dragon, c))) {
			Vec3 to = e.getBoundingBox().getCenter();
			if (to.distanceTo(centre) > SPLASH_RADIUS + e.getBbWidth() * 0.5 || !DragonBreath.clear(level, dragon, centre, to)) {
				continue;
			}
			if (e.hurtServer(level, dragon.damageSources().indirectMagic(dragon, dragon), DAMAGE)) {
				e.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_LEVEL), dragon);
			}
		}
		if (nearFriends(dragon, at)) {
			// a tamed one's puddle would poison its own (a cloud spares no one): the splash alone
			return;
		}
		AreaEffectCloud puddle = new AreaEffectCloud(level, at.x, at.y, at.z);
		puddle.setOwner(dragon);
		puddle.setCustomParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, GREEN));
		puddle.setRadius(PUDDLE_RADIUS);
		puddle.setDuration(PUDDLE_TICKS);
		puddle.setRadiusPerTick(-PUDDLE_RADIUS * 0.5F / PUDDLE_TICKS);
		puddle.setPotionDurationScale(PUDDLE_SCALE);
		puddle.addEffect(new MobEffectInstance(MobEffects.POISON, PUDDLE_POISON_TICKS, 0));
		level.addFreshEntity(puddle);
	}
}
