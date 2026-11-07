package com.jaymospowers.client.power.wind;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (Andy, 2026-11-01) Jaymo's Kingdoms, when it's loaded: whether a player is held up by Thessaly's mana wings
 * ({@code com.jaymoskingdoms.client.spirits.ManaWings.hovering(Player)}, looked up once by reflection). They hang in the
 * air the way the wind's hover does, so {@link WindHoverSway} sways them the same. False without Kingdoms.
 */
final class ManaWingsCompat {
	private static boolean looked;
	private static @Nullable MethodHandle hovering;

	private ManaWingsCompat() {
	}

	static boolean hovering(Player player) {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
				try {
					hovering = MethodHandles.publicLookup().findStatic(Class.forName("com.jaymoskingdoms.client.spirits.ManaWings"), "hovering",
						MethodType.methodType(boolean.class, Player.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					hovering = null;
				}
			}
		}
		if (hovering == null) {
			return false;
		}
		try {
			return (boolean)hovering.invoke(player);
		} catch (Throwable e) {
			return false;
		}
	}
}
