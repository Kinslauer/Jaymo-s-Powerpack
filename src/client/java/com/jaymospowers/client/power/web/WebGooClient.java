package com.jaymospowers.client.power.web;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.power.web.WebGoo;
import com.jaymospowers.power.web.WebPuddle;
import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

/**
 * The client's side of the goo moves: a key for each ({@code P} puddle, {@code O} spit, {@code ;} wall; all rebindable),
 * the puddle's drawing layer, and a nudge to every player's box when a puddle forms or pours back (the box comes from
 * their synced state, but nothing else tells the client to measure it again).
 */
final class WebGooClient {
	private static KeyMapping puddleKey;
	private static KeyMapping spitKey;
	private static KeyMapping wallKey;
	/** Players seen puddled last tick (entity ids). */
	private static final Set<Integer> PUDDLED = new HashSet<>();

	private WebGooClient() {
	}

	static void init() {
		puddleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_goo_puddle", InputConstants.KEY_P, JaymosPowersClient.CATEGORY));
		spitKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_goo_spit", InputConstants.KEY_O, JaymosPowersClient.CATEGORY));
		wallKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.web_goo_wall", InputConstants.KEY_SEMICOLON, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(WebGooClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PUDDLED.clear());
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new WebPuddlePose(avatar));
			}
		});
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		if (self == null || minecraft.level == null) {
			return;
		}
		boolean free = WebSymbiote.isBonded(self) && minecraft.gui.screen() == null && !WebSymbioteClient.inputLocked() && !self.isSpectator()
			&& !minecraft.isPaused();
		press(puddleKey, free, WebGoo.PUDDLE);
		press(spitKey, free, WebGoo.SPIT);
		press(wallKey, free, WebGoo.WALL);
		for (AbstractClientPlayer player : minecraft.level.players()) {
			boolean now = WebPuddle.puddled(player);
			if (now ? PUDDLED.add(player.getId()) : PUDDLED.remove(player.getId())) {
				player.refreshDimensions();
			}
		}
	}

	private static void press(KeyMapping key, boolean free, int move) {
		while (key.consumeClick()) {
			if (free && ClientPlayNetworking.canSend(WebGoo.Use.TYPE)) {
				ClientPlayNetworking.send(new WebGoo.Use(move));
			}
		}
	}
}
