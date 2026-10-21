package com.jaymoskingdoms.nether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (Kingdoms 0.7.142) What the Nether's war forts are built of ({@link NetherForts}), all our own. Each is built at once on
 * flattened ground, its gate facing its rival:
 * <ul>
 * <li>A Hornkin war fort: a square blackstone-brick wall with gilded stones in it and crenels on top, a tower at each
 * corner with a soul lantern, soul fires in the yard, and the warlord's keep at the back (a throne, and his hoard
 * chest beside a block of gold).</li>
 * <li>A piglin war camp: a nether-brick palisade with basalt posts and lanterns, red and black tents, a war fire, and
 * the warboss's gold-block seat with the camp's war chest.</li>
 * </ul>
 */
final class FortBuildings {
	/** How far each reaches from its centre (blocks); how far below the floor the ground is filled; how far above it cleared. */
	static final int FORT_RADIUS = 8, CAMP_RADIUS = 7, FILL_DEPTH = 6, CLEAR_HEIGHT = 9;
	/** A Hornkin fort: wall height, tower height, how often a wall stone is gilded. */
	static final int WALL_HEIGHT = 4, TOWER_HEIGHT = 6;
	static final float GILDED_CHANCE = 0.06F;
	/** The palisade's height. */
	static final int PALISADE_HEIGHT = 3;

	/** Where a fort's people belong: its keep (or the warboss's seat), its hoard chest, and a spot just inside its gate. */
	record Layout(BlockPos keep, BlockPos hoard, BlockPos gate) {
	}

	private FortBuildings() {
	}

