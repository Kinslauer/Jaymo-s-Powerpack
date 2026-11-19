package com.jaymoskitchen.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Kingdoms' Tideborn, when Kingdoms is loaded (by method handle, so Kitchen never compiles against it): a fish trap
 * set or emptied in their sight, or in their haven, counts as fishing ({@code TideFishing#fished}).
 */
public final class TideCompat {
	private static boolean looked;
	private static @Nullable MethodHandle fished;

	private TideCompat() {
	}

	/** {@code player} set or emptied a fish trap at {@code pos}. */
	public static void fished(ServerPlayer player, BlockPos pos) {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
				try {
					fished = MethodHandles.publicLookup().findStatic(Class.forName("com.jaymoskingdoms.TideFishing"), "fished",
						MethodType.methodType(void.class, ServerPlayer.class, BlockPos.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					fished = null;
				}
			}
		}
		if (fished != null) {
			try {
				fished.invoke(player, pos);
			} catch (Throwable e) {
				fished = null;
			}
		}
	}
}
