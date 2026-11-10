package com.jaymospowers.aether.world;

import com.jaymospowers.compat.Stacked;
import com.jaymospowers.power.zfighter.ZFighterKingdoms;
import com.jaymospowers.power.wind.aether.WindAether;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpringFeature;

/**
 * (0.20.45, Andy) The Aether's water springs, with the sky waterfalls kept for a few far-off places.
 * <p>
 * A spring in an island's side pours off the edge. In the Aether on its own that water fell away into nothing; in a One
 * Dimension world ({@link Stacked}) the Overworld is right under the Aether, so it fell the whole way down and stood as a
 * column of water from the sky, one of them right at spawn, giving the islands away before anyone had looked up. So in a
 * stacked world a spring whose water would run out of the bottom of the Aether is not placed, unless it lies in a
 * <b>heavenfall</b>: one chunk in some of the {@link #CELL}-block squares of the world, never within {@link #FAR_FROM_SPAWN}
 * blocks of the world spawn (or of 0, 0). There the springs are left alone and the waterfall from heaven stays.
 * <p>
 * (0.21.4, Andy: "remove skyfalls until dread") Not even a heavenfall before then: its springs are placed only while a player
 * on the server has reached the Kingdoms world tier Dread ({@link #DREAD_TIER}). A heavenfall chunk made before that has
 * none, for good; they are far from spawn, so nearly all are made later.
 * <p>
 * Springs that fall onto a lower island, or into a cave, are untouched, and so is the Aether as a dimension of its own.
 * Only new chunks change; water already flowing in a world stays.
 */
public record SkySpringFeature(SpringFeature spring) implements Feature {
	/** The same fields as a vanilla spring, so the configured feature is the old one with a new type. */
	public static final MapCodec<SkySpringFeature> CODEC = SpringFeature.CODEC.xmap(SkySpringFeature::new, SkySpringFeature::spring);

	/** The world is cut into squares this many blocks across; each may hold one heavenfall. */
	public static final int CELL = 2048;
	/** One square in this many has one. */
	public static final int ONE_IN = 3;
	/** No heavenfall closer than this to the world spawn or to 0, 0. */
	public static final int FAR_FROM_SPAWN = 3000;
	/** A heavenfall sits at least this many chunks inside its square, so two never meet across an edge. */
	private static final int MARGIN_CHUNKS = 16;
	/** Kingdoms' WorldTiers.Tier.DREAD, by its place in the list (Hearth 0, Borderlands, Wildlands, Dread, Legend). */
	public static final int DREAD_TIER = 3;
	/** Whether a player online has reached Dread; read by worldgen threads, set on the server thread every second or so. */
	private static volatile boolean dreadReached;
	/** Blocks the flow check may look at before it gives up and calls the spring a leak. */
	private static final int BUDGET = 6000;

	/** Called from {@code AetherFeatures.init}: keeps {@link #dreadReached} up to date. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				dreadReached = dreadReached(server);
			}
		});
	}

	private static boolean dreadReached(MinecraftServer server) {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			// no world tiers to wait for
			return true;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ZFighterKingdoms.worldTier(player) >= DREAD_TIER) {
				return true;
			}
		}
		return false;
	}

	@Override
	public MapCodec<SkySpringFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		if (!stackedAether(level)) {
			return spring.place(level, chunkGenerator, random, origin);
		}
		BlockState before = level.getBlockState(origin);
		if (!spring.place(level, chunkGenerator, random, origin)) {
			return false;
		}
		if (!spring.state().isEmpty() && reachesBottom(level, origin) && !heavenfall(level, origin)) {
			level.setBlock(origin, before, 2);
			return false;
		}
		return true;
	}

	/** Whether this is the Aether's band of a stacked Overworld (not the Aether as a dimension of its own). */
	private static boolean stackedAether(WorldGenLevel level) {
		ServerLevel server = level.getLevel();
		return server != null && server.dimension() == Level.OVERWORLD && Stacked.joined(server.getServer(), WindAether.AETHER);
	}

