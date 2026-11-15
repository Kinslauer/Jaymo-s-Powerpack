package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.HelmBlock;
import com.jaymoskingdoms.sailing.Sailing;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The steam towns' own buildings ({@link SteamTowns}), drawn in code like every other design: brick below and creosoted
 * timber above, iron at the windows, dark slate roofs, a chimney smoking on every house. Like every design, its front on
 * the north side (z = 0), y = 0 the floor laid in place of the ground. Kitchen's and Powers' blocks come from
 * {@link SteamGoods}, which stands in vanilla ones without them.
 */
final class SteamBuildings {
	/** The airfield's size, and where on it the town's own airship lies and where a visiting one comes down (her corner, y her keel). */
	static final int FIELD_WIDTH = 17, FIELD_DEPTH = 15;
	static final int[] HOME_BERTH = {1, 2, 2}, VISITOR_BERTH = {11, 2, 2};
	/** Where on the landing stage each berth's captain waits for passengers, beside her gangway. */
	static final int[] HOME_STAGE = {7, 3, 8}, VISITOR_STAGE = {9, 3, 8};
	/** The airship: her deck's width and length, and how high she stands from her keel to the top of her bag. */
	static final int SHIP_WIDTH = 5, SHIP_LENGTH = 11, SHIP_HEIGHT = 7;
	/** The windmill's hub, above the tower's floor. */
	static final int HUB_HEIGHT = 8;

	private SteamBuildings() {
	}

	private static BlockState roofStairs() {
		return Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
	}

	private static BlockState roofSlab() {
		return Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
	}

	private static BlockState post() {
		return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
	}

	private static BlockState brick() {
		return Blocks.BRICKS.defaultBlockState();
	}

	/** A chimney of brick up the column (x, z) from {@code y0} to {@code y1}, smoking at its top. */
	private static void chimney(Buildings.Sketch s, int x, int z, int y0, int y1) {
		for (int y = y0; y <= y1; y++) {
			s.set(x, y, z, brick());
		}
		s.set(x, y1 + 1, z, Blocks.CAMPFIRE.defaultBlockState());
	}

