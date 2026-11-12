package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.HowlNose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Howl's nose on its own screen: the creature it tracks is outlined gold through walls, and every bleeding creature the
 * server named in {@link HowlNose#VIEW} red. Nobody else sees anything.
 */
public final class HowlNoseClient {
	/** The outlines (ARGB): the tracked prey, and the bleeding. */
	static final int PREY_COLOR = 0xFFFFB030, BLEEDING_COLOR = 0xFFD01818;

	private HowlNoseClient() {
	}

	/** The outline colour for {@code entity} on our screen, or 0. */
	public static int outline(Entity entity) {
		LocalPlayer self = Minecraft.getInstance().player;
		if (self == null || entity == self) {
			return 0;
		}
		HowlNose.Nose nose = self.getAttached(HowlNose.VIEW);
		if (nose == null) {
			return 0;
		}
		if (entity.getId() == nose.prey()) {
			return PREY_COLOR;
		}
		return nose.bleeders().contains(entity.getId()) ? BLEEDING_COLOR : 0;
	}
}
