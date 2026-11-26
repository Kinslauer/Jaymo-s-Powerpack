package com.jaymoskingdoms.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Kitchen (optional) draws a zombie feeding on a body down on its knees over it ({@code GnawPose}). While it is,
 * a horde's walking reach ({@link HordePoses}) and Fresh Animations' idle arms ({@link EmfBladePause}) leave it alone.
 */
final class KitchenGnaw {
	private static final @Nullable MethodHandle POSED = find();

	private KitchenGnaw() {
	}

	private static @Nullable MethodHandle find() {
		if (!FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return null;
		}
		try {
			return MethodHandles.publicLookup().findStatic(Class.forName("com.jaymoskitchen.client.corpse.GnawPose"), "posed",
				MethodType.methodType(boolean.class, Object.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	/** Whether Kitchen draws {@code entity} down over a body it's gnawing (never without Kitchen). */
	static boolean posed(Object entity) {
		if (POSED == null) {
			return false;
		}
		try {
			return (boolean) POSED.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}
}
