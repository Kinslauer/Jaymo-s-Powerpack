package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * (0.7.518) What the patrons' wild temples are built of, all our own. Each is drawn at once (a sketch in its own frame, the
 * doors on its +z side), then turned to face the way out. Halls carved into stone or built in the Nether are walled and roofed
 * in their own stone so nothing floods; containers are left empty (the champion's head is the prize).
 */
final class TempleBuildings {
	/** How far each reaches from its centre (blocks) on each side. */
	static int radius(Patron p) {
		return switch (p) {
			case HEARTHKEEPER, GREEN_HUNTSMAN -> 9;
			case ANVIL_FATHER, DEEP_MOTHER, CROWNED_SUN -> 10;
			case LANTERN_WARDEN, ASH_WHISPERER, TRUTH -> 8;
		};
	}

	/** How far below the floor the ground is filled, and how far above it cleared. */
	static final int FILL_DEPTH = 6, CLEAR_HEIGHT = 10, GLADE_CLEAR = 16;
	/** The crypt's depth below the yard, the length of a carved hall's tunnel out. */
	static final int CRYPT_DEPTH = 10, TUNNEL = 24;

	/** Where it stands: its floor centre, its altar and champion's spot, and the way out. */
	record Layout(BlockPos center, BlockPos altar, BlockPos championSpot, Direction facing) {
	}

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private TempleBuildings() {
	}

	private static BlockState named(String id, Block fallback) {
		return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(id)).orElse(fallback).defaultBlockState();
	}

	private static BlockState state(Block b) {
		return b.defaultBlockState();
	}

	private static Rotation rotation(Direction facing) {
		return switch (facing) {
			case WEST -> Rotation.CLOCKWISE_90;
			case NORTH -> Rotation.CLOCKWISE_180;
			case EAST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** Builds {@code p}'s temple at {@code site} (its floor centre) in {@code level}'s own manner. */
	static Layout build(ServerLevel level, Patron p, BlockPos site, RandomSource random) {
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		boolean nether = Stacked.at(level, site, net.minecraft.world.level.Level.NETHER);
		int r = radius(p);
		Buildings.Sketch s;
		int cx = r, cz = r, y0 = 0;
		int[] altar;
		int[] champ;
		boolean carved = false;
		switch (p) {
			case HEARTHKEEPER -> {
				s = new Buildings.Sketch("hearth_hall", 2 * r + 1, 2 * r + 1, 18);
				hearthHall(s, r);
				altar = new int[] {9, 1, 6};
				champ = new int[] {9, 1, 8};
				ground(level, site, r, Blocks.STONE.defaultBlockState(), CLEAR_HEIGHT);
			}
			case ANVIL_FATHER -> {
				boolean deep = !nether;
				s = new Buildings.Sketch("forge_hall", 2 * r + 1, 2 * r + 1 + (deep ? TUNNEL : 0), 12);
				forgeHall(s, r, nether, deep);
				altar = new int[] {10, 2, 5};
				champ = new int[] {10, 1, 8};
				carved = deep;
				if (!deep) {
					ground(level, site, r, state(Blocks.BLACKSTONE), CLEAR_HEIGHT);
				}
			}
			case DEEP_MOTHER -> {
				s = new Buildings.Sketch("tide_court", 2 * r + 1, 2 * r + 1, 12);
				tideCourt(s, r);
				altar = new int[] {10, 2, 3};
				champ = new int[] {10, 1, 7};
				pillars(level, site, r, facing);
			}
			case GREEN_HUNTSMAN -> {
				s = new Buildings.Sketch("hunters_glade", 2 * r + 1, 2 * r + 1, 10);
				glade(s, r, random);
				altar = new int[] {9, 1, 3};
				champ = new int[] {9, 1, 7};
				ground(level, site, r, state(Blocks.DIRT), GLADE_CLEAR);
			}
			case LANTERN_WARDEN -> {
				int h = nether ? 12 : CRYPT_DEPTH + 16;
				s = new Buildings.Sketch("graveyard", 2 * r + 1, 21, h);
				if (nether) {
					crypt(s, 0, false);
					altar = new int[] {8, 1, 2};
					champ = new int[] {8, 1, 5};
					ground(level, site, 10, state(Blocks.SOUL_SOIL), CLEAR_HEIGHT);
					cz = 10;
				} else {
					y0 = CRYPT_DEPTH + 2;
					crypt(s, 2, true);
					yard(s, y0);
					altar = new int[] {8, 3, 2};
					champ = new int[] {8, 3, 5};
					cz = 12;
					ground(level, site, r, state(Blocks.DIRT), CLEAR_HEIGHT);
				}
			}
			case CROWNED_SUN -> {
				s = new Buildings.Sketch("sun_court", 2 * r + 1, 2 * r + 1, 10);
				sunCourt(s, r);
				altar = new int[] {10, 2, 3};
				champ = new int[] {10, 1, 7};
				ground(level, site, r, state(Blocks.STONE), CLEAR_HEIGHT);
			}
			case ASH_WHISPERER -> {
				s = new Buildings.Sketch("ash_hollow", 2 * r + 1, 2 * r + 1, 10);
				ashHollow(s, r, nether);
				altar = new int[] {8, 1, 3};
				champ = new int[] {8, 1, 7};
				carved = !nether;
				if (nether) {
					ground(level, site, r, state(Blocks.BLACKSTONE), CLEAR_HEIGHT);
				}
			}
			default -> {
				s = new Buildings.Sketch("ruined_cathedral", GreatTemple.W, GreatTemple.D, GreatTemple.H);
				BlockState wall = state(Blocks.STONE_BRICKS);
				GreatTemple.Look look = new GreatTemple.Look(wall, state(Blocks.MOSSY_STONE_BRICKS), state(Blocks.CRACKED_STONE_BRICKS),
					state(Blocks.CHISELED_STONE_BRICKS), state(Blocks.STONE_BRICKS), state(Blocks.STONE_BRICK_STAIRS), state(Blocks.STONE_BRICK_SLAB),
					state(Blocks.SPRUCE_PLANKS), state(Blocks.GLASS_PANE), named("red_carpet", Blocks.MOSS_CARPET));
				GreatTemple.nave(s::set, look, true, random);
				s.set(GreatTemple.ALTAR_X, GreatTemple.ALTAR_Y, GreatTemple.ALTAR_Z, GreatTemple.altar());
				cx = 6;
				cz = 9;
				altar = new int[] {GreatTemple.ALTAR_X, GreatTemple.ALTAR_Y, GreatTemple.ALTAR_Z};
				champ = new int[] {6, 2, GreatTemple.D - 1 - 5};
				ground(level, site, 10, state(Blocks.STONE), 22);
			}
		}
		if (p == Patron.ASH_WHISPERER && !nether) {
			s.set(r, 8, r, AIR);
		}
		Blueprint bp = s.done();
		if (p != Patron.TRUTH && p != Patron.LANTERN_WARDEN) {
			cz = r;
		}
		place(level, bp, cx, cz, y0, site, facing, carved);
		BlockPos at = toWorld(site, cx, cz, y0, altar, facing);
		BlockPos spot = toWorld(site, cx, cz, y0, champ, facing);
		if (p == Patron.ASH_WHISPERER && !nether) {
			shaft(level, site.offset(0, 0, 0), r, facing);
		}
		if (p == Patron.ANVIL_FATHER && !nether) {
			tunnelShaft(level, toWorld(site, cx, cz, y0, new int[] {10, 1, 2 * r + TUNNEL - 1}, facing), facing);
		}
		// the ruin is drawn flipped (doors on local -z), so its way out is the other way
		return new Layout(site, at, spot, p == Patron.TRUTH ? facing.getOpposite() : facing);
	}

	private static BlockPos toWorld(BlockPos site, int cx, int cz, int y0, int[] local, Direction facing) {
		return site.offset(new BlockPos(local[0] - cx, local[1] - y0, local[2] - cz).rotate(rotation(facing)));
	}

	/** Lays the sketch, turned to face {@code facing}, its (cx, y0, cz) at {@code center}. */
	private static void place(ServerLevel level, Blueprint bp, int cx, int cz, int y0, BlockPos center, Direction facing, boolean carved) {
		Rotation rot = rotation(facing);
		for (Blueprint.Placement pl : bp.blocks()) {
			BlockPos off = new BlockPos(pl.offset().getX() - cx, pl.offset().getY() - y0, pl.offset().getZ() - cz).rotate(rot);
			level.setBlock(center.offset(off), pl.state().rotate(rot), Block.UPDATE_ALL);
		}
	}

	/** Fills the ground under the square, clears the air over it. */
	private static void ground(ServerLevel level, BlockPos center, int r, BlockState fill, int clear) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				for (int y = -FILL_DEPTH; y < 0; y++) {
					BlockState was = level.getBlockState(pos.set(center.getX() + a, center.getY() + y, center.getZ() + b));
					if (was.isAir() || !was.getFluidState().isEmpty() || was.canBeReplaced()) {
						level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
					}
				}
				for (int y = 0; y <= clear; y++) {
					pos.set(center.getX() + a, center.getY() + y, center.getZ() + b);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, AIR, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	private static void box(Buildings.Sketch s, int x0, int y0, int z0, int x1, int y1, int z1, BlockState st) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					s.set(x, y, z, st);
				}
			}
		}
	}

	/** A walled room: wall on its sides, floor and ceiling (null: left open, a rim on top), air inside. */
	private static void shell(Buildings.Sketch s, int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall, BlockState floor, BlockState ceil) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					boolean side = x == x0 || x == x1 || z == z0 || z == z1;
					if (y == y0) {
						if (floor != null) {
							s.set(x, y, z, floor);
						}
					} else if (y == y1 && ceil != null) {
						s.set(x, y, z, ceil);
					} else {
						s.set(x, y, z, side ? wall : AIR);
					}
				}
			}
		}
	}

	// ---- the hearth-hall -----------------------------------------------------------------------------------------------------

	private static void hearthHall(Buildings.Sketch s, int r) {
		int n = 2 * r;
		BlockState bricks = state(Blocks.STONE_BRICKS), post = state(Blocks.SPRUCE_LOG), stairs = state(Blocks.SPRUCE_STAIRS);
		shell(s, 0, 0, 0, n, 7, n, bricks, state(Blocks.STONE_BRICKS), null);
		for (int i = 0; i <= n; i++) {
			if (i % 4 == 0) {
				for (int y = 1; y <= 6; y++) {
					for (int[] c : new int[][] {{0, i}, {n, i}, {i, 0}, {i, n}}) {
						s.set(c[0], y, c[1], post);
					}
				}
			} else if (i % 4 == 2) {
				for (int y = 3; y <= 4; y++) {
					for (int[] c : new int[][] {{0, i}, {n, i}, {i, 0}}) {
						s.set(c[0], y, c[1], state(Blocks.GLASS_PANE));
					}
				}
			}
		}
		for (int y = 1; y <= 3; y++) {
			for (int x = 8; x <= 10; x++) {
				s.set(x, y, n, AIR);
			}
		}
		s.set(7, 4, n, state(Blocks.LANTERN));
		s.hip(0, n, 0, n, 7, stairs, state(Blocks.SPRUCE_SLAB));
		// the chimney and the hearth behind bars
		box(s, 7, 1, 0, 11, 9, 4, state(Blocks.BRICKS));
		box(s, 8, 1, 1, 10, 3, 3, AIR);
		for (int x = 8; x <= 10; x++) {
			s.set(x, 1, 2, state(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, true));
			for (int y = 1; y <= 3; y++) {
				s.set(x, y, 4, state(Blocks.IRON_BARS));
			}
		}
		// benches along the side walls
		for (int z = 6; z <= 14; z += 2) {
			s.set(1, 1, z, Buildings.Sketch.stair(stairs, Direction.WEST));
			s.set(n - 1, 1, z, Buildings.Sketch.stair(stairs, Direction.EAST));
		}
		s.set(9, 1, 6, altar(Patron.HEARTHKEEPER));
	}

	static BlockState altar(Patron p) {
		return Healing.SHRINE.defaultBlockState().setValue(Shrine.DEDICATION, Shrine.Dedication.of(p));
	}

	// ---- the forge-hall ------------------------------------------------------------------------------------------------------

	private static void forgeHall(Buildings.Sketch s, int r, boolean nether, boolean deep) {
		int n = 2 * r;
		BlockState wall = nether ? state(Blocks.NETHER_BRICKS) : state(Blocks.DEEPSLATE_BRICKS);
		BlockState floor = nether ? state(Blocks.POLISHED_BLACKSTONE_BRICKS) : state(Blocks.DEEPSLATE_TILES);
		BlockState pillar = nether ? state(Blocks.POLISHED_BLACKSTONE) : state(Blocks.POLISHED_DEEPSLATE);
		shell(s, 0, 0, 0, n, 9, n, wall, floor, wall);
		for (int z = 5; z <= 15; z += 5) {
			for (int y = 1; y <= 8; y++) {
				s.set(1, y, z, pillar);
				s.set(n - 1, y, z, pillar);
			}
		}
		// lava in channels behind bars, both sides: sealed by wall, floor and ceiling
		for (int z = 2; z <= n - 2; z++) {
			for (int side : new int[] {1, n - 1}) {
				if (z % 5 == 0) {
					continue;
				}
				s.set(side, 0, z, wall);
				s.set(side, 1, z, state(Blocks.LAVA));
				s.set(side, 2, z, wall);
				int bars = side == 1 ? 2 : n - 2;
				for (int y = 1; y <= 3; y++) {
					s.set(bars, y, z, state(Blocks.IRON_BARS));
				}
			}
		}
		for (int side : new int[] {1, n - 1}) {
			for (int y = 1; y <= 3; y++) {
				s.set(side, y, 1, pillar);
				s.set(side, y, n - 1, pillar);
			}
		}
		// the doorway
		for (int y = 1; y <= 3; y++) {
			for (int x = 9; x <= 11; x++) {
				s.set(x, y, n, AIR);
			}
		}
		if (deep) {
			// a lined tunnel out, five wide and four high; its far end is walled; a ladder shaft rises from it (tunnelShaft)
			shell(s, 8, 0, n + 1, 12, 4, n + TUNNEL, state(Blocks.DEEPSLATE_BRICKS), floor, state(Blocks.DEEPSLATE_BRICKS));
			for (int y = 1; y <= 3; y++) {
				for (int x = 9; x <= 11; x++) {
					for (int z = n + 1; z < n + TUNNEL; z++) {
						s.set(x, y, z, AIR);
					}
				}
			}
		}
		// anvils and blast furnaces along the back
		for (int x = 4; x <= n - 4; x += 3) {
			if (x >= 9 && x <= 11) {
				continue;
			}
			s.set(x, 1, 1, state(Blocks.BLAST_FURNACE));
			s.set(x + 1, 1, 2, state(Blocks.ANVIL));
		}
		// the dais and the altar
		box(s, 9, 1, 4, 11, 1, 6, state(Blocks.IRON_BLOCK));
		s.set(10, 2, 5, altar(Patron.ANVIL_FATHER));
	}

	// ---- the tide-court ------------------------------------------------------------------------------------------------------

	private static void tideCourt(Buildings.Sketch s, int r) {
		int n = 2 * r;
		BlockState bricks = state(Blocks.PRISMARINE_BRICKS), dark = state(Blocks.DARK_PRISMARINE);
		for (int x = 0; x <= n; x++) {
			for (int z = 0; z <= n; z++) {
				boolean edge = x == 0 || z == 0 || x == n || z == n;
				s.set(x, 0, z, edge || (x + z) % 7 == 0 ? dark : bricks);
				if (edge) {
					s.set(x, 1, z, state(Blocks.PRISMARINE_WALL));
				}
			}
		}
		// the stairs' gaps in the low wall
		for (int i = 9; i <= 11; i++) {
			s.set(i, 1, n, AIR);
			s.set(i, 1, 0, AIR);
			s.set(0, 1, i, AIR);
			s.set(n, 1, i, AIR);
		}
		for (int[] c : new int[][] {{1, 1}, {n - 1, 1}, {1, n - 1}, {n - 1, n - 1}}) {
			for (int y = 1; y <= 4; y++) {
				s.set(c[0], y, c[1], bricks);
			}
			s.set(c[0], 5, c[1], state(Blocks.SEA_LANTERN));
		}
		// the pool
		for (int x = 7; x <= 13; x++) {
			for (int z = 11; z <= 17; z++) {
				s.set(x, 0, z, state(Blocks.WATER));
			}
		}
		box(s, 8, 1, 1, 12, 1, 5, bricks);
		s.set(10, 2, 3, altar(Patron.DEEP_MOTHER));
	}

	/** Pillars under the court, from the sea floor up, so it stands on the water. */
	private static void pillars(ServerLevel level, BlockPos site, int r, Direction facing) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState pillar = state(Blocks.PRISMARINE_BRICKS);
		for (int a = -r; a <= r; a += 5) {
			for (int b = -r; b <= r; b += 5) {
				for (int y = -1; y >= -24; y--) {
					pos.set(site.getX() + a, site.getY() + y, site.getZ() + b);
					BlockState was = level.getBlockState(pos);
					if (was.isSolid() && was.getFluidState().isEmpty()) {
						break;
					}
					level.setBlock(pos, pillar, Block.UPDATE_CLIENTS);
				}
			}
		}
		// the court's under-floor, so the lanes between the pillars hold up
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				level.setBlock(site.offset(a, -1, b), pillar, Block.UPDATE_CLIENTS);
			}
		}
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				for (int y = 1; y <= 6; y++) {
					pos.set(site.getX() + a, site.getY() + y, site.getZ() + b);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, AIR, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	// ---- the hunter's glade --------------------------------------------------------------------------------------------------

	private static void glade(Buildings.Sketch s, int r, RandomSource random) {
		int n = 2 * r;
		for (int x = 0; x <= n; x++) {
			for (int z = 0; z <= n; z++) {
				s.set(x, 0, z, random.nextFloat() < 0.35F ? state(Blocks.COARSE_DIRT) : state(Blocks.MOSS_BLOCK));
			}
		}
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6.0;
			int x = r + (int)Math.round(Math.cos(a) * 7.0), z = r + (int)Math.round(Math.sin(a) * 7.0);
			for (int y = 1; y <= 4; y++) {
				s.set(x, y, z, state(Blocks.DARK_OAK_LOG));
			}
			s.set(x, 5, z, state(Blocks.BONE_BLOCK));
		}
		// the lodge: four log posts and a hide awning over the altar
		for (int[] c : new int[][] {{6, 1}, {12, 1}, {6, 5}, {12, 5}}) {
			for (int y = 1; y <= 3; y++) {
				s.set(c[0], y, c[1], state(Blocks.SPRUCE_LOG));
			}
		}
		box(s, 6, 4, 0, 12, 4, 6, Blocks.WOOL.pick(DyeColor.BROWN).defaultBlockState());
		for (int[] c : new int[][] {{3, 4}, {15, 5}, {2, 12}, {16, 12}, {5, 15}, {13, 15}}) {
			s.set(c[0], 1, c[1], state(Blocks.HAY_BLOCK));
		}
		for (int[] c : new int[][] {{4, 9}, {14, 9}, {9, 14}, {7, 12}}) {
			s.set(c[0], 1, c[1], state(Blocks.DEAD_BUSH));
		}
		s.set(9, 1, 3, altar(Patron.GREEN_HUNTSMAN));
	}

	// ---- the graveyard and crypt ---------------------------------------------------------------------------------------------

	/** A vault 17 x 21 from sketch height {@code floor}: stone bricks, 7 high; {@code roof} false leaves it open. */
	private static void crypt(Buildings.Sketch s, int floor, boolean roof) {
		BlockState bricks = state(Blocks.STONE_BRICKS), deep = state(Blocks.POLISHED_DEEPSLATE);
		shell(s, 0, floor, 0, 16, floor + 7, 20, bricks, deep, roof ? bricks : null);
		BlockState coffin = state(Blocks.DARK_OAK_SLAB);
		for (int z = 3; z <= 17; z += 4) {
			s.set(1, floor + 1, z, coffin);
			s.set(1, floor + 1, z + 1, coffin);
			s.set(15, floor + 1, z, coffin);
			s.set(15, floor + 1, z + 1, coffin);
		}
		for (int z = 4; z <= 16; z += 6) {
			for (int x : new int[] {4, 12}) {
				s.set(x, floor + 7, z, state(Blocks.IRON_CHAIN));
				s.set(x, floor + 6, z, state(Blocks.SOUL_LANTERN).setValue(LanternBlock.HANGING, true));
			}
		}
		s.set(8, floor + 1, 2, altar(Patron.LANTERN_WARDEN));
		if (!roof) {
			// the vault open on the valley floor: a doorway in the front wall
			for (int y = floor + 1; y <= floor + 3; y++) {
				for (int x = 7; x <= 9; x++) {
					s.set(x, y, 20, AIR);
				}
			}
		}
	}

	/** The yard over the crypt: fenced in iron bars, headstones, and a stair down. */
	private static void yard(Buildings.Sketch s, int y0) {
		BlockState moss = state(Blocks.MOSSY_COBBLESTONE);
		for (int x = 0; x <= 16; x++) {
			for (int z = 4; z <= 20; z++) {
				s.set(x, y0, z, (x + z) % 5 == 0 ? state(Blocks.COARSE_DIRT) : state(Blocks.PODZOL));
				boolean edge = x == 0 || x == 16 || z == 4 || z == 20;
				if (edge && !(z == 20 && x >= 7 && x <= 9)) {
					s.set(x, y0 + 1, z, state(Blocks.IRON_BARS));
					s.set(x, y0 + 2, z, state(Blocks.IRON_BARS));
				}
			}
		}
		for (int z = 7; z <= 17; z += 3) {
			for (int x : new int[] {3, 5, 11, 13}) {
				s.set(x, y0 + 1, z, moss);
				s.set(x, y0 + 2, z, state(Blocks.MOSSY_COBBLESTONE_SLAB));
			}
		}
		// the stair down: ten steps toward the vault's back, three wide
		for (int i = 0; i < CRYPT_DEPTH; i++) {
			int y = y0 - 1 - i, z = 18 - i;
			for (int x = 7; x <= 9; x++) {
				s.set(x, y, z, Buildings.Sketch.stair(state(Blocks.STONE_BRICK_STAIRS), Direction.SOUTH));
				for (int h = 1; h <= 3; h++) {
					s.set(x, y + h, z, AIR);
				}
			}
		}
		for (int x = 7; x <= 9; x++) {
			s.set(x, y0, 19, AIR);
			s.set(x, y0, 18, AIR);
		}
	}

	// ---- the sun-court -------------------------------------------------------------------------------------------------------

	private static void sunCourt(Buildings.Sketch s, int r) {
		int n = 2 * r;
		for (int x = 0; x <= n; x++) {
			for (int z = 0; z <= n; z++) {
				boolean edge = x == 0 || z == 0 || x == n || z == n;
				s.set(x, 0, z, edge ? state(Blocks.CUT_SANDSTONE) : state(Blocks.SMOOTH_QUARTZ));
			}
		}
		for (int[] c : new int[][] {{2, 2}, {10, 2}, {18, 2}, {2, 10}, {18, 10}, {2, 18}, {10, 18}, {18, 18}}) {
			for (int y = 1; y <= 5; y++) {
				s.set(c[0], y, c[1], state(Blocks.QUARTZ_PILLAR));
			}
			s.set(c[0], 6, c[1], state(Blocks.GLOWSTONE));
		}
		for (int[] c : new int[][] {{0, 0}, {n, 0}, {0, n}, {n, n}}) {
			s.set(c[0], 0, c[1], state(Blocks.GOLD_BLOCK));
		}
		box(s, 8, 1, 1, 12, 1, 5, state(Blocks.QUARTZ_BLOCK));
		s.set(10, 2, 3, altar(Patron.CROWNED_SUN));
	}

	// ---- the ash-hollow ------------------------------------------------------------------------------------------------------

	private static void ashHollow(Buildings.Sketch s, int r, boolean nether) {
		int n = 2 * r;
		BlockState wall = state(Blocks.BLACKSTONE), floor = state(Blocks.BASALT);
		shell(s, 0, 0, 0, n, 8, n, wall, floor, wall);
		for (int[] c : new int[][] {{1, 1}, {n - 1, 1}, {1, n - 1}, {n - 1, n - 1}}) {
			s.set(c[0], 0, c[1], state(Blocks.MAGMA_BLOCK));
			s.set(c[0], 1, c[1], state(Blocks.COAL_BLOCK));
		}
		BlockState banner = named("black_banner", Blocks.COAL_BLOCK);
		for (int x : new int[] {3, 13}) {
			s.set(x, 1, 2, banner);
		}
		for (int x = 6; x <= 10; x++) {
			for (int y = 3; y <= 7; y++) {
				s.set(x, y, 1, state(Blocks.IRON_CHAIN));
			}
		}
		s.set(8, 1, 3, altar(Patron.ASH_WHISPERER));
		if (nether) {
			for (int y = 1; y <= 3; y++) {
				for (int x = 7; x <= 9; x++) {
					s.set(x, y, n, AIR);
				}
			}
		}
	}

	/** The hollow's shaft: a one-block way up to the surface from the hollow's floor, ladders on one side, lined so nothing floods in. */
	private static void shaft(ServerLevel level, BlockPos site, int r, Direction facing) {
		BlockPos top = site.above(8);
		int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.getX(), top.getZ());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState lining = state(Blocks.COBBLESTONE);
		// the rungs go down inside the hollow too, to its floor, so nobody drops or gets stuck (backed from the south, clear of the altar and the champion)
		for (int y = site.getY() + 1; y < top.getY(); y++) {
			pos.set(top.getX(), y, top.getZ());
			level.setBlock(pos.south(), lining, Block.UPDATE_CLIENTS);
			level.setBlock(pos, state(Blocks.LADDER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
		}
		for (int y = top.getY(); y <= surface; y++) {
			pos.set(top.getX(), y, top.getZ());
			level.setBlock(pos, AIR, Block.UPDATE_CLIENTS);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(d);
				BlockState was = level.getBlockState(side);
				if (!was.isSolid() || !was.getFluidState().isEmpty()) {
					level.setBlock(side, lining, Block.UPDATE_CLIENTS);
				}
			}
			level.setBlock(pos, state(Blocks.LADDER).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
			level.setBlock(pos.north(), lining, Block.UPDATE_CLIENTS);
		}
		BlockPos bush = new BlockPos(top.getX() + 1, surface, top.getZ());
		if (level.getBlockState(bush).isAir() && level.getBlockState(bush.below()).isSolid()) {
			level.setBlock(bush, state(Blocks.DEAD_BUSH), Block.UPDATE_CLIENTS);
		}
	}

	/** The deep forge-hall's tunnel end: a ladder shaft up to the surface, lined, so the hall can be found and left. */
	private static void tunnelShaft(ServerLevel level, BlockPos end, Direction facing) {
		if (!level.isLoaded(end) || Factions.at(level.getServer(), level, end) != null) {
			return;
		}
		int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, end.getX(), end.getZ());
		BlockState lining = state(Blocks.DEEPSLATE_BRICKS);
		BlockState ladder = state(Blocks.LADDER).setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getOpposite());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = end.getY(); y <= surface; y++) {
			pos.set(end.getX(), y, end.getZ());
			level.setBlock(pos, AIR, Block.UPDATE_CLIENTS);
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(d);
				BlockState was = level.getBlockState(side);
				if (y < surface && y > end.getY() + 2 && (!was.isSolid() || !was.getFluidState().isEmpty())) {
					level.setBlock(side, lining, Block.UPDATE_CLIENTS);
				}
			}
			level.setBlock(pos, ladder, Block.UPDATE_CLIENTS);
		}
	}
}
