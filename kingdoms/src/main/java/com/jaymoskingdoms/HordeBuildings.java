package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

	// ---- (0.7.490) the goblin town: stone and timber ----------------------------------------------------------------------

	/** A storey of a town building is this high (three of wall and a beam or ceiling course). */
	private static final int STOREY = 4;

	private static BlockState pane() {
		return Blocks.GLASS_PANE.defaultBlockState();
	}

	private static void door(CampBuildings.Draft t, int x, int y, int z, Direction facing) {
		BlockState lower = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		t.set(x, y, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		t.set(x, y + 1, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	private static BlockState chest(Direction facing) {
		return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
	}

	/**
	 * The shell of a town building ({@code w} wide, odd, by {@code d} deep, {@code storeys} high): a floor of the village's
	 * mud brick, stone infill between log posts and beams, a door in the middle of the front (z = 0) with posts either side,
	 * two-high glass windows either side of it and in the middle of the sides, a ceiling course over each storey, and a
	 * spruce-stair gable roof along z with a mushroom ridge (red or brown). Returns the ridge's height.
	 */
	private static int townShell(CampBuildings.Draft t, int w, int d, int storeys, boolean red) {
		int mid = w / 2, dmid = d / 2;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				t.set(x, -1, z, floor(false, x, z));
			}
		}
		for (int s = 0; s < storeys; s++) {
			int base = s * STOREY;
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean onSide = x == 0 || x == w - 1, onEnd = z == 0 || z == d - 1;
					if (!onSide && !onEnd) {
						t.set(x, base + 3, z, planks(false));
						continue;
					}
					boolean corner = onSide && onEnd;
					for (int y = base; y <= base + 2; y++) {
						if (s == 0 && z == 0 && x == mid && y <= base + 1) {
							continue;
						}
						BlockState b;
						if (corner) {
							b = log(false);
						} else if (onEnd) {
							int dx = Math.abs(x - mid);
							b = dx == 1 ? log(false) : dx == 2 && y >= base + 1 ? pane() : stone(false, x, y, z);
						} else {
							int dz = Math.abs(z - dmid);
							b = dz == 1 ? log(false) : dz == 0 && y >= base + 1 ? pane() : stone(false, x, y, z);
						}
						t.set(x, y, z, b);
					}
					t.set(x, base + 3, z, corner ? log(false) : logAlong(false, onEnd ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
		}
		door(t, mid, 0, 0, Direction.NORTH);
		// a lantern over the door, hung from its lintel course inside
		t.set(mid, 2, 1, lantern(true));
		int roofY = storeys * STOREY, half = (w - 1) / 2;
		for (int k = 0; k <= half; k++) {
			for (int z = 0; z < d; z++) {
				if (k == half) {
					t.set(half, roofY + k, z, roof(false, red));
				} else {
					t.set(k, roofY + k, z, stairs(false, Direction.EAST));
					t.set(w - 1 - k, roofY + k, z, stairs(false, Direction.WEST));
				}
			}
			for (int x = k + 1; x <= w - 2 - k; x++) {
				for (int z : new int[] {0, d - 1}) {
					t.set(x, roofY + k, z, k == 1 && x == mid ? pane() : planks(false));
				}
			}
		}
		t.set(mid, roofY + half - 1, dmid, lantern(true));
		return roofY + half;
	}

	/** A stone chimney stack up the inside corner at {@code (x, z)}, a furnace as its hearth, a campfire smoking at its top. */
	private static void chimney(CampBuildings.Draft t, int x, int z, int topY, BlockState hearth) {
		t.set(x, 0, z, hearth);
		for (int y = 1; y <= topY; y++) {
			t.set(x, y, z, stone(false, x, y, z));
		}
		t.set(x, topY + 1, z, Blocks.CAMPFIRE.defaultBlockState());
	}

	/** A flight of spruce stairs up the inside of the left wall (x = 1) from z = 1 to the upper floor, with the floor opened over it. */
	private static void stairUp(CampBuildings.Draft t) {
		for (int y = 0; y < STOREY; y++) {
			t.set(1, y, 1 + y, stairs(false, Direction.SOUTH));
		}
		t.blocks.remove(new BlockPos(1, 3, 2));
		t.blocks.remove(new BlockPos(1, 3, 3));
		t.set(2, STOREY, 2, fence(false));
		t.set(2, STOREY, 3, fence(false));
	}

	/**
	 * A town house (7 x 6): stone infill between spruce posts, glass windows and a spruce door, a spruce-stair roof with a
	 * mushroom ridge, a chimney with a hearth; inside, a hay bed, a chest, a table and a barrel.
	 */
	static Blueprint townHouse(boolean red) {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int top = townShell(t, 7, 6, 1, red);
		chimney(t, 5, 4, top, facing(Blocks.FURNACE.defaultBlockState(), Direction.NORTH));
		t.set(1, 0, 4, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(1, 0, 3, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(1, 0, 1, chest(Direction.EAST));
		t.set(3, 0, 3, fence(false));
		t.set(3, 1, 3, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
		t.set(4, 0, 4, Blocks.BARREL.defaultBlockState());
		t.set(5, 0, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
		return t.done(red ? "goblin_town_house_red" : "goblin_town_house_brown");
	}

	/**
	 * A two-storey home (7 x 7): the house's stone and timber twice over, a stair up the left wall to a sleeping floor with
	 * beds of hay and shelves under the roof, a kitchen below with a hearth and a cauldron.
	 */
	static Blueprint townHome() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int top = townShell(t, 7, 7, 2, false);
		stairUp(t);
		chimney(t, 5, 5, top, facing(Blocks.SMOKER.defaultBlockState(), Direction.NORTH));
		t.set(3, 0, 5, Blocks.CAULDRON.defaultBlockState());
		t.set(4, 0, 5, Blocks.BARREL.defaultBlockState());
		t.set(5, 0, 1, chest(Direction.WEST));
		t.set(4, 0, 3, fence(false));
		t.set(4, 1, 3, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
		t.set(2, 0, 5, Blocks.COMPOSTER.defaultBlockState());
		// upstairs: hay beds, bookshelves under the windows' sills
		t.set(5, 4, 1, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(5, 4, 2, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(4, 4, 5, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(3, 4, 5, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(1, 4, 5, Blocks.BOOKSHELF.defaultBlockState());
		t.set(1, 5, 5, Blocks.BOOKSHELF.defaultBlockState());
		t.set(3, 4, 1, Blocks.BOOKSHELF.defaultBlockState());
		t.set(3, 6, 3, lantern(true));
		return t.done("goblin_town_home");
	}

	/**
	 * A tinker's workshop (9 x 7): the town shell with copper blocks stacked at the door as a sign of trade, benches along the
	 * back wall (smithing table, grindstone, crafting table, anvil), chained lanterns, a quench trough and a lightning rod
	 * on the ridge, a hearth in the corner.
	 */
	static Blueprint tinkerWorkshop() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int top = townShell(t, 9, 7, 1, false);
		chimney(t, 7, 5, top, facing(Blocks.BLAST_FURNACE.defaultBlockState(), Direction.NORTH));
		t.set(2, 0, 5, Blocks.SMITHING_TABLE.defaultBlockState());
		t.set(3, 0, 5, Blocks.GRINDSTONE.defaultBlockState());
		t.set(4, 0, 5, Blocks.CRAFTING_TABLE.defaultBlockState());
		t.set(1, 0, 5, Blocks.BARREL.defaultBlockState());
		t.set(5, 0, 3, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
		t.set(6, 0, 5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		t.set(1, 0, 1, Blocks.COPPER_BLOCK.weathering().exposed().defaultBlockState());
		t.set(1, 1, 1, Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState());
		t.set(7, 0, 1, chest(Direction.WEST));
		t.set(2, 2, 2, Blocks.IRON_CHAIN.defaultBlockState());
		t.set(2, 1, 2, lantern(true));
		t.set(6, 2, 2, Blocks.IRON_CHAIN.defaultBlockState());
		t.set(6, 1, 2, lantern(true));
		t.set(4, top + 1, 1, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
		return t.done("goblin_tinker_workshop");
	}

	/**
	 * A town smelter (9 x 7): a stone house with a chimney at each back corner (a blast furnace and a furnace as their
	 * hearths, campfires smoking above), a row of furnaces and a lava cauldron along the back wall, an anvil, a quench trough
	 * and heaps of ore and ingots at the front.
	 */
	static Blueprint townSmelter() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int top = townShell(t, 9, 7, 1, true);
		chimney(t, 1, 5, top, facing(Blocks.BLAST_FURNACE.defaultBlockState(), Direction.NORTH));
		chimney(t, 7, 5, top, facing(Blocks.BLAST_FURNACE.defaultBlockState(), Direction.NORTH));
		for (int x = 2; x <= 6; x++) {
			t.set(x, 0, 5, x == 4 ? Blocks.LAVA_CAULDRON.defaultBlockState() : facing(x % 2 == 0 ? Blocks.FURNACE.defaultBlockState()
				: Blocks.BLAST_FURNACE.defaultBlockState(), Direction.NORTH));
		}
		t.set(3, 0, 3, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
		t.set(5, 0, 3, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		t.set(1, 0, 1, Blocks.RAW_IRON_BLOCK.defaultBlockState());
		t.set(1, 1, 1, Blocks.RAW_IRON_BLOCK.defaultBlockState());
		t.set(7, 0, 1, Blocks.RAW_GOLD_BLOCK.defaultBlockState());
		t.set(1, 0, 2, Blocks.IRON_BLOCK.defaultBlockState());
		t.set(7, 0, 2, Blocks.GOLD_BLOCK.defaultBlockState());
		t.set(4, 2, 4, Blocks.IRON_CHAIN.defaultBlockState());
		t.set(4, 1, 4, lantern(true));
		return t.done("goblin_town_smelter");
	}

	/**
	 * Market stalls (11 x 5): three stalls in a row against a stone back wall, each with its counter, striped awning, hanging
	 * lantern and wares (melons, hay, mushrooms, casks), a mud brick floor, alleys between them.
	 */
	static Blueprint marketStalls() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 4; z++) {
				t.set(x, -1, z, floor(false, x, z));
			}
			for (int y = 0; y <= 2; y++) {
				t.set(x, y, 4, x % 4 == 0 ? log(false) : stone(false, x, y, 4));
			}
		}
		BlockState[] wares = {Blocks.MELON.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState(), Blocks.RED_MUSHROOM_BLOCK.defaultBlockState()};
		for (int s = 0; s < 3; s++) {
			int x0 = s * 4;
			for (int x = x0; x <= x0 + 2; x++) {
				for (int z = 0; z <= 3; z++) {
					t.set(x, 3, z, awning(false, (x + z & 1) == 0));
				}
				t.set(x, 0, 1, slab(false));
				t.set(x, 0, 2, x == x0 + 1 ? wares[s] : Blocks.BARREL.defaultBlockState());
			}
			for (int[] c : new int[][] {{x0, 0}, {x0 + 2, 0}}) {
				t.set(c[0], 0, c[1], fence(false));
				t.set(c[0], 1, c[1], fence(false));
				t.set(c[0], 2, c[1], fence(false));
			}
			t.set(x0 + 1, 2, 1, lantern(true));
			t.set(x0 + 1, 1, 1, wares[(s + 1) % 3]);
		}
		return t.done("goblin_market_stalls");
	}

	/**
	 * A counting house (9 x 9, two storeys): a long counter across the ground floor with lecterns on it, a barred vault in
	 * the back corner heaped with gold and a chest, a stair to an upper floor of bookshelves and a desk, a bell by the door.
	 */
	static Blueprint countingHouse() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int top = townShell(t, 9, 9, 2, true);
		stairUp(t);
		chimney(t, 1, 7, top, facing(Blocks.FURNACE.defaultBlockState(), Direction.NORTH));
		for (int x = 3; x <= 7; x++) {
			t.set(x, 0, 3, slab(false));
			if ((x & 1) == 1) {
				t.set(x, 1, 3, Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
			}
		}
		// the vault: iron bars round gold and a chest, its door a gap bars leave on the counter side
		// (0.7.490) the gap is (7, 5), opening north toward the counter and onto the empty cell before the chest
		for (int x = 5; x <= 7; x++) {
			for (int z = 5; z <= 7; z++) {
				boolean edge = (x == 5 || z == 5) && !(x == 7 && z == 5);
				for (int y = 0; y <= 1; y++) {
					t.set(x, y, z, edge ? Blocks.IRON_BARS.defaultBlockState() : y == 0 && (x + z & 1) == 0 ? Blocks.GOLD_BLOCK.defaultBlockState()
						: Blocks.AIR.defaultBlockState());
				}
			}
		}
		t.set(7, 0, 7, chest(Direction.WEST));
		t.set(6, 0, 6, Blocks.GOLD_BLOCK.defaultBlockState());
		t.set(2, 0, 1, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR));
		// upstairs
		for (int z = 3; z <= 7; z++) {
			t.set(7, 4, z, Blocks.BOOKSHELF.defaultBlockState());
			t.set(7, 5, z, Blocks.BOOKSHELF.defaultBlockState());
		}
		t.set(4, 4, 6, Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
		t.set(3, 4, 6, Blocks.BOOKSHELF.defaultBlockState());
		t.set(5, 4, 6, Blocks.BOOKSHELF.defaultBlockState());
		t.set(4, 4, 4, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
		return t.done("goblin_counting_house");
	}

	/**
	 * A garden (9 x 9): a low cobble wall round it with timber posts and lanterns, a gate in the front, four beds of crops
	 * watered from a pool in the middle, a patch of mushrooms in the shade, a scarecrow and a compost bin.
	 */
	static Blueprint garden() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 8; z++) {
				t.set(x, -1, z, (x + z & 1) == 0 ? Blocks.PACKED_MUD.defaultBlockState() : Blocks.MUD_BRICKS.defaultBlockState());
				boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
				boolean corner = (x == 0 || x == 8) && (z == 0 || z == 8);
				if (corner) {
					t.set(x, 0, z, log(false));
					t.set(x, 1, z, log(false));
					t.set(x, 2, z, lantern(false));
				} else if (edge && !(z == 0 && x >= 3 && x <= 5)) {
					t.set(x, 0, z, x % 4 == 0 || z % 4 == 0 ? Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState() : Blocks.COBBLESTONE_WALL.defaultBlockState());
				}
			}
		}
		BlockState[] crops = {Blocks.CARROTS.defaultBlockState(), Blocks.POTATOES.defaultBlockState(), Blocks.WHEAT.defaultBlockState()};
		for (int[] bed : new int[][] {{1, 2}, {5, 2}, {1, 6}, {5, 6}}) {
			for (int dx = 0; dx <= 2; dx++) {
				for (int dz = 0; dz <= 1; dz++) {
					int x = bed[0] + dx, z = bed[1] + dz;
					if (z == 6 && x >= 5 || z == 7 && x >= 5) {
						// (the back right bed is a mushroom patch)
						t.set(x, -1, z, Blocks.MYCELIUM.defaultBlockState());
						if ((x + z & 1) == 0) {
							t.set(x, 0, z, (x + z & 2) == 0 ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState());
						}
						continue;
					}
					t.set(x, -1, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
					t.set(x, 0, z, crops[Math.floorMod(bed[0] + bed[1], 3)].setValue(BlockStateProperties.AGE_7, 4 + (x + z) % 3));
				}
			}
		}
		// the pool, edged in spruce, a lantern post beside it
		t.set(4, -1, 4, Blocks.WATER.defaultBlockState());
		t.set(4, -1, 3, Blocks.WATER.defaultBlockState());
		t.set(4, -1, 5, Blocks.WATER.defaultBlockState());
		t.set(3, -1, 4, Blocks.WATER.defaultBlockState());
		t.set(5, -1, 4, Blocks.WATER.defaultBlockState());
		// the scarecrow, the compost bin
		t.set(7, 0, 4, fence(false));
		t.set(7, 1, 4, Blocks.HAY_BLOCK.defaultBlockState());
		t.set(7, 2, 4, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
		t.set(1, 0, 4, Blocks.COMPOSTER.defaultBlockState());
		return t.done("goblin_garden");
	}

	/**
	 * A footbridge (11 x 5): a mud brick bank each end, a pool between with lily pads, and a spruce walkway three wide over it
	 * on log piers, stairs up at each end, fence rails and a lantern post at each corner.
	 */
	static Blueprint footbridge() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 10; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean bank = x == 0 || x == 10 || z == 0 || z == 4;
				t.set(x, -1, z, bank ? Blocks.MUD_BRICKS.defaultBlockState() : Blocks.WATER.defaultBlockState());
			}
		}
		// the walkway: one plank wide, logs across it every other block, on piers in the water
		t.set(0, 0, 2, stairs(false, Direction.EAST));
		t.set(10, 0, 2, stairs(false, Direction.WEST));
		for (int x = 1; x <= 9; x++) {
			t.set(x, 0, 2, x % 2 == 0 ? logAlong(false, Direction.Axis.X) : planks(false));
		}
		for (int x : new int[] {3, 7}) {
			t.set(x, -1, 2, log(false));
		}
		// rails stand in the water either side; a post and a lantern at each end
		for (int x = 1; x <= 9; x++) {
			for (int z : new int[] {1, 3}) {
				boolean post = x == 1 || x == 9;
				t.set(x, 0, z, post ? log(false) : fence(false));
				t.set(x, 1, z, post ? log(false) : fence(false));
				if (post) {
					t.set(x, 2, z, lantern(false));
				}
			}
		}
		return t.done("goblin_footbridge");
	}

	// ---- (0.7.490) the orc holds: bigger, but crude -----------------------------------------------------------------------

	/**
	 * A bone hall (9 x 7): blackstone feet under dark oak log walls, bone-block corner posts crowned with skulls, a doorway
	 * between bone posts, a stepped roof of black hides striped brown with a ridge of spikes; inside, a fire pit, hay for
	 * bedding, long tables of fence and slab, hung with chains.
	 */
	static Blueprint boneHall() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int w = 9, d = 7;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				t.set(x, -1, z, floor(true, x, z));
				boolean onSide = x == 0 || x == w - 1, onEnd = z == 0 || z == d - 1;
				if (onSide || onEnd) {
					boolean corner = onSide && onEnd;
					for (int y = 0; y <= 2; y++) {
						if (z == 0 && x >= 3 && x <= 5) {
							continue;
						}
						t.set(x, y, z, corner ? Blocks.BONE_BLOCK.defaultBlockState() : y == 0 ? stone(true, x, y, z) : log(true));
					}
					if (corner) {
						t.set(x, 3, z, Blocks.BONE_BLOCK.defaultBlockState());
						t.set(x, 4, z, skull());
					}
				}
			}
		}
		// the doorway: bone posts and a log lintel
		for (int y = 0; y <= 2; y++) {
			t.set(2, y, 0, Blocks.BONE_BLOCK.defaultBlockState());
			t.set(6, y, 0, Blocks.BONE_BLOCK.defaultBlockState());
		}
		t.set(2, 3, 0, skull());
		t.set(6, 3, 0, skull());
		for (int x = 3; x <= 5; x++) {
			t.set(x, 3, 0, logAlong(true, Direction.Axis.X));
		}
		// the stepped hide roof
		for (int k = 0; k <= 4; k++) {
			for (int z = 0; z < d; z++) {
				boolean stripe = z % 3 == 1;
				BlockState hide = roof(true, stripe);
				if (k == 4) {
					t.set(4, 3 + k, z, hide);
				} else {
					t.set(k, 3 + k, z, hide);
					t.set(w - 1 - k, 3 + k, z, hide);
				}
			}
			for (int x = k + 1; x <= w - 2 - k && k < 4; x++) {
				if (!(k == 0 && x >= 2 && x <= 6)) {
					t.set(x, 3 + k, 0, roof(true, false));
				}
				t.set(x, 3 + k, d - 1, roof(true, false));
			}
		}
		// spikes along the ridge, the smoke hole over the fire
		for (int z = 0; z < d; z += 2) {
			t.set(4, 8, z, fence(true));
		}
		t.set(4, 9, 0, skull());
		t.set(4, 9, d - 1, skull());
		t.blocks.remove(new BlockPos(4, 7, 3));
		t.blocks.remove(new BlockPos(4, 7, 2));
		// within
		t.set(4, 0, 3, Blocks.CAMPFIRE.defaultBlockState());
		for (int z = 1; z <= 5; z++) {
			t.set(1, 0, z, z % 2 == 1 ? Blocks.HAY_BLOCK.defaultBlockState() : fence(true));
			t.set(7, 0, z, z % 2 == 1 ? Blocks.HAY_BLOCK.defaultBlockState() : fence(true));
		}
		for (int x = 2; x <= 6; x += 4) {
			t.set(x, 0, 2, fence(true));
			t.set(x, 1, 2, slab(true));
			t.set(x, 0, 4, fence(true));
			t.set(x, 1, 4, slab(true));
		}
		t.set(3, 2, 5, Blocks.IRON_CHAIN.defaultBlockState());
		t.set(3, 1, 5, lantern(true));
		t.set(5, 2, 5, Blocks.IRON_CHAIN.defaultBlockState());
		t.set(5, 1, 5, lantern(true));
		t.set(4, 0, 5, Blocks.BARREL.defaultBlockState());
		return t.done("orc_bone_hall");
	}

	/**
	 * A war ring (9 x 9): a fighting pit ringed with a double fence and bone posts crowned with skulls, a gap at the front,
	 * a trampled floor stained red at the middle, a chained post at its heart and weapon benches in the corners.
	 */
	static Blueprint warRing() {
		CampBuildings.Draft t = new CampBuildings.Draft();
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 8; z++) {
				boolean cross = Math.abs(x - 4) + Math.abs(z - 4) <= 2;
				t.set(x, -1, z, cross ? Blocks.DYED_TERRACOTTA.pick(DyeColor.RED).defaultBlockState() : floor(true, x, z));
				boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
				if (!edge || z == 0 && x >= 3 && x <= 5) {
					continue;
				}
				boolean post = x % 4 == 0 && z % 4 == 0 || x == 2 && z == 0 || x == 6 && z == 0;
				if (post) {
					for (int y = 0; y <= 2; y++) {
						t.set(x, y, z, Blocks.BONE_BLOCK.defaultBlockState());
					}
					t.set(x, 3, z, skull());
				} else {
					t.set(x, 0, z, fence(true));
					t.set(x, 1, z, fence(true));
					if ((x + z & 1) == 0) {
						t.set(x, 2, z, fence(true));
					}
				}
			}
		}
		// the post at the heart: chained, crowned with a skull
		for (int y = 0; y <= 3; y++) {
			t.set(4, y, 4, log(true));
		}
		t.set(4, 4, 4, skull());
		// the weapon benches
		t.set(1, 0, 1, Blocks.GRINDSTONE.defaultBlockState());
		t.set(7, 0, 1, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
		t.set(1, 0, 7, Blocks.BARREL.defaultBlockState());
		t.set(7, 0, 7, Blocks.BARREL.defaultBlockState());
		t.set(2, 0, 7, Blocks.CAMPFIRE.defaultBlockState());
		return t.done("orc_war_ring");
	}

	// ---- (0.7.491) the king city: the arena and the shaman's tower --------------------------------------------------------

	/**
	 * (0.7.491) Hordes patch 4: the arena (13 x 13) of a horde kingdom or goblin kingdom. A round fighting pit of sand ringed
	 * by three rising rows of stands (stairs of the camp's stone, facing in), a breast-high wall behind with fence on top,
	 * bone posts crowned with skulls (orcs) or lanterns on mud-brick posts (goblins) round the rim, and a gap in the front
	 * for the fighters to walk in.
	 */
	static Blueprint arena(boolean orc) {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int c = 6;
		for (int x = 0; x <= 12; x++) {
			for (int z = 0; z <= 12; z++) {
				double r = Math.hypot(x - c, z - c);
				if (r > 6.4) {
					continue;
				}
				boolean gap = z < c && Math.abs(x - c) <= 1;
				if (r <= 3.5) {
					t.set(x, -1, z, (x + z) % 5 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.SAND.defaultBlockState());
					continue;
				}
				t.set(x, -1, z, floor(orc, x, z));
				if (gap) {
					continue;
				}
				// the stands rise outward: a step at each ring, the wall behind
				int row = r <= 4.5 ? 0 : r <= 5.5 ? 1 : 2;
				Direction out = Math.abs(x - c) >= Math.abs(z - c) ? (x > c ? Direction.EAST : Direction.WEST) : (z > c ? Direction.SOUTH : Direction.NORTH);
				for (int y = 0; y < row; y++) {
					t.set(x, y, z, stone(orc, x, y, z));
				}
				if (row < 2) {
					t.set(x, row, z, standStep(orc, out));
				} else {
					t.set(x, 2, z, stone(orc, x, 2, z));
					t.set(x, 3, z, fence(orc));
				}
			}
		}
		// posts round the rim, crowned
		int[][] posts = {{2, 2}, {10, 2}, {2, 10}, {10, 10}, {0, 6}, {12, 6}, {6, 12}};
		for (int[] p : posts) {
			for (int y = 0; y <= 3; y++) {
				t.set(p[0], y, p[1], orc ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.MUD_BRICK_WALL.defaultBlockState());
			}
			t.set(p[0], 4, p[1], orc ? skull() : lantern(false));
		}
		// the gate posts, and a banner over the way in
		for (int y = 0; y <= 3; y++) {
			t.set(c - 2, y, 1, log(orc));
			t.set(c + 2, y, 1, log(orc));
		}
		for (int x = c - 2; x <= c + 2; x++) {
			t.set(x, 4, 1, logAlong(orc, Direction.Axis.X));
		}
		t.set(c, 4, 2, banner(orc, Direction.SOUTH));
		// the champion's stake at the far side of the pit
		t.set(c, 0, c + 3, log(orc));
		t.set(c, 1, c + 3, orc ? skull() : lantern(false));
		return t.done(orc ? "orc_arena" : "goblin_arena");
	}

	/** One step of the stands, its tall side outward. */
	private static BlockState standStep(boolean orc, Direction out) {
		return (orc ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS : Blocks.MUD_BRICK_STAIRS).defaultBlockState().setValue(StairBlock.FACING, out);
	}

	/**
	 * (0.7.491) The shaman's tower (5 x 5, twelve high) of a horde kingdom or goblin kingdom: walls of the camp's stone with
	 * slit windows, a door at the front, a ladder up the back wall to a flat top with battlements, and up there a brazier
	 * (soul fire for the orcs' shaman, a bubbling cauldron under a lantern for the goblins' brewer-witch), skulls or green
	 * banners on the merlons. A brewing stand and a barrel of herbs stand at its foot inside.
	 */
	static Blueprint shamanTower(boolean orc) {
		CampBuildings.Draft t = new CampBuildings.Draft();
		int top = 9;
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				t.set(x, -1, z, stone(orc, x, -1, z));
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				if (edge) {
					for (int y = 0; y < top; y++) {
						boolean slit = (y == 3 || y == 6) && (x == 2 || z == 2) && !(z == 0 && x == 2);
						t.set(x, y, z, slit ? Blocks.AIR.defaultBlockState() : stone(orc, x, y, z));
					}
					// the battlements: a merlon at every other block
					if ((x + z) % 2 == 0) {
						t.set(x, top + 1, z, stone(orc, x, top + 1, z));
						t.set(x, top + 2, z, orc ? skull() : Blocks.AIR.defaultBlockState());
					}
				}
				if (!(x == 2 && z == 3)) {
					t.set(x, top, z, edge ? stone(orc, x, top, z) : planks(orc));
				}
			}
		}
		door(t, 2, 0, 0, Direction.NORTH);
		for (int y = 0; y <= top; y++) {
			t.set(2, y, 3, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		}
		t.set(1, 0, 3, Blocks.BREWING_STAND.defaultBlockState());
		t.set(3, 0, 3, Blocks.BARREL.defaultBlockState());
		t.set(1, 0, 1, lantern(false));
		if (orc) {
			t.set(2, top + 1, 1, Blocks.SOUL_CAMPFIRE.defaultBlockState());
		} else {
			t.set(2, top + 1, 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			t.set(1, top + 1, 1, Blocks.CAMPFIRE.defaultBlockState());
			t.set(2, top - 2, -1, facing(Blocks.WALL_BANNER.pick(DyeColor.GREEN).defaultBlockState(), Direction.NORTH));
		}
		return t.done(orc ? "orc_shaman_tower" : "goblin_brewer_tower");
	}
}
