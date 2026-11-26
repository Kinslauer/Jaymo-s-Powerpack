package com.jaymoskingdoms;

import com.jaymoskingdoms.blossomland.BlossomBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The village styles' buildings ({@link VillageStyles}), all drawn in code like every other design here, from one small
 * set of shapes: {@link House} (a cottage on the ground, on a plinth or on legs, under any of four roofs), {@link #hole}
 * (a hill-hole), {@link #palm}, {@link #stall}, {@link #well} and the few one-offs (the clock tower, the mooring, the
 * temple, the keep). A {@link Look} says what a style builds in, so a mud-brick town and a log-cabin village are the
 * same cottage in other materials. Like every design, the front is the north side (z = 0) and y = 0 is laid in place of
 * the ground.
 */
final class StyleBuildings {
	enum Roof {
		GABLE, GABLE_X, HIP, FLAT
	}

	/** What a style builds in: walls, corner posts, floor, the footing under it, the roof, the gable's infill, windows, fences, doors. */
	record Look(BlockState wall, BlockState post, BlockState floor, BlockState foot, BlockState roofStairs, BlockState roofSlab, BlockState gable,
		BlockState window, BlockState fence, Block door, boolean logWalls, BlockState skirt, int postEvery) {
		static Look of(BlockState wall, BlockState post, BlockState floor, BlockState roofStairs, BlockState roofSlab) {
			return new Look(wall, post, floor, wall, roofStairs, roofSlab, floor, Blocks.GLASS_PANE.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(),
				Blocks.OAK_DOOR, false, null, 0);
		}

		/** A settlement's own kit (its land's palette and woods): what the capitals' and the market towns' houses are made of. */
		static Look of(Buildings.Kit k) {
			return new Look(k.p.wall(), k.p.log(), k.p.planks(), k.p.wall(), k.roofStairs, k.roofSlab, k.infill, k.pane, k.p.fence(), k.door,
				false, null, 0);
		}

		Look foot(BlockState v) {
			return new Look(this.wall, this.post, this.floor, v, this.roofStairs, this.roofSlab, this.gable, this.window, this.fence, this.door, this.logWalls, this.skirt, this.postEvery);
		}

		Look gable(BlockState v) {
			return new Look(this.wall, this.post, this.floor, this.foot, this.roofStairs, this.roofSlab, v, this.window, this.fence, this.door, this.logWalls, this.skirt, this.postEvery);
		}

		Look window(BlockState v) {
			return new Look(this.wall, this.post, this.floor, this.foot, this.roofStairs, this.roofSlab, this.gable, v, this.fence, this.door, this.logWalls, this.skirt, this.postEvery);
		}

		Look fence(BlockState v) {
			return new Look(this.wall, this.post, this.floor, this.foot, this.roofStairs, this.roofSlab, this.gable, this.window, v, this.door, this.logWalls, this.skirt, this.postEvery);
		}

		Look door(Block v) {
			return new Look(this.wall, this.post, this.floor, this.foot, this.roofStairs, this.roofSlab, this.gable, this.window, this.fence, v, this.logWalls, this.skirt, this.postEvery);
		}

		Look logs() {
			return new Look(this.wall, this.post, this.floor, this.foot, this.roofStairs, this.roofSlab, this.gable, this.window, this.fence, this.door, true, this.skirt, this.postEvery);
		}

		/** A bottom course of {@code skirt}, and a post in the wall every {@code every} blocks (the frame of a timber-and-paper house). */
		Look framed(BlockState skirt, int every) {
			return new Look(this.wall, this.post, this.floor, this.foot, this.roofStairs, this.roofSlab, this.gable, this.window, this.fence, this.door, this.logWalls, skirt, every);
		}
	}

	private StyleBuildings() {
	}

	/** A vanilla block by name (26.3 keeps the coloured ones in collections, not fields). */
	static BlockState named(String name, Block fallback) {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
		return (b == Blocks.AIR ? fallback : b).defaultBlockState();
	}

	static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	static BlockState leaves() {
		return Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	/** Reed thatch (the Blossom land's), or hay where the blocks aren't there. */
	static BlockState thatch() {
		return BlossomBlocks.THATCH != null ? BlossomBlocks.THATCH.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState();
	}

	static BlockState thatchStairs() {
		return BlossomBlocks.THATCH_STAIRS != null ? BlossomBlocks.THATCH_STAIRS.defaultBlockState() : Blocks.OAK_STAIRS.defaultBlockState();
	}

	static BlockState thatchSlab() {
		return BlossomBlocks.THATCH_SLAB != null ? BlossomBlocks.THATCH_SLAB.defaultBlockState() : Blocks.OAK_SLAB.defaultBlockState();
	}

	// ---- the cottage -------------------------------------------------------------------------------------------------

	/**
	 * A cottage {@code w} x {@code d} (odd widths, so a roof has a ridge), walls {@code h} high, on a plinth {@code plinth} high (solid, or
	 * on legs), with a porch {@code porch} deep along its front (steps up from the ground to it), under a {@link Roof}, with
	 * a chimney and a loft if asked. A bed, a table and a stove inside; the inside is carved clear (these stand on slopes
	 * and in the bog, where the ground is in the way).
	 */
	static final class House {
		final String id;
		final int w, d, h;
		int plinth, porch;
		boolean steps, legs, chimney, loft;
		Roof roof = Roof.GABLE;
		Fit fit;

		/** Furnishes a house for a trade: the sketch, the floor's layer, and the front and back rows of its body. */
		interface Fit {
			void on(Buildings.Sketch s, int floor, int front, int back, int w);
		}

		House fit(Fit v) {
			this.fit = v;
			return this;
		}

		House(String id, int w, int d, int h) {
			this.id = id;
			this.w = w;
			this.d = d;
			this.h = h;
		}

		House plinth(int v) {
			this.plinth = v;
			return this;
		}

		House porch(int v) {
			this.porch = v;
			return this;
		}

		House steps() {
			this.steps = true;
			return this;
		}

		House legs() {
			this.legs = true;
			return this;
		}

		House chimney() {
			this.chimney = true;
			return this;
		}

		House loft() {
			this.loft = true;
			return this;
		}

		House roof(Roof v) {
			this.roof = v;
			return this;
		}

		Blueprint build(Look l) {
			int p = this.plinth, front = this.steps && p > 0 ? Math.max(this.porch, p + 1) : this.porch, back = this.d - 1;
			int walls = this.loft ? this.h * 2 + 1 : this.h, top = p + walls, mid = this.w / 2;
			Buildings.Sketch s = new Buildings.Sketch(this.id, this.w, this.d, top + Math.max(this.w, this.d) / 2 + 6);
			for (int x = 0; x < this.w; x++) {
				for (int z = 0; z < this.d; z++) {
					boolean edge = x == 0 || x == this.w - 1 || z == 0 || z == back;
					if (p > 0) {
						boolean leg = edge && (x == 0 || x == this.w - 1 || z == 0 || z == back) && (((x + z) & 1) == 0);
						for (int y = 0; y < p; y++) {
							if (!this.legs || leg) {
								s.set(x, y, z, this.legs ? l.post() : l.foot());
							}
						}
					}
					s.set(x, p, z, l.floor());
				}
			}
			for (int x = 0; x < this.w; x++) {
				for (int z = front; z <= back; z++) {
					boolean sideX = x == 0 || x == this.w - 1, sideZ = z == front || z == back;
					for (int y = p + 1; y <= top; y++) {
						if (!sideX && !sideZ) {
							s.set(x, y, z, air());
							continue;
						}
						boolean corner = sideX && sideZ;
						BlockState wall = l.wall();
						if (l.logWalls()) {
							wall = Buildings.Sketch.lying(l.wall(), sideZ ? Direction.Axis.X : Direction.Axis.Z);
						}
						if (l.postEvery() > 0 && !corner && (sideZ ? x : z) % l.postEvery() == 0) {
							wall = l.post();
						}
						if (l.skirt() != null && y == p + 1 && !corner) {
							wall = l.skirt();
						}
						s.set(x, y, z, corner ? l.post() : wall);
					}
				}
			}
			// windows (side and back walls, and the front either side of the door), on each storey
			for (int storey = 0; storey < (this.loft ? 2 : 1); storey++) {
				int y = p + 2 + storey * (this.h + 1), zm = (front + back) / 2;
				s.set(0, y, zm, l.window());
				s.set(this.w - 1, y, zm, l.window());
				s.set(mid, y, back, l.window());
				s.set(1, y, front, l.window());
				s.set(this.w - 2, y, front, l.window());
			}
			s.door(mid, p + 1, front, Direction.NORTH, l.door());
			if (this.loft) {
				for (int x = 1; x < this.w - 1; x++) {
					for (int z = front + 1; z < back; z++) {
						s.set(x, p + this.h + 1, z, x == 1 && z == front + 1 ? air() : l.floor());
					}
				}
				for (int y = p + 1; y <= p + this.h + 1; y++) {
					s.set(1, y, front + 1, Buildings.Sketch.facing(Blocks.LADDER, Direction.SOUTH));
				}
				s.bed(this.w - 3, p + this.h + 2, back - 2, Direction.SOUTH, named("brown_bed", Blocks.AIR));
				s.set(2, p + this.h + 2, back - 1, Blocks.BARREL.defaultBlockState());
			}
			// the roof
			int roofY = top + 1;
			switch (this.roof) {
				case GABLE -> s.gable(0, this.w - 1, front, back, roofY, l.roofStairs(), l.roofSlab(), l.gable());
				case GABLE_X -> s.gableX(0, this.w - 1, front, back, roofY, l.roofStairs(), l.roofSlab(), l.gable());
				case HIP -> s.hip(0, this.w - 1, front, back, roofY, l.roofStairs(), l.roofSlab());
				case FLAT -> s.flat(0, this.w - 1, front, back, roofY, l.wall(), l.roofSlab());
			}
			// inside: a bed, a table with a lamp, a stove
			if (this.d - front >= 5 && this.w >= 5) {
				s.bed(1, p + 1, back - 2, Direction.SOUTH, named("red_bed", Blocks.AIR));
				s.set(this.w - 2, p + 1, back - 1, Blocks.CRAFTING_TABLE.defaultBlockState());
				s.set(this.w - 2, p + 2, back - 1, Blocks.LANTERN.defaultBlockState());
				s.set(this.w - 3, p + 1, back - 1, Buildings.Sketch.facing(Blocks.FURNACE, Direction.NORTH));
			}
			// the porch: a rail along its front and sides, a lamp on the corner post
			if (front > 0 && p > 0) {
				for (int x = 0; x < this.w; x++) {
					if (x != mid) {
						s.set(x, p + 1, 0, l.fence());
					}
				}
				for (int z = 1; z < front; z++) {
					s.set(0, p + 1, z, l.fence());
					s.set(this.w - 1, p + 1, z, l.fence());
				}
				s.set(0, p + 2, 0, Blocks.LANTERN.defaultBlockState());
				if (this.steps) {
					for (int i = 0; i < p; i++) {
						s.set(mid, i + 1, i, Buildings.Sketch.stair(Blocks.OAK_STAIRS.defaultBlockState(), Direction.SOUTH));
						for (int y = 0; y <= i; y++) {
							s.set(mid, y, i, l.foot());
						}
					}
				}
			}
			if (this.fit != null) {
				this.fit.on(s, p, front, back, this.w);
			}
			if (this.chimney) {
				int x = this.w - 2;
				for (int y = p + 2; y <= top + 3; y++) {
					s.set(x, y, back, l.foot());
				}
				s.set(x, top + 4, back, Blocks.CAMPFIRE.defaultBlockState());
			}
			return s.done();
		}
	}

	// ---- a hill-hole -----------------------------------------------------------------------------------------------------

	/**
	 * A hill-hole: half a ball of turf {@code r} across the door's wall, hollowed out, the door round (a ring of pale logs
	 * round a small door), a bed and a table inside, a chimney through the turf, and flowers on top (the roof is a garden).
	 */
	static Blueprint hole(int r, RandomSource random) {
		int w = r * 2 + 1, cx = r;
		Buildings.Sketch s = new Buildings.Sketch("hole", w, r + 2, r + 7);
		BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
		BlockState ring = named("stripped_birch_log", Blocks.OAK_LOG), planks = Blocks.OAK_PLANKS.defaultBlockState();
		int[] top = new int[w * (r + 2)];
		for (int x = 0; x < w; x++) {
			for (int z = 0; z <= r + 1; z++) {
				for (int y = 0; y <= r; y++) {
					double dist = Math.sqrt((x - cx) * (x - cx) + y * y + z * z);
					if (dist > r) {
						continue;
					}
					top[x * (r + 2) + z] = Math.max(top[x * (r + 2) + z], y + 1);
					boolean hollow = z >= 1 && dist <= r - 1.5 && y >= 1;
					if (hollow) {
						s.set(x, y, z, air());
					} else if (z == 0) {
						s.set(x, y, z, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
					} else if (y == 0 && dist <= r - 1.5) {
						s.set(x, y, z, planks);
					} else {
						s.set(x, y, z, dist > r - 1 ? ((x + z) % 3 == 0 ? moss : grass) : dirt);
					}
				}
			}
		}
		// the round door in its front wall
		for (int y = 1; y <= 2; y++) {
			s.set(cx - 1, y, 0, ring);
			s.set(cx + 1, y, 0, ring);
		}
		s.set(cx, 3, 0, ring);
		s.set(cx - 1, 3, 0, Buildings.Sketch.upsideDown(Blocks.BIRCH_STAIRS.defaultBlockState(), Direction.EAST));
		s.set(cx + 1, 3, 0, Buildings.Sketch.upsideDown(Blocks.BIRCH_STAIRS.defaultBlockState(), Direction.WEST));
		s.door(cx, 1, 0, Direction.NORTH, Blocks.BIRCH_DOOR);
		s.set(cx - 2, 2, 0, Blocks.LANTERN.defaultBlockState());
		// inside
		s.bed(cx - 2, 1, 2, Direction.EAST, named("green_bed", Blocks.AIR));
		s.set(cx + 2, 1, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
		s.set(cx + 2, 2, 1, Blocks.LANTERN.defaultBlockState());
		s.set(cx + 1, 1, 2, Blocks.COMPOSTER.defaultBlockState());
		// the chimney, and the roof garden
		int chx = cx + 2, chz = 2;
		for (int y = 3; y <= r + 2; y++) {
			if (y >= 1) {
				s.set(chx, y, chz, Blocks.STONE_BRICK_WALL.defaultBlockState());
			}
		}
		s.set(chx, r + 3, chz, Blocks.CAMPFIRE.defaultBlockState());
		String[] flowers = {"poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley"};
		for (int x = 0; x < w; x++) {
			for (int z = 1; z <= r + 1; z++) {
				int t = top[x * (r + 2) + z];
				if (t > 0 && (x != chx || z != chz) && random.nextInt(4) == 0) {
					BlockState plant = random.nextInt(5) == 0 ? Blocks.FLOWERING_AZALEA.defaultBlockState() : named(flowers[random.nextInt(flowers.length)], Blocks.DANDELION);
					s.set(x, t, z, plant);
				}
			}
		}
		return s.done();
	}

	/** A hamlet's garden (7 x 5): moss beds fenced round, a composter, flowers, mushrooms and saplings (what a gnome keeps). */
	static Blueprint garden(RandomSource random) {
		Buildings.Sketch s = new Buildings.Sketch("garden", 7, 5, 3);
		String[] plants = {"poppy", "dandelion", "cornflower", "red_mushroom", "brown_mushroom", "oak_sapling", "birch_sapling", "allium"};
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 5; z++) {
				boolean edge = x == 0 || x == 6 || z == 0 || z == 4;
				s.set(x, 0, z, Blocks.MOSS_BLOCK.defaultBlockState());
				if (edge && !(z == 0 && x == 3)) {
					s.set(x, 1, z, Blocks.OAK_FENCE.defaultBlockState());
				} else if (!edge) {
					s.set(x, 1, z, named(plants[random.nextInt(plants.length)], Blocks.DANDELION));
				}
			}
		}
		s.set(3, 1, 0, Blocks.OAK_FENCE_GATE.defaultBlockState());
		s.set(5, 1, 3, Blocks.COMPOSTER.defaultBlockState());
		return s.done();
	}

	// ---- small things ---------------------------------------------------------------------------------------------------

	/** A well (5 x 5): a ring of stone round water, two posts and a roof, a bucket's chain. */
	static Blueprint well(Look l) {
		Buildings.Sketch s = new Buildings.Sketch("well", 5, 5, 6);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean ring = x == 0 || x == 4 || z == 0 || z == 4;
				s.set(x, 0, z, ring ? l.foot() : Blocks.WATER.defaultBlockState());
				if (ring) {
					s.set(x, 1, z, (x == 0 || x == 4) && (z == 0 || z == 4) ? l.post() : Blocks.COBBLESTONE_SLAB.defaultBlockState());
				}
			}
		}
		for (int y = 2; y <= 3; y++) {
			s.set(0, y, 2, l.post());
			s.set(4, y, 2, l.post());
		}
		for (int x = 0; x < 5; x++) {
			s.set(x, 4, 2, l.roofSlab());
		}
		s.set(2, 3, 2, Blocks.IRON_CHAIN.defaultBlockState());
		return s.done();
	}

	/** A market stall (5 x 3): four posts, a counter, and a striped awning in two colours. */
	static Blueprint stall(Look l, String colour, String second) {
		Buildings.Sketch s = new Buildings.Sketch("stall", 5, 3, 5);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 3; z++) {
				s.set(x, 3, z, named((x % 2 == 0 ? colour : second) + "_wool", Blocks.HAY_BLOCK));
				s.set(x, 0, z, l.floor());
			}
			s.set(x, 1, 1, x % 2 == 0 ? Blocks.BARREL.defaultBlockState() : l.fence());
		}
		for (int[] c : new int[][] {{0, 0}, {4, 0}, {0, 2}, {4, 2}}) {
			s.set(c[0], 1, c[1], l.fence());
			s.set(c[0], 2, c[1], l.fence());
		}
		s.set(2, 2, 1, Blocks.LANTERN.defaultBlockState());
		return s.done();
	}

	/** A date palm into {@code s}: a trunk {@code h} high leaning a little, a crown of fronds, and pods of dates under it. */
	static void palm(Buildings.Sketch s, int cx, int cz, int h) {
		BlockState log = Blocks.JUNGLE_LOG.defaultBlockState();
		for (int y = 0; y < h; y++) {
			s.set(cx + (y >= h - 2 ? 1 : 0), y, cz, log);
		}
		int tx = cx + 1;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int n = Math.abs(dx) + Math.abs(dz);
				if (n <= 3 && n >= 1) {
					s.set(tx + dx, h, cz + dz, leaves());
				}
				if ((Math.abs(dx) == 3 && dz == 0) || (Math.abs(dz) == 3 && dx == 0)) {
					s.set(tx + dx, h - 1, cz + dz, leaves());
				}
			}
		}
		s.set(tx, h + 1, cz, leaves());
		s.set(tx, h, cz, log);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (d == Direction.EAST) {
				continue;
			}
			s.set(tx + d.getStepX(), h - 1, cz + d.getStepZ(), Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE, 2)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, d.getOpposite()));
		}
	}

	/** A palm grove (9 x 9): sandstone beds ringed round a water channel, a palm in each corner bed and a row of date-pod trees between. */
	static Blueprint grove(Look l, RandomSource random) {
		Buildings.Sketch s = new Buildings.Sketch("grove", 9, 9, 14);
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
				s.set(x, 0, z, z == 4 ? Blocks.WATER.defaultBlockState() : edge ? l.foot() : Blocks.COARSE_DIRT.defaultBlockState());
				if (edge && !(z == 0 && x == 4)) {
					s.set(x, 1, z, Blocks.SANDSTONE_WALL.defaultBlockState());
				}
			}
		}
		palm(s, 1, 2, 6 + random.nextInt(2));
		palm(s, 5, 2, 6 + random.nextInt(2));
		palm(s, 1, 6, 6 + random.nextInt(2));
		palm(s, 5, 6, 6 + random.nextInt(2));
		return s.done();
	}

	/** A walled well (11 x 11): a sandstone wall, a gate on the front, the well in the middle with benches round it. */
	static Blueprint wallyard(Look l) {
		Buildings.Sketch s = new Buildings.Sketch("wallyard", 11, 11, 7);
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 11; z++) {
				s.set(x, 0, z, l.floor());
				boolean edge = x == 0 || x == 10 || z == 0 || z == 10;
				if (edge && !(z == 0 && x >= 4 && x <= 6)) {
					for (int y = 1; y <= 3; y++) {
						s.set(x, y, z, y == 3 && (x + z) % 2 == 0 ? l.wall() : y == 3 ? l.roofSlab() : l.wall());
					}
				}
			}
		}
		for (int y = 1; y <= 4; y++) {
			s.set(3, y, 0, l.post());
			s.set(7, y, 0, l.post());
		}
		s.set(3, 5, 0, Blocks.LANTERN.defaultBlockState());
		s.set(7, 5, 0, Blocks.LANTERN.defaultBlockState());
		for (int x = 4; x <= 6; x++) {
			for (int z = 4; z <= 6; z++) {
				boolean mid = x == 5 && z == 5;
				s.set(x, 0, z, mid ? Blocks.WATER.defaultBlockState() : l.foot());
				if (!mid) {
					s.set(x, 1, z, Blocks.SANDSTONE_SLAB.defaultBlockState());
				}
			}
		}
		for (int[] b : new int[][] {{2, 5}, {8, 5}}) {
			s.set(b[0], 1, b[1], Buildings.Sketch.stair(Blocks.SANDSTONE_STAIRS.defaultBlockState(), b[0] < 5 ? Direction.EAST : Direction.WEST));
		}
		return s.done();
	}

	/** A whitewashed lighthouse (5 x 5): a tapering tower, a lantern room with a beacon-lit lamp, a rail. */
	static Blueprint lighthouse(Look l) {
		Buildings.Sketch s = new Buildings.Sketch("lighthouse", 7, 7, 20);
		for (int y = 0; y <= 13; y++) {
			int in = y >= 8 ? 2 : y >= 4 ? 1 : 0;
			for (int x = in; x < 7 - in; x++) {
				for (int z = in; z < 7 - in; z++) {
					boolean edge = x == in || x == 6 - in || z == in || z == 6 - in;
					if (y == 0 || edge) {
						s.set(x, y, z, y % 4 == 3 && edge ? named("blue_concrete", Blocks.LAPIS_BLOCK) : l.wall());
					}
				}
			}
		}
		s.door(3, 1, 0, Direction.NORTH, l.door());
		for (int y = 1; y <= 12; y++) {
			s.set(3, y, 2, Buildings.Sketch.facing(Blocks.LADDER, Direction.SOUTH));
		}
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				boolean edge = x == 1 || x == 5 || z == 1 || z == 5;
				s.set(x, 14, z, edge ? l.fence() : l.floor());
				if (edge) {
					s.set(x, 15, z, Blocks.GLASS.defaultBlockState());
					s.set(x, 16, z, Blocks.GLASS.defaultBlockState());
				}
			}
		}
		s.set(3, 15, 3, Blocks.LANTERN.defaultBlockState());
		s.hip(0, 6, 0, 6, 17, l.roofStairs(), l.roofSlab());
		return s.done();
	}

	/** A pile of timber (5 x 3): cut logs stacked, a sawhorse, a splitting block (the timber trade's yard). */
	static Blueprint timberYard(Look l) {
		Buildings.Sketch s = new Buildings.Sketch("timber_yard", 7, 5, 5);
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 5; z++) {
				s.set(x, 0, z, Blocks.PODZOL.defaultBlockState());
			}
		}
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 2; z++) {
				for (int y = 1; y <= 3 - (x == 1 || x == 5 ? 1 : 0); y++) {
					s.set(x, y, z, Buildings.Sketch.lying(l.wall(), Direction.Axis.X));
				}
			}
		}
		s.set(1, 1, 4, Blocks.STONECUTTER.defaultBlockState());
		s.set(3, 1, 4, Blocks.OAK_LOG.defaultBlockState());
		s.set(3, 2, 4, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
		s.set(5, 1, 4, Blocks.BARREL.defaultBlockState());
		return s.done();
	}

	// ---- one-offs ---------------------------------------------------------------------------------------------------------

	/** A clock tower (7 x 7): a brick tower in the tinkers' way, a great clock face of white concrete and black hands on its front, gears either side, a hipped slate roof. */
	static Blueprint clockTower() {
		Buildings.Sketch s = new Buildings.Sketch("clock_tower", 9, 9, 22);
		BlockState brick = Blocks.BRICKS.defaultBlockState(), iron = Blocks.IRON_BLOCK.defaultBlockState();
		for (int y = 0; y <= 15; y++) {
			for (int x = 1; x <= 7; x++) {
				for (int z = 1; z <= 7; z++) {
					boolean edge = x == 1 || x == 7 || z == 1 || z == 7;
					if (y == 0 || edge) {
						s.set(x, y, z, y >= 12 && ((x + z) & 1) == 0 && edge ? Blocks.IRON_BARS.defaultBlockState() : brick);
					}
				}
			}
		}
		s.door(4, 1, 1, Direction.NORTH, Blocks.SPRUCE_DOOR);
		// the face: a disc of white, twelve marks, two hands
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -3; dy <= 3; dy++) {
				double r = Math.sqrt(dx * dx + dy * dy);
				if (r <= 3.2) {
					s.set(4 + dx, 10 + dy, 0, r >= 2.6 && (dx == 0 || dy == 0) ? Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState() : Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
				}
			}
		}
		for (int i = 0; i <= 2; i++) {
			s.set(4, 10 + i, 0, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState());
		}
		s.set(5, 10, 0, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState());
		s.set(6, 10, 0, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState());
		s.set(4, 10, 0, iron);
		for (int y = 1; y <= 14; y++) {
			s.set(4, y, 6, Buildings.Sketch.facing(Blocks.LADDER, Direction.NORTH));
		}
		s.hip(1, 7, 1, 7, 16, Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(), Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
		s.set(4, 20, 4, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
		// gears either side (a ring of iron with spokes of chain)
		for (int[] g : new int[][] {{0, 4}, {8, 4}}) {
			for (int a = 0; a < 8; a++) {
				double ang = a * Math.PI / 4.0;
				s.set(g[0], 7 + (int)Math.round(Math.sin(ang) * 2), 2 + (int)Math.round(Math.cos(ang) * 2), a % 2 == 0 ? iron : Blocks.POLISHED_ANDESITE.defaultBlockState());
			}
		}
		return s.done();
	}

	/** A gear on a stand (5 x 3 x 6): a ring of iron and andesite teeth round a hub, set on a post, as a sign of the guild. */
	static Blueprint gear() {
		Buildings.Sketch s = new Buildings.Sketch("gear", 7, 3, 9);
		for (int y = 0; y <= 2; y++) {
			s.set(3, y, 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
		}
		for (int a = 0; a < 12; a++) {
			double ang = a * Math.PI / 6.0;
			s.set(3 + (int)Math.round(Math.cos(ang) * 3), 5 + (int)Math.round(Math.sin(ang) * 3), 1, a % 2 == 0 ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState());
			s.set(3 + (int)Math.round(Math.cos(ang) * 2), 5 + (int)Math.round(Math.sin(ang) * 2), 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
		}
		s.set(3, 5, 1, Blocks.COPPER_BLOCK.weathering().unaffected().defaultBlockState());
		return s.done();
	}

	/**
	 * The airship mooring (15 x 15 x 22): a stone mast tower at the back, a gangway at the height of its top deck out to an
	 * airship lying alongside (her hull of planks railed round, a bag of wool over her), on a cradle of timber, her bow to the
	 * front. A static ship: nothing sails her.
	 */
	static Blueprint mooring(String bag) {
		Buildings.Sketch s = new Buildings.Sketch("mooring", 15, 15, 22);
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState(), planks = Blocks.SPRUCE_PLANKS.defaultBlockState(), rail = Blocks.SPRUCE_FENCE.defaultBlockState();
		for (int y = 0; y <= 11; y++) {
			for (int x = 0; x < 5; x++) {
				for (int z = 9; z < 14; z++) {
					boolean edge = x == 0 || x == 4 || z == 9 || z == 13;
					if (y == 0 || edge) {
						s.set(x, y, z, stone);
					}
				}
			}
		}
		s.door(2, 1, 9, Direction.NORTH, Blocks.SPRUCE_DOOR);
		for (int y = 1; y <= 10; y++) {
			s.set(2, y, 12, Buildings.Sketch.facing(Blocks.LADDER, Direction.NORTH));
		}
		for (int x = 0; x < 5; x++) {
			for (int z = 9; z < 14; z++) {
				s.set(x, 11, z, planks);
				if (x == 0 || x == 4 || z == 13 || z == 9) {
					s.set(x, 12, z, rail);
				}
			}
		}
		s.set(2, 12, 9, air());
		s.set(4, 12, 11, air());
		// the mast: a pole with a lantern, the mooring arm and its chain
		for (int y = 12; y <= 18; y++) {
			s.set(2, y, 11, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
		}
		s.set(2, 19, 11, Blocks.LANTERN.defaultBlockState());
		// the gangway from the tower's deck (x = 5..6) to the ship's rail
		for (int x = 5; x <= 6; x++) {
			s.set(x, 11, 11, planks);
			s.set(x, 12, 10, rail);
			s.set(x, 12, 12, rail);
		}
		// the ship: x = 7..11, z = 1..11, her deck at y = 11, hull below, bag over
		for (int z = 1; z <= 11; z++) {
			for (int x = 7; x <= 11; x++) {
				boolean bow = z == 1 && (x == 7 || x == 11);
				if (bow) {
					continue;
				}
				s.set(x, 11, z, planks);
				if (z > 1 && z < 11 && x > 7 && x < 11) {
					s.set(x, 10, z, planks);
				}
				if (z == 1 || z == 11 || x == 7 || x == 11) {
					s.set(x, 12, z, (x == 7 && z == 6) ? air() : rail);
				}
			}
		}
		for (int y = 14; y <= 16; y++) {
			int in = y == 16 ? 1 : 0;
			for (int x = 7 + in; x <= 11 - in; x++) {
				for (int z = 1 + in; z <= 11 - in; z++) {
					s.set(x, y, z, named(bag + "_wool", Blocks.HAY_BLOCK));
				}
			}
		}
		for (int[] p : new int[][] {{7, 2}, {11, 2}, {7, 10}, {11, 10}}) {
			s.set(p[0], 13, p[1], Blocks.IRON_CHAIN.defaultBlockState());
		}
		s.set(9, 12, 9, Blocks.BARREL.defaultBlockState());
		s.set(9, 12, 3, Blocks.LANTERN.defaultBlockState());
		// the cradle: timber legs under the hull
		for (int[] p : new int[][] {{8, 3}, {10, 3}, {8, 9}, {10, 9}}) {
			for (int y = 0; y <= 9; y++) {
				s.set(p[0], y, p[1], Blocks.SPRUCE_LOG.defaultBlockState());
			}
		}
		return s.done();
	}

	/** A temple (11 x 17): a nave under a high gable, an altar and a bell at its far end, pews, a spire over the door. */
	static Blueprint temple(Look l) {
		Buildings.Sketch s = new Buildings.Sketch("temple", 11, 17, 18);
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 17; z++) {
				s.set(x, 0, z, l.floor());
				boolean edge = x == 0 || x == 10 || z == 0 || z == 16;
				for (int y = 1; y <= 6; y++) {
					boolean pillar = (x == 0 || x == 10) && z % 4 == 0;
					if (edge) {
						s.set(x, y, z, pillar || (x == 0 || x == 10) && z == 16 ? l.post() : (y == 3 || y == 4) && (x == 0 || x == 10) && z % 4 == 2 ? l.window() : l.wall());
					} else {
						s.set(x, y, z, air());
					}
				}
			}
		}
		s.door(5, 1, 0, Direction.NORTH, l.door());
		s.gable(0, 10, 0, 16, 7, l.roofStairs(), l.roofSlab(), l.wall());
		for (int z = 3; z <= 11; z += 2) {
			s.set(2, 1, z, Buildings.Sketch.stair(Blocks.OAK_STAIRS.defaultBlockState(), Direction.EAST));
			s.set(3, 1, z, Buildings.Sketch.stair(Blocks.OAK_STAIRS.defaultBlockState(), Direction.WEST));
			s.set(7, 1, z, Buildings.Sketch.stair(Blocks.OAK_STAIRS.defaultBlockState(), Direction.EAST));
			s.set(8, 1, z, Buildings.Sketch.stair(Blocks.OAK_STAIRS.defaultBlockState(), Direction.WEST));
		}
		for (int x = 3; x <= 7; x++) {
			s.set(x, 1, 14, l.floor());
		}
		s.set(5, 2, 14, Blocks.LECTERN.defaultBlockState());
		s.set(5, 5, 15, Blocks.BELL.defaultBlockState());
		s.set(3, 1, 15, Blocks.CANDLE.defaultBlockState());
		s.set(7, 1, 15, Blocks.CANDLE.defaultBlockState());
		for (int y = 8; y <= 12; y++) {
			s.set(5, y, 1, l.post());
		}
		s.set(5, 13, 1, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
		return s.done();
	}

	/**
	 * A keep (21 x 15): a great hall of stone under a flat crenellated roof with four corner towers, a door in its front,
	 * a clear floor two high where the throne is set ({@link Seats#room}), a stair up to the walk.
	 */
	static Blueprint keep(Look l) {
		Buildings.Sketch s = new Buildings.Sketch("keep", 21, 15, 20);
		for (int x = 0; x < 21; x++) {
			for (int z = 0; z < 15; z++) {
				s.set(x, 0, z, l.floor());
				boolean edge = x == 0 || x == 20 || z == 0 || z == 14;
				for (int y = 1; y <= 7; y++) {
					if (edge) {
						s.set(x, y, z, (y == 3 || y == 4) && z != 0 && (x == 0 || x == 20) && z % 4 == 2 ? l.window() : l.wall());
					} else if (y <= 5) {
						s.set(x, y, z, air());
					}
				}
				if (!edge) {
					s.set(x, 6, z, l.floor());
				}
			}
		}
		s.door(10, 1, 0, Direction.NORTH, l.door());
		s.flat(0, 20, 0, 14, 8, l.wall(), l.roofSlab());
		for (int[] c : new int[][] {{0, 0}, {18, 0}, {0, 12}, {18, 12}}) {
			for (int x = c[0]; x <= c[0] + 2; x++) {
				for (int z = c[1]; z <= c[1] + 2; z++) {
					for (int y = 1; y <= 11; y++) {
						if (x == c[0] || x == c[0] + 2 || z == c[1] || z == c[1] + 2) {
							s.set(x, y, z, l.wall());
						}
					}
					s.set(x, 12, z, (x + z) % 2 == 0 ? l.wall() : l.roofSlab());
				}
			}
		}
		for (int y = 1; y <= 7; y++) {
			s.set(10, y, 13, Buildings.Sketch.facing(Blocks.LADDER, Direction.NORTH));
		}
		s.set(10, 6, 13, air());
		for (int x : new int[] {4, 16}) {
			s.set(x, 4, 1, Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		}
		return s.done();
	}
}
