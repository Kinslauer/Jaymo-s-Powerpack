package com.jaymoskitchen.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Whether Jaymo's Kingdoms (when it is loaded) says tonight is a Blood Moon or a Super Blood Moon ({@code Moons.now},
 * by reflection, like Powers' {@code BloodMoon}). Without Kingdoms it never is. Asked once a tick at most.
 */
public final class MoonCompat {
	private static boolean looked;
	private static MethodHandle now;
	private static int askedAt = -1;
	private static boolean blood;

	private MoonCompat() {
	}

	public static boolean bloodMoon(MinecraftServer server) {
		if (server.getTickCount() == askedAt) {
			return blood;
		}
		askedAt = server.getTickCount();
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
				try {
					Class<?> moons = Class.forName("com.jaymoskingdoms.Moons");
					now = MethodHandles.publicLookup().findStatic(moons, "now",
						MethodType.methodType(Class.forName("com.jaymoskingdoms.Moons$Moon"), MinecraftServer.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					now = null;
				}
			}
		}
		blood = false;
		if (now != null) {
			try {
				Object moon = now.invoke(server);
				blood = moon instanceof Enum<?> e && (e.name().equals("BLOOD") || e.name().equals("SUPER_BLOOD"));
			} catch (Throwable e) {
				now = null;
			}
		}
		return blood;
	}
}
