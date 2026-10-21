package com.jaymoskitchen.mill.power;

import net.minecraft.core.Direction;

/**
 * A machine a turning shaft can work: implement it on the machine's block entity. A windmill or waterwheel follows
 * its shaft to the block at the far end (see {@link Shafts#driven}) and, while it turns, calls
 * {@link #turnFromShaft} about once a second.
 */
public interface ShaftDriven {
	/** Whether a shaft (or corner) ending against this side of the machine turns it. */
	boolean takesShaftFrom(Direction side);

	/**
	 * The shaft is turning: work for the next {@code ticks} ticks at {@code rate} (1 = a windmill in clear weather or a
	 * waterwheel; more in rain and storms). Not called again once the source stops, so let it run out.
	 */
	void turnFromShaft(float rate, int ticks);
}
