package com.jaymospowers.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Andy (2026-11-27): "you should get a hint when using tables that you can't yet or dont have the requirements for". When a
 * use of a station would otherwise do nothing, it says what's missing on the action bar: what it takes, the tool it wants,
 * that it isn't built yet. Every line is a {@code message.jaymospowers.station.*} key listed in {@code hint_keys.txt}, so
 * it comes up as a tip card (or stays on the bar with hints off). Told from the server, at most every {@link #EVERY} ticks
 * a player, so a held use key doesn't repeat it.
 */
public final class StationHints {
	/** Ticks before the same player is told again. */
	static final int EVERY = 20;

	private static final Map<UUID, Long> TOLD = new HashMap<>();

	private StationHints() {
	}

	public static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOLD.remove(handler.player.getUUID()));
	}

	/**
	 * Whether a use with this hand is worth a hint: an empty hand, or an item that isn't a block (a block in hand is
	 * usually being placed against the station, and that stays quiet).
	 */
	public static boolean idle(Player player) {
		ItemStack held = player.getMainHandItem();
		return held.isEmpty() || !(held.getItem() instanceof BlockItem);
	}

	/** Tells {@code player} (on the server) the line {@code message.jaymospowers.station.<key>}. */
	public static void tell(Player player, String key, Object... args) {
		if (!(player instanceof ServerPlayer server)) {
			return;
		}
		long now = server.level().getGameTime();
		Long last = TOLD.get(server.getUUID());
		if (last != null && now - last < EVERY && now >= last) {
			return;
		}
		TOLD.put(server.getUUID(), now);
		server.sendOverlayMessage(Component.translatable("message.jaymospowers.station." + key, args).withStyle(ChatFormatting.GRAY));
	}
}
