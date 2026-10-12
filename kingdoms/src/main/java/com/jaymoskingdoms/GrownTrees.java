package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.trunkplacers.BendingTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.DarkOakTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.FancyTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.GiantTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.MegaJungleTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.UpwardsBranchingTrunkPlacer;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.10) Trees grown the way Dynamic Trees grows them (our own code, in vanilla logs and leaves): in place of the
 * game's blocky shapes, every oak, birch, spruce, jungle, acacia, dark oak and cherry the world generates (or a sapling
 * grows into, {@link Trees}) is drawn branch by branch: a trunk that leans a little, branches reaching out and up from
 * it, rounded clumps of leaves at their tips, and old trees spreading their roots at the foot. Each kind has its own
 * habit: round-crowned oaks, slender birches, spruces in tiers that shorten toward the top, tall jungle trees with a
 * crown only at the top, forked acacias with flat crowns, broad dark oaks, cherries arching outward. Bushes, azaleas,
 * mangroves and modded trees are left to the game. Leaves are only set within reach of the wood (so none decay), and
 * nothing is set where something solid already stands.
 */
public final class GrownTrees {
	/** Leaves farther than this from the wood (counted through the leaves) aren't set: they'd only decay. */
	static final int LEAF_REACH = 6;
	/** How far from its foot a tree may spread (blocks), to stay inside the land being generated. */
	static final int MAX_SPREAD = 9;

	enum Kind { OAK, BIRCH, SPRUCE, JUNGLE, ACACIA, DARK_OAK, CHERRY }

	private GrownTrees() {
	}

	/** What kind a trunk is, or null for one we leave to the game. */
	private static @Nullable Kind kind(BlockState log) {
		String name = BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath();
		return switch (name) {
			case "oak_log" -> Kind.OAK;
			case "birch_log" -> Kind.BIRCH;
			case "spruce_log" -> Kind.SPRUCE;
			case "jungle_log" -> Kind.JUNGLE;
			case "acacia_log" -> Kind.ACACIA;
			case "dark_oak_log", "pale_oak_log" -> Kind.DARK_OAK;
			case "cherry_log" -> Kind.CHERRY;
			default -> null;
		};
	}

	/**
	 * In place of {@code tree} at {@code origin} ({@code TreeFeatureMixin}): grows one of ours and returns true, or
	 * returns false to leave it to the game (a bush, an azalea, a mangrove, a modded tree, or no room).
	 */
	public static boolean place(TreeFeature tree, WorldGenLevel level, RandomSource random, BlockPos origin) {
		TrunkPlacer trunk = tree.trunkPlacer();
		if (trunk instanceof BendingTrunkPlacer || trunk instanceof UpwardsBranchingTrunkPlacer || trunk.getBaseHeight() <= 1) {
			return false;
		}
		BlockState log = tree.trunkProvider().value().getState(level, random, origin);
		Kind kind = kind(log);
		if (kind == null || !log.hasProperty(BlockStateProperties.AXIS)) {
			return false;
		}
		boolean big = trunk instanceof GiantTrunkPlacer || trunk instanceof MegaJungleTrunkPlacer || trunk instanceof DarkOakTrunkPlacer
			|| trunk instanceof FancyTrunkPlacer;
		Shape shape = new Shape(random);
		switch (kind) {
			case OAK -> shape.oak(big);
			case BIRCH -> shape.birch();
			case SPRUCE -> shape.spruce(big);
			case JUNGLE -> shape.jungle(big);
			case ACACIA -> shape.acacia();
			case DARK_OAK -> shape.darkOak();
			case CHERRY -> shape.cherry();
		}
		// room for its trunk, and ground under it
		for (BlockPos foot : shape.feet) {
			BlockState under = level.getBlockState(origin.offset(foot).below());
			if (!under.is(BlockTags.DIRT) && !under.is(BlockTags.SUBSTRATE_OVERWORLD)) {
				return false;
			}
		}
		for (Map.Entry<BlockPos, Direction.Axis> e : shape.logs.entrySet()) {
			if (e.getValue() == Direction.Axis.Y && !free(level.getBlockState(origin.offset(e.getKey())))) {
				return false;
			}
		}
		for (BlockPos foot : shape.feet) {
			BlockPos below = origin.offset(foot).below();
			BlockState dirt = tree.belowTrunkProvider().value().getState(level, random, below);
			if (!dirt.isAir()) {
				level.setBlock(below, dirt, 19);
			}
		}
		for (Map.Entry<BlockPos, Direction.Axis> e : shape.logs.entrySet()) {
			BlockPos at = origin.offset(e.getKey());
			if (free(level.getBlockState(at))) {
				level.setBlock(at, log.setValue(BlockStateProperties.AXIS, e.getValue()), 19);
			}
		}
		leaves(tree, level, random, origin, shape);
		return true;
	}

