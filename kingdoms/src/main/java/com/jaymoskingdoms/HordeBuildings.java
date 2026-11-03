package com.jaymoskingdoms;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * (0.7.475) Hordes patch 2: the buildings a greenskin camp raises once it grows past its first stockade ({@link Hordes}),
 * all original designs drawn in the camps' own materials: a <b>market square</b> (stalls under striped awnings round a
 * lantern post), an <b>ogre den</b> (a stone mound with a gaping mouth, hay and bones inside), a <b>smelter</b> (furnaces
 * under a smoking chimney), a goblin <b>brewery</b> (a mushroom-roofed hall of cauldrons and barrels), <b>war-beast pens</b>
 * (a staked yard with troughs and a lean-to), and the <b>great hall</b> that replaces the warchief's longhouse or the
 * goblin king's great hut at tier 4, in its footprint, with its throne and hoard where they were.
 *
 * <p>Designs are {@link Blueprint}s facing north (their front on z = 0, y = 0 the first layer on the ground), like
 * {@link OrcBuildings} and {@link GoblinBuildings}.
 */
final class HordeBuildings {
	private HordeBuildings() {
	}

	// ---- materials: an orc camp's dark oak, blackstone and hides, a goblin village's spruce, mud and mushroom ----------

	private static BlockState log(boolean orc) {
		return (orc ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG).defaultBlockState();
	}

	private static BlockState logAlong(boolean orc, Direction.Axis axis) {
		return log(orc).setValue(RotatedPillarBlock.AXIS, axis);
	}

	private static BlockState planks(boolean orc) {
		return (orc ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS).defaultBlockState();
	}

	private static BlockState fence(boolean orc) {
		return (orc ? Blocks.DARK_OAK_FENCE : Blocks.SPRUCE_FENCE).defaultBlockState();
	}

	private static BlockState slab(boolean orc) {
		return (orc ? Blocks.DARK_OAK_SLAB : Blocks.SPRUCE_SLAB).defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
	}

