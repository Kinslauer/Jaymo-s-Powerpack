package com.jaymospowers.core.gifted;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.1) What a gifted villager ({@link Gifted}) can do with one power: a few moves of its own, simpler and weaker than
 * a player's (no keys, no client; only a ruler unleashes an ultimate), drawn in the power's own colours and sounds. Each power keeps its kit in
 * its own package ({@code power/<name>/<Name>Gift}) and registers it with {@link Gifted#register}; {@link GiftedAI} picks
 * the moves in a fight.
 */
public interface GiftKit {
	/** The power this kit is for ({@code jaymospowers:wind}...). */
	Identifier power();

	/** The moves, strongest first (a bold villager opens with the first ready one). */
	List<Move> moves();

	/** Now and then ({@link GiftedAI#PASSIVE_EVERY}), fighting or not: the power's quiet gift. */
	default void passive(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
	}

	/**
	 * Out of a fight, now and then ({@link GiftedAI#EVERYDAY_EVERY}): the power at work in their everyday life (Andy: "they
	 * should use everything they can"), like a Wind villager clearing smoke or a Cryo one chilling the larder.
	 */
	default void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
	}

	/**
	 * The power's ultimate, for a gifted ruler whose throne room is attacked ({@link Gifted#unleash}), or null without one.
	 * Andy: "Absolutely, unless they are gravity, then the meteor is a last resort."
	 */
	default @Nullable Ultimate ultimate() {
		return null;
	}

	/** The power has just been corrupted in {@code mob} (the word of it is {@link GiftedAI}'s). */
	default void corrupted(ServerLevel level, PathfinderMob mob) {
	}

	/** What a move is for, so the AI knows when it fits. */
	enum Kind {
		/** At range, with a clear line to the foe. */
		STRIKE,
		/** On a foe close by. */
		CLOSE,
		/** To get away from a foe too close (a wary villager's first choice). */
		ESCAPE,
		/** On themselves or the folk round them. */
		SUPPORT
	}

	/** Uses the move; false if it couldn't be used just now (no cooldown then). */
	@FunctionalInterface
	interface Use {
		boolean use(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift);
	}

	/**
	 * An ultimate: the film nearby players see the bars of ({@code UltimateFilm}'s shot and length), whether it's kept for
	 * a last resort (only once the ruler is badly hurt, {@link GiftedAI#LAST_RESORT}), and the move itself.
	 */
	record Ultimate(int shot, int filmTicks, boolean lastResort, Use use) {
	}

	/**
	 * One move: its name (for {@code /power gift}), what it's for, its cooldown (ticks), the foe's distance it fits
	 * (blocks), whether only a corrupted power has it, and the move itself.
	 */
	record Move(String name, Kind kind, int cooldown, double minRange, double maxRange, boolean corruptOnly, Use use) {
		public static Move of(String name, Kind kind, int cooldown, double minRange, double maxRange, Use use) {
			return new Move(name, kind, cooldown, minRange, maxRange, false, use);
		}

		public static Move corrupt(String name, Kind kind, int cooldown, double minRange, double maxRange, Use use) {
			return new Move(name, kind, cooldown, minRange, maxRange, true, use);
		}

		boolean fits(double distance) {
			return distance >= this.minRange && distance <= this.maxRange;
		}
	}
}
