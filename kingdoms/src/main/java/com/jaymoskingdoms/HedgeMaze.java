package com.jaymoskingdoms;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A castle's hedge maze ({@link SeatCompounds.Kind#MAZE}): the lord's garden grown into a puzzle, set beside the castle
 * like the walled garden and drawn in the land's materials. A perfect maze ({@link MazeGen}) of hedge walls, two high inside
 * and three round the outside, gravel walks, one way in on the side turned toward the castle (north, as the garden's gate
 * is), and at its heart a small fountain. It holds no loot: a castle's stores are its lord's.
 * The same seed always draws the same maze; tools/small_designs/mazes.py draws a sample of it.
 */
final class HedgeMaze {
	/** The maze is this many cells a side (19 blocks), its heart room this many cells from the first cell and this wide. */
	static final int CELLS = 9, HEART_AT = 3, HEART_SIZE = 3;
	/** Hedge height inside and round the outside of the maze. */
	static final int INNER_HEIGHT = 2, OUTER_HEIGHT = 3;

	private HedgeMaze() {
	}

	/** The maze: its way in at the middle of the north side, its fountain in the heart. */
	static Blueprint draw(Buildings.Kit k, long seed) {
		int size = MazeGen.size(CELLS);
		Palette p = k.p;
		Buildings.Sketch s = new Buildings.Sketch("seat_hedge_maze", size, size, 6);
		BlockState hedge = SeatCompounds.leaves(p);
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockState gravel = Blocks.GRAVEL.defaultBlockState();
		boolean[][] open = MazeGen.layout(CELLS, new MazeGen.Room(HEART_AT, HEART_AT, HEART_SIZE), null, seed, CELLS / 2, 0);
		int gate = 2 * (CELLS / 2) + 1;
		open[gate][0] = true;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				if (open[x][z]) {
					s.set(x, 0, z, gravel);
					continue;
				}
				s.set(x, 0, z, grass);
				boolean edge = x == 0 || z == 0 || x == size - 1 || z == size - 1;
				for (int y = 1; y <= (edge ? OUTER_HEIGHT : INNER_HEIGHT); y++) {
					s.set(x, y, z, hedge);
				}
			}
		}
		// lamps on the hedge beside the way in
		s.set(gate - 1, OUTER_HEIGHT + 1, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		s.set(gate + 1, OUTER_HEIGHT + 1, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		// the fountain in the heart room: a slab rim round water, a lamp on a pillar in the middle
		int c = 2 * HEART_AT + 3;
		for (int x = c - 2; x <= c + 2; x++) {
			for (int z = c - 2; z <= c + 2; z++) {
				boolean rim = Math.max(Math.abs(x - c), Math.abs(z - c)) == 2;
				s.set(x, 0, z, p.wall());
				s.set(x, 1, z, rim ? p.slab() : Blocks.WATER.defaultBlockState());
			}
		}
		s.set(c, 1, c, p.wall());
		s.set(c, 2, c, p.wall());
		s.set(c, 3, c, Blocks.LANTERN.defaultBlockState());
		return s.done();
	}
}
