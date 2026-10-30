package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MoonCurse;
import com.jaymoskingdoms.Moons;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.7.415) The moon-cursed player's screen under a blood moon ({@link MoonCurse#feral}): a red wash with dark red edges that
 * fades in with the moon ({@link Moons#clientFade}) and throbs slowly, like a pulse.
 */
final class MoonCurseClient {
	/** The red wash over everything and the edges, at full; how fast it throbs and how much of it the throb takes. */
	static final float WASH = 0.20F, EDGE = 0.75F, THROB_SPEED = 0.17F, THROB = 0.3F;
	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");

	private MoonCurseClient() {
	}

	static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("moon_curse"), MoonCurseClient::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !MoonCurse.feral(player)) {
			return;
		}
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		float k = Mth.clamp(Moons.clientFade, 0.0F, 1.0F) * (1.0F - THROB + THROB * Mth.sin(time * THROB_SPEED));
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.colorFromFloat(WASH * k, 0.8F, 0.04F, 0.04F));
		float e = EDGE * k;
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight(), graphics.guiWidth(),
			graphics.guiHeight(), ARGB.colorFromFloat(1.0F, e, 0.0F, 0.0F));
	}
}
