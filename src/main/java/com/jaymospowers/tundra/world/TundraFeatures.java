package com.jaymospowers.tundra.world;

import com.jaymospowers.ground.GroundRocks;
import com.jaymospowers.ground.LooseStonesBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;

/**
 * What stands on the tundra's rock and in the lichen woodland ({@link TundraWorld}): spills of loose stones, the banded iron inside
 * the greenstone belts, lichen patches, shrubs, yellow moss, white ice and dwarfed spruce. Each is a feature with
 * nothing to configure; how often it is placed is in {@code worldgen/placed_feature/tundra_*.json}.
 */
public final class TundraFeatures {
	// ---- tuning ----
	/** A spill of stones: piles in it (least, most), how far they lie from where it is placed, and how many spills in this many are greenstone (basalt) instead of deepslate. */
	public static final int BOULDER_PILES_MIN = 4, BOULDER_PILES_MAX = 8, BOULDER_SPREAD = 4, BOULDER_GREENSTONE_ONE_IN = 4;
	/** A banded vein: its radius, the ore's share of the belt rock inside it (in 10), and how often its heart is raw iron blocks. */
	public static final int VEIN_RADIUS = 3, VEIN_ORE_SHARE = 6, VEIN_RAW_ONE_IN = 3;
	/** A lichen patch: how far it reaches, how many tries, and how many of those sprout (the rest is plain lichen for grazers). */
	public static final int LICHEN_REACH = 4, LICHEN_TRIES = 14, LICHEN_SPROUTED = 2;
	/** A shrub patch: reach and tries. */
	public static final int SHRUB_REACH = 5, SHRUB_TRIES = 12;
	/** A yellow moss patch: radius from 2 to this. */
	public static final int MOSS_RADIUS = 4;
	/** A dwarfed spruce stands this tall, from the lower to the higher (blocks of trunk). */
	public static final int DWARF_MIN = 2, DWARF_MAX = 4;

	private TundraFeatures() {
	}

