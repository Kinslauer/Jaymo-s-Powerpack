package com.jaymoskingdoms;

import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.7.441) Havens patch 6c: havens built their own way (ideas from the havens pick list; our own designs). A haven's
 * houses are drawn in its blood's way instead of a cottage ({@link Buildings#house}); a mixed haven's take a bit of
 * each, house by house.
 * <ul>
 * <li><b>Tideborn:</b> up on log stilts with steps to a front deck, plank walls, a roof of dried kelp, and a fish rack
 * on the deck.</li>
 * <li><b>Stoneborn:</b> low and heavy, cut-stone walls half sunk with a turf-and-moss mound for a roof (as if dug into
 * a hillside), the door set deep in a stone arch.</li>
 * <li><b>Sylvan:</b> up in a tree: a platform on a trunk and posts, a ladder up, rope rails round it, the house on top
 * and leaves over all.</li>
 * <li><b>Warrior-born:</b> a longhouse, long and low under a steep roof, a hearth down the middle, beds along the
 * walls; and once the town is big enough, a fighting ring ({@link #ring}).</li>
 * </ul>
 * Drawn like every design: front on the north side (z = 0), y = 0 the floor laid in place of the ground.
 */
final class HavenBuildings {
	/** The fighting ring's catalogue id; it's only built by a Warrior-born haven (or a mixed one). */
	static final String RING = "fighting_ring";
	/** How high a Tideborn house stands on its stilts, and a Sylvan house's platform (blocks). */
	static final int STILTS = 3, BOUGH = 5;

	private static final BlockState KELP = Blocks.DRIED_KELP_BLOCK.defaultBlockState();
	private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
	private static final BlockState TURF = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

	private HavenBuildings() {
	}

	/** {@code f} builds a fighting ring: a Warrior-born haven, or a mixed one. */
	static boolean ringFor(MinecraftServer server, Faction f) {
		String haven = Havens.of(server, f);
		return haven.equals(Heritage.Kind.WARRIORBORN.key()) || haven.equals(Havens.MIXED);
	}

	/** A haven's house, in its blood's way (a mixed haven's, any of the four). */
	static Blueprint house(Buildings.Kit k) {
		String style = Havens.MIXED.equals(k.haven)
			? k.pick(Heritage.Kind.TIDEBORN.key(), Heritage.Kind.STONEBORN.key(), Heritage.Kind.SYLVAN.key(), Heritage.Kind.WARRIORBORN.key())
			: k.haven;
		if (style.equals(Heritage.Kind.TIDEBORN.key())) {
			return stilts(k);
		}
		if (style.equals(Heritage.Kind.STONEBORN.key())) {
			return hillside(k);
		}
		if (style.equals(Heritage.Kind.SYLVAN.key())) {
			return treehouse(k);
		}
		return longhouse(k);
	}

	// ---- Tideborn ----------------------------------------------------------------------------------------------------

	/** A stilt house: 7 or 9 wide, the house 6 deep behind three steps up and a deck; a kelp roof. */
	static Blueprint stilts(Buildings.Kit k) {
		Palette p = k.p;
		int w = k.pickInt(7, 9, 7);
		int deck = STILTS + 1, d = deck + 6;
		int floor = STILTS, top = floor + 4;
		Buildings.Sketch s = new Buildings.Sketch("house", w, d, top + w / 2 + 3);
		int mid = w / 2;
		// stilts at the corners and down the sides
		for (int x = 0; x < w; x += w - 1) {
			for (int z = deck - 1; z < d; z += 2) {
				for (int y = 0; y < floor; y++) {
					s.set(x, y, Math.min(z, d - 1), p.log());
				}
			}
		}
		// the deck (with the steps up its front) and the house's floor
		for (int x = 0; x < w; x++) {
			for (int z = deck - 1; z < d; z++) {
				s.set(x, floor, z, p.planks());
			}
		}
		for (int i = 0; i < deck - 1; i++) {
			// steps up from the ground to the deck
			s.set(mid, i + 1, i, Buildings.Sketch.stair(k.woodStairs, Direction.SOUTH));
			for (int y = 0; y < i + 1; y++) {
				s.set(mid, y, i, p.planks());
			}
		}
		// the walls
		for (int x = 0; x < w; x++) {
			for (int z = deck; z < d; z++) {
				boolean edge = x == 0 || x == w - 1 || z == deck || z == d - 1;
				if (!edge) {
					continue;
				}
				boolean corner = (x == 0 || x == w - 1) && (z == deck || z == d - 1);
				for (int y = floor + 1; y < top; y++) {
					s.set(x, y, z, corner ? p.log() : p.planks());
				}
			}
		}
		s.door(mid, floor + 1, deck, Direction.NORTH, k.door);
		for (int z : new int[] {deck + 2, d - 2}) {
			s.set(0, floor + 2, z, k.pane);
			s.set(w - 1, floor + 2, z, k.pane);
		}
		s.set(mid, floor + 2, d - 1, k.pane);
		// the deck's rail, and a fish rack along it (fence posts under a slab, a barrel of the catch)
		for (int x = 0; x < w; x++) {
			if (x != mid) {
				s.set(x, floor + 1, deck - 1, p.fence());
			}
		}
		s.set(1, floor + 2, deck - 1, Buildings.Sketch.top(p.woodSlab()));
		s.set(w - 2, floor + 2, deck - 1, Buildings.Sketch.top(p.woodSlab()));
		s.set(w - 2, floor + 1, deck - 1, Blocks.BARREL.defaultBlockState());
		// a kelp roof: rings of dried kelp drawing in to a ridge
		for (int ring = 0; ; ring++) {
			int x0 = ring - 1, x1 = w - ring, z0 = deck - 1 + ring, z1 = d - ring;
			x0 = Math.max(0, x0);
			x1 = Math.min(w - 1, x1);
			z1 = Math.min(d - 1, z1);
			if (x0 > x1 || z0 > z1 || ring > w / 2) {
				break;
			}
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					if (x == x0 || x == x1 || z == z0 || z == z1) {
						s.set(x, top + ring, z, KELP);
					}
				}
			}
		}
		// inside: beds at the back, a table, a lamp
		s.bed(1, floor + 1, d - 3, Direction.SOUTH, k.bed);
		if (w >= 9) {
			s.bed(w - 2, floor + 1, d - 3, Direction.SOUTH, k.bed);
		}
		s.set(w >= 9 ? mid : w - 2, floor + 1, d - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		s.set(mid, floor + 3, d - 2, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		s.set(mid, floor + 3, deck - 1, Buildings.Sketch.hanging(Blocks.LANTERN));
		return s.done();
	}

	// ---- Stoneborn ---------------------------------------------------------------------------------------------------

	/** A hillside house: 7-9 square, stone walls two high under a turf mound, the door deep in an arch. */
	static Blueprint hillside(Buildings.Kit k) {
		Palette p = k.p;
		int w = k.pickInt(7, 9, 9), d = k.pickInt(8, 9);
		int front = 1, top = 3;
		Buildings.Sketch s = new Buildings.Sketch("house", w, d, top + w / 2 + 3);
		int mid = w / 2;
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState(), mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		for (int x = 0; x < w; x++) {
			for (int z = front; z < d; z++) {
				boolean edge = x == 0 || x == w - 1 || z == front || z == d - 1;
				s.set(x, 0, z, edge ? stone : Blocks.POLISHED_ANDESITE.defaultBlockState());
				if (edge) {
					for (int y = 1; y < top; y++) {
						s.set(x, y, z, (x * 3 + y + z) % 4 == 0 ? mossy : stone);
					}
				}
			}
		}
		// the arch round the door: a porch of stone, stairs turned over above it
		for (int y = 1; y <= 2; y++) {
			s.set(mid - 1, y, 0, stone);
			s.set(mid + 1, y, 0, stone);
		}
		s.set(mid - 1, 3, 0, Buildings.Sketch.upsideDown(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.EAST));
		s.set(mid, 3, 0, stone);
		s.set(mid + 1, 3, 0, Buildings.Sketch.upsideDown(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.WEST));
		s.set(mid, 0, 0, stone);
		s.door(mid, 1, front, Direction.NORTH, k.door);
		// small deep windows
		s.set(0, 2, d / 2, k.pane);
		s.set(w - 1, 2, d / 2, k.pane);
		// the mound: a stone lid, then turf drawn in ring by ring, mossy at the edges
		for (int ring = 0; ring <= w / 2; ring++) {
			int x0 = ring, x1 = w - 1 - ring, z0 = front + ring, z1 = d - 1 - ring;
			if (x0 > x1 || z0 > z1) {
				break;
			}
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					if (ring == 0) {
						s.set(x, top, z, stone);
					} else if (edge || x0 == x1 || z0 == z1) {
						s.set(x, top + ring, z, ring == 1 ? MOSS : TURF);
					}
				}
			}
		}
		// inside: beds, a furnace and a stonecutter, a lamp
		s.bed(1, 1, d - 3, Direction.SOUTH, k.bed);
		if (w >= 9) {
			s.bed(w - 2, 1, d - 3, Direction.SOUTH, k.bed);
		}
		s.set(1, 1, front + 1, Buildings.Sketch.facing(Blocks.FURNACE, Direction.EAST));
		s.set(w - 2, 1, front + 1, Buildings.Sketch.facing(Blocks.STONECUTTER, Direction.WEST));
		s.set(mid, 2, d - 2, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		return s.done();
	}

	// ---- Sylvan ------------------------------------------------------------------------------------------------------

	/** A tree house: a 9-square platform on a trunk and four posts, a ladder up its front, the house on it, leaves over all. */
	static Blueprint treehouse(Buildings.Kit k) {
		Palette p = k.p;
		int w = 9, d = 10, floor = BOUGH, top = floor + 4;
		Buildings.Sketch s = new Buildings.Sketch("house", w, d, top + 8);
		int mid = w / 2, zm = 5;
		// posts and the trunk up through the middle, to the crown
		for (int y = 0; y < floor; y++) {
			for (int[] at : new int[][] {{0, 1}, {w - 1, 1}, {0, d - 1}, {w - 1, d - 1}, {mid, 1}}) {
				s.set(at[0], y, at[1], p.log());
			}
		}
		for (int y = 0; y < top + 4; y++) {
			if (y <= floor || y >= top) {
				s.set(mid, y, zm, p.log());
			}
		}
		// the ladder up the front post
		for (int y = 1; y <= floor; y++) {
			s.set(mid, y, 0, Buildings.Sketch.facing(Blocks.LADDER, Direction.NORTH));
		}
		// the platform, its rope rail (fences) round the edge but for the ladder, a lantern on a chain at each corner
		for (int x = 0; x < w; x++) {
			for (int z = 1; z < d; z++) {
				s.set(x, floor, z, p.planks());
				boolean edge = x == 0 || x == w - 1 || z == 1 || z == d - 1;
				if (edge && !(x == mid && z == 1)) {
					s.set(x, floor + 1, z, p.fence());
				}
			}
		}
		// the house: 5 wide, 5 deep on the platform's back (x 2..6, z 3..7)
		int x0 = 2, x1 = 6, z0 = 3, z1 = 7;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (!edge || x == mid && z == zm) {
					continue;
				}
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				for (int y = floor + 1; y < top; y++) {
					s.set(x, y, z, corner ? p.log() : p.planks());
				}
			}
		}
		s.door(mid, floor + 1, z0, Direction.NORTH, k.door);
		s.set(x0, floor + 2, zm, k.pane);
		s.set(x1, floor + 2, zm, k.pane);
		s.gable(x0, x1, z0, z1, top, k.woodStairs, p.woodSlab(), p.planks());
		// the crown: leaves over the roof and round the trunk's top
		for (int x = 0; x < w; x++) {
			for (int z = 1; z < d; z++) {
				int dx = Math.abs(x - mid), dz = Math.abs(z - zm);
				for (int y = top + 2; y <= top + 5; y++) {
					int r = y == top + 5 ? 2 : y == top + 2 ? 4 : 4;
					if (dx * dx + dz * dz <= r * r && !(x == mid && z == zm) && (y > top + 2 || dx > 2 || dz > 2)) {
						s.set(x, y, z, LEAVES);
					}
				}
			}
		}
		// inside: a bed, a lamp; outside, a flower pot by the door
		s.bed(x0 + 1, floor + 1, z1 - 2, Direction.SOUTH, k.bed);
		s.set(x1 - 1, floor + 3, z1 - 1, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		s.set(x0 - 1, floor + 1, 2, k.flowerPot());
		return s.done();
	}

	// ---- Warrior-born ------------------------------------------------------------------------------------------------

	/** A longhouse: 7 wide and 13-15 long, log walls two high under a steep roof, a hearth down the middle, beds along it. */
	static Blueprint longhouse(Buildings.Kit k) {
		Palette p = k.p;
		int w = 7, d = k.pickInt(13, 15, 13), top = 3;
		Buildings.Sketch s = new Buildings.Sketch("house", w, d, top + w / 2 + 3);
		int mid = w / 2;
		BlockState dark = Blocks.DARK_OAK_LOG.defaultBlockState();
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				boolean alongX = z == 0 || z == d - 1;
				boolean edge = x == 0 || x == w - 1 || alongX;
				s.set(x, 0, z, edge ? p.wall() : Blocks.PACKED_MUD.defaultBlockState());
				if (!edge) {
					continue;
				}
				boolean post = (x == 0 || x == w - 1) && (alongX || z % 4 == 0);
				for (int y = 1; y < top; y++) {
					s.set(x, y, z, post ? dark : Buildings.Sketch.lying(p.log(), alongX ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
		}
		s.door(mid, 1, 0, Direction.NORTH, k.door);
		s.set(mid, 3, 0, dark);
		s.gable(0, w - 1, 0, d - 1, top, Blocks.SPRUCE_STAIRS.defaultBlockState(), Blocks.SPRUCE_SLAB.defaultBlockState(), p.planks());
		// the hearth down the middle (a fire under the ridge), shields on the walls (banners), beds along the sides
		s.set(mid, 0, d / 2, Blocks.CAMPFIRE.defaultBlockState());
		for (int z = 2; z < d - 1; z += 3) {
			s.bed(2, 1, z, Direction.WEST, k.bed);
			if (z + 1 < d - 1) {
				s.bed(w - 3, 1, z + 1, Direction.EAST, k.bed);
			}
		}
		s.set(mid, 1, d - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		s.set(mid, 3, d - 2, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		s.set(mid + 1, 2, 1, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.SOUTH));
		return s.done();
	}

	/** A fighting ring: a round of posts and rails 13 across round a sand floor, a gap on the north side, torches on four posts. */
	static Blueprint ring(Buildings.Kit k) {
		Palette p = k.p;
		int size = 13, c = size / 2;
		Buildings.Sketch s = new Buildings.Sketch(RING, size, size, 4);
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				double r = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
				if (r > c + 0.5) {
					continue;
				}
				s.set(x, 0, z, r < c - 0.5 ? Blocks.SAND.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
				if (r >= c - 0.5 && !(z < c && Math.abs(x - c) <= 1)) {
					boolean post = (x + z) % 3 == 0;
					s.set(x, 1, z, post ? p.log() : p.fence());
					if (post) {
						s.set(x, 2, z, (x == c || z == c) ? Blocks.TORCH.defaultBlockState() : p.fence());
					}
				}
			}
		}
		return s.done();
	}
}
