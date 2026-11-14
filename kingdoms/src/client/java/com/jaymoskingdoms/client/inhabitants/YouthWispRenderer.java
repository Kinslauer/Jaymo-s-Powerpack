package com.jaymoskingdoms.client.inhabitants;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.inhabitants.YouthWisp;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Youth, the wood-wisp: drawn see-through and glowing (its wings and trail are half-there light), and lit as if it stood
 * in full light wherever it is, so it shows in a dark cave. Its head turns to whoever it faces. Someone it carries home
 * ({@link YouthWisp.HomeFlash}) sees the screen flash green and fade.
 */
public class YouthWispRenderer extends InhabitantRenderer<YouthWisp> {
	/** Its size against the model, and its shadow (blocks): a faint one, being mostly light. */
	static final float SCALE = 1.0F, SHADOW = 0.15F;
	/** The green flash of a trip home: how long it fades (ms), its strongest alpha (0-255), and its colour (0xRRGGBB). */
	static final long FLASH_MS = 900L;
	static final int FLASH_ALPHA = 200, FLASH_COLOUR = 0x5CE07A;
	private static long flashAt = -FLASH_MS;

	public YouthWispRenderer(EntityRendererProvider.Context context) {
		super(context, "youth_wisp", SCALE, SHADOW, "head");
	}

	@Override
	public RenderType getRenderType(LivingEntityRenderState renderState, Identifier texture) {
		return RenderTypes.entityTranslucentEmissive(texture);
	}

	@Override
	protected int getBlockLightLevel(YouthWisp entity, BlockPos pos) {
		return 15;
	}

	static void registerFlash() {
		ClientPlayNetworking.registerGlobalReceiver(YouthWisp.HomeFlash.TYPE, (payload, context) -> flashAt = net.minecraft.util.Util.getMillis());
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("youth_home_flash"), YouthWispRenderer::drawFlash);
	}

	private static void drawFlash(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		long since = net.minecraft.util.Util.getMillis() - flashAt;
		if (since >= 0 && since < FLASH_MS) {
			float k = 1.0F - since / (float) FLASH_MS;
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(FLASH_ALPHA * k * k) << 24 | FLASH_COLOUR);
		}
	}
}
