package com.jaymospowers.power.zfighter;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * What the scouter's scan reads from Jaymo's Kingdoms (when it is loaded), by reflection only, like
 * {@link ZFighterKingdoms}: a person's realm and mood, a dead walker's road to the horde, how harsh the land is, and the
 * study a full scan of a beast counts for. Without Kingdoms every answer is empty and the scan simply has fewer lines.
 */
final class ZFighterScanKingdoms {
	/** {@code ScouterBridge.NO_MOOD}: no mood to read. */
	static final int NO_MOOD = Integer.MIN_VALUE;

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static Method REALM, MOOD, HORDE, STUDIED, LAND;
	private static boolean failed;

	private ZFighterScanKingdoms() {
	}

	private static boolean ready() {
		if (!LOADED || failed) {
			return false;
		}
		if (STUDIED == null) {
			try {
				Class<?> bridge = Class.forName("com.jaymoskingdoms.ScouterBridge");
				REALM = bridge.getMethod("realm", ServerLevel.class, LivingEntity.class);
				MOOD = bridge.getMethod("mood", LivingEntity.class);
				HORDE = bridge.getMethod("horde", LivingEntity.class);
				STUDIED = bridge.getMethod("studied", ServerPlayer.class, LivingEntity.class);
				LAND = Class.forName("com.jaymoskingdoms.HarshLands").getMethod("at", ServerLevel.class, BlockPos.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				failed = true;
				return false;
			}
		}
		return true;
	}

	/** The realm {@code person} serves or stands in the land of, or "". */
	static String realm(ServerLevel level, LivingEntity person) {
		ready();
		Object out = call(REALM, null, level, person);
		return out instanceof String name ? name : "";
	}

	/** An MCA villager's mood (-15 to 15), or {@link #NO_MOOD}. */
	static int mood(LivingEntity person) {
		ready();
		return call(MOOD, null, person) instanceof Integer mood ? mood : NO_MOOD;
	}

	/** A dead walker's road to the horde: 0 alone, 1 in a horde, 2 its captain; -1 if Kingdoms can't say. */
	static int horde(LivingEntity dead) {
		ready();
		return call(HORDE, null, dead) instanceof Integer stage ? stage : -1;
	}

	/** How many steps harsher than the temperate lands the land at {@code pos} is (0 to 3; 0 without Kingdoms). */
	static int land(ServerLevel level, BlockPos pos) {
		ready();
		return call(LAND, null, level, pos) instanceof Integer steps ? steps : 0;
	}

	/** Whether the land can be read at all (Kingdoms is there). */
	static boolean present() {
		return ready();
	}

	/** A full scan of {@code beast} counts as studying it (a star of that kind, a Druid's shape). */
	static void studied(ServerPlayer player, LivingEntity beast) {
		ready();
		call(STUDIED, null, player, beast);
	}

	private static Object call(Method method, Object on, Object... args) {
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(on, args);
		} catch (ReflectiveOperationException | LinkageError e) {
			failed = true;
			return null;
		}
	}
}
