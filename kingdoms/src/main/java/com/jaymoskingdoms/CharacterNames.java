package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
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
 * <p>
 * (0.7.166) Andy: "it should do the same with equipped costumes and disguises. right now it doesnt change the
 * scoreboard name". A hood shows its costume's name (or "Hooded Figure"), a goblin disguise "Goblin", and the tab list
 * is resent whenever what a player shows as changes (a bandit mask put on, say, which Powers never resent).
 */
public final class CharacterNames {
	/** How often (ticks) each player's name is checked against MCA's and what they wear, so a change shows. */
	static final int CHECK_TICKS = 20;

	/**
	 * On a player, synced: what they show as when no mask from Powers names them (their character name, or (0.7.166) a
	 * hood's or disguise's), so the client shows it too (absent when it's just the username).
	 */
	static final AttachmentType<Component> NAME = AttachmentRegistry.create(JaymosKingdoms.id("shown_name"),
		builder -> builder.syncWith(ComponentSerialization.TRUSTED_STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * (0.7.160) On a player, saved: the name they were given because they kept their username in MCA's creator. Andy:
	 * "usernames should only show up for commands". Kept, so they are always given the same one.
	 */
	static final AttachmentType<String> GIVEN = AttachmentRegistry.create(JaymosKingdoms.id("given_name"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());

	/** (0.7.166) Player → the tab list name last sent for them, so any change is resent. */
	private static final Map<UUID, Component> SENT = new HashMap<>();

	private CharacterNames() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("character name attachments: {} {}", NAME, GIVEN);
		ServerPlayerEvents.JOIN.register(CharacterNames::refresh);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(CharacterNames::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refresh(player);
		}
	}

	/** Brings {@code player}'s synced name in line with MCA's and what they wear, and everyone's tab list with it. */
	private static void refresh(ServerPlayer player) {
		if (picked(player) == null && McaCompat.LOADED && player.level() != null) {
			// (0.7.160) no name of their own but their username: they get one from MCA's lists (renamed in its editor as usual)
			String given = player.getAttached(GIVEN);
			if (given == null) {
				given = McaBridge.randomName(player);
			}
			if (given != null && !given.equals(player.getGameProfile().name())) {
				player.setAttached(GIVEN, given);
				McaBridge.setName(player, given);
			}
		}
		Component name = live(player);
		if (!Objects.equals(name, player.getAttached(NAME))) {
			if (name == null) {
				player.removeAttached(NAME);
			} else {
				player.setAttached(NAME, name);
			}
		}
		// (0.7.166) the tab list as everyone sees it (Powers' masks included), resent when it changes
		Component tab = player.getTabListDisplayName();
		if (!Objects.equals(tab, SENT.put(player.getUUID(), tab))) {
			player.level().getServer().getPlayerList().broadcastAll(
				new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
		}
	}

	/** (0.7.201) {@code player}'s own character name (no hood or disguise), or null for just the username. Server only. */
	static @Nullable String character(ServerPlayer player) {
		return picked(player);
	}

	/** The name {@code player} picked in MCA's creator, or null if they have none but their username. Server only. */
	private static @Nullable String picked(ServerPlayer player) {
		if (!McaCompat.LOADED || player.level() == null) {
			return null;
		}
		String name = McaBridge.name(player).strip();
		return name.isEmpty() || name.equals(player.getGameProfile().name()) ? null : name;
	}

	/**
	 * (0.7.166) What {@code player} shows as, worked out now (server): a hood's name, a goblin disguise, else their
	 * character name; null for just the username. A full mask is Powers' to name, so not here.
	 */
	private static @Nullable Component live(ServerPlayer player) {
		if (Identities.maskKey(player) != null) {
			return null;
		}
		if (Identities.hooded(player)) {
			String hood = Identities.tagName(player);
			return hood.isEmpty() ? Component.translatable("identity.jaymoskingdoms.hooded_name") : Component.literal(hood);
		}
		if (Disguise.wearsDisguise(player)) {
			return Component.translatable("identity.jaymoskingdoms.goblin_name");
		}
		String name = picked(player);
		return name == null ? null : Component.literal(name);
	}

	/** What {@code player} shows as when no mask from Powers names them, or null for their username (either side). */
	public static @Nullable Component of(Player player) {
		if (player instanceof ServerPlayer sp) {
			return live(sp);
		}
		// a full mask is Powers' to name (Identities' synced mirror of it)
		return Identities.maskedAs(player) != null ? null : player.getAttached(NAME);
	}

	/** {@code player}'s shown name in their team's colours (no hover card: that would give the username away), or null. */
	public static @Nullable Component shown(Player player) {
		Component name = of(player);
		return name == null ? null : PlayerTeam.formatNameForTeam(player.getTeam(), name);
	}
}
