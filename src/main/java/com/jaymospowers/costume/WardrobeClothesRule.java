package com.jaymospowers.costume;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Whether the wardrobe's Clothes tab opens for a player: MCA's own rule for {@code /mca editor} (an operator, or a
 * server whose MCA config lets players use the full or limited editor). Without MCA there is no Clothes tab.
 */
final class WardrobeClothesRule {
	private WardrobeClothesRule() {
	}

	static boolean allowed(ServerPlayer player) {
		return FabricLoader.getInstance().isModLoaded("mca") && Mca.allowed(player);
	}

	/** Touches MCA's classes only once it is known to be loaded. */
	private static final class Mca {
		static boolean allowed(ServerPlayer player) {
			net.conczin.mca.Config config = net.conczin.mca.Config.getInstance();
			return player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
				|| config.allowFullPlayerEditor || config.allowLimitedPlayerEditor;
		}
	}
}
