package com.jaymoskingdoms;

import com.jaymoskingdoms.StyleBuildings.House;
import com.jaymoskingdoms.StyleBuildings.Look;
import com.jaymoskingdoms.StyleBuildings.Roof;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The drawings of the outlying buildings ({@link Outliers}): a farmhouse and its field, a shepherd's hut and its pasture,
 * a woodcutter's cabin and its woodpile, a roadside trading post, a watchtower (a village's and a lookout's), the
 * evoker's frozen tower and a vindicator's cabin. All original, in the same plain materials as the village styles. Each
 * faces north (its front on z = 0) like every {@link Blueprint}; {@link VillageStyles#stamp} turns and sets them.
 */
final class OutlyingDesigns {
	private OutlyingDesigns() {
	}

	private static BlockState b(Block block) {
		return block.defaultBlockState();
	}

	private static Look oak() {
		return Look.of(b(Blocks.OAK_PLANKS), b(Blocks.OAK_LOG), b(Blocks.SPRUCE_PLANKS), b(Blocks.SPRUCE_STAIRS), b(Blocks.SPRUCE_SLAB))
			.foot(b(Blocks.COBBLESTONE)).fence(b(Blocks.OAK_FENCE)).door(Blocks.OAK_DOOR);
	}

	private static Look timber() {
		return Look.of(b(Blocks.SPRUCE_LOG), b(Blocks.DARK_OAK_LOG), b(Blocks.SPRUCE_PLANKS), b(Blocks.SPRUCE_STAIRS), b(Blocks.SPRUCE_SLAB))
			.foot(b(Blocks.COBBLESTONE)).fence(b(Blocks.SPRUCE_FENCE)).door(Blocks.SPRUCE_DOOR).logs();
	}

	private static Look dark() {
		return Look.of(b(Blocks.DARK_OAK_PLANKS), b(Blocks.DARK_OAK_LOG), b(Blocks.DARK_OAK_PLANKS), b(Blocks.DARK_OAK_STAIRS), b(Blocks.DARK_OAK_SLAB))
			.foot(b(Blocks.COBBLESTONE)).fence(b(Blocks.DARK_OAK_FENCE)).door(Blocks.DARK_OAK_DOOR);
	}

	/** A house furnished for a trade: its job block by the front door, where the bed's owner can reach it. */
	private static House.Fit job(Block block) {
		return (s, floor, front, back, w) -> s.set(w - 2, floor + 1, front + 1, b(block));
	}

	// ---- V15: homes out by the work ------------------------------------------------------------------------------------

	/** The farmer's house (7 x 7): oak and spruce, a composter by the door. */
	static Blueprint farmhouse() {
		return new House("outlying_farmhouse", 7, 7, 3).roof(Roof.GABLE).fit(job(Blocks.COMPOSTER)).build(oak());
	}

	/** The farmer's field (9 x 9): a fenced plot of ripe wheat round a well-pool, a gap at its front. */
	static Blueprint field() {
		Buildings.Sketch s = new Buildings.Sketch("outlying_field", 9, 9, 3);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
				if (edge) {
					s.set(x, 0, z, b(Blocks.COARSE_DIRT));
					if (!(x == 4 && z == 0)) {
						s.set(x, 1, z, b(Blocks.OAK_FENCE));
					}
				} else if (x == 4 && z == 4) {
					s.set(x, 0, z, b(Blocks.WATER));
				} else {
					s.set(x, 0, z, b(Blocks.FARMLAND).setValue(BlockStateProperties.MOISTURE, 7));
					s.set(x, 1, z, b(Blocks.WHEAT).setValue(CropBlock.AGE, 7));
				}
			}
		}
		s.set(0, 2, 0, b(Blocks.LANTERN));
		return s.done();
	}

	/** The shepherd's hut (5 x 5): a small low house with a loom by the door. */
	static Blueprint shepherdHut() {
		return new House("outlying_shepherd_hut", 5, 5, 3).roof(Roof.HIP).fit(job(Blocks.LOOM)).build(oak());
	}

	/** The shepherd's pasture (11 x 9): a rail fence with a gate at its front, hay and a trough inside. */
	static Blueprint pasture() {
		Buildings.Sketch s = new Buildings.Sketch("outlying_pasture", 11, 9, 3);
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 9; z++) {
				s.set(x, 0, z, b(Blocks.GRASS_BLOCK));
				if (x == 0 || x == 10 || z == 0 || z == 8) {
					s.set(x, 1, z, x == 5 && z == 0 ? b(Blocks.OAK_FENCE_GATE).setValue(FenceGateBlock.FACING, Direction.NORTH) : b(Blocks.OAK_FENCE));
				}
			}
		}
		s.set(8, 1, 6, b(Blocks.HAY_BLOCK));
		s.set(9, 1, 6, b(Blocks.HAY_BLOCK));
		s.set(2, 1, 6, b(Blocks.CAULDRON));
		return s.done();
	}

	/** The woodcutter's cabin (5 x 7): spruce logs on a stone footing, a fletching table by the door. */
	static Blueprint woodcutterCabin() {
		return new House("outlying_woodcutter_cabin", 5, 7, 3).roof(Roof.GABLE).fit(job(Blocks.FLETCHING_TABLE)).build(timber());
	}

	/** A woodpile and a chopping place (7 x 4): split logs stacked in three rows, a stump with a stonecutter beside it. */
	static Blueprint woodpile() {
		Buildings.Sketch s = new Buildings.Sketch("outlying_woodpile", 7, 4, 4);
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 4; z++) {
				s.set(x, 0, z, b(Blocks.PODZOL));
			}
		}
		for (int x = 1; x <= 5; x++) {
			for (int y = 1; y <= (x == 1 || x == 5 ? 2 : 3); y++) {
				s.set(x, y, 3, Buildings.Sketch.lying(b(Blocks.OAK_LOG), Direction.Axis.X));
			}
		}
		s.set(1, 1, 1, b(Blocks.OAK_LOG));
		s.set(3, 1, 1, b(Blocks.STONECUTTER));
		s.set(5, 1, 1, b(Blocks.BARREL));
		return s.done();
	}

	// ---- V16: a roadside trading post -----------------------------------------------------------------------------------

	/** Where the trader stands in a trading post (offset in {@link #tradingPost}). */
	static final int[] TRADER_SPOT = {4, 1, 4};

	/** A trading post (9 x 7): a plank floor under a slab roof on four posts, a counter of barrels with the trader behind it, a hitching rail along its front, hay and lanterns. */
	static Blueprint tradingPost() {
		Buildings.Sketch s = new Buildings.Sketch("outlying_trading_post", 9, 7, 6);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, b(Blocks.SPRUCE_PLANKS));
				s.set(x, 4, z, x == 0 || x == 8 || z == 0 || z == 6 ? b(Blocks.SPRUCE_SLAB) : b(Blocks.SPRUCE_PLANKS));
			}
		}
		for (int[] c : new int[][] {{0, 0}, {8, 0}, {0, 6}, {8, 6}}) {
			for (int y = 1; y <= 3; y++) {
				s.set(c[0], y, c[1], b(Blocks.STRIPPED_SPRUCE_LOG));
			}
		}
		// the back wall, so the trader has a wall behind him
		for (int x = 1; x <= 7; x++) {
			for (int y = 1; y <= 3; y++) {
				s.set(x, y, 6, b(Blocks.SPRUCE_PLANKS));
			}
		}
		// the counter, two deep in the middle, with a gap behind it for the trader
		for (int x = 2; x <= 6; x++) {
			s.set(x, 1, 3, x % 2 == 0 ? b(Blocks.BARREL) : b(Blocks.HAY_BLOCK));
		}
		s.set(3, 2, 3, b(Blocks.LANTERN));
		s.set(5, 2, 3, b(Blocks.LANTERN));
		// a hitching rail along the front, a gap for the road
		for (int x = 1; x <= 7; x++) {
			if (x != 4) {
				s.set(x, 1, 0, b(Blocks.SPRUCE_FENCE));
			}
		}
		s.set(1, 1, 5, b(Blocks.BARREL));
		s.set(7, 1, 5, b(Blocks.CHEST));
		s.set(7, 2, 5, b(Blocks.HAY_BLOCK));
		return s.done();
	}

	// ---- V17: watchtowers ---------------------------------------------------------------------------------------------------

	/** Where the guard stands on a watchtower, and its signal fire (offsets in {@link #watchtower}). */
	static final int[] TOWER_GUARD = {2, 10, 1}, TOWER_FIRE = {2, 10, 2}, TOWER_BANNER = {0, 10, 0};

	/**
	 * A watchtower (5 x 5, 11 high): a door and slits in a stone-and-timber shaft, a ladder up inside, an open platform with a rail,
	 * and a signal fire laid on hay (unlit; {@link Outliers} lights it). An illager's lookout is dark oak and flies a banner.
	 */
	static Blueprint watchtower(boolean illager) {
		Buildings.Sketch s = new Buildings.Sketch(illager ? "outlying_lookout" : "outlying_watchtower", 5, 5, 13);
		Look l = illager ? dark() : oak();
		BlockState stone = b(Blocks.COBBLESTONE), post = l.post(), wall = illager ? b(Blocks.DARK_OAK_PLANKS) : b(Blocks.STONE_BRICKS);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				s.set(x, 0, z, stone);
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				for (int y = 1; y <= 8; y++) {
					boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
					s.set(x, y, z, !edge ? b(Blocks.AIR) : corner ? post : y <= 2 ? stone : wall);
				}
				// the platform
				s.set(x, 9, z, l.floor());
				if (edge) {
					s.set(x, 10, z, (x == 0 || x == 4) && (z == 0 || z == 4) ? post : l.fence());
				}
			}
		}
		s.door(2, 1, 0, Direction.NORTH, l.door());
		for (int y : new int[] {4, 7}) {
			s.set(0, y, 2, b(Blocks.IRON_BARS));
			s.set(4, y, 2, b(Blocks.IRON_BARS));
			s.set(2, y, 4, b(Blocks.IRON_BARS));
		}
		// the ladder up the back wall, through a gap in the platform
		for (int y = 1; y <= 9; y++) {
			s.set(2, y, 3, Buildings.Sketch.facing(Blocks.LADDER, Direction.NORTH));
		}
		s.set(0, 11, 0, b(Blocks.LANTERN));
		s.set(4, 11, 4, b(Blocks.LANTERN));
		// the signal fire: a campfire on a hay bale, unlit
		s.set(TOWER_FIRE[0], 9, TOWER_FIRE[2], b(Blocks.HAY_BLOCK));
		s.set(TOWER_FIRE[0], TOWER_FIRE[1], TOWER_FIRE[2], b(Blocks.CAMPFIRE).setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
		if (illager) {
			s.set(TOWER_BANNER[0], TOWER_BANNER[1], TOWER_BANNER[2], StyleBuildings.named("white_banner", Blocks.AIR));
		}
		return s.done();
	}

	// ---- V19: the evoker's frozen tower ---------------------------------------------------------------------------------

	/** Where the evoker stands (the top room), and his lab's chest (the ground room), offsets in {@link #frozenTower}. */
	static final int[] EVOKER_SPOT = {3, 11, 3}, LAB_CHEST = {5, 1, 5};

	/** The evoker's frozen tower (7 x 7, 19 high): stone brick, packed ice and snow, three rooms (a cellar of barrels, a library, a laboratory), a spire of blue ice. */
	static Blueprint frozenTower() {
		Buildings.Sketch s = new Buildings.Sketch("evoker_frozen_tower", 7, 7, 20);
		BlockState[] skin = {b(Blocks.STONE_BRICKS), b(Blocks.PACKED_ICE), b(Blocks.SNOW_BLOCK), b(Blocks.STONE_BRICKS), b(Blocks.PACKED_ICE)};
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, b(Blocks.STONE_BRICKS));
				boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
				for (int y = 1; y <= 14; y++) {
					s.set(x, y, z, edge ? skin[Math.floorMod(x * 3 + y + z * 2, skin.length)] : b(Blocks.AIR));
				}
				// the floors: the ground one, the library's, the laboratory's, and the roof's
				for (int y : new int[] {5, 10}) {
					if (!edge) {
						s.set(x, y, z, b(Blocks.SPRUCE_PLANKS));
					}
				}
				if (x <= 5 && x >= 1 && z >= 1 && z <= 5) {
					s.set(x, 15, z, b(Blocks.PACKED_ICE));
				}
				if (x >= 2 && x <= 4 && z >= 2 && z <= 4) {
					s.set(x, 16, z, b(Blocks.BLUE_ICE));
				}
			}
		}
		s.set(3, 17, 3, b(Blocks.BLUE_ICE));
		s.set(3, 18, 3, b(Blocks.ICE));
		// the door, and windows on each room
		s.door(3, 1, 0, Direction.NORTH, Blocks.SPRUCE_DOOR);
		for (int y : new int[] {3, 8, 13}) {
			for (int[] w : new int[][] {{0, 3}, {6, 3}, {3, 6}}) {
				s.set(w[0], y, w[1], b(Blocks.GLASS_PANE));
			}
		}
		// the ladder, west side, through holes in the two floors
		for (int y = 1; y <= 14; y++) {
			s.set(1, y, 3, Buildings.Sketch.facing(Blocks.LADDER, Direction.EAST));
		}
		// the cellar: barrels, the lab's chest, a lantern
		s.set(1, 1, 5, b(Blocks.BARREL));
		s.set(1, 1, 1, b(Blocks.BARREL));
		s.set(LAB_CHEST[0], LAB_CHEST[1], LAB_CHEST[2], b(Blocks.CHEST));
		s.set(5, 1, 1, b(Blocks.CAULDRON));
		s.set(3, 4, 3, b(Blocks.LANTERN));
		// the library: shelves along the back, a lectern
		for (int x = 1; x <= 5; x++) {
			s.set(x, 6, 5, b(Blocks.BOOKSHELF));
			s.set(x, 7, 5, b(Blocks.BOOKSHELF));
		}
		s.set(4, 6, 2, b(Blocks.LECTERN));
		s.set(3, 9, 3, b(Blocks.LANTERN));
		// the laboratory: brewing stands, a cauldron, an enchanting table where the evoker stands
		s.set(5, 11, 1, b(Blocks.BREWING_STAND));
		s.set(5, 11, 2, b(Blocks.BREWING_STAND));
		s.set(5, 11, 4, b(Blocks.CAULDRON));
		s.set(4, 11, 5, b(Blocks.ENCHANTING_TABLE));
		s.set(1, 11, 5, b(Blocks.BOOKSHELF));
		s.set(1, 12, 5, b(Blocks.BOOKSHELF));
		s.set(3, 14, 3, b(Blocks.LANTERN));
		return s.done();
	}

	// ---- V20: the exile's cabin ---------------------------------------------------------------------------------------

	/** A vindicator's cabin (5 x 5): dark oak, a bed, a barrel and a chopping block; no banner on it. */
	static Blueprint exileCabin() {
		return new House("exile_cabin", 5, 5, 3).roof(Roof.GABLE).fit((s, floor, front, back, w) -> {
			s.set(w - 2, floor + 1, front + 1, b(Blocks.BARREL));
			s.set(1, floor + 1, front + 1, b(Blocks.GRINDSTONE));
		}).build(dark());
	}
}
