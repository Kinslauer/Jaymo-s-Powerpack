package com.jaymoskingdoms;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.7.250) The Blossom land's way of building: the cherry groves are the pack's Japan, as the north is its Norse lands
 * and the south its sands ({@link Climate#CHERRY}, the Blossom tongue in {@link Languages}, guards in the ronin
 * harness in {@link Outfits}). Andy (2026-10-27): the cherry blossom village is "perfect for the unused japanese/samurai
 * armor we have. they can be the analogue for japan in the pack like we have for most other locations."
 * <p>
 * A settlement among the cherry trees builds its houses as minka here: a dark timber frame over a stone footing,
 * boards below and white plaster above, paper windows, tatami floors, futons, a low table, a paper lantern under a wide
 * hipped roof of dark tile that overhangs on every side, and often a veranda (engawa) along the front. Its other
 * buildings take the same dark tile and plaster ({@link Buildings.Kit}). A Blossom village ({@link BlossomVillages})
 * also has a torii at its gate and a shrine at its heart. Drawn like every design: front on the north side (z = 0),
 * y = 0 the floor laid in place of the ground.
 */
final class Blossom {
	/** The frame's dark timber, the paper of the windows, the tatami, a futon, the torii's vermilion (all looked up by name). */
	private static final BlockState TIMBER = named("stripped_dark_oak_log", Blocks.STRIPPED_SPRUCE_LOG);
	private static final BlockState BEAM = named("dark_oak_log", Blocks.SPRUCE_LOG);
	private static final BlockState PAPER = named("white_stained_glass_pane", Blocks.GLASS_PANE);
	private static final BlockState TATAMI = named("bamboo_mosaic", Blocks.BIRCH_PLANKS);
	private static final BlockState FUTON = named("white_bed", Blocks.HAY_BLOCK);
	private static final BlockState VERMILION = named("red_concrete", Blocks.MANGROVE_PLANKS);
	private static final BlockState BONSAI = named("potted_cherry_sapling", Blocks.FLOWER_POT);

	private Blossom() {
	}

	/**
	 * A minka: 7 or 9 wide, 7 or 9 deep, inside a ring of overhanging roof (the blueprint is two wider and deeper). Two
	 * futons when it's 9 wide (sometimes when it's 7), an engawa along the front half the time when it's 9 deep.
	 */
	static Blueprint minka(Buildings.Kit k) {
		int w = k.pickInt(7, 9, 7), d = k.pickInt(7, 9, 9);
		boolean engawa = d == 9 && k.chance(0.5);
		// the house's walls run x 1..w, z front..d; the roof covers 0..w+1, 0..d+1
		int front = engawa ? 3 : 1;
		Buildings.Sketch s = new Buildings.Sketch("house", w + 2, d + 2, 16);
		BlockState footing = k.p.wall();
		int mid = (w + 1) / 2, zm = (front + d) / 2;
		for (int x = 1; x <= w; x++) {
			for (int z = front; z <= d; z++) {
				boolean alongX = z == front || z == d;
				boolean edge = x == 1 || x == w || alongX;
				s.set(x, 0, z, edge ? footing : TATAMI);
				if (!edge) {
					continue;
				}
				// posts at the corners, framing the door (and the middle of the back), and halfway along each side
				boolean post = (x == 1 || x == w) && alongX || (alongX ? Math.abs(x - mid) == 1 : z == zm);
				for (int y = 1; y <= 3; y++) {
					s.set(x, y, z, post ? TIMBER : y == 1 ? k.p.planks() : k.infill);
				}
				// the top plate the rafters sit on
				s.set(x, 4, z, post ? TIMBER : Buildings.Sketch.lying(BEAM, alongX ? Direction.Axis.X : Direction.Axis.Z));
			}
		}
		s.door(mid, 1, front, Direction.NORTH, k.door);
		// paper windows: either side of the door, either side of each side's middle post, the back
		for (int z : new int[] {front, d}) {
			s.set(mid - 2, 2, z, PAPER);
			s.set(mid + 2, 2, z, PAPER);
		}
		for (int x : new int[] {1, w}) {
			s.set(x, 2, zm - 1, PAPER);
			s.set(x, 2, zm + 1, PAPER);
		}
		// the engawa: a plank deck under the roof's front, two posts and a beam
		if (engawa) {
			for (int x = 1; x <= w; x++) {
				for (int z = 1; z < front; z++) {
					s.set(x, 0, z, k.p.planks());
				}
				s.set(x, 4, 1, Buildings.Sketch.lying(BEAM, Direction.Axis.X));
			}
			for (int y = 1; y <= 3; y++) {
				s.set(1, y, 1, TIMBER);
				s.set(w, y, 1, TIMBER);
			}
		}
		// the roof: hipped, dark tile, overhanging all round
		s.hip(0, w + 1, 0, d + 1, 4, k.roofStairs, k.roofSlab);
		if (engawa) {
			// the front wall stands back under the roof: plaster up to it
			for (int x = 2; x < w; x++) {
				int roof = 4 + Math.min(Math.min(x, w + 1 - x), front);
				for (int y = 5; y < roof; y++) {
					s.set(x, y, front, k.infill);
				}
			}
		}
		int ridge = 4 + (Math.min(w, d) + 1) / 2;
		// inside: futons at the back, a low table on the tatami, a bonsai, a barrel, a paper lantern under the ridge
		s.bed(2, 1, d - 2, Direction.SOUTH, FUTON);
		if (w >= 9 || k.chance(0.5)) {
			s.bed(w - 1, 1, d - 2, Direction.SOUTH, FUTON);
		}
		s.set(mid, 1, zm, Buildings.Sketch.facing(k.p.trapdoor().getBlock(), Direction.NORTH));
		s.set(2, 1, front + 1, BONSAI);
		s.set(w - 1, 1, front + 1, Blocks.BARREL.defaultBlockState());
		s.set(mid, ridge - 1, (d + 1) / 2, Buildings.Sketch.hanging(Blocks.LANTERN));
		// a lantern by the door, hung from the eaves
		if (k.chance(0.6)) {
			s.set(mid + 1, 3, 0, Buildings.Sketch.hanging(Blocks.LANTERN));
		}
		return s.done();
	}

	/**
	 * A torii (7 x 1): two vermilion pillars, the tie beam through them, and the black lintel on top, its ends turned up.
	 * Stands over a village's road where it comes in.
	 */
	static Blueprint torii() {
		Buildings.Sketch s = new Buildings.Sketch("blossom_torii", 7, 1, 8);
		for (int y = 1; y <= 4; y++) {
			s.set(1, y, 0, VERMILION);
			s.set(5, y, 0, VERMILION);
		}
		for (int x = 0; x <= 6; x++) {
			s.set(x, 3, 0, VERMILION);
		}
		BlockState lintel = Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState();
		BlockState stairs = Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState();
		for (int x = 1; x <= 5; x++) {
			s.set(x, 5, 0, lintel);
		}
		s.set(0, 5, 0, Buildings.Sketch.stair(stairs, Direction.WEST));
		s.set(6, 5, 0, Buildings.Sketch.stair(stairs, Direction.EAST));
		// the plaque between the beams
		s.set(3, 4, 0, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		return s.done();
	}

	/**
	 * A shrine (7 x 7): a stone platform up two steps, a hall on vermilion posts with a plank back wall, an offering box
	 * at its open front, a hipped roof of dark tile, and a stone lantern either side of the steps.
	 */
	static Blueprint shrine() {
		Buildings.Sketch s = new Buildings.Sketch("blossom_shrine", 7, 7, 12);
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, stone);
				if (z >= 1) {
					s.set(x, 1, z, z == 1 ? Buildings.Sketch.stair(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.SOUTH) : stone);
				}
			}
		}
		// the hall, x 1..5, z 2..6, its floor one up
		for (int x = 1; x <= 5; x++) {
			for (int z = 2; z <= 6; z++) {
				boolean corner = (x == 1 || x == 5) && (z == 2 || z == 6);
				if (corner) {
					for (int y = 2; y <= 4; y++) {
						s.set(x, y, z, VERMILION);
					}
				} else if (z == 6 || x == 1 || x == 5) {
					for (int y = 2; y <= 4; y++) {
						s.set(x, y, z, z == 6 || y == 4 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : PAPER);
					}
				}
			}
		}
		for (int x = 1; x <= 5; x++) {
			for (int z = 2; z <= 6; z++) {
				if (x == 1 || x == 5 || z == 2 || z == 6) {
					s.set(x, 5, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
				}
			}
		}
		for (int x = 2; x <= 4; x++) {
			s.set(x, 4, 2, Buildings.Sketch.lying(BEAM, Direction.Axis.X));
			s.set(x, 6, 2, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		}
		s.set(3, 2, 3, Blocks.BARREL.defaultBlockState());
		s.set(3, 2, 5, BONSAI);
		s.hip(0, 6, 0, 6, 5, Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(), Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
		s.set(3, 6, 4, Buildings.Sketch.hanging(Blocks.LANTERN));
		// the stone lanterns
		for (int x : new int[] {0, 6}) {
			s.set(x, 1, 0, Blocks.STONE_BRICK_WALL.defaultBlockState());
			s.set(x, 2, 0, Blocks.LANTERN.defaultBlockState());
		}
		return s.done();
	}

	/** A stone lantern (1 x 1) along the road. */
	static Blueprint stoneLantern() {
		Buildings.Sketch s = new Buildings.Sketch("blossom_lantern", 1, 1, 4);
		s.set(0, 1, 0, Blocks.STONE_BRICK_WALL.defaultBlockState());
		s.set(0, 2, 0, Blocks.LANTERN.defaultBlockState());
		s.set(0, 3, 0, Blocks.STONE_BRICK_SLAB.defaultBlockState());
		return s.done();
	}

	/** The first {@code rings} rings of a hipped roof over x0..x1, z0..z1 from {@code y}: the skirt of roof round a storey that stands back from the one below. */
	private static void skirt(Buildings.Sketch s, int x0, int x1, int z0, int z1, int y, int rings, BlockState stairs) {
		for (int k = 0; k < rings; k++) {
			int ax0 = x0 + k, ax1 = x1 - k, az0 = z0 + k, az1 = z1 - k;
			for (int x = ax0; x <= ax1; x++) {
				s.set(x, y + k, az0, Buildings.Sketch.stair(stairs, Direction.SOUTH));
				s.set(x, y + k, az1, Buildings.Sketch.stair(stairs, Direction.NORTH));
			}
			for (int z = az0 + 1; z < az1; z++) {
				s.set(ax0, y + k, z, Buildings.Sketch.stair(stairs, Direction.EAST));
				s.set(ax1, y + k, z, Buildings.Sketch.stair(stairs, Direction.WEST));
			}
		}
	}

	/** One storey of the keep: walls round x0..x1, z0..z1 from y0 to y1 (stone for the first {@code stone} rows, plaster above, timber posts, paper windows), a top plate over them. */
	private static void storey(Buildings.Sketch s, Buildings.Kit k, int x0, int x1, int z0, int z1, int y0, int y1, int stone) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x != x0 && x != x1 && z != z0 && z != z1) {
					continue;
				}
				boolean alongX = z == z0 || z == z1;
				int along = alongX ? x - x0 : z - z0, span = alongX ? x1 - x0 : z1 - z0;
				boolean post = (x == x0 || x == x1) && alongX || along % 4 == 0 && along != 0 && along != span;
				for (int y = y0; y <= y1; y++) {
					boolean window = !post && y == y0 + stone + 1 && along % 4 == 2;
					s.set(x, y, z, post ? TIMBER : y < y0 + stone ? k.p.wall() : window ? PAPER : k.infill);
				}
				s.set(x, y1 + 1, z, post ? TIMBER : Buildings.Sketch.lying(BEAM, alongX ? Direction.Axis.X : Direction.Axis.Z));
			}
		}
	}

	/** The castle's size (its blueprint), and where its keep's middle is across it: the gatehouse wing stands off to the right. */
	static final int CASTLE_WIDTH = 31, CASTLE_DEPTH = 25, CASTLE_HEIGHT = 30, CASTLE_MIDDLE = 12;
	/** How wide the gables on the castle's front eaves are, lowest first (half their width). */
	private static final int[] GABLE_HALF = {3, 2};

	/** The castle's roofs: grey stone tile, as the Blossom lords have always roofed their seats; the horns on the ridge are pale. */
	private static final BlockState ROOF = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
	private static final BlockState ROOF_SLAB = Blocks.STONE_BRICK_SLAB.defaultBlockState();
	private static final BlockState HORN = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
	private static final BlockState HORN_TIP = Blocks.SMOOTH_SANDSTONE_STAIRS.defaultBlockState();

	/** The rough stone of the castle's footing: the land's own stone, so the seat's cellar can still be dug under it. */
	private static BlockState rock(int x, int y, int z) {
		int roll = Math.floorMod(x * 31 + y * 17 + z * 13 + x * z, 10);
		return (roll < 6 ? Blocks.STONE : roll < 9 ? Blocks.ANDESITE : Blocks.TUFF).defaultBlockState();
	}

	/** A skirt of roof (see {@link #skirt}) whose four corners turn up at the tips. */
	private static void eaves(Buildings.Sketch s, int x0, int x1, int z0, int z1, int y, int rings) {
		skirt(s, x0, x1, z0, z1, y, rings, ROOF);
		BlockState tip = Buildings.Sketch.top(ROOF_SLAB);
		for (int x : new int[] {x0, x1}) {
			for (int z : new int[] {z0, z1}) {
				s.set(x, y, z, tip);
			}
		}
	}

	/** A little gable standing on the front slope of a skirt of roof (its eave at z0, y): its middle at cx, {@code half} wide each side, plaster in its face. */
	private static void gablet(Buildings.Sketch s, Buildings.Kit k, int cx, int y, int z0, int half) {
		for (int i = 0; i <= half; i++) {
			int left = cx - half + i, right = cx + half - i, yy = y + 1 + i;
			// each course runs back until it meets the slope behind it
			for (int j = 0; j <= i; j++) {
				if (j == 1) {
					for (int x = left + 1; x < right; x++) {
						s.set(x, yy, z0 + j, k.infill);
					}
				}
				if (left == right) {
					s.set(left, yy, z0 + j, ROOF_SLAB);
				} else {
					s.set(left, yy, z0 + j, Buildings.Sketch.stair(ROOF, Direction.EAST));
					s.set(right, yy, z0 + j, Buildings.Sketch.stair(ROOF, Direction.WEST));
				}
			}
		}
	}

	/** A ladder up through the floor at {@code top}, against a post of its own behind it. */
	private static void ladder(Buildings.Sketch s, int x, int z, int bottom, int top) {
		BlockState ladder = Buildings.Sketch.facing(Blocks.LADDER, Direction.NORTH);
		for (int y = bottom; y < top; y++) {
			s.set(x, y, z + 1, TIMBER);
		}
		for (int y = bottom; y <= top; y++) {
			s.set(x, y, z, ladder);
		}
	}

	/**
	 * A castle in the Blossom way (25 x 25, and a gatehouse wing to its right): the seat of a Blossom town's kingdom. A
	 * moat along its front with a stone bridge over it, steps up a footing of rough stone that leans in as it rises, and
	 * on that a keep of four storeys, each standing back from the one below under a skirt of grey tile with turned-up
	 * corners (a gable on the front of the two lowest). White plaster between dark posts, paper windows, and a roof on
	 * top whose ridge ends in a pair of pale horns. Inside, the great room: a boarded hall between two raised tatami
	 * floors, and at its far end a niche for whoever rules there. Ladders climb to the guards' floor, the lord's rooms and
	 * the lookout; a door off the right-hand floor leads into the gatehouse.
	 */
	static Blueprint tenshu(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("blossom_tenshu", CASTLE_WIDTH, CASTLE_DEPTH, CASTLE_HEIGHT);
		BlockState boards = k.p.planks(), brick = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState steps = Blocks.STONE_BRICK_STAIRS.defaultBlockState(), rail = Blocks.STONE_BRICK_WALL.defaultBlockState();
		int mid = CASTLE_MIDDLE;
		// the moat along the front, and the bridge over it
		for (int x = 0; x <= 24; x++) {
			for (int z = 0; z <= 1; z++) {
				boolean bridge = Math.abs(x - mid) <= 2;
				s.set(x, 0, z, bridge ? brick : Blocks.WATER.defaultBlockState());
				if (Math.abs(x - mid) == 2) {
					s.set(x, 1, z, rail);
				}
			}
		}
		s.set(mid - 2, 2, 0, Blocks.LANTERN.defaultBlockState());
		s.set(mid + 2, 2, 0, Blocks.LANTERN.defaultBlockState());
		// the footing: rough stone, a block wider at the ground than above, the keep's walls flush with it at the sides and back
		for (int x = 1; x <= 23; x++) {
			for (int z = 3; z <= 23; z++) {
				s.set(x, 1, z, rock(x, 1, z));
				if (x >= 2 && x <= 22 && z >= 4 && z <= 22) {
					s.set(x, 2, z, rock(x, 2, z));
					s.set(x, 3, z, z <= 5 || x == 2 || x == 22 || z == 22 ? brick : x >= 8 && x <= 16 || z <= 8 ? boards : rock(x, 3, z));
				}
			}
		}
		// the steps up it from the bridge
		for (int x = mid - 1; x <= mid + 1; x++) {
			for (int i = 0; i < 3; i++) {
				s.set(x, 1 + i, 2 + i, Buildings.Sketch.stair(steps, Direction.SOUTH));
				for (int y = 1; y <= i; y++) {
					s.set(x, y, 2 + i, brick);
				}
			}
		}
		// the great room: x 2..22, z 6..22, five high, behind a terrace under the eaves
		storey(s, k, 2, 22, 6, 22, 4, 8, 2);
		s.door(mid, 4, 6, Direction.NORTH, k.door);
		eaves(s, 0, 24, 4, 24, 8, 4);
		gablet(s, k, mid, 8, 4, GABLE_HALF[0]);
		// a raised tatami floor down each side of it, the boarded hall between them
		for (int z = 9; z <= 21; z++) {
			for (int x = 3; x <= 7; x++) {
				s.set(x, 4, z, TATAMI);
				s.set(24 - x, 4, z, TATAMI);
			}
		}
		// the niche at the hall's far end: the only place along the back wall to set a seat
		for (int x = 8; x <= 16; x++) {
			if (x == mid) {
				continue;
			}
			for (int y = 4; y <= 8; y++) {
				s.set(x, y, 21, Math.abs(x - mid) == 1 ? TIMBER : y == 6 && x % 2 == 0 ? PAPER : k.infill);
			}
		}
		// its ceiling is the guards' floor
		for (int x = 3; x <= 21; x++) {
			for (int z = 7; z <= 21; z++) {
				s.set(x, 9, z, boards);
			}
		}
		// the second storey: x 4..20, z 8..20
		storey(s, k, 4, 20, 8, 20, 10, 13, 0);
		eaves(s, 2, 22, 6, 22, 13, 4);
		gablet(s, k, mid, 13, 6, GABLE_HALF[1]);
		for (int x = 5; x <= 19; x++) {
			for (int z = 9; z <= 19; z++) {
				s.set(x, 14, z, boards);
			}
		}
		// the third: x 6..18, z 10..18
		storey(s, k, 6, 18, 10, 18, 15, 18, 0);
		eaves(s, 4, 20, 8, 20, 18, 4);
		for (int x = 7; x <= 17; x++) {
			for (int z = 11; z <= 17; z++) {
				s.set(x, 19, z, boards);
			}
		}
		// the lookout: x 8..16, z 12..16, under the top roof, whose ridge runs across the front
		storey(s, k, 8, 16, 12, 16, 20, 23, 0);
		for (int i = 0; i <= 4; i++) {
			for (int x = 6; x <= 18; x++) {
				boolean end = i == 0 && (x == 6 || x == 18);
				if (i == 4) {
					s.set(x, 27, 14, ROOF_SLAB);
				} else {
					s.set(x, 23 + i, 10 + i, end ? Buildings.Sketch.top(ROOF_SLAB) : Buildings.Sketch.stair(ROOF, Direction.SOUTH));
					s.set(x, 23 + i, 18 - i, end ? Buildings.Sketch.top(ROOF_SLAB) : Buildings.Sketch.stair(ROOF, Direction.NORTH));
				}
			}
			// the gable ends, over the lookout's side walls
			for (int z = 10 + i + 1; z <= 18 - i - 1; z++) {
				if (23 + i >= 25) {
					s.set(8, 23 + i, z, k.infill);
					s.set(16, 23 + i, z, k.infill);
				}
			}
		}
		// the horns at the ridge's ends, curling up and out
		s.set(7, 27, 14, HORN);
		s.set(17, 27, 14, HORN);
		s.set(7, 28, 14, HORN);
		s.set(17, 28, 14, HORN);
		s.set(7, 29, 14, Buildings.Sketch.stair(HORN_TIP, Direction.WEST));
		s.set(17, 29, 14, Buildings.Sketch.stair(HORN_TIP, Direction.EAST));
		// the gatehouse wing against the great room's right side: its own footing, its floor a step up, its ridge dying into the keep
		for (int x = 23; x <= 29; x++) {
			for (int z = 7; z <= 15; z++) {
				s.set(x, 1, z, rock(x, 1, z));
				if (x <= 28 && z >= 8 && z <= 14) {
					s.set(x, 2, z, rock(x, 2, z));
					s.set(x, 3, z, brick);
					for (int y = 4; y <= 10; y++) {
						s.set(x, y, z, y == 4 ? boards : Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		storey(s, k, 22, 28, 8, 14, 4, 8, 1);
		s.gableX(22, 28, 7, 15, 9, ROOF, ROOF_SLAB, k.infill);
		s.door(22, 5, 11, Direction.EAST, k.door);
		s.set(24, 5, 9, Blocks.BARREL.defaultBlockState());
		s.set(27, 5, 9, Blocks.BARREL.defaultBlockState());
		s.bed(27, 5, 12, Direction.SOUTH, FUTON);
		s.set(25, 12, 11, Buildings.Sketch.hanging(Blocks.LANTERN));
		// the ladders: guards' floor from the left-hand tatami, the lord's rooms, the lookout
		ladder(s, 6, 18, 5, 9);
		ladder(s, 9, 15, 10, 14);
		ladder(s, 10, 14, 15, 19);
		// lanterns under the great room's ceiling and either side of the niche
		for (int[] at : new int[][] {{5, 11}, {19, 11}, {5, 17}, {19, 17}, {10, 8}, {14, 8}, {mid - 2, 19}, {mid + 2, 19}}) {
			s.set(at[0], 8, at[1], Buildings.Sketch.hanging(Blocks.LANTERN));
		}
		s.set(4, 5, 20, BONSAI);
		s.set(20, 5, 20, BONSAI);
		// the guards' floor: futons along the back, barrels by the ladder
		for (int x = 8; x <= 16; x += 4) {
			s.bed(x, 10, 17, Direction.SOUTH, FUTON);
		}
		s.set(5, 10, 9, Blocks.BARREL.defaultBlockState());
		s.set(19, 10, 9, Blocks.BARREL.defaultBlockState());
		s.set(mid, 13, 14, Buildings.Sketch.hanging(Blocks.LANTERN));
		// the lord's rooms, and the lookout
		s.bed(15, 15, 15, Direction.SOUTH, FUTON);
		s.set(7, 15, 11, Blocks.BARREL.defaultBlockState());
		s.set(17, 15, 11, BONSAI);
		s.set(mid, 18, 14, Buildings.Sketch.hanging(Blocks.LANTERN));
		s.set(14, 20, 13, BONSAI);
		s.set(mid, 26, 14, Buildings.Sketch.hanging(Blocks.LANTERN));
		return s.done();
	}

	private static BlockState named(String name, Block fallback) {
		Block b = Townscape.named(name);
		return (b == null ? fallback : b).defaultBlockState();
	}
}
