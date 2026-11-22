package com.jaymoskingdoms.client.forms;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.forms.Lasso;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * The rider's strike at the hogtied captive slung across their horse ({@link Lasso.StrikeCaptive}). Its own key in Controls,
 * on the attack button by default (it's a hit, and that button does nothing else to a body on your horse's back): it only
 * does anything while you ride a horse with a hogtied one over it, and the attack goes on as usual alongside.
 */
public final class LassoStrikeKey {
	/** A slung body is held at its horse's feet: this near (blocks, squared) counts as on it. */
	static final double ON_HORSE_SQR = 2.0 * 2.0;

	private static KeyMapping key;

	private LassoStrikeKey() {
	}

	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.strike_captive", InputConstants.Type.MOUSE,
			InputConstants.MOUSE_BUTTON_LEFT, KeyMapping.Category.register(JaymosKingdoms.id("lasso"))));
		ClientTickEvents.END_CLIENT_TICK.register(LassoStrikeKey::tick);
	}

	private static void tick(Minecraft minecraft) {
		boolean pressed = false;
		while (key.consumeClick()) {
			pressed = true;
		}
		LocalPlayer player = minecraft.player;
		if (!pressed || player == null || minecraft.level == null || minecraft.gui.screen() != null
			|| !(player.getVehicle() instanceof AbstractHorse horse) || !ClientPlayNetworking.canSend(Lasso.StrikeCaptive.TYPE)) {
			return;
		}
		for (Entity body : minecraft.level.getEntities(horse, horse.getBoundingBox().inflate(1.0), e -> e != player && Lasso.hogtied(e))) {
			if (PowersCompat.carried(body) && body.distanceToSqr(horse) < ON_HORSE_SQR) {
				ClientPlayNetworking.send(new Lasso.StrikeCaptive());
				return;
			}
		}
	}
}
