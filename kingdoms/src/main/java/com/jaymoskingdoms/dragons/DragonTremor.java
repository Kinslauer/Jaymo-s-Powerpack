package com.jaymoskingdoms.dragons;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Stegonaut's breath, the stone tremor (after Dragonforge's earth breath, RochStudio): a rumble that runs along the ground
 * in a cone before it, flinging grit ({@link DragonSounds#RUBBLE}), knocking whoever stands in it back and leaving them weak.
 * Where blocks may be broken, it shatters glass and leaves and cracks the ground: stone to cobblestone, cobblestone to gravel,
 * grass and dirt to coarse dirt.
 */
final class DragonTremor {
	/** How far it runs (blocks), how wide (the cosine of its half angle), how often it strikes (ticks) and how hard. */
	static final double RANGE = 9.0, CONE = 0.8, PUSH = 0.6;
	static final int EVERY = 6, WEAK_TICKS = 60, CRACKS = 3;
	static final float DAMAGE = 5.0F;

	private DragonTremor() {
	}

	static void breathe(ServerLevel level, WindDragon dragon, Vec3 aim, int tick, boolean griefing) {
		RandomSource random = dragon.getRandom();
		Vec3 flat = new Vec3(aim.x, 0.0, aim.z);
		flat = flat.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0.0F, dragon.getYRot()) : flat.normalize();
		Vec3 from = dragon.position().add(flat.scale(dragon.getBbWidth() * 0.5));
		for (int i = 0; i < 6; i++) {
			Vec3 at = from.add(DragonBreath.spread(flat, random, 0.35).multiply(1.0, 0.0, 1.0).scale(random.nextDouble() * RANGE));
			level.sendParticles(DragonSounds.RUBBLE, at.x, at.y + 0.3, at.z, 1, 0.2, 0.1, 0.2, 0.12);
		}
		if (tick % 20 == 0) {
			level.playSound(null, dragon.blockPosition(), DragonSounds.EARTH_BREATH.value(), SoundSource.NEUTRAL, 2.0F, 0.9F + random.nextFloat() * 0.2F);
		}
		if (tick % EVERY != 0) {
			return;
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(RANGE, 3.0, RANGE), dragon::spareable)) {
			Vec3 to = e.position().subtract(from).multiply(1.0, 0.0, 1.0);
			double d = to.length();
			if (d > RANGE || d > 0.5 && to.scale(1.0 / d).dot(flat) < CONE || Math.abs(e.getY() - dragon.getY()) > 3.0 || !e.onGround()) {
				continue;
			}
			if (e.hurtServer(level, dragon.damageSources().mobAttack(dragon), DAMAGE)) {
				e.push(flat.x * PUSH, 0.25, flat.z * PUSH);
				e.syncVelocity = true;
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 1), dragon);
			}
		}
		if (!griefing) {
			return;
		}
		for (int i = 0; i < CRACKS; i++) {
			Vec3 at = from.add(DragonBreath.spread(flat, random, 0.35).multiply(1.0, 0.0, 1.0).scale(1.0 + random.nextDouble() * (RANGE - 1.0)));
			// the first block down from just above its feet
			BlockPos pos = BlockPos.containing(at.x, dragon.getY() + 1.0, at.z);
			for (int down = 0; down < 4; down++, pos = pos.below()) {
				if (!level.getBlockState(pos).isAir()) {
					crack(level, pos);
					break;
				}
			}
		}
	}

	/** Cracks the block at {@code pos}: true if it changed. */
	private static boolean crack(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
			return false;
		}
		if (state.getSoundType() == SoundType.GLASS || state.is(BlockTags.LEAVES)) {
			level.destroyBlock(pos, false);
			return true;
		}
		BlockState now = state.is(Blocks.STONE) ? Blocks.COBBLESTONE.defaultBlockState()
			: state.is(Blocks.COBBLESTONE) ? Blocks.GRAVEL.defaultBlockState()
			: state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
				? Blocks.COARSE_DIRT.defaultBlockState() : null;
		if (now == null) {
			return false;
		}
		level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
		level.setBlockAndUpdate(pos, now);
		return true;
	}
}
