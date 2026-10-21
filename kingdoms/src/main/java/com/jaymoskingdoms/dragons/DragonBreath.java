package com.jaymoskingdoms.dragons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.137) A dragon's fire breath: a cone of flame from its mouth along where it's aimed. Everything alive in the cone
 * (that the dragon can see) is burned and set alight (fire damage: what fire never hurts, Emberborn and blazes among
 * them, shrugs it off); the blocks it meets catch fire. Andy: "fire burns blocks": a wild dragon's fire obeys the
 * mobGriefing gamerule (as a creeper does), a ridden one's always burns. A tamed dragon's fire spares its riders, its
 * owner, the owner's tamed animals and teammates. Notus breathes it (the others breathe their own).
 */
public final class DragonBreath {
	/** How far the flame reaches (blocks) and the cone's half-angle (its cosine). */
	public static final double RANGE = 11.0, CONE_COS = 0.93;
	/** Damage each burn (every {@link #HIT_EVERY} ticks) and how long the fire stays on them (seconds). */
	public static final float DAMAGE = 5.0F, BURN_SECONDS = 6.0F;
	public static final int HIT_EVERY = 5;
	/** Flame particles a tick, and how many rays look for blocks to set alight each burn. */
	static final int FLAMES = 7, BLOCK_RAYS = 3;

	private DragonBreath() {
	}

	/**
	 * One tick of breath from {@code mouth} along {@code aim} (a unit vector). {@code tick} paces the burns;
	 * {@code burnBlocks} whether the blocks it meets catch.
	 */
	public static void breathe(ServerLevel level, LivingEntity dragon, Vec3 mouth, Vec3 aim, int tick, boolean burnBlocks) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < FLAMES; i++) {
			Vec3 d = spread(aim, random, 0.16);
			double speed = 0.55 + random.nextDouble() * 0.35;
			// (count 0: the particle flies along (dx, dy, dz) times speed)
			level.sendParticles(random.nextInt(4) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 0, d.x, d.y, d.z,
				speed);
		}
		if (random.nextInt(3) == 0) {
			Vec3 d = spread(aim, random, 0.2);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, mouth.x, mouth.y, mouth.z, 0, d.x, d.y + 0.1, d.z, 0.4);
		}
		if (tick % 6 == 0) {
			level.playSound(null, mouth.x, mouth.y, mouth.z, tick % 12 == 0 ? SoundEvents.BLAZE_SHOOT : SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE,
				1.2F, 0.6F + random.nextFloat() * 0.2F);
		}
		if (tick % HIT_EVERY != 0) {
			return;
		}
		AABB box = new AABB(mouth, mouth).inflate(RANGE);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != dragon && !e.isSpectator())) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(mouth);
			double distance = to.length();
			if (distance > RANGE || distance > 0.5 && to.normalize().dot(aim) < CONE_COS || spared(dragon, target) || !clear(level, dragon, mouth,
				target.getBoundingBox().getCenter())) {
				continue;
			}
			if (target.hurtServer(level, dragon.damageSources().source(DamageTypes.IN_FIRE, dragon), DAMAGE)) {
				target.igniteForSeconds(BURN_SECONDS);
			}
		}
		if (burnBlocks) {
			for (int i = 0; i < BLOCK_RAYS; i++) {
				Vec3 end = mouth.add(spread(aim, random, 0.12).scale(RANGE));
				BlockHitResult hit = level.clip(new ClipContext(mouth, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, dragon));
				if (hit.getType() == HitResult.Type.BLOCK) {
					light(level, hit.getBlockPos().relative(hit.getDirection()));
				}
			}
		}
	}

	/** A dragon's own side: its riders, and a tamed one's owner, the owner's tamed animals and teammates. */
	static boolean spared(LivingEntity dragon, Entity target) {
		if (dragon.hasPassenger(target) || target instanceof WindDragon other && !other.isTamed() && dragon instanceof WindDragon self
			&& !self.isTamed()) {
			return true;
		}
		if (!(dragon instanceof OwnableEntity ownable)) {
			return false;
		}
		LivingEntity owner = ownable.getOwner();
		if (owner == null) {
			return false;
		}
		return target == owner || target.isAlliedTo(owner) || target instanceof OwnableEntity pet && pet.getOwner() == owner;
	}

	/** Nothing solid between the mouth and {@code to}. */
	static boolean clear(ServerLevel level, Entity dragon, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, dragon)).getType() == HitResult.Type.MISS;
	}

	/** Sets {@code pos} alight if fire can stand there (as Powers' Pyre does). */
	static void light(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.NORTH)) {
			level.setBlock(pos, BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
		}
	}

	/** {@code aim} scattered by up to about {@code by} each way, still a unit vector. */
	static Vec3 spread(Vec3 aim, RandomSource random, double by) {
		return aim.add((random.nextDouble() - 0.5) * 2.0 * by, (random.nextDouble() - 0.5) * 2.0 * by, (random.nextDouble() - 0.5) * 2.0 * by)
			.normalize();
	}

	/** The unit vector of a yaw and pitch (degrees), as {@code Entity.calculateViewVector}. */
	public static Vec3 aim(float yaw, float pitch) {
		float p = pitch * ((float)Math.PI / 180.0F), y = -yaw * ((float)Math.PI / 180.0F);
		float cy = (float)Math.cos(y), sy = (float)Math.sin(y), cp = (float)Math.cos(p), sp = (float)Math.sin(p);
		return new Vec3(sy * cp, -sp, cy * cp);
	}
}
