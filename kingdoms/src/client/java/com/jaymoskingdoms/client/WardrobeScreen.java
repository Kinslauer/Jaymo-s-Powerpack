package com.jaymoskingdoms.client;

import java.util.UUID;
import net.conczin.mca.client.gui.NeedleScreen;

/**
 * (0.7.53) MCA's character editor held to its clothing page, for your own clothes ({@link Wardrobe}). It's MCA's Needle
 * and Thread screen without the needle: no other page (body, hair, destiny) can be reached, picking an outfit saves it
 * and closes, Escape changes nothing, and no held needle gets worn down.
 */
final class WardrobeScreen extends NeedleScreen {
	WardrobeScreen(UUID self) {
		super(self);
	}

	@Override
	protected void eventCallback(String event) {
	}
}
