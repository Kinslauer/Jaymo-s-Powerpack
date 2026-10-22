package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.149) Andy: "your name on scoreboard should be the one you pick at the start, not your default user. to aid with
 * immersion. it should no longer show up anywhere." The name picked in MCA's creator (its family tree, {@link
 * McaBridge#name}) stands in for the username wherever a player's display name shows: chat, death, join and leave
 * messages, the name over their head, the tab list, Jade, Xaero's minimap ({@code client/mixin/xaero}) and other mods'
 * lines ({@code mixin/mca/McaPlayerNameMixin}). Commands still take usernames. A costume, Rig Helm or bandit mask from
 * Jaymo's Powers still wins: its name is shown first and this never runs.
 */
public final class CharacterNames {
	/** How often (ticks) each player's name is checked against MCA's, so a rename in its editor shows. */
	static final int CHECK_TICKS = 20;

	/** On a player, synced: their character name, so the client shows it too (absent when there's none). */
	static final AttachmentType<String> NAME = AttachmentRegistry.create(JaymosKingdoms.id("character_name"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	/**
	 * (0.7.159) On a player, saved: the name they were given because they kept their username in MCA's creator. Andy:
	 * "usernames should only show up for commands". Kept, so they are always given the same one.
	 */
	static final AttachmentType<String> GIVEN = AttachmentRegistry.create(JaymosKingdoms.id("given_name"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());

	private CharacterNames() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("character name attachments: {} {}", NAME, GIVEN);
		ServerPlayerEvents.JOIN.register(CharacterNames::refresh);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(CharacterNames::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refresh(player);
		}
	}

	/** Brings {@code player}'s synced name in line with MCA's, and everyone's tab list with it. */
	private static void refresh(ServerPlayer player) {
		String name = picked(player);
		if (name == null && McaCompat.LOADED && player.level() != null) {
			// (0.7.159) no name of their own but their username: they get one from MCA's lists (renamed in its editor as usual)
			String given = player.getAttached(GIVEN);
			if (given == null) {
				given = McaBridge.randomName(player);
			}
			if (given != null && !given.equals(player.getGameProfile().name())) {
				player.setAttached(GIVEN, given);
				McaBridge.setName(player, given);
				name = given;
			}
		}
		String had = player.getAttached(NAME);
		if (name == null ? had == null : name.equals(had)) {
			return;
		}
		if (name == null) {
			player.removeAttached(NAME);
		} else {
			player.setAttached(NAME, name);
		}
		player.level().getServer().getPlayerList().broadcastAll(
			new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
	}

	/** The name {@code player} picked in MCA's creator, or null if they have none but their username. Server only. */
	private static @Nullable String picked(ServerPlayer player) {
		if (!McaCompat.LOADED || player.level() == null) {
			return null;
		}
		String name = McaBridge.name(player).strip();
		return name.isEmpty() || name.equals(player.getGameProfile().name()) ? null : name;
	}

	/** The character name {@code player} goes by, or null if they have none (either side). */
	public static @Nullable String of(Player player) {
		return player instanceof ServerPlayer sp ? picked(sp) : player.getAttached(NAME);
	}

	/** {@code player}'s character name in their team's colours (no hover card: that would give the username away), or null. */
	public static @Nullable Component shown(Player player) {
		String name = of(player);
		return name == null ? null : PlayerTeam.formatNameForTeam(player.getTeam(), Component.literal(name));
	}
}
