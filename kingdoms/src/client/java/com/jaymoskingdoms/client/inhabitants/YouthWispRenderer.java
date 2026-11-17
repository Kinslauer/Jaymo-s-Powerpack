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
	/** The "can Youth hear you" line: shown this long past the server's last word (it sends one every 0.4 s while you
	 * talk), then faded over this long; its height from the top, and its colours. */
	static final long HEARING_MS = 700L, HEARING_FADE_MS = 400L;
	static final int HEARING_Y = 3, HEARING_OPEN = 0xBEF0D6, HEARING_CLOSED = 0x9A9A9A;
	private static long hearingAt = -HEARING_MS - HEARING_FADE_MS;
	private static boolean hearing;

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
		ClientPlayNetworking.registerGlobalReceiver(com.jaymoskingdoms.inhabitants.YouthEars.Hearing.TYPE, (payload, context) -> {
			hearingAt = net.minecraft.util.Util.getMillis();
			hearing = payload.open();
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("youth_hearing"), YouthWispRenderer::drawHearing);
	}

	/**
	 * While you talk in voice chat, a line at the top of the screen: "Youth can hear you" in its leaf-green, or "Youth
	 * can't hear voices here" in grey ({@link com.jaymoskingdoms.inhabitants.YouthEars}). It fades out once you stop.
	 */
	private static void drawHearing(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		long since = net.minecraft.util.Util.getMillis() - hearingAt;
		if (since < 0 || since >= HEARING_MS + HEARING_FADE_MS) {
			return;
		}
		float k = since < HEARING_MS ? 1.0F : 1.0F - (since - HEARING_MS) / (float) HEARING_FADE_MS;
		int alpha = Math.max(8, Math.round(255 * k));
		net.minecraft.network.chat.Component line = net.minecraft.network.chat.Component.translatable(
			hearing ? "hud.jaymoskingdoms.youth_hearing.open" : "hud.jaymoskingdoms.youth_hearing.closed");
		net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
		int x = (graphics.guiWidth() - font.width(line)) / 2;
		graphics.text(font, line, x, HEARING_Y, alpha << 24 | (hearing ? HEARING_OPEN : HEARING_CLOSED), true);
	}

	private static void drawFlash(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		long since = net.minecraft.util.Util.getMillis() - flashAt;
		if (since >= 0 && since < FLASH_MS) {
			float k = 1.0F - since / (float) FLASH_MS;
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(FLASH_ALPHA * k * k) << 24 | FLASH_COLOUR);
		}
	}
}
