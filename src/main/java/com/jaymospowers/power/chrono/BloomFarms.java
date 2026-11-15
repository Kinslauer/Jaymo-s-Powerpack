package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Time Bloom and Jaymo's Kitchen's slow farms (Andy, on the GT New Horizons slow herds: "bloom affects all of them"):
 * Kitchen slows crops and saplings ({@code com.jaymoskitchen.farm.SlowFarms}) and herds' growing up, breeding waits, laying
 * and milking ({@code com.jaymoskitchen.farm.Herds}); a bloom's own random ticks go through {@link #hurry} so they aren't
 * slowed, and the creatures in it have their clocks run on with {@link #bloom}. Looked up once by reflection, so Powers
 * never needs Kitchen: without it, a random tick just runs and creatures are left as they are.
 */
final class BloomFarms {
	private static boolean looked;
	private static @Nullable MethodHandle hurry, bloom;

	private BloomFarms() {
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return;
		}
		try {
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			hurry = lookup.findStatic(Class.forName("com.jaymoskitchen.farm.SlowFarms"), "hurry", MethodType.methodType(void.class, Runnable.class));
			bloom = lookup.findStatic(Class.forName("com.jaymoskitchen.farm.Herds"), "bloom", MethodType.methodType(void.class, Entity.class, int.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			hurry = bloom = null;
			JaymosPowers.LOGGER.warn("Jaymo's Kitchen's slow farms not found; Time Bloom ticks them as plain random ticks", e);
		}
	}

	/** Runs {@code tick} (a bloom's random tick) past Kitchen's slow farms. */
	static void hurry(Runnable tick) {
		look();
		if (hurry != null) {
			boolean[] ran = {false};
			try {
				hurry.invoke((Runnable) () -> {
					ran[0] = true;
					tick.run();
				});
				return;
			} catch (RuntimeException | Error e) {
				if (ran[0]) {
					// (the tick itself threw: as it would have without Kitchen)
					throw e;
				}
				hurry = null;
			} catch (Throwable t) {
				hurry = null;
			}
			if (ran[0]) {
				return;
			}
		}
		tick.run();
	}

	/** Runs {@code entity}'s slowed clocks (growing up, the wait to breed, laying, milk) on by {@code ticks}. */
	static void bloom(Entity entity, int ticks) {
		look();
		if (bloom != null) {
			try {
				bloom.invoke(entity, ticks);
			} catch (Throwable t) {
				bloom = null;
			}
		}
	}
}