	/** Air, plants, snow, leaves or a sapling: a tree may grow through it. */
	private static boolean free(BlockState state) {
		return state.isAir() || state.canBeReplaced() || state.getBlock() instanceof LeavesBlock || state.getBlock() instanceof SaplingBlock
			|| state.is(BlockTags.REPLACEABLE_BY_TREES);
	}

	/** Its leaves: every clump, kept to what's within {@link #LEAF_REACH} of its wood, each knowing how far that is. */
	private static void leaves(TreeFeature tree, WorldGenLevel level, RandomSource random, BlockPos origin, Shape shape) {
		Map<BlockPos, Integer> reach = new HashMap<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		for (BlockPos l : shape.logs.keySet()) {
			reach.put(l, 0);
			open.add(l);
		}
		while (!open.isEmpty()) {
			BlockPos at = open.poll();
			int d = reach.get(at);
			if (d >= LEAF_REACH) {
				continue;
			}
			for (Direction dir : Direction.values()) {
				BlockPos n = at.relative(dir);
				if (shape.leaves.contains(n) && !reach.containsKey(n)) {
					reach.put(n, d + 1);
					open.add(n);
				}
			}
		}
		for (BlockPos leaf : shape.leaves) {
			Integer d = reach.get(leaf);
			if (d == null || d == 0) {
				continue;
			}
			BlockPos at = origin.offset(leaf);
			BlockState there = level.getBlockState(at);
			if (!(there.isAir() || there.canBeReplaced() || there.is(BlockTags.REPLACEABLE_BY_TREES))) {
				continue;
			}
			BlockState state = tree.foliageProvider().value().getState(level, random, at);
			if (state.hasProperty(LeavesBlock.DISTANCE)) {
				state = state.setValue(LeavesBlock.DISTANCE, Math.min(7, d)).setValue(LeavesBlock.PERSISTENT, false);
			}
			level.setBlock(at, state, 19);
		}
	}

	/** A tree being drawn, relative to its foot: its logs (and how each lies), its leaves, the columns it stands on. */
	static final class Shape {
		final Map<BlockPos, Direction.Axis> logs = new LinkedHashMap<>();
		final java.util.Set<BlockPos> leaves = new java.util.LinkedHashSet<>();
		final List<BlockPos> feet = new ArrayList<>();
		private final RandomSource r;

		Shape(RandomSource r) {
			this.r = r;
		}

		private int between(int lo, int hi) {
			return lo + this.r.nextInt(hi - lo + 1);
		}

		private Direction anyWay() {
			return Direction.Plane.HORIZONTAL.getRandomDirection(this.r);
		}

		private boolean inReach(BlockPos p) {
			return Math.abs(p.getX()) <= MAX_SPREAD && Math.abs(p.getZ()) <= MAX_SPREAD;
		}

		private void log(BlockPos p, Direction.Axis axis) {
			if (!this.inReach(p)) {
				return;
			}
			// a trunk keeps its upright logs where a branch meets it
			this.logs.merge(p, axis, (was, now) -> was == Direction.Axis.Y ? was : now);
		}

		/**
		 * A trunk {@code height} high ({@code wide}: two by two), leaning now and then ({@code lean}: the chance a block,
		 * once it's a third of the way up, steps over by one toward {@code leanWay}, at most twice). Returns its top.
		 */
		BlockPos trunk(int height, boolean wide, float lean, Direction leanWay) {
			BlockPos at = BlockPos.ZERO;
			int stepped = 0;
			this.feet.add(BlockPos.ZERO);
			if (wide) {
				this.feet.add(new BlockPos(1, 0, 0));
				this.feet.add(new BlockPos(0, 0, 1));
				this.feet.add(new BlockPos(1, 0, 1));
			}
			for (int y = 0; y < height; y++) {
				if (y > height / 3 && stepped < 2 && this.r.nextFloat() < lean) {
					BlockPos over = at.relative(leanWay);
					this.log(over, leanWay.getAxis());
					if (wide) {
						this.log(over.offset(1, 0, 1), leanWay.getAxis());
					}
					at = over;
					stepped++;
				}
				BlockPos here = at.atY(y);
				this.log(here, Direction.Axis.Y);
				if (wide && y < height - 2) {
					this.log(here.offset(1, 0, 0), Direction.Axis.Y);
					this.log(here.offset(0, 0, 1), Direction.Axis.Y);
					this.log(here.offset(1, 0, 1), Direction.Axis.Y);
				}
				at = here;
			}
			return at;
		}

