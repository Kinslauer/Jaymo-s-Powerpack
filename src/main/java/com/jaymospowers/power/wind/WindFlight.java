package com.jaymospowers.power.wind;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;

/** The flight states. Shared by both sides (the client drives the flight; the server follows and validates). */
public enum WindFlight {
	/** On the ground or falling normally. */
	NONE,
	/** Floating: slow WASD movement, Jump rises, Sneak sinks. */
	HOVER,
	/** Fast flight in the look direction (hold Sprint to accelerate). */
	CRUISE,
	/** Cruise at 80%+ of top speed. */
	SONIC,
	/**
	 * Lost control: a turn too sharp for the speed sent the flyer tumbling end over end. Not flying (gravity and
	 * momentum have them; falls count), until they recover into a hover.
	 */
	TUMBLE;

	private static final WindFlight[] VALUES = values();

	public static WindFlight byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : null;
	}

	public boolean flying() {
		return this != NONE && this != TUMBLE;
	}

	/** Cruise or sonic: the superman pose, the trail and the lock apply. */
	public boolean fast() {
		return this == CRUISE || this == SONIC;
	}

	/**
	 * Whether the player may be flying right now. Flight ends when the player dies, rides something, sleeps, goes
	 * under water (eyes submerged) or wades into lava past the knees. Same rule on both sides.
	 */
	public static boolean canFly(Player player) {
		return player.isAlive()
			&& !player.isSpectator()
			&& !player.isPassenger()
			&& !player.isSleeping()
			&& !player.isEyeInFluid(FluidTags.WATER)
			&& player.getFluidHeight(FluidTags.LAVA) < 0.4;
	}
}
