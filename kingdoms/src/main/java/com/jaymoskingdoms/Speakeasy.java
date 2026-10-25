package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.210) The blocks of the {@link Underworld}: a realm's speakeasy, and its cells.
 *
 * <p>The den is dug under open ground in the town ({@link #site}): a spruce hatch in the grass with a hay bale by it, a
 * ladder down a stone-lined shaft, and a low room {@link #DEPTH} below, plank-floored and stone-walled under a dark ceiling,
 * lit by hanging lanterns: a bar of barrels with the fence penned behind it, a red runner before it, a dice table with
 * a green cloth and stools, crates, cobwebs in the corners. Everything is measured from the hatch.
 *
 * <p>The cells ({@link #cell}) are a sealed stone pit under the realm's seat: no door, a lantern, a bale to sleep on.
 * Digging out is breaking out ({@link Busts}).
 */
final class Speakeasy {
	/** The den's floor is this far under the hatch; its room this wide (x), deep (z) and high inside. */
	static final int DEPTH = 9, WIDTH = 9, LENGTH = 7, HEIGHT = 4;
	/** Sites are looked for in rings this far from the seat, this far apart, at this many points round each. */
	static final int RING_MIN = 14, RING_MAX = 50, RING_STEP = 3, RING_POINTS = 16;
	/** At least this much earth over the den's ceiling, everywhere over it. */
	static final int COVER = 2;
	/** How far up over the hatch must be open (or only leaves). */
	static final int ROOF_CHECK = 24;
	/** The cells: this far under the seat, tried again this much deeper if the ground there is taken (so many times). */
	static final int CELL_DEPTH = 12, CELL_STEP = 6, CELL_TRIES = 3;
	/** The share of a wall's stones that are mossy, and cracked. */
	static final float MOSSY = 0.2F, CRACKED = 0.12F;

	private Speakeasy() {
	}

	// ---- measures ------------------------------------------------------------------------------------------------------

	private static int floor(BlockPos hatch) {
		return hatch.getY() - DEPTH;
	}

	/** The room's inside (where the den's people are, and a player in it has found it). */
	static AABB room(BlockPos hatch) {
		int fy = floor(hatch) + 1;
		return new AABB(hatch.getX() - 2, fy, hatch.getZ(), hatch.getX() - 2 + WIDTH, fy + HEIGHT, hatch.getZ() + LENGTH);
	}

	/** Where the fence stands, behind his bar. */
	static Vec3 fenceSpot(BlockPos hatch) {
		return new Vec3(hatch.getX() + 5.5, floor(hatch) + 1, hatch.getZ() + 3.5);
	}

	/** Where the dice-thrower stands, at the end of his table. */
	static Vec3 diceSpot(BlockPos hatch) {
		return new Vec3(hatch.getX() + 1.5, floor(hatch) + 1, hatch.getZ() + 4.5);
	}

	// ---- the site --------------------------------------------------------------------------------------------------------

	/** Open, natural, level ground in {@code f}'s town with room under it for the den (loaded only), or null. */
	static @Nullable BlockPos site(ServerLevel level, Faction f) {
		BlockPos seat = f.seat();
		double turn = level.getRandom().nextDouble() * Math.PI * 2.0;
		for (int r = RING_MIN; r <= RING_MAX; r += RING_STEP) {
			for (int k = 0; k < RING_POINTS; k++) {
				double angle = turn + k * Math.PI * 2.0 / RING_POINTS;
				int x = seat.getX() + (int)Math.round(Math.cos(angle) * r), z = seat.getZ() + (int)Math.round(Math.sin(angle) * r);
				BlockPos hatch = ground(level, x, z);
				if (hatch != null && fits(level, f, hatch)) {
					return hatch;
				}
			}
		}
		return null;
	}

	/** The top block of the ground at {@code x, z} (loaded only), or null. */
	private static @Nullable BlockPos ground(ServerLevel level, int x, int z) {
		if (!level.isLoaded(new BlockPos(x, 0, z))) {
			return null;
		}
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
	}

	private static boolean fits(ServerLevel level, Faction f, BlockPos hatch) {
		BlockPos above = hatch.above();
		BlockState ground = level.getBlockState(hatch);
		if (!(ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND)) || !roofless(level, above) || !level.getFluidState(above).isEmpty()
			|| !(level.getBlockState(above).isAir() || level.getBlockState(above).canBeReplaced())) {
			return false;
		}
		Faction law = Theft.lawAt(level, above);
		if (law == null || law.id() != f.id()) {
			return false;
		}
		int fy = floor(hatch), ceiling = fy + HEIGHT + 1;
		if (fy <= level.getMinY() + 2) {
			return false;
		}
		int x0 = hatch.getX() - 3, x1 = hatch.getX() + WIDTH - 2, z0 = hatch.getZ() - 1, z1 = hatch.getZ() + LENGTH;
		// earth over all of it: the den mustn't open out of a hillside
		for (int x = x0; x <= x1; x += (x1 - x0) / 2) {
			for (int z = z0; z <= z1; z += (z1 - z0) / 2) {
				BlockPos top = ground(level, x, z);
				if (top == null || top.getY() < ceiling + COVER) {
					return false;
				}
			}
		}
		// nothing anyone keeps down there (a chest, a spawner, a mine's works), no water or lava
		for (BlockPos p : BlockPos.betweenClosed(x0, fy, z0, x1, ceiling, z1)) {
			if (level.getBlockEntity(p) != null || !level.getFluidState(p).isEmpty()) {
				return false;
			}
		}
		for (int y = ceiling; y < hatch.getY(); y++) {
			BlockPos p = new BlockPos(hatch.getX(), y, hatch.getZ());
			if (level.getBlockEntity(p) != null || !level.getFluidState(p).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Nothing built over {@code pos} (a tree's leaves may hang over it; a roof, a floor or a trunk may not). */
	private static boolean roofless(ServerLevel level, BlockPos pos) {
		for (int dy = 0; dy < ROOF_CHECK; dy++) {
			BlockState s = level.getBlockState(pos.above(dy));
			if (!s.isAir() && !s.is(BlockTags.LEAVES) && !s.canBeReplaced()) {
				return false;
			}
		}
		return true;
	}

	// ---- building ------------------------------------------------------------------------------------------------------

	/** Digs and fits out the den under {@code hatch}. */
	static void build(ServerLevel level, BlockPos hatch) {
		RandomSource random = level.getRandom();
		int hx = hatch.getX(), hz = hatch.getZ(), gy = hatch.getY(), fy = floor(hatch), top = fy + HEIGHT + 1;
		// the shell: plank floor, stone walls, a dark ceiling, the inside cleared
		for (int x = hx - 3; x <= hx + WIDTH - 2; x++) {
			for (int z = hz - 1; z <= hz + LENGTH; z++) {
				for (int y = fy; y <= top; y++) {
					boolean edge = x == hx - 3 || x == hx + WIDTH - 2 || z == hz - 1 || z == hz + LENGTH || y == fy || y == top;
					BlockState s = !edge ? Blocks.AIR.defaultBlockState() : y == fy ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: y == top ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : stone(random);
					set(level, x, y, z, s);
				}
			}
		}
		// the shaft: stone round it, a ladder down it (against its north side), the hatch over it
		for (int y = top + 1; y < gy; y++) {
			set(level, hx - 1, y, hz, stone(random));
			set(level, hx + 1, y, hz, stone(random));
			set(level, hx, y, hz - 1, stone(random));
			set(level, hx, y, hz + 1, stone(random));
		}
		for (int y = fy + 1; y < gy; y++) {
			set(level, hx, y, hz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		set(level, hx, gy, hz, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
			.setValue(TrapDoorBlock.OPEN, false).setValue(TrapDoorBlock.FACING, Direction.NORTH));
		// a bale by the hatch, as if it were only somebody's fodder
		BlockPos bale = new BlockPos(hx + 1, gy + 1, hz);
		if (level.getBlockState(bale).canBeReplaced() && level.getBlockState(bale.below()).isFaceSturdy(level, bale.below(), Direction.UP)) {
			set(level, bale.getX(), bale.getY(), bale.getZ(), Blocks.HAY_BLOCK.defaultBlockState());
		}
		int y1 = fy + 1, ceil = fy + HEIGHT;
		// the bar: barrels across the room, the fence penned behind it; shelves of barrels at its ends; a red runner before it
		BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
		for (int z = hz; z < hz + LENGTH; z++) {
			set(level, hx + 4, y1, z, barrel);
		}
		for (int y = y1; y <= y1 + 1; y++) {
			set(level, hx + 6, y, hz, barrel);
			set(level, hx + 6, y, hz + LENGTH - 1, barrel);
		}
		BlockState runner = colored("red_carpet");
		for (int z = hz + 1; z < hz + LENGTH - 1; z++) {
			set(level, hx + 3, y1, z, runner);
		}
		// the dice table: a green cloth on two posts, a stool either side
		for (int x = hx - 1; x <= hx; x++) {
			set(level, x, y1, hz + 4, Blocks.SPRUCE_FENCE.defaultBlockState());
			set(level, x, y1 + 1, hz + 4, colored("green_carpet"));
			set(level, x, y1, hz + 5, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
			set(level, x, y1, hz + 3, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
		}
		// a side table with a lamp on it
		set(level, hx + 2, y1, hz + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
		set(level, hx + 2, y1 + 1, hz + 1, Blocks.LANTERN.defaultBlockState());
		// crates in the far corner
		set(level, hx - 2, y1, hz + LENGTH - 1, barrel);
		set(level, hx - 2, y1 + 1, hz + LENGTH - 1, barrel);
		set(level, hx - 1, y1, hz + LENGTH - 1, barrel);
		set(level, hx - 2, y1, hz + LENGTH - 2, barrel);
		// lanterns hung from the ceiling, cobwebs in the corners
		BlockState hung = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		set(level, hx + 5, ceil, hz + 3, hung);
		set(level, hx, ceil, hz + 4, hung);
		set(level, hx - 1, ceil, hz + 1, hung);
		set(level, hx - 2, ceil, hz, Blocks.COBWEB.defaultBlockState());
		set(level, hx + 6, ceil, hz + LENGTH - 1, Blocks.COBWEB.defaultBlockState());
	}

	/**
	 * {@code f}'s cells under its seat, dug if they aren't yet ({@code known}: where they were dug, or null): the spot a
	 * prisoner stands in.
	 */
	static BlockPos cell(ServerLevel level, Faction f, @Nullable BlockPos known) {
		if (known != null && level.getBlockState(known.below()).is(Blocks.COBBLESTONE)) {
			return known;
		}
		BlockPos seat = f.seat();
		level.getChunk(seat);
		RandomSource random = level.getRandom();
		BlockPos at = null;
		for (int i = 0; i < CELL_TRIES; i++) {
			BlockPos c = seat.below(CELL_DEPTH + i * CELL_STEP);
			at = c;
			boolean clear = true;
			for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 3, 2))) {
				if (level.getBlockEntity(p) != null || !level.getFluidState(p).isEmpty()) {
					clear = false;
					break;
				}
			}
			if (clear) {
				break;
			}
		}
		int cx = at.getX(), cy = at.getY(), cz = at.getZ();
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				for (int y = cy - 1; y <= cy + 3; y++) {
					if (level.getBlockEntity(new BlockPos(x, y, z)) != null) {
						continue;
					}
					boolean edge = x == cx - 2 || x == cx + 2 || z == cz - 2 || z == cz + 2 || y == cy - 1 || y == cy + 3;
					set(level, x, y, z, !edge ? Blocks.AIR.defaultBlockState() : y == cy - 1 ? Blocks.COBBLESTONE.defaultBlockState() : stone(random));
				}
			}
		}
		set(level, cx - 1, cy, cz - 1, Blocks.HAY_BLOCK.defaultBlockState());
		set(level, cx + 1, cy + 2, cz + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		return at;
	}

	/** Where a prisoner is let out: open ground by the seat. */
	static Vec3 release(ServerLevel level, Faction f) {
		BlockPos seat = f.seat();
		int x = seat.getX() + 3, z = seat.getZ() + 3;
		level.getChunk(new BlockPos(x, 0, z));
		return new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
	}

	private static BlockState stone(RandomSource random) {
		float r = random.nextFloat();
		return (r < MOSSY ? Blocks.MOSSY_STONE_BRICKS : r < MOSSY + CRACKED ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
	}

	/** A coloured block by name (they have no {@code Blocks} fields in 26.3). */
	private static BlockState colored(String name) {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
		return b.defaultBlockState();
	}

	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
	}
}
