package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * One of the gone-before ({@link Remains}) lying where it fell: the placed feature gives the spot (a surface, a riverbed, a cave
 * height) and this finds the floor under it and checks the place suits {@link #kind} (snow or dirt for the frozen, a wall
 * behind a cliff's, an open drop beside a sky island's, a ceiling over dripstone's), never inside a village.
 */
public record RemainsFeature(String kind) implements Feature {
	public static final MapCodec<RemainsFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.STRING.fieldOf("kind").forGetter(RemainsFeature::kind)).apply(i, RemainsFeature::new));
	/** How far down a floor is looked for. */
	static final int REACH = 24;
	/** How tall a wall makes a cliff, and how far the dripstone's ceiling may be (blocks). */
	static final int CLIFF_WALL = 5, CEILING_MAX = 8;

	@Override
	public MapCodec<RemainsFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		int k = java.util.Arrays.asList(Remains.KINDS).indexOf(this.kind);
		if (k < 0) {
			return false;
		}
		BlockPos at = floor(level, origin, k == Remains.RIVER);
		if (at == null || village(level, at) || !suits(level, at, k, random)) {
			return false;
		}
		BlockState state = Remains.BLOCK.defaultBlockState().setValue(RemainsBlock.KIND, k).setValue(RemainsBlock.POSE, random.nextInt(3))
			.setValue(RemainsBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)).setValue(RemainsBlock.WATERLOGGED, k == Remains.RIVER);
		level.setBlock(at, state, Block.UPDATE_CLIENTS);
		return true;
	}

	/** The open block over a sturdy floor at or under {@code origin}; null if there is none in reach. */
	static BlockPos floor(WorldGenLevel level, BlockPos origin, boolean wet) {
		BlockPos.MutableBlockPos p = origin.mutable();
		for (int i = 0; i < REACH; i++) {
			BlockState here = level.getBlockState(p);
			boolean open = wet ? here.getFluidState().is(FluidTags.WATER) : (here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty();
			if (open && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
				return p.immutable();
			}
			p.move(Direction.DOWN);
		}
		return null;
	}

	/** Whether {@code at} is in a village's land (the vanilla ones and the pack's). */
	static boolean village(WorldGenLevel level, BlockPos at) {
		ChunkAccess chunk = level.getChunk(at);
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (Structure structure : chunk.getAllReferences().keySet()) {
			net.minecraft.core.Holder<Structure> holder = registry.wrapAsHolder(structure);
			if (holder.is(StructureTags.VILLAGE) || Seats.village(holder) || SharedPools.village(holder)) {
				return true;
			}
		}
		return false;
	}

	private static boolean solid(WorldGenLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced();
	}

	private static boolean suits(WorldGenLevel level, BlockPos at, int kind, RandomSource random) {
		BlockState below = level.getBlockState(at.below());
		switch (kind) {
			case Remains.CLIFF:
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (solid(level, at.relative(d).above(1)) && solid(level, at.relative(d).above(CLIFF_WALL))) {
						return true;
					}
				}
				return false;
			case Remains.DRIPSTONE:
				for (int dy = 3; dy <= CEILING_MAX; dy++) {
					if (solid(level, at.above(dy))) {
						level.setBlock(at.above(dy - 1), Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN), Block.UPDATE_CLIENTS);
						return true;
					}
				}
				return false;
			case Remains.CAVE:
				if (level.canSeeSky(at)) {
					return false;
				}
				for (int i = 0; i < 4; i++) {
					BlockPos web = at.offset(random.nextInt(3) - 1, random.nextInt(2), random.nextInt(3) - 1);
					if (!web.equals(at) && level.getBlockState(web).isAir()) {
						level.setBlock(web, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				return true;
			case Remains.SKY:
				for (Direction d : Direction.Plane.HORIZONTAL) {
					boolean drop = true;
					for (int dy = 1; dy <= 6 && drop; dy++) {
						drop = level.getBlockState(at.relative(d, 2).below(dy)).isAir();
					}
					if (drop) {
						return true;
					}
				}
				return false;
			case Remains.NETHER:
				return below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL);
			case Remains.END:
				return below.is(Blocks.END_STONE);
			case Remains.DESERT:
				return below.is(BlockTags.SAND) || below.is(BlockTags.TERRACOTTA) || below.is(Blocks.SANDSTONE) || below.is(Blocks.RED_SANDSTONE);
			default:
				return true;
		}
	}
}
