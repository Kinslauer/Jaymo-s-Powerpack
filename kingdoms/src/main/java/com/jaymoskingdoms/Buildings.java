package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The building catalogue of kingdoms, fiefdoms and villages: every design drawn in code from the faction's own
 * {@link Palette} (and its colour, for wool and beds), like the castles. All are drawn with their door on the north side
 * (z = 0) and y = 0 the floor laid in place of the ground; the construction code turns them to face the seat. Each
 * type's growth order is registered here ({@link Blueprint#register}): an entry may come back later with a higher
 * count and a bigger settlement (villagers plus folk) needed, so the town grows in stages, outward ring by ring.
 */
final class Buildings {
	/** The ring (blocks from the seat) where a realm's walls, towers and gatehouses go up, and a village's palisade. */
	static final int WALL_RING_MIN = 56, WALL_RING_MAX = 92, PALISADE_RING_MIN = 26, PALISADE_RING_MAX = 46;

	private Buildings() {
	}

	static void register() {
		for (FactionType type : new FactionType[] {FactionType.KINGDOM, FactionType.FIEFDOM}) {
			realm(type, "house", Buildings::house, 2, 0);
			realm(type, "farm", Buildings::farm, 1, 0);
			realm(type, "smithy", Buildings::smithy, 1, 0);
			realm(type, "road", Buildings::road, 2, 0);
			realm(type, "well", Buildings::well, 1, 0);
			realm(type, "house", Buildings::house, 4, 16);
			realm(type, "mason", Buildings::mason, 1, 20);
			realm(type, "market", Buildings::market, 1, 24);
			realm(type, "stable", Buildings::stable, 1, 28);
			realm(type, "farm", Buildings::farm, 2, 28);
			realm(type, "road", Buildings::road, 4, 28);
			realm(type, "barracks", Buildings::barracks, 1, 32);
			realm(type, "house", Buildings::house, 7, 36);
			realm(type, "wall", Buildings::wall, 12, 40);
			realm(type, "tower", Buildings::tower, 4, 40);
			realm(type, "gatehouse", Buildings::gatehouse, 2, 40);
			realm(type, "farm", Buildings::farm, 4, 44);
			realm(type, "house", Buildings::house, 10, 48);
			realm(type, "wall", Buildings::wall, 24, 56);
			realm(type, "tower", Buildings::tower, 8, 56);
		}
		realm(FactionType.VILLAGE, "house", Buildings::house, 2, 0);
		realm(FactionType.VILLAGE, "farm", Buildings::farm, 1, 0);
		realm(FactionType.VILLAGE, "well", Buildings::well, 1, 0);
		realm(FactionType.VILLAGE, "stall", Buildings::stall, 1, 8);
		realm(FactionType.VILLAGE, "road", Buildings::road, 2, 8);
		realm(FactionType.VILLAGE, "house", Buildings::house, 4, 12);
		realm(FactionType.VILLAGE, "farm", Buildings::farm, 2, 14);
		realm(FactionType.VILLAGE, "stall", Buildings::stall, 2, 16);
		realm(FactionType.VILLAGE, "road", Buildings::road, 4, 16);
		realm(FactionType.VILLAGE, "house", Buildings::house, 6, 20);
		realm(FactionType.VILLAGE, "palisade", Buildings::palisade, 10, 22);
		realm(FactionType.VILLAGE, "farm", Buildings::farm, 3, 24);
		realm(FactionType.VILLAGE, "house", Buildings::house, 8, 28);
		for (String ring : new String[] {"wall", "tower", "gatehouse"}) {
			Planner.setRings(ring, WALL_RING_MIN, WALL_RING_MAX);
		}
		Planner.setRings("palisade", PALISADE_RING_MIN, PALISADE_RING_MAX);
	}

	private interface Design {
		Blueprint draw(Kit k);
	}

	private static void realm(FactionType type, String id, Design design, int max, int minSize) {
		Blueprint.register(type, new Blueprint.Entry(id, (level, faction) -> design.draw(Kit.of(level, faction)), max, minSize));
	}

	// ---- the designs ----------------------------------------------------------------------------------------------

