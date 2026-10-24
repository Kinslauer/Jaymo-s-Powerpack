package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Los Perrito, a lifetime after the end: the first time each of the city's chunks loads in {@link Future#FUTURE}, it
 * decays. Most windows are smashed out, the lights are dead (lamps unlit, light blocks gone dark, lanterns and torches
 * gone), lawns have died back to dirt, flowers to dead bushes, moss creeps over the roads, vines hang down the walls,
 * cobwebs gather under ceilings, and the whole island turns to the {@code jaymospowers:future_ruins} biome (ashen sky,
 * dead grass, murky water). Chunks are done a few per tick, seeded by their position, and marked so it happens once.
 */
public final class FutureRuins {
	public static final ResourceKey<Biome> RUINS = ResourceKey.create(Registries.BIOME, JaymosPowers.id("future_ruins"));
	/** Set on a chunk once it has decayed. */
	public static final AttachmentType<Boolean> RUINED = AttachmentRegistry.create(JaymosPowers.id("future_ruined"),
		builder -> builder.persistent(Codec.BOOL));

	/** Chunks decayed per tick. */
	static final int CHUNKS_PER_TICK = 2;
	/** Chances, per block, of: a pane or glass block smashed out; a lawn gone to dirt (or podzol); a flower dead. */
	static final float PANE_BROKEN = 0.65F, GLASS_BROKEN = 0.45F, LAWN_DEAD = 0.35F, LAWN_PODZOL = 0.12F, FLOWER_DEAD = 0.6F;
	/** Moss over open hard ground; a vine started on an open wall (it hangs down up to VINE_DROP); a cobweb under a roof. */
	static final float ROAD_MOSS = 0.035F, WALL_VINE = 0.012F, COBWEB = 0.012F;
	static final int VINE_DROP = 6;
	/** Block updates without neighbour shape checks, so vines and carpets hold where they're put. */
	static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();
	private static final Set<ChunkPos> QUEUED = new HashSet<>();

	private FutureRuins() {
	}

	static void clear() {
		QUEUE.clear();
		QUEUED.clear();
	}

	static boolean onIsland(ChunkPos pos) {
		return pos.getMaxBlockX() >= LosPerrito.MIN_X && pos.getMinBlockX() <= LosPerrito.MAX_X
			&& pos.getMaxBlockZ() >= LosPerrito.MIN_Z && pos.getMinBlockZ() <= LosPerrito.MAX_Z;
	}

	static void onLoad(ServerLevel level, LevelChunk chunk, boolean generated) {
		if (level.dimension() != Future.FUTURE || Boolean.TRUE.equals(chunk.getAttached(RUINED))) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		if (!FutureCities.LOADED && (generated || !onIsland(pos))) {
			// open sea beyond the map: nothing to ruin (with Lost Cities, its streets decay like the island's)
			chunk.setAttached(RUINED, true);
			return;
		}
		if (QUEUED.add(pos)) {
			QUEUE.add(pos);
		}
	}

	static void tick(MinecraftServer server) {
		if (QUEUE.isEmpty()) {
			return;
		}
		ServerLevel level = server.getLevel(Future.FUTURE);
		if (level == null) {
			clear();
			return;
		}
		for (int i = 0; i < CHUNKS_PER_TICK && !QUEUE.isEmpty(); i++) {
			ChunkPos pos = QUEUE.poll();
			QUEUED.remove(pos);
			if (level.getChunkSource().getChunkNow(pos.x(), pos.z()) instanceof LevelChunk chunk && !Boolean.TRUE.equals(chunk.getAttached(RUINED))) {
				ruin(level, chunk);
			}
		}
	}

