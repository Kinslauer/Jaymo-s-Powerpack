package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import com.jaymoskingdoms.Rites;
import com.jaymoskingdoms.Shrine;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.7.509) The boon key (left square bracket, rebindable; the full stop is Shoulder Surfing's swap shoulder) and the shrine's screen: {@code Patrons.OpenShrine} opens the patrons screen
 * with that shrine's patron picked. It is not Z: sneak and Z already wrap a tail and start a Merging in Jaymo's Powers. It acts only on the
 * ground, with no screen open and a patron sworn.
 */
final class PatronKeys {
	private static KeyMapping boon;

	private PatronKeys() {
	}

	static void register() {
		boon = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.boon", InputConstants.KEY_LBRACKET,
			KeyMapping.Category.register(JaymosKingdoms.id("patrons"))));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (boon.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null && minecraft.player.onGround()
					&& Patrons.of(minecraft.player) != null) {
					minecraft.gui.setScreen(new BoonScreen());
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(Rites.OpenRite.TYPE, (payload, context) -> context.client().gui.setScreen(new RiteScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(Patrons.OpenShrine.TYPE, (payload, context) -> {
			Patron p = null;
			if (context.client().level != null) {
				BlockState state = context.client().level.getBlockState(payload.pos());
				if (state.getBlock() instanceof Shrine) {
					p = state.getValue(Shrine.DEDICATION).patron();
				}
			}
			context.client().gui.setScreen(new PatronScreen(null, p));
		});
	}
}
