package com.jaymospowers.eden;

import com.jaymospowers.aether.AetherBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Everything of {@link Eden} that grows or is laid in the world, one {@link Kind} each: the turf of the mycotic forests
 * and the worn ground of the stone garden (laid a chunk at a time, only where the column's own biome asks for it, so the
 * edges follow the biome's), balloon mushrooms young and old, pulse trees, and the stone garden's pillars and boulders.
 * The trees take their origin as the first block of the trunk, which is how both a sapling and the world's placement
 * give it. Data: {@code worldgen/feature/eden_*.json} and their placements, from {@code tools/gen_eden.py}.
 */
public record EdenFeature(Kind kind) implements Feature {
	public enum Kind {
		MYCELIUM_GROUND, STONE_GROUND, MYCOTIC_PLANTS, BALLOON_MUSHROOM, OLD_BALLOON_MUSHROOM, PULSE_TREE, STONE_PILLAR, STONE_BOULDER,
		SAND_GROUND, POND, AQUATUS, BRAIN_TREE, METAL_GRASS, GRAVILITE,
		VIOLUM, LONLIX, PARIGNUM, EDEN_MOSS, LIMPHIUM, EDEN_VINES, SYMBIOTIC_MOLD,
		SUNROOT_TREE, VOLVOX_COLONY, HOLLOW_ISLAND, ISLAND_CAVES;

		static final Codec<Kind> CODEC = Codec.STRING.xmap(s -> Kind.valueOf(s.toUpperCase(Locale.ROOT)), k -> k.name().toLowerCase(Locale.ROOT));
	}

	public static final MapCodec<EdenFeature> CODEC = Kind.CODEC.fieldOf("kind").xmap(EdenFeature::new, EdenFeature::kind);

	// ---- tuning ----
	/** Of the stone garden's ground, out of 100: mossy stone, bare stone, gravel (the rest stays turf). */
	public static final int GARDEN_MOSSY = 42, GARDEN_STONE = 18, GARDEN_GRAVEL = 10;
	/** A patch of mycotic grass: tries, how far out, and the share of them that is a small balloon mushroom instead. */
	public static final int PLANT_TRIES = 24, PLANT_SPREAD = 6;
	public static final float SMALL_BALLOON_SHARE = 0.08F;
	/** A young balloon mushroom: stem height (least, spread) and cap radius. */
	public static final int BALLOON_STEM = 3, BALLOON_STEM_SPREAD = 5, BALLOON_CAP = 2;
	/** An old one: trunk height (least, spread), top cap radius, branches (most), a branch's reach and rise (most), lanterns (most). */
	public static final int OLD_TRUNK = 11, OLD_TRUNK_SPREAD = 10, OLD_CAP = 4, OLD_BRANCHES = 4, OLD_REACH = 4, OLD_RISE = 3, OLD_LANTERNS = 6;
	/** A lantern hangs this far under its cap at most, on a thin stem. */
	public static final int LANTERN_DROP = 3;
	/** A pulse tree: share with a log trunk and its height (most), stems under each head (least, spread). */
	public static final float PULSE_TRUNK_CHANCE = 0.45F;
	public static final int PULSE_TRUNK = 4, PULSE_STEMS = 1, PULSE_STEMS_SPREAD = 3;
	/** A stone pillar: height (least, spread) and radius (most); a boulder's radius (least, spread). */
	public static final int PILLAR_HEIGHT = 5, PILLAR_HEIGHT_SPREAD = 10, PILLAR_RADIUS = 2, BOULDER_RADIUS = 2, BOULDER_RADIUS_SPREAD = 2;
	/** The lakeside desert: share of its sand that is gravel; a pond's radius (least, spread), depth, and the aquatus on its shore (most). */
	public static final int DESERT_GRAVEL = 14, POND_RADIUS = 3, POND_RADIUS_SPREAD = 3, POND_DEPTH = 3, POND_AQUATUS = 4;
	/** The ground round a pond may stand this far off its level, or no pond is dug. */
	public static final int POND_TOLERANCE = 2;
	/** A brain tree: trunk height (least, spread) and crown radius (least, spread); a patch of metal grass: tries and spread. */
	public static final int BRAIN_TRUNK = 5, BRAIN_TRUNK_SPREAD = 4, BRAIN_CROWN = 2, BRAIN_CROWN_SPREAD = 1, METAL_GRASS_TRIES = 14, METAL_GRASS_SPREAD = 5;
	/** A stand of gravilite shards: tries and spread. */
	public static final int GRAVILITE_TRIES = 8, GRAVILITE_SPREAD = 2;
	/** A patch of small plants: tries and spread; a patch of moss or parignum is laid thicker. */
	public static final int SMALL_TRIES = 10, SMALL_SPREAD = 4, COVER_TRIES = 28;
	/** Hanging growth: columns tried, spread, how far below the surface an underside is looked for, a vine's length (most), share of mould that glows. */
	public static final int HANG_TRIES = 12, HANG_SPREAD = 5, HANG_SEARCH = 24, VINE_LENGTH = 5;
	public static final float MOLD_GLOW_SHARE = 0.25F;
	/** A sunroot: trunk height (least, spread) and the radius of its crown's widest layer. */
	public static final int SUNROOT_TRUNK = 6, SUNROOT_TRUNK_SPREAD = 4, SUNROOT_CROWN = 3;
	/** A volvox colony: radius (least, spread), the share of its skin that is dense, and its dense heart's share of the radius. */
	public static final int VOLVOX_RADIUS = 3, VOLVOX_RADIUS_SPREAD = 4;
	public static final float VOLVOX_DENSE_SHARE = 0.15F, VOLVOX_HEART = 0.35F;
	/** A volvox with a room in it: from this radius up, the share of the radius that is pool, and the share the pool takes of the width (the rest is shore). */
	public static final int VOLVOX_ROOM_FROM = 5;
	public static final float VOLVOX_WATER = 0.3F, VOLVOX_SHORE = 0.45F;
	/** A hollow island: radius (least, spread), its height as a share of that, the hollow's share of the shell, and the doors broken into it (most). */
	public static final int HOLLOW_RADIUS = 7, HOLLOW_RADIUS_SPREAD = 5, HOLLOW_DOORS = 3;
	public static final float HOLLOW_HEIGHT = 0.6F, HOLLOW_INSIDE = 0.72F;
	/** One hollow island, and one island cave, in this many is a gas cave (see {@link EdenCaves}). */
	public static final int GAS_ONE_IN = 4;
	/** Island caves: the length of a tunnel (steps, least and spread), how far it may wander from where it starts, and its girth. */
	public static final int CAVE_STEPS = 22, CAVE_STEPS_SPREAD = 22, CAVE_WANDER = 12;
	public static final double CAVE_GIRTH = 1.6, CAVE_GIRTH_SWELL = 0.9;
	/** Tunnels a try cuts (least and spread), and how far (blocks) down it looks for the island's solid body. */
	public static final int CAVE_TUNNELS = 1, CAVE_TUNNELS_SPREAD = 2, CAVE_SEEK = 14;
	/** The colours lanterns grow in wild (builders dye the rest). */
	private static final DyeColor[] WILD_LANTERNS = {DyeColor.WHITE, DyeColor.YELLOW, DyeColor.LIGHT_BLUE, DyeColor.LIME, DyeColor.PINK, DyeColor.ORANGE,
		DyeColor.MAGENTA, DyeColor.CYAN};

	@Override
	public MapCodec<EdenFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		return switch (this.kind) {
			case MYCELIUM_GROUND -> ground(level, random, origin, false);
			case STONE_GROUND -> ground(level, random, origin, true);
			case MYCOTIC_PLANTS -> plants(level, random, origin);
			case BALLOON_MUSHROOM -> balloon(level, random, origin);
			case OLD_BALLOON_MUSHROOM -> oldBalloon(level, random, origin);
			case PULSE_TREE -> pulse(level, random, origin);
			case STONE_PILLAR -> pillar(level, random, origin);
			case STONE_BOULDER -> boulder(level, random, origin);
			case SAND_GROUND -> sand(level, random, origin);
			case POND -> pond(level, random, origin);
			case AQUATUS -> aquatus(level, random, origin);
			case BRAIN_TREE -> brainTree(level, random, origin);
			case METAL_GRASS -> metalGrass(level, random, origin);
			case GRAVILITE -> gravilite(level, random, origin);
			case VIOLUM -> scatter(level, random, origin, Eden.VIOLUM, SMALL_TRIES);
			case LIMPHIUM -> scatter(level, random, origin, Eden.LIMPHIUM, SMALL_TRIES);
			case LONLIX -> scatter(level, random, origin, Eden.LONLIX, SMALL_TRIES);
			case PARIGNUM -> scatter(level, random, origin, Eden.PARIGNUM, COVER_TRIES);
			case EDEN_MOSS -> scatter(level, random, origin, Eden.EDEN_MOSS, COVER_TRIES);
			case EDEN_VINES -> hang(level, random, origin, true);
			case SYMBIOTIC_MOLD -> hang(level, random, origin, false);
			case SUNROOT_TREE -> sunroot(level, random, origin);
			case VOLVOX_COLONY -> volvox(level, random, origin);
			case HOLLOW_ISLAND -> hollowIsland(level, random, origin);
			case ISLAND_CAVES -> islandCaves(level, random, origin);
		};
	}

	// ---------------------------------------------------------------- ground

	private static boolean ground(WorldGenLevel level, RandomSource random, BlockPos origin, boolean garden) {
		int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean laid = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x0 + dx, z0 + dz) - 1;
				pos.set(x0 + dx, y, z0 + dz);
				if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK) || !level.getBiome(pos).is(garden ? Eden.STONE_GROUND : Eden.MYCELIUM_GROUND)) {
					continue;
				}
				if (!garden) {
					level.setBlock(pos, Eden.MYCELIUM.defaultBlockState(), Block.UPDATE_CLIENTS);
					laid = true;
					continue;
				}
				int roll = random.nextInt(100);
				BlockState state = roll < GARDEN_MOSSY ? Eden.MOSSY_STONE.defaultBlockState()
					: roll < GARDEN_MOSSY + GARDEN_STONE ? Blocks.STONE.defaultBlockState()
					: roll < GARDEN_MOSSY + GARDEN_STONE + GARDEN_GRAVEL ? Blocks.GRAVEL.defaultBlockState() : null;
				if (state != null) {
					level.setBlock(pos, state, Block.UPDATE_CLIENTS);
					// stone goes a little way down, so a dug pit isn't dirt under a skin of rock
					if (level.getBlockState(pos.move(Direction.DOWN)).is(BlockTags.DIRT)) {
						level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					laid = true;
				}
			}
		}
		return laid;
	}

	private static boolean plants(WorldGenLevel level, RandomSource random, BlockPos origin) {
		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < PLANT_TRIES; i++) {
			int x = origin.getX() + random.nextInt(PLANT_SPREAD * 2 + 1) - PLANT_SPREAD, z = origin.getZ() + random.nextInt(PLANT_SPREAD * 2 + 1) - PLANT_SPREAD;
			pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Eden.MYCELIUM)) {
				level.setBlock(pos, (random.nextFloat() < SMALL_BALLOON_SHARE ? Eden.BALLOON_SMALL : Eden.MYCOTIC_GRASS).defaultBlockState(), Block.UPDATE_CLIENTS);
				placed = true;
			}
		}
		return placed;
	}

	// ---------------------------------------------------------------- helpers

	/** Whether a tree can root on what is under {@code origin}. */
	private static boolean soil(WorldGenLevel level, BlockPos origin) {
		BlockState below = level.getBlockState(origin.below());
		return below.is(Eden.MYCELIUM) || below.is(BlockTags.DIRT);
	}

	private static boolean free(WorldGenLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES);
	}

	/** Whether the column from {@code origin} up {@code height} blocks is free. */
	private static boolean room(WorldGenLevel level, BlockPos origin, int height) {
		if (origin.getY() + height + 2 >= level.getMaxY()) {
			return false;
		}
		for (int dy = 0; dy < height; dy++) {
			if (!free(level, origin.above(dy))) {
				return false;
			}
		}
		return true;
	}

	private static void put(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (free(level, pos)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	/** A round cap layer {@code radius} out from {@code middle} (corners trimmed). */
	private static void disc(WorldGenLevel level, BlockPos middle, int radius, BlockState state) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius + 1) {
					put(level, middle.offset(dx, 0, dz), state);
				}
			}
		}
	}

	/** A domed cap whose widest layer sits at {@code middle}. */
	private static void cap(WorldGenLevel level, BlockPos middle, int radius) {
		BlockState cap = Eden.BALLOON_CAP.defaultBlockState();
		for (int r = radius, dy = 0; r >= 1 && dy < 3; r -= dy == 0 ? 1 : 2, dy++) {
			disc(level, middle.above(dy), r, cap);
		}
	}

	// ---------------------------------------------------------------- balloon mushrooms

	private static boolean balloon(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int height = BALLOON_STEM + random.nextInt(BALLOON_STEM_SPREAD + 1);
		if (!soil(level, origin) || !room(level, origin, height + 2)) {
			return false;
		}
		for (int dy = 0; dy < height; dy++) {
			level.setBlock(origin.above(dy), Eden.BALLOON_STEM.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		cap(level, origin.above(height), height < 5 ? 1 : BALLOON_CAP);
		return true;
	}

	private static boolean oldBalloon(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int height = OLD_TRUNK + random.nextInt(OLD_TRUNK_SPREAD + 1);
		if (!soil(level, origin) || !room(level, origin, height + 3)) {
			return false;
		}
		BlockState log = Eden.BALLOON_LOG.defaultBlockState();
		for (int dy = 0; dy < height; dy++) {
			level.setBlock(origin.above(dy), log, Block.UPDATE_CLIENTS);
		}
		DyeColor colour = WILD_LANTERNS[random.nextInt(WILD_LANTERNS.length)];
		BlockPos top = origin.above(height);
		cap(level, top, OLD_CAP);
		lanterns(level, random, top, OLD_CAP, 2 + random.nextInt(OLD_LANTERNS - 1), colour);
		// branches: out from the trunk, then up to a smaller cap
		int branches = random.nextInt(OLD_BRANCHES + 1);
		for (int i = 0; i < branches; i++) {
			Direction way = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int at = 4 + random.nextInt(Math.max(1, height - 8));
			int reach = 2 + random.nextInt(OLD_REACH - 1), rise = 1 + random.nextInt(OLD_RISE);
			BlockPos.MutableBlockPos pos = origin.above(at).mutable();
			BlockState lying = log.setValue(RotatedPillarBlock.AXIS, way.getAxis());
			for (int out = 0; out < reach; out++) {
				put(level, pos.move(way), lying);
			}
			for (int up = 0; up < rise; up++) {
				put(level, pos.move(Direction.UP), log);
			}
			BlockPos head = pos.above();
			cap(level, head, BALLOON_CAP);
			lanterns(level, random, head, BALLOON_CAP, random.nextInt(3), colour);
		}
		return true;
	}

	/** Hangs {@code count} lanterns under the rim of the cap at {@code middle}. */
	private static void lanterns(WorldGenLevel level, RandomSource random, BlockPos middle, int radius, int count, DyeColor colour) {
		BlockState lantern = Eden.LANTERNS.get(colour).defaultBlockState();
		for (int i = 0; i < count; i++) {
			int dx = random.nextInt(radius * 2 + 1) - radius, dz = random.nextInt(radius * 2 + 1) - radius;
			BlockPos.MutableBlockPos pos = middle.offset(dx, 0, dz).mutable();
			if ((dx == 0 && dz == 0) || !level.getBlockState(pos).is(Eden.BALLOON_CAP)) {
				continue;
			}
			int drop = random.nextInt(LANTERN_DROP + 1);
			boolean clear = true;
			for (int down = 1; down <= drop + 1 && clear; down++) {
				clear = level.getBlockState(pos.setWithOffset(middle, dx, -down, dz)).isAir();
			}
			if (!clear) {
				continue;
			}
			for (int down = 1; down <= drop; down++) {
				level.setBlock(pos.setWithOffset(middle, dx, -down, dz), Eden.BALLOON_STEM.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			level.setBlock(pos.setWithOffset(middle, dx, -drop - 1, dz), lantern, Block.UPDATE_CLIENTS);
		}
	}

	// ---------------------------------------------------------------- pulse trees

	private static boolean pulse(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int trunk = random.nextFloat() < PULSE_TRUNK_CHANCE ? 1 + random.nextInt(PULSE_TRUNK) : 0;
		int[] stems = new int[3];
		int height = trunk;
		for (int i = 0; i < stems.length; i++) {
			stems[i] = PULSE_STEMS + random.nextInt(PULSE_STEMS_SPREAD + 1) + (i == 0 ? 1 : 0);
			height += stems[i] + 1;
		}
		if (!soil(level, origin) || !room(level, origin, height)) {
			return false;
		}
		BlockPos.MutableBlockPos pos = origin.mutable();
		for (int i = 0; i < trunk; i++) {
			level.setBlock(pos, Eden.PULSE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
			pos.move(Direction.UP);
		}
		BlockState stem = Eden.PULSE_TREE.defaultBlockState();
		PulseTreeBlock.Shape[] heads = {PulseTreeBlock.Shape.HEAD_BIG, PulseTreeBlock.Shape.HEAD_MEDIUM, PulseTreeBlock.Shape.HEAD_SMALL};
		for (int i = 0; i < stems.length; i++) {
			for (int s = 0; s < stems[i]; s++) {
				level.setBlock(pos, stem, Block.UPDATE_CLIENTS);
				pos.move(Direction.UP);
			}
			level.setBlock(pos, stem.setValue(PulseTreeBlock.SHAPE, heads[i]), Block.UPDATE_CLIENTS);
			pos.move(Direction.UP);
		}
		return true;
	}

	// ---------------------------------------------------------------- the stone garden

	/** The rock of a layer {@code dy} up: bands of stone and andesite, mossy at the foot and wherever it rolls so. */
	private static BlockState rock(RandomSource random, int dy, int height) {
		if (dy < 2 ? random.nextInt(3) > 0 : random.nextInt(9) == 0) {
			return Eden.MOSSY_STONE.defaultBlockState();
		}
		return switch ((dy / 2) % 3) {
			case 0 -> Blocks.STONE.defaultBlockState();
			case 1 -> Blocks.ANDESITE.defaultBlockState();
			default -> dy > height - 3 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
		};
	}

	private static boolean stands(WorldGenLevel level, BlockPos origin) {
		BlockState below = level.getBlockState(origin.below());
		return below.is(Eden.MOSSY_STONE) || below.is(BlockTags.DIRT) || below.is(BlockTags.BASE_STONE_OVERWORLD) || below.is(Blocks.GRAVEL);
	}

	private static boolean pillar(WorldGenLevel level, RandomSource random, BlockPos origin) {
		if (!stands(level, origin)) {
			return false;
		}
		int height = PILLAR_HEIGHT + random.nextInt(PILLAR_HEIGHT_SPREAD + 1);
		int radius = 1 + random.nextInt(PILLAR_RADIUS);
		// wind-worn: thick at the foot and under the head, thin at the waist
		int waist = height / 3 + random.nextInt(Math.max(1, height / 3));
		for (int dy = -2; dy < height; dy++) {
			float worn = Math.min(1.0F, Math.abs(dy - waist) / (float) Math.max(2, height / 2));
			float r = dy >= height - 1 ? radius * 0.7F : 0.6F + radius * (0.45F + 0.55F * worn);
			int reach = (int) Math.ceil(r);
			BlockState state = rock(random, Math.max(dy, 0), height);
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if (dx * dx + dz * dz <= r * r + 0.3F) {
						BlockPos pos = origin.offset(dx, dy, dz);
						if (dy < 0 ? !level.getBlockState(pos).isSolidRender() : true) {
							level.setBlock(pos, state, Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
		return true;
	}

	private static boolean boulder(WorldGenLevel level, RandomSource random, BlockPos origin) {
		if (!stands(level, origin)) {
			return false;
		}
		int radius = BOULDER_RADIUS + random.nextInt(BOULDER_RADIUS_SPREAD + 1);
		int high = Math.max(2, radius - random.nextInt(2));
		for (int dy = -1; dy <= high; dy++) {
			BlockState state = rock(random, dy + 1, high + 4);
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					float d = (dx * dx + dz * dz) / (float) (radius * radius) + (dy * dy) / (float) (high * high);
					if (d <= 1.0F + (random.nextFloat() - 0.5F) * 0.25F) {
						level.setBlock(origin.offset(dx, dy, dz), state, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- the lakeside desert

	private static boolean sand(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean laid = false;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x0 + dx, z0 + dz) - 1;
				pos.set(x0 + dx, y, z0 + dz);
				if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK) || !level.getBiome(pos).is(Eden.SAND_GROUND)) {
					continue;
				}
				level.setBlock(pos, (random.nextInt(100) < DESERT_GRAVEL ? Blocks.GRAVEL : Blocks.SAND).defaultBlockState(), Block.UPDATE_CLIENTS);
				// sandstone under it, so the sand has something to lie on over a cave
				for (int down = 0; down < 2; down++) {
					if (level.getBlockState(pos.move(Direction.DOWN)).is(BlockTags.DIRT)) {
						level.setBlock(pos, Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				laid = true;
			}
		}
		return laid;
	}

	private static boolean shore(BlockState state) {
		return state.is(Blocks.SAND) || state.is(Blocks.GRAVEL) || state.is(BlockTags.DIRT);
	}

	private static boolean pond(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int radius = POND_RADIUS + random.nextInt(POND_RADIUS_SPREAD + 1);
		int surface = origin.getY() - 1;
		// only on ground level enough to hold water
		for (int dx = -radius - 1; dx <= radius + 1; dx += radius + 1) {
			for (int dz = -radius - 1; dz <= radius + 1; dz += radius + 1) {
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX() + dx, origin.getZ() + dz) - 1;
				if (Math.abs(y - surface) > POND_TOLERANCE || y < surface) {
					return false;
				}
			}
		}
		if (!shore(level.getBlockState(origin.below()))) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius - 1; dx <= radius + 1; dx++) {
			for (int dz = -radius - 1; dz <= radius + 1; dz++) {
				float out = (dx * dx + dz * dz) / (float) (radius * radius);
				if (out > 1.45F) {
					continue;
				}
				if (out > 1.0F) {
					// the rim: make sure the water has a bank at its own level
					pos.set(origin.getX() + dx, surface, origin.getZ() + dz);
					if (!level.getBlockState(pos).isSolidRender()) {
						level.setBlock(pos, Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
						level.setBlock(pos.move(Direction.DOWN), Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					continue;
				}
				int depth = Math.max(1, Math.round(POND_DEPTH * (1.0F - out)));
				// clear what stands over the water, then the bowl: clay under, water in it
				for (int up = 1; up <= 3; up++) {
					pos.set(origin.getX() + dx, surface + up, origin.getZ() + dz);
					if (!level.getBlockState(pos).isAir()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				for (int down = 0; down < depth; down++) {
					level.setBlock(pos.set(origin.getX() + dx, surface - down, origin.getZ() + dz), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				level.setBlock(pos.set(origin.getX() + dx, surface - depth, origin.getZ() + dz),
					(random.nextInt(3) == 0 ? Blocks.CLAY : Blocks.SAND).defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(pos.move(Direction.DOWN), Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		// the drinkers on the shore
		int plants = 1 + random.nextInt(POND_AQUATUS);
		for (int i = 0; i < plants; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int x = origin.getX() + (int) Math.round(Math.cos(angle) * (radius + 2)), z = origin.getZ() + (int) Math.round(Math.sin(angle) * (radius + 2));
			aquatus(level, random, new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z));
		}
		return true;
	}

	/** An aquatus: a twist of roots one or two blocks tall with the head on top. */
	private static boolean aquatus(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int roots = 1 + random.nextInt(2);
		if (!shore(level.getBlockState(origin.below())) || !room(level, origin, roots + 1)) {
			return false;
		}
		for (int dy = 0; dy < roots; dy++) {
			level.setBlock(origin.above(dy), Eden.AQUATUS_ROOTS.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		level.setBlock(origin.above(roots), Eden.AQUATUS.defaultBlockState(), Block.UPDATE_CLIENTS);
		return true;
	}

	// ---------------------------------------------------------------- the brainstorm

	private static boolean brainTree(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int height = BRAIN_TRUNK + random.nextInt(BRAIN_TRUNK_SPREAD + 1);
		int radius = BRAIN_CROWN + random.nextInt(BRAIN_CROWN_SPREAD + 1);
		if (!soil(level, origin) || !room(level, origin, height + radius * 2)) {
			return false;
		}
		BlockState log = Eden.BRAIN_LOG.defaultBlockState();
		for (int dy = 0; dy < height; dy++) {
			level.setBlock(origin.above(dy), log, Block.UPDATE_CLIENTS);
		}
		// one metal to a tree
		Block[] metals = {Eden.BRAIN_COPPER, Eden.BRAIN_COPPER, Eden.BRAIN_IRON, Eden.BRAIN_IRON, Eden.BRAIN_GOLD};
		BlockState flesh = metals[random.nextInt(metals.length)].defaultBlockState();
		BlockPos middle = origin.above(height + radius - 1);
		float high = radius * 0.85F;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					float d = (dx * dx + dz * dz) / (float) (radius * radius) + (dy * dy) / (high * high);
					// a groove down the middle, as a brain has
					if (d <= 1.05F && !(dx == 0 && dy >= radius - 1)) {
						put(level, middle.offset(dx, dy, dz), flesh);
					}
				}
			}
		}
		return true;
	}

	private static boolean metalGrass(WorldGenLevel level, RandomSource random, BlockPos origin) {
		Block[] kinds = {Eden.COPPER_GRASS, Eden.IRON_GRASS, Eden.GOLD_GRASS};
		Block kind = kinds[random.nextInt(kinds.length)];
		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < METAL_GRASS_TRIES; i++) {
			int x = origin.getX() + random.nextInt(METAL_GRASS_SPREAD * 2 + 1) - METAL_GRASS_SPREAD;
			int z = origin.getZ() + random.nextInt(METAL_GRASS_SPREAD * 2 + 1) - METAL_GRASS_SPREAD;
			pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) {
				level.setBlock(pos, kind.defaultBlockState(), Block.UPDATE_CLIENTS);
				placed = true;
			}
		}
		return placed;
	}

	// ---------------------------------------------------------------- gravilite

	private static boolean gravilite(WorldGenLevel level, RandomSource random, BlockPos origin) {
		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < GRAVILITE_TRIES; i++) {
			int x = origin.getX() + random.nextInt(GRAVILITE_SPREAD * 2 + 1) - GRAVILITE_SPREAD;
			int z = origin.getZ() + random.nextInt(GRAVILITE_SPREAD * 2 + 1) - GRAVILITE_SPREAD;
			pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
			BlockState below = level.getBlockState(pos.below());
			if (level.getBlockState(pos).isAir() && below.isFaceSturdy(level, pos.below(), Direction.UP) && !below.is(BlockTags.LEAVES)
				&& !below.is(Eden.BALLOON_CAP)) {
				level.setBlock(pos, Eden.GRAVILITE_SHARDS.defaultBlockState(), Block.UPDATE_CLIENTS);
				placed = true;
			}
		}
		return placed;
	}

	// ---------------------------------------------------------------- the small growth

	/** A patch of one ground plant on whatever it can stand on (two blocks tall where the plant is). */
	private static boolean scatter(WorldGenLevel level, RandomSource random, BlockPos origin, Block plant, int tries) {
		boolean placed = false;
		BlockState state = plant.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < tries; i++) {
			int x = origin.getX() + random.nextInt(SMALL_SPREAD * 2 + 1) - SMALL_SPREAD;
			int z = origin.getZ() + random.nextInt(SMALL_SPREAD * 2 + 1) - SMALL_SPREAD;
			pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
			if (!level.getBlockState(pos).isAir() || !state.canSurvive(level, pos) || !level.getFluidState(pos.below()).isEmpty()) {
				continue;
			}
			if (plant instanceof DoublePlantBlock) {
				if (!level.getBlockState(pos.above()).isAir()) {
					continue;
				}
				DoublePlantBlock.placeAt(level, state, pos, Block.UPDATE_CLIENTS);
			} else {
				level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}
			placed = true;
		}
		return placed;
	}

	/** Vines or mould under whatever overhangs here: caps, crowns, ledges. */
	private static boolean hang(WorldGenLevel level, RandomSource random, BlockPos origin, boolean vines) {
		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < HANG_TRIES; i++) {
			int x = origin.getX() + random.nextInt(HANG_SPREAD * 2 + 1) - HANG_SPREAD;
			int z = origin.getZ() + random.nextInt(HANG_SPREAD * 2 + 1) - HANG_SPREAD;
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
			// down through the roof to the first air under a solid underside
			pos.set(x, top, z);
			int searched = 0;
			while (searched < HANG_SEARCH && !(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN))) {
				pos.move(Direction.DOWN);
				searched++;
			}
			if (searched == 0 || searched >= HANG_SEARCH) {
				continue;
			}
			if (!vines) {
				Block mold = random.nextFloat() < MOLD_GLOW_SHARE ? Eden.SYMBIOTIC_MOLD_EMISSIVE : Eden.SYMBIOTIC_MOLD;
				level.setBlock(pos, mold.defaultBlockState(), Block.UPDATE_CLIENTS);
				placed = true;
				continue;
			}
			int length = 1 + random.nextInt(VINE_LENGTH);
			for (int n = 0; n < length && level.getBlockState(pos).isAir(); n++) {
				boolean last = n == length - 1 || !level.getBlockState(pos.below()).isAir();
				level.setBlock(pos, Eden.EDEN_VINE.defaultBlockState().setValue(HangingPlantBlock.BOTTOM, last), Block.UPDATE_CLIENTS);
				pos.move(Direction.DOWN);
				placed = true;
				if (last) {
					break;
				}
			}
		}
		return placed;
	}

	// ---------------------------------------------------------------- the Aether's share

	/** A sunroot: a straight trunk under a broad, flat-topped golden crown. */
	private static boolean sunroot(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int height = SUNROOT_TRUNK + random.nextInt(SUNROOT_TRUNK_SPREAD + 1);
		BlockState ground = level.getBlockState(origin.below());
		if (!(ground.is(BlockTags.DIRT) || ground.is(AetherBlocks.AETHER_GRASS_BLOCK)) || !room(level, origin, height + 2)) {
			return false;
		}
		BlockState log = Eden.SUNROOT_LOG.defaultBlockState();
		for (int dy = 0; dy < height; dy++) {
			level.setBlock(origin.above(dy), log, Block.UPDATE_CLIENTS);
		}
		// the crown, bottom to top: a fringe, two wide layers, a narrower one and a cap
		int[] radii = {SUNROOT_CROWN - 1, SUNROOT_CROWN, SUNROOT_CROWN, SUNROOT_CROWN - 1, 1};
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int layer = 0; layer < radii.length; layer++) {
			int y = origin.getY() + height - 3 + layer;
			int radius = radii[layer];
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					int far = Math.abs(dx) + Math.abs(dz);
					boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
					if (corner && (radius > 1 || random.nextBoolean()) || layer == 0 && far < 2) {
						continue;
					}
					pos.set(origin.getX() + dx, y, origin.getZ() + dz);
					if (free(level, pos) && !level.getBlockState(pos).is(Eden.SUNROOT_LOG)) {
						// the distance a leaf would count to the trunk, so none withers before it is first looked at
						int distance = Math.max(1, Math.min(6, far + Math.max(0, y - (origin.getY() + height - 1))));
						level.setBlock(pos, Eden.SUNROOT_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, distance), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		return true;
	}

	/** Whether a ball of this radius about {@code middle} is open sky (looked at in its middle and at its six ends). */
	private static boolean openSky(WorldGenLevel level, BlockPos middle, int radius) {
		if (middle.getY() - radius <= level.getMinY() + 1 || middle.getY() + radius >= level.getMaxY() - 1 || !level.getBlockState(middle).isAir()) {
			return false;
		}
		for (Direction side : Direction.values()) {
			if (!level.getBlockState(middle.relative(side, radius)).isAir()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A volvox colony: a drifting ball of jelly, patched with denser growth, round a dense heart. Inside it holds a pool in
	 * its lower part, with a mossy shore planted with small growth round it, so a flyer can come in through a gap and rest.
	 */
	private static boolean volvox(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int radius = VOLVOX_RADIUS + random.nextInt(VOLVOX_RADIUS_SPREAD + 1);
		if (!openSky(level, origin, radius + 1)) {
			return false;
		}
		float heart = Math.max(1.0F, radius * VOLVOX_HEART);
		// small balls are all heart; from the middle size up there is a room, a pool and a shore
		boolean room = radius >= VOLVOX_ROOM_FROM;
		int water = -(int)Math.floor(radius * VOLVOX_WATER);
		// a gap in the skin, high on one side, to fly in by
		double gapAngle = random.nextDouble() * Math.PI * 2.0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					double far = Math.sqrt(dx * dx + dy * dy + dz * dz);
					boolean skin = far <= radius + 0.3 && far > radius - 0.9;
					pos.setWithOffset(origin, dx, dy, dz);
					if (room && skin) {
						double angle = Math.atan2(dz, dx);
						double off = Math.abs(Math.atan2(Math.sin(angle - gapAngle), Math.cos(angle - gapAngle)));
						if (dy >= 1 && dy <= 3 && off * Math.sqrt(dx * dx + dz * dz) < 1.6) {
							continue;
						}
					}
					if (skin) {
						if (level.getBlockState(pos).isAir()) {
							Block block = random.nextFloat() < VOLVOX_DENSE_SHARE ? Eden.DENSE_VOLVOX : Eden.VOLVOX;
							level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					} else if (far <= heart && !room) {
						if (level.getBlockState(pos).isAir()) {
							level.setBlock(pos, Eden.DENSE_VOLVOX.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					} else if (room && far <= radius - 0.9 && level.getBlockState(pos).isAir()) {
						double flat = Math.sqrt(dx * dx + dz * dz);
						if (dy < water - 1 || dy == water - 1 && flat > radius * VOLVOX_SHORE) {
							level.setBlock(pos, Eden.VOLVOX.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (dy == water && flat > radius * VOLVOX_SHORE) {
							// the shore: moss, planted
							level.setBlock(pos, Blocks.MOSS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (dy <= water) {
							level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (dy == radius - 2 && flat < 1.0) {
							// a dense lantern hanging in the roof
							level.setBlock(pos, Eden.DENSE_VOLVOX.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (dy == water + 1 && flat > radius * VOLVOX_SHORE && random.nextInt(3) == 0) {
							Block plant = switch (random.nextInt(3)) {
								case 0 -> Eden.LONLIX;
								case 1 -> Eden.EDEN_MOSS;
								default -> Eden.PARIGNUM;
							};
							level.setBlock(pos, plant.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
		return true;
	}

	/**
	 * A hollow island: a shell of holystone adrift on its own, turfed on top, with a chamber inside that a few doors
	 * open on. Gravilite stands on the chamber's floor and vines hang from its roof.
	 */
	private static boolean hollowIsland(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int radius = HOLLOW_RADIUS + random.nextInt(HOLLOW_RADIUS_SPREAD + 1);
		if (!openSky(level, origin, radius + 1)) {
			return false;
		}
		double tall = radius * HOLLOW_HEIGHT;
		BlockState stone = AetherBlocks.HOLYSTONE.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int high = (int)Math.ceil(tall);
		// the doors: straight ways in from the rim, at chamber-floor height
		int doors = 1 + random.nextInt(HOLLOW_DOORS);
		double[] doorAngles = new double[doors];
		for (int i = 0; i < doors; i++) {
			doorAngles[i] = random.nextDouble() * Math.PI * 2.0;
		}
		int floor = -(int)Math.floor(tall * HOLLOW_INSIDE) + 1;
		boolean gassy = random.nextInt(GAS_ONE_IN) == 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = -high; dy <= high; dy++) {
					double x = dx / (double)radius, y = dy / tall, z = dz / (double)radius;
					double far = Math.sqrt(x * x + y * y + z * z);
					if (far > 1.0 || far < HOLLOW_INSIDE) {
						continue;
					}
					boolean door = false;
					if (dy >= floor && dy <= floor + 2) {
						double angle = Math.atan2(dz, dx);
						for (double at : doorAngles) {
							double off = Math.abs(Math.atan2(Math.sin(angle - at), Math.cos(angle - at)));
							door |= off * Math.sqrt(dx * dx + dz * dz) < 1.5;
						}
					}
					pos.setWithOffset(origin, dx, dy, dz);
					if (!door && level.getBlockState(pos).isAir()) {
						level.setBlock(pos, stone, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		// turf the top, and dress the chamber
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = high; dy >= -high; dy--) {
					pos.setWithOffset(origin, dx, dy, dz);
					if (!level.getBlockState(pos).is(AetherBlocks.HOLYSTONE)) {
						continue;
					}
					boolean top = dy > 0;
					if (top && level.getBlockState(pos.above()).isAir() && level.canSeeSky(pos.above())) {
						level.setBlock(pos, AetherBlocks.AETHER_GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
						if (level.getBlockState(pos.below()).is(AetherBlocks.HOLYSTONE)) {
							level.setBlock(pos.below(), AetherBlocks.AETHER_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
						continue;
					}
					if (top && level.getBlockState(pos.below()).isAir() && random.nextInt(5) == 0) {
						level.setBlock(pos.below(), Eden.EDEN_VINE.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else if (!top && level.getBlockState(pos.above()).isAir() && random.nextInt(7) == 0) {
						level.setBlock(pos.above(), (gassy ? EdenCaves.GAS_VENT : Eden.GRAVILITE_SHARDS).defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		return true;
	}

	/** Whether a block is the island's own body, which a cave may be cut from. */
	private static boolean body(BlockState state) {
		return state.is(AetherBlocks.HOLYSTONE) || state.is(AetherBlocks.AETHER_DIRT) || state.is(AetherBlocks.QUICKSOIL);
	}

	/**
	 * Caves worn into an island itself: from a point in its body, winding tunnels swell and narrow, never wandering far, cut
	 * only through the island's own stone and soil (so they open on air where they meet it); now and then one is a gas
	 * cave, its floor dotted with vents, and in the others a shard of gravilite shows here and there.
	 */
	private static boolean islandCaves(WorldGenLevel level, RandomSource random, BlockPos origin) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos start = null;
		for (int dy = 0; dy < CAVE_SEEK && start == null; dy++) {
			pos.set(origin.getX(), origin.getY() - dy, origin.getZ());
			if (body(level.getBlockState(pos)) && body(level.getBlockState(pos.below())) && body(level.getBlockState(pos.below(4)))) {
				start = pos.below(3 + random.nextInt(3)).immutable();
			}
		}
		if (start == null) {
			return false;
		}
		boolean gassy = random.nextInt(GAS_ONE_IN) == 0;
		int tunnels = CAVE_TUNNELS + random.nextInt(CAVE_TUNNELS_SPREAD + 1);
		boolean cut = false;
		for (int t = 0; t < tunnels; t++) {
			double x = start.getX() + 0.5, y = start.getY() + 0.5, z = start.getZ() + 0.5;
			double yaw = random.nextDouble() * Math.PI * 2.0, pitch = (random.nextDouble() - 0.5) * 0.4;
			int steps = CAVE_STEPS + random.nextInt(CAVE_STEPS_SPREAD + 1);
			for (int i = 0; i < steps; i++) {
				double girth = CAVE_GIRTH + Math.sin(i * 0.45) * CAVE_GIRTH_SWELL;
				int r = (int)Math.ceil(girth);
				for (int dx = -r; dx <= r; dx++) {
					for (int dy = -r; dy <= r; dy++) {
						for (int dz = -r; dz <= r; dz++) {
							if (dx * dx + dy * dy * 1.4 + dz * dz > girth * girth) {
								continue;
							}
							pos.set((int)Math.floor(x) + dx, (int)Math.floor(y) + dy, (int)Math.floor(z) + dz);
							if (body(level.getBlockState(pos))) {
								level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
								cut = true;
							}
						}
					}
				}
				yaw += (random.nextDouble() - 0.5) * 0.7;
				pitch = Math.max(-0.5, Math.min(0.5, pitch + (random.nextDouble() - 0.5) * 0.3));
				x += Math.cos(yaw) * Math.cos(pitch) * 1.5;
				y += Math.sin(pitch) * 1.5;
				z += Math.sin(yaw) * Math.cos(pitch) * 1.5;
				if (Math.abs(x - start.getX()) > CAVE_WANDER || Math.abs(z - start.getZ()) > CAVE_WANDER) {
					yaw += Math.PI;
				}
				if (i % 3 == 0) {
					pos.set((int)Math.floor(x), (int)Math.floor(y) - Math.max(1, (int)Math.floor(girth)), (int)Math.floor(z));
					if (level.getBlockState(pos.above()).isAir() && body(level.getBlockState(pos))) {
						if (gassy) {
							level.setBlock(pos, EdenCaves.GAS_VENT.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (random.nextInt(3) == 0) {
							level.setBlock(pos.above(), Eden.GRAVILITE_SHARDS.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
		return cut;
	}
}