	/** Builds a Hornkin war fort round {@code center} (its floor level), the gate on its {@code gate} side. */
	static Layout hornkinFort(ServerLevel level, BlockPos center, Direction gate, RandomSource random) {
		Frame f = new Frame(level, center, gate);
		int r = FORT_RADIUS;
		f.groundwork(r, Blocks.BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				boolean edge = Math.abs(a) == r || Math.abs(b) == r;
				boolean tower = Math.abs(a) >= r - 1 && Math.abs(b) >= r - 1;
				if (tower) {
					for (int y = 1; y <= TOWER_HEIGHT; y++) {
						f.set(a, y, b, Blocks.BLACKSTONE.defaultBlockState());
					}
					if (Math.abs(a) == r && Math.abs(b) == r) {
						f.set(a, TOWER_HEIGHT + 1, b, Blocks.SOUL_LANTERN.defaultBlockState());
					}
				} else if (edge) {
					boolean gateway = b == r && Math.abs(a) <= 1;
					for (int y = 1; y <= WALL_HEIGHT; y++) {
						if (gateway && y <= 3) {
							continue;
						}
						Block stone = random.nextFloat() < GILDED_CHANCE ? Blocks.GILDED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS;
						f.set(a, y, b, stone.defaultBlockState());
					}
					if ((a + b) % 2 == 0) {
						f.set(a, WALL_HEIGHT + 1, b, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
					}
				}
			}
		}
		// the keep: 5x5 at the back, its door facing the gate
		int kb = -r + 4;
		for (int a = -2; a <= 2; a++) {
			for (int b = kb - 2; b <= kb + 2; b++) {
				boolean wall = Math.abs(a) == 2 || Math.abs(b - kb) == 2;
				for (int y = 1; y <= 4; y++) {
					if (wall && !(a == 0 && b == kb + 2 && y <= 2)) {
						f.set(a, y, b, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
					}
				}
				f.set(a, 5, b, Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState());
			}
		}
		// the throne at the back of the keep, facing the door; the hoard beside it
		f.set(0, 1, kb - 1, Blocks.BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, gate));
		f.set(-1, 1, kb - 1, Blocks.GOLD_BLOCK.defaultBlockState());
		BlockPos hoard = f.pos(1, 1, kb - 1);
		level.setBlock(hoard, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, gate), Block.UPDATE_ALL);
		fill(level, hoard, random, true);
		f.set(-1, 4, kb, Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		// soul fires in the yard
		f.set(-4, 1, 3, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.FACING, gate));
		f.set(4, 1, 3, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.FACING, gate));
		f.set(-5, 1, -1, Blocks.GILDED_BLACKSTONE.defaultBlockState());
		f.set(5, 1, -1, Blocks.RAW_GOLD_BLOCK.defaultBlockState());
		return new Layout(f.pos(0, 1, kb), hoard, f.pos(0, 1, r - 2));
	}

	/** Builds a piglin war camp round {@code center} (its floor level), the gate on its {@code gate} side. */
	static Layout piglinCamp(ServerLevel level, BlockPos center, Direction gate, RandomSource random) {
		Frame f = new Frame(level, center, gate);
		int r = CAMP_RADIUS;
		f.groundwork(r, Blocks.NETHERRACK.defaultBlockState(), null);
		for (int a = -r; a <= r; a++) {
			for (int b = -r; b <= r; b++) {
				if (Math.abs(a) != r && Math.abs(b) != r) {
					continue;
				}
				if (b == r && Math.abs(a) <= 1) {
					continue;
				}
				boolean post = (a + r) % 3 == 0 && (b + r) % 3 == 0;
				for (int y = 1; y <= PALISADE_HEIGHT; y++) {
					f.set(a, y, b, post ? Blocks.POLISHED_BASALT.defaultBlockState() : Blocks.NETHER_BRICK_FENCE.defaultBlockState());
				}
				if (post) {
					f.set(a, PALISADE_HEIGHT + 1, b, Blocks.LANTERN.defaultBlockState());
				}
			}
		}
		tent(f, -4, -3, Blocks.WOOL.pick(DyeColor.RED));
		tent(f, 4, -3, Blocks.WOOL.pick(DyeColor.BLACK));
		tent(f, -4, 2, Blocks.WOOL.pick(DyeColor.BLACK));
		// the war fire, and behind it the warboss's seat of gold with the war chest
		f.set(0, 0, 1, Blocks.NETHER_BRICKS.defaultBlockState());
		f.set(0, 1, 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.FACING, gate));
		f.set(0, 1, -4, Blocks.CRIMSON_STAIRS.defaultBlockState().setValue(StairBlock.FACING, gate));
		f.set(-1, 1, -4, Blocks.GOLD_BLOCK.defaultBlockState());
		f.set(1, 1, -5, Blocks.GOLD_BLOCK.defaultBlockState());
		BlockPos chest = f.pos(1, 1, -4);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, gate), Block.UPDATE_ALL);
		fill(level, chest, random, false);
		return new Layout(f.pos(0, 1, -3), chest, f.pos(0, 1, r - 2));
	}

	/** A 3x3 tent, open at the front: wool walls a block high and a wool roof. */
	private static void tent(Frame f, int a, int b, Block wool) {
		for (int da = -1; da <= 1; da++) {
			for (int db = -1; db <= 1; db++) {
				if (db == 1 && da == 0) {
					continue;
				}
				if (Math.abs(da) == 1 || db == -1) {
					f.set(a + da, 1, b + db, wool.defaultBlockState());
				}
				f.set(a + da, 2, b + db, wool.defaultBlockState());
			}
		}
	}

	/** A hoard: gold, and the camp's plunder. */
	private static void fill(ServerLevel level, BlockPos pos, RandomSource random, boolean hornkin) {
		if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
			return;
		}
		int slot = 0;
		chest.setItem(slot++, new ItemStack(Items.GOLD_INGOT, 6 + random.nextInt(8)));
		chest.setItem(slot++, new ItemStack(Items.GOLD_NUGGET, 12 + random.nextInt(20)));
		chest.setItem(slot++, new ItemStack(Items.GILDED_BLACKSTONE, 1 + random.nextInt(3)));
		if (random.nextFloat() < 0.35F) {
			chest.setItem(slot++, new ItemStack(Items.GOLDEN_APPLE));
		}
		if (hornkin) {
			chest.setItem(slot++, new ItemStack(Items.MAGMA_CREAM, 2 + random.nextInt(4)));
			chest.setItem(slot++, new ItemStack(Items.LEATHER, 3 + random.nextInt(5)));
			if (random.nextFloat() < 0.15F) {
				chest.setItem(slot++, new ItemStack(Items.NETHERITE_SCRAP));
			}
		} else {
			chest.setItem(slot++, new ItemStack(Items.CROSSBOW));
			chest.setItem(slot++, new ItemStack(Items.ARROW, 8 + random.nextInt(12)));
			chest.setItem(slot++, new ItemStack(Items.COOKED_PORKCHOP, 3 + random.nextInt(5)));
			if (random.nextFloat() < 0.2F) {
				chest.setItem(slot++, new ItemStack(Items.GOLDEN_CARROT, 2));
			}
		}
		chest.setChanged();
	}

	/** Places blocks by a fort's own frame: {@code a} across (right of the gate), {@code b} towards the gate, {@code y} up from the floor. */
	private static final class Frame {
		final ServerLevel level;
		final BlockPos center;
		final Direction forward, right;

		Frame(ServerLevel level, BlockPos center, Direction gate) {
			this.level = level;
			this.center = center;
			this.forward = gate;
			this.right = gate.getClockWise();
		}

		BlockPos pos(int a, int y, int b) {
			return this.center.relative(this.right, a).relative(this.forward, b).above(y);
		}

		void set(int a, int y, int b, BlockState state) {
			this.level.setBlock(this.pos(a, y, b), state, Block.UPDATE_ALL);
		}

		/** Fills the ground under the whole square, levels its floor ({@code floor}, or the ground as it was) and clears the air above. */
		void groundwork(int r, BlockState fill, @org.jspecify.annotations.Nullable BlockState floor) {
			for (int a = -r; a <= r; a++) {
				for (int b = -r; b <= r; b++) {
					for (int y = -FILL_DEPTH; y < 0; y++) {
						BlockState was = this.level.getBlockState(this.pos(a, y, b));
						if (was.isAir() || !was.getFluidState().isEmpty() || was.canBeReplaced()) {
							this.set(a, y, b, fill);
						}
					}
					BlockState ground = this.level.getBlockState(this.pos(a, 0, b));
					if (floor != null) {
						this.set(a, 0, b, floor);
					} else if (ground.isAir() || !ground.getFluidState().isEmpty() || ground.canBeReplaced()) {
						this.set(a, 0, b, fill);
					}
					for (int y = 1; y <= CLEAR_HEIGHT; y++) {
						if (!this.level.getBlockState(this.pos(a, y, b)).isAir()) {
							this.set(a, y, b, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
	}
}
