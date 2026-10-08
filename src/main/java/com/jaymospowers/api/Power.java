package com.jaymospowers.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A superpower. Server-side half: everything here runs on the server (or the integrated server in singleplayer).
 * The client-side half, if any, is a {@code com.jaymospowers.client.PowerClient} registered under the same id.
 *
 * <p>Lang keys (put them in {@code lang/parts/<power>.json}):
 * <ul>
 * <li>{@code power.jaymospowers.<path>} - the name</li>
 * <li>{@code power.jaymospowers.<path>.desc} - a one or two sentence description</li>
 * <li>{@code power.jaymospowers.<path>.key.ability_1} … {@code ability_4} - what each key does (omit unused keys)</li>
 * </ul>
 */
public interface Power {
	Identifier id();

	/** Accent colour, 0xRRGGBB. */
	int color();

	/** Shown on the HUD badge and the powers screen. */
	ItemStack icon();

	default Component name() {
		return Component.translatable("power." + id().getNamespace() + "." + id().getPath());
	}

	default Component description() {
		return Component.translatable("power." + id().getNamespace() + "." + id().getPath() + ".desc");
	}

	/** The player just got this power (awakening, the powers screen, or /power set). */
	default void onGained(ServerPlayer player) {
	}

	/** The player is losing this power (switching, /power clear). Undo anything persistent (attributes, abilities). */
	default void onLost(ServerPlayer player) {
	}

	/** Every server tick, for each player holding this power (alive or not; check {@code isAlive()} as needed). */
	default void tick(ServerPlayer player) {
	}

	/** One of the ability keys was pressed ({@code down}) or released. {@link com.jaymospowers.core.PowerInput} has the held state. */
	default void onKey(ServerPlayer player, PowerKey key, boolean down) {
	}

	/** True while the player may stay airborne without the server's "kicked for floating" check (flight, swinging). */
	default boolean allowsFloating(ServerPlayer player) {
		return false;
	}

	/** True while the server should skip its "moved too quickly" check for the player (dashes, launches). */
	default boolean skipsMovementCheck(ServerPlayer player) {
		return false;
	}
}
