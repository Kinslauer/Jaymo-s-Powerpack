package com.jaymoskingdoms;

import java.util.function.ToDoubleFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Psithurism (mod id {@value #MOD}, by the MModding Team; code PolyForm Shield, art all rights reserved: shipped as the
 * untouched jar, nothing of it copied here), when it is in the pack. Kingdoms never compiles against it: this class only
 * asks the block registry for its ids, so without it every call answers "nothing" and the Blossom land builds as before.
 * <ul>
 * <li><b>Houses.</b> A Blossom house's paper windows, floor, timber and veranda deck are Psithurism's paper wall block,
 * small tatami, dark cherry logs and planks ({@link #paper}, {@link #tatami}, {@link #timber}, {@link #beam}, {@link #deck}).</li>
 * <li><b>Onsen.</b> Now and then a village has a hot spring pool beyond its fields ({@link #onsen}), filled with Psithurism's
 * onsen water. In winter the snow monkeys come to it (SnowMonkey), and a soak in it lifts the winter cold off the body
 * ({@link #warmth}: Kitchen's body heat, by reflection, with the pack's own seasons).</li>
 * </ul>
 */
public final class PsithurismCompat {
	static final String MOD = "psithurism";
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);

	/** A village has a hot spring this share of the time, between these distances from its bell (blocks), on ground this level. */
	static final float ONSEN_CHANCE = 0.6F;
	static final int ONSEN_MIN = 40, ONSEN_SPREAD = 24, ONSEN_SLOPE = 3, ONSEN_TRIES = 4;
	/** The pool's inside (blocks), and how high the ground is cleared round it. */
	static final int POOL_WIDTH = 5, POOL_DEPTH = 4, ONSEN_CLEAR = 6;
	/** The body heat a soak adds in the cold (Kitchen's scale: chilly is -35, comfort 0), and below what base temperature a land counts as cold. */
	static final float SOAK_WARMTH = 55.0F, COLD_BIOME = 0.15F;

	private PsithurismCompat() {
	}

	/** One of its blocks by path, or null when it isn't loaded (or has no such block). */
	static @Nullable Block block(String path) {
		if (!LOADED) {
			return null;
		}
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(MOD, path));
		return b == Blocks.AIR ? null : b;
	}

	private static BlockState of(String path, BlockState fallback) {
		Block b = block(path);
		return b == null ? fallback : b.defaultBlockState();
	}

	/** The paper window: its paper wall block, else {@code fallback}. */
	static BlockState paper(BlockState fallback) {
		return of("paper_wall_block", fallback);
	}

	/** The floor mat: its small tatami, else {@code fallback}. */
	static BlockState tatami(BlockState fallback) {
		return of("small_tatami", fallback);
	}

	/** The house's posts: stripped dark cherry. */
	static BlockState timber(BlockState fallback) {
		return of("stripped_dark_cherry_log", fallback);
	}

	/** The house's beams: dark cherry log. */
	static BlockState beam(BlockState fallback) {
		return of("dark_cherry_log", fallback);
	}

	/** The veranda's deck: dark cherry planks. */
	static BlockState deck(BlockState fallback) {
		return of("dark_cherry_planks", fallback);
	}

	/** Whether {@code state} is its onsen water. */
	public static boolean onsenWater(BlockState state) {
		Block b = block("onsen_water");
		return b != null && state.is(b);
	}

	/**
	 * Maybe a hot spring beyond {@code bell}'s fields, on level ground nobody else's: a rim of its ashino stone bricks round a
	 * pool a block deep of onsen water, a stone lantern at one corner. Nothing without Psithurism or a place for it.
	 */
	static void onsen(ServerLevel level, Faction village, BlockPos bell, RandomSource random) {
		Block water = block("onsen_water");
		if (water == null || random.nextFloat() >= ONSEN_CHANCE) {
			return;
		}
		BlockState rim = of("ashino_stone_bricks", Blocks.STONE_BRICKS.defaultBlockState());
		for (int tries = 0; tries < ONSEN_TRIES; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = ONSEN_MIN + random.nextInt(ONSEN_SPREAD);
			BlockPos at = bell.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
			int floor = IllagerBuildings.floor(level, at, ONSEN_SLOPE, POOL_WIDTH);
			Faction owner = Factions.at(level.getServer(), level, at);
			if (floor == Integer.MIN_VALUE || owner != null && owner.id() != village.id()) {
				continue;
			}
			BlockPos middle = new BlockPos(at.getX(), floor, at.getZ());
			CampBuildings.level(level, middle, POOL_WIDTH, ONSEN_CLEAR);
			int hx = POOL_WIDTH / 2 + 1, hz = POOL_DEPTH / 2 + 1;
			for (int dx = -hx; dx <= hx; dx++) {
				for (int dz = -hz; dz <= hz; dz++) {
					boolean edge = Math.abs(dx) == hx || Math.abs(dz) == hz;
					// the rim stands level with the ground; the pool is dug one block down into a stone floor
					level.setBlock(middle.offset(dx, -1, dz), edge ? rim : water.defaultBlockState(), Block.UPDATE_ALL);
					level.setBlock(middle.offset(dx, -2, dz), rim, Block.UPDATE_ALL);
				}
			}
			level.setBlock(middle.offset(hx, 0, hz), of("stone_lantern", Blocks.STONE_BRICK_WALL.defaultBlockState()), Block.UPDATE_ALL);
			JaymosKingdoms.LOGGER.info("A hot spring by {} at {}", village.name(), middle);
			return;
		}
	}

	/** Winter's cold, or a cold land: when a soak in onsen water counts for warmth. */
	private static boolean cold(ServerLevel level, BlockPos at) {
		return "winter".equals(KitchenCompat.season(level)) || level.getBiome(at).value().getBaseTemperature() <= COLD_BIOME;
	}

	/** Body heat from a soak: {@link #SOAK_WARMTH} while the player is in onsen water in the cold, else nothing. */
	static double warmth(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		return onsenWater(level.getBlockState(at)) && cold(level, at) ? SOAK_WARMTH : 0.0;
	}

	static void register() {
		if (!LOADED) {
			return;
		}
		try {
			Class.forName("com.jaymoskitchen.climate.BodyHeat").getMethod("addWeather", ToDoubleFunction.class)
				.invoke(null, (ToDoubleFunction<ServerPlayer>) PsithurismCompat::warmth);
		} catch (ReflectiveOperationException e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old (or missing) for a soak in an onsen to lift the cold: {}", e.toString());
		}
	}
}
