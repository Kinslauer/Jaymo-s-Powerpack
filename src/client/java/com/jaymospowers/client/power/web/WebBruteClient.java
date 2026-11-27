package com.jaymospowers.client.power.web;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.ScreenShake;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.web.WebBruteArts;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;

/** The big body's remappable moves and the local camera's response to its strain. */
final class WebBruteClient {
	private static final int[] DEFAULTS = {InputConstants.KEY_F6, InputConstants.KEY_F10,
		InputConstants.KEY_F12, InputConstants.KEY_F8, InputConstants.KEY_PAUSE,
		InputConstants.KEY_NUMLOCK, InputConstants.KEY_SCROLLLOCK};
	private static final KeyMapping[] KEYS = new KeyMapping[DEFAULTS.length];

	private WebBruteClient() {
	}

	static void register() {
		for (WebBruteArts.Art art : WebBruteArts.Art.values()) {
			int i = art.ordinal();
			KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_" + art.key,
				DEFAULTS[i], JaymosPowersClient.CATEGORY));
		}
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			LocalPlayer player = mc.player;
			boolean ready = player != null && mc.gui.screen() == null && player.isAlive() && !player.isSpectator()
				&& !Downed.isDowned(player) && Powers.has(player, WebPower.INSTANCE) && WebSymbiote.bulked(player)
				&& !WebSymbioteClient.inputLocked();
			for (int i = 0; i < KEYS.length; i++) {
				while (KEYS[i].consumeClick()) {
					if (ready && ClientPlayNetworking.canSend(WebBruteArts.Cast.TYPE)) {
						ClientPlayNetworking.send(new WebBruteArts.Cast(i));
					}
				}
			}
			if (player == null) {
				return;
			}
			long now = player.level().getGameTime();
			Long rhythm = player.getAttached(WebSymbiote.RHYTHM);
			if (rhythm != null && now >= rhythm && (now - rhythm) % WebSymbiote.BEAT_PERIOD == 0) {
				ScreenShake.add(0.38F);
				WebSwing.play(player, net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.1F);
			}
			WebPose pose = player.getAttached(WebPower.POSE);
			if (pose != null && pose.kind() == WebPose.BRUTE_NOVA) {
				long age = now - pose.start();
				if (age >= 0 && age < WebBruteArts.NOVA_WINDUP && age % 5 == 0) {
					ScreenShake.add(0.24F + age * 0.014F);
				}
			}
		});
	}
}
