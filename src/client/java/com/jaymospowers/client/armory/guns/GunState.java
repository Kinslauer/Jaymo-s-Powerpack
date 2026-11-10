package com.jaymospowers.client.armory.guns;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What one draw of a gun in a hand needs, taken when the item's model is updated: whose hand (the animation instance),
 * how the gun stands, and the game times of its last shot and of its coming into the hand (-1: none).
 */
record GunState(GunRenderer renderer, long instance, Pose pose, boolean aiming, long shotStart, long drawStart,
	@Nullable Identifier texture, List<String> hidden) {
	enum Pose {
		LOADED, SPENT, RELOAD
	}
}
