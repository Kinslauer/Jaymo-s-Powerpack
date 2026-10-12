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
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A goblin village's buildings, all original designs: squat <b>huts</b> of mud brick and mossy cobble patched together,
 * capped with red or brown mushroom domes; the king's <b>great hut</b> (a mushroom-roofed hall, skulls at its door, his
 * throne of mossy stone and gold, his hoard behind it); a <b>tavern</b> of spruce with a barrel bar where the goblins
 * drink; a stilted <b>lookout</b>; the <b>junk heap</b> (chests and barrels under a lean-to: the village's
 * {@link Storage}); mushroom patches, jack-o'-lantern totems, a stewpot on a fire; and a palisade fort round it all
 * (sharpened logs, skull posts, a walkway, gate towers). A village grows by its own hands ({@link GoblinVillages}): each day it may raise the next of {@link #GROWTH}.
 *
 * <p>Designs are {@link Blueprint}s facing north (their front on z = 0, y = 0 the first layer on the ground), placed
 * with {@link CampBuildings.Placer} like the camps'.
 */
final class GoblinBuildings {
	/** The palisade's radius round the fire, and how far (and how high) the ground is levelled and cleared. */
	static final int RING_RADIUS = 22, CLEAR_RADIUS = RING_RADIUS + 2, CLEAR_HEIGHT = 16;
	/**
	 * The palisade: a wall of upright logs this high, each topped with a sharpened stake (one fence, or two every other log);
	 * a skull post every {@code POST_EVERY} degrees rising above it; a walkway inside at {@code WALK_HIGH} (a top slab);
	 * the gate's half-width; the gate towers' height.
	 */
	static final int WALL_HIGH = 6, POST_HIGH = WALL_HIGH + 2, WALK_HIGH = 3, GATE_HALF = 2, TOWER_HIGH = 10;
	static final double POST_EVERY = 30.0;
	/** How far round the fire its goblins wander. */
	static final int HOME_RADIUS = 18;

	private GoblinBuildings() {
	}

	// ---- materials -------------------------------------------------------------------------------------------------

	/** Patched walls: mud brick, packed mud and mossy cobble, picked by position so a design always looks the same. */
	private static BlockState patch(int x, int y, int z) {
		int h = Math.floorMod(x * 73428767 ^ y * 912931 ^ z * 42317861, 7);
		return switch (h) {
			case 0, 1, 2 -> Blocks.MUD_BRICKS.defaultBlockState();
			case 3, 4 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case 5 -> Blocks.PACKED_MUD.defaultBlockState();
			default -> Blocks.COBBLESTONE.defaultBlockState();
		};
	}

	private static BlockState log() {
		return Blocks.SPRUCE_LOG.defaultBlockState();
	}

	private static BlockState planks() {
		return Blocks.SPRUCE_PLANKS.defaultBlockState();
	}

	private static BlockState fence() {
		return Blocks.SPRUCE_FENCE.defaultBlockState();
	}

	private static BlockState slab() {
		return Blocks.SPRUCE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	private static BlockState cap(boolean red) {
		return (red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState();
	}

	private static BlockState skull() {
		return Blocks.SKELETON_SKULL.defaultBlockState();
	}

	private static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	// ---- designs ---------------------------------------------------------------------------------------------------

	/** A hut (5 x 5): patched walls on spruce corners, a doorway, a mushroom dome; a hay bed, a barrel and a lantern inside. */
	static Blueprint hut(boolean red) {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				if (edge) {
					for (int y = 0; y <= 2; y++) {
						if (x == 2 && z == 0 && y <= 1) {
							continue;
						}
						t.set(x, y, z, corner ? log() : patch(x, y, z));
					}
				}
				// the dome: a full cap, then a ring stepped in, then the crown
				t.set(x, 3, z, cap(red));
				if (x >= 1 && x <= 3 && z >= 1 && z <= 3) {
					t.set(x, 4, z, cap(red));
				}
			}
		}
		t.set(2, 5, 2, cap(red));
		// a white spot or two on a red cap (mushroom stem shows pale)
		if (red) {
			t.set(1, 3, 0, Blocks.MUSHROOM_STEM.defaultBlockState());
			t.set(3, 4, 1, Blocks.MUSHROOM_STEM.defaultBlockState());
		}
		t.set(1, 0, 3, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(3, 0, 3, Blocks.BARREL.defaultBlockState());
		t.set(2, 2, 2, lantern(true));
		return t.done(red ? "goblin_hut_red" : "goblin_hut_brown");
	}

	/**
	 * The king's great hut (9 x 11): a mossy cobble footing under patched mud walls, spruce posts, a three-wide doorway
	 * flanked by skulls on posts, a stepped brown mushroom roof; inside, a green carpet to his throne (mossy stone stairs
	 * between blocks of gold), his hoard chest behind it, lanterns and barrels.
	 */
	static Blueprint greatHut() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		// the doorway's skull posts
		for (int x : new int[] {2, 6}) {
			t.set(x, 0, 0, fence());
			t.set(x, 1, 0, fence());
			t.set(x, 2, 0, skull());
		}
		for (int z = 1; z <= 10; z++) {
			for (int x = 0; x <= 8; x++) {
				boolean edge = x == 0 || x == 8 || z == 1 || z == 10;
				if (!edge) {
					continue;
				}
				boolean post = (x == 0 || x == 8) && (z == 1 || z == 10 || z == 5);
				for (int y = 0; y <= 3; y++) {
					if (z == 1 && x >= 3 && x <= 5 && y <= 2) {
						continue;
					}
					t.set(x, y, z, post ? log() : y == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : patch(x, y, z));
				}
			}
		}
		// the roof: rings of brown cap stepping in, a red crown
		for (int step = 0; step <= 3; step++) {
			int x0 = step, x1 = 8 - step, z0 = 1 + step, z1 = 10 - step;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (x == x0 || x == x1 || z == z0 || z == z1 || step == 3) {
						t.set(x, 4 + step, z, cap(step == 3));
					}
				}
			}
		}
		// inside
		BlockState carpet = Blocks.CARPET.pick(DyeColor.GREEN).defaultBlockState();
		for (int z = 2; z <= 6; z++) {
			t.set(4, 0, z, carpet);
		}
		t.set(4, 0, 8, Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		t.set(3, 0, 8, Blocks.GOLD_BLOCK.defaultBlockState());
		t.set(5, 0, 8, Blocks.GOLD_BLOCK.defaultBlockState());
		t.set(3, 1, 8, Blocks.SKELETON_SKULL.defaultBlockState());
		t.set(5, 1, 8, Blocks.SKELETON_SKULL.defaultBlockState());
		t.set(4, 0, 9, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		t.set(1, 0, 9, Blocks.BARREL.defaultBlockState());
		t.set(7, 0, 9, Blocks.BARREL.defaultBlockState());
		t.set(1, 0, 2, Blocks.LANTERN.defaultBlockState());
		t.set(7, 0, 2, Blocks.LANTERN.defaultBlockState());
		t.set(4, 3, 5, lantern(true));
		t.set(1, 0, 6, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(7, 0, 6, Blocks.HAY_BLOCK.defaultBlockState());
		return t.done("goblin_great_hut");
	}

	/** Where the king sits, and his hoard (offsets in {@link #greatHut}). */
	static final BlockPos THRONE = new BlockPos(4, 0, 8), HOARD_CHEST = new BlockPos(4, 0, 9);

	/** The tavern (7 x 7): spruce walls on log corners, a slab-stepped roof, a barrel bar with a brewing stand, stools and a table. */
	static Blueprint tavern() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 6; z++) {
				boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
				boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
				if (edge) {
					for (int y = 0; y <= 2; y++) {
						if (x == 3 && z == 0 && y <= 1 || (y == 1 && !corner && (x == 1 || x == 5) && z == 0)) {
							// the door, and two windows either side of it
							continue;
						}
						t.set(x, y, z, corner ? log() : planks());
					}
				}
				t.set(x, 3, z, planks());
			}
		}
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				t.set(x, 4, z, slab());
			}
		}
		t.set(3, 4, 3, planks());
		// (a fire on the roof for a chimney: it smokes)
		t.set(3, 5, 3, Blocks.CAMPFIRE.defaultBlockState());
		// the bar along the back, a brewing stand on it
		for (int x = 1; x <= 5; x++) {
			t.set(x, 0, 5, Blocks.BARREL.defaultBlockState());
		}
		t.set(2, 1, 5, Blocks.BREWING_STAND.defaultBlockState());
		t.set(4, 1, 5, Blocks.BARREL.defaultBlockState());
		// a table and stools
		t.set(2, 0, 2, fence());
		t.set(2, 1, 2, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
		t.set(1, 0, 2, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
		t.set(3, 0, 2, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		t.set(5, 2, 2, lantern(true));
		t.set(1, 2, 4, lantern(true));
		return t.done("goblin_tavern");
	}

	/** Where its drinkers sit (offset in {@link #tavern}). */
	static final BlockPos TAVERN_SEAT = new BlockPos(4, 0, 3);

	/** A lookout (5 x 5, 9 high): spruce stilts, a ladder up a plank spine, a railed platform, a skull on a pole and a lantern. */
	static Blueprint lookout() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int[] c : new int[][] {{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
			t.set(c[0], 0, c[1], Blocks.MOSSY_COBBLESTONE.defaultBlockState());
			for (int y = 1; y <= 6; y++) {
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
				if ((x == 0 || x == 4 || z == 0 || z == 4) && !(x == 2 && z == 4)) {
					t.set(x, 7, z, fence());
				}
			}
		}
		t.set(2, 7, 4, fence());
		t.set(0, 8, 0, skull());
		t.set(4, 8, 4, Blocks.LANTERN.defaultBlockState());
		return t.done("goblin_lookout");
	}

	/** Where a slinger keeps watch (offset in {@link #lookout}). */
	static final BlockPos LOOKOUT_SPOT = new BlockPos(1, 7, 1);

	/** The junk heap (7 x 4): chests and barrels under a spruce lean-to, a heap of gravel, bones and coarse dirt at its end. */
	static Blueprint junkHeap() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 6; x++) {
			for (int y = 0; y <= 2; y++) {
				t.set(x, y, 3, x == 0 || x == 6 ? log() : patch(x, y, 3));
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
		t.set(5, 1, 2, Blocks.BONE_BLOCK.defaultBlockState());
		t.set(1, 0, 0, Blocks.GRAVEL.defaultBlockState());
		t.set(5, 0, 0, Blocks.COARSE_DIRT.defaultBlockState());
		return t.done("goblin_junk_heap");
	}

	/** Where the village's takings are brought (offset in {@link #junkHeap}). */
	static final BlockPos JUNK_DEPOT = new BlockPos(3, 0, 1);

	/** A mushroom patch (5 x 3): podzol and mycelium, red and brown mushrooms, a composter. */
	static Blueprint mushroomPatch() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 2; z++) {
				t.set(x, -1, z, (x + z) % 2 == 0 ? Blocks.PODZOL.defaultBlockState() : Blocks.MYCELIUM.defaultBlockState());
				if (x != 2 || z != 1) {
					t.set(x, 0, z, (x + z) % 3 == 0 ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState());
				}
			}
		}
		t.set(2, 0, 1, Blocks.COMPOSTER.defaultBlockState());
		return t.done("goblin_mushroom_patch");
	}

	/** A totem (1 x 1): a mossy cobble foot, a spruce pole, a skull, a jack-o'-lantern grinning on top. */
	static Blueprint totem() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		t.set(0, 0, 0, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		t.set(0, 1, 0, fence());
		t.set(0, 2, 0, log());
		t.set(0, 3, 0, Blocks.JACK_O_LANTERN.defaultBlockState());
		return t.done("goblin_totem");
	}

	/** The stewpot (3 x 1): a cauldron of water over a fire, a barrel of scraps and a stool beside it. */
	static Blueprint stewpot() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		t.set(1, 0, 0, Blocks.CAMPFIRE.defaultBlockState());
		t.set(1, 1, 0, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		t.set(0, 0, 0, Blocks.BARREL.defaultBlockState());
		t.set(2, 0, 0, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		return t.done("goblin_stewpot");
	}

	// ---- the village -------------------------------------------------------------------------------------------------

	/** turns: 0 its front to the gate, 1 to the gate's right (a building on the left facing the fire), 3 to its left. */
	static final IllagerBuildings.Spot GREAT_HUT = new IllagerBuildings.Spot("goblin_great_hut", GoblinBuildings::greatHut, 0, 9, 0, 9);
	static final IllagerBuildings.Spot JUNK = new IllagerBuildings.Spot("goblin_junk_heap", GoblinBuildings::junkHeap, 12, 0, 3, 7);
	static final IllagerBuildings.Spot TAVERN = new IllagerBuildings.Spot("goblin_tavern", GoblinBuildings::tavern, -12, 2, 1, 7);
	static final IllagerBuildings.Spot LOOKOUT = new IllagerBuildings.Spot("goblin_lookout", GoblinBuildings::lookout, -12, -13, 0, 5);
	static final IllagerBuildings.Spot STEWPOT = new IllagerBuildings.Spot("goblin_stewpot", GoblinBuildings::stewpot, 4, 2, 0, 3);
	static final List<IllagerBuildings.Spot> START = List.of(
		new IllagerBuildings.Spot("goblin_hut_red", () -> hut(true), 8, -13, 0, 5),
		new IllagerBuildings.Spot("goblin_hut_brown", () -> hut(false), 13, 9, 3, 5),
		new IllagerBuildings.Spot("goblin_hut_red", () -> hut(true), -11, 12, 1, 5),
		new IllagerBuildings.Spot("goblin_mushroom_patch", GoblinBuildings::mushroomPatch, 8, 4, 0, 5),
		new IllagerBuildings.Spot("goblin_totem", GoblinBuildings::totem, -3, -6, 0, 1),
		new IllagerBuildings.Spot("goblin_totem", GoblinBuildings::totem, 3, -6, 0, 1));

	/** What a village raises as it grows, in order (one a day at most: see {@link GoblinVillages}). */
	static final List<IllagerBuildings.Spot> GROWTH = List.of(
		new IllagerBuildings.Spot("goblin_hut_brown", () -> hut(false), -6, -16, 0, 5),
		new IllagerBuildings.Spot("goblin_mushroom_patch", GoblinBuildings::mushroomPatch, -6, 4, 0, 5),
		new IllagerBuildings.Spot("goblin_hut_red", () -> hut(true), 14, -6, 3, 5),
		new IllagerBuildings.Spot("goblin_totem", GoblinBuildings::totem, -8, -4, 0, 1),
		new IllagerBuildings.Spot("goblin_hut_brown", () -> hut(false), -16, -4, 1, 5),
		new IllagerBuildings.Spot("goblin_totem", GoblinBuildings::totem, 8, 15, 0, 1),
		new IllagerBuildings.Spot("goblin_hut_red", () -> hut(true), 6, -18, 0, 5));

	/** What a new village's founding built: its fire (the seat), the junk heap's containers and depot, the throne, the tavern's seat, the lookout. */
	record Layout(BlockPos fire, List<BlockPos> storage, BlockPos depot, BlockPos throne, BlockPos tavern, BlockPos lookout) {
	}

	/** Builds a whole village round {@code center} at {@code floor}, its gate facing {@code gate}. */
	static Layout build(ServerLevel level, BlockPos center, int floor, Direction gate) {
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		CampBuildings.Placer village = new CampBuildings.Placer(fire, gate, 0);
		CampBuildings.level(level, fire, CLEAR_RADIUS, CLEAR_HEIGHT);
		ring(level, village);
		// the bonfire, ringed with mossy cobble benches, a path of coarse dirt from the gate
		level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockState bench = Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState();
		for (int[] b : new int[][] {{0, -2}, {-2, 0}, {2, 0}, {-2, -2}, {2, -2}}) {
			level.setBlock(village.at(b[0], 0, b[1]), bench, Block.UPDATE_CLIENTS);
		}
		for (int z = -RING_RADIUS; z <= -3; z++) {
			for (int x = -1; x <= 1; x++) {
				BlockPos at = village.at(x, -1, z);
				if (level.getBlockState(at).is(BlockTags.DIRT) || level.getBlockState(at).is(Blocks.GRASS_BLOCK)) {
					level.setBlock(at, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		List<BlockPos> storage = new ArrayList<>();
		place(level, GREAT_HUT, fire, gate);
		storage.addAll(place(level, JUNK, fire, gate));
		place(level, TAVERN, fire, gate);
		place(level, LOOKOUT, fire, gate);
		place(level, STEWPOT, fire, gate);
		for (IllagerBuildings.Spot s : START) {
			place(level, s, fire, gate);
		}
		CampBuildings.Placer hall = GREAT_HUT.placer(fire, gate);
		stockHoard(level, hall.at(HOARD_CHEST), level.getRandom());
		return new Layout(fire, storage, JUNK.placer(fire, gate).at(JUNK_DEPOT), hall.at(THRONE), TAVERN.placer(fire, gate).at(TAVERN_SEAT),
			LOOKOUT.placer(fire, gate).at(LOOKOUT_SPOT));
	}

	/** Where the king of a village built round {@code fire} (its gate facing {@code gate}) sits. */
	static BlockPos throne(BlockPos fire, Direction gate) {
		return GREAT_HUT.placer(fire, gate).at(THRONE);
	}

	/**
	 * The palisade, a fort's wall: upright spruce logs {@link #WALL_HIGH} high with sharpened stakes along the top, skull-topped
	 * posts above them, a slab walkway inside reached by ladders, and a gate under a log lintel between two tall towers hung
	 * with lanterns.
	 */
	private static void ring(ServerLevel level, CampBuildings.Placer village) {
		List<BlockPos> stakes = new ArrayList<>();
		for (int x = -RING_RADIUS - 1; x <= RING_RADIUS + 1; x++) {
			for (int z = -RING_RADIUS - 1; z <= RING_RADIUS + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				boolean gate = z < 0 && Math.abs(x) <= GATE_HALF;
				if (d >= RING_RADIUS - 1.5 && d < RING_RADIUS - 0.5 && !gate) {
					// the walkway, wherever nothing of the village stands
					BlockPos at = village.at(x, WALK_HIGH, z);
					if (level.getBlockState(at).isAir()) {
						level.setBlock(at, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP),
							Block.UPDATE_CLIENTS);
					}
				}
				if (d < RING_RADIUS - 0.5 || d >= RING_RADIUS + 0.5) {
					continue;
				}
				if (gate) {
					// the lintel over the gate, staked like the wall
					level.setBlock(village.at(x, WALL_HIGH - 1, z), village.turn(log().setValue(BlockStateProperties.AXIS, Direction.Axis.X)), Block.UPDATE_CLIENTS);
					level.setBlock(village.at(x, WALL_HIGH, z), fence(), Block.UPDATE_CLIENTS);
					stakes.add(village.at(x, WALL_HIGH, z));
					continue;
				}
				double angle = Math.toDegrees(Math.atan2(z, x)) + 360.0;
				boolean post = angle % POST_EVERY < Math.toDegrees(1.0 / RING_RADIUS);
				int logs = post ? POST_HIGH : WALL_HIGH;
				for (int y = 0; y < logs; y++) {
					level.setBlock(village.at(x, y, z), log(), Block.UPDATE_CLIENTS);
				}
				if (post) {
					level.setBlock(village.at(x, POST_HIGH, z), skull(), Block.UPDATE_CLIENTS);
				} else {
					int high = (x + z & 1) == 0 ? 2 : 1;
					for (int y = 0; y < high; y++) {
						level.setBlock(village.at(x, WALL_HIGH + y, z), fence(), Block.UPDATE_CLIENTS);
						stakes.add(village.at(x, WALL_HIGH + y, z));
					}
				}
			}
		}
		// ladders up to the walkway at the three far points of the wall (never at the gate)
		ladder(level, village, RING_RADIUS - 1, 0, Direction.WEST);
		ladder(level, village, -RING_RADIUS + 1, 0, Direction.EAST);
		ladder(level, village, 0, RING_RADIUS - 1, Direction.NORTH);
		tower(level, village, -GATE_HALF - 2);
		tower(level, village, GATE_HALF + 2);
		for (BlockPos at : stakes) {
			joinFence(level, at);
		}
	}

	/** A ladder up the inside of the wall at {@code (x, z)} (a walkway cell), facing {@code facing} (north toward the gate). */
	private static void ladder(ServerLevel level, CampBuildings.Placer village, int x, int z, Direction facing) {
		BlockState ladder = village.turn(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
		for (int y = 0; y <= WALK_HIGH; y++) {
			level.setBlock(village.at(x, y, z), ladder, Block.UPDATE_CLIENTS);
		}
	}

	/** A gate tower (3 x 3 logs, {@link #TOWER_HIGH} high) beside the gate at {@code x}: a plank top, stakes at its corners, a lantern. */
	private static void tower(ServerLevel level, CampBuildings.Placer village, int x) {
		int z = -RING_RADIUS + 1;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 0; y < TOWER_HIGH; y++) {
					level.setBlock(village.at(x + dx, y, z + dz), log(), Block.UPDATE_CLIENTS);
				}
				level.setBlock(village.at(x + dx, TOWER_HIGH, z + dz), Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
				if (dx != 0 && dz != 0) {
					level.setBlock(village.at(x + dx, TOWER_HIGH + 1, z + dz), fence(), Block.UPDATE_CLIENTS);
				}
			}
		}
		level.setBlock(village.at(x, TOWER_HIGH + 1, z), skull(), Block.UPDATE_CLIENTS);
		// lanterns hung under the gate side of the tower
		int side = x < 0 ? 2 : -2;
		level.setBlock(village.at(x + side, WALL_HIGH - 2, z - 1), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true),
			Block.UPDATE_CLIENTS);
	}

	private static void joinFence(ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		if (state.is(BlockTags.FENCES)) {
			level.setBlock(at, Block.updateFromNeighbourShapes(state, level, at), Block.UPDATE_CLIENTS);
		}
	}

	/** Places a building of the village at once (its fences joined up). Returns where its containers went. */
	static List<BlockPos> place(ServerLevel level, IllagerBuildings.Spot spot, BlockPos fire, Direction gate) {
		CampBuildings.Placer placer = spot.placer(fire, gate);
		Blueprint design = spot.design().get();
		List<BlockPos> containers = CampBuildings.place(level, design, placer);
		for (Blueprint.Placement p : design.blocks()) {
			joinFence(level, placer.at(p.offset()));
		}
		return containers;
	}

	/** The king's hoard: gold (lots), emeralds, a golden apple, mushroom stew, and a spare mask. */
	private static void stockHoard(ServerLevel level, BlockPos chest, RandomSource random) {
		if (!(level.getBlockEntity(chest) instanceof Container c)) {
			return;
		}
		List<ItemStack> hoard = List.of(new ItemStack(Items.GOLD_INGOT, 8 + random.nextInt(9)), new ItemStack(Items.GOLD_NUGGET, 16 + random.nextInt(33)),
			new ItemStack(Items.EMERALD, 4 + random.nextInt(9)), new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2)),
			new ItemStack(Items.MUSHROOM_STEW), new ItemStack(Items.RAW_GOLD, 3 + random.nextInt(6)), new ItemStack(Disguise.MASK),
			random.nextInt(4) == 0 ? new ItemStack(Items.ENCHANTED_GOLDEN_APPLE) : new ItemStack(Items.GOLDEN_CARROT, 4 + random.nextInt(5)));
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

	/** What rots away once a village is cleared: its mushroom roofs and patches too, besides a camp's wool and fences. */
	static boolean perishable(BlockState state) {
		return CampBuildings.perishable(state) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
			|| state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.JACK_O_LANTERN) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM);
	}
}
