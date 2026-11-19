package com.jaymospowers.client.power.web;

import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.power.web.WebSkim;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Skim the water, on the local player ({@link WebSkim} has the rules): movement is the client's to decide, so while a
 * skim lasts it keeps you moving at a cruising speed that eases off over the skim, and steers you gently toward where
 * you look while forward is held. A small bar under the crosshair shows how long is left.
 */
final class WebSkimClient {
	/** The cruising speed (blocks/tick) at the start of a skim and at its end, and how fast a faster entry eases down to it. */
	static final double CRUISE_START = 0.75, CRUISE_END = 0.4, EASE = 0.97;
	/** How much of the way toward the look direction forward turns you each tick. */
	static final double STEER = 0.12;
	static final int BAR_WIDTH = 60, ROW = 4;
	static final int WATER = 0xFF6FC8E8;

	private WebSkimClient() {
	}

	static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(WebSkimClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !WebSkim.skimming(player) || minecraft.isPaused()) {
			return;
		}
		WebSkim.Skim skim = player.getAttached(WebSkim.SKIM);
		double left = Mth.clamp((skim.until() - player.level().getGameTime()) / (double)skim.total(), 0.0, 1.0);
		double cruise = Mth.lerp(left, CRUISE_END, CRUISE_START);
		Vec3 d = player.getDeltaMovement();
		double speed = Math.hypot(d.x, d.z);
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		Vec3 heading = speed > 0.05 ? new Vec3(d.x / speed, 0.0, d.z / speed) : look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
		if (player.input.keyPresses.forward() && look.lengthSqr() > 1.0E-4) {
			Vec3 mixed = heading.scale(1.0 - STEER).add(look.normalize().scale(STEER));
			heading = mixed.lengthSqr() < 1.0E-4 ? heading : mixed.normalize();
		}
		double target = speed > cruise ? Math.max(cruise, speed * EASE) : cruise;
		player.setDeltaMovement(heading.x * target, d.y, heading.z * target);
	}

	/** The bar, from the power's HUD. */
	static void renderHud(GuiGraphicsExtractor graphics, LocalPlayer player) {
		WebSkim.Skim skim = player.getAttached(WebSkim.SKIM);
		if (skim == null || !WebSkim.skimming(player)) {
			HudZones.clear(HudZones.ABOVE, "jaymospowers:web_skim");
			return;
		}
		int[] at = HudZones.place(HudZones.ABOVE, "jaymospowers:web_skim", ROW, BAR_WIDTH, HudStyle.LABELLED_BAR_H);
		if (at == null) {
			return;
		}
		float share = Mth.clamp((skim.until() - player.level().getGameTime()) / (float)skim.total(), 0.0F, 1.0F);
		HudStyle.labelledBar(graphics, Minecraft.getInstance().font, Component.translatable("hud.jaymospowers.web_skim"), at[0], at[1], BAR_WIDTH, share,
			share < 0.25F ? HudStyle.WARN : WATER);
	}
}
