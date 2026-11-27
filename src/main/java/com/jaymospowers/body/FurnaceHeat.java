package com.jaymospowers.body;

/** A furnace's cooking progress and heat, as the client knows them from the block entity's own tag (see FurnaceSyncMixin). */
public interface FurnaceHeat {
	/** How far the item inside has cooked: 0 to 1. */
	float jaymospowers$cooked();

	/** Whether the fuel is burning. */
	boolean jaymospowers$burning();
}
