package com.jaymoskingdoms;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Andy (2026-11-26, a clip of Haldale): "broken pathing on these poorly generated buildings." Haldale's save showed why.
 * Its town centre is Epic Structures' great square (its {@code plains_meeting_point_3}), drawn as an overgrown ruin: a
 * tower whose rooms and stairwell are part filled with leaves and cobwebs, oak trunks either side of its door. On top of
 * that, trees the world grew after the village was placed (oak wood the square's template doesn't hold, most likely Just
 * Better Forest's, which the game's {@code minecraft:template} feature pastes in checking only the block under the trunk)
 * stand in the alley beside it. Villagers gathered at the bell were boxed in there, or ran into the gaps and stuck.
 * <p>
 * The mends, all here:
 * <ul>
 * <li>As a village generates, the overgrowth in its rooms is tidied ({@link #tidy}): the cobwebs where folk would walk,
 * and the leaves at foot or head height under a roof.</li>
 * <li>As the world generates, a tree, log, bush or boulder pasted from a template, or one of our own grown trees
 * ({@link GrownTrees}), that would land in or right against anything built (a house, a wall, a road, a field: any block
 * that isn't the land's own, {@link #natural}) isn't set down at all ({@link #crowds}).</li>
 * <li>In a settlement already standing, its groundskeeper ({@link Groundwork}) clears what's in the way
 * ({@link #inTheWay}): the same overgrowth in its rooms, and the wild leaves at foot or head height in its streets.</li>
 * <li>A villager or one of our folk that can't get where it's going pushes through: what's in the way just ahead of where
 * its path gives out (or round where it stands stuck) is brushed aside ({@link #pushThrough}).</li>
 * </ul>
 * Never touched: leaves out in the open that someone placed (persistent ones: a hedge), wood of any kind (a trunk cut
 * would fell its tree, {@link LooseWood}), or anything else built.
 */
public final class Overgrowth {
	/** Room kept clear round a pasted template's box (blocks across): a trunk can't stand flush against a wall. */
	static final int MARGIN = 1;
	/** A grown tree's leaves reach this far past its wood (across and up) when its room is checked. */
	static final int LEAF_ROOM = 2;
	/** A stuck walker brushes aside the overgrowth this far (across) round the end of its path, or round itself. */
	static final int PUSH_REACH = 1;
	/** Solid overhead within this many blocks is a roof. */
	static final int ROOF_SCAN = 6;

	private Overgrowth() {
	}

	// ---- what's built and what's wild ---------------------------------------------------------------------------------

	/**
	 * The land's own: air, plants and water, the earth, sand, stone and ice it's made of, and its trees' wood and leaves
	 * (not stripped logs). Everything else is built: planks, stone bricks, glass, doors, fences, a road's path, a field's
	 * farmland.
	 */
	public static boolean natural(BlockState s) {
		// (plants, flowers and saplings, ours and other mods' too, have nothing to bump into; so have torches and signs,
		// but they hang on something built that is seen)
		if (s.isAir() || s.canBeReplaced() || s.getFluidState().is(net.minecraft.tags.FluidTags.WATER)
			|| s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
			return true;
		}
		if (s.is(BlockTags.LEAVES)) {
			// (some trees' leaves are set persistent; a hedge's are the house's hedge, but its house is seen anyway)
			return true;
		}
		if (s.is(BlockTags.LOGS)) {
			return !BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("stripped_");
		}
		if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND)) {
			return false;
		}
		String name = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SUBSTRATE_OVERWORLD)
			|| s.is(BlockTags.MOSS_REPLACEABLE) || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(BlockTags.TERRACOTTA)
			|| s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.MUD)
			|| s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.COBWEB) || s.is(Blocks.BEE_NEST) || s.is(Blocks.POINTED_DRIPSTONE) || s.is(Blocks.DRIPSTONE_BLOCK)
			|| name.endsWith("_ore") || name.endsWith("mushroom_block") || name.equals("mushroom_stem");
	}

	/** Leaves the world grew (not placed by anyone): {@code persistent} false. */
	static boolean wildLeaves(BlockState s) {
		return s.getBlock() instanceof LeavesBlock && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT);
	}

	/** Not air or a plant: something folk stand on (not a tree's wood or wild leaves, and not water). */
	static boolean floor(BlockGetter level, BlockPos at, BlockState s) {
		return !s.getCollisionShape(level, at).isEmpty() && !wildLeaves(s) && !(s.is(BlockTags.LOGS) && natural(s)) && s.getFluidState().isEmpty();
	}

	/** Solid overhead within {@link #ROOF_SCAN} (not leaves, not a tree's branch): indoors. */
	static boolean roofed(BlockGetter level, BlockPos at) {
		BlockPos.MutableBlockPos up = new BlockPos.MutableBlockPos();
		for (int dy = 1; dy <= ROOF_SCAN; dy++) {
			BlockState s = level.getBlockState(up.setWithOffset(at, 0, dy, 0));
			if (!s.is(BlockTags.LEAVES) && !(s.is(BlockTags.LOGS) && natural(s)) && !s.isAir() && !s.getCollisionShape(level, up).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Someone standing on a floor here would have {@code at} at their feet or their head. */
	static boolean footOrHead(BlockGetter level, BlockPos at) {
		BlockPos below = at.below();
		BlockState b = level.getBlockState(below);
		if (floor(level, below, b)) {
			return true;
		}
		if (!(b.isAir() || b.canBeReplaced() || b.is(BlockTags.LEAVES) || b.is(Blocks.COBWEB))) {
			return false;
		}
		BlockPos under = below.below();
		return floor(level, under, level.getBlockState(under));
	}

	/**
	 * {@code s} at {@code at} is overgrowth in folk's way: a cobweb, leaves indoors ({@link #roofed}), or ({@code wild})
	 * wild leaves anywhere, where someone standing on a floor would have their feet or head.
	 */
	public static boolean inTheWay(BlockGetter level, BlockPos at, BlockState s, boolean wild) {
		boolean growth = s.is(Blocks.COBWEB) || s.getBlock() instanceof LeavesBlock && (wild && wildLeaves(s) || roofed(level, at));
		return growth && footOrHead(level, at);
	}

	// ---- as the world generates ---------------------------------------------------------------------------------------

	/**
	 * While the world generates ({@code level} is a generating region): anything built in {@code box}, or within
	 * {@link #MARGIN} round it, so whatever would be set down there would crowd or cut into it. A column whose chunk the
	 * region doesn't hold counts as clear. Outside generation (a sapling growing) nothing is checked: false.
	 */
	public static boolean crowds(LevelAccessor level, BoundingBox box) {
		if (!(level instanceof WorldGenRegion region)) {
			return false;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int minY = Math.max(box.minY(), region.getMinY()), maxY = Math.min(box.maxY(), region.getMaxY());
		for (int x = box.minX() - MARGIN; x <= box.maxX() + MARGIN; x++) {
			for (int z = box.minZ() - MARGIN; z <= box.maxZ() + MARGIN; z++) {
				if (!region.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				for (int y = minY; y <= maxY; y++) {
					if (!natural(region.getBlockState(at.set(x, y, z)))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * As a village's pieces are placed in a chunk ({@code StructureStartMixin}): the overgrowth in its rooms that would be
	 * in folk's way ({@link #inTheWay}, not the wild leaves out in its streets, which are its own look) is taken out.
	 */
	public static void tidy(WorldGenLevel level, Structure structure, List<StructurePiece> pieces, BoundingBox chunk) {
		if (structure.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
			return;
		}
		Holder<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(structure);
		if (!Seats.village(holder) && !holder.unwrapKey().map(k -> k.identifier().getPath().contains("village")).orElse(false)) {
			return;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (StructurePiece piece : pieces) {
			BoundingBox box = piece.getBoundingBox();
			if (!box.intersects(chunk)) {
				continue;
			}
			for (int x = Math.max(box.minX(), chunk.minX()); x <= Math.min(box.maxX(), chunk.maxX()); x++) {
				for (int z = Math.max(box.minZ(), chunk.minZ()); z <= Math.min(box.maxZ(), chunk.maxZ()); z++) {
					for (int y = box.minY(); y <= box.maxY(); y++) {
						BlockState s = level.getBlockState(at.set(x, y, z));
						if ((s.is(Blocks.COBWEB) || s.is(BlockTags.LEAVES)) && inTheWay(level, at, s, false)) {
							level.setBlock(at, air, Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
	}

	// ---- a stuck walker -----------------------------------------------------------------------------------------------

	/**
	 * {@code mob} (a villager or one of our folk) can't get where it's going: the overgrowth in its way ({@link #inTheWay})
	 * round where its path gives out (or round where it stands, with no path) are brushed aside, so the next
	 * path can go through. True if anything was.
	 */
	public static boolean pushThrough(ServerLevel level, Mob mob, Path path) {
		BlockPos end = path != null && path.getNodeCount() > 0 ? path.getEndNode().asBlockPos() : mob.blockPosition();
		boolean any = brushAround(level, end);
		if (!end.equals(mob.blockPosition())) {
			any |= brushAround(level, mob.blockPosition());
		}
		return any;
	}

	/** Brushes aside the overgrowth in folk's way ({@link #inTheWay}) within {@link #PUSH_REACH} of {@code at}. */
	static boolean brushAround(ServerLevel level, BlockPos at) {
		boolean any = false;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-PUSH_REACH, 0, -PUSH_REACH), at.offset(PUSH_REACH, 1, PUSH_REACH))) {
			BlockState s = level.getBlockState(p);
			if (inTheWay(level, p, s, true)) {
				clear(level, p.immutable(), s);
				any = true;
			}
		}
		return any;
	}

	/** Takes {@code s} out at {@code at}, with its sound and a puff of its bits. Nothing drops. */
	static void clear(ServerLevel level, BlockPos at, BlockState s) {
		level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		SoundType sound = s.getSoundType();
		level.playSound(null, at, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
	}
}
