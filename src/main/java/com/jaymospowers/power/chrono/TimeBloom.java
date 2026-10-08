package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Speeds up time in a sphere around where it was cast: extra random ticks (crops, saplings, cane, berries...) and
 * extra ticks for every block entity that has a ticker (furnaces, smokers, campfires, brewing stands, and modded
 * machines such as Cooking for Blockheads ovens, through the block's own {@link EntityBlock#getTicker}).
 *
 * <p>Both kinds of work are capped per server tick, and the area is rescanned once a second, so it can't lag.
 */
final class TimeBloom {
	static final int RADIUS = 5;
	static final int DURATION = 100;
	/** Chance per candidate per tick of an extra random tick (vanilla is ~3/4096 per block per tick). */
	private static final float RANDOM_TICK_CHANCE = 0.3F;
	private static final int MAX_RANDOM_TICKS_PER_TICK = 64;
	/** Extra ticks per block entity per server tick: 3 means it runs at 4x speed. */
	private static final int EXTRA_BLOCK_ENTITY_TICKS = 3;
	private static final int MAX_BLOCK_ENTITY_TICKS_PER_TICK = 60;
	private static final int RESCAN_INTERVAL = 20;
	private static final DustParticleOptions BLOOM_DUST = new DustParticleOptions(0xC8E05A, 1.0F);
	private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(0xF2C14E, 0.9F);

	/** Block entity types that misbehaved (threw) when ticked from here; never ticked by a bloom again. */
	private static final Set<BlockEntityType<?>> BROKEN = new HashSet<>();

	final ServerLevel level;
	final BlockPos center;
	int ticksLeft = DURATION;
	private int age;
	private final List<BlockPos> randomTickers = new ArrayList<>();
	private final List<BlockPos> blockEntities = new ArrayList<>();

	TimeBloom(ServerLevel level, BlockPos center) {
		this.level = level;
		this.center = center;
	}

	void start() {
		Vec3 c = Vec3.atCenterOf(center);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 30, RADIUS * 0.5, 0.6, RADIUS * 0.5, 0.0);
		level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y + 0.5, c.z, 20, 1.0, 0.5, 1.0, 0.5);
		scan();
	}

	/** One tick. Returns false once the bloom is over. */
	boolean tick() {
		age++;
		if (--ticksLeft <= 0) {
			Vec3 c = Vec3.atCenterOf(center);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
			return false;
		}
		if (age % RESCAN_INTERVAL == 0) {
			scan();
		}
		randomTicks();
		blockEntityTicks();
		ambience();
		return true;
	}

	private void scan() {
		randomTickers.clear();
		blockEntities.clear();
		int r2 = RADIUS * RADIUS;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
			if (pos.distSqr(center) > r2 || !level.shouldTickBlocksAt(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isRandomlyTicking() && acceptsRandomTick(state)) {
				randomTickers.add(pos.immutable());
			}
			if (state.hasBlockEntity() && state.getBlock() instanceof EntityBlock) {
				BlockEntity be = level.getBlockEntity(pos);
				if (be != null && acceleratable(be)) {
					blockEntities.add(pos.immutable());
				}
			}
		}
		// share the per-tick budget fairly when the area is crowded
		Collections.shuffle(blockEntities, new Random(level.getRandom().nextLong()));
	}

	/** Leaves out random ticks that would only melt, spread fire, or age builds: speed up life, not decay. */
	private static boolean acceptsRandomTick(BlockState state) {
		return !(state.getBlock() instanceof LiquidBlock)
			&& !(state.getBlock() instanceof IceBlock)
			&& !(state.getBlock() instanceof SnowLayerBlock)
			&& !(state.getBlock() instanceof WeatheringCopper);
	}

	/** No spawners, gateways, vaults or moving pistons: speeding those up would be an exploit or a glitch. */
	private static boolean acceleratable(BlockEntity be) {
		return !(be instanceof SpawnerBlockEntity) && !(be instanceof TrialSpawnerBlockEntity) && !(be instanceof VaultBlockEntity)
			&& !(be instanceof PistonMovingBlockEntity) && !(be instanceof TheEndGatewayBlockEntity)
			&& !(be instanceof CreakingHeartBlockEntity) && !(be instanceof CommandBlockEntity) && !(be instanceof StructureBlockEntity)
			&& !BROKEN.contains(be.getType());
	}

	private void randomTicks() {
		RandomSource random = level.getRandom();
		int budget = MAX_RANDOM_TICKS_PER_TICK;
		int n = randomTickers.size();
		if (n == 0) {
			return;
		}
		// start at a random offset so a capped budget still spreads over the whole list
		int start = random.nextInt(n);
		for (int k = 0; k < n && budget > 0; k++) {
			if (random.nextFloat() >= RANDOM_TICK_CHANCE) {
				continue;
			}
			BlockPos pos = randomTickers.get((start + k) % n);
			BlockState state = level.getBlockState(pos);
			if (!state.isRandomlyTicking() || !acceptsRandomTick(state)) {
				continue;
			}
			budget--;
			state.randomTick(level, pos, random);
			if (level.getBlockState(pos) != state) {
				// it grew (or otherwise changed): a little green-gold puff
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
				if (random.nextInt(3) == 0) {
					level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.0);
				}
			}
		}
	}

	private void blockEntityTicks() {
		int budget = MAX_BLOCK_ENTITY_TICKS_PER_TICK;
		for (int i = 0; i < blockEntities.size() && budget > 0; i++) {
			BlockPos pos = blockEntities.get(i);
			for (int t = 0; t < EXTRA_BLOCK_ENTITY_TICKS && budget > 0; t++) {
				budget--;
				if (!tickBlockEntity(pos)) {
					break;
				}
			}
			if ((age + i) % 10 == 0) {
				level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 2, 0.25, 0.05, 0.25, 0.0);
				level.sendParticles(GOLD_DUST, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.45, 0.45, 0.45, 0.0);
			}
		}
	}

	/** One extra tick of the block entity at {@code pos}, the way {@code LevelChunk} ticks it. False if it can't tick. */
	private boolean tickBlockEntity(BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null || be.isRemoved() || !level.shouldTickBlocksAt(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!be.getType().isValid(state) || !acceleratable(be)) {
			return false;
		}
		try {
			return tick(level, pos, state, be);
		} catch (RuntimeException e) {
			BROKEN.add(be.getType());
			JaymosPowers.LOGGER.warn("Time Bloom: {} at {} failed an extra tick; it won't be accelerated again", be.getType(), pos, e);
			return false;
		}
	}

	private static <T extends BlockEntity> boolean tick(Level level, BlockPos pos, BlockState state, T be) {
		if (!(state.getBlock() instanceof EntityBlock block)) {
			return false;
		}
		@SuppressWarnings("unchecked")
		BlockEntityType<T> type = (BlockEntityType<T>) be.getType();
		BlockEntityTicker<T> ticker = block.getTicker(level, state, type);
		if (ticker == null) {
			return false;
		}
		ticker.tick(level, pos, state, be);
		return true;
	}

	private void ambience() {
		RandomSource random = level.getRandom();
		if (age % 3 == 0) {
			for (int i = 0; i < 4; i++) {
				double x = center.getX() + 0.5 + (random.nextDouble() * 2 - 1) * RADIUS;
				double z = center.getZ() + 0.5 + (random.nextDouble() * 2 - 1) * RADIUS;
				double y = center.getY() + random.nextDouble() * 2.0;
				level.sendParticles(i % 2 == 0 ? BLOOM_DUST : GOLD_DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (age % 5 == 0) {
			// a ring that marks the edge, turning like a clock hand
			double a0 = age * 0.05;
			for (int h = 0; h < 12; h++) {
				double a = a0 + h / 12.0 * Math.PI * 2.0;
				level.sendParticles(h % 3 == 0 ? ParticleTypes.WAX_ON : ParticleTypes.COMPOSTER,
					center.getX() + 0.5 + Math.sin(a) * RADIUS, center.getY() + 0.15, center.getZ() + 0.5 - Math.cos(a) * RADIUS,
					1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (age % 20 == 0) {
			Vec3 c = Vec3.atCenterOf(center);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F + random.nextFloat() * 0.6F);
		}
	}
}
