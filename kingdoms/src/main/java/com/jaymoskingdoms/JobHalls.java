package com.jaymoskingdoms;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

	/**
	 * A butcher's shop (7 x 7): an open counter across the front with a gate through it, a smoker, a chopping block and
	 * a barrel of cuts at the back, chains hanging from the roof beam, stone or plank walls, a gable along the counter.
	 */
	static Blueprint butcher(Buildings.Kit k) {
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
		s.set(2, 3, 3, Blocks.IRON_CHAIN.defaultBlockState());
		s.set(4, 3, 3, Blocks.IRON_CHAIN.defaultBlockState());
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

	private static @Nullable BlockState named(String name) {
		Block b = Townscape.named(name);
		return b == null ? null : b.defaultBlockState();
	}
}