	/** Bare rock the lichen and the boulders belong on. */
	private static boolean rock(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.GRAVEL);
	}

	/** Ground a shrub or a spruce can root in. */
	private static boolean soil(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(Blocks.GRAVEL) || state.is(TundraWorld.block("yellow_moss", Blocks.MOSS_BLOCK));
	}

	private static BlockPos surface(WorldGenLevel level, BlockPos at) {
		return new BlockPos(at.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, at.getX(), at.getZ()), at.getZ());
	}

	/**
	 * A spill of loose stones on the bare ground: the pack's own throwable rocks ({@link GroundRocks}), deepslate and now
	 * and then greenstone (basalt), a heap of four in the middle and lesser piles round it. A punch or a right click picks
	 * a stone up, and a thrown one hurts (ThrownRocks).
	 */
	public record Boulder() implements Feature {
		public static final MapCodec<Boulder> CODEC = MapCodec.unit(Boulder::new);

		@Override
		public MapCodec<Boulder> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			boolean greenstone = random.nextInt(BOULDER_GREENSTONE_ONE_IN) == 0;
			int piles = BOULDER_PILES_MIN + random.nextInt(BOULDER_PILES_MAX - BOULDER_PILES_MIN + 1);
			int placed = 0;
			for (int i = 0; i < piles; i++) {
				BlockPos at = i == 0 ? origin : origin.offset(random.nextInt(BOULDER_SPREAD * 2 + 1) - BOULDER_SPREAD, 0,
					random.nextInt(BOULDER_SPREAD * 2 + 1) - BOULDER_SPREAD);
				BlockPos top = surface(level, at);
				BlockState ground = level.getBlockState(top.below());
				if (!level.getBlockState(top).isAir() || !rock(ground) && !soil(ground)) {
					continue;
				}
				// (the odd pile of the other stone, so a spill isn't all one)
				GroundRocks.Variant kind = greenstone == (random.nextInt(5) != 0) ? GroundRocks.Variant.BASALT : GroundRocks.Variant.DEEPSLATE;
				BlockState rocks = GroundRocks.ROCKS.get(kind).defaultBlockState()
					.setValue(LooseStonesBlock.ROCKS, i == 0 ? 4 : 1 + random.nextInt(4)).setValue(LooseStonesBlock.WATERLOGGED, false);
				if (rocks.canSurvive(level, top)) {
					level.setBlock(top, rocks, Block.UPDATE_CLIENTS);
					placed++;
				}
			}
			return placed > 0;
		}
	}

	/** Banded iron ore inside a greenstone belt: a pocket of the belt's basalt turns to ore, sometimes round a heart of raw iron. */
	public record BandedVein() implements Feature {
		public static final MapCodec<BandedVein> CODEC = MapCodec.unit(BandedVein::new);

		@Override
		public MapCodec<BandedVein> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.getBlockState(origin).is(Blocks.SMOOTH_BASALT)) {
				return false;
			}
			BlockState ore = TundraWorld.block("banded_iron_ore", Blocks.IRON_ORE).defaultBlockState();
			boolean heart = random.nextInt(VEIN_RAW_ONE_IN) == 0;
			int radius = 2 + random.nextInt(VEIN_RADIUS - 1);
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dy = -radius; dy <= radius; dy++) {
					for (int dz = -radius; dz <= radius; dz++) {
						int d = dx * dx + dy * dy + dz * dz;
						BlockPos pos = origin.offset(dx, dy, dz);
						if (d > radius * radius || !level.getBlockState(pos).is(Blocks.SMOOTH_BASALT)) {
							continue;
						}
						if (heart && d <= 1) {
							level.setBlock(pos, Blocks.RAW_IRON_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (random.nextInt(10) < VEIN_ORE_SHARE) {
							level.setBlock(pos, ore, Block.UPDATE_CLIENTS);
						}
					}
				}
			}
			return true;
		}
	}

	/** Glow lichen lying on bare rock under the sky: mostly plain lichen, with a few sprouted ones that will work the rock. */
	public record LichenPatch() implements Feature {
		public static final MapCodec<LichenPatch> CODEC = MapCodec.unit(LichenPatch::new);

		@Override
		public MapCodec<LichenPatch> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			Block sprouted = TundraWorld.block("sprouted_glow_lichen", Blocks.GLOW_LICHEN);
			int placed = 0;
			for (int i = 0; i < LICHEN_TRIES; i++) {
				BlockPos pos = surface(level, origin.offset(random.nextInt(LICHEN_REACH * 2 + 1) - LICHEN_REACH, 0, random.nextInt(LICHEN_REACH * 2 + 1) - LICHEN_REACH));
				if (!level.getBlockState(pos).isAir() || !rock(level.getBlockState(pos.below()))) {
					continue;
				}
				BlockState lichen = onFloor(placed < LICHEN_SPROUTED ? sprouted : Blocks.GLOW_LICHEN);
				if (lichen != null) {
					level.setBlock(pos, lichen, Block.UPDATE_CLIENTS);
					placed++;
				}
			}
			return placed > 0;
		}

		/** The block clinging to the floor only, or null if it has no floor face. */
		private static BlockState onFloor(Block block) {
			BlockState state = block.defaultBlockState();
			var down = MultifaceBlock.getFaceProperty(Direction.DOWN);
			if (!state.hasProperty(down)) {
				return null;
			}
			for (Direction face : Direction.values()) {
				var side = MultifaceBlock.getFaceProperty(face);
				if (state.hasProperty(side)) {
					state = state.setValue(side, face == Direction.DOWN);
				}
			}
			return state;
		}
	}

	/** A scatter of the three tundra shrubs: the stunted red one on the poorest ground, orange and yellow where there is soil. */
	public record Shrubs() implements Feature {
		public static final MapCodec<Shrubs> CODEC = MapCodec.unit(Shrubs::new);

		@Override
		public MapCodec<Shrubs> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			Block red = TundraWorld.block("stunted_red_shrub", Blocks.DEAD_BUSH), orange = TundraWorld.block("orange_shrub", Blocks.DEAD_BUSH),
				yellow = TundraWorld.block("yellow_shrub", Blocks.DEAD_BUSH);
			// (a patch leans to one colour, as a real stand does)
			Block lean = random.nextBoolean() ? orange : yellow;
			int placed = 0;
			for (int i = 0; i < SHRUB_TRIES; i++) {
				BlockPos pos = surface(level, origin.offset(random.nextInt(SHRUB_REACH * 2 + 1) - SHRUB_REACH, 0, random.nextInt(SHRUB_REACH * 2 + 1) - SHRUB_REACH));
				BlockState ground = level.getBlockState(pos.below());
				if (!level.getBlockState(pos).isAir() || !soil(ground)) {
					continue;
				}
				Block pick = ground.is(Blocks.GRAVEL) || ground.is(Blocks.COARSE_DIRT) ? red : random.nextInt(4) == 0 ? (lean == orange ? yellow : orange) : lean;
				BlockState shrub = pick.defaultBlockState();
				if (shrub.canSurvive(level, pos)) {
					level.setBlock(pos, shrub, Block.UPDATE_CLIENTS);
					placed++;
				}
			}
			return placed > 0;
		}
	}

	/** A round patch of yellow moss over the woodland's grass and dirt. */
	public record MossPatch() implements Feature {
		public static final MapCodec<MossPatch> CODEC = MapCodec.unit(MossPatch::new);

		@Override
		public MapCodec<MossPatch> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockState moss = TundraWorld.block("yellow_moss", Blocks.MOSS_BLOCK).defaultBlockState();
			int radius = 2 + random.nextInt(MOSS_RADIUS - 1);
			boolean placed = false;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dz * dz > radius * radius - random.nextInt(3)) {
						continue;
					}
					BlockPos pos = surface(level, origin.offset(dx, 0, dz)).below();
					if (level.getBlockState(pos).is(BlockTags.DIRT) && level.getBlockState(pos.above()).isAir()) {
						level.setBlock(pos, moss, Block.UPDATE_CLIENTS);
						placed = true;
					}
				}
			}
			return placed;
		}
	}

	/** The tundra's frozen water: every open water surface in the chunk freezes to white ice. Nothing snows here. */
	public record WhiteIce() implements Feature {
		public static final MapCodec<WhiteIce> CODEC = MapCodec.unit(WhiteIce::new);

		@Override
		public MapCodec<WhiteIce> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockState ice = TundraWorld.block("white_ice", Blocks.ICE).defaultBlockState();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int dx = 0; dx < 16; dx++) {
				for (int dz = 0; dz < 16; dz++) {
					int x = origin.getX() + dx, z = origin.getZ() + dz;
					pos.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
					if (level.getBiome(pos).is(TundraWorld.TUNDRA) && level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).is(Fluids.WATER)) {
						level.setBlock(pos, ice, Block.UPDATE_CLIENTS);
					}
				}
			}
			return true;
		}
	}

	/** A dwarfed spruce: a short thin trunk (log below, post above) under a small cone of spruce needles. */
	public record DwarfSpruce() implements Feature {
		public static final MapCodec<DwarfSpruce> CODEC = MapCodec.unit(DwarfSpruce::new);

		@Override
		public MapCodec<DwarfSpruce> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			return build(level, origin, random);
		}

		/** A sapling's growth: the sapling goes, and comes back if there was no room for the tree. */
		public static void grow(ServerLevel level, BlockPos pos, RandomSource random, BlockState sapling) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
			if (!build(level, pos, random)) {
				level.setBlock(pos, sapling, Block.UPDATE_NONE);
			}
		}

		private static boolean build(LevelAccessor level, BlockPos base, RandomSource random) {
			if (!soil(level.getBlockState(base.below()))) {
				return false;
			}
			int height = DWARF_MIN + random.nextInt(DWARF_MAX - DWARF_MIN + 1);
			for (int y = 0; y <= height; y++) {
				BlockState there = level.getBlockState(base.above(y));
				if (!there.isAir() && !there.canBeReplaced() && !there.is(BlockTags.LEAVES)) {
					return false;
				}
			}
			BlockState log = TundraWorld.block("dwarfed_spruce_log", Blocks.SPRUCE_LOG).defaultBlockState();
			BlockState post = TundraWorld.block("dwarfed_spruce_post", Blocks.SPRUCE_FENCE).defaultBlockState();
			int logs = height >= 3 ? 1 + random.nextInt(2) : 1;
			for (int y = 0; y < height; y++) {
				level.setBlock(base.above(y), y < logs ? log : post, Block.UPDATE_ALL);
			}
			// The needles: a tip over the trunk, then rings widening downward, never touching the ground.
			leaf(level, base.above(height), 1);
			for (int y = height - 1, ring = 0; y >= 1; y--, ring++) {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					leaf(level, base.above(y).relative(side), 1);
					if (ring % 2 == 1 || (ring > 0 && random.nextBoolean())) {
						leaf(level, base.above(y).relative(side).relative(side.getClockWise()), 2);
					}
				}
			}
			return true;
		}

		private static void leaf(LevelAccessor level, BlockPos pos, int distance) {
			BlockState there = level.getBlockState(pos);
			if (there.isAir() || there.canBeReplaced()) {
				level.setBlock(pos, Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, distance)
					.setValue(BlockStateProperties.PERSISTENT, false), Block.UPDATE_ALL);
			}
		}
	}
}
