package com.jaymoskingdoms;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.250) Andy (2026-10-27): Tax' Village Architect "adds the new buildings we need for jobs (butcher, stable, etc)".
 * It is all rights reserved and Forge/NeoForge 1.21.1 only, so these are our own: the job halls a settlement builds as
 * it grows ({@link Buildings#register}), drawn in code from its {@link Buildings.Kit} like every other building (so
 * among the cherry trees they take the Blossom land's dark tile roofs and white plaster). Each holds its trade's job site
 * block, so a villager who moves in near it takes the trade (a smoker for a butcher, a barrel for a fisherman, a
 * fletching table for a fletcher), and each is a player job's workplace ({@link Careers.Job#hall}): a town where it
 * stands is always hiring for that job and pays more an order. Like every design, door (or front) on the north side
 * (z = 0), y = 0 the floor laid in place of the ground.
 */
final class JobHalls {
	private JobHalls() {
	}

	/** (0.7.483) The land a hall is built for, so each draws its own design there (Andy: "their own specific buildings per biome"). */
	enum Region {
		/** plains, forest and the rest: timber and plank */
		GREEN,
		/** snow and taiga: shut in stone, round a fire */
		COLD,
		/** desert, badlands, savanna: flat roofs, thick walls, striped awnings */
		DRY,
		/** jungle and swamp: open sides for the air, deep hipped roofs */
		WET,
		/** among the cherry trees: white plaster, paper windows, dark tile */
		BLOSSOM;

		static Region of(Buildings.Kit k) {
			Palette land = k.land;
			if (land == Palette.CHERRY) {
				return BLOSSOM;
			} else if (land == Palette.SNOWY || land == Palette.TAIGA) {
				return COLD;
			} else if (land == Palette.DESERT || land == Palette.BADLANDS || land == Palette.SAVANNA) {
				return DRY;
			} else if (land == Palette.JUNGLE || land == Palette.SWAMP) {
				return WET;
			}
			return GREEN;
		}
	}

	/** (0.7.483) A butcher's shop in the land's own way: a smokehouse in the cold, a market stall in the dry, an open shed in the wet. */
	static Blueprint butcher(Buildings.Kit k) {
		return switch (Region.of(k)) {
			case COLD -> smokehouse(k);
			case DRY -> meatStall(k);
			case WET -> butcherShed(k);
			default -> butcherShop(k);
		};
	}

	/**
	 * A butcher's shop (7 x 7): an open counter across the front with a gate through it, a smoker, a chopping block and
	 * a barrel of cuts at the back, chains hanging from the roof beam, stone or plank walls, a gable along the counter.
	 */
	static Blueprint butcherShop(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("butcher", 7, 7, 12);
		Palette p = k.p;
		boolean wooden = k.chance(0.5);
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, p.floor());
				boolean side = x == 0 || x == 6;
				boolean back = z == 6;
				if (!side && !back) {
					continue;
				}
				for (int y = 1; y <= 3; y++) {
					boolean corner = side && (z == 0 || z == 6);
					s.set(x, y, z, corner ? p.log() : wooden ? p.planks() : Buildings.Sketch.weathered(p, x, y, z, k.salt));
				}
			}
		}
		// the counter, with a gate through its middle and a beam over the serving window
		for (int x = 1; x <= 5; x++) {
			if (x == 3) {
				s.set(x, 1, 0, Buildings.Sketch.facing(k.gate, Direction.NORTH));
			} else {
				s.set(x, 1, 0, Buildings.Sketch.top(p.woodSlab()));
			}
			s.set(x, 3, 0, Buildings.Sketch.lying(p.log(), Direction.Axis.X));
		}
		boolean mirrored = k.chance(0.5);
		int left = mirrored ? 5 : 1, right = mirrored ? 1 : 5;
		s.set(right, 1, 5, Buildings.Sketch.facing(Blocks.SMOKER, Direction.NORTH));
		s.set(left, 1, 5, Blocks.BARREL.defaultBlockState());
		s.set(3, 1, 5, k.stripped);
		if (k.chance(0.5)) {
			s.set(3, 2, 5, k.plate());
		}
		// a beam down the middle to hang the chains and the lamp from, under the roof
		for (int x = 1; x <= 5; x++) {
			s.set(x, 4, 3, Buildings.Sketch.lying(p.log(), Direction.Axis.X));
		}
		s.set(2, 3, 3, hook());
		s.set(4, 3, 3, hook());
		s.set(3, 3, 3, Buildings.Sketch.hanging(Blocks.LANTERN));
		s.gableX(0, 6, 0, 6, 4, k.roofStairs, k.roofSlab, wooden ? p.planks() : p.wall());
		BlockState rack = Displays.rack(Direction.NORTH);
		if (rack != null && k.chance(0.7)) {
			s.set(left, 2, 5, rack);
		}
		// hay for the beasts waiting their turn
		if (k.chance(0.5)) {
			s.set(mirrored ? 1 : 5, 1, 1, Blocks.HAY_BLOCK.defaultBlockState());
		}
		return s.done();
	}

	/**
	 * A fishery (7 x 9): a drying rack and stacks of dried kelp out front, and a plank hut behind with the catch in a
	 * barrel, a chest of tackle and a window either side.
	 */
	static Blueprint fishery(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("fishery", 7, 9, 12);
		Palette p = k.p;
		// the yard: the rack's posts and rail, the kelp drying
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 3; z++) {
				s.set(x, 0, z, k.road);
			}
		}
		int[] posts = k.chance(0.5) ? new int[] {0, 6} : new int[] {1, 5};
		for (int x : posts) {
			s.set(x, 1, 1, p.fence());
			s.set(x, 2, 1, p.fence());
		}
		for (int x = posts[0]; x <= posts[1]; x++) {
			s.set(x, 3, 1, p.fence());
		}
		s.set(2, 1, 1, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
		if (k.chance(0.6)) {
			s.set(4, 1, 1, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
			s.set(4, 2, 1, Blocks.DRIED_KELP_BLOCK.defaultBlockState());
		}
		// the hut
		for (int x = 0; x < 7; x++) {
			for (int z = 3; z < 9; z++) {
				boolean edge = x == 0 || x == 6 || z == 3 || z == 8;
				boolean corner = (x == 0 || x == 6) && (z == 3 || z == 8);
				s.set(x, 0, z, edge ? p.planks() : p.floor());
				if (!edge) {
					continue;
				}
				for (int y = 1; y <= 3; y++) {
					s.set(x, y, z, corner ? p.log() : p.planks());
				}
			}
		}
		s.door(3, 1, 3, Direction.NORTH, k.door);
		s.set(0, 2, 5, k.pane);
		s.set(6, 2, 5, k.pane);
		s.gable(0, 6, 3, 8, 4, k.roofStairs, k.roofSlab, p.planks());
		s.set(5, 1, 7, Blocks.BARREL.defaultBlockState());
		s.set(1, 1, 7, Buildings.Sketch.facing(Blocks.CHEST, Direction.NORTH));
		s.set(3, 3, 7, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		if (k.chance(0.5)) {
			s.set(5, 2, 7, Blocks.LANTERN.defaultBlockState());
		}
		return s.done();
	}

	/**
	 * A hunting lodge (9 x 7): a log cabin with a fletching table, a chest, pelts on the floor and a rack for the bows,
	 * and beside it a target on a hay bale to practise on.
	 */
	static Blueprint huntingLodge(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("hunting_lodge", 9, 7, 12);
		Palette p = k.p;
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				boolean alongX = z == 0 || z == 6;
				boolean edge = x == 0 || x == 6 || alongX;
				boolean corner = (x == 0 || x == 6) && alongX;
				s.set(x, 0, z, edge ? p.wall() : p.floor());
				if (!edge) {
					continue;
				}
				for (int y = 1; y <= 3; y++) {
					// logs laid along each wall, upright at the corners
					s.set(x, y, z, corner ? p.log() : Buildings.Sketch.lying(p.log(), alongX ? Direction.Axis.X : Direction.Axis.Z));
				}
			}
		}
		s.door(3, 1, 0, Direction.NORTH, k.door);
		s.set(0, 2, 3, k.pane);
		s.set(6, 2, 3, k.pane);
		s.gable(0, 6, 0, 6, 4, k.roofStairs, k.roofSlab, p.planks());
		s.set(1, 1, 5, Blocks.FLETCHING_TABLE.defaultBlockState());
		s.set(5, 1, 5, Buildings.Sketch.facing(Blocks.CHEST, Direction.NORTH));
		BlockState pelt = named("brown_carpet");
		if (pelt != null) {
			s.set(3, 1, 3, pelt);
			if (k.chance(0.5)) {
				s.set(2, 1, 3, pelt);
				s.set(4, 1, 3, pelt);
			}
		}
		BlockState rack = Displays.rack(Direction.NORTH);
		if (rack != null && k.chance(0.8)) {
			s.set(3, 2, 5, rack);
		}
		s.set(3, 3, 5, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		// the practice butt beside the door
		for (int z = 0; z < 7; z++) {
			s.set(7, 0, z, k.road);
			s.set(8, 0, z, k.road);
		}
		s.set(8, 1, 4, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(8, 2, 4, Blocks.TARGET.defaultBlockState());
		return s.done();
	}

	/**
	 * (0.7.384) A training hall (9 x 9, Weapon Forms): the school's hall in a realm, a school master's post ({@link SchoolHalls}
	 * puts them at its middle). Plank-and-timber walls with a window either side, a gable roof, a runner of carpet down the
	 * floor, a pair of training posts (Powers' block if it is loaded; else bales) with a practice butt beside the door side, and
	 * a tool rack either side of the back wall under wall torches.
	 */
	static Blueprint trainingHall(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("training_hall", 9, 9, 12);
		Palette p = k.p;
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
				boolean corner = (x == 0 || x == 8) && (z == 0 || z == 8);
				s.set(x, 0, z, edge ? p.wall() : p.floor());
				if (!edge) {
					continue;
				}
				for (int y = 1; y <= 3; y++) {
					s.set(x, y, z, corner ? p.log() : Buildings.Sketch.weathered(p, x, y, z, k.salt));
				}
			}
		}
		s.door(4, 1, 0, Direction.NORTH, k.door);
		for (int z : new int[] {2, 6}) {
			s.set(0, 2, z, k.pane);
			s.set(8, 2, z, k.pane);
		}
		s.set(2, 2, 8, k.pane);
		s.set(6, 2, 8, k.pane);
		s.gable(0, 8, 0, 8, 4, k.roofStairs, k.roofSlab, p.planks());
		// the runner down the middle, keeping the master's place at the centre open
		for (int z = 1; z <= 7; z++) {
			for (int x = 3; x <= 5; x++) {
				s.set(x, 1, z, k.carpet);
			}
		}
		BlockState post = trainingPost();
		s.set(1, 1, 7, post);
		s.set(7, 1, 7, post);
		// the practice butt: a bale with a target on it
		s.set(7, 1, 1, Blocks.HAY_BLOCK.defaultBlockState());
		s.set(7, 2, 1, Blocks.TARGET.defaultBlockState());
		BlockState rack = Displays.rack(Direction.NORTH);
		for (int x : new int[] {2, 6}) {
			if (rack != null && k.chance(0.85)) {
				s.set(x, 2, 7, rack);
			}
			s.set(x, 3, 7, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		}
		return s.done();
	}

	// ---- (0.7.483) the butcher's shop by region ---------------------------------------------------------------------

	/**
	 * In the snow and the taiga, a smokehouse (7 x 7): shut-in stone walls and a door against the cold, the smoker and a
	 * fire on a hearth at the back, cuts hanging on chains from the beam, a chopping block, a barrel and a fur underfoot.
	 */
	static Blueprint smokehouse(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("butcher", 7, 7, 12);
		Palette p = k.p;
		shell(s, k, 7, 7, true);
		s.door(3, 1, 0, Direction.NORTH, k.door);
		s.set(0, 2, 3, k.pane);
		s.set(6, 2, 3, k.pane);
		s.gable(0, 6, 0, 6, 4, k.roofStairs, k.roofSlab, p.planks());
		// the hearth: a fire between two stones at the back, the smoker beside it
		boolean mirrored = k.chance(0.5);
		s.set(3, 1, 5, Blocks.CAMPFIRE.defaultBlockState());
		s.set(2, 1, 5, p.wall());
		s.set(4, 1, 5, p.wall());
		s.set(mirrored ? 1 : 5, 1, 5, Buildings.Sketch.facing(Blocks.SMOKER, Direction.NORTH));
		s.set(mirrored ? 5 : 1, 1, 5, Blocks.BARREL.defaultBlockState());
		s.set(mirrored ? 5 : 1, 1, 2, k.stripped);
		s.set(mirrored ? 5 : 1, 2, 2, k.plate());
		s.set(3, 1, 2, carpet("brown_carpet", k));
		beam(s, k, 1, 5, 3);
		s.set(2, 3, 3, hook());
		s.set(4, 3, 3, hook());
		s.set(3, 3, 3, Buildings.Sketch.hanging(Blocks.LANTERN));
		return s.done();
	}

	/**
	 * In the desert, the badlands and the savanna, a meat stall (7 x 7): four posts under a striped awning in the
	 * settlement's colours, a low back wall for shade, a counter of slabs with a gate, the smoker and a barrel behind it,
	 * cuts hanging from the awning and pots of salt and spice.
	 */
	static Blueprint meatStall(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("butcher", 7, 7, 12);
		Palette p = k.p;
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, p.floor());
				// the awning, striped across
				s.set(x, 4, z, x % 2 == 0 ? k.wool : k.accentWool);
			}
			s.set(x, 1, 6, p.wall());
			s.set(x, 2, 6, p.wall());
		}
		for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) {
			for (int y = 1; y <= 3; y++) {
				s.set(c[0], y, c[1], p.log());
			}
		}
		for (int x = 1; x <= 5; x++) {
			s.set(x, 1, 1, x == 3 ? Buildings.Sketch.facing(k.gate, Direction.NORTH) : Buildings.Sketch.top(p.woodSlab()));
		}
		boolean mirrored = k.chance(0.5);
		s.set(mirrored ? 1 : 5, 1, 5, Buildings.Sketch.facing(Blocks.SMOKER, Direction.NORTH));
		s.set(mirrored ? 5 : 1, 1, 5, Blocks.BARREL.defaultBlockState());
		s.set(3, 1, 5, k.stripped);
		s.set(mirrored ? 2 : 4, 2, 1, Blocks.DECORATED_POT.defaultBlockState());
		if (k.chance(0.6)) {
			s.set(mirrored ? 4 : 2, 2, 1, Blocks.DECORATED_POT.defaultBlockState());
		}
		s.set(2, 3, 3, hook());
		s.set(4, 3, 3, hook());
		s.set(3, 3, 4, Buildings.Sketch.hanging(Blocks.LANTERN));
		return s.done();
	}

	/**
	 * In the jungle and the swamp, a butcher's shed (7 x 7): open sides behind a rail for the air, posts under a deep
	 * hipped roof, the smoker and a barrel at the back, a chopping block, hooks on chains and a lantern from the beam.
	 */
	static Blueprint butcherShed(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("butcher", 7, 7, 12);
		Palette p = k.p;
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				s.set(x, 0, z, p.planks());
				boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
				boolean post = (x == 0 || x == 6 || x == 3) && (z == 0 || z == 6 || z == 3);
				if (post && edge) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, p.log());
					}
				} else if (edge && !(z == 0 && x == 3)) {
					s.set(x, 1, z, p.fence());
				}
			}
		}
		s.set(3, 1, 0, Buildings.Sketch.facing(k.gate, Direction.NORTH));
		s.hip(0, 6, 0, 6, 4, k.roofStairs, k.roofSlab);
		boolean mirrored = k.chance(0.5);
		s.set(mirrored ? 1 : 5, 1, 5, Buildings.Sketch.facing(Blocks.SMOKER, Direction.NORTH));
		s.set(mirrored ? 5 : 1, 1, 5, Blocks.BARREL.defaultBlockState());
		s.set(3, 1, 4, k.stripped);
		beam(s, k, 1, 5, 3);
		s.set(2, 3, 3, hook());
		s.set(4, 3, 3, hook());
		s.set(3, 3, 2, Buildings.Sketch.hanging(Blocks.LANTERN));
		return s.done();
	}

	// ---- (0.7.483) the healer's infirmary --------------------------------------------------------------------------

	/**
	 * (0.7.483) The healer's infirmary, where a town's cleric works: a brewing stand (their job site, so a cleric who
	 * moves in near it takes the trade), a cauldron of water, infirmary cots along the side walls ({@link InfirmaryCot};
	 * one near a brewing stand treats you as you lie on it), herbs in pots and a lantern. A kingdom's is bigger (11 x 9,
	 * four cots, a lectern for the healer's book) than a village's (9 x 7, two). Each land builds its own:
	 * <ul>
	 * <li>green lands: a timber-framed house under a gable;</li>
	 * <li>the cold: thick stone round a hearth at the back, fur rugs;</li>
	 * <li>the dry: a flat roof behind a parapet, grilles in the windows, pots of herbs and spice;</li>
	 * <li>the wet: half walls and open sides under a deep hipped roof, ferns;</li>
	 * <li>the Blossom land: white plaster between posts, paper windows, a dark tile hipped roof.</li>
	 * </ul>
	 */
	static Blueprint infirmary(Buildings.Kit k) {
		Region region = Region.of(k);
		boolean big = !k.village;
		int w = big ? 11 : 9, d = big ? 9 : 7;
		Buildings.Sketch s = new Buildings.Sketch("infirmary", w, d, 14);
		Palette p = k.p;
		int mid = w / 2;
		switch (region) {
			case COLD -> {
				shell(s, k, w, d, true);
				windows(s, k, w, d, k.pane);
				s.gable(0, w - 1, 0, d - 1, 4, k.roofStairs, k.roofSlab, p.wall());
			}
			case DRY -> {
				shell(s, k, w, d, true);
				windows(s, k, w, d, p.fence());
				s.flat(0, w - 1, 0, d - 1, 4, p.wall(), p.slab());
			}
			case WET -> {
				for (int x = 0; x < w; x++) {
					for (int z = 0; z < d; z++) {
						boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
						boolean post = (x == 0 || x == w - 1 || x == mid && z == d - 1) && (z == 0 || z == d - 1) || (x == 0 || x == w - 1) && z == d / 2;
						s.set(x, 0, z, p.planks());
						if (z == 0 && x == mid) {
							// the open doorway, a beam over it
							s.set(x, 3, z, Buildings.Sketch.lying(p.log(), Direction.Axis.X));
						} else if (post) {
							for (int y = 1; y <= 3; y++) {
								s.set(x, y, z, p.log());
							}
						} else if (edge) {
							s.set(x, 1, z, p.wall());
							s.set(x, 2, z, p.fence());
						}
					}
				}
				s.hip(0, w - 1, 0, d - 1, 4, k.roofStairs, k.roofSlab);
			}
			case BLOSSOM -> {
				for (int x = 0; x < w; x++) {
					for (int z = 0; z < d; z++) {
						boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
						s.set(x, 0, z, edge ? p.log() : p.planks());
						if (!edge) {
							continue;
						}
						boolean post = x % 2 == 0 && (z == 0 || z == d - 1) || z % 2 == 0 && (x == 0 || x == w - 1);
						for (int y = 1; y <= 3; y++) {
							s.set(x, y, z, post || y == 3 ? p.log() : k.infill);
						}
					}
				}
				BlockState paper = named("white_stained_glass_pane");
				windows(s, k, w, d, paper != null ? paper : k.pane);
				s.hip(0, w - 1, 0, d - 1, 4, k.roofStairs, k.roofSlab);
			}
			default -> {
				// a timber frame: logs at the corners and every few blocks, the infill between, a beam along the top
				for (int x = 0; x < w; x++) {
					for (int z = 0; z < d; z++) {
						boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
						s.set(x, 0, z, p.floor());
						if (!edge) {
							continue;
						}
						boolean post = (x == 0 || x == w - 1) && (z == 0 || z == d - 1) || (z == 0 || z == d - 1) && x % 4 == 0
							|| (x == 0 || x == w - 1) && z % 4 == 0;
						for (int y = 1; y <= 3; y++) {
							s.set(x, y, z, post || y == 3 ? p.log() : k.infill);
						}
					}
				}
				windows(s, k, w, d, k.pane);
				s.gable(0, w - 1, 0, d - 1, 4, k.roofStairs, k.roofSlab, p.planks());
			}
		}
		if (region != Region.WET) {
			s.door(mid, 1, 0, Direction.NORTH, k.door);
		}
		// the cots along the side walls, heads to the wall
		BlockState cot = Healing.COT != null ? Healing.COT.defaultBlockState() : k.bed;
		int[] rows = big ? new int[] {3, 5} : new int[] {3};
		for (int z : rows) {
			s.bed(2, 1, z, Direction.WEST, cot);
			s.bed(w - 3, 1, z, Direction.EAST, cot);
		}
		// the back: a cauldron of water, the brewing stand, and between them a hearth in the cold, else a table of herbs
		s.set(mid - 2, 1, d - 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		s.set(mid + 2, 1, d - 2, Blocks.BREWING_STAND.defaultBlockState());
		if (region == Region.COLD) {
			s.set(mid, 1, d - 2, Blocks.CAMPFIRE.defaultBlockState());
			s.set(mid - 1, 1, d - 2, p.wall());
			s.set(mid + 1, 1, d - 2, p.wall());
			for (int z : rows) {
				s.set(mid, 1, z - 1, carpet("brown_carpet", k));
			}
		} else {
			s.set(mid, 1, d - 2, Buildings.Sketch.top(p.woodSlab()));
			s.set(mid, 2, d - 2, herbs(region, k));
			s.set(mid - 1, 1, d - 2, Blocks.BARREL.defaultBlockState());
		}
		if (big) {
			s.set(w - 2, 1, 1, Buildings.Sketch.facing(Blocks.LECTERN, Direction.WEST));
			s.set(1, 1, 1, Buildings.Sketch.facing(Blocks.CHEST, Direction.EAST));
		}
		// herbs by the door
		s.set(mid - 1, 1, 1, herbs(region, k));
		s.set(mid + 1, 1, 1, herbs(region, k));
		// the light: from a beam under the ridge, or the flat roof
		if (region == Region.DRY) {
			s.set(mid, 3, d / 2, Buildings.Sketch.hanging(Blocks.LANTERN));
		} else {
			for (int z = 1; z < d - 1; z++) {
				s.set(mid, 4, z, Buildings.Sketch.lying(p.log(), Direction.Axis.Z));
			}
			s.set(mid, 3, d / 2, Buildings.Sketch.hanging(Blocks.LANTERN));
		}
		return s.done();
	}

	/** Walls round {@code w} x {@code d} at y 1 to 3: logs at the corners, the stonework (weathered) or planks between; the floor inside. */
	private static void shell(Buildings.Sketch s, Buildings.Kit k, int w, int d, boolean stone) {
		Palette p = k.p;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
				boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
				s.set(x, 0, z, edge ? p.wall() : p.floor());
				if (!edge) {
					continue;
				}
				for (int y = 1; y <= 3; y++) {
					s.set(x, y, z, corner ? p.log() : stone ? Buildings.Sketch.weathered(p, x, y, z, k.salt) : p.planks());
				}
			}
		}
	}

	/** Windows at head height: one each side by every pair of cots, two in the back wall. */
	private static void windows(Buildings.Sketch s, Buildings.Kit k, int w, int d, BlockState pane) {
		for (int z = 3; z < d - 1; z += 2) {
			s.set(0, 2, z, pane);
			s.set(w - 1, 2, z, pane);
		}
		s.set(2, 2, d - 1, pane);
		s.set(w - 3, 2, d - 1, pane);
	}

	/** A log beam along x from {@code x0} to {@code x1} at y 4 over row {@code z}, to hang things from. */
	private static void beam(Buildings.Sketch s, Buildings.Kit k, int x0, int x1, int z) {
		for (int x = x0; x <= x1; x++) {
			s.set(x, 4, z, Buildings.Sketch.lying(k.p.log(), Direction.Axis.X));
		}
	}

	/** Herbs in a pot: ferns in the wet, a dead bush or cactus in the dry, flowers elsewhere. */
	private static BlockState herbs(Region region, Buildings.Kit k) {
		String pot = switch (region) {
			case WET -> "potted_fern";
			case DRY -> k.chance(0.5) ? "potted_cactus" : "potted_dead_bush";
			case COLD -> "potted_spruce_sapling";
			case BLOSSOM -> "potted_cherry_sapling";
			default -> null;
		};
		BlockState state = pot == null ? null : named(pot);
		return state != null ? state : k.flowerPot();
	}

	/** (0.7.483) Jaymo's Kitchen's meat hook where Kitchen is loaded (carcasses hang on it to bleed), else a chain. */
	private static BlockState hook() {
		Block hook = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("jaymoskitchen:meat_hook"));
		return (hook == Blocks.AIR ? Blocks.IRON_CHAIN : hook).defaultBlockState();
	}

	/** A carpet by name, or the settlement's own. */
	private static BlockState carpet(String name, Buildings.Kit k) {
		BlockState c = named(name);
		return c != null ? c : k.carpet;
	}

	/** Powers' training post, where Powers is loaded (the belt can be set at it), else a bale of hay. */
	private static BlockState trainingPost() {
		Block post = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("jaymospowers:training_post"));
		return (post == Blocks.AIR ? Blocks.HAY_BLOCK : post).defaultBlockState();
	}

	private static @Nullable BlockState named(String name) {
		Block b = Townscape.named(name);
		return b == null ? null : b.defaultBlockState();
	}
}
