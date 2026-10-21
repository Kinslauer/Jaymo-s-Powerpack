package com.jaymospowers.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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

	/**
	 * (0.15.21) The player just went down (Wounds' downed state): end whatever is running (holds, grabs, forms of
	 * movement), keeping cooldowns and everything earned. While they're down their keys do nothing.
	 */
	default void onDowned(ServerPlayer player) {
	}

	/** Every server tick, for each player holding this power (alive or not; check {@code isAlive()} as needed). */
	default void tick(ServerPlayer player) {
	}

	/** One of the ability keys was pressed ({@code down}) or released. {@link com.jaymospowers.core.PowerInput} has the held state. */
	default void onKey(ServerPlayer player, PowerKey key, boolean down) {
	}

	/**
	 * The ascended ability's key (C by default; see {@link com.jaymospowers.core.Fate}) went down or up. Only sent
	 * while the power is ascended.
	 */
	/** Whether the C key answers a corrupted (not ascended) power too. */
	default boolean ultimateWhenCorrupted() {
		return false;
	}

	default void onUltimate(ServerPlayer player, boolean down) {
	}

	/**
	 * The power grew: {@code level} is how many abilities are now unlocked (2-4) from mastery, or 5 for ascension.
	 * Not called for powers handed out complete (creative, /power set).
	 */
	default void onLevelUp(ServerPlayer player, int level) {
	}

	/** The power just ascended (Fate already says so). Grant the form here. */
	default void onAscended(ServerPlayer player) {
	}

	/** The power was just corrupted (Fate already says so). */
	default void onCorrupted(ServerPlayer player) {
	}

	/** The power was just purified of its corruption, for good (Fate already says so). Undo corruption effects. */
	default void onPurified(ServerPlayer player) {
	}

	/**
	 * Whether fate can hand out this power: a random awakening can land on it and it can be chosen as an affinity. A
	 * power that says no (Xenoform) comes only through its own calling (the band).
	 */
	default boolean fated() {
		return true;
	}

	/**
	 * The key a fresh awakening's starter answers to, before any ability unlocks (null: the starter is passive, or
	 * there isn't one). While {@code Mastery.starterOnly(player)}, presses of this key reach {@link #onKey}.
	 */
	default @org.jspecify.annotations.Nullable PowerKey starterKey() {
		return null;
	}

	/**
	 * The power's own named state for the HUD badge and the powers screen, as a lang suffix (e.g. {@code ".symbiote"}
	 * for {@code power.jaymospowers.web.symbiote}), or null to name it by its fate ({@code .ascended}, {@code .corrupted},
	 * {@code .ascended_corrupted}). Both sides.
	 */
	default @org.jspecify.annotations.Nullable String statusSuffix(net.minecraft.world.entity.player.Player player) {
		return null;
	}

	/**
	 * Whether the Z ({@code "mode"}) or X ({@code "special"}) key does anything yet for this player's mastery, for the
	 * powers screen (H). Both sides.
	 */
	default boolean slotUnlocked(net.minecraft.world.entity.player.Player player, String slot) {
		return true;
	}

	/** The Power Mode key (Z by default) went down or up. Sent for every power. */
	default void onMode(ServerPlayer player, boolean down) {
	}

	/**
	 * The order the abilities unlock in when the power awakens naturally (see Mastery): the first is there from the
	 * start. Charged abilities go last.
	 */
	/** Whether {@code key} works before it's unlocked (the power decides what it does meanwhile). */
	default boolean usableEarly(PowerKey key) {
		return false;
	}

	/** Whether {@code key} works for {@code player} before it's unlocked (by default, {@link #usableEarly(PowerKey)}). */
	default boolean usableEarly(Player player, PowerKey key) {
		return usableEarly(key);
	}

	default PowerKey[] unlockOrder() {
		return PowerKey.values();
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
