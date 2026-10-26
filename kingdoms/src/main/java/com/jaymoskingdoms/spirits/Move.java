package com.jaymoskingdoms.spirits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.237) One thing a spirit's hands can do. Andy: "some have entire move sets but all can be upgraded and leveled". A move
 * is cast from the spirit key ({@link Spirits.Key}, action CAST): {@link Spirits} checks the cooldown ({@link Moves#ready})
 * and that the player can pay, calls {@link #cast}, and only if that returns true takes the mana
 * ({@link com.jaymoskingdoms.Mana#pay}) and starts the cooldown. So a move never pays for itself, and returns false when
 * nothing happened (no target, nothing to grab) so nothing is spent.
 */
public interface Move {
	/** The move's id ({@code "far_hand"}); its name on the HUD is the lang key {@code spirit.jaymoskingdoms.move.<id>}. */
	String id();

	/** Mana a cast costs (before sickness or corruption make it dearer). */
	float cost();

	/** What the cast just made by {@code player} costs: {@link #cost()} unless the move set otherwise ({@link CastHints}). */
	default float cost(ServerPlayer player) {
		return CastHints.cost(player, cost());
	}

	/** Ticks before it can be cast again. */
	int cooldown();

	/** The cooldown the cast just made by {@code player} starts: {@link #cooldown()} unless the move set otherwise. */
	default int cooldown(ServerPlayer player) {
		return CastHints.cooldown(player, cooldown());
	}

	/** The spirit level it unlocks at. */
	int level();

	/** Whether the key is held to charge it (the javelin): {@code charge} is then the ticks held. */
	default boolean charges() {
		return false;
	}

	/** The {@link Poses} id the arms hold when it is cast. */
	int pose();

	/** The pose the cast just made by {@code player} shows: {@link #pose()} unless the move set otherwise. */
	default int pose(ServerPlayer player) {
		return CastHints.pose(player, pose());
	}

	/** The intent the aura ripples with when it is paid for ({@link com.jaymoskingdoms.Mana#HARM}, {@code WILL}...). */
	default int intent() {
		return com.jaymoskingdoms.Mana.HARM;
	}

	/**
	 * Casts it. {@code charge} is the ticks the key was held (0 for a tap). False if nothing happened, so no mana is taken
	 * and no cooldown starts.
	 */
	boolean cast(ServerPlayer player, SpiritState state, int charge);

	/**
	 * Casts it from a spirit-carrying folk ({@code caster}: a court wizard, a hermit) at {@code target}; nothing by default.
	 * The moves the folk use (flurry, riposte, javelin, volley) implement it: no mana, no key, the folk's own cooldown.
	 */
	default boolean castFrom(LivingEntity caster, LivingEntity target, SpiritState state) {
		return false;
	}
}
