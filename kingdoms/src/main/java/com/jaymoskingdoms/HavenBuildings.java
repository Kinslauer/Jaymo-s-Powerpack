package com.jaymoskingdoms;

import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

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

	// ---- (0.7.490) Havens expand: the works a haven raises to make the most of its land ----------------------------------

	/** (0.7.490) The dock's rows from this one on (the boat shed's end) stand over water, at least this many of its columns. */
	static final int DOCK_WATER_ROW = 6, DOCK_WET = 9;
	/** The works a haven raises, in their catalogue ids ({@link HavenGrowth} says which a blood builds and what each makes). */
	static final String FIELDS = "haven_fields", WORKSHOP = "haven_workshop", MINE = "haven_mine", DOCK = "haven_dock", MILL = "haven_mill";

	/** Which blood's way {@code k}'s building is drawn in (a mixed haven's, any of the four, building by building). */
	private static String style(Buildings.Kit k) {
		return Havens.MIXED.equals(k.haven) || k.haven.isEmpty()
			? k.pick(Heritage.Kind.TIDEBORN.key(), Heritage.Kind.STONEBORN.key(), Heritage.Kind.SYLVAN.key(), Heritage.Kind.WARRIORBORN.key())
			: k.haven;
	}

	/**
	 * Fields (13 x 9): the haven's blood works its land its own way. Tideborn flood their rows (paddies, reeds between the
	 * water), Stoneborn cut theirs in three terraces behind stone, Sylvan keep an orchard of berry bushes and saplings,
	 * Warrior-born a fenced pasture of hay and wheat with a watch post at the corner.
	 */
	static Blueprint fields(Buildings.Kit k) {
		Palette p = k.p;
		String style = style(k);
		int w = 13, d = 9;
		Buildings.Sketch s = new Buildings.Sketch(FIELDS, w, d, 6);
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState farmland = Blocks.FARMLAND.defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();
		Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES};
		if (style.equals(Heritage.Kind.TIDEBORN.key())) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
					if (edge) {
						s.set(x, 0, z, p.planks());
					} else if (z % 3 == 0) {
						s.set(x, 0, z, water);
					} else {
						// (0.7.490) crops stand on farmland (the water rows keep it wet); reeds only on dirt beside a water row
						boolean reed = x % 4 == 2 && z > 1;
						s.set(x, 0, z, reed ? dirt : farmland);
						s.set(x, 1, z, reed ? Blocks.SUGAR_CANE.defaultBlockState() : crops[(x / 4) % crops.length].defaultBlockState());
					}
				}
			}
			s.set(w / 2, 1, 0, Blocks.BARREL.defaultBlockState());
			s.set(1, 1, 0, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
		} else if (style.equals(Heritage.Kind.STONEBORN.key())) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					int tier = z / 3;
					for (int y = 0; y < tier; y++) {
						s.set(x, y, z, p.wall());
					}
					boolean lip = z % 3 == 0 && z > 0;
					if (lip || x == 0 || x == w - 1) {
						s.set(x, tier, z, lip ? p.wall() : dirt);
					} else if (x == w / 2 && z % 3 == 1) {
						s.set(x, tier, z, water);
					} else {
						s.set(x, tier, z, farmland);
						s.set(x, tier + 1, z, crops[(x / 3) % crops.length].defaultBlockState());
					}
				}
			}
		} else if (style.equals(Heritage.Kind.SYLVAN.key())) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					s.set(x, 0, z, x == 0 || x == w - 1 || z == 0 || z == d - 1 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
				}
			}
			BlockState berries = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3);
			for (int x = 2; x < w - 2; x += 3) {
				for (int z = 2; z < d - 1; z += 2) {
					if (z % 4 == 2) {
						s.set(x, 1, z, Blocks.OAK_SAPLING.defaultBlockState());
					} else {
						s.set(x, 1, z, berries);
						s.set(x + 1, 1, z, berries);
					}
				}
			}
			s.set(w / 2, 1, 0, Blocks.COMPOSTER.defaultBlockState());
		} else {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
					s.set(x, 0, z, edge ? p.log() : farmland);
					if (edge) {
						s.set(x, 1, z, x == w / 2 && z == 0 ? Buildings.Sketch.facing(k.gate, Direction.NORTH) : p.fence());
					} else if (z == d / 2 && x % 4 == 2) {
						s.set(x, 0, z, water);
					} else if (x < w / 2) {
						s.set(x, 1, z, Blocks.WHEAT.defaultBlockState());
					} else if ((x + z) % 3 == 0) {
						s.set(x, 1, z, Blocks.HAY_BLOCK.defaultBlockState());
					}
				}
			}
			// a watch post at the far corner: a post, a platform, a torch
			for (int y = 1; y <= 3; y++) {
				s.set(w - 2, y, d - 2, p.log());
			}
			s.set(w - 2, 4, d - 2, p.planks());
			s.set(w - 2, 5, d - 2, Blocks.TORCH.defaultBlockState());
		}
		return s.done();
	}

	/** A hut {@code w} x {@code d} (door in the north wall), walls of {@code wall}, posts of {@code corner}, a gabled roof; drawn into {@code s}. */
	private static void hut(Buildings.Kit k, Buildings.Sketch s, int w, int d, BlockState wall, BlockState corner, BlockState roofStairs, BlockState roofSlab,
		BlockState gableFill) {
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
				boolean post = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
				s.set(x, 0, z, edge ? wall : k.p.floor());
				if (edge) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, post ? corner : wall);
					}
				}
			}
		}
		s.door(w / 2, 1, 0, Direction.NORTH, k.door);
		s.set(0, 2, d / 2, k.pane);
		s.set(w - 1, 2, d / 2, k.pane);
		s.gable(0, w - 1, 0, d - 1, 4, roofStairs, roofSlab, gableFill);
		s.set(w / 2, 3, d - 2, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
	}

	/**
	 * A workshop (9 x 7): Stoneborn a smithy (blast furnace, anvil, a cauldron), Warrior-born an armoury (anvil, fletching
	 * table, chests of arms), Sylvan a weaver's and fletcher's under leaves, Tideborn a net loft on kelp and planks.
	 */
	static Blueprint workshop(Buildings.Kit k) {
		Palette p = k.p;
		String style = style(k);
		int w = 9, d = 7;
		Buildings.Sketch s = new Buildings.Sketch(WORKSHOP, w, d, 10);
		if (style.equals(Heritage.Kind.STONEBORN.key())) {
			hut(k, s, w, d, p.wall(), p.trim(), p.stairs(), p.slab(), p.wall());
			s.set(1, 1, d - 2, Buildings.Sketch.facing(Blocks.BLAST_FURNACE, Direction.SOUTH));
			s.set(2, 1, d - 2, Buildings.Sketch.facing(Blocks.SMITHING_TABLE, Direction.SOUTH));
			s.set(w - 3, 1, d - 2, Buildings.Sketch.facing(Blocks.ANVIL, Direction.EAST));
			s.set(w - 2, 1, d - 2, Blocks.CAULDRON.defaultBlockState());
		} else if (style.equals(Heritage.Kind.WARRIORBORN.key())) {
			hut(k, s, w, d, p.planks(), p.log(), k.roofStairs, k.roofSlab, p.planks());
			s.set(1, 1, d - 2, Buildings.Sketch.facing(Blocks.ANVIL, Direction.SOUTH));
			s.set(2, 1, d - 2, Buildings.Sketch.facing(Blocks.FLETCHING_TABLE, Direction.SOUTH));
			s.set(w - 2, 1, d - 2, Buildings.Sketch.facing(Blocks.CHEST, Direction.NORTH));
			s.set(w - 3, 1, d - 2, Buildings.Sketch.facing(Blocks.CHEST, Direction.NORTH));
			s.set(4, 1, 2, Blocks.HAY_BLOCK.defaultBlockState());
			s.set(4, 2, 2, Buildings.Sketch.facing(Blocks.CARVED_PUMPKIN, Direction.SOUTH));
		} else if (style.equals(Heritage.Kind.SYLVAN.key())) {
			hut(k, s, w, d, p.planks(), p.log(), k.roofStairs, k.roofSlab, p.planks());
			for (int x = 1; x < w - 1; x++) {
				s.set(x, 4, 0, LEAVES);
			}
			s.set(1, 1, d - 2, Buildings.Sketch.facing(Blocks.LOOM, Direction.SOUTH));
			s.set(2, 1, d - 2, Buildings.Sketch.facing(Blocks.FLETCHING_TABLE, Direction.SOUTH));
			s.set(w - 2, 1, d - 2, Blocks.BARREL.defaultBlockState());
			s.set(w - 2, 1, 2, Blocks.POTTED_FERN.defaultBlockState());
		} else {
			hut(k, s, w, d, p.planks(), p.log(), k.roofStairs, k.roofSlab, KELP);
			s.set(1, 1, d - 2, Blocks.BARREL.defaultBlockState());
			s.set(2, 1, d - 2, Blocks.BARREL.defaultBlockState());
			s.set(w - 3, 1, d - 2, Blocks.CAULDRON.defaultBlockState());
			s.set(w - 2, 1, d - 2, Buildings.Sketch.facing(Blocks.LOOM, Direction.SOUTH));
			for (int x = 2; x < w - 2; x++) {
				s.set(x, 3, 2, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
			}
		}
		return s.done();
	}

	/**
	 * A mine head (9 x 9): a timber frame over the shaft, a rail track from it to a heap of spoil, ore stacked by a lean-to
	 * with a furnace and a chest. (The pit itself is a ring of gravel and trapdoors, since a blueprint can't dig.)
	 */
	static Blueprint mine(Buildings.Kit k) {
		Palette p = k.p;
		int w = 9, d = 9;
		Buildings.Sketch s = new Buildings.Sketch(MINE, w, d, 7);
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				s.set(x, 0, z, x < 5 && z < 5 ? Blocks.GRAVEL.defaultBlockState() : k.road);
			}
		}
		// the headframe over the shaft: four posts and a beam, a lantern hung from it
		for (int[] c : new int[][] {{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
			for (int y = 1; y <= 4; y++) {
				s.set(c[0], y, c[1], p.log());
			}
		}
		for (int i = 0; i <= 4; i++) {
			s.set(i, 5, 0, Buildings.Sketch.lying(p.log(), Direction.Axis.X));
			s.set(i, 5, 4, Buildings.Sketch.lying(p.log(), Direction.Axis.X));
		}
		for (int i = 1; i < 4; i++) {
			s.set(0, 5, i, Buildings.Sketch.lying(p.log(), Direction.Axis.Z));
			s.set(4, 5, i, Buildings.Sketch.lying(p.log(), Direction.Axis.Z));
		}
		s.set(2, 4, 2, Buildings.Sketch.hanging(Blocks.LANTERN));
		s.set(2, 5, 2, p.fence());
		for (int x = 1; x < 4; x++) {
			s.set(x, 1, 1, p.trapdoor());
		}
		// the track out to the spoil heap
		for (int z = 5; z < d; z++) {
			s.set(2, 1, z, Blocks.RAIL.defaultBlockState());
		}
		for (int x = 5; x < w; x++) {
			for (int z = 5; z < d; z++) {
				int height = Math.max(0, 3 - Math.abs(x - 7) - Math.abs(z - 7));
				for (int y = 1; y <= height; y++) {
					s.set(x, y, z, y == height && k.chance(0.4) ? Blocks.COAL_ORE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
				}
			}
		}
		// the lean-to by the shaft: furnace, chest, a bench
		for (int z = 0; z < 4; z++) {
			s.set(8, 1, z, p.log());
			s.set(8, 2, z, p.planks());
			s.set(6, 3, z, k.roofSlab);
			s.set(7, 3, z, k.roofSlab);
			s.set(8, 3, z, k.roofSlab);
		}
		s.set(7, 1, 0, Buildings.Sketch.facing(Blocks.FURNACE, Direction.WEST));
		s.set(7, 1, 1, Buildings.Sketch.facing(Blocks.CHEST, Direction.WEST));
		s.set(7, 1, 2, Blocks.IRON_ORE.defaultBlockState());
		s.set(7, 1, 3, Blocks.CRAFTING_TABLE.defaultBlockState());
		return s.done();
	}

	/**
	 * A dock (9 x 13): a plank quay on log piles with a boat shed's roof over its far end, crates and barrels, a drying
	 * rack of kelp, a lantern at the head. (It stands on the shore; the boats are the haven's own.) (0.7.490) Its head is on
	 * dry land toward the town and its far end, the shed's, out over the water ({@link Planner#setShore}): a haven with no
	 * shore in its land builds none.
	 */
	static Blueprint dock(Buildings.Kit k) {
		Palette p = k.p;
		int w = 9, d = 13;
		Buildings.Sketch s = new Buildings.Sketch(DOCK, w, d, 8);
		for (int x = 2; x < w - 2; x++) {
			for (int z = 0; z < d; z++) {
				s.set(x, 0, z, p.planks());
			}
		}
		for (int z = 1; z < d; z += 3) {
			for (int x : new int[] {2, w - 3}) {
				s.set(x, 0, z, p.log());
				s.set(x, 1, z, p.fence());
			}
		}
		// a boat shed over the water end: four posts, a plank roof
		for (int[] c : new int[][] {{2, 6}, {w - 3, 6}, {2, d - 1}, {w - 3, d - 1}}) {
			for (int y = 1; y <= 3; y++) {
				s.set(c[0], y, c[1], p.log());
			}
		}
		for (int x = 2; x <= w - 3; x++) {
			for (int z = 6; z < d; z++) {
				s.set(x, 4, z, x == 2 || x == w - 3 ? k.roofSlab : p.planks());
			}
		}
		s.set(w / 2, 3, 8, Buildings.Sketch.hanging(Blocks.LANTERN));
		// the quay's furniture: barrels, a chest, the kelp rack
		s.set(3, 1, 1, Blocks.BARREL.defaultBlockState());
		s.set(3, 1, 2, Blocks.BARREL.defaultBlockState());
		s.set(w - 4, 1, 1, Buildings.Sketch.facing(Blocks.CHEST, Direction.WEST));
		for (int x = 3; x <= w - 4; x++) {
			s.set(x, 2, 4, p.fence());
		}
		s.set(3, 1, 4, p.fence());
		s.set(w - 4, 1, 4, p.fence());
		s.set(4, 1, 4, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
		s.set(w - 5, 1, 4, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
		return s.done();
	}

	/** A mill (9 x 7): an open shed on posts with stacked logs, a stonecutter for a saw, barrels of herbs and a roof of planks. */
	static Blueprint mill(Buildings.Kit k) {
		Palette p = k.p;
		int w = 9, d = 7;
		Buildings.Sketch s = new Buildings.Sketch(MILL, w, d, 7);
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				s.set(x, 0, z, k.road);
			}
		}
		for (int[] c : new int[][] {{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}, {w / 2, 0}, {w / 2, d - 1}}) {
			for (int y = 1; y <= 3; y++) {
				s.set(c[0], y, c[1], p.log());
			}
		}
		s.gable(0, w - 1, 0, d - 1, 4, k.roofStairs, k.roofSlab, p.planks());
		s.set(2, 1, 3, Buildings.Sketch.facing(Blocks.STONECUTTER, Direction.SOUTH));
		s.set(3, 1, 3, p.planks());
		// stacked logs along the back, on their sides
		for (int x = 1; x < w - 1; x++) {
			s.set(x, 1, d - 2, Buildings.Sketch.lying(p.log(), Direction.Axis.X));
			if (x % 2 == 1) {
				s.set(x, 2, d - 2, Buildings.Sketch.lying(p.log(), Direction.Axis.X));
			}
		}
		s.set(w - 2, 1, 1, Blocks.BARREL.defaultBlockState());
		s.set(w - 2, 1, 2, Blocks.BARREL.defaultBlockState());
		s.set(w - 3, 1, 1, Blocks.POTTED_FERN.defaultBlockState());
		s.set(w / 2, 3, 3, Buildings.Sketch.hanging(Blocks.LANTERN));
		return s.done();
	}

	/** Registers the haven works in the catalogues of the settlements that can be havens (built only where {@link HavenGrowth#allows}). */
	static void register() {
		Planner.setShore(DOCK, DOCK_WATER_ROW, DOCK_WET);
		for (FactionType type : new FactionType[] {FactionType.VILLAGE, FactionType.KINGDOM, FactionType.FIEFDOM}) {
			Buildings.realm(type, FIELDS, HavenBuildings::fields, HavenGrowth.MOST_BUILT, HavenGrowth.FIELDS_FROM);
			Buildings.realm(type, WORKSHOP, HavenBuildings::workshop, HavenGrowth.MOST_BUILT, HavenGrowth.WORKSHOP_FROM);
			Buildings.realm(type, MINE, HavenBuildings::mine, HavenGrowth.MOST_BUILT, HavenGrowth.MINE_FROM);
			Buildings.realm(type, DOCK, HavenBuildings::dock, HavenGrowth.MOST_BUILT, HavenGrowth.DOCK_FROM);
			Buildings.realm(type, MILL, HavenBuildings::mill, HavenGrowth.MOST_BUILT, HavenGrowth.MILL_FROM);
		}
	}
}
