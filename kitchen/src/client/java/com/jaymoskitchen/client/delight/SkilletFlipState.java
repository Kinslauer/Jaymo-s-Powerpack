package com.jaymoskitchen.client.delight;

/** Added to a humanoid's render state (by a mixin): the game tick its pan was last flipped, or -1. */
public interface SkilletFlipState {
	long jaymoskitchen$getFlipTimestamp();

	void jaymoskitchen$setFlipTimestamp(long value);
}