		/** A branch out from {@code from}, {@code length} long, rising a block every {@code rise} (0: level). Returns its tip. */
		BlockPos branch(BlockPos from, Direction way, int length, int rise) {
			BlockPos at = from;
			for (int i = 1; i <= length; i++) {
				at = at.relative(way);
				this.log(at, way.getAxis());
				if (rise > 0 && i % rise == 0 && i < length) {
					at = at.above();
					this.log(at, Direction.Axis.Y);
				}
			}
			return at;
		}

		/** A clump of leaves round {@code c}: {@code across} wide and {@code up} tall (radii), its edge ragged. */
		void clump(BlockPos c, float across, float up) {
			int rx = (int)Math.ceil(across), ry = (int)Math.ceil(up);
			for (int dx = -rx; dx <= rx; dx++) {
				for (int dy = -ry; dy <= ry; dy++) {
					for (int dz = -rx; dz <= rx; dz++) {
						float d = dx * dx / (across * across) + dy * dy / (up * up) + dz * dz / (across * across);
						if (d <= 1.0F - this.r.nextFloat() * 0.3F + 0.15F) {
							BlockPos p = c.offset(dx, dy, dz);
							if (this.inReach(p) && !this.logs.containsKey(p)) {
								this.leaves.add(p);
							}
						}
					}
				}
			}
		}

		/** Roots spreading at the foot of an old tree (round a {@code wide} trunk too). */
		void roots(boolean wide) {
			int span = wide ? 2 : 1;
			for (Direction way : Direction.Plane.HORIZONTAL) {
				if (this.r.nextFloat() < 0.65F) {
					BlockPos start = way.getStepX() > 0 || way.getStepZ() > 0 ? new BlockPos(wide && way.getStepX() > 0 ? 1 : 0, 0,
						wide && way.getStepZ() > 0 ? 1 : 0) : BlockPos.ZERO;
					int length = this.between(1, span);
					for (int i = 1; i <= length; i++) {
						this.log(start.relative(way, i), way.getAxis());
					}
				}
			}
		}

		// ---- the kinds ----

		void oak(boolean big) {
			int height = big ? this.between(9, 14) : this.between(5, 8);
			BlockPos top = this.trunk(height, false, big ? 0.15F : 0.25F, this.anyWay());
			int branches = big ? this.between(4, 6) : this.between(2, 4);
			for (int i = 0; i < branches; i++) {
				int y = this.between(height / 2, height - 2);
				BlockPos from = this.column(top, y);
				BlockPos tip = this.branch(from, this.anyWay(), big ? this.between(3, 5) : this.between(2, 3), 2);
				if (big && this.r.nextBoolean()) {
					Direction fork = this.anyWay();
					BlockPos second = this.branch(tip, fork, this.between(1, 2), 1);
					this.clump(second, 2.5F, 1.8F);
				}
				this.clump(tip, big ? 2.8F : 2.2F, big ? 2.0F : 1.7F);
			}
			this.clump(top, big ? 3.0F : 2.5F, big ? 2.2F : 2.0F);
			if (big) {
				this.roots(false);
			}
		}

		void birch() {
			int height = this.between(6, 10);
			BlockPos top = this.trunk(height, false, 0.12F, this.anyWay());
			for (int i = 0, n = this.between(1, 2); i < n; i++) {
				BlockPos tip = this.branch(this.column(top, this.between(height - 4, height - 2)), this.anyWay(), this.between(1, 2), 1);
				this.clump(tip, 1.8F, 2.0F);
			}
			this.clump(top, 2.0F, 2.6F);
		}

