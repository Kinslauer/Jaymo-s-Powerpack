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
	static final int HEARING_OPEN = 0x8CE0A8, HEARING_CLOSED = 0x8A8A8A;
	/** Voice chat's heads are this far apart in its row (its own spacing). */
	static final int ICON_STEP = 11;
	/** Youth's face for that row (kingdoms/tools/gen_youth_icon.py). */
	static final Identifier ICON = JaymosKingdoms.id("textures/gui/youth_icon.png");
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
	 * While you talk in voice chat, Youth sits in voice chat's own player row (Andy: "it should just go in the existing
	 * box that we have for players"): Simple Voice Chat's group icons, top left by default, one head every 11 pixels. Its
	 * face goes in the next place after the group's heads (the first place when you're in no group), framed in its
	 * leaf-green when it can hear you, grey when it can't ({@link com.jaymoskingdoms.inhabitants.YouthEars}). Where that
	 * row is, how big and which way it runs come from voice chat's own client settings, read by name
	 * ({@link #voiceRow}); without them, its defaults. It fades out once you stop.
	 */
	private static void drawHearing(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		long since = net.minecraft.util.Util.getMillis() - hearingAt;
		if (since < 0 || since >= HEARING_MS + HEARING_FADE_MS) {
			return;
		}
		float k = since < HEARING_MS ? 1.0F : 1.0F - (since - HEARING_MS) / (float) HEARING_FADE_MS;
		int alpha = Math.max(8, Math.round(255 * k));
		VoiceRow row = voiceRow();
		var pose = graphics.pose();
		pose.pushMatrix();
		if (row.x() < 0) {
			pose.translate(graphics.guiWidth(), 0.0F);
		}
		if (row.y() < 0) {
			pose.translate(0.0F, graphics.guiHeight());
		}
		pose.translate(row.x(), row.y());
		pose.scale(row.scale(), row.scale());
		float step = ICON_STEP * row.index() * (row.vertical() ? (row.y() < 0 ? -1 : 1) : (row.x() < 0 ? -1 : 1));
		pose.translate(row.vertical() ? 0.0F : step, row.vertical() ? step : 0.0F);
		int ox = row.x() < 0 ? -10 : 0, oy = row.y() < 0 ? -10 : 0;
		int frame = alpha << 24 | (hearing ? HEARING_OPEN : HEARING_CLOSED);
		graphics.fill(ox, oy, ox + 10, oy + 1, frame);
		graphics.fill(ox, oy + 9, ox + 10, oy + 10, frame);
		graphics.fill(ox, oy + 1, ox + 1, oy + 9, frame);
		graphics.fill(ox + 9, oy + 1, ox + 10, oy + 9, frame);
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ICON, ox + 1, oy + 1, 0.0F, 0.0F, 8, 8, 8, 8,
			(hearing ? alpha : alpha / 2) << 24 | 0xFFFFFF);
		pose.popMatrix();
	}

	/** Where voice chat's player row is: its x and y (negative: from the right or bottom), scale, direction, and Youth's place in it. */
	record VoiceRow(int x, int y, float scale, boolean vertical, int index) {
	}

	/**
	 * Simple Voice Chat's group icon settings (its client config) and how many heads its row shows now, read by name since
	 * it has no API for them; its defaults when it isn't there or anything is off.
	 */
	static VoiceRow voiceRow() {
		try {
			Class<?> client = Class.forName("de.maxhenkel.voicechat.VoicechatClient");
			Object config = client.getField("CLIENT_CONFIG").get(null);
			int x = ((Number) entry(config, "groupPlayerIconPosX")).intValue(), y = ((Number) entry(config, "groupPlayerIconPosY")).intValue();
			float scale = ((Number) entry(config, "groupHudIconScale")).floatValue();
			boolean vertical = "VERTICAL".equals(String.valueOf(entry(config, "groupPlayerIconOrientation")));
			boolean own = (Boolean) entry(config, "showOwnGroupIcon");
			Object members = Class.forName("de.maxhenkel.voicechat.voice.client.GroupChatManager").getMethod("getGroupMembers", boolean.class)
				.invoke(null, own);
			return new VoiceRow(x, y, scale, vertical, ((java.util.List<?>) members).size());
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return new VoiceRow(4, 4, 2.0F, true, 0);
		}
	}

	private static Object entry(Object config, String name) throws ReflectiveOperationException {
		Object entry = config.getClass().getField(name).get(config);
		return entry.getClass().getMethod("get").invoke(entry);
	}

	private static void drawFlash(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		long since = net.minecraft.util.Util.getMillis() - flashAt;
		if (since >= 0 && since < FLASH_MS) {
			float k = 1.0F - since / (float) FLASH_MS;
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(FLASH_ALPHA * k * k) << 24 | FLASH_COLOUR);
		}
	}
}