	/**
	 * A house (7 x 7): two courses of brick and one of timber under a slate gable, iron bars at the windows, a bed, a
	 * stove at the back under its own chimney.
	 */
	static Blueprint house(RandomSource random) {
		Buildings.Sketch s = new Buildings.Sketch("house", 7, 7, 12);
		BlockState timber = SteamGoods.planks();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
				boolean side = x == 0 || x == 6, end = z == 0 || z == 6;
				if (!side && !end) {
					continue;
				}
				for (int y = 1; y <= 3; y++) {
					s.set(x, y, z, side && end ? post() : y == 3 ? timber : brick());
				}
			}
		}
		s.set(0, 2, 3, Blocks.IRON_BARS.defaultBlockState());
		s.set(6, 2, 3, Blocks.IRON_BARS.defaultBlockState());
		s.set(3, 2, 6, Blocks.IRON_BARS.defaultBlockState());
		s.door(3, 1, 0, Direction.NORTH, Blocks.SPRUCE_DOOR);
		s.gable(0, 6, 0, 6, 4, roofStairs(), roofSlab(), timber);
		String[] beds = {"red_bed", "brown_bed", "gray_bed", "black_bed"};
		s.bed(1, 1, 4, Direction.SOUTH, SteamGoods.vanilla(beds[random.nextInt(beds.length)], Blocks.AIR));
		s.set(5, 1, 5, Buildings.Sketch.facing(Blocks.FURNACE, Direction.NORTH));
		s.set(4, 1, 5, Blocks.CRAFTING_TABLE.defaultBlockState());
		s.set(4, 2, 5, Blocks.LANTERN.defaultBlockState());
		chimney(s, 5, 6, 1, 8);
		return s.done();
	}

	/**
	 * A workshop (9 x 7), open to the street: a tinker's (anvil, smithing table, grindstone, a boiler half mended) or
	 * an engineer's (cauldrons, a barrel, a heap of coke, a firebox and boiler on show).
	 */
	static Blueprint workshop(boolean tinker) {
		Buildings.Sketch s = new Buildings.Sketch(tinker ? "steam_tinkers" : "steam_engineers", 9, 7, 12);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
				boolean side = x == 0 || x == 8, back = z == 6;
				if (!side && !back) {
					continue;
				}
				for (int y = 1; y <= 3; y++) {
					s.set(x, y, z, side && (z == 0 || back) ? post() : brick());
				}
			}
		}
		for (int x = 1; x <= 7; x++) {
			s.set(x, 4, 3, Buildings.Sketch.lying(post(), Direction.Axis.X));
		}
		s.gableX(0, 8, 0, 6, 4, roofStairs(), roofSlab(), SteamGoods.planks());
		s.set(4, 3, 3, Buildings.Sketch.hanging(Blocks.LANTERN));
		s.set(2, 3, 3, Blocks.IRON_CHAIN.defaultBlockState());
		if (tinker) {
			s.set(2, 1, 5, Blocks.ANVIL.defaultBlockState());
			s.set(4, 1, 5, Blocks.SMITHING_TABLE.defaultBlockState());
			s.set(6, 1, 5, Buildings.Sketch.facing(Blocks.GRINDSTONE, Direction.NORTH));
			s.set(6, 1, 2, SteamGoods.boiler(Direction.NORTH));
			s.set(7, 1, 2, Blocks.IRON_BARS.defaultBlockState());
		} else {
			s.set(2, 1, 5, Blocks.CAULDRON.defaultBlockState());
			s.set(3, 1, 5, Blocks.CAULDRON.defaultBlockState());
			s.set(5, 1, 5, Blocks.BARREL.defaultBlockState());
			s.set(7, 1, 5, SteamGoods.cokeBlock());
			s.set(7, 2, 5, SteamGoods.cokeBlock());
			s.set(7, 1, 4, SteamGoods.cokeBlock());
			s.set(2, 1, 2, SteamGoods.firebox(Direction.NORTH));
			s.set(2, 2, 2, SteamGoods.boiler(Direction.NORTH));
		}
		return s.done();
	}

	/**
	 * The boiler house (9 x 11) the town is built round: a brick hall, firebrick at its back, two fireboxes under two
	 * boilers with an engine on each driving a shaft out toward the door, the coke heaped by the door, and one great
	 * chimney up through the ridge.
	 */
	static Blueprint boilerHouse() {
		Buildings.Sketch s = new Buildings.Sketch("steam_boiler_house", 9, 11, 16);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 11; z++) {
				s.set(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
				boolean side = x == 0 || x == 8, end = z == 0 || z == 10;
				if (!side && !end) {
					continue;
				}
				for (int y = 1; y <= 5; y++) {
					s.set(x, y, z, side && end ? post() : z == 10 ? SteamGoods.firebricks() : brick());
				}
			}
		}
		for (int z : new int[] {3, 5, 7}) {
			for (int y = 2; y <= 3; y++) {
				s.set(0, y, z, Blocks.IRON_BARS.defaultBlockState());
				s.set(8, y, z, Blocks.IRON_BARS.defaultBlockState());
			}
		}
		s.door(4, 1, 0, Direction.NORTH, Blocks.SPRUCE_DOOR);
		s.gable(0, 8, 0, 10, 6, roofStairs(), roofSlab(), SteamGoods.planks());
		for (int x : new int[] {2, 6}) {
			s.set(x, 1, 8, SteamGoods.firebox(Direction.NORTH));
			s.set(x, 2, 8, SteamGoods.boiler(Direction.NORTH));
			s.set(x, 3, 8, SteamGoods.engine(Direction.NORTH));
			s.set(x, 3, 7, SteamGoods.shaft(Direction.Axis.Z));
			s.set(x, 3, 6, SteamGoods.shaft(Direction.Axis.Z));
		}
		s.set(1, 1, 1, SteamGoods.cokeBlock());
		s.set(1, 1, 2, SteamGoods.cokeBlock());
		s.set(7, 1, 1, SteamGoods.cokeBlock());
		s.set(4, 1, 3, Blocks.LANTERN.defaultBlockState());
		chimney(s, 4, 9, 1, 12);
		return s.done();
	}

	/**
	 * The windmill (5 x 5): a brick and timber tower, slate hipped, its hub out in front at {@link #HUB_HEIGHT} on a
	 * shaft through the front wall: Kitchen's windmill with its four sails where Kitchen is, a bare hub where it isn't.
	 * The sails sweep the plane of the front row (z = 0), so nothing may stand there.
	 */
	static Blueprint windmill() {
		Buildings.Sketch s = new Buildings.Sketch("steam_windmill", 5, 5, 16);
		BlockState timber = SteamGoods.planks();
		for (int x = 0; x < 5; x++) {
			for (int z = 1; z < 5; z++) {
				s.set(x, 0, z, Blocks.STONE_BRICKS.defaultBlockState());
				boolean side = x == 0 || x == 4, end = z == 1 || z == 4;
				if (!side && !end) {
					continue;
				}
				for (int y = 1; y <= 9; y++) {
					s.set(x, y, z, side && end ? post() : y <= 4 ? brick() : timber);
				}
			}
		}
		s.door(2, 1, 4, Direction.SOUTH, Blocks.SPRUCE_DOOR);
		s.set(0, 6, 2, Blocks.IRON_BARS.defaultBlockState());
		s.set(4, 6, 3, Blocks.IRON_BARS.defaultBlockState());
		s.hip(0, 4, 1, 4, 10, roofStairs(), roofSlab());
		s.set(2, HUB_HEIGHT, 1, SteamGoods.shaft(Direction.Axis.Z));
		BlockState hub = SteamGoods.windmill(Direction.SOUTH);
		s.set(2, HUB_HEIGHT, 0, hub != null ? hub : SteamGoods.axis(post(), Direction.Axis.Z));
		return s.done();
	}

	/**
	 * The coke yard (7 x 7): a coke oven of Powers' bricks (its mouth to the street, its hollow inside, smoke over it),
	 * a cauldron for the creosote, coke heaped beside, a low brick wall round the back.
	 */
	static Blueprint cokeYard() {
		Buildings.Sketch s = new Buildings.Sketch("coke_yard", 7, 7, 8);
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, (x + z) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
				if (x == 0 || x == 6 || z == 6) {
					s.set(x, 1, z, Blocks.BRICK_WALL.defaultBlockState());
				}
			}
		}
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) {
				for (int z = 2; z <= 4; z++) {
					if (x == 3 && y == 2 && z == 3) {
						continue;
					}
					s.set(x, y, z, SteamGoods.ovenBricks());
				}
			}
		}
		s.set(3, 2, 2, SteamGoods.ovenMouth(Direction.NORTH));
		s.set(3, 4, 3, Blocks.CAMPFIRE.defaultBlockState());
		s.set(1, 1, 2, Blocks.CAULDRON.defaultBlockState());
		s.set(5, 1, 1, SteamGoods.cokeBlock());
		s.set(5, 1, 2, SteamGoods.cokeBlock());
		s.set(5, 2, 1, SteamGoods.cokeBlock());
		s.set(1, 1, 4, Blocks.BARREL.defaultBlockState());
		return s.done();
	}

	/**
	 * The airfield ({@link #FIELD_WIDTH} x {@link #FIELD_DEPTH}): two berths either side of a brick landing stage with
	 * its mast (a windsock on top), a block of air all round the stage so no ship moored there is joined to it. The town's
	 * airship lies on the left berth ({@link #HOME_BERTH}); the right ({@link #VISITOR_BERTH}) is kept clear for a ship
	 * from another town. The ships themselves are laid apart ({@link #airship}).
	 */
	static Blueprint airfield() {
		Buildings.Sketch s = new Buildings.Sketch("steam_airfield", FIELD_WIDTH, FIELD_DEPTH, 16);
		for (int x = 0; x < FIELD_WIDTH; x++) {
			for (int z = 0; z < FIELD_DEPTH; z++) {
				boolean berth = z >= 2 && z < 2 + SHIP_LENGTH && (x >= HOME_BERTH[0] && x < HOME_BERTH[0] + SHIP_WIDTH
					|| x >= VISITOR_BERTH[0] && x < VISITOR_BERTH[0] + SHIP_WIDTH);
				s.set(x, 0, z, berth ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
			}
		}
		BlockState steps = Blocks.BRICK_STAIRS.defaultBlockState();
		for (int x = 7; x <= 9; x++) {
			for (int z = 4; z <= 10; z++) {
				s.set(x, 1, z, brick());
				s.set(x, 2, z, z == 4 ? Buildings.Sketch.stair(steps, Direction.SOUTH) : z == 10 ? Buildings.Sketch.stair(steps, Direction.NORTH) : brick());
			}
			s.set(x, 1, 3, Buildings.Sketch.stair(steps, Direction.SOUTH));
			s.set(x, 1, 11, Buildings.Sketch.stair(steps, Direction.NORTH));
		}
		for (int y = 3; y <= 12; y++) {
			s.set(8, y, 7, post());
		}
		s.set(8, 13, 7, SteamGoods.windsock());
		s.set(7, 3, 5, Blocks.LANTERN.defaultBlockState());
		s.set(9, 3, 9, Blocks.LANTERN.defaultBlockState());
		return s.done();
	}

	/**
	 * The airship ({@link #SHIP_WIDTH} x {@link #SHIP_LENGTH}, her bow at z = 0): a deck of treated planks railed round
	 * (a gap amidships either side to step aboard), her wheel aft facing forward, a stove for her boiler, and a bag of
	 * {@code wool} over her on four posts: enough cloth to lift her ({@code Hull#airworthy}) and catch the wind. She
	 * touches nothing when she lies on her berth, so her wheel takes all of her.
	 */
	static Blueprint airship(BlockState wool) {
		Buildings.Sketch s = new Buildings.Sketch("steam_airship", SHIP_WIDTH, SHIP_LENGTH, SHIP_HEIGHT);
		BlockState deck = SteamGoods.planks(), rail = Blocks.SPRUCE_FENCE.defaultBlockState();
		int last = SHIP_LENGTH - 1, right = SHIP_WIDTH - 1;
		for (int z = 0; z <= last; z++) {
			for (int x = 0; x <= right; x++) {
				boolean bow = z == 0;
				if (bow && (x == 0 || x == right)) {
					continue;
				}
				s.set(x, 0, z, deck);
				boolean edge = bow || z == last || x == 0 || x == right;
				boolean gangway = (x == 0 || x == right) && (z == 5 || z == 6);
				if (edge && !gangway) {
					s.set(x, 1, z, rail);
				}
			}
		}
		for (int[] p : new int[][] {{0, 1}, {right, 1}, {0, last - 1}, {right, last - 1}}) {
			s.set(p[0], 2, p[1], rail);
			s.set(p[0], 3, p[1], rail);
		}
		s.set(2, 1, last - 2, Sailing.HELM.defaultBlockState().setValue(HelmBlock.FACING, Direction.NORTH));
		s.set(1, 1, last - 1, Buildings.Sketch.facing(Blocks.FURNACE, Direction.EAST));
		s.set(2, 1, 1, Blocks.LANTERN.defaultBlockState());
		for (int y = 4; y <= 6; y++) {
			int in = y == 6 ? 1 : 0;
			for (int x = in; x <= right - in; x++) {
				for (int z = in; z <= last - in; z++) {
					s.set(x, y, z, wool);
				}
			}
		}
		return s.done();
	}
}
