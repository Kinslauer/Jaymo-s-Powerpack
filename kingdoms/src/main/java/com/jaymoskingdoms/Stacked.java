package com.jaymoskingdoms;

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

/**
 * (0.7.528) One Dimension worlds (One Dimension + CustomChunks, CodxIO; shipped unchanged): the Aether, the Overworld and
 * the Nether stacked as height bands of one level, the Overworld's, top to bottom. In such a world every band answers
 * {@code level.dimension() == OVERWORLD}, and {@code server.getLevel(NETHER)} is still the old, separate Nether that
 * nobody walks in any more. So code that asks "which realm" asks here, by height; code that goes to a realm gets the
 * level that holds it ({@link #level}) and moves that realm's own heights by {@link #shift}.
 * <p>
 * Read through CustomChunks' public API by reflection, so nothing changes (and nothing is loaded) without it. Every answer
 * falls back to the plain vanilla one: an ordinary world, or CustomChunks missing, behaves exactly as before. The same as
 * Jaymo's Powers' {@code compat.Stacked} (which also keeps ground heights to the Overworld's band, for every mod).
 */
public final class Stacked {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("customchunks");
	private static boolean ready;
	private static Method dimensionAt, stacksOf, bandOf, bandShift, bandMin, bandMax, enter, leave;

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
				stacksOf = stacks.getMethod("of", ServerLevel.class);
				bandOf = stack.getMethod("bandOf", ResourceKey.class);
				bandShift = band.getMethod("shift");
				bandMin = band.getMethod("minY");
				bandMax = band.getMethod("maxYExclusive");
				Class<?> context = Class.forName("codx.customchunks.BandContext");
				enter = context.getMethod("enter", Level.class, int.class);
				leave = context.getMethod("leave", Object.class);
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
