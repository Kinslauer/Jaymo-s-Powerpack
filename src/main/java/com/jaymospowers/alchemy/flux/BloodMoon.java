package com.jaymospowers.alchemy.flux;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Whether Jaymo's Kingdoms (when it is loaded) says tonight is a Blood Moon or Super Blood Moon, by reflection only
 * ({@code Moons.now(server)} returns an enum whose names are NONE, BLOOD, SUPER_BLOOD, HARVEST, BLUE), like
 * {@code ZFighterKingdoms}. Without Kingdoms the answer is always no, and flux behaves as on an ordinary night.
 */
final class BloodMoon {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static Method now;
	private static boolean failed;

	private BloodMoon() {
	}

	static boolean active(MinecraftServer server) {
		if (!LOADED || failed) {
			return false;
		}
		try {
			if (now == null) {
				now = Class.forName("com.jaymoskingdoms.Moons").getMethod("now", MinecraftServer.class);
			}
			Object moon = now.invoke(null, server);
			if (moon instanceof Enum<?> e) {
				String name = e.name();
				return name.equals("BLOOD") || name.equals("SUPER_BLOOD");
			}
		} catch (ReflectiveOperationException | LinkageError e) {
			failed = true;
		}
		return false;
	}
}
