package com.jaymospowers.power.wind;

import com.jaymospowers.core.Fate;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * The weather governs the wind: a factor that scales flight speed, gusts, Tailwind and the other abilities. Computed the
 * same way on both sides (the client for the local flight and HUD, the server for the abilities), and eased toward its
 * target over a few seconds so it never snaps.
 */
public final class WindWeather {
	/** Clear sky by day: the air is still. */
	public static final float CLEAR_DAY = 0.6F;
	/** Clear sky by night. */
	public static final float CLEAR_NIGHT = 0.85F;
	/** No sky overhead (underground, under a roof). */
	public static final float SHELTERED = 0.85F;
	/** The Nether, the End, and anywhere else without weather. */
	public static final float OTHERWORLD = 0.85F;
	/** Rain or snow falling on you. */
	public static final float RAIN = 1.0F;
	/** A thunderstorm overhead: the maximum. Also what a corrupted (Stormbound) power always counts as. */
	public static final float STORM = 1.3F;
	/** How far the eased factor moves toward its target per tick (0.6 → 1.3 takes 3.5 s). */
	public static final float EASE_PER_TICK = 0.01F;

	/** What the sky is doing, for the HUD glyph. */
	public enum Kind {
		CLEAR_DAY(WindWeather.CLEAR_DAY),
		CLEAR_NIGHT(WindWeather.CLEAR_NIGHT),
		SHELTERED(WindWeather.SHELTERED),
		OTHERWORLD(WindWeather.OTHERWORLD),
		RAIN(WindWeather.RAIN),
		STORM(WindWeather.STORM),
		/** Stormbound: the storm is inside you, whatever the sky says. */
		STORMBOUND(WindWeather.STORM);

		public final float factor;

		Kind(float factor) {
			this.factor = factor;
		}
	}

	/** Eased factor per player, one map per side (singleplayer runs both in one JVM). */
	private static final Map<UUID, Float> CLIENT = new HashMap<>();
	private static final Map<UUID, Float> SERVER = new HashMap<>();

	private WindWeather() {
	}

	/** The sky as it is, ignoring corruption. */
	public static Kind sky(Player player) {
		Level level = player.level();
		if (!level.canHaveWeather()) {
			return Kind.OTHERWORLD;
		}
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		if (!level.canSeeSky(eye)) {
			return Kind.SHELTERED;
		}
		// rain or snow actually falling here (a dry biome in a storm counts as clear)
		if (level.precipitationAt(eye) != Biome.Precipitation.NONE) {
			return level.isThundering() || kingdomsThunder(level, eye) ? Kind.STORM : Kind.RAIN;
		}
		return level.isBrightOutside() ? Kind.CLEAR_DAY : Kind.CLEAR_NIGHT;
	}

	/** What the factor is heading toward: the sky, or the storm itself for a corrupted power. */
	public static Kind kind(Player player) {
		return Fate.isCorrupted(player) ? Kind.STORMBOUND : sky(player);
	}

	/**
	 * (0.9.26) Jaymo's Kingdoms' storm cells bring thunder to one patch of sky; under one, it's a storm. Looked up by
	 * name, so it's simply never true without Kingdoms.
	 */
	private static final MethodHandle KINGDOMS_THUNDER = findKingdomsThunder();

	private static MethodHandle findKingdomsThunder() {
		try {
			Class<?> storms = Class.forName("com.jaymoskingdoms.disasters.Storms");
			return MethodHandles.publicLookup().findStatic(storms, "thunderAt", MethodType.methodType(boolean.class, Level.class, double.class, double.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	private static boolean kingdomsThunder(Level level, BlockPos pos) {
		if (KINGDOMS_THUNDER == null) {
			return false;
		}
		try {
			return (boolean)KINGDOMS_THUNDER.invokeExact(level, pos.getX() + 0.5, pos.getZ() + 0.5);
		} catch (Throwable t) {
			return false;
		}
	}

	/** True while a real thunderstorm is overhead (corruption builds only then). */
	public static boolean stormOverhead(Player player) {
		return sky(player) == Kind.STORM;
	}

	/** Steps the eased factor toward its target. Call once per tick per side. Returns the new value. */
	public static float tick(Player player) {
		Map<UUID, Float> map = map(player);
		float target = kind(player).factor;
		Float current = map.get(player.getUUID());
		float value = current == null ? target : approach(current, target);
		map.put(player.getUUID(), value);
		return value;
	}

	/** The eased weather factor (0.6–1.3) for this player, on this side. */
	public static float factor(Player player) {
		Float value = map(player).get(player.getUUID());
		return value == null ? kind(player).factor : value;
	}

	/** Forgets a player (disconnect). */
	public static void forget(Player player) {
		map(player).remove(player.getUUID());
	}

	private static Map<UUID, Float> map(Player player) {
		return player.level().isClientSide() ? CLIENT : SERVER;
	}

	private static float approach(float from, float to) {
		if (Math.abs(to - from) <= EASE_PER_TICK) {
			return to;
		}
		return from + Math.signum(to - from) * EASE_PER_TICK;
	}
}
