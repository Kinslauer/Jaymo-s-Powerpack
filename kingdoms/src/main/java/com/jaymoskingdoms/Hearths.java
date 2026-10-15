package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * (0.7.54) Village fires get a stone hearth and a chimney when the village generates. Villages (vanilla's and the
 * ones Epic Structures swaps in) put campfires and netherrack fires inside wooden houses, which burned down under our
 * fire rules (a burning corpse lights what it lies by, like lava). Every campfire, fire or lava pool inside a village
 * piece gets:
 * <ul>
 * <li>a hearth: whatever catches (planks, logs, wool, hay, leaves...) within {@link #HEARTH} blocks of it, and in the
 * floor within {@link #APRON}, becomes stone brick of the same shape (stairs stay stairs, slabs stay slabs); carpets
 * near it are taken up;</li>
 * <li>if there's a roof over it, a chimney: a stone brick hood from two blocks above the fire up through the roof
 * (floors and roof over the fire turned to stone), standing {@link #STACK} blocks proud of the roof, open at the top.</li>
 * </ul>
 * Beds, doors, chests and anything else with a block entity or two halves are left alone. Run from
 * {@code StructureStartMixin} after a chunk's pieces are placed; since a fire near a chunk edge needs blocks in the
 * next chunk, each chunk looks for fires a hearth's width round it and builds into its neighbours too, so whichever of
 * them is placed last leaves it whole.
 */
public final class Hearths {
	/** Blocks round a fire that are made safe; the floor apron's reach; how far up a roof is looked for. */
	static final int HEARTH = 1, APRON = 2, ROOF_REACH = 20;
	/** How many blocks a chimney stands above the roof. */
	static final int STACK = 2;

	private Hearths() {
	}

	public static void build(WorldGenLevel level, Structure structure, List<StructurePiece> pieces, BoundingBox chunk) {
		if (structure.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
			return;
		}
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(structure);
		if (!isVillage(holder)) {
			return;
		}
		int reach = Math.max(HEARTH, APRON) + 1;
		BoundingBox around = chunk.inflatedBy(reach);
		List<BlockPos> fires = new ArrayList<>();
		for (StructurePiece piece : pieces) {
			BoundingBox box = piece.getBoundingBox();
			if (!box.intersects(around)) {
				continue;
			}
			for (int x = Math.max(box.minX(), around.minX()); x <= Math.min(box.maxX(), around.maxX()); x++) {
				for (int z = Math.max(box.minZ(), around.minZ()); z <= Math.min(box.maxZ(), around.maxZ()); z++) {
					for (int y = box.minY(); y <= box.maxY(); y++) {
						BlockPos at = new BlockPos(x, y, z);
						if (level.hasChunkAt(at) && isFire(level.getBlockState(at))) {
							fires.add(at);
						}
					}
				}
			}
		}
		for (BlockPos fire : fires) {
			hearth(level, fire);
		}
	}

	/** A village structure: {@link Seats#village}, or any mod's structure with "village" in its id. */
	private static boolean isVillage(Holder<Structure> holder) {
		return Seats.village(holder) || holder.unwrapKey().map(k -> k.identifier().getPath().contains("village")).orElse(false);
	}

	/** A campfire (lit or not), fire, or a lava source. */
	static boolean isFire(BlockState state) {
		return state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.FIRE) || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && state.getFluidState().isSource();
	}

	/** Builds the hearth round {@code fire}, and a chimney over it if it's under a roof. */
	static void hearth(WorldGenLevel level, BlockPos fire) {
		// the hearth: what catches beside it becomes stone; the floor round it too
		for (int dx = -APRON; dx <= APRON; dx++) {
			for (int dz = -APRON; dz <= APRON; dz++) {
				boolean close = Math.abs(dx) <= HEARTH && Math.abs(dz) <= HEARTH;
				for (int dy = -1; dy <= 1; dy++) {
					if (dx == 0 && dz == 0 && dy == 0) {
						continue;
					}
					BlockPos at = fire.offset(dx, dy, dz);
					if (!level.hasChunkAt(at)) {
						continue;
					}
					BlockState state = level.getBlockState(at);
					if (close || dy == -1) {
						BlockState safe = fireproof(state);
						if (safe != null) {
							level.setBlock(at, safe, Block.UPDATE_CLIENTS);
						}
					} else if (dy == 0 && state.is(BlockTags.WOOL_CARPETS)) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		// the chimney: only for a fire with a roof over it (a chimney-top campfire has open sky)
		int top = roofTop(level, fire);
		if (top < 0) {
			return;
		}
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		for (int y = fire.getY() + 2; y <= top + STACK; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos at = new BlockPos(fire.getX() + dx, y, fire.getZ() + dz);
					if (!level.hasChunkAt(at)) {
						continue;
					}
					BlockState state = level.getBlockState(at);
					boolean open = state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
					if (dx == 0 && dz == 0) {
						// the flue: what catches over the fire (a floor, the roof) becomes stone; open above the roof
						if (y <= top && !open && state.ignitedByLava() && plain(state)) {
							level.setBlock(at, brick, Block.UPDATE_CLIENTS);
						}
					} else if (open || y <= top && state.ignitedByLava() && plain(state)) {
						// the hood up to the roof (in the air of the room, and through the wood), then the stack
						level.setBlock(at, brick, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/** The top of the roof over {@code fire} (the highest block in its column within {@link #ROOF_REACH}), or -1 for open sky. */
	private static int roofTop(WorldGenLevel level, BlockPos fire) {
		for (int y = fire.getY() + ROOF_REACH; y > fire.getY() + 1; y--) {
			BlockPos at = new BlockPos(fire.getX(), y, fire.getZ());
			BlockState state = level.getBlockState(at);
			if (!state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)) {
				// a fire too deep under a tall building (a tower) is left without one
				BlockState above = level.getBlockState(at.above());
				return above.isAir() || above.canBeReplaced() ? y : -1;
			}
		}
		return -1;
	}

	/** A block with no block entity and no second half (not a bed, door, chest or tall plant). */
	private static boolean plain(BlockState state) {
		return !state.hasBlockEntity() && !state.hasProperty(BlockStateProperties.BED_PART)
			&& !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
	}

	/** What a block that catches becomes beside a fire (stone brick of its shape), null to leave it as it is. */
	private static BlockState fireproof(BlockState state) {
		if (!state.ignitedByLava() || !plain(state) || isFire(state)) {
			return null;
		}
		if (state.getBlock() instanceof StairBlock) {
			return Blocks.STONE_BRICK_STAIRS.withPropertiesOf(state);
		}
		if (state.getBlock() instanceof SlabBlock) {
			return Blocks.STONE_BRICK_SLAB.withPropertiesOf(state);
		}
		if (state.is(BlockTags.WOOL_CARPETS) || state.canBeReplaced()) {
			return Blocks.AIR.defaultBlockState();
		}
		if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS)) {
			return Blocks.STONE_BRICK_WALL.defaultBlockState();
		}
		if (state.is(BlockTags.WOODEN_PRESSURE_PLATES)) {
			return Blocks.STONE_PRESSURE_PLATE.defaultBlockState();
		}
		if (state.is(BlockTags.WOODEN_BUTTONS)) {
			return Blocks.STONE_BUTTON.withPropertiesOf(state);
		}
		if (state.isSolid() && state.getBlock().defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
			return state.is(BlockTags.LOGS) ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
		}
		return null;
	}
}
