package com.jaymoskingdoms;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The dead player's body as Jaymo's Kitchen keeps it, reached by name (the death and land rites of Andy's picks E4 and E5):
 * whose it is, whether it is kept fresh, putting it in the ground and taking it out, and the hotbar it lies with. Without the
 * Kitchen every question answers no.
 */
public final class DeadBodies {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskitchen");
	private static final @Nullable Class<?> CORPSE, KEEPING, BURIAL, FRESHNESS;
	private static final @Nullable Method IS_PLAYER, OWNER, KEPT, KEEP, LOWER, RAISE, TODAY, PRESERVE, HOTBAR;

	static {
		Class<?> corpse = null, keeping = null, burial = null, freshness = null;
		Method isPlayer = null, owner = null, kept = null, keep = null, lower = null, raise = null, today = null, preserve = null, hotbar = null;
		if (LOADED) {
			try {
				corpse = Class.forName("com.jaymoskitchen.corpse.CorpseEntity");
				keeping = Class.forName("com.jaymoskitchen.corpse.Keeping");
				burial = Class.forName("com.jaymoskitchen.corpse.Burial");
				freshness = Class.forName("com.jaymoskitchen.fresh.Freshness");
				isPlayer = corpse.getMethod("isPlayerCorpse");
				owner = corpse.getMethod("owner");
				hotbar = corpse.getMethod("takeHotbar");
				kept = keeping.getMethod("kept", Entity.class, Level.class);
				keep = keeping.getMethod("keep", Entity.class, long.class);
				lower = burial.getMethod("lower", corpse);
				raise = burial.getMethod("raise", ServerLevel.class, CompoundTag.class, double.class, double.class, double.class);
				today = freshness.getMethod("today", Level.class);
				preserve = freshness.getMethod("preserve", ItemStack.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for keeping and burying bodies: {}", e.toString());
				corpse = null;
			}
		}
		CORPSE = corpse;
		KEEPING = keeping;
		BURIAL = burial;
		FRESHNESS = freshness;
		IS_PLAYER = isPlayer;
		OWNER = owner;
		KEPT = kept;
		KEEP = keep;
		LOWER = lower;
		RAISE = raise;
		TODAY = today;
		PRESERVE = preserve;
		HOTBAR = hotbar;
	}

	private DeadBodies() {
	}

	/** Whether the Kitchen's bodies are there at all. */
	public static boolean present() {
		return CORPSE != null;
	}

	/** Whether {@code e} is a body. */
	public static boolean isBody(Entity e) {
		return CORPSE != null && CORPSE.isInstance(e) && !e.isRemoved();
	}

	/** Whether {@code e} is a dead player's body. */
	public static boolean isPlayerBody(Entity e) {
		return isBody(e) && Boolean.TRUE.equals(call(IS_PLAYER, e));
	}

	/** Whose body {@code e} is. */
	@SuppressWarnings("unchecked")
	public static Optional<UUID> owner(Entity e) {
		return isBody(e) && call(OWNER, e) instanceof Optional<?> o ? (Optional<UUID>) o : Optional.empty();
	}

	/** Whether {@code body} is kept fresh just now. */
	public static boolean kept(Entity body) {
		return isBody(body) && Boolean.TRUE.equals(call(KEPT, body, body.level()));
	}

	/** Keeps {@code body} fresh through day {@code lastDay} of {@link #today}'s count. */
	public static void keep(Entity body, long lastDay) {
		if (isBody(body)) {
			call(KEEP, body, lastDay);
		}
	}

	/** The day count the Kitchen keeps its food by. */
	public static long today(Level level) {
		return call(TODAY, null, level) instanceof Long l ? l : level.getGameTime() / 24000L;
	}

	/** Wraps {@code food} so it keeps. */
	public static void preserve(ItemStack food) {
		call(PRESERVE, null, food);
	}

	/** The body, saved as data and gone from the world; null if it could not be. */
	public static @Nullable CompoundTag lower(Entity body) {
		return isBody(body) && call(LOWER, null, body) instanceof CompoundTag tag ? tag : null;
	}

	/** The body saved in {@code data}, lying at {@code x}, {@code y}, {@code z}. */
	public static @Nullable Entity raise(ServerLevel level, CompoundTag data, double x, double y, double z) {
		return call(RAISE, null, level, data, x, y, z) instanceof Entity e ? e : null;
	}

	/** The hotbar that lay with the body, taken off it. */
	@SuppressWarnings("unchecked")
	public static List<ItemStack> takeHotbar(Entity body) {
		return isBody(body) && call(HOTBAR, body) instanceof List<?> l ? (List<ItemStack>) l : List.of();
	}

	private static @Nullable Object call(@Nullable Method method, @Nullable Object on, Object... args) {
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(on, args);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
