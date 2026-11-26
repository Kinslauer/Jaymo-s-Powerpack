package com.jaymospowers.dino;

/** Tuning for the Verdant World's dinosaurs (Amber Age's config values, fixed here: change balance in these constants). */
public final class DinoConfig {
	/** Whether dinosaurs appear on the Verdant World on their own. Spawn eggs and commands always work. */
	public static final boolean NATURAL_SPAWNS = true;
	/** Highest level a wild dinosaur can have. Most are far below it. */
	public static final int MAX_WILD_LEVEL = 20;
	/** How fast the taming bar fills: 2.0 needs half the food, 0.5 twice as much. */
	public static final double TAMING_SPEED = 1.0;
	/** Multiplier on the torpor of every tranquilizer dart. */
	public static final double DART_STRENGTH = 1.0;
	/** Torpor one dose of narcotic adds to a knocked-out dinosaur. */
	public static final float NARCOTIC_TORPOR = 40.0F;
	/** Multiplier on the damage every dinosaur deals. */
	public static final double DAMAGE_MULTIPLIER = 1.0;
	/** Whether wild meat-eaters hunt the plant-eaters on sight (and the plant-eaters that can, fight back). Tamed ones still fight for their owner. */
	public static final boolean DINOSAURS_FIGHT = true;
	/** Ticks a wild meat-eater rests after a kill before it hunts again (it takes up to twice as long): 45 s. */
	public static final int HUNT_REST_TICKS = 45 * 20;
	/** Whether the how-to hints (what to do next with the dinosaur you look at) show on screen. */
	public static final boolean SHOW_HINTS = true;

	private DinoConfig() {
	}
}
