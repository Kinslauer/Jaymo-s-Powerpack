package com.jaymospowers.compat;

import com.jaymospowers.core.Loaded;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * (0.20.30) One Dimension worlds (One Dimension + CustomChunks, CodxIO; shipped unchanged): the Aether, the Overworld and
 * the Nether stacked as height bands of one level, the Overworld's, top to bottom. In such a world every band answers
 * {@code level.dimension() == OVERWORLD}, and {@code server.getLevel(NETHER)} is still the old, separate Nether that
 * nobody walks in any more. So code that asks "which realm" asks here, by height; code that goes to a realm gets the
 * level that holds it ({@link #level}) and moves that realm's own heights by {@link #shift}.
 * <p>
 * Read through CustomChunks' public API by reflection, so nothing changes (and nothing is loaded) without it. Every answer
 * falls back to the plain vanilla one: an ordinary world, or CustomChunks missing, behaves exactly as before.
 */
public final class Stacked {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("customchunks");
	private static boolean ready;
	private static Method dimensionAt, contextDimension, stacksOf, bandOf, bandShift, bandMin, bandMax, enter, leave, surface;
	private static Method bandGenerator, bandRandomState, bandHeight, bandStructures;
	/** CustomChunks' own "searching this band" mark ({@code StackedChunkGenerator.SEARCHING}), or null if it can't be read. */
	private static ThreadLocal<Object> searching;
	/** Set while {@link #surface} runs, so its own chunk reads never come back through the heightmap hook. */
	private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);

	private Stacked() {
	}

	private static boolean api() {
		if (!LOADED) {
			return false;
		}
		if (!ready) {
			ready = true;
			try {
				Class<?> layout = Class.forName("codx.customchunks.StackLayout");
				Class<?> stacks = Class.forName("codx.customchunks.Stacks");
				Class<?> stack = Class.forName("codx.customchunks.Stack");
				Class<?> band = Class.forName("codx.customchunks.Band");
				dimensionAt = layout.getMethod("dimensionAt", Level.class, int.class);
				contextDimension = Class.forName("codx.customchunks.BandContext").getMethod("dimension", Level.class);
				surface = Class.forName("codx.customchunks.gen.BandChunks").getMethod("surface", ChunkAccess.class, int.class, int.class,
					Heightmap.Types.class, int.class, int.class);
				stacksOf = stacks.getMethod("of", ServerLevel.class);
				bandOf = stack.getMethod("bandOf", ResourceKey.class);
				bandShift = band.getMethod("shift");
				bandMin = band.getMethod("minY");
				bandMax = band.getMethod("maxYExclusive");
				Class<?> context = Class.forName("codx.customchunks.BandContext");
				enter = context.getMethod("enter", Level.class, int.class);
				leave = context.getMethod("leave", Object.class);
				bandGenerator = band.getMethod("generator");
				bandRandomState = band.getMethod("randomState");
				bandHeight = band.getMethod("localHeight");
				bandStructures = band.getMethod("structureState");
				try {
					java.lang.reflect.Field mark = Class.forName("codx.customchunks.gen.StackedChunkGenerator").getDeclaredField("SEARCHING");
					mark.setAccessible(true);
					@SuppressWarnings("unchecked")
					ThreadLocal<Object> local = (ThreadLocal<Object>) mark.get(null);
					searching = local;
				} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
					searching = null;
				}
			} catch (ReflectiveOperationException | LinkageError e) {
				dimensionAt = null;
			}
		}
		return dimensionAt != null;
	}

	/** The realm at height y of this level: the band's own dimension in a stacked world, else the level's (client or server). */
	@SuppressWarnings("unchecked")
	public static ResourceKey<Level> realm(Level level, double y) {
		if (api()) {
			try {
				Object key = dimensionAt.invoke(null, level, (int) Math.floor(y));
				if (key != null) {
					return (ResourceKey<Level>) key;
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				// fall through to the plain answer
			}
		}
		return level.dimension();
	}

	/** The realm this entity is in. */
	public static ResourceKey<Level> realm(Entity entity) {
		return realm(entity.level(), entity.getY());
	}

	/** Whether this entity is in that realm (its own dimension, or that band of a stacked world). */
	public static boolean in(Entity entity, ResourceKey<Level> realm) {
		return realm(entity) == realm;
	}

	/** Whether this spot of the level is in that realm. */
	public static boolean at(Level level, BlockPos pos, ResourceKey<Level> realm) {
		return realm(level, pos.getY()) == realm;
	}

	/** The band of the stacked Overworld that holds this realm, or null (no stack, or the realm isn't in it). */
	private static Object band(MinecraftServer server, ResourceKey<Level> realm) {
		if (!api() || server == null) {
			return null;
		}
		try {
			Optional<?> stack = (Optional<?>) stacksOf.invoke(null, server.overworld());
			return stack.isPresent() ? bandOf.invoke(stack.get(), realm) : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static int read(Object band, Method method, int fallback) {
		try {
			return (Integer) method.invoke(band);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return fallback;
		}
	}

	/** Whether this realm is a band of the stacked Overworld (so you reach it by walking, digging or flying, not a jump). */
	public static boolean joined(MinecraftServer server, ResourceKey<Level> realm) {
		return realm != Level.OVERWORLD && band(server, realm) != null;
	}

	/** The level that holds this realm: the stacked Overworld when the realm is one of its bands, else its own (may be null). */
	public static ServerLevel level(MinecraftServer server, ResourceKey<Level> realm) {
		return band(server, realm) != null ? server.overworld() : server.getLevel(realm);
	}

	/** Added to a height in the realm's own terms gives the height in {@link #level}; 0 unless the realm is a band. */
	public static int shift(MinecraftServer server, ResourceKey<Level> realm) {
		Object band = band(server, realm);
		return band == null ? 0 : read(band, bandShift, 0);
	}

	/** The lowest height of this realm in {@link #level}. */
	public static int minY(MinecraftServer server, ResourceKey<Level> realm) {
		Object band = band(server, realm);
		if (band != null) {
			return read(band, bandMin, server.overworld().getMinY());
		}
		ServerLevel level = server.getLevel(realm);
		return level == null ? 0 : level.getMinY();
	}

	/** The highest block height of this realm in {@link #level}. */
	public static int maxY(MinecraftServer server, ResourceKey<Level> realm) {
		Object band = band(server, realm);
		if (band != null) {
			return read(band, bandMax, server.overworld().getMaxY() + 1) - 1;
		}
		ServerLevel level = server.getLevel(realm);
		return level == null ? 0 : level.getMaxY();
	}

	/**
	 * The ground height at (x, z) for {@code Level.getHeight(type, x, z)} in a stacked world (StackedHeightMixin), or null to
	 * leave it to the game. A stacked level's heightmaps see the whole column, so with the Aether's islands over the Overworld
	 * every "top of the ground" would be an island's top. This answers the top of the band being ticked (a mob in the Nether
	 * band gets the Nether's), and outside one (server ticks, commands, our own sweeps) the Overworld's, which is what nearly
	 * every caller means. Server only; the client keeps the game's answer.
	 */
	public static Integer surface(Level level, Heightmap.Types type, int x, int z) {
		if (!api() || level.isClientSide() || !(level instanceof ServerLevel server) || BUSY.get()) {
			return null;
		}
		BUSY.set(true);
		try {
			Optional<?> stack = (Optional<?>) stacksOf.invoke(null, server);
			if (stack.isEmpty()) {
				return null;
			}
			@SuppressWarnings("unchecked")
			ResourceKey<Level> realm = (ResourceKey<Level>) contextDimension.invoke(null, level);
			Object band = realm == null ? null : bandOf.invoke(stack.get(), realm);
			if (band == null) {
				band = bandOf.invoke(stack.get(), Level.OVERWORLD);
			}
			if (band == null) {
				return null;
			}
			if (!Loaded.made(level, x, z)) {
				return null;
			}
			ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
			return (Integer) surface.invoke(null, chunk, read(band, bandMin, level.getMinY()), read(band, bandMax, level.getMaxY() + 1), type, x, z);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		} finally {
			BUSY.set(false);
		}
	}

	/**
	 * How the ground of a realm is made: its generator, the random state it reads, the heights it works in, what to add to
	 * one of its heights to get the height in the level ({@link #shift}), and where its structures may start.
	 */
	public record Ground(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heights, int shift,
		ChunkGeneratorStructureState structures) {
	}

	/**
	 * The Overworld's ground in {@code level}. A stacked level's own generator answers "how high is the ground here" with
	 * the top band that has any (the Cloud Sea's overcast, an Aether island), and its own structure state has no placements
	 * at all. So in a stacked world this is the Overworld band's own, read in its own heights (add {@code shift} to place
	 * anything in the level); anywhere else the level's own, shift 0. The same as Kingdoms' {@code Stacked.overworldGround}.
	 */
	public static Ground overworldGround(ServerLevel level) {
		Object band = level.dimension() == Level.OVERWORLD ? band(level.getServer(), Level.OVERWORLD) : null;
		if (band != null) {
			try {
				return new Ground((ChunkGenerator) bandGenerator.invoke(band), (RandomState) bandRandomState.invoke(band),
					(LevelHeightAccessor) bandHeight.invoke(band), read(band, bandShift, 0), (ChunkGeneratorStructureState) bandStructures.invoke(band));
			} catch (ReflectiveOperationException | RuntimeException e) {
				// fall through to the level's own
			}
		}
		return new Ground(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level, 0,
			level.getChunkSource().getGeneratorState());
	}

	/**
	 * Runs {@code body} the way CustomChunks runs its own structure search (/locate) for the Overworld's band: while it
	 * runs, the level's structure checks are worked out by that band's generator. An ordinary world just runs it.
	 */
	public static <T> T searchingOverworld(ServerLevel level, java.util.function.Supplier<T> body) {
		Object band = level.dimension() == Level.OVERWORLD ? band(level.getServer(), Level.OVERWORLD) : null;
		ThreadLocal<Object> mark = searching;
		if (band == null || mark == null) {
			return body.get();
		}
		Object was = mark.get();
		mark.set(band);
		try {
			return body.get();
		} finally {
			mark.set(was);
		}
	}

	/**
	 * Runs {@code body} as if in the band at height y of this level (CustomChunks' band context), so the game's answers inside
	 * it (ground heights, the sky, the dimension's rules) are that band's: for code that works in a realm outside any entity's
	 * tick, such as a server-tick sweep building something in the Aether. Just runs it in an ordinary world.
	 */
	public static void within(Level level, int y, Runnable body) {
		Object token = null;
		if (api()) {
			try {
				token = enter.invoke(null, level, y);
			} catch (ReflectiveOperationException | RuntimeException e) {
				token = null;
			}
		}
		try {
			body.run();
		} finally {
			if (token != null) {
				try {
					leave.invoke(null, token);
				} catch (ReflectiveOperationException | RuntimeException e) {
					// the band context is CustomChunks' own; nothing more to undo here
				}
			}
		}
	}

	/** The players in this realm: the level's own players, or in a stacked world those standing in its band. */
	public static List<ServerPlayer> players(MinecraftServer server, ResourceKey<Level> realm) {
		ServerLevel level = level(server, realm);
		if (level == null) {
			return List.of();
		}
		if (!joined(server, realm) && realm != Level.OVERWORLD) {
			return level.players();
		}
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (in(player, realm)) {
				out.add(player);
			}
		}
		return out;
	}
}
