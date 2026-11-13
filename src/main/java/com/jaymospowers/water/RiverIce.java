package com.jaymospowers.water;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rivers freeze over in the cold, and ice floes drift on them (Andy's TFC Hardrock picks, the Dynamic Waters row; our own
 * code). Where a river's biome is cold enough to snow (Kitchen's seasons chill it in winter, so a temperate river freezes
 * in the winter months and thaws in spring) its surface water turns to ice, growing in from the banks and from the ice
 * already there; on a hard night the whole width closes. Now and then, where the water is open all round, a loose floe
 * forms instead. A floe drifts downstream ({@link RiverRun}) a block every {@link #FLOE_EVERY} ticks, carrying whatever
 * stands on it, until it jams against the bank or other ice, and melts away in the thaw. In a thaw the ice that closed a
 * river opens again. Only the ice of rivers is touched, near players.
 */
public final class RiverIce {
	/** Ticks between looks at the water round each player, and columns tried each time; how far (blocks). */
	static final int EVERY = 40, TRIES = 10, RANGE = 28;
	/** Ticks between a floe's drifts, and the chance a try on open water starts a floe. */
	static final int FLOE_EVERY = 30;
	static final float FLOE_CHANCE = 0.1F;
	/** Most floes drifting in one level. */
	static final int MOST_FLOES = 160;
	/** The push a floe gives what stands on it (blocks a tick). */
	static final double CARRY = 0.35;

	private static final Map<ResourceKey<Level>, LongOpenHashSet> FLOES = new HashMap<>();

	private RiverIce() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RiverIce::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FLOES.clear());
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		boolean look = tick % EVERY == 0, drift = tick % FLOE_EVERY == 0;
		if (!look && !drift) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (!level.dimensionType().hasSkyLight() || level.players().isEmpty()) {
				continue;
			}
			if (look) {
				for (ServerPlayer player : level.players()) {
					if (!player.isSpectator()) {
						around(level, player.blockPosition());
					}
				}
			}
			if (drift) {
				drift(level);
			}
		}
	}

	/** Whether the cold has hold of the water at {@code pos}. */
	static boolean cold(ServerLevel level, BlockPos pos) {
		Biome biome = level.getBiome(pos).value();
		return biome.coldEnoughToSnow(pos, level.getSeaLevel());
	}

	/** The top of the water in the column {@code x}, {@code z}, or null if it isn't open river. */
	private static BlockPos surface(ServerLevel level, int x, int z) {
		if (!level.hasChunkAt(new BlockPos(x, level.getSeaLevel(), z))) {
			return null;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z)).below();
		return RiverRun.river(level, top) && level.canSeeSky(top.above()) ? top : null;
	}

	private static void around(ServerLevel level, BlockPos centre) {
		RandomSource random = level.getRandom();
		boolean hard = !level.isBrightOutside();
		for (int i = 0; i < TRIES; i++) {
			BlockPos top = surface(level, centre.getX() + random.nextInt(RANGE * 2 + 1) - RANGE, centre.getZ() + random.nextInt(RANGE * 2 + 1) - RANGE);
			if (top == null) {
				continue;
			}
			BlockState state = level.getBlockState(top);
			boolean cold = cold(level, top);
			if (state.is(Blocks.WATER) && state.getFluidState().isSource() && level.getBlockState(top.above()).isAir()) {
				if (cold) {
					freeze(level, top, hard, random);
				}
			} else if (state.is(Blocks.ICE) && !cold && random.nextInt(3) == 0) {
				thaw(level, top);
			}
		}
	}

	/** Ice grows in from a bank or from ice; on a hard night it closes over open water too; sometimes a floe forms. */
	private static void freeze(ServerLevel level, BlockPos top, boolean hard, RandomSource random) {
		int shore = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState side = level.getBlockState(top.relative(d));
			if (side.is(Blocks.ICE) || !side.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && side.canOcclude()) {
				shore++;
			}
		}
		if (shore == 0) {
			LongOpenHashSet floes = FLOES.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet());
			if (random.nextFloat() < FLOE_CHANCE && floes.size() < MOST_FLOES && Math.abs(RiverRun.downstream(level, top).x)
				+ Math.abs(RiverRun.downstream(level, top).z) > 0.1) {
				level.setBlock(top, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
				floes.add(top.asLong());
				level.playSound(null, top, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.4F, 0.6F);
			} else if (hard && random.nextInt(3) == 0) {
				level.setBlock(top, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
			}
			return;
		}
		if (random.nextInt(shore >= 2 ? 1 : 3) == 0) {
			level.setBlock(top, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
			if (random.nextInt(6) == 0) {
				level.playSound(null, top, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.3F, 0.5F);
			}
		}
	}

	private static void thaw(ServerLevel level, BlockPos pos) {
		level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
		level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.0, 0.3, 0.1);
		FLOES.getOrDefault(level.dimension(), new LongOpenHashSet()).remove(pos.asLong());
	}

	/** Every floe in the level goes a block downstream if the water ahead is open. */
	private static void drift(ServerLevel level) {
		LongOpenHashSet floes = FLOES.get(level.dimension());
		if (floes == null || floes.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		LongArrayList all = new LongArrayList(floes);
		for (long packed : all) {
			BlockPos from = BlockPos.of(packed);
			floes.remove(packed);
			if (!level.hasChunkAt(from) || !level.getBlockState(from).is(Blocks.ICE)) {
				continue;
			}
			if (!cold(level, from)) {
				thaw(level, from);
				continue;
			}
			Vec3 heading = RiverRun.downstream(level, from);
			if (heading.lengthSqr() < 1.0E-4) {
				continue;
			}
			Direction dir = Direction.getApproximateNearest(heading.x + (random.nextDouble() - 0.5) * 0.4, 0.0,
				heading.z + (random.nextDouble() - 0.5) * 0.4);
			BlockPos to = from.relative(dir);
			BlockState ahead = level.getBlockState(to);
			if (!level.hasChunkAt(to) || !ahead.is(Blocks.WATER) || !level.getBlockState(to.above()).isAir()) {
				// jammed against the bank or other ice: it stays and joins the sheet
				continue;
			}
			// what stands on it rides along
			AABB deck = new AABB(from).move(0.0, 1.0, 0.0).inflate(0.0, 0.5, 0.0);
			for (Entity e : level.getEntities((Entity) null, deck, x -> !x.isSpectator())) {
				e.push(dir.getStepX() * CARRY, 0.0, dir.getStepZ() * CARRY);
				e.needsSync = true;
			}
			level.setBlock(from, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(to, Blocks.ICE.defaultBlockState(), Block.UPDATE_ALL);
			floes.add(to.asLong());
			if (random.nextInt(8) == 0) {
				level.playSound(null, to, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.4F, 0.5F + random.nextFloat() * 0.3F);
			}
		}
	}
}
