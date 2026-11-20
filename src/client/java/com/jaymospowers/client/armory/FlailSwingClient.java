package com.jaymospowers.client.armory;

import com.jaymospowers.armory.Lash;
import com.jaymospowers.client.HeldInput;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Balkon's flail's left click on this side ({@link Lash#windUp}): the press tells the server to wind the ball up, the
 * let-go how long it was held (a tap jabs, a hold swings), each after an {@link Lash.Aim} with where the ball really is
 * and how the mouse moved. With Sneak held the click is left alone (executions, shaking someone awake, a plain blow),
 * and so is a click some other hold has the attack key for ({@link HeldInput#locksAttack}).
 */
public final class FlailSwingClient {
	private static boolean wasDown, pressing;
	private static int held;

	private FlailSwingClient() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(FlailSwingClient::tick);
	}

	/** Whether {@code player}'s left click is the flail's own now (so no plain swing, hit or digging goes with it). */
	static boolean takes(Player player) {
		return Lash.isBallFlail(player.getMainHandItem()) && !player.isShiftKeyDown() && !player.isSpectator();
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean down = minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null;
		boolean pressed = down && !wasDown;
		wasDown = down;
		boolean ours = player != null && takes(player) && !player.isUsingItem();
		if (pressing) {
			if (down && ours) {
				held++;
				return;
			}
			pressing = false;
			if (player != null && ClientPlayNetworking.canSend(Lash.Swing.TYPE)) {
				LashRenderer.aim(player, true);
				ClientPlayNetworking.send(new Lash.Swing(false, held));
			}
			return;
		}
		if (pressed && ours && !HeldInput.locksAttack(minecraft) && ClientPlayNetworking.canSend(Lash.Swing.TYPE)) {
			pressing = true;
			held = 0;
			LashRenderer.aim(player, true);
			ClientPlayNetworking.send(new Lash.Swing(true, 0));
		}
	}
}
