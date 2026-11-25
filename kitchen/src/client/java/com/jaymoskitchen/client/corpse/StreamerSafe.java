package com.jaymoskitchen.client.corpse;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.jspecify.annotations.Nullable;

/**
 * Whether this player has streamer safe on (Jaymo's Powers' {@code BloodSettings.streamerSafe()}, by name; never without
 * Powers): gibs aren't drawn, bodies are drawn whole and rot doesn't show bare bone.
 */
public final class StreamerSafe {
	private static @Nullable MethodHandle safe;
	private static boolean looked;

	private StreamerSafe() {
	}

	public static boolean on() {
		if (!looked) {
			looked = true;
			try {
				safe = MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.client.blood.BloodSettings"), "streamerSafe",
					MethodType.methodType(boolean.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				safe = null;
			}
		}
		if (safe != null) {
			try {
				return (boolean) safe.invokeExact();
			} catch (Throwable e) {
				safe = null;
			}
		}
		return false;
	}
}
