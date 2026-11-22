package com.jaymospowers.client.bodymenu;

import com.jaymospowers.core.BodyMenu;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * The body menu's client side ({@link BodyMenu}): it times each press of use that lands on a body untaken (the server's say
 * on what's a body is the same, both sides), and a press let go within {@link BodyMenu#TAP_TICKS} is a tap, told to the
 * server, which answers with the menu ({@link BodyMenuScreen}). Held longer, it's the drag's (or the revive's) as ever.
 */
public final class BodyMenuClient {
	/** The press being timed: on which entity, from which client tick; -1 for none. */
	private static int pressed = -1;
	private static long pressedAt, lastSeen, ticks;
	/** Whether use was down at the end of the last tick: a press then is a hold's repeat, not a fresh click. */
	private static boolean useWasDown;

	private BodyMenuClient() {
	}

	public static void init() {
		BodyMenu.clientPress = entity -> {
			if (pressed == entity.getId() && ticks - lastSeen <= 6) {
				lastSeen = ticks;
			} else if (!useWasDown) {
				// (only a fresh click: use held on from before, say through a lasso's tying, opens nothing when let go)
				pressed = entity.getId();
				pressedAt = ticks;
				lastSeen = ticks;
			}
		};
		ClientPlayNetworking.registerGlobalReceiver(BodyMenu.Menu.TYPE, (menu, context) -> {
			Minecraft minecraft = context.client();
			if (minecraft.gui.screen() == null && minecraft.player != null && minecraft.player.level().getEntity(menu.entity()) != null) {
				minecraft.gui.setScreen(new BodyMenuScreen(menu));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(BodyMenuClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		useWasDown = minecraft.options.keyUse.isDown();
		if (pressed < 0) {
			return;
		}
		if (minecraft.player == null || minecraft.gui.screen() != null) {
			pressed = -1;
			return;
		}
		if (minecraft.options.keyUse.isDown()) {
			if (ticks - pressedAt > BodyMenu.TAP_TICKS) {
				// held: a drag's or a revive's, not the menu's
				pressed = -1;
			}
			return;
		}
		if (ticks - pressedAt <= BodyMenu.TAP_TICKS && !minecraft.player.isShiftKeyDown()) {
			ClientPlayNetworking.send(new BodyMenu.Tap(pressed));
		}
		pressed = -1;
	}
}
