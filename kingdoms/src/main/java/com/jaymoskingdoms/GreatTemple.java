package com.jaymoskingdoms;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The Truth's great temple, drawn from the realm's palette: a nave eleven wide inside, a bell tower over the doors,
 * an apse with the altar at the far end, glass windows and a rose window. {@link #nave} is shared with the ruined cathedral
 * ({@link TempleBuildings}). 13 x 19, 20 high; the doors are on the north (z = 0) side, like every blueprint.
 */
final class GreatTemple {
	static final int W = 13, D = 19, H = 20;
	/** The ruined cathedral: how many of the upper wall blocks are gone, how many are cracked, how many mossy. */
	static final float RUIN_HOLES = 0.25F, CRACKED = 0.3F, MOSSY = 0.3F;
	/** Where the altar and the dais stand (x, y, z). */
	static final int ALTAR_X = 6, ALTAR_Y = 2, ALTAR_Z = D - 1 - 3;

	@FunctionalInterface
	interface Setter {
		void set(int x, int y, int z, BlockState state);
	}

	/** What the nave is built of. */
	record Look(BlockState wall, BlockState mossy, BlockState cracked, BlockState trim, BlockState floor, BlockState stairs, BlockState slab,
		BlockState planks, BlockState pane, BlockState carpet) {
	}

	private GreatTemple() {
	}

	static BlockState altar() {
		return Healing.SHRINE.defaultBlockState().setValue(Shrine.DEDICATION, Shrine.Dedication.of(Patron.TRUTH));
	}

	static Blueprint draw(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch(Temples.GREAT_TEMPLE, W, D, H);
		Look look = new Look(k.p.wall(), k.p.mossy(), k.p.cracked(), k.p.trim(), k.p.floor(), k.roofStairs, k.roofSlab, k.p.planks(), k.pane, k.carpet);
		nave(s::set, look, false, null);
		s.set(ALTAR_X, ALTAR_Y, ALTAR_Z, altar());
		return s.done();
	}

	private static BlockState pick(Look k, boolean ruin, @Nullable RandomSource rnd) {
		if (!ruin || rnd == null) {
			return k.wall;
		}
		float r = rnd.nextFloat();
		return r < CRACKED ? k.cracked : r < CRACKED + MOSSY ? k.mossy : k.wall;
	}

	/** The nave, apse and (unless ruined) roof and tower, into {@code s}. */
	static void nave(Setter sink, Look k, boolean ruin, @Nullable RandomSource rnd) {
		// drawn with the apse at z = 0 and the doors at z = D - 1, then flipped so the doors are on the north side like every blueprint
		Setter s = (x, y, z, state) -> sink.set(x, y, D - 1 - z, state);
		for (int x = 0; x < W; x++) {
			for (int z = 0; z < D; z++) {
				s.set(x, 0, z, k.floor);
			}
		}
		for (int z = 5; z <= 17; z++) {
			if (!ruin || rnd.nextFloat() < 0.6F) {
				s.set(6, 0, z, k.carpet);
			}
		}
		// the walls, pilasters and windows
		for (int y = 1; y <= 12; y++) {
			for (int z = 0; z < D; z++) {
				boolean window = z % 4 == 0 && z > 0 && z < D - 1 && y >= 4 && y <= 10;
				put(s, k, ruin, rnd, 0, y, z, window ? k.pane : null);
				put(s, k, ruin, rnd, 12, y, z, window ? k.pane : null);
				if (z % 4 == 2 && y <= 11) {
					s.set(1, y, z, k.trim);
					s.set(11, y, z, k.trim);
				}
			}
			for (int x = 1; x < W - 1; x++) {
				boolean slit = (x == 3 || x == 6 || x == 9) && y >= 5 && y <= 10;
				put(s, k, ruin, rnd, x, y, 0, slit ? k.pane : null);
				put(s, k, ruin, rnd, x, y, D - 1, null);
			}
		}
		// the doorway and the rose window over it
		for (int y = 1; y <= 5; y++) {
			for (int x = 5; x <= 7; x++) {
				s.set(x, y, D - 1, Blocks.AIR.defaultBlockState());
			}
		}
		for (int y = 1; y <= 6; y++) {
			s.set(4, y, D - 1, k.trim);
			s.set(8, y, D - 1, k.trim);
		}
		for (int x = 4; x <= 8; x++) {
			s.set(x, 6, D - 1, k.trim);
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				double d = Math.sqrt(dx * dx + dy * dy);
				if (d >= 2.2 && d <= 3.2) {
					s.set(6 + dx, 10 + dy, D - 1, k.trim);
				} else if (d < 2.2) {
					s.set(6 + dx, 10 + dy, D - 1, k.pane);
				}
			}
		}
		// pews
		for (int z : new int[] {8, 10, 12, 14}) {
			for (int x = 2; x <= 10; x++) {
				if (x >= 5 && x <= 7) {
					continue;
				}
				if (!ruin || rnd.nextFloat() < 0.6F) {
					s.set(x, 1, z, k.planks);
				}
			}
		}
		// the apse's dais
		for (int x = 3; x <= 9; x++) {
			for (int z = 1; z <= 5; z++) {
				s.set(x, 1, z, k.trim);
			}
		}
		// the roof (a ruin keeps its ends and loses the middle)
		for (int kk = 0; kk <= 6; kk++) {
			int y = 13 + kk;
			for (int z = 0; z < D; z++) {
				if (ruin && z > 4 && z < 14) {
					continue;
				}
				if (kk == 6) {
					s.set(6, y, z, k.slab);
				} else {
					s.set(kk, y, z, Buildings.Sketch.stair(k.stairs, Direction.EAST));
					s.set(12 - kk, y, z, Buildings.Sketch.stair(k.stairs, Direction.WEST));
				}
			}
			for (int x = kk + 1; x <= 11 - kk; x++) {
				s.set(x, y, 0, k.wall);
				if (!ruin) {
					s.set(x, y, D - 1, k.wall);
				}
			}
		}
		if (ruin) {
			rubble(s, k, rnd);
			return;
		}
		tower(s, k);
	}

	private static void put(Setter s, Look k, boolean ruin, @Nullable RandomSource rnd, int x, int y, int z, @Nullable BlockState special) {
		if (ruin && y > 5 && rnd.nextFloat() < RUIN_HOLES) {
			s.set(x, y, z, Blocks.AIR.defaultBlockState());
			return;
		}
		s.set(x, y, z, special != null ? special : pick(k, ruin, rnd));
	}

	/** The bell tower over the doors: walls, open arches, a bell, a small spire. */
	private static void tower(Setter s, Look k) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int y = 13; y <= 17; y++) {
			for (int x = 4; x <= 8; x++) {
				for (int z = 15; z <= 18; z++) {
					boolean edge = x == 4 || x == 8 || z == 15 || z == 18;
					s.set(x, y, z, y == 13 ? k.wall : edge ? k.wall : air);
				}
			}
		}
		for (int y = 14; y <= 15; y++) {
			s.set(6, y, 15, air);
			s.set(6, y, 18, air);
			s.set(4, y, 16, air);
			s.set(8, y, 16, air);
		}
		s.set(6, 14, 16, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT,
			net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR));
		for (int x = 4; x <= 8; x++) {
			for (int z = 15; z <= 18; z++) {
				s.set(x, 18, z, k.slab);
			}
		}
		s.set(6, 19, 16, k.trim);
	}

	/** A fallen tower: rubble in the ruin's front end. */
	private static void rubble(Setter s, Look k, RandomSource rnd) {
		for (int i = 0; i < 18; i++) {
			int x = 1 + rnd.nextInt(11), z = 13 + rnd.nextInt(5);
			int h = 1 + rnd.nextInt(2);
			for (int y = 1; y <= h; y++) {
				s.set(x, y, z, rnd.nextBoolean() ? k.mossy : k.cracked);
			}
		}
	}

	static BlockState wool(DyeColor c) {
		return Blocks.WOOL.pick(c).defaultBlockState();
	}
}
