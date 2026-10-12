package com.jaymoskingdoms;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.23) Jaymo's Kitchen (optional), for {@link Undertakers}: its corpses, reached by reflection so Kingdoms needs no
 * Kitchen jar to build or run. Without Kitchen 0.4.28 or later ({@code CorpseEntity.carryBy} and {@code throwAt}) every
 * call fails soft: nothing is a corpse.
 */
final class KitchenCompat {
	static final String MOD = "jaymoskitchen";
	private static final @Nullable Class<?> CORPSE;
	private static final @Nullable Method HOSTILE, CARRIER, CARRY_BY, THROW_AT;

	static {
		Class<?> corpse = null;
		Method hostile = null, carrier = null, carryBy = null, throwAt = null;
		if (FabricLoader.getInstance().isModLoaded(MOD)) {
			try {
				corpse = Class.forName("com.jaymoskitchen.corpse.CorpseEntity");
				hostile = corpse.getMethod("isHostile");
				carrier = corpse.getMethod("carrier");
				carryBy = corpse.getMethod("carryBy", Entity.class);
				throwAt = corpse.getMethod("throwAt", Vec3.class);
			} catch (ReflectiveOperationException e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for guards to clear corpses (needs 0.4.28): {}", e.toString());
				corpse = null;
			}
		}
		CORPSE = corpse;
		HOSTILE = hostile;
		CARRIER = carrier;
		CARRY_BY = carryBy;
		THROW_AT = throwAt;
	}

	private KitchenCompat() {
	}

	/** Kitchen is here, and new enough. */
	static boolean loaded() {
		return CORPSE != null;
	}

	/** A Kitchen corpse of a monster (a zombie, a skeleton, a spider...), lying free: nobody carrying it, not in a cart. */
	static boolean isFreeMonsterCorpse(Entity entity) {
		return CORPSE != null && CORPSE.isInstance(entity) && !entity.isRemoved() && !entity.isPassenger()
			&& Boolean.TRUE.equals(call(HOSTILE, entity)) && call(CARRIER, entity) == null;
	}

	/** Who's carrying this corpse, if anyone. */
	static @Nullable Entity carrier(Entity corpse) {
		return call(CARRIER, corpse) instanceof Entity e ? e : null;
	}

	/** {@code by} takes the corpse up in its arms; false if it couldn't (someone has it, it was just let go...). */
	static boolean carry(Entity corpse, Entity by) {
		return Boolean.TRUE.equals(call(CARRY_BY, corpse, by));
	}

	/** Whoever is carrying the corpse throws it at {@code at}. */
	static void throwAt(Entity corpse, Vec3 at) {
		call(THROW_AT, corpse, at);
	}

	private static @Nullable Object call(@Nullable Method method, Entity corpse, Object... args) {
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(corpse, args);
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosKingdoms.LOGGER.debug("Kitchen corpse call {} failed: {}", method.getName(), e.toString());
			return null;
		}
	}
}
