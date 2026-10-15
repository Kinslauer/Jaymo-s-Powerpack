package com.jaymoskingdoms.disasters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.31) Natural disasters, stage 3: earthquakes, mostly near mountains. A quake is a {@link Calamity} of kind
 * {@link Calamity#QUAKE} (so the clients shake and rumble with it), magnitude 1 to 3 by its radius. While it lasts,
 * anyone standing staggers; loose blocks and cave ceilings round each player shake free and fall (on heads, too); a
 * strong one opens cracks in the ground, and the strongest can swallow a patch of it in a sinkhole. Breaking blocks
 * waits on {@link Disasters#DISASTER_DAMAGE}; a settlement puts its buildings back up in time ({@code Rebuilds}).
 */
public final class Earthquakes {
	/** By magnitude (1..3): how far it's felt, blocks shaken loose a tick round each player, cracks opened in all. */
	static final float[] RADIUS = {0.0F, 60.0F, 100.0F, 150.0F};
	static final int[] LOOSE = {0, 1, 3, 6}, CRACKS = {0, 0, 2, 4};
	/** The odds the strongest swallows a patch of ground (once); a sinkhole's radius and depth. */
	static final float SINKHOLE = 0.35F;
	static final int SINK_RADIUS_MIN = 3, SINK_RADIUS_MAX = 5, SINK_DEPTH_MIN = 6, SINK_DEPTH_MAX = 10;
	/** A crack's length range and depth range. */
	static final int CRACK_MIN = 7, CRACK_MAX = 16, CRACK_DEPTH_MIN = 3, CRACK_DEPTH_MAX = 6;
	/** How long it lasts (ticks), and how fast it comes on and dies away. */
	static final int LIFE_MIN = 300, LIFE_MAX = 600, GATHER = 60;
	/** Blocks shaken loose are found this far round (and below, above) a player; the hardest that can come loose. */
	static final int LOOSE_RANGE = 20, LOOSE_BELOW = 16, LOOSE_ABOVE = 10;
	static final float LOOSE_HARDEST = 3.0F;
	/** A falling block hurts this much per block fallen, at most this. */
	static final float FALL_HURT = 1.0F;
	static final int FALL_HURT_MOST = 6;
	/** How hard it staggers anyone standing (per magnitude), every few ticks. */
	static final double STAGGER = 0.06;
	/** The odds a land disaster near mountains is a quake instead; elsewhere. */
	static final float MOUNTAINS = 0.5F, ELSEWHERE = 0.05F;
	/** The ground this high above the sea counts as mountains too. */
	static final int HIGH = 50;

	private Earthquakes() {
	}

	/** The magnitude of a quake (by its radius). */
	static int magnitude(Calamity c) {
		return c.radius >= RADIUS[3] ? 3 : c.radius >= RADIUS[2] ? 2 : 1;
	}

	/** Whether {@code pos} is in or by the mountains. */
	static boolean mountainous(ServerLevel level, BlockPos pos) {
		if (level.getBiome(pos).is(BiomeTags.IS_MOUNTAIN)) {
			return true;
		}
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) > level.getSeaLevel() + HIGH;
	}

	/** A quake of {@code magnitude} (0: by chance, stronger in the mountains) somewhere near {@code near}. */
	static @Nullable Calamity start(ServerLevel level, Vec3 near, int magnitude, RandomSource random) {
		if (magnitude <= 0) {
			boolean hills = mountainous(level, BlockPos.containing(near));
			float roll = random.nextFloat();
			magnitude = roll < (hills ? 0.25F : 0.1F) ? 3 : roll < (hills ? 0.65F : 0.45F) ? 2 : 1;
		}
		if (Calamities.bloodMoon(level)) {
			magnitude = Math.min(3, magnitude + 1);
		}
		double a = random.nextDouble() * Math.PI * 2.0, d = random.nextDouble() * 30.0;
		Calamity c = new Calamity(Storms.nextId(), Calamity.QUAKE, near.x + Math.cos(a) * d, near.z + Math.sin(a) * d, 0.0, 0.0,
			RADIUS[magnitude], 0, Mth.nextInt(random, LIFE_MIN, LIFE_MAX), -1);
		Calamities.SERVER.add(c);
		for (ServerPlayer p : level.players()) {
			if (c.over(p.getX(), p.getZ()) > 0.0F || p.position().distanceToSqr(c.x, p.getY(), c.z) < Calamities.NEWS_RANGE * Calamities.NEWS_RANGE) {
				p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("disaster.jaymoskingdoms.quake." + magnitude));
			}
		}
		return c;
	}

	/** One tick of a quake. */
	static void tick(ServerLevel level, Calamity c, RandomSource random, long now, boolean damage) {
		int m = magnitude(c);
		float s = c.strength();
		ServerPlayer shaken = null;
		for (ServerPlayer p : level.players()) {
			float here = c.over(p.getX(), p.getZ());
			if (here <= 0.1F || p.isSpectator()) {
				continue;
			}
			shaken = p;
			if (now % 6 == 0 && p.onGround() && !p.isCreative()) {
				double push = STAGGER * m * here;
				p.push((random.nextDouble() - 0.5) * push, 0.02 * m * here, (random.nextDouble() - 0.5) * push);
				p.syncVelocity = true;
			}
			if (damage) {
				int loose = Math.round(LOOSE[m] * here);
				for (int i = 0; i < loose; i++) {
					loosen(level, p.blockPosition(), random);
				}
			}
		}
		if (!damage || shaken == null || s < 0.8F) {
			return;
		}
		if (CRACKS[m] > 0 && random.nextFloat() < CRACKS[m] / (float)c.life) {
			crack(level, shaken.blockPosition(), random);
		}
		if (m >= 3 && c.age == c.life / 2 && random.nextFloat() < SINKHOLE) {
			double a = random.nextDouble() * Math.PI * 2.0, d = 8.0 + random.nextDouble() * 14.0;
			sinkhole(level, shaken.blockPosition().offset((int)(Math.cos(a) * d), 0, (int)(Math.sin(a) * d)), random);
		}
	}

	/** A block near {@code near} hanging over open air (a cave ceiling, an overhang, a roof) shakes free and falls. */
	private static void loosen(ServerLevel level, BlockPos near, RandomSource random) {
		BlockPos.MutableBlockPos at = near.offset(random.nextInt(LOOSE_RANGE * 2 + 1) - LOOSE_RANGE,
			random.nextInt(LOOSE_BELOW + LOOSE_ABOVE + 1) - LOOSE_BELOW, random.nextInt(LOOSE_RANGE * 2 + 1) - LOOSE_RANGE).mutable();
		if (!level.isLoaded(at)) {
			return;
		}
		// up through the open air to what's over it
		for (int i = 0; i < 10 && level.getBlockState(at).isAir(); i++) {
			at.move(0, 1, 0);
		}
		BlockState state = level.getBlockState(at);
		if (state.isAir() || !level.getBlockState(at.below()).isAir() || !loose(level, at, state)) {
			return;
		}
		FallingBlockEntity falling = FallingBlockEntity.fall(level, at.immutable(), state);
		falling.setHurtsEntities(FALL_HURT, FALL_HURT_MOST);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 6, 0.4, 0.1, 0.4, 0.05);
	}

	/** Whether {@code state} can shake loose: a plain, whole, not-too-hard block (no chests, no trees, no bedrock). */
	private static boolean loose(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.is(BlockTags.FEATURES_CANNOT_REPLACE)
			|| state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness <= LOOSE_HARDEST && state.isCollisionShapeFullBlock(level, pos);
	}

	/** Whether the ground's own (stone, dirt, sand, gravel) is at {@code pos}: what cracks and sinkholes open in. */
	private static boolean ground(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
			|| state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW) || state.is(BlockTags.REPLACEABLE);
	}

	/** A crack opens in the ground somewhere near {@code near}: a jagged line a block wide, a few deep. */
	private static void crack(ServerLevel level, BlockPos near, RandomSource random) {
		double a = random.nextDouble() * Math.PI * 2.0, d = 6.0 + random.nextDouble() * 18.0;
		double x = near.getX() + Math.cos(a) * d, z = near.getZ() + Math.sin(a) * d;
		double heading = random.nextDouble() * Math.PI * 2.0;
		int length = Mth.nextInt(random, CRACK_MIN, CRACK_MAX);
		for (int i = 0; i < length; i++) {
			heading += (random.nextDouble() - 0.5) * 0.8;
			x += Math.cos(heading);
			z += Math.sin(heading);
			BlockPos column = BlockPos.containing(x, near.getY(), z);
			if (!level.isLoaded(column)) {
				return;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
			// deepest in the middle, closing at the ends
			float middle = 1.0F - Math.abs(i - length / 2.0F) / (length / 2.0F);
			int depth = Math.max(1, Math.round(Mth.lerp(middle, 1, Mth.nextInt(random, CRACK_DEPTH_MIN, CRACK_DEPTH_MAX))));
			for (int y = 0; y < depth; y++) {
				BlockPos q = top.below(y);
				BlockState state = level.getBlockState(q);
				if (!ground(state)) {
					break;
				}
				level.destroyBlock(q, false);
			}
			// a plant on the crack's lip falls in
			if (level.getBlockState(top.above()).is(BlockTags.REPLACEABLE)) {
				level.destroyBlock(top.above(), false);
			}
		}
		level.playSound(null, BlockPos.containing(x, near.getY(), z), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5F, 0.4F);
	}

	/** The ground at {@code center} gives way: a round pit, its sides crumbling in. */
	private static void sinkhole(ServerLevel level, BlockPos center, RandomSource random) {
		if (!level.isLoaded(center)) {
			return;
		}
		int radius = Mth.nextInt(random, SINK_RADIUS_MIN, SINK_RADIUS_MAX), depth = Mth.nextInt(random, SINK_DEPTH_MIN, SINK_DEPTH_MAX);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > radius + 0.3) {
					continue;
				}
				// a bowl: deepest in the middle
				int down = (int)Math.round(depth * (1.0 - (r / (radius + 1.0)) * (r / (radius + 1.0))));
				BlockPos column = top.offset(dx, 0, dz);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
				for (int y = ground + 1; y >= top.getY() - down; y--) {
					BlockPos q = new BlockPos(column.getX(), y, column.getZ());
					BlockState state = level.getBlockState(q);
					if (state.isAir() || !ground(state) && !loose(level, q, state)) {
						continue;
					}
					level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
				// a little rubble on the floor
				if (random.nextInt(3) == 0) {
					BlockPos floor = new BlockPos(column.getX(), top.getY() - down, column.getZ());
					if (level.getBlockState(floor).isAir()) {
						level.setBlock(floor, Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
		level.playSound(null, top, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.3F);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 30, radius, 1.0, radius, 0.02);
	}
}
