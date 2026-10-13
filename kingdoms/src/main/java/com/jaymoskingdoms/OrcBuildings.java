package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * (0.5.45) An orc war camp's buildings, all original designs: the warchief's <b>longhouse</b> (a blackstone footing,
 * dark oak walls on log posts, a steep roof of black hides, bone-and-skull posts at its door, red war banners; inside, a
 * red aisle to his throne of blackstone between bone pillars, his hoard behind it); hide <b>tents</b> on dark oak frames;
 * the <b>forge</b> (a blast furnace, an anvil, a lava trough, racks of chain); a <b>watchtower</b> for the rangers; the
 * <b>plunder pile</b> (the camp's {@link Storage}); a <b>fighting pit</b>; trophy racks and spike totems; a roasting
 * spit by the fire; and a stockade of dark oak round it all, its stakes sharpened, skulls on bone posts, gate towers
 * either side of the gate. A camp grows by its own hands ({@link OrcCamps}): each day it may raise the next of
 * {@link #GROWTH}.
 *
 * <p>Designs are {@link Blueprint}s facing north (their front on z = 0, y = 0 the first layer on the ground), placed with
 * {@link CampBuildings.Placer} like the camps'. Spots reuse the goblin village's plan, which is known to fit its ring.
 */
final class OrcBuildings {
	/** The stockade's radius round the fire, and how far (and how high) the ground is levelled and cleared. */
	static final int RING_RADIUS = 22, CLEAR_RADIUS = RING_RADIUS + 2, CLEAR_HEIGHT = 16;
	/** The stockade: logs this high, sharpened stakes on top, a bone post with a skull every {@code POST_EVERY} degrees; a walkway; the gate. */
	static final int WALL_HIGH = 6, POST_HIGH = WALL_HIGH + 2, WALK_HIGH = 3, GATE_HALF = 2, TOWER_HIGH = 10;
	static final double POST_EVERY = 24.0;
	/** The stockade goes up without reshaping its neighbours, so its stakes stay lone posts. */
	private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** How far round the fire its orcs wander. */
	static final int HOME_RADIUS = 18;

	private OrcBuildings() {
	}

	// ---- materials ---------------------------------------------------------------------------------------------------

	private static BlockState log() {
		return Blocks.DARK_OAK_LOG.defaultBlockState();
	}

	private static BlockState logAlong(Direction.Axis axis) {
		return Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
	}

	private static BlockState planks() {
		return Blocks.DARK_OAK_PLANKS.defaultBlockState();
	}

	private static BlockState fence() {
		return Blocks.DARK_OAK_FENCE.defaultBlockState();
	}

	private static BlockState slab() {
		return Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	private static BlockState wool(DyeColor colour) {
		return Blocks.WOOL.pick(colour).defaultBlockState();
	}

	private static BlockState bone() {
		return Blocks.BONE_BLOCK.defaultBlockState();
	}

	private static BlockState skull() {
		return Blocks.SKELETON_SKULL.defaultBlockState();
	}

	/** Footings: blackstone, its bricks and cracked bricks, picked by position so a design always looks the same. */
	private static BlockState footing(int x, int y, int z) {
		return switch (Math.floorMod(x * 73428767 ^ y * 912931 ^ z * 42317861, 5)) {
			case 0, 1 -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			case 2 -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			default -> Blocks.BLACKSTONE.defaultBlockState();
		};
	}

	private static BlockState banner(Direction facing) {
		return Blocks.WALL_BANNER.pick(DyeColor.RED).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
	}

	private static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	// ---- designs -----------------------------------------------------------------------------------------------------

	/**
	 * The warchief's longhouse (11 x 13): a blackstone footing, dark oak walls between log posts, a three-wide, three-high
	 * doorway between bone posts with skulls and red banners, a steep roof of black hides with a log ridge; inside, a red
	 * aisle to his throne (blackstone stairs between bone pillars crowned with skulls), his hoard chest behind it,
	 * braziers along the walls.
	 */
	static Blueprint longhouse() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int w = 10, d = 12;
		for (int x = 0; x <= w; x++) {
			for (int z = 0; z <= d; z++) {
				boolean edge = x == 0 || x == w || z == 0 || z == d;
				if (!edge) {
					continue;
				}
				boolean post = (x == 0 || x == w) && z % 4 == 0 || (z == 0 || z == d) && (x == 0 || x == w || x == 3 || x == 7);
				for (int y = 0; y <= 3; y++) {
					if (z == 0 && x >= 4 && x <= 6 && y <= 2) {
						// the doorway
						continue;
					}
					BlockState s = post ? log() : y == 0 ? footing(x, y, z) : planks();
					// slit windows high in the long walls
					if (!post && y == 2 && (x == 0 || x == w) && z % 4 == 2) {
						s = Blocks.IRON_BARS.defaultBlockState();
					}
					t.set(x, y, z, s);
				}
			}
		}
		// the roof: hides stepping in from both long walls to a log ridge, gables filled with planks
		for (int step = 0; step <= 5; step++) {
			int y = 4 + step;
			for (int z = -1; z <= d + 1; z++) {
				if (step < 5) {
					t.set(step, y, z, wool(DyeColor.BLACK));
					t.set(w - step, y, z, wool(DyeColor.BLACK));
				} else {
					t.set(5, y, z, logAlong(Direction.Axis.Z));
				}
			}
			for (int x = step + 1; x <= w - step - 1; x++) {
				t.set(x, y, 0, planks());
				t.set(x, y, d, planks());
			}
		}
		// the doorway's bone posts and skulls, banners on the front
		for (int x : new int[] {2, 8}) {
			t.set(x, 0, -1, bone());
			t.set(x, 1, -1, bone());
			t.set(x, 2, -1, bone());
			t.set(x, 3, -1, skull());
		}
		t.set(3, 3, -1, banner(Direction.NORTH));
		t.set(7, 3, -1, banner(Direction.NORTH));
		// inside: a plank floor, the aisle, the throne between bone pillars, the hoard, braziers
		for (int x = 1; x < w; x++) {
			for (int z = 1; z < d; z++) {
				t.set(x, -1, z, planks());
			}
		}
		BlockState carpet = Blocks.CARPET.pick(DyeColor.RED).defaultBlockState();
		for (int z = 1; z <= 8; z++) {
			t.set(5, 0, z, carpet);
		}
		t.set(4, 0, 10, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		t.set(6, 0, 10, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		t.set(5, 0, 10, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		for (int x : new int[] {3, 7}) {
			t.set(x, 0, 10, bone());
			t.set(x, 1, 10, bone());
			t.set(x, 2, 10, skull());
		}
		t.set(5, 1, 11, banner(Direction.NORTH));
		t.set(5, 0, 11, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		for (int z : new int[] {2, 6}) {
			t.set(1, 0, z, Blocks.CAMPFIRE.defaultBlockState());
			t.set(w - 1, 0, z, Blocks.CAMPFIRE.defaultBlockState());
		}
		t.set(1, 0, 4, Blocks.BARREL.defaultBlockState());
		t.set(w - 1, 0, 4, Blocks.BARREL.defaultBlockState());
		t.set(1, 0, 9, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(w - 1, 0, 9, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(5, 3, 5, lantern(true));
		return t.done("orc_longhouse");
	}

	/** Where the warchief stands, and his hoard (offsets in {@link #longhouse}). */
	static final BlockPos THRONE = new BlockPos(5, 0, 10), HOARD_CHEST = new BlockPos(5, 0, 11);

	/** A hide tent (5 x 5): dark oak corner posts, walls of black or brown hide, an A-frame roof, a doorway, hay to sleep on. */
	static Blueprint tent(boolean black) {
		CampBuildings.Draft t = new CampBuildings.Draft();
		BlockState hide = wool(black ? DyeColor.BLACK : DyeColor.BROWN), trim = wool(black ? DyeColor.BROWN : DyeColor.BLACK);
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				if (edge) {
					for (int y = 0; y <= 1; y++) {
						if (x == 2 && z == 0) {
							continue;
						}
						t.set(x, y, z, corner ? fence() : hide);
					}
				}
				// the roof: low along the sides, up to a ridge
				if (x == 0 || x == 4) {
					t.set(x, 2, z, hide);
				} else if (x == 1 || x == 3) {
					t.set(x, 3, z, hide);
				} else {
					t.set(x, 4, z, trim);
				}
			}
		}
		// the gable ends
		for (int x = 1; x <= 3; x++) {
			t.set(x, 2, 4, hide);
			if (x != 2) {
				t.set(x, 2, 0, hide);
			}
		}
		t.set(2, 3, 4, hide);
		t.set(2, 3, 0, hide);
		t.set(1, 0, 3, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(3, 0, 3, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(2, 0, 4, bone());
		return t.done(black ? "orc_tent_black" : "orc_tent_brown");
	}

	/** The forge (7 x 7): a blackstone floor and back wall, a blast furnace and anvil, a lava trough behind iron bars, chains hung with lanterns. */
	static Blueprint forge() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 6; z++) {
				t.set(x, -1, z, footing(x, -1, z));
			}
		}
		for (int x = 0; x <= 6; x++) {
			for (int y = 0; y <= 3; y++) {
				t.set(x, y, 6, x == 0 || x == 6 ? log() : footing(x, y, 6));
			}
			t.set(x, 4, 6, slab());
		}
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 3}, {6, 3}}) {
			for (int y = 0; y <= 3; y++) {
				t.set(c[0], y, c[1], log());
			}
		}
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 5; z++) {
				t.set(x, 4, z, slab());
			}
		}
		// the trough of lava behind bars, the furnace and anvil before it
		t.set(2, 0, 5, Blocks.LAVA_CAULDRON.defaultBlockState());
		t.set(3, 0, 5, Blocks.LAVA_CAULDRON.defaultBlockState());
		t.set(4, 0, 5, Blocks.LAVA_CAULDRON.defaultBlockState());
		for (int x = 2; x <= 4; x++) {
			t.set(x, 0, 4, Blocks.IRON_BARS.defaultBlockState());
		}
		t.set(1, 0, 5, Blocks.BLAST_FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
		t.set(5, 0, 5, Blocks.BLAST_FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
		t.set(3, 0, 2, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
		t.set(1, 0, 2, Blocks.GRINDSTONE.defaultBlockState());
		t.set(5, 0, 2, Blocks.SMITHING_TABLE.defaultBlockState());
		for (int x : new int[] {1, 5}) {
			t.set(x, 3, 3, Blocks.IRON_CHAIN.defaultBlockState());
			t.set(x, 2, 3, lantern(true));
		}
		return t.done("orc_forge");
	}

	/** Where the forge's smith stands (offset in {@link #forge}). */
	static final BlockPos FORGE_SPOT = new BlockPos(3, 0, 1);

	/** A watchtower (5 x 5, 10 high): dark oak stilts on blackstone, a ladder up a plank spine, a railed platform under a slab roof, a skull pole. */
	static Blueprint watchtower() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int[] c : new int[][] {{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
			t.set(c[0], 0, c[1], Blocks.BLACKSTONE.defaultBlockState());
			for (int y = 1; y <= 9; y++) {
				t.set(c[0], y, c[1], log());
			}
		}
		for (int y = 0; y <= 5; y++) {
			t.set(2, y, 4, planks());
			t.set(2, y, 3, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
		}
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				if (!corner && !(x == 2 && (z == 3 || z == 4))) {
					t.set(x, 6, z, planks());
				}
				if (!corner && (x == 0 || x == 4 || z == 0 || z == 4) && !(x == 2 && z == 4)) {
					t.set(x, 7, z, fence());
				}
				t.set(x, 10, z, slab());
			}
		}
		t.set(2, 7, 4, fence());
		t.set(2, 11, 2, skull());
		t.set(2, 9, 2, lantern(true));
		t.set(0, 7, 2, banner(Direction.WEST));
		return t.done("orc_watchtower");
	}

	/** Where a ranger keeps watch (offset in {@link #watchtower}). */
	static final BlockPos TOWER_SPOT = new BlockPos(1, 7, 1);

	/** The plunder pile (7 x 4): chests and barrels under a dark oak lean-to, a heap of bones, gravel and gold at its end. */
	static Blueprint plunderPile() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 6; x++) {
			for (int y = 0; y <= 2; y++) {
				t.set(x, y, 3, x == 0 || x == 6 ? log() : footing(x, y, 3));
			}
			for (int z = 0; z <= 2; z++) {
				t.set(x, 2, z, slab());
			}
		}
		for (int x : new int[] {0, 6}) {
			t.set(x, 0, 0, log());
			t.set(x, 1, 0, log());
		}
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
		for (int x = 1; x <= 5; x++) {
			t.set(x, 0, 2, x % 2 == 0 ? chest : Blocks.BARREL.defaultBlockState());
		}
		t.set(1, 1, 2, Blocks.BARREL.defaultBlockState());
		t.set(5, 1, 2, bone());
		t.set(1, 0, 0, Blocks.BONE_BLOCK.defaultBlockState());
		t.set(5, 0, 0, Blocks.GRAVEL.defaultBlockState());
		return t.done("orc_plunder_pile");
	}

	/** Where raiders bring their takings (offset in {@link #plunderPile}). */
	static final BlockPos PLUNDER_DEPOT = new BlockPos(3, 0, 1);

	/** A fighting pit (5 x 5): a ring of fence round a floor of coarse dirt, a gap to step in by, skulls on its corners. */
	static Blueprint pit() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				t.set(x, -1, z, (x + z) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				if (edge && !(x == 2 && z == 0)) {
					t.set(x, 0, z, corner ? bone() : fence());
				}
				if (corner) {
					t.set(x, 1, z, skull());
				}
			}
		}
		return t.done("orc_pit");
	}

	/** A trophy rack (5 x 3): a dark oak beam on posts hung with skulls, bone blocks and a banner. */
	static Blueprint trophyRack() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x : new int[] {0, 4}) {
			t.set(x, 0, 1, log());
			t.set(x, 1, 1, log());
		}
		for (int x = 0; x <= 4; x++) {
			t.set(x, 2, 1, logAlong(Direction.Axis.X));
		}
		for (int x = 1; x <= 3; x++) {
			t.set(x, 3, 1, x == 2 ? Blocks.WITHER_SKELETON_SKULL.defaultBlockState() : skull());
		}
		t.set(1, 0, 1, bone());
		t.set(3, 0, 1, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(2, 1, 0, banner(Direction.NORTH));
		return t.done("orc_trophy_rack");
	}

	/** A spike totem (1 x 1): a blackstone foot, a dark oak stake, a bone block, a skull. */
	static Blueprint totem() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		t.set(0, 0, 0, Blocks.BLACKSTONE.defaultBlockState());
		t.set(0, 1, 0, fence());
		t.set(0, 2, 0, bone());
		t.set(0, 3, 0, skull());
		return t.done("orc_totem");
	}

	/** The roasting spit (3 x 1): a fire between two posts, a barrel of scraps beside it. */
	static Blueprint spit() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		t.set(0, 0, 0, fence());
		t.set(1, 0, 0, Blocks.CAMPFIRE.defaultBlockState());
		t.set(2, 0, 0, fence());
		t.set(0, 1, 0, fence());
		t.set(2, 1, 0, fence());
		t.set(1, 1, 0, Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		return t.done("orc_spit");
	}

	// ---- the camp ----------------------------------------------------------------------------------------------------

	/** turns: 0 its front to the gate, 1 to the gate's right (a building on the left facing the fire), 3 to its left. */
	static final IllagerBuildings.Spot LONGHOUSE = new IllagerBuildings.Spot("orc_longhouse", OrcBuildings::longhouse, 0, 7, 0, 11);
	static final IllagerBuildings.Spot PLUNDER = new IllagerBuildings.Spot("orc_plunder_pile", OrcBuildings::plunderPile, 12, 0, 3, 7);
	static final IllagerBuildings.Spot FORGE = new IllagerBuildings.Spot("orc_forge", OrcBuildings::forge, -12, 2, 1, 7);
	static final IllagerBuildings.Spot WATCHTOWER = new IllagerBuildings.Spot("orc_watchtower", OrcBuildings::watchtower, -12, -13, 0, 5);
	static final IllagerBuildings.Spot SPIT = new IllagerBuildings.Spot("orc_spit", OrcBuildings::spit, 4, 2, 0, 3);
	static final IllagerBuildings.Spot PIT = new IllagerBuildings.Spot("orc_pit", OrcBuildings::pit, 8, -13, 0, 5);
	static final List<IllagerBuildings.Spot> START = List.of(
		new IllagerBuildings.Spot("orc_tent_black", () -> tent(true), 13, 9, 3, 5),
		new IllagerBuildings.Spot("orc_tent_brown", () -> tent(false), -11, 12, 1, 5),
		new IllagerBuildings.Spot("orc_trophy_rack", OrcBuildings::trophyRack, 8, 4, 0, 5),
		new IllagerBuildings.Spot("orc_totem", OrcBuildings::totem, -3, -6, 0, 1),
		new IllagerBuildings.Spot("orc_totem", OrcBuildings::totem, 3, -6, 0, 1));

	/** What a camp raises as it grows, in order (one a day at most: see {@link OrcCamps}). */
	static final List<IllagerBuildings.Spot> GROWTH = List.of(
		new IllagerBuildings.Spot("orc_tent_brown", () -> tent(false), -6, -16, 0, 5),
		new IllagerBuildings.Spot("orc_tent_black", () -> tent(true), 14, -6, 3, 5),
		new IllagerBuildings.Spot("orc_totem", OrcBuildings::totem, -8, -4, 0, 1),
		new IllagerBuildings.Spot("orc_trophy_rack", OrcBuildings::trophyRack, -6, 4, 0, 5),
		new IllagerBuildings.Spot("orc_tent_black", () -> tent(true), -16, -4, 1, 5),
		new IllagerBuildings.Spot("orc_totem", OrcBuildings::totem, 8, 15, 0, 1),
		new IllagerBuildings.Spot("orc_tent_brown", () -> tent(false), 6, -18, 0, 5));

	/** What a new camp's founding built: its fire (the seat), the plunder pile's containers and depot, the throne, the forge, the tower, the pit. */
	record Layout(BlockPos fire, List<BlockPos> storage, BlockPos depot, BlockPos throne, BlockPos forge, BlockPos tower, BlockPos pit) {
	}

	/** Builds a whole camp round {@code center} at {@code floor}, its gate facing {@code gate}. */
	static Layout build(ServerLevel level, BlockPos center, int floor, Direction gate) {
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		CampBuildings.Placer camp = new CampBuildings.Placer(fire, gate, 0);
		CampBuildings.level(level, fire, CLEAR_RADIUS, CLEAR_HEIGHT);
		ring(level, camp);
		// the war fire ringed with blackstone, a pair of war drums (note blocks on bone), a trodden path from the gate
		level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
		for (int[] b : new int[][] {{0, -2}, {-2, 0}, {2, 0}, {0, 2}}) {
			level.setBlock(camp.at(b[0], 0, b[1]), Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (int[] b : new int[][] {{-3, -3}, {3, -3}}) {
			level.setBlock(camp.at(b[0], 0, b[1]), bone(), Block.UPDATE_CLIENTS);
			level.setBlock(camp.at(b[0], 1, b[1]), Blocks.NOTE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (int z = -RING_RADIUS; z <= -3; z++) {
			for (int x = -1; x <= 1; x++) {
				BlockPos at = camp.at(x, -1, z);
				if (level.getBlockState(at).is(BlockTags.DIRT) || level.getBlockState(at).is(Blocks.GRASS_BLOCK)) {
					level.setBlock(at, (x + z & 1) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		List<BlockPos> storage = new ArrayList<>();
		place(level, LONGHOUSE, fire, gate);
		storage.addAll(place(level, PLUNDER, fire, gate));
		place(level, FORGE, fire, gate);
		place(level, WATCHTOWER, fire, gate);
		place(level, SPIT, fire, gate);
		place(level, PIT, fire, gate);
		for (IllagerBuildings.Spot s : START) {
			place(level, s, fire, gate);
		}
		CampBuildings.Placer hall = LONGHOUSE.placer(fire, gate);
		stockHoard(level, hall.at(HOARD_CHEST), level.getRandom());
		return new Layout(fire, storage, PLUNDER.placer(fire, gate).at(PLUNDER_DEPOT), hall.at(THRONE), FORGE.placer(fire, gate).at(FORGE_SPOT),
			WATCHTOWER.placer(fire, gate).at(TOWER_SPOT), PIT.placer(fire, gate).at(2, 0, 2));
	}

	/** Where the warchief of a camp built round {@code fire} (its gate facing {@code gate}) holds court. */
	static BlockPos throne(BlockPos fire, Direction gate) {
		return LONGHOUSE.placer(fire, gate).at(THRONE);
	}

	/**
	 * The stockade: upright dark oak logs {@link #WALL_HIGH} high, sharpened stakes along the top (lone fence posts), bone
	 * posts crowned with skulls rising above them, a slab walkway inside reached by ladders, and a gate under a log lintel
	 * between two towers.
	 */
	private static void ring(ServerLevel level, CampBuildings.Placer camp) {
		for (int x = -RING_RADIUS - 1; x <= RING_RADIUS + 1; x++) {
			for (int z = -RING_RADIUS - 1; z <= RING_RADIUS + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				boolean gate = z < 0 && Math.abs(x) <= GATE_HALF;
				if (d >= RING_RADIUS - 1.5 && d < RING_RADIUS - 0.5 && !gate) {
					BlockPos at = camp.at(x, WALK_HIGH, z);
					if (level.getBlockState(at).isAir()) {
						level.setBlock(at, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), QUIET);
					}
				}
				if (d < RING_RADIUS - 0.5 || d >= RING_RADIUS + 0.5) {
					continue;
				}
				if (gate) {
					level.setBlock(camp.at(x, WALL_HIGH - 1, z), camp.turn(logAlong(Direction.Axis.X)), QUIET);
					level.setBlock(camp.at(x, WALL_HIGH, z), x == 0 ? skull() : fence(), QUIET);
					continue;
				}
				double angle = Math.toDegrees(Math.atan2(z, x)) + 360.0;
				boolean post = angle % POST_EVERY < Math.toDegrees(1.0 / RING_RADIUS);
				level.setBlock(camp.at(x, 0, z), Blocks.BLACKSTONE.defaultBlockState(), QUIET);
				int high = post ? POST_HIGH : WALL_HIGH;
				for (int y = 1; y < high; y++) {
					level.setBlock(camp.at(x, y, z), post && y >= WALL_HIGH - 1 ? bone() : log(), QUIET);
				}
				if (post) {
					level.setBlock(camp.at(x, POST_HIGH, z), skull(), QUIET);
				} else {
					int stakes = (x + z & 1) == 0 ? 2 : 1;
					for (int y = 0; y < stakes; y++) {
						level.setBlock(camp.at(x, WALL_HIGH + y, z), fence(), QUIET);
					}
				}
			}
		}
		ladder(level, camp, RING_RADIUS - 1, 0, Direction.WEST);
		ladder(level, camp, -RING_RADIUS + 1, 0, Direction.EAST);
		ladder(level, camp, 0, RING_RADIUS - 1, Direction.NORTH);
		tower(level, camp, -GATE_HALF - 2);
		tower(level, camp, GATE_HALF + 2);
	}

	private static void ladder(ServerLevel level, CampBuildings.Placer camp, int x, int z, Direction facing) {
		BlockState ladder = camp.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
		for (int y = 0; y <= WALK_HIGH; y++) {
			level.setBlock(camp.at(x, y, z), ladder, QUIET);
		}
	}

	/** A gate tower (3 x 3, {@link #TOWER_HIGH} high) beside the gate at {@code x}: a blackstone foot, logs, a plank top with stakes, a skull, a red banner. */
	private static void tower(ServerLevel level, CampBuildings.Placer camp, int x) {
		int z = -RING_RADIUS + 1;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 0; y < TOWER_HIGH; y++) {
					level.setBlock(camp.at(x + dx, y, z + dz), y < 2 ? Blocks.BLACKSTONE.defaultBlockState() : log(), QUIET);
				}
				level.setBlock(camp.at(x + dx, TOWER_HIGH, z + dz), planks(), QUIET);
				if (dx != 0 && dz != 0) {
					level.setBlock(camp.at(x + dx, TOWER_HIGH + 1, z + dz), fence(), QUIET);
				}
			}
		}
		level.setBlock(camp.at(x, TOWER_HIGH + 1, z), skull(), QUIET);
		// a red war banner on the tower's outer face, toward whoever comes
		level.setBlock(camp.at(x, WALL_HIGH, z - 2), camp.turn(banner(Direction.NORTH)), QUIET);
	}

	/** The top of a gate tower, where a ranger stands ({@code side} -1 left of the gate, 1 right). */
	static BlockPos towerTop(BlockPos fire, Direction gate, int side) {
		return new CampBuildings.Placer(fire, gate, 0).at(side * (GATE_HALF + 2), TOWER_HIGH + 1, -RING_RADIUS + 1);
	}

	private static void joinFence(ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		if (state.is(BlockTags.FENCES)) {
			level.setBlock(at, Block.updateFromNeighbourShapes(state, level, at), Block.UPDATE_CLIENTS);
		}
	}

	/** Places a building of the camp at once (its fences joined up). Returns where its containers went. */
	static List<BlockPos> place(ServerLevel level, IllagerBuildings.Spot spot, BlockPos fire, Direction gate) {
		CampBuildings.Placer placer = spot.placer(fire, gate);
		Blueprint design = spot.design().get();
		List<BlockPos> containers = CampBuildings.place(level, design, placer);
		for (Blueprint.Placement p : design.blocks()) {
			joinFence(level, placer.at(p.offset()));
		}
		return containers;
	}

	/** The warchief's hoard: iron (lots), gold, emeralds, a golden apple, an iron axe, meat, and now and then a diamond. */
	private static void stockHoard(ServerLevel level, BlockPos chest, RandomSource random) {
		if (!(level.getBlockEntity(chest) instanceof Container c)) {
			return;
		}
		List<ItemStack> hoard = List.of(new ItemStack(Items.IRON_INGOT, 10 + random.nextInt(13)), new ItemStack(Items.GOLD_INGOT, 4 + random.nextInt(7)),
			new ItemStack(Items.EMERALD, 6 + random.nextInt(9)), new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2)),
			new ItemStack(Items.IRON_AXE), new ItemStack(Items.COOKED_BEEF, 8 + random.nextInt(9)), new ItemStack(Items.BONE, 6 + random.nextInt(10)),
			random.nextInt(3) == 0 ? new ItemStack(Items.DIAMOND, 1 + random.nextInt(3)) : new ItemStack(Items.CHAINMAIL_CHESTPLATE));
		for (ItemStack stack : hoard) {
			for (int tries = 0; tries < 10; tries++) {
				int slot = random.nextInt(c.getContainerSize());
				if (c.getItem(slot).isEmpty()) {
					c.setItem(slot, stack);
					break;
				}
			}
		}
		c.setChanged();
	}

	/** What rots away once a camp is cleared: its hides, fences, skulls, banners and fires (a camp's), and its bones. The stockade logs stay. */
	static boolean perishable(BlockState state) {
		return CampBuildings.perishable(state) || state.is(Blocks.BONE_BLOCK) || state.is(Blocks.WITHER_SKELETON_SKULL) || state.is(Blocks.NOTE_BLOCK)
			|| state.is(Blocks.IRON_CHAIN);
	}
}
