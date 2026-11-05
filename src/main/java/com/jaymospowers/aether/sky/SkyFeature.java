package com.jaymospowers.aether.sky;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.AetherBlocks;
import com.jaymospowers.aether.AetherFeatures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.96) The Aether sky's worldgen (seven-mods plan, patch 10), one feature type with a {@code kind}:
 * <ul>
 * <li>{@code updraft_vent}: a vent set into the ground at the origin (the air over an island top), breathing its column up.</li>
 * <li>{@code upfall}: a column of water pouring up into an island's underside, found looking up a random column.</li>
 * <li>{@code hanging_roots}: cave vines, hanging roots and spore blossoms trailing from the undersides (Hanging Roots).</li>
 * <li>{@code mist_sheet}: a thin, wide sheet of cold aercloud lying in the open sky (Mistsea).</li>
 * <li>{@code rust_wreck}: a heap of weathered copper and chain round a bent rod, on the ground at the origin (Rustworks).</li>
 * <li>{@code sky_arena}: the sky warden's arena, one in a {@link #ARENA_CELL}-block square of the world, far from 0, 0.</li>
 * <li>{@code watched_vault}: a small holystone vault in the Hestveil, its chest and crystals watched over by a vault heart.</li>
 * </ul>
 */
public record SkyFeature(String kind) implements Feature {
	public static final MapCodec<SkyFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.STRING.fieldOf("kind").forGetter(SkyFeature::kind)).apply(i, SkyFeature::new));

	/** Undersides are looked for between these heights (the lowland islands' bellies). */
	static final int UNDER_FROM = 12, UNDER_TO = 120;
	/** One arena in each square this many blocks across, none nearer 0, 0 than {@link #ARENA_FAR}. */
	static final int ARENA_CELL = 1280, ARENA_FAR = 900;
	/** The arena floor: this far over the ground under its vent, and kept between these heights. */
	static final int ARENA_RISE = 80, ARENA_MIN_Y = 200, ARENA_MAX_Y = 330;
	static final ResourceKey<LootTable> VAULT_LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosPowers.id("chests/watched_vault"));
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	@Override
	public MapCodec<SkyFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		return switch (this.kind) {
			case "updraft_vent" -> vent(level, origin);
			case "upfall" -> upfall(level, random, origin);
			case "hanging_roots" -> roots(level, random, origin);
			case "mist_sheet" -> mist(level, random, origin);
			case "rust_wreck" -> wreck(level, random, origin);
			case "sky_arena" -> arena(level, random, origin);
			case "watched_vault" -> vault(level, random, origin);
			default -> false;
		};
	}

	static BlockState mc(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	static boolean isIsland(BlockState s) {
		return s.is(AetherBlocks.HOLYSTONE) || s.is(AetherBlocks.AETHER_DIRT) || s.is(AetherBlocks.UNDERSHALE) || s.is(AetherBlocks.MOSSY_HOLYSTONE)
			|| s.is(AetherBlocks.FERROSITE);
	}

	/** The first underside looking up the column (air with island right over it), or -1. */
	static int underside(WorldGenLevel level, int x, int z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int y = Math.max(level.getMinY() + 1, UNDER_FROM); y < UNDER_TO; y++) {
			at.set(x, y, z);
			if (level.getBlockState(at).isAir() && isIsland(level.getBlockState(at.above()))) {
				return y;
			}
		}
		return -1;
	}

	// ---- vents ----

	private static boolean vent(WorldGenLevel level, BlockPos origin) {
		BlockPos ground = origin.below();
		if (!AetherFeatures.canWrite(level, ground) || !level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
			return false;
		}
		AetherFeatures.put(level, ground, SkyBlocks.UPDRAFT_VENT.defaultBlockState());
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos rim = ground.relative(d);
			if (AetherFeatures.canWrite(level, rim) && level.getBlockState(rim).isFaceSturdy(level, rim, Direction.UP)) {
				AetherFeatures.put(level, rim, AetherBlocks.MOSSY_HOLYSTONE.defaultBlockState());
			}
		}
		UpdraftVentBlock.fill(level, ground, FLAGS);
		return true;
	}

	// ---- upfalls ----

	private static boolean upfall(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int x = (origin.getX() & ~15) + 4 + random.nextInt(8), z = (origin.getZ() & ~15) + 4 + random.nextInt(8);
		int top = underside(level, x, z);
		if (top < 0) {
			return false;
		}
		int length = 10 + random.nextInt(16);
		BlockState water = SkyBlocks.UPFALL.defaultBlockState();
		boolean placed = false;
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				// each strand hangs from its own underside, as far down as there is air
				int y = top;
				BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x + dx, y, z + dz);
				if (!level.getBlockState(at).isAir() || !isIsland(level.getBlockState(at.above()))) {
					continue;
				}
				for (int i = 0; i < length - random.nextInt(3) && level.getBlockState(at).isAir(); i++) {
					placed |= AetherFeatures.put(level, at, water);
					at.move(0, -1, 0);
				}
			}
		}
		return placed;
	}

	// ---- hanging roots ----

	private static boolean roots(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int cx = origin.getX() & ~15, cz = origin.getZ() & ~15;
		boolean placed = false;
		for (int i = 0; i < 40; i++) {
			int x = cx + random.nextInt(16), z = cz + random.nextInt(16);
			int y = underside(level, x, z);
			if (y < 0) {
				continue;
			}
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, y, z);
			int pick = random.nextInt(10);
			if (pick == 0) {
				placed |= AetherFeatures.put(level, at, Blocks.SPORE_BLOSSOM.defaultBlockState());
			} else if (pick < 4) {
				placed |= AetherFeatures.put(level, at, Blocks.HANGING_ROOTS.defaultBlockState());
			} else {
				int length = 2 + random.nextInt(8);
				for (int k = 0; k < length && AetherFeatures.isAir(level, at); k++) {
					boolean tip = k == length - 1 || !level.getBlockState(at.below()).isAir();
					Block vine = tip ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT;
					AetherFeatures.put(level, at, vine.defaultBlockState().setValue(CaveVines.BERRIES, random.nextInt(5) == 0));
					placed = true;
					if (tip) {
						break;
					}
					at.move(0, -1, 0);
				}
			}
		}
		return placed;
	}

	// ---- mist ----

	private static boolean mist(WorldGenLevel level, RandomSource random, BlockPos origin) {
		BlockState cloud = AetherBlocks.COLD_AERCLOUD.defaultBlockState();
		double rx = 4.0 + random.nextInt(6), rz = 4.0 + random.nextInt(6);
		boolean placed = false;
		for (int dx = (int) -rx; dx <= rx; dx++) {
			for (int dz = (int) -rz; dz <= rz; dz++) {
				double f = dx * dx / (rx * rx) + dz * dz / (rz * rz);
				if (f > 1.0 || f > 0.7 && random.nextInt(3) == 0) {
					continue;
				}
				BlockPos at = origin.offset(dx, 0, dz);
				if (AetherFeatures.isAir(level, at)) {
					placed |= AetherFeatures.put(level, at, cloud);
				}
			}
		}
		return placed;
	}

	// ---- rust wrecks ----

	private static boolean wreck(WorldGenLevel level, RandomSource random, BlockPos origin) {
		if (!level.getBlockState(origin).isFaceSturdy(level, origin, Direction.UP)) {
			return false;
		}
		BlockState[] heap = {mc("weathered_copper"), mc("oxidized_copper"), mc("exposed_copper"), mc("oxidized_cut_copper"), mc("weathered_cut_copper"),
			mc("oxidized_copper_grate"), mc("weathered_copper_grate"), AetherBlocks.FERROSITE.defaultBlockState()};
		double r = 2.0 + random.nextDouble() * 1.5;
		int reach = (int) Math.ceil(r);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				for (int dy = 0; dy <= reach; dy++) {
					double f = (dx * dx + dz * dz) / (r * r) + dy * dy / (r * r * 0.5);
					if (f > 1.0 || f > 0.6 && random.nextInt(3) == 0) {
						continue;
					}
					BlockPos at = origin.offset(dx, dy, dz);
					if (AetherFeatures.isFree(level, at) || dy == 0) {
						AetherFeatures.put(level, at, heap[random.nextInt(heap.length)]);
					}
				}
			}
		}
		// a bent spar of rod and chain sticking out of the heap
		BlockPos.MutableBlockPos at = origin.mutable().move(0, reach + 1, 0);
		BlockState rod = mc("oxidized_lightning_rod");
		for (int i = 0; i < 2 + random.nextInt(3) && AetherFeatures.isAir(level, at); i++) {
			AetherFeatures.put(level, at, rod);
			at.move(0, 1, 0);
		}
		Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos chainAt = origin.relative(side, reach + 1).above(2);
		for (int i = 0; i < 3 && AetherFeatures.isAir(level, chainAt); i++) {
			AetherFeatures.put(level, chainAt, mc("oxidized_copper_chain"));
			chainAt = chainAt.below();
		}
		return true;
	}

	// ---- the sky warden's arena ----

	/** Whether this chunk is its square's arena chunk. */
	static boolean arenaChunk(long seed, int chunkX, int chunkZ) {
		int per = ARENA_CELL / 16;
		int cellX = Math.floorDiv(chunkX, per), cellZ = Math.floorDiv(chunkZ, per);
		long h = seed ^ 0x5A7EL ^ cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		int margin = 4;
		int span = per - 2 * margin;
		int ax = cellX * per + margin + (int) Math.floorMod(h, (long) span);
		int az = cellZ * per + margin + (int) Math.floorMod(h >>> 20, (long) span);
		if ((long) ax * ax + (long) az * az < (long) (ARENA_FAR / 16) * (ARENA_FAR / 16)) {
			return false;
		}
		return chunkX == ax && chunkZ == az;
	}

	private static boolean arena(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int chunkX = origin.getX() >> 4, chunkZ = origin.getZ() >> 4;
		if (!arenaChunk(level.getSeed(), chunkX, chunkZ)) {
			return false;
		}
		int cx = (chunkX << 4) + 8, cz = (chunkZ << 4) + 8;
		int r = SkyWarden.ARENA_RADIUS, h = SkyWarden.ARENA_HEIGHT;
		// its vent stands beside the floor, on whatever ground is under it
		int vx = cx + r + 3, vz = cz;
		int ground = -1;
		for (int y = ARENA_MAX_Y - 20; y > level.getMinY() + 4; y--) {
			if (isIsland(level.getBlockState(new BlockPos(vx, y, vz))) || level.getBlockState(new BlockPos(vx, y, vz)).is(AetherBlocks.AETHER_GRASS_BLOCK)) {
				ground = y;
				break;
			}
		}
		int floor = Math.max(ARENA_MIN_Y, Math.min(ARENA_MAX_Y, (ground < 0 ? 120 : ground) + ARENA_RISE));
		BlockState bricks = AetherBlocks.HOLYSTONE_BRICKS.defaultBlockState(), stone = AetherBlocks.HOLYSTONE.defaultBlockState();
		BlockState light = AetherBlocks.ZANITE_BLOCK.defaultBlockState(), glow = Blocks.GLOWSTONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r + 0.5) {
					continue;
				}
				int x = cx + dx, z = cz + dz;
				// clear the room, then floor, underside and ceiling
				for (int y = floor - 2; y <= floor + h; y++) {
					AetherFeatures.put(level, new BlockPos(x, y, z), air);
				}
				boolean ring = d > r - 1.0;
				AetherFeatures.put(level, new BlockPos(x, floor, z), (dx == 0 && dz == 0) || (Math.abs(dx) == Math.abs(dz) && d < 4) ? light : bricks);
				AetherFeatures.put(level, new BlockPos(x, floor - 1, z), stone);
				if (d < r - 3.0) {
					AetherFeatures.put(level, new BlockPos(x, floor - 2, z), stone);
				}
				AetherFeatures.put(level, new BlockPos(x, floor + h, z), (dx % 4 == 0 && dz % 4 == 0) ? glow : bricks);
				if (ring && random.nextInt(4) == 0) {
					AetherFeatures.put(level, new BlockPos(x, floor + 1, z), bricks);
				}
			}
		}
		// six pillars hold the ceiling
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3.0;
			int px = cx + (int) Math.round(Math.cos(a) * (r - 2)), pz = cz + (int) Math.round(Math.sin(a) * (r - 2));
			for (int y = floor + 1; y < floor + h; y++) {
				AetherFeatures.put(level, new BlockPos(px, y, pz), AetherBlocks.HOLYSTONE_PILLAR.defaultBlockState());
			}
		}
		// the way up: a vent on the ground beside it, its column cut clear to the floor's edge
		if (ground > 0) {
			BlockPos vent = new BlockPos(vx, ground, vz);
			AetherFeatures.put(level, vent, SkyBlocks.UPDRAFT_VENT.defaultBlockState());
			for (int y = ground + 1; y <= floor + 2; y++) {
				AetherFeatures.put(level, new BlockPos(vx, y, vz), SkyBlocks.UPDRAFT.defaultBlockState());
			}
			// a landing to step off onto
			for (int dx = -2; dx <= -1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					AetherFeatures.put(level, new BlockPos(vx + dx, floor, vz + dz), bricks);
				}
			}
		}
		if (SkyMobs.SKY_WARDEN != null) {
			SkyWarden warden = SkyMobs.SKY_WARDEN.create(level.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
			if (warden != null) {
				warden.snapTo(cx + 0.5, floor + 1, cz + 0.5, random.nextFloat() * 360.0F, 0.0F);
				warden.setHome(new Vec3(cx + 0.5, floor + 1, cz + 0.5));
				warden.setPersistenceRequired();
				level.addFreshEntity(warden);
			}
		}
		return true;
	}

	// ---- the watched vault ----

	private static boolean vault(WorldGenLevel level, RandomSource random, BlockPos origin) {
		int half = VaultHeartEntity.HALF, high = VaultHeartEntity.HIGH;
		int cx = (origin.getX() & ~15) + 8, cz = (origin.getZ() & ~15) + 8, fy = origin.getY();
		// only set into rock, never hung in the open
		if (level.getBlockState(new BlockPos(cx, fy - 1, cz)).isAir() || level.getBlockState(new BlockPos(cx, fy + high + 2, cz)).isAir()) {
			return false;
		}
		BlockState bricks = AetherBlocks.HOLYSTONE_BRICKS.defaultBlockState(), faded = AetherBlocks.FADED_HOLYSTONE_BRICKS.defaultBlockState();
		for (int dx = -half - 1; dx <= half + 1; dx++) {
			for (int dz = -half - 1; dz <= half + 1; dz++) {
				for (int dy = 0; dy <= high + 1; dy++) {
					BlockPos at = new BlockPos(cx + dx, fy + dy, cz + dz);
					boolean wall = Math.abs(dx) == half + 1 || Math.abs(dz) == half + 1 || dy == 0 || dy == high + 1;
					AetherFeatures.put(level, at, wall ? (random.nextInt(4) == 0 ? faded : bricks) : Blocks.AIR.defaultBlockState());
				}
			}
		}
		// a way in: a gap in one wall
		Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		for (int dy = 1; dy <= 2; dy++) {
			AetherFeatures.put(level, new BlockPos(cx, fy + dy, cz).relative(door, half + 1), Blocks.AIR.defaultBlockState());
			BlockPos outside = new BlockPos(cx, fy + dy, cz).relative(door, half + 2);
			if (!level.getBlockState(outside).isAir()) {
				AetherFeatures.put(level, outside, Blocks.AIR.defaultBlockState());
			}
		}
		AetherFeatures.put(level, new BlockPos(cx, fy, cz), SkyBlocks.VAULT_HEART.defaultBlockState());
		BlockPos chest = new BlockPos(cx + random.nextInt(5) - 2, fy + 1, cz + random.nextInt(5) - 2);
		if (chest.getX() == cx && chest.getZ() == cz) {
			chest = chest.east();
		}
		if (AetherFeatures.put(level, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)))) {
			RandomizableContainer.setBlockEntityLootTable(level, random, chest, VAULT_LOOT);
		}
		int crystals = 4 + random.nextInt(3);
		for (int i = 0; i < crystals * 3 && crystals > 0; i++) {
			BlockPos at = new BlockPos(cx + random.nextInt(2 * half + 1) - half, fy + 1, cz + random.nextInt(2 * half + 1) - half);
			if (level.getBlockState(at).isAir() && !(at.getX() == cx && at.getZ() == cz)) {
				AetherFeatures.put(level, at, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
				crystals--;
			}
		}
		return true;
	}
}
