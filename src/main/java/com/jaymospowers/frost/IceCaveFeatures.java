package com.jaymospowers.frost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * What grows in the {@link IceCaves} (placed by the biome, {@code worldgen/biome/ice_caves.json}):
 * <ul>
 * <li>{@link Crevasse}: a winding crack carved down into a cave floor, narrowing at its ends and towards its bottom,
 * with a bed of {@code fill_block} (powder snow) down in it, roofed over at floor level with {@code surface_block} (brittle
 * ice), so it looks like floor until something heavy walks on it. The same settings as 26.4's crevasse feature.</li>
 * <li>{@link LargeIcicle}: a great spike of packed ice hanging from a ceiling, rising from the floor under it, and
 * sometimes the two grown into one column.</li>
 * <li>{@link Icicles}: a few icicles hanging from a patch of ceiling, and now and then a short one standing under them
 * where the drip lands.</li>
 * <li>{@link Floor}: a patch of floor with snow lying on it and ice crystals growing out of it.</li>
 * </ul>
 * Nothing reaches more than {@link #REACH} blocks across from where it starts, so it never writes outside the chunks being
 * made.
 */
public final class IceCaveFeatures {
	// ---- tuning ----
	/** The furthest any of these reaches sideways from its start (the chunks round the one being decorated). */
	public static final int REACH = 15;
	/** How far a decoration looks up for its ceiling or down for its floor. */
	public static final int SEEK = 12;
	/** Icicles: the patch radius, the share of its ceiling that grows one (in 10), and the share of those with a standing one under. */
	public static final int ICICLE_RADIUS = 3, ICICLE_SHARE = 3, STANDING_SHARE = 3;
	/** Crevasse: how far down from where it is tried it looks for its floor, and how far the floor may rise or fall from one step to the next, and across it. */
	public static final int START_SEEK = 16, STEP_RISE = 2, COLUMN_RISE = 1;
	/** Floor: the patch radius (2 to this), and the shares (in 100) of snow and of crystals. */
	public static final int FLOOR_RADIUS = 4, SNOW_SHARE = 40, CRYSTAL_SHARE = 10;

	private IceCaveFeatures() {
	}

	private static boolean inReach(BlockPos origin, int x, int z) {
		return Math.abs(x - origin.getX()) <= REACH && Math.abs(z - origin.getZ()) <= REACH;
	}

	/** The top of the floor under {@code from}: the first block going down that isn't air, with air over it; or null. */
	private static BlockPos floorUnder(WorldGenLevel level, BlockPos from, int range) {
		BlockPos.MutableBlockPos at = from.mutable();
		for (int i = 0; i <= range; i++) {
			if (!level.getBlockState(at).isAir()) {
				return level.getBlockState(at.above()).isAir() ? at.immutable() : null;
			}
			at.move(Direction.DOWN);
		}
		return null;
	}

	/** The underside of the ceiling over {@code from}; or null. */
	private static BlockPos ceilingOver(WorldGenLevel level, BlockPos from, int range) {
		BlockPos.MutableBlockPos at = from.mutable();
		for (int i = 0; i <= range; i++) {
			if (!level.getBlockState(at).isAir()) {
				return level.getBlockState(at.below()).isAir() ? at.immutable() : null;
			}
			at.move(Direction.UP);
		}
		return null;
	}

	/**
	 * The crevasse. From a cave floor it grows two arms in opposite directions, each step a little further, turned a
	 * little ({@code twistiness} times {@code turn_factor}) and a little narrower ({@code tapering}); each step finds the
	 * floor again below its cursor, so it follows the ground. Across, it is deepest at its middle line ({@code carve_depth})
	 * and shallow at its lips. It is only carved if every block round what it would hollow out is a block it accepts as
	 * its wall ({@code acceptable_barrier}, or one it could carve), so it never breaks into another cave or lets its fill
	 * spill, and only if it covers at least {@code min_footprint} columns.
	 */
	public record Crevasse(int maxWidth, int maxLength, int minFootprint, int cursorSurfaceSearchRange, int columnSurfaceSearchRange,
		float twistiness, float tapering, float turnFactor, IntProvider carveDepth, IntProvider fillDepth, BlockState surfaceBlock,
		BlockState fillBlock, BlockPredicate carvableBlocks, BlockPredicate acceptableBarrier) implements Feature {
		public static final MapCodec<Crevasse> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.intRange(2, 8).fieldOf("max_width").forGetter(Crevasse::maxWidth),
			Codec.intRange(4, 16).fieldOf("max_length").forGetter(Crevasse::maxLength),
			Codec.intRange(1, 32).fieldOf("min_footprint").forGetter(Crevasse::minFootprint),
			ExtraCodecs.POSITIVE_INT.fieldOf("cursor_surface_search_range").forGetter(Crevasse::cursorSurfaceSearchRange),
			ExtraCodecs.POSITIVE_INT.fieldOf("column_surface_search_range").forGetter(Crevasse::columnSurfaceSearchRange),
			Codec.floatRange(0.0F, 16.0F).fieldOf("twistiness").forGetter(Crevasse::twistiness),
			Codec.floatRange(0.0F, 1.0F).fieldOf("tapering").forGetter(Crevasse::tapering),
			Codec.floatRange(0.0F, 1.0F).fieldOf("turn_factor").forGetter(Crevasse::turnFactor),
			IntProviders.codec(1, 64).fieldOf("carve_depth").forGetter(Crevasse::carveDepth),
			IntProviders.codec(0, 64).fieldOf("fill_depth").forGetter(Crevasse::fillDepth),
			BlockState.CODEC.fieldOf("surface_block").forGetter(Crevasse::surfaceBlock),
			BlockState.CODEC.fieldOf("fill_block").forGetter(Crevasse::fillBlock),
			BlockPredicate.CODEC.fieldOf("carvable_blocks").forGetter(Crevasse::carvableBlocks),
			BlockPredicate.CODEC.fieldOf("acceptable_barrier").forGetter(Crevasse::acceptableBarrier)).apply(i, Crevasse::new));

		@Override
		public MapCodec<Crevasse> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.getBlockState(origin).isAir()) {
				return false;
			}
			BlockPos start = this.surfaceNear(level, origin, Math.max(this.cursorSurfaceSearchRange, START_SEEK));
			if (start == null) {
				return false;
			}
			int depth = this.carveDepth.sample(random), fill = this.fillDepth.sample(random);
			// each column (x, z) -> its floor height and how deep it is cut
			Long2IntLinkedOpenHashMap tops = new Long2IntLinkedOpenHashMap(), cuts = new Long2IntLinkedOpenHashMap();
			float yaw = random.nextFloat() * Mth.TWO_PI;
			int length = Math.max(2, this.maxLength / 2 + random.nextInt(this.maxLength / 2 + 1));
			float width = this.maxWidth * (0.6F + random.nextFloat() * 0.4F);
			for (int arm = 0; arm < 2; arm++) {
				float heading = yaw + arm * Mth.PI, w = width;
				double x = start.getX() + 0.5, z = start.getZ() + 0.5;
				int y = start.getY();
				for (int step = 0; step < (length + 1) / 2 && w >= 1.0F; step++) {
					BlockPos cursor = this.surfaceNear(level, BlockPos.containing(x, y + this.cursorSurfaceSearchRange, z), this.cursorSurfaceSearchRange * 2);
					// (it stops where the floor drops or climbs away: a crevasse runs along level ground)
					if (cursor == null || Math.abs(cursor.getY() - y) > STEP_RISE) {
						break;
					}
					y = cursor.getY();
					this.cutAcross(level, origin, x, y, z, w, depth, tops, cuts);
					heading += (random.nextFloat() * 2.0F - 1.0F) * this.twistiness * this.turnFactor * 0.1F;
					w *= 1.0F - this.tapering * (0.5F + random.nextFloat() * 0.5F);
					x += Mth.cos(heading);
					z += Mth.sin(heading);
				}
			}
			if (tops.size() < this.minFootprint) {
				return false;
			}
			// everything it changes, and of that, what is cut out under the roof
			LongSet changed = new LongOpenHashSet(), under = new LongOpenHashSet();
			for (var entry : tops.long2IntEntrySet()) {
				int cx = BlockPos.getX(entry.getLongKey()), cz = BlockPos.getZ(entry.getLongKey()), top = entry.getIntValue();
				for (int d = 0; d < cuts.get(entry.getLongKey()); d++) {
					changed.add(BlockPos.asLong(cx, top - d, cz));
					if (d > 0) {
						under.add(BlockPos.asLong(cx, top - d, cz));
					}
				}
			}
			// its walls must hold: every side and the bottom of what is cut out under the roof
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (long packed : under) {
				int bx = BlockPos.getX(packed), by = BlockPos.getY(packed), bz = BlockPos.getZ(packed);
				for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
					at.set(bx + d.getStepX(), by + d.getStepY(), bz + d.getStepZ());
					if (changed.contains(at.asLong())) {
						continue;
					}
					if (!this.acceptableBarrier.test(level, at) && !this.carvableBlocks.test(level, at)) {
						return false;
					}
				}
			}
			for (var entry : tops.long2IntEntrySet()) {
				int cx = BlockPos.getX(entry.getLongKey()), cz = BlockPos.getZ(entry.getLongKey()), top = entry.getIntValue();
				int cut = cuts.get(entry.getLongKey());
				for (int d = 0; d < cut; d++) {
					at.set(cx, top - d, cz);
					BlockState into = d == 0 ? this.surfaceBlock : d >= fill ? this.fillBlock : Blocks.AIR.defaultBlockState();
					level.setBlock(at, into, Block.UPDATE_CLIENTS);
				}
			}
			return true;
		}

		/** The carvable floor top within {@code range} below {@code from} (air over it), or null. */
		private BlockPos surfaceNear(WorldGenLevel level, BlockPos from, int range) {
			BlockPos.MutableBlockPos at = from.mutable();
			for (int i = 0; i <= range && !level.isOutsideBuildHeight(at); i++) {
				if (!level.getBlockState(at).isAir()) {
					return this.carvableBlocks.test(level, at) && level.getBlockState(at.above()).isAir() ? at.immutable() : null;
				}
				at.move(Direction.DOWN);
			}
			return null;
		}

		/** One step's cut: every column within {@code width / 2} of the cursor, deepest at the middle. */
		private void cutAcross(WorldGenLevel level, BlockPos origin, double x, int y, double z, float width, int depth,
			Long2IntLinkedOpenHashMap tops, Long2IntLinkedOpenHashMap cuts) {
			float r = width / 2.0F;
			int ri = Mth.ceil(r);
			for (int dx = -ri; dx <= ri; dx++) {
				for (int dz = -ri; dz <= ri; dz++) {
					int cx = Mth.floor(x) + dx, cz = Mth.floor(z) + dz;
					double off = Math.sqrt((cx + 0.5 - x) * (cx + 0.5 - x) + (cz + 0.5 - z) * (cz + 0.5 - z));
					if (off > r + 0.25 || !inReach(origin, cx, cz)) {
						continue;
					}
					long key = BlockPos.asLong(cx, 0, cz);
					int top;
					if (tops.containsKey(key)) {
						top = tops.get(key);
					} else {
						BlockPos found = this.surfaceNear(level, new BlockPos(cx, y + 2, cz), this.columnSurfaceSearchRange);
						if (found == null || Math.abs(found.getY() - y) > COLUMN_RISE) {
							continue;
						}
						top = found.getY();
					}
					float across = (float) Math.min(1.0, off / Math.max(r, 0.5F));
					int cut = Math.max(1, Math.round(depth * (1.0F - across * across)));
					// (stops where it meets something it can't carve)
					int can = 0;
					while (can < cut && this.carvableBlocks.test(level, new BlockPos(cx, top - can, cz))) {
						can++;
					}
					if (can == 0) {
						continue;
					}
					tops.put(key, top);
					cuts.put(key, Math.max(cuts.getOrDefault(key, 0), can));
				}
			}
		}
	}

	/**
	 * A large icicle: from a cave's ceiling a cone of {@code block} hangs down, and from the floor under it one rises; when
	 * they would meet they grow into one column. Each is {@code radius} at its base (no more than a quarter of the cave's
	 * height) and narrows to a point. Only air and water are filled.
	 */
	public record LargeIcicle(BlockState block, IntProvider radius, int searchRange, float columnChance) implements Feature {
		public static final MapCodec<LargeIcicle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BlockState.CODEC.fieldOf("block").forGetter(LargeIcicle::block),
			IntProviders.codec(1, 8).fieldOf("radius").forGetter(LargeIcicle::radius),
			Codec.intRange(4, 32).fieldOf("floor_to_ceiling_search_range").forGetter(LargeIcicle::searchRange),
			Codec.floatRange(0.0F, 1.0F).fieldOf("column_chance").forGetter(LargeIcicle::columnChance)).apply(i, LargeIcicle::new));

		@Override
		public MapCodec<LargeIcicle> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.getBlockState(origin).isAir()) {
				return false;
			}
			BlockPos ceiling = ceilingOver(level, origin, this.searchRange), floor = floorUnder(level, origin, this.searchRange);
			if (ceiling == null || floor == null) {
				return false;
			}
			int height = ceiling.getY() - floor.getY() - 1;
			if (height < 4) {
				return false;
			}
			int r = Math.max(1, Math.min(this.radius.sample(random), height / 4));
			int down, up;
			if (random.nextFloat() < this.columnChance) {
				down = height / 2 + 1;
				up = height - down + 1;
			} else {
				down = Math.max(2, Mth.floor(height * (0.25F + random.nextFloat() * 0.4F)));
				up = Math.max(1, Math.min(height - down - 1, Mth.floor(height * (0.15F + random.nextFloat() * 0.35F))));
			}
			int x = origin.getX(), z = origin.getZ();
			boolean placed = this.cone(level, x, ceiling.getY() - 1, z, r, down, -1);
			placed |= this.cone(level, x, floor.getY() + 1, z, r, up, 1);
			return placed;
		}

		/** A cone from {@code baseY} going {@code dir} (-1 down, 1 up) for {@code length} blocks, widest at its base. */
		private boolean cone(WorldGenLevel level, int x, int baseY, int z, int r, int length, int dir) {
			boolean placed = false;
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int k = 0; k < length; k++) {
				float layer = r * (float) Math.pow(1.0 - (double) k / length, 0.9);
				int li = Mth.ceil(layer);
				for (int dx = -li; dx <= li; dx++) {
					for (int dz = -li; dz <= li; dz++) {
						if (dx * dx + dz * dz > layer * layer + 0.3F) {
							continue;
						}
						at.set(x + dx, baseY + k * dir, z + dz);
						if (k == 0) {
							// seat it on an uneven ceiling or floor: fill the gap back to the rock
							for (int seat = 1; seat <= 2; seat++) {
								BlockPos back = at.relative(dir > 0 ? Direction.DOWN : Direction.UP, seat);
								if (!this.open(level, back)) {
									break;
								}
								level.setBlock(back, this.block, Block.UPDATE_CLIENTS);
							}
						}
						if (this.open(level, at)) {
							level.setBlock(at, this.block, Block.UPDATE_CLIENTS);
							placed = true;
						}
					}
				}
			}
			return placed;
		}

		private boolean open(WorldGenLevel level, BlockPos pos) {
			BlockState there = level.getBlockState(pos);
			return there.isAir() || there.is(Blocks.WATER);
		}
	}

	/** Icicles hanging from a patch of ceiling, and a short standing one now and then under them. */
	public record Icicles() implements Feature {
		public static final MapCodec<Icicles> CODEC = MapCodec.unit(Icicles::new);

		@Override
		public MapCodec<Icicles> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.getBlockState(origin).isAir()) {
				return false;
			}
			BlockPos ceiling = ceilingOver(level, origin, SEEK);
			if (ceiling == null) {
				return false;
			}
			boolean placed = false;
			for (int dx = -ICICLE_RADIUS; dx <= ICICLE_RADIUS; dx++) {
				for (int dz = -ICICLE_RADIUS; dz <= ICICLE_RADIUS; dz++) {
					if (dx * dx + dz * dz > ICICLE_RADIUS * ICICLE_RADIUS || random.nextInt(10) >= ICICLE_SHARE) {
						continue;
					}
					BlockPos top = ceilingOver(level, origin.offset(dx, ceiling.getY() - origin.getY() - 2, dz), 4);
					if (top == null) {
						continue;
					}
					int hung = hang(level, top.below(), 1 + random.nextInt(IcicleBlock.MAX_LENGTH - 1), Direction.DOWN);
					placed |= hung > 0;
					if (hung > 0 && random.nextInt(10) < STANDING_SHARE) {
						BlockPos floor = floorUnder(level, top.below(hung + 1), SEEK);
						if (floor != null && floor.getY() < top.getY() - hung - 2) {
							hang(level, floor.above(), 1 + random.nextInt(2), Direction.UP);
						}
					}
				}
			}
			return placed;
		}

		/** An icicle from {@code from} pointing {@code tip}, up to {@code length} long (shorter if it meets anything). */
		private static int hang(WorldGenLevel level, BlockPos from, int length, Direction tip) {
			BlockState base = Frost.ICICLE.defaultBlockState().setValue(IcicleBlock.TIP_DIRECTION, tip);
			if (!base.canSurvive(level, from)) {
				return 0;
			}
			int fits = 0;
			while (fits < length && level.getBlockState(from.relative(tip, fits)).isAir() && level.getBlockState(from.relative(tip, fits + 1)).isAir()) {
				fits++;
			}
			for (int i = 0; i < fits; i++) {
				IcicleBlock.Part part = i == fits - 1 ? IcicleBlock.Part.TIP : i == 0 ? IcicleBlock.Part.BASE : IcicleBlock.Part.MIDDLE;
				level.setBlock(from.relative(tip, i), base.setValue(IcicleBlock.PART, part), Block.UPDATE_CLIENTS);
			}
			return fits;
		}
	}

	/** A patch of floor: snow lying on it (a layer or two) and ice crystals growing out of it. */
	public record Floor() implements Feature {
		public static final MapCodec<Floor> CODEC = MapCodec.unit(Floor::new);

		@Override
		public MapCodec<Floor> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.getBlockState(origin).isAir()) {
				return false;
			}
			BlockPos floor = floorUnder(level, origin, SEEK);
			if (floor == null) {
				return false;
			}
			int radius = 2 + random.nextInt(FLOOR_RADIUS - 1);
			boolean placed = false;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dz * dz > radius * radius - random.nextInt(2)) {
						continue;
					}
					BlockPos ground = floorUnder(level, floor.offset(dx, 2, dz), 4);
					if (ground == null) {
						continue;
					}
					BlockPos on = ground.above();
					int roll = random.nextInt(100);
					BlockState put = null;
					if (roll < CRYSTAL_SHARE) {
						put = Frost.ICE_CRYSTAL.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
					} else if (roll < CRYSTAL_SHARE + SNOW_SHARE) {
						put = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(2));
					}
					if (put != null && put.canSurvive(level, on)) {
						level.setBlock(on, put, Block.UPDATE_CLIENTS);
						placed = true;
					}
				}
			}
			return placed;
		}
	}
}