		void spruce(boolean big) {
			int height = big ? this.between(14, 24) : this.between(7, 12);
			BlockPos top = this.trunk(height, big, 0.0F, Direction.NORTH);
			int start = big ? this.between(4, 6) : 2;
			for (int y = start; y < height - 1; y += 2) {
				int length = Math.max(1, Math.round((height - y) * (big ? 0.35F : 0.4F)));
				for (Direction way : Direction.Plane.HORIZONTAL) {
					if (this.r.nextFloat() < 0.8F) {
						BlockPos from = big ? this.wideSide(top.atY(y), way) : top.atY(y);
						BlockPos at = from;
						for (int i = 1; i <= length; i++) {
							at = at.relative(way);
							this.log(at, way.getAxis());
							this.clump(at, 1.3F, 0.9F);
						}
					}
				}
			}
			this.clump(top, 1.4F, 1.2F);
			this.leaves.add(top.above());
			this.leaves.add(top.above(2));
			if (big) {
				this.roots(true);
			}
		}

		void jungle(boolean big) {
			int height = big ? this.between(18, 26) : this.between(8, 12);
			BlockPos top = this.trunk(height, big, 0.1F, this.anyWay());
			int branches = big ? this.between(3, 5) : this.between(2, 3);
			for (int i = 0; i < branches; i++) {
				int y = this.between(height - (big ? 7 : 4), height - 2);
				BlockPos from = big ? this.wideSide(this.column(top, y), this.anyWay()) : this.column(top, y);
				BlockPos tip = this.branch(from, this.anyWay(), big ? this.between(3, 5) : this.between(2, 3), 1);
				this.clump(tip, big ? 3.0F : 2.4F, big ? 1.8F : 1.5F);
			}
			this.clump(top, big ? 3.2F : 2.6F, big ? 2.0F : 1.8F);
			if (big) {
				this.roots(true);
			}
		}

		void acacia() {
			Direction lean = this.anyWay();
			int height = this.between(3, 5);
			BlockPos top = this.trunk(height, false, 0.0F, lean);
			// it forks: one limb on the way it leans, another off to the side, each climbing outward to a flat crown
			for (Direction way : new Direction[] {lean, this.r.nextBoolean() ? lean.getClockWise() : lean.getCounterClockWise()}) {
				// (diagonally, upright logs touching at their edges, as the game's own acacias grow)
				BlockPos at = top;
				for (int i = 0, n = this.between(2, 4); i < n; i++) {
					at = at.relative(way).above();
					this.log(at, Direction.Axis.Y);
				}
				this.clump(at.above(), 3.0F, 0.8F);
			}
		}

		void darkOak() {
			// a stout trunk bare to well up, then thick limbs spreading under a broad, low-domed crown
			int height = this.between(7, 10);
			BlockPos top = this.trunk(height, true, 0.0F, Direction.NORTH);
			for (int i = 0, n = this.between(4, 6); i < n; i++) {
				Direction way = this.anyWay();
				BlockPos from = this.wideSide(this.column(top, this.between(height - 4, height - 2)), way);
				BlockPos tip = this.branch(from, way, this.between(2, 4), 2);
				this.clump(tip.above(), 3.0F, 1.4F);
			}
			this.clump(top.above(), 3.4F, 1.6F);
			this.roots(true);
		}

		void cherry() {
			int height = this.between(4, 6);
			BlockPos top = this.trunk(height, false, 0.2F, this.anyWay());
			Direction first = this.anyWay();
			Direction[] ways = this.r.nextBoolean() ? new Direction[] {first, first.getOpposite()}
				: new Direction[] {first, first.getClockWise(), first.getCounterClockWise()};
			for (Direction way : ways) {
				BlockPos tip = this.branch(top, way, this.between(3, 4), 2);
				this.clump(tip, 3.0F, 1.8F);
			}
			this.clump(top, 2.4F, 1.8F);
		}

		/** Where the trunk is at height {@code y} (it may have leaned on the way up to {@code top}). */
		private BlockPos column(BlockPos top, int y) {
			for (Map.Entry<BlockPos, Direction.Axis> e : this.logs.entrySet()) {
				if (e.getKey().getY() == y && e.getValue() == Direction.Axis.Y) {
					return e.getKey();
				}
			}
			return top.atY(y);
		}

		/** A two-by-two trunk's edge facing {@code way} at {@code at}'s height. */
		private BlockPos wideSide(BlockPos at, Direction way) {
			return at.offset(way.getStepX() > 0 ? 1 : 0, 0, way.getStepZ() > 0 ? 1 : 0);
		}
	}
}
