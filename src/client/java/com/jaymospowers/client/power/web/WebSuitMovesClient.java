package com.jaymospowers.client.power.web;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.web.WebGrind;
import com.jaymospowers.power.web.WebLunge;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebStilts;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebTempest;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The symbiote's suit moves from the player's side (Legends patch 4): the two remappable keys (Grind, Numpad - by default, and
 * Tempest, Numpad *), the click that starts a feeding lunge in a surge, and the stilts' input (a double tap of Jump to rise,
 * Jump held to go higher, Sneak to come down). Only what was pressed is sent; the server checks everything.
 */
final class WebSuitMovesClient {
	private static final int DOUBLE_TAP_TICKS = 7, TAP_HOLD_TICKS = 4;
	/** GLFW key codes of the numpad minus and times (InputConstants has no names for them). */
	private static final int KP_MULTIPLY = 332, KP_SUBTRACT = 333;

	private static KeyMapping grindKey, tempestKey;
	private static boolean jumpWas, attackWas, sentUp, sentDown, firstGrounded;
	private static int pressedAt = -100, releasedAt = -100, clock;

	private WebSuitMovesClient() {
	}

	static void register() {
		grindKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_grind", KP_SUBTRACT, JaymosPowersClient.CATEGORY));
		tempestKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_tempest", KP_MULTIPLY, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(WebSuitMovesClient::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		clock++;
		boolean ready = player != null && mc.gui.screen() == null && player.isAlive() && !player.isSpectator() && !Downed.isDowned(player)
			&& Powers.has(player, WebPower.INSTANCE) && WebSymbiote.isBonded(player) && !WebSymbioteClient.inputLocked();
		while (grindKey.consumeClick()) {
			if (ready && ClientPlayNetworking.canSend(WebGrind.Press.TYPE)) {
				ClientPlayNetworking.send(new WebGrind.Press());
			}
		}
		while (tempestKey.consumeClick()) {
			if (ready && ClientPlayNetworking.canSend(WebTempest.Press.TYPE)) {
				ClientPlayNetworking.send(new WebTempest.Press());
			}
		}
		boolean attack = mc.options.keyAttack.isDown();
		if (ready && attack && !attackWas && ClientPlayNetworking.canSend(WebLunge.Click.TYPE) && WebSymbiote.tendrilKeys(player)) {
			// (the click also swings as ever; the server decides whether it was a lunge)
			ClientPlayNetworking.send(new WebLunge.Click());
		}
		attackWas = attack;
		boolean jump = mc.options.keyJump.isDown();
		boolean sneak = mc.options.keyShift.isDown();
		boolean up = player != null && WebStilts.up(player);
		boolean toggle = false;
		if (ready && ClientPlayNetworking.canSend(WebStilts.Input.TYPE)) {
			if (jump && !jumpWas) {
				// a second press right after a short tap is the double tap
				// the ground test is for the first tap (it is the jump); the second lands in the air
				if (clock - releasedAt <= DOUBLE_TAP_TICKS && releasedAt - pressedAt <= TAP_HOLD_TICKS && WebSymbiote.tendrilKeys(player)
					&& (firstGrounded || up)) {
					toggle = true;
					com.jaymospowers.client.freerun.AirFlipClient.cancelTap();
				}
				if (!toggle) {
					firstGrounded = player.onGround();
				}
				pressedAt = clock;
			} else if (!jump && jumpWas) {
				releasedAt = clock;
			}
			boolean holdUp = jump && up, holdDown = sneak && up;
			if (toggle || holdUp != sentUp || holdDown != sentDown) {
				ClientPlayNetworking.send(new WebStilts.Input(toggle, jump, sneak));
				sentUp = holdUp;
				sentDown = holdDown;
			}
		}
		jumpWas = jump;
	}
}