	private static BlockState stairs(boolean orc, Direction facing) {
		return (orc ? Blocks.DARK_OAK_STAIRS : Blocks.SPRUCE_STAIRS).defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	/** Stone walling, picked by position so a design always looks the same: blackstone for orcs, patched mud and mossy cobble for goblins. */
	private static BlockState stone(boolean orc, int x, int y, int z) {
		int h = Math.floorMod(x * 73428767 ^ y * 912931 ^ z * 42317861, 7);
		if (orc) {
			return switch (h) {
				case 0, 1, 2 -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
				case 3 -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
				default -> Blocks.BLACKSTONE.defaultBlockState();
			};
		}
		return switch (h) {
			case 0, 1, 2 -> Blocks.MUD_BRICKS.defaultBlockState();
			case 3, 4 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case 5 -> Blocks.PACKED_MUD.defaultBlockState();
			default -> Blocks.COBBLESTONE.defaultBlockState();
		};
	}

	/** A roof block: black and brown hides for orcs, brown mushroom cap (red at the crown) for goblins. */
	private static BlockState roof(boolean orc, boolean crown) {
		if (orc) {
			return Blocks.WOOL.pick(crown ? DyeColor.BROWN : DyeColor.BLACK).defaultBlockState();
		}
		return (crown ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState();
	}

	/** Awning stripes: red and black for orcs, green and lime for goblins. */
	private static BlockState awning(boolean orc, boolean stripe) {
		return Blocks.WOOL.pick(orc ? (stripe ? DyeColor.RED : DyeColor.BLACK) : (stripe ? DyeColor.LIME : DyeColor.GREEN)).defaultBlockState();
	}

	private static BlockState floor(boolean orc, int x, int z) {
		int h = Math.floorMod(x * 31 + z * 17 + x * z, 5);
		if (orc) {
			return h == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : h < 3 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
		}
		return h == 0 ? Blocks.PACKED_MUD.defaultBlockState() : h < 3 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.MUD_BRICKS.defaultBlockState();
	}

	private static BlockState skull() {
		return Blocks.SKELETON_SKULL.defaultBlockState();
	}

	private static BlockState lantern(boolean hanging) {
		return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, hanging);
	}

	private static BlockState banner(boolean orc, Direction facing) {
		return Blocks.WALL_BANNER.pick(orc ? DyeColor.RED : DyeColor.GREEN).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
	}

	private static BlockState facing(BlockState state, Direction facing) {
		return state.setValue(HorizontalDirectionalBlock.FACING, facing);
	}

	// ---- the market square ------------------------------------------------------------------------------------------

	/**
	 * A market square (9 x 9): a trodden floor of the camp's stones; a stall at each corner (fence posts, a striped awning,
	 * a counter of barrels and a slab, wares stacked on it); a lantern post in the middle over a water trough. Open on every
	 * side, so the roads run through it. (Patch 3's traders will keep its stalls.)
	 */
	static Blueprint marketSquare(boolean orc) {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 8; z++) {
				t.set(x, -1, z, floor(orc, x, z));
			}
		}
		int[][] stalls = {{0, 0}, {6, 0}, {0, 6}, {6, 6}};
		for (int i = 0; i < stalls.length; i++) {
			int sx = stalls[i][0], sz = stalls[i][1];
			for (int[] c : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
				t.set(sx + c[0], 0, sz + c[1], fence(orc));
				t.set(sx + c[0], 1, sz + c[1], fence(orc));
			}
			for (int x = 0; x <= 2; x++) {
				for (int z = 0; z <= 2; z++) {
					t.set(sx + x, 2, sz + z, awning(orc, (x + i) % 2 == 0));
				}
			}
			// the counter faces the middle of the square
			int cz = sz == 0 ? 2 : 0, cx = sx == 0 ? 2 : 0;
			t.set(sx + 1, 0, sz + 1, Blocks.BARREL.defaultBlockState());
			t.set(sx + 1, 0, sz + cz, slab(orc).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
			t.set(sx + cx, 0, sz + 1, slab(orc).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
			BlockState ware = switch (i) {
				case 0 -> Blocks.HAY_BLOCK.defaultBlockState();
				case 1 -> orc ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState();
				case 2 -> Blocks.CRAFTING_TABLE.defaultBlockState();
				default -> Blocks.BARREL.defaultBlockState();
			};
			t.set(sx + 1, 1, sz + 1, ware);
			t.set(sx + 1, 1, sz + 1 + (sz == 0 ? 1 : -1), lantern(false));
		}
		// the lantern post over its trough
		t.set(4, 0, 4, orc ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		t.set(4, 1, 4, fence(orc));
		t.set(4, 2, 4, fence(orc));
		t.set(4, 3, 4, lantern(false));
		t.set(3, 0, 4, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		t.set(5, 0, 4, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		return t.done(orc ? "orc_market_square" : "goblin_market_square");
	}

	// ---- the ogre den -----------------------------------------------------------------------------------------------

	/**
	 * An ogre den (9 x 9): a mound of mossy cobble and stone, its walls leaning in to a rough dome, a mouth three wide and
	 * four high framed in bone, skulls over it; inside, a floor of coarse dirt and bones, hay to sleep on, a water trough
	 * and a pile of gnawed bone blocks.
	 */
	static Blueprint ogreDen() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 8; z++) {
				t.set(x, -1, z, (x * 7 + z * 3) % 4 == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
				boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
				for (int y = 0; y <= 3; y++) {
					if (edge && !(z == 0 && x >= 3 && x <= 5)) {
						t.set(x, y, z, denStone(x, y, z));
					}
				}
				// the dome: rings stepping in
				if (x >= 1 && x <= 7 && z >= 1 && z <= 7 && (x == 1 || x == 7 || z == 1 || z == 7)) {
					t.set(x, 4, z, denStone(x, 4, z));
				}
				if (x >= 2 && x <= 6 && z >= 2 && z <= 6) {
					t.set(x, 5, z, x >= 3 && x <= 5 && z >= 3 && z <= 5 ? Blocks.MOSS_BLOCK.defaultBlockState() : denStone(x, 5, z));
				}
			}
		}
		// the mouth: bone jambs, a lintel of bone, skulls above it
		for (int y = 0; y <= 3; y++) {
			t.set(2, y, 0, Blocks.BONE_BLOCK.defaultBlockState());
			t.set(6, y, 0, Blocks.BONE_BLOCK.defaultBlockState());
		}
		for (int x = 2; x <= 6; x++) {
			t.set(x, 4, 0, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		}
		t.set(3, 5, 0, skull());
		t.set(5, 5, 0, skull());
		t.set(4, 5, 0, Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
		// inside
		for (int x : new int[] {1, 2, 6, 7}) {
			t.set(x, 0, 7, Blocks.HAY_BLOCK.defaultBlockState());
		}
		t.set(1, 0, 6, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(7, 0, 6, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(4, 0, 7, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		t.set(1, 0, 2, Blocks.BONE_BLOCK.defaultBlockState());
		t.set(1, 1, 2, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
		t.set(7, 0, 3, Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
		t.set(4, 3, 4, lantern(true));
		return t.done("orc_ogre_den");
	}

	private static BlockState denStone(int x, int y, int z) {
		return switch (Math.floorMod(x * 73428767 ^ y * 912931 ^ z * 42317861, 6)) {
			case 0, 1 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case 2 -> Blocks.COBBLESTONE.defaultBlockState();
			case 3 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			case 4 -> Blocks.TUFF.defaultBlockState();
			default -> Blocks.STONE.defaultBlockState();
		};
	}

	// ---- the smelter --------------------------------------------------------------------------------------------------

	/**
	 * A smelter (7 x 7): a stone floor, a back wall of the camp's stone with blast furnaces and furnaces set in it, a
	 * chimney stack rising behind (a campfire smoking at its top), a lava cauldron behind bars, log posts holding a slab
	 * roof over the open front, an anvil and a stack of ore at the side.
	 */
	static Blueprint smelter(boolean orc) {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 6; z++) {
				t.set(x, -1, z, stone(orc, x, -1, z));
				t.set(x, 4, z, slab(orc));
			}
			for (int y = 0; y <= 3; y++) {
				t.set(x, y, 6, x == 0 || x == 6 ? log(orc) : stone(orc, x, y, 6));
			}
		}
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 3}, {6, 3}}) {
			for (int y = 0; y <= 3; y++) {
				t.set(c[0], y, c[1], log(orc));
			}
		}
		// the chimney: a stone stack behind the middle, smoking
		for (int y = 0; y <= 8; y++) {
			t.set(3, y, 7, y == 8 ? Blocks.CAMPFIRE.defaultBlockState() : stone(orc, 3, y, 7));
		}
		t.set(3, 4, 6, stone(orc, 3, 4, 6));
		// the furnaces along the back wall
		t.set(1, 0, 5, facing(Blocks.BLAST_FURNACE.defaultBlockState(), Direction.NORTH));
		t.set(2, 0, 5, facing(Blocks.FURNACE.defaultBlockState(), Direction.NORTH));
		t.set(4, 0, 5, facing(Blocks.FURNACE.defaultBlockState(), Direction.NORTH));
		t.set(5, 0, 5, facing(Blocks.BLAST_FURNACE.defaultBlockState(), Direction.NORTH));
		t.set(3, 0, 5, Blocks.LAVA_CAULDRON.defaultBlockState());
		t.set(3, 0, 4, Blocks.IRON_BARS.defaultBlockState());
		t.set(1, 1, 5, facing(Blocks.BLAST_FURNACE.defaultBlockState(), Direction.NORTH));
		t.set(5, 1, 5, facing(Blocks.FURNACE.defaultBlockState(), Direction.NORTH));
		// the anvil, a quench trough, the ore pile
		t.set(2, 0, 2, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
		t.set(4, 0, 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 2));
		t.set(1, 0, 3, Blocks.RAW_IRON_BLOCK.defaultBlockState());
		t.set(5, 0, 3, orc ? Blocks.COAL_BLOCK.defaultBlockState() : Blocks.RAW_GOLD_BLOCK.defaultBlockState());
		t.set(1, 3, 2, Blocks.IRON_CHAIN.defaultBlockState());
		t.set(1, 2, 2, lantern(true));
		t.set(5, 3, 2, Blocks.IRON_CHAIN.defaultBlockState());
		t.set(5, 2, 2, lantern(true));
		return t.done(orc ? "orc_smelter" : "goblin_smelter");
	}

	// ---- the brewery ----------------------------------------------------------------------------------------------------

	/**
	 * A goblin brewery (7 x 9): patched mud walls on spruce corners, a stepped brown mushroom roof with a red crown; inside,
	 * a row of cauldrons along one wall, brewing stands on barrels along the other, a stack of casks at the back and a
	 * composter of rotting mushrooms by the door.
	 */
	static Blueprint brewery() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int w = 6, d = 8;
		for (int x = 0; x <= w; x++) {
			for (int z = 0; z <= d; z++) {
				t.set(x, -1, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
				boolean edge = x == 0 || x == w || z == 0 || z == d;
				boolean corner = (x == 0 || x == w) && (z == 0 || z == d);
				if (edge) {
					for (int y = 0; y <= 2; y++) {
						if (z == 0 && x == 3 && y <= 1 || y == 1 && (x == 0 || x == w) && z % 3 == 1) {
							// the door, and windows along the sides
							continue;
						}
						t.set(x, y, z, corner ? log(false) : stone(false, x, y, z));
					}
				}
			}
		}
		for (int step = 0; step <= 2; step++) {
			for (int x = step; x <= w - step; x++) {
				for (int z = step; z <= d - step; z++) {
					if (x == step || x == w - step || z == step || z == d - step || step == 2) {
						t.set(x, 3 + step, z, roof(false, step == 2));
					}
				}
			}
		}
		// a smoking vent in the crown
		t.set(3, 6, 4, Blocks.CAMPFIRE.defaultBlockState());
		for (int z = 2; z <= 6; z += 2) {
			t.set(1, 0, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			t.set(5, 0, z, Blocks.BARREL.defaultBlockState());
			t.set(5, 1, z, Blocks.BREWING_STAND.defaultBlockState());
		}
		for (int x = 1; x <= 5; x++) {
			t.set(x, 0, 7, Blocks.BARREL.defaultBlockState());
			if (x % 2 == 1) {
				t.set(x, 1, 7, Blocks.BARREL.defaultBlockState());
			}
		}
		t.set(1, 0, 1, Blocks.COMPOSTER.defaultBlockState());
		t.set(2, 0, 1, Blocks.BROWN_MUSHROOM.defaultBlockState());
		t.set(3, 2, 4, lantern(true));
		t.set(4, 1, 1, Blocks.JACK_O_LANTERN.defaultBlockState());
		t.set(4, 0, 1, Blocks.SPRUCE_FENCE.defaultBlockState());
		return t.done("goblin_brewery");
	}

	// ---- war-beast pens -----------------------------------------------------------------------------------------------

	/**
	 * War-beast pens (9 x 7): a yard staked round with the camp's fence (a gap at the front, bones on its corner posts), a
	 * floor of coarse dirt and straw, a trough of water, chains on a post for tethering, and a lean-to at the back to keep
	 * the beasts' hay dry.
	 */
	static Blueprint warBeastPens(boolean orc) {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 6; z++) {
				t.set(x, -1, z, (x + 2 * z) % 4 == 0 ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
				boolean edge = x == 0 || x == 8 || z == 0 || z == 6;
				boolean corner = (x == 0 || x == 8) && (z == 0 || z == 6);
				if (edge && !(z == 0 && x >= 3 && x <= 5)) {
					t.set(x, 0, z, corner ? Blocks.BONE_BLOCK.defaultBlockState() : fence(orc));
					if (!corner) {
						t.set(x, 1, z, fence(orc));
					} else {
						t.set(x, 1, z, skull());
					}
				}
			}
		}
		// the lean-to along the back: posts and a slab roof, hay under it
		for (int x = 1; x <= 7; x++) {
			t.set(x, 2, 5, slab(orc));
			t.set(x, 2, 4, slab(orc));
		}
		t.set(1, 0, 4, fence(orc));
		t.set(1, 1, 4, fence(orc));
		t.set(7, 0, 4, fence(orc));
		t.set(7, 1, 4, fence(orc));
		t.set(2, 0, 5, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(3, 0, 5, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(2, 1, 5, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(6, 0, 5, Blocks.HAY_BLOCK.defaultBlockState());
		// the trough, the tethering post
		t.set(5, 0, 5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		t.set(4, 0, 5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		t.set(4, 0, 2, log(orc));
		t.set(4, 1, 2, log(orc));
		t.set(4, 2, 2, Blocks.IRON_CHAIN.defaultBlockState());
		t.set(2, 0, 1, Blocks.BONE_BLOCK.defaultBlockState());
		t.set(6, 0, 2, Blocks.HAY_BLOCK.defaultBlockState());
		return t.done(orc ? "orc_war_beast_pens" : "goblin_war_beast_pens");
	}

	// ---- the great halls ------------------------------------------------------------------------------------------------

	/**
	 * An orc kingdom's great hall, raised on the longhouse's footprint (11 x 13, the same throne and hoard): blackstone
	 * walls three high under dark oak with log posts, barred windows, a steep roof of black hides with a brown stripe and a
	 * log ridge, a squat blackstone tower at each corner crowned with skulls, a doorway four high between bone posts and
	 * war banners; inside, a red aisle between braziers and chained lanterns to the throne (gilded blackstone and stairs
	 * between bone pillars), long tables down the sides.
	 */
	static Blueprint orcGreatHall() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		boolean orc = true;
		int w = 10, d = 12;
		for (int x = 0; x <= w; x++) {
			for (int z = 0; z <= d; z++) {
				boolean edge = x == 0 || x == w || z == 0 || z == d;
				if (!edge) {
					t.set(x, -1, z, (x + z) % 2 == 0 ? planks(orc) : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
					continue;
				}
				boolean post = (x == 0 || x == w) && z % 4 == 0 || (z == 0 || z == d) && (x == 3 || x == 7);
				for (int y = 0; y <= 4; y++) {
					if (z == 0 && x >= 4 && x <= 6 && y <= 3) {
						continue;
					}
					BlockState s = post ? log(orc) : y <= 2 ? stone(orc, x, y, z) : planks(orc);
					if (!post && y == 3 && (x == 0 || x == w) && z % 4 == 2) {
						s = Blocks.IRON_BARS.defaultBlockState();
					}
					t.set(x, y, z, s);
				}
			}
		}
		// the corner towers
		for (int[] c : new int[][] {{0, 0}, {w, 0}, {0, d}, {w, d}}) {
			for (int y = 0; y <= 8; y++) {
				t.set(c[0], y, c[1], Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
			}
			t.set(c[0], 9, c[1], Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			t.set(c[0], 10, c[1], skull());
		}
		// the roof: hides stepping in from the long walls to a log ridge, a brown stripe, gables of planks
		for (int step = 0; step <= 5; step++) {
			int y = 5 + step;
			for (int z = -1; z <= d + 1; z++) {
				if (step < 5) {
					BlockState hide = roof(orc, step == 2);
					if (!(z == 0 || z == d) || step > 0) {
						t.set(step, y, z, hide);
						t.set(w - step, y, z, hide);
					}
				} else {
					t.set(5, y, z, logAlong(orc, Direction.Axis.Z));
				}
			}
			for (int x = step + 1; x <= w - step - 1; x++) {
				t.set(x, y, 0, planks(orc));
				t.set(x, y, d, planks(orc));
			}
		}
		t.set(5, 7, -1, banner(orc, Direction.NORTH));
		// the doorway's bone posts, skulls and banners
		for (int x : new int[] {2, 8}) {
			for (int y = 0; y <= 4; y++) {
				t.set(x, y, -1, Blocks.BONE_BLOCK.defaultBlockState());
			}
			t.set(x, 5, -1, skull());
		}
		t.set(3, 4, -1, banner(orc, Direction.NORTH));
		t.set(7, 4, -1, banner(orc, Direction.NORTH));
		// inside: the aisle, the braziers, the long tables
		BlockState carpet = Blocks.CARPET.pick(DyeColor.RED).defaultBlockState();
		for (int z = 1; z <= 8; z++) {
			t.set(5, 0, z, carpet);
		}
		for (int z : new int[] {2, 6}) {
			t.set(1, 0, z, Blocks.CAMPFIRE.defaultBlockState());
			t.set(w - 1, 0, z, Blocks.CAMPFIRE.defaultBlockState());
		}
		for (int z = 3; z <= 8; z++) {
			if (z == 6) {
				continue;
			}
			t.set(2, 0, z, slab(orc).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
			t.set(8, 0, z, slab(orc).setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
		}
		// the throne between bone pillars, the hoard behind it (where the longhouse kept them)
		t.set(4, 0, 10, Blocks.GILDED_BLACKSTONE.defaultBlockState());
		t.set(6, 0, 10, Blocks.GILDED_BLACKSTONE.defaultBlockState());
		t.set(5, 0, 10, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		for (int x : new int[] {3, 7}) {
			t.set(x, 0, 10, Blocks.BONE_BLOCK.defaultBlockState());
			t.set(x, 1, 10, Blocks.BONE_BLOCK.defaultBlockState());
			t.set(x, 2, 10, Blocks.BONE_BLOCK.defaultBlockState());
			t.set(x, 3, 10, skull());
		}
		t.set(5, 2, 11, banner(orc, Direction.NORTH));
		t.set(5, 0, 11, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		t.set(1, 0, 11, Blocks.BARREL.defaultBlockState());
		t.set(w - 1, 0, 11, Blocks.BARREL.defaultBlockState());
		for (int z : new int[] {3, 7}) {
			t.set(5, 4, z, Blocks.IRON_CHAIN.defaultBlockState());
			t.set(5, 3, z, lantern(true));
		}
		return t.done("orc_great_hall");
	}

	/**
	 * A goblin kingdom's great hall, raised on the great hut's footprint (9 x 11, the same throne and hoard): mossy stone
	 * brick and patched mud walls four high on spruce posts, a doorway between skull posts, a tall mushroom roof stepping
	 * in to a red crown, two mushroom-capped spires at the front corners; inside, a green carpet to the throne on a dais of
	 * gold, lanterns, barrels and heaps of loot.
	 */
	static Blueprint goblinGreatHall() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		boolean orc = false;
		for (int x : new int[] {2, 6}) {
			t.set(x, 0, 0, fence(orc));
			t.set(x, 1, 0, fence(orc));
			t.set(x, 2, 0, fence(orc));
			t.set(x, 3, 0, skull());
		}
		for (int z = 1; z <= 10; z++) {
			for (int x = 0; x <= 8; x++) {
				boolean edge = x == 0 || x == 8 || z == 1 || z == 10;
				if (!edge) {
					t.set(x, -1, z, (x + z) % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : planks(orc));
					continue;
				}
				boolean post = (x == 0 || x == 8) && (z == 1 || z == 10 || z == 5);
				for (int y = 0; y <= 4; y++) {
					if (z == 1 && x >= 3 && x <= 5 && y <= 2) {
						continue;
					}
					if (!post && y == 2 && (x == 0 || x == 8) && (z == 3 || z == 7)) {
						// windows
						continue;
					}
					t.set(x, y, z, post ? log(orc) : y <= 1 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : stone(orc, x, y, z));
				}
			}
		}
		// the roof: brown cap rings stepping in, then a red crown
		for (int step = 0; step <= 4; step++) {
			int x0 = step, x1 = 8 - step, z0 = 1 + step, z1 = 10 - step;
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (x == x0 || x == x1 || z == z0 || z == z1 || step == 4) {
						t.set(x, 5 + step, z, roof(orc, step >= 3));
					}
				}
			}
		}
		t.set(3, 6, 1, Blocks.MUSHROOM_STEM.defaultBlockState());
		t.set(6, 7, 3, Blocks.MUSHROOM_STEM.defaultBlockState());
		// the spires at the front corners
		for (int x : new int[] {0, 8}) {
			for (int y = 5; y <= 9; y++) {
				t.set(x, y, 1, log(orc));
			}
			t.set(x, 10, 1, roof(orc, true));
			t.set(x, 11, 1, Blocks.JACK_O_LANTERN.defaultBlockState());
		}
		// inside
		BlockState carpet = Blocks.CARPET.pick(DyeColor.GREEN).defaultBlockState();
		for (int z = 2; z <= 6; z++) {
			t.set(4, 0, z, carpet);
		}
		t.set(4, 0, 7, Blocks.CARPET.pick(DyeColor.LIME).defaultBlockState());
		t.set(4, 0, 8, Blocks.MOSSY_STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		for (int x : new int[] {3, 5}) {
			t.set(x, 0, 8, Blocks.GOLD_BLOCK.defaultBlockState());
			t.set(x, 1, 8, Blocks.GOLD_BLOCK.defaultBlockState());
			t.set(x, 2, 8, skull());
		}
		t.set(4, 0, 9, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		t.set(4, 2, 9, banner(orc, Direction.NORTH));
		t.set(1, 0, 9, Blocks.BARREL.defaultBlockState());
		t.set(7, 0, 9, Blocks.BARREL.defaultBlockState());
		t.set(1, 1, 9, Blocks.RAW_GOLD_BLOCK.defaultBlockState());
		t.set(7, 0, 8, Blocks.GOLD_BLOCK.defaultBlockState());
		t.set(1, 0, 2, Blocks.LANTERN.defaultBlockState());
		t.set(7, 0, 2, Blocks.LANTERN.defaultBlockState());
		t.set(1, 0, 6, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(7, 0, 6, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(4, 4, 5, lantern(true));
		t.set(2, 3, 3, lantern(true));
		t.set(6, 3, 3, lantern(true));
		return t.done("goblin_great_hall");
	}
}
