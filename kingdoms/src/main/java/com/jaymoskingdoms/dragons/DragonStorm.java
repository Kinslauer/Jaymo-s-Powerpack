package com.jaymoskingdoms.dragons;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.139) Eurus's storm breath: a crackling bolt from its mouth along where it's aimed. It strikes the first creature
 * on the line (within {@link #RANGE}), then arcs from it to up to {@link #CHAIN_MAX} more near each other, each a little
 * weaker, with sparks along every arc and a crack of thunder. Lightning damage, no fire of its own, and no block is ever
 * touched, with one rare exception: when mobGriefing allows it and it's thundering, the first creature struck may have a
 * real bolt called down on it. It spares what {@link DragonBreath#spared} spares.
 */
public final class DragonStorm {
	/** How far the bolt reaches (blocks) and how wide the line it strikes along is (the most a creature may be off the aim). */
	public static final double RANGE = 20.0, AIM_WIDTH = 1.6;
	/** Damage of the first strike; each arc after it does this share of the one before. */
	public static final float DAMAGE = 7.0F, FALLOFF = 0.75F;
	/** The most creatures a bolt arcs on to after the first, and how far (blocks) an arc leaps. */
	public static final int CHAIN_MAX = 3;
	public static final double CHAIN_RANGE = 7.0;
	/** A bolt every this many ticks. */
	public static final int HIT_EVERY = 8;
	/** The chance of a real lightning bolt on the first creature struck, in a thunderstorm with mobGriefing on. */
	public static final float STRIKE_CHANCE = 0.12F;
	/** Sparks a tick round the mouth, and how many along each arc (per block). */
	static final int MOUTH_SPARKS = 4, SPARKS_PER_BLOCK = 3;
	/** How far an arc's sparks wander off a straight line (blocks). */
	static final double JAG = 0.35;

	private DragonStorm() {
	}

	/**
	 * One tick of breath from {@code mouth} along {@code aim} (a unit vector). {@code tick} paces the bolts;
	 * {@code callLightning} whether a real bolt may be called down in a thunderstorm (the wild one's follows mobGriefing).
	 */
	public static void breathe(ServerLevel level, LivingEntity dragon, Vec3 mouth, Vec3 aim, int tick, boolean callLightning) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < MOUTH_SPARKS; i++) {
			Vec3 d = DragonBreath.spread(aim, random, 0.3);
			// (count 0: the particle flies along (dx, dy, dz) times speed)
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mouth.x, mouth.y, mouth.z, 0, d.x, d.y, d.z, 0.5 + random.nextDouble() * 0.5);
		}
		if (tick % HIT_EVERY != 0) {
			return;
		}
		LivingEntity first = firstAlong(level, dragon, mouth, aim);
		if (first == null) {
			// nothing on the line: the bolt crackles out to the end of its reach (or the first block)
			Vec3 end = mouth.add(aim.scale(RANGE));
			BlockHitResult hit = level.clip(new ClipContext(mouth, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon));
			if (hit.getType() == HitResult.Type.BLOCK) {
				end = hit.getLocation();
			}
			arc(level, mouth, end, random);
			level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.8F, 1.6F + random.nextFloat() * 0.3F);
			return;
		}
		List<LivingEntity> struck = new ArrayList<>();
		@Nullable LivingEntity at = first;
		Vec3 from = mouth;
		float damage = DAMAGE;
		while (at != null) {
			struck.add(at);
			Vec3 to = at.getBoundingBox().getCenter();
			arc(level, from, to, random);
			at.hurtServer(level, dragon.damageSources().source(DamageTypes.LIGHTNING_BOLT, dragon), damage);
			level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 0.7F, 1.4F + random.nextFloat() * 0.4F);
			if (struck.size() > CHAIN_MAX) {
				break;
			}
			damage *= FALLOFF;
			from = to;
			at = nextAfter(level, dragon, struck, at);
		}
		Vec3 where = first.position();
		// the crack of thunder, once a bolt
		level.playSound(null, where.x, where.y, where.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 1.1F + random.nextFloat() * 0.3F);
		if (callLightning && level.isThundering() && random.nextFloat() < STRIKE_CHANCE && level.canSeeSky(first.blockPosition())) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(where.x, where.y, where.z);
				level.addFreshEntity(bolt);
			}
		}
	}

	/** The nearest creature along the aim: within reach, no further off the line than {@link #AIM_WIDTH}, and in the clear. */
	private static @Nullable LivingEntity firstAlong(ServerLevel level, LivingEntity dragon, Vec3 mouth, Vec3 aim) {
		AABB box = new AABB(mouth, mouth.add(aim.scale(RANGE))).inflate(AIM_WIDTH + 1.0);
		@Nullable LivingEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != dragon && !e.isSpectator())) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(mouth);
			double along = to.dot(aim);
			if (along < 0.0 || along > RANGE || to.subtract(aim.scale(along)).length() > AIM_WIDTH + target.getBbWidth() * 0.5 || along >= bestAlong
				|| DragonBreath.spared(dragon, target) || !DragonBreath.clear(level, dragon, mouth, target.getBoundingBox().getCenter())) {
				continue;
			}
			best = target;
			bestAlong = along;
		}
		return best;
	}

	/** The nearest creature to {@code from} within {@link #CHAIN_RANGE} that the bolt hasn't struck yet (and may strike), or null. */
	private static @Nullable LivingEntity nextAfter(ServerLevel level, LivingEntity dragon, List<LivingEntity> struck, LivingEntity from) {
		Vec3 centre = from.getBoundingBox().getCenter();
		@Nullable LivingEntity best = null;
		double bestDistance = CHAIN_RANGE * CHAIN_RANGE;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(CHAIN_RANGE),
			e -> e.isAlive() && e != dragon && !e.isSpectator() && !struck.contains(e))) {
			double distance = target.getBoundingBox().getCenter().distanceToSqr(centre);
			if (distance < bestDistance && !DragonBreath.spared(dragon, target) && DragonBreath.clear(level, dragon, centre, target.getBoundingBox().getCenter())) {
				best = target;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** A jagged line of sparks from {@code from} to {@code to}. */
	private static void arc(ServerLevel level, Vec3 from, Vec3 to, RandomSource random) {
		double length = from.distanceTo(to);
		int steps = Math.max(2, (int)Math.ceil(length * SPARKS_PER_BLOCK));
		for (int i = 0; i <= steps; i++) {
			double t = i / (double)steps;
			// (the ends stay put; the middle wanders)
			double jag = JAG * Math.sin(t * Math.PI);
			double x = from.x + (to.x - from.x) * t + (random.nextDouble() - 0.5) * 2.0 * jag;
			double y = from.y + (to.y - from.y) * t + (random.nextDouble() - 0.5) * 2.0 * jag;
			double z = from.z + (to.z - from.z) * t + (random.nextDouble() - 0.5) * 2.0 * jag;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
		}
	}
}