	/**
	 * Whether water from a spring at {@code origin} could run out under the band: a flood of where it may spread (seven
	 * blocks along a floor, any distance straight down, spreading again where it lands), looking for a fall that never lands.
	 * Errs toward yes: unloaded neighbours, or a flood too big to follow, count as a leak.
	 */
	static boolean reachesBottom(WorldGenLevel level, BlockPos origin) {
		int minY = level.getMinY();
		ArrayDeque<long[]> queue = new ArrayDeque<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		queue.add(new long[] {origin.asLong(), 8});
		seen.add(origin.asLong());
		int budget = BUDGET;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		while (!queue.isEmpty()) {
			long[] next = queue.poll();
			if (--budget < 0) {
				return true;
			}
			BlockPos pos = BlockPos.of(next[0]);
			int spread = (int)next[1];
			// straight down first: water over a drop falls rather than spreading
			at.set(pos.getX(), pos.getY() - 1, pos.getZ());
			if (at.getY() < minY) {
				return true;
			}
			if (passable(level, at)) {
				while (at.getY() >= minY && passable(level, at)) {
					if (--budget < 0) {
						return true;
					}
					at.move(Direction.DOWN);
				}
				if (at.getY() < minY) {
					return true;
				}
				long landing = BlockPos.asLong(at.getX(), at.getY() + 1, at.getZ());
				if (seen.add(landing)) {
					queue.add(new long[] {landing, 8});
				}
				continue;
			}
			if (spread <= 1) {
				continue;
			}
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos side = pos.relative(direction);
				if (!level.hasChunk(side.getX() >> 4, side.getZ() >> 4)) {
					return true;
				}
				if (passable(level, side) && seen.add(side.asLong())) {
					queue.add(new long[] {side.asLong(), spread - 1});
				}
			}
		}
		return false;
	}

	/** Where water can run: air, or what it washes away (plants, snow); not solid ground, and not other water. */
	private static boolean passable(WorldGenLevel level, BlockPos pos) {
		if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.isAir() || !state.isSolid() && state.getFluidState().isEmpty();
	}

	/** Whether {@code pos} is in this world's heavenfall chunk for its square (if the square has one, far from spawn). */
	static boolean heavenfall(WorldGenLevel level, BlockPos pos) {
		if (!dreadReached) {
			return false;
		}
		int cellX = Math.floorDiv(pos.getX(), CELL), cellZ = Math.floorDiv(pos.getZ(), CELL);
		long hash = mix(level.getSeed() ^ 0x5EAF_A11L ^ cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xC2B2AE3D27D4EB4FL);
		if (Math.floorMod(hash, ONE_IN) != 0) {
			return false;
		}
		int span = CELL / 16 - 2 * MARGIN_CHUNKS;
		int chunkX = Math.floorDiv(cellX * CELL, 16) + MARGIN_CHUNKS + (int)Math.floorMod(hash >>> 8, span);
		int chunkZ = Math.floorDiv(cellZ * CELL, 16) + MARGIN_CHUNKS + (int)Math.floorMod(hash >>> 32, span);
		if (pos.getX() >> 4 != chunkX || pos.getZ() >> 4 != chunkZ) {
			return false;
		}
		int x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
		BlockPos spawn = level.getLevelData().getRespawnData().pos();
		return far(x, z, 0, 0) && far(x, z, spawn.getX(), spawn.getZ());
	}

	private static boolean far(int x, int z, int fromX, int fromZ) {
		long dx = x - fromX, dz = z - fromZ;
		return dx * dx + dz * dz >= (long)FAR_FROM_SPAWN * FAR_FROM_SPAWN;
	}

	private static long mix(long h) {
		h = (h ^ h >>> 33) * 0xFF51AFD7ED558CCDL;
		h = (h ^ h >>> 33) * 0xC4CEB9FE1A85EC53L;
		return h ^ h >>> 33;
	}
}
