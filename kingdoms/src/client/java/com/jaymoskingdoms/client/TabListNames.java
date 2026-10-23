package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Identities;
import com.jaymoskingdoms.Introductions;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.149) For other mods' lines that name a player by their username ({@code mixin/xaero}): the name the tab list shows
 * instead, their character name ({@code CharacterNames}) or a costume's while masked. (0.7.180) "Stranger" for one you
 * haven't been introduced to ({@link Introductions}).
 */
public final class TabListNames {
	private TabListNames() {
	}

	/** The name the tab list shows for {@code profile}, or {@code username} if it shows none. */
	public static String shown(GameProfile profile, String username) {
		if (stranger(profile.id())) {
			return strangerName().getString();
		}
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(profile.id());
		Component name = info == null ? null : info.getTabListDisplayName();
		return name == null ? username : name.getString();
	}

	/** (0.7.180) The player with {@code id} is a stranger to this client's: not introduced, and not in a mask (seen near). */
	public static boolean stranger(UUID id) {
		if (Introductions.knownHere(id)) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Player near = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(id);
		// a costume's name is for everyone
		return near == null || Identities.maskedAs(near) == null;
	}

	/** (0.7.180) What the tab list and minimap call a stranger. */
	public static Component strangerName() {
		return Introductions.stranger().copy().withStyle(ChatFormatting.GRAY);
	}
}
