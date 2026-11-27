package com.jaymospowers.client;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/**
 * Icy's Better Horses (in the pack, shipped unchanged) hangs a horse-head tab on the survival inventory's left edge while
 * you sit a tame horse: back to the horse's inventory ({@code RiderPanel$Tab}, at 18 px out and 24 px down, 16x21, put
 * back there every frame). Our own tabs on that edge (powers, baubles, Kingdoms' harness) hid it. While it is there they
 * move down by {@link #ROOM}, under it, and come back up when it is gone. Andy, 2026-11-27: "the inventory swap button
 * gets hidden under the harness and baubles."
 */
public final class IcyRiderTab {
	/** Icy's tab's class, found by name: Powers doesn't compile against Icy's. */
	static final String TAB = "icy.betterhorses.net.client.RiderPanel$Tab";
	/** How far our tabs move down: from the powers tab's 8 px to 48, 3 px under the bottom of Icy's (24 + 21). */
	public static final int ROOM = 40;

	private IcyRiderTab() {
	}

	/** How far down to move our left-edge tabs on {@code screen}: {@link #ROOM} while Icy's horse tab is on it, else 0. */
	public static int shift(Screen screen) {
		for (AbstractWidget widget : Screens.getWidgets(screen)) {
			if (widget.visible && widget.getClass().getName().equals(TAB)) {
				return ROOM;
			}
		}
		return 0;
	}
}
