package com.jaymoskitchen.mill;

/**
 * The mill machines, after the Horse Power mod (by Gory_Moon; LGPL-3.0, see LICENSE_horsepower): a grindstone, a
 * chopping block and a press, each worked by hand or by a horse walking circles around it on a lead.
 */
public enum MachineKind {
	/** Turn the handle to grind (wheat to flour, bones to bone meal, flowers to dye...). */
	HAND_GRINDSTONE("hand_grindstone", Work.GRIND, false, false, 6),
	/** A horse walks it round: a lap per item. */
	GRINDSTONE("grindstone", Work.GRIND, true, false, 1),
	/** Set a log on it and chop it into planks with an axe: more than the crafting table gives. */
	CHOPPING_BLOCK("chopping_block", Work.CHOP, false, false, 3),
	/** A horse works the blade: a lap per log, and more planks still. */
	CHOPPER("chopper", Work.CHOP, true, true, 1),
	/** Pull the plate down on a load: seeds to oil or dirt, leaves to water. */
	PRESS("press", Work.PRESS, false, true, 4),
	/** A horse works the press: a lap per load. */
	HORSE_PRESS("horse_press", Work.PRESS, true, true, 1);

	public enum Work {
		GRIND, CHOP, PRESS
	}

	public final String id;
	public final Work work;
	/** Worked by a horse (on a lead tied to it) rather than by hand. */
	public final boolean horse;
	/** Two blocks tall: there's a filler block above it. */
	public final boolean tall;
	/** Hand: actions (turns, chops, pulls) per item or load. Horse: laps per item or load. */
	public final int effort;

	MachineKind(String id, Work work, boolean horse, boolean tall, int effort) {
		this.id = id;
		this.work = work;
		this.horse = horse;
		this.tall = tall;
		this.effort = effort;
	}
}
