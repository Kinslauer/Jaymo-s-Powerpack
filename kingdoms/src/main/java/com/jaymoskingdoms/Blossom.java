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

	/**
	 * A castle keep in the Blossom way (a tenshu; 17 x 17): three storeys, each standing back from the one below with a
	 * skirt of dark tile round it, stone to the knee and white plaster above between dark posts, paper windows, a hipped
	 * roof on top with a lantern at each end of the ridge. The great room below is left open from the door to the back
	 * wall for whoever rules there; ladders climb to the two rooms above. The seat of a Blossom town's kingdom.
	 */
	static Blueprint tenshu(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("blossom_tenshu", 17, 17, 28);
		BlockState boards = k.p.planks(), tile = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(), ridge = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
		BlockState ladder = Buildings.Sketch.facing(Blocks.LADDER, Direction.NORTH);
		// the footing and the great room's floor
		for (int x = 1; x <= 15; x++) {
			for (int z = 1; z <= 15; z++) {
				boolean inside = x >= 3 && x <= 13 && z >= 3 && z <= 13;
				s.set(x, 0, z, inside ? TATAMI : k.p.wall());
			}
		}
		// the great room: x 2..14, z 2..14, five high
		storey(s, k, 2, 14, 2, 14, 1, 5, 2);
		s.door(8, 1, 2, Direction.NORTH, k.door);
		skirt(s, 0, 16, 0, 16, 5, 4, tile);
		// its ceiling is the second storey's floor
		for (int x = 3; x <= 13; x++) {
			for (int z = 3; z <= 13; z++) {
				if (!(x == 5 && z == 11)) {
					s.set(x, 6, z, boards);
				}
			}
		}
		// the second storey: x 4..12, z 4..12
		storey(s, k, 4, 12, 4, 12, 7, 10, 0);
		skirt(s, 2, 14, 2, 14, 10, 4, tile);
		for (int x = 5; x <= 11; x++) {
			for (int z = 5; z <= 11; z++) {
				if (!(x == 7 && z == 9)) {
					s.set(x, 11, z, boards);
				}
			}
		}
		// the third: x 6..10, z 6..10, and the roof
		storey(s, k, 6, 10, 6, 10, 12, 14, 0);
		s.hip(4, 12, 4, 12, 14, tile, ridge);
		s.set(8, 19, 8, Blocks.LANTERN.defaultBlockState());
		// the ladders: each against a post of its own below the wall it comes up beside
		for (int y = 1; y <= 5; y++) {
			s.set(5, y, 12, TIMBER);
		}
		for (int y = 1; y <= 6; y++) {
			s.set(5, y, 11, ladder);
		}
		for (int y = 7; y <= 10; y++) {
			s.set(7, y, 10, TIMBER);
		}
		for (int y = 7; y <= 11; y++) {
			s.set(7, y, 9, ladder);
		}
		// lanterns under the great room's ceiling, the lord's things above
		for (int[] at : new int[][] {{5, 5}, {11, 5}, {5, 9}, {11, 11}}) {
			s.set(at[0], 5, at[1], Buildings.Sketch.hanging(Blocks.LANTERN));
		}
		s.bed(10, 7, 9, Direction.SOUTH, FUTON);
		s.set(6, 7, 6, Blocks.BARREL.defaultBlockState());
		s.set(10, 7, 6, BONSAI);
		s.set(8, 10, 8, Buildings.Sketch.hanging(Blocks.LANTERN));
		s.set(8, 12, 9, BONSAI);
		return s.done();
	}

	private static BlockState named(String name, Block fallback) {
		Block b = Townscape.named(name);
		return (b == null ? fallback : b).defaultBlockState();
	}
}