	/** A cottage (7 x 7): a stone course and timber walls (all timber in a village), a gabled roof, a bed, a table. */
	static Blueprint house(Kit k) {
		Sketch s = new Sketch("house", 7, 7, 8);
		Palette p = k.p;
		BlockState foundation = k.village ? Blocks.COBBLESTONE.defaultBlockState() : p.wall();
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
				boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
				s.set(x, 0, z, edge ? foundation : k.village ? p.planks() : p.floor());
				if (!edge) {
					continue;
				}
				for (int y = 1; y <= 3; y++) {
					s.set(x, y, z, corner ? p.log() : y == 1 && !k.village ? p.wall() : p.planks());
				}
			}
		}
		s.door(3, 1, 0, Direction.NORTH, k.door);
		for (int[] w : new int[][] {{0, 3}, {6, 3}, {3, 6}, {1, 0}, {5, 0}}) {
			s.set(w[0], 2, w[1], Blocks.GLASS_PANE.defaultBlockState());
		}
		s.gable(0, 6, 0, 6, 4, k.village ? k.woodStairs : p.stairs(), p.woodSlab(), p.planks());
		s.set(3, 5, 0, Blocks.GLASS_PANE.defaultBlockState());
		s.bed(1, 1, 4, Direction.SOUTH, k.bed);
		s.set(5, 1, 5, Blocks.CRAFTING_TABLE.defaultBlockState());
		s.set(3, 1, 3, k.carpet);
		s.set(3, 3, 5, Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		return s.done();
	}

	/** A field (9 x 7): a log kerb, farmland round a water hole, wheat, carrots and potatoes, a fence with a gate, a composter. */
	static Blueprint farm(Kit k) {
		Sketch s = new Sketch("farm", 9, 7, 2);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 7; z++) {
				boolean edge = x == 0 || x == 8 || z == 0 || z == 6;
				if (edge) {
					s.set(x, 0, z, k.p.log());
					s.set(x, 1, z, k.p.fence());
				} else if (x == 4 && z == 3) {
					s.set(x, 0, z, Blocks.WATER.defaultBlockState());
				} else {
					s.set(x, 0, z, Blocks.FARMLAND.defaultBlockState());
					s.set(x, 1, z, (x < 4 ? Blocks.WHEAT : z % 2 == 0 ? Blocks.CARROTS : Blocks.POTATOES).defaultBlockState());
				}
			}
		}
		s.set(4, 1, 0, Sketch.facing(k.gate, Direction.NORTH));
		s.set(8, 1, 6, Blocks.COMPOSTER.defaultBlockState());
		return s.done();
	}

	/** A well (5 x 5): a stone ring round the water, four posts and a little roof. */
	static Blueprint well(Kit k) {
		Sketch s = new Sketch("well", 5, 5, 5);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
				boolean middle = x == 2 && z == 2;
				s.set(x, 0, z, edge ? k.road : middle ? Blocks.WATER.defaultBlockState() : k.p.wall());
				if (!edge && !middle) {
					s.set(x, 1, z, k.p.wall());
				}
				if (!edge) {
					s.set(x, 4, z, k.p.woodSlab());
				}
			}
		}
		for (int[] c : new int[][] {{1, 1}, {3, 1}, {1, 3}, {3, 3}}) {
			s.set(c[0], 2, c[1], k.p.fence());
			s.set(c[0], 3, c[1], k.p.fence());
		}
		return s.done();
	}

	/** A village market stall (5 x 5): log posts, a wool awning in the village's colour, a counter, wares. */
	static Blueprint stall(Kit k) {
		Sketch s = new Sketch("stall", 5, 5, 4);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				s.set(x, 0, z, k.road);
				s.set(x, 3, z, (x + z) % 2 == 0 ? k.wool : Kit.WHITE_WOOL.defaultBlockState());
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					s.set(x, 1, z, k.p.log());
					s.set(x, 2, z, k.p.log());
				}
			}
		}
		for (int x = 1; x <= 3; x++) {
			s.set(x, 1, 1, Sketch.top(k.p.woodSlab()));
		}
		s.set(1, 1, 3, Blocks.BARREL.defaultBlockState());
		s.set(3, 1, 3, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(2, 1, 3, k.carpet);
		return s.done();
	}

	/** A crossroads (9 x 9): two roads three wide (dirt paths in a village, the realm's road stone), two lamp posts. */
	static Blueprint road(Kit k) {
		Sketch s = new Sketch("road", 9, 9, 4);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				if ((x >= 3 && x <= 5) || (z >= 3 && z <= 5)) {
					s.set(x, 0, z, k.road);
				}
			}
		}
		for (int c : new int[] {2, 6}) {
			s.set(c, 1, c, k.p.fence());
			s.set(c, 2, c, k.p.fence());
			s.set(c, 3, c, Blocks.TORCH.defaultBlockState());
		}
		return s.done();
	}

	/** A smithy (9 x 7): stone walls open to the front, a furnace, smithing table, anvil and grindstone, a smoking chimney. */
	static Blueprint smithy(Kit k) {
		Sketch s = new Sketch("smithy", 9, 7, 8);
		Palette p = k.p;
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, p.floor());
				boolean wall = z == 6 || x == 0 || x == 8;
				boolean post = (x == 0 || x == 8) && z == 0;
				for (int y = 1; y <= 3; y++) {
					if (post) {
						s.set(x, y, z, p.log());
					} else if (wall) {
						s.set(x, y, z, Sketch.weathered(p, x, y, z));
					}
				}
				s.set(x, 4, z, p.slab());
			}
		}
		s.set(1, 1, 5, Sketch.facing(Blocks.BLAST_FURNACE, Direction.NORTH));
		s.set(2, 1, 5, Blocks.SMITHING_TABLE.defaultBlockState());
		s.set(4, 1, 5, Blocks.ANVIL.defaultBlockState());
		s.set(6, 1, 5, Blocks.GRINDSTONE.defaultBlockState());
		for (int y = 1; y <= 6; y++) {
			s.set(7, y, 5, p.wall());
		}
		s.set(7, 7, 5, Blocks.CAMPFIRE.defaultBlockState());
		s.set(4, 3, 3, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
		return s.done();
	}

	/** A mason's yard (7 x 7): weathered stone walls, a door, a flat roof, a stonecutter and a pile of cut stone. */
	static Blueprint mason(Kit k) {
		Sketch s = new Sketch("mason", 7, 7, 5);
		Palette p = k.p;
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
				boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
				s.set(x, 0, z, p.floor());
				if (edge) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, corner ? p.trim() : Sketch.weathered(p, x, y, z));
					}
				}
				s.set(x, 4, z, p.slab());
			}
		}
		s.door(3, 1, 0, Direction.NORTH, k.door);
		s.set(0, 2, 3, Blocks.GLASS_PANE.defaultBlockState());
		s.set(6, 2, 3, Blocks.GLASS_PANE.defaultBlockState());
		s.set(5, 1, 5, Sketch.facing(Blocks.STONECUTTER, Direction.NORTH));
		s.set(1, 1, 5, p.wall());
		s.set(1, 2, 5, p.slab());
		s.set(1, 1, 4, p.slab());
		s.set(3, 3, 5, Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		return s.done();
	}

	/** A stable (11 x 7): two stalls behind fences and gates, log posts, a plank roof, hay and a trough. */
	static Blueprint stable(Kit k) {
		Sketch s = new Sketch("stable", 11, 7, 5);
		Palette p = k.p;
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, Blocks.COARSE_DIRT.defaultBlockState());
				boolean edge = x == 0 || x == 10 || z == 0 || z == 6;
				boolean post = (x == 0 || x == 5 || x == 10) && (z == 0 || z == 6);
				if (post) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, p.log());
					}
				} else if (edge && z != 0) {
					s.set(x, 1, z, p.planks());
					s.set(x, 2, z, p.planks());
					s.set(x, 3, z, p.planks());
				} else if (z == 0) {
					s.set(x, 1, z, p.fence());
					s.set(x, 3, z, p.planks());
				} else if (x == 5) {
					s.set(x, 1, z, p.fence());
				}
				s.set(x, 4, z, p.woodSlab());
			}
		}
		s.set(2, 1, 0, Sketch.facing(k.gate, Direction.NORTH));
		s.set(8, 1, 0, Sketch.facing(k.gate, Direction.NORTH));
		s.set(1, 1, 5, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(9, 1, 5, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(4, 1, 5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		s.set(6, 1, 5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		return s.done();
	}

	/** A market square (9 x 9): paved, four stalls under striped awnings in the realm's colour, a lamp in the middle. */
	static Blueprint market(Kit k) {
		Sketch s = new Sketch("market", 9, 9, 4);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				s.set(x, 0, z, k.road);
			}
		}
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			for (int dx = 0; dx <= 2; dx++) {
				for (int dz = 0; dz <= 2; dz++) {
					s.set(c[0] + dx, 3, c[1] + dz, (dx + dz) % 2 == 0 ? k.wool : Kit.WHITE_WOOL.defaultBlockState());
					if ((dx == 0 || dx == 2) && (dz == 0 || dz == 2)) {
						s.set(c[0] + dx, 1, c[1] + dz, k.p.fence());
						s.set(c[0] + dx, 2, c[1] + dz, k.p.fence());
					}
				}
			}
			s.set(c[0] + 1, 1, c[1] + 1, (c[0] + c[1]) % 12 == 0 ? Blocks.BARREL.defaultBlockState() : Sketch.top(k.p.woodSlab()));
		}
		s.set(4, 1, 4, k.p.trim());
		s.set(4, 2, 4, Blocks.LANTERN.defaultBlockState());
		return s.done();
	}

	/** Barracks (11 x 9): a stone hall with a parapet, four bunks along the back, a door and windows. */
	static Blueprint barracks(Kit k) {
		Sketch s = new Sketch("barracks", 11, 9, 6);
		Palette p = k.p;
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 9; z++) {
				boolean edge = x == 0 || x == 10 || z == 0 || z == 8;
				boolean corner = (x == 0 || x == 10) && (z == 0 || z == 8);
				s.set(x, 0, z, edge ? p.wall() : p.floor());
				if (edge) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, corner ? p.trim() : Sketch.weathered(p, x, y, z));
					}
					s.set(x, 4, z, p.wall());
					if ((x + z) % 2 == 0) {
						s.set(x, 5, z, p.wall());
					}
				} else {
					s.set(x, 4, z, p.slab());
				}
			}
		}
		s.door(5, 1, 0, Direction.NORTH, k.door);
		for (int[] w : new int[][] {{2, 0}, {8, 0}, {0, 4}, {10, 4}}) {
			s.set(w[0], 2, w[1], Blocks.GLASS_PANE.defaultBlockState());
		}
		for (int x : new int[] {1, 3, 7, 9}) {
			s.bed(x, 1, 6, Direction.SOUTH, k.bed);
		}
		s.set(5, 1, 7, Blocks.CRAFTING_TABLE.defaultBlockState());
		s.set(5, 3, 7, Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		s.set(5, 3, 1, Sketch.facing(Blocks.WALL_TORCH, Direction.SOUTH));
		return s.done();
	}

	/** A section of the town wall (9 x 3): five courses of weathered stone, crenellations on the outer side. */
	static Blueprint wall(Kit k) {
		Sketch s = new Sketch("wall", 9, 3, 6);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 3; z++) {
				for (int y = 0; y <= 4; y++) {
					s.set(x, y, z, y == 0 ? k.p.wall() : Sketch.weathered(k.p, x, y, z));
				}
			}
			if (x % 2 == 0) {
				s.set(x, 5, 2, k.p.wall());
			}
		}
		return s.done();
	}

	/** A wall tower (7 x 7): ten courses, a door, arrow slits, a floor halfway, a ladder to a crenellated top. */
	static Blueprint tower(Kit k) {
		Sketch s = new Sketch("tower", 7, 7, 11);
		Palette p = k.p;
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
				s.set(x, 0, z, edge ? p.wall() : p.floor());
				for (int y = 1; y <= 8; y++) {
					boolean slit = (y == 3 || y == 6) && ((x == 3 && z == 6) || (z == 3 && (x == 0 || x == 6)));
					if (edge && !slit) {
						s.set(x, y, z, Sketch.weathered(p, x, y, z));
					}
				}
				if (!edge) {
					s.set(x, 5, z, p.planks());
				}
				s.set(x, 9, z, p.wall());
				if (edge && (x + z) % 2 == 0) {
					s.set(x, 10, z, p.wall());
				}
			}
		}
		s.door(3, 1, 0, Direction.NORTH, k.door);
		for (int y = 1; y <= 9; y++) {
			s.set(3, y, 5, Sketch.facing(Blocks.LADDER, Direction.NORTH));
		}
		s.set(1, 3, 1, Sketch.facing(Blocks.WALL_TORCH, Direction.SOUTH));
		s.set(1, 7, 1, Sketch.facing(Blocks.WALL_TORCH, Direction.SOUTH));
		return s.done();
	}

	/** A gatehouse (9 x 5): two stone piers, an arch three wide over the road, crenellations along the top. */
	static Blueprint gatehouse(Kit k) {
		Sketch s = new Sketch("gatehouse", 9, 5, 8);
		Palette p = k.p;
		for (int x = 0; x < 9; x++) {
			boolean arch = x >= 3 && x <= 5;
			for (int z = 0; z < 5; z++) {
				s.set(x, 0, z, arch ? k.road : p.wall());
				for (int y = 1; y <= 6; y++) {
					if (!arch || y >= 4) {
						s.set(x, y, z, Sketch.weathered(p, x, y, z));
					}
				}
				if ((z == 0 || z == 4) && x % 2 == 0) {
					s.set(x, 7, z, p.wall());
				}
			}
		}
		for (int z : new int[] {0, 4}) {
			s.set(3, 3, z, Sketch.upsideDown(p.stairs(), Direction.WEST));
			s.set(5, 3, z, Sketch.upsideDown(p.stairs(), Direction.EAST));
		}
		return s.done();
	}

	/** A village palisade section (9 x 2): sharpened logs on a cobble footing. */
	static Blueprint palisade(Kit k) {
		Sketch s = new Sketch("palisade", 9, 2, 4);
		for (int x = 0; x < 9; x++) {
			s.set(x, 0, 0, Blocks.COBBLESTONE.defaultBlockState());
			s.set(x, 0, 1, Blocks.COBBLESTONE.defaultBlockState());
			for (int y = 1; y <= (x % 2 == 0 ? 3 : 2); y++) {
				s.set(x, y, 1, k.p.log());
			}
		}
		return s.done();
	}

	// ---- drawing helpers ------------------------------------------------------------------------------------------

	/** A faction's materials: its palette plus the wood's door, stairs, gate, and wool, bed and carpet in its colour. */
	static final class Kit {
		/** (coloured blocks are looked up by name: 26.3 keeps them in collections, not fields) */
		static final Block WHITE_WOOL = block("white_wool", Blocks.HAY_BLOCK);

		final Palette p;
		final boolean village;
		final Block door, gate;
		final BlockState woodStairs, wool, bed, carpet, road;

		private Kit(Palette p, boolean village, String wood, String color) {
			this.p = p;
			this.village = village;
			this.door = block(wood + "_door", Blocks.OAK_DOOR);
			this.gate = block(wood + "_fence_gate", Blocks.OAK_FENCE_GATE);
			this.woodStairs = block(wood + "_stairs", Blocks.OAK_STAIRS).defaultBlockState();
			this.wool = block(color + "_wool", Kit.WHITE_WOOL).defaultBlockState();
			this.bed = block(color + "_bed", Kit.WHITE_WOOL).defaultBlockState();
			this.carpet = block(color + "_carpet", Kit.WHITE_WOOL).defaultBlockState();
			this.road = village ? Blocks.DIRT_PATH.defaultBlockState() : p.path();
		}

		static Kit of(ServerLevel level, Faction faction) {
			Palette p = Palette.at(level, faction.seat());
			String planks = BuiltInRegistries.BLOCK.getKey(p.planks().getBlock()).getPath();
			String wood = planks.endsWith("_planks") ? planks.substring(0, planks.length() - "_planks".length()) : "oak";
			return new Kit(p, faction.type() == FactionType.VILLAGE, wood, faction.styleColor(level.getServer()).getName());
		}

		private static Block block(String name, Block fallback) {
			Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
			return b == Blocks.AIR ? fallback : b;
		}

		/** A wooden sign of this wood (for the foreman's stakes). */
		static Block sign(Palette p) {
			String planks = BuiltInRegistries.BLOCK.getKey(p.planks().getBlock()).getPath();
			String wood = planks.endsWith("_planks") ? planks.substring(0, planks.length() - "_planks".length()) : "oak";
			return block(wood + "_sign", Blocks.OAK_SIGN);
		}
	}

	/** A blueprint being drawn: later blocks at the same spot replace earlier ones; laid bottom up. */
	static final class Sketch {
		private final String id;
		private final int w, d, h;
		private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		Sketch(String id, int w, int d, int h) {
			this.id = id;
			this.w = w;
			this.d = d;
			this.h = h;
		}

		void set(int x, int y, int z, BlockState state) {
			if (x < 0 || x >= this.w || z < 0 || z >= this.d || y < 0 || y >= this.h) {
				return;
			}
			BlockPos at = new BlockPos(x, y, z);
			this.blocks.remove(at);
			this.blocks.put(at, state);
		}

		void door(int x, int y, int z, Direction facing, Block door) {
			BlockState lower = door.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			this.set(x, y, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			this.set(x, y + 1, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		}

		/** A bed: its foot here, its head toward {@code facing}. */
		void bed(int x, int y, int z, Direction facing, BlockState bed) {
			if (!bed.hasProperty(BlockStateProperties.BED_PART)) {
				return;
			}
			BlockState foot = bed.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
			this.set(x, y, z, foot.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
			this.set(x + facing.getStepX(), y, z + facing.getStepZ(), foot.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		}

		/** A gabled roof over x0..x1 (its ridge running along z0..z1), from {@code y}; the gable ends filled. */
		void gable(int x0, int x1, int z0, int z1, int y, BlockState stairs, BlockState ridge, BlockState fill) {
			int half = (x1 - x0) / 2;
			for (int k = 0; k <= half; k++) {
				for (int z = z0; z <= z1; z++) {
					if (x0 + k == x1 - k) {
						this.set(x0 + k, y + k, z, ridge);
					} else {
						this.set(x0 + k, y + k, z, stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
						this.set(x1 - k, y + k, z, stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
					}
				}
				for (int x = x0 + k + 1; x <= x1 - k - 1; x++) {
					this.set(x, y + k, z0, fill);
					this.set(x, y + k, z1, fill);
				}
			}
		}

		static BlockState facing(Block block, Direction facing) {
			BlockState state = block.defaultBlockState();
			return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : state;
		}

		static BlockState top(BlockState slab) {
			return slab.hasProperty(BlockStateProperties.SLAB_TYPE) ? slab.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP) : slab;
		}

		static BlockState upsideDown(BlockState stairs, Direction facing) {
			if (!stairs.hasProperty(BlockStateProperties.HALF)) {
				return stairs;
			}
			return stairs.setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.HALF, Half.TOP);
		}

		/** Mostly the palette's wall, here and there mossy or cracked (the same every time for the same spot). */
		static BlockState weathered(Palette p, int x, int y, int z) {
			int n = Math.floorMod(x * 31 + y * 17 + z * 7, 11);
			return n == 0 ? p.mossy() : n == 5 ? p.cracked() : p.wall();
		}

		Blueprint done() {
			List<Blueprint.Placement> out = new ArrayList<>();
			this.blocks.forEach((at, state) -> out.add(new Blueprint.Placement(at, state)));
			out.sort(Comparator.comparingInt(pl -> pl.offset().getY()));
			return new Blueprint(this.id, this.w, this.d, this.h, List.copyOf(out));
		}
	}
}
