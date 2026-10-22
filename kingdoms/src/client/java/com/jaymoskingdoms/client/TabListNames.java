package com.jaymoskingdoms.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * (0.7.149) For other mods' lines that name a player by their username ({@code mixin/xaero}): the name the tab list shows
 * instead, their character name ({@code CharacterNames}) or a costume's while masked.
 */
public final class TabListNames {
	private TabListNames() {
	}

	/** The name the tab list shows for {@code profile}, or {@code username} if it shows none. */
	public static String shown(GameProfile profile, String username) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(profile.id());
		Component name = info == null ? null : info.getTabListDisplayName();
		return name == null ? username : name.getString();
	}
}