	static void ruin(ServerLevel level, LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		RandomSource random = RandomSource.create(pos.pack() * 31L + 0x5EEDL);
		LevelChunkSection[] sections = chunk.getSections();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int baseX = pos.getMinBlockX(), baseZ = pos.getMinBlockZ();
		for (int index = sections.length - 1; index >= 0; index--) {
			LevelChunkSection section = sections[index];
			if (section == null || section.hasOnlyAir()) {
				continue;
			}
			int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
			for (int y = 15; y >= 0; y--) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (state.isAir()) {
							continue;
						}
						at.set(baseX + x, baseY + y, baseZ + z);
						decay(level, chunk, at, state, random);
					}
				}
			}
		}
		// the sky over the ruins: ash, dead grass, murky water
		Holder<Biome> ruins = level.registryAccess().lookupOrThrow(Registries.BIOME).get(RUINS).orElse(null);
		if (ruins != null) {
			chunk.fillBiomesFromNoise((qx, qy, qz) -> ruins);
			level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
		}
		chunk.setAttached(RUINED, true);
		chunk.markUnsaved();
	}

	private static void decay(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos at, BlockState state, RandomSource random) {
		if (state.is(ConventionalBlockTags.GLASS_PANES)) {
			if (random.nextFloat() < PANE_BROKEN) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
			}
			return;
		}
		if (state.is(ConventionalBlockTags.GLASS_BLOCKS)) {
			if (random.nextFloat() < GLASS_BROKEN) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
			}
			return;
		}
		if (state.is(Blocks.REDSTONE_LAMP)) {
			if (state.getValue(RedstoneLampBlock.LIT)) {
				level.setBlock(at, state.setValue(RedstoneLampBlock.LIT, false), FLAGS);
			}
			return;
		}
		if (state.getLightEmission() > 0 && state.getFluidState().isEmpty() && !state.is(BlockTags.FIRE) && !state.hasBlockEntity()
			&& !state.is(Blocks.MAGMA_BLOCK)) {
			// the power's been off for a lifetime: light blocks go dark, small lights are gone
			BlockState dead = state.isCollisionShapeFullBlock(level, at) ? Blocks.REDSTONE_LAMP.defaultBlockState() : Blocks.AIR.defaultBlockState();
			level.setBlock(at, dead, FLAGS);
			return;
		}
		boolean openAbove = at.getY() >= chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX() & 15, at.getZ() & 15) - 1;
		if (state.is(Blocks.GRASS_BLOCK)) {
			float roll = random.nextFloat();
			if (roll < LAWN_PODZOL) {
				level.setBlock(at, Blocks.PODZOL.defaultBlockState(), FLAGS);
			} else if (roll < LAWN_DEAD) {
				level.setBlock(at, Blocks.COARSE_DIRT.defaultBlockState(), FLAGS);
			}
			return;
		}
		if (state.is(BlockTags.SMALL_FLOWERS)) {
			if (random.nextFloat() < FLOWER_DEAD && level.getBlockState(at.below()).is(BlockTags.DIRT)) {
				level.setBlock(at, Blocks.DEAD_BUSH.defaultBlockState(), FLAGS);
			}
			return;
		}
		if (!state.isCollisionShapeFullBlock(level, at)) {
			return;
		}
		BlockPos above = at.above();
		BlockState over = level.getBlockState(above);
		if (openAbove && over.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.DIRT) && random.nextFloat() < ROAD_MOSS) {
			level.setBlock(above, Blocks.MOSS_CARPET.defaultBlockState(), FLAGS);
		}
		if (!openAbove) {
			BlockPos below = at.below();
			if (level.getBlockState(below).isAir() && random.nextFloat() < COBWEB) {
				level.setBlock(below, Blocks.COBWEB.defaultBlockState(), FLAGS);
			}
		}
		if (random.nextFloat() < WALL_VINE && !state.is(BlockTags.LEAVES)) {
			Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockPos.MutableBlockPos hang = at.relative(side).mutable();
			BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(side.getOpposite()), true);
			int drop = 1 + random.nextInt(VINE_DROP);
			for (int i = 0; i < drop && level.getBlockState(hang).isAir()
				&& level.getBlockState(hang.relative(side.getOpposite())).isFaceSturdy(level, hang.relative(side.getOpposite()), side); i++) {
				level.setBlock(hang, vine, FLAGS);
				hang.move(Direction.DOWN);
			}
		}
	}
}
