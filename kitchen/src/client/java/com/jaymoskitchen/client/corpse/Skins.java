package com.jaymoskitchen.client.corpse;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

/** A dead player's skin: theirs if they're on the server, otherwise the default skin their id picks. */
public final class Skins {
	private Skins() {
	}

	public static PlayerSkin of(Optional<UUID> owner) {
		return of(owner, "");
	}

	/** By id, or failing that by name (if the id hasn't arrived yet). */
	public static PlayerSkin of(Optional<UUID> owner, String name) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (owner.isPresent()) {
			PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner.get());
			return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner.get());
		}
		PlayerInfo byName = connection == null || name.isEmpty() ? null : connection.getPlayerInfo(name);
		return byName != null ? byName.getSkin() : DefaultPlayerSkin.getDefaultSkin();
	}
}
