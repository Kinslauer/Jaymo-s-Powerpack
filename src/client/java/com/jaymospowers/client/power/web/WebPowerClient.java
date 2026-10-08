package com.jaymospowers.client.power.web;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.web.WebAnchors;
import com.jaymospowers.power.web.WebCooldowns;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebPower;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Web Slinger, client side: swing physics (see {@link WebSwing}), the web lines, and the HUD. */
public final class WebPowerClient implements PowerClient {
	public static final WebPowerClient INSTANCE = new WebPowerClient();

	final WebSwing swing = new WebSwing();
	/** What the swing key would stick to right now, for the reticle (refreshed every other tick). */
	private WebAnchors.@Nullable Anchor preview;

	private WebPowerClient() {
	}

	/** Called once from {@link com.jaymospowers.client.JaymosPowersClient}: renderers, client packets, the PowerClient. */
	public static void init() {
		PowerClients.register(WebPower.ID, INSTANCE);
		EntityRenderers.register(WebPower.WEB_BALL, context -> new ThrownItemRenderer<>(context, 0.85F, false));
		LevelRenderEvents.COLLECT_SUBMITS.register(WebLineRenderer::render);
		ClientPlayNetworking.registerGlobalReceiver(WebNet.SwingReject.TYPE, (payload, context) -> {
			if (isActive(context.player())) {
				INSTANCE.swing.rejected(context.player());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(WebNet.Pull.TYPE, (payload, context) -> {
			if (isActive(context.player())) {
				INSTANCE.swing.startPull(context.player(), payload);
			}
		});
	}

	static boolean isActive(@Nullable LocalPlayer player) {
		return player != null && Powers.has(player, WebPower.INSTANCE);
	}

	@Override
	public void onActiveChanged(LocalPlayer player, boolean active) {
		swing.reset();
		preview = null;
	}

	@Override
	public void onKey(LocalPlayer player, PowerKey key, boolean down) {
		if (key != PowerKey.ABILITY_1 || player.isSpectator()) {
			return;
		}
		if (down) {
			swing.press(player);
		} else {
			swing.releaseKey(player);
		}
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		swing.tick(minecraft, player);
		if (swing.attached || swing.pull != null || player.isSpectator()) {
			preview = null;
		} else if (player.tickCount % 2 == 0) {
			preview = WebAnchors.find(player);
		}
	}

	// ---- HUD --------------------------------------------------------------------------------------------------------

	private static final int RETICLE_LIVE = 0xFFFFFFFF;
	private static final int RETICLE_ASSIST = 0xFFF06A72;
	private static final int RETICLE_NONE = 0x50FFFFFF;
	private static final int RETICLE_ATTACHED = 0xFFE0303A;

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;

		// anchor reticle: four corner ticks around the crosshair
		int color;
		int r;
		if (swing.attached || swing.pull != null) {
			color = RETICLE_ATTACHED;
			r = 5;
		} else if (preview == null) {
			color = RETICLE_NONE;
			r = 7;
		} else {
			color = preview.assisted() ? RETICLE_ASSIST : RETICLE_LIVE;
			r = 6;
		}
		corner(graphics, cx - r, cy - r, 1, 1, color);
		corner(graphics, cx + r, cy - r, -1, 1, color);
		corner(graphics, cx - r, cy + r, 1, -1, color);
		corner(graphics, cx + r, cy + r, -1, -1, color);

		// cooldown chips under the crosshair (clear of vanilla's attack indicator)
		WebCooldowns cooldowns = player.getAttached(WebPower.COOLDOWNS);
		if (cooldowns == null) {
			cooldowns = WebCooldowns.NONE;
		}
		long now = player.level().getGameTime();
		Font font = minecraft.font;
		int chipW = 18;
		int gap = 3;
		int y = cy + 20;
		int x = cx - (chipW * 3 + gap * 2) / 2;
		chip(graphics, font, x, y, chipW, PowerKey.ABILITY_2, cooldowns.zip() - now, WebPower.ZIP_COOLDOWN);
		chip(graphics, font, x + chipW + gap, y, chipW, PowerKey.ABILITY_3, cooldowns.shot() - now, WebPower.SHOT_COOLDOWN);
		chip(graphics, font, x + (chipW + gap) * 2, y, chipW, PowerKey.ABILITY_4, cooldowns.yank() - now, WebPower.YANK_COOLDOWN);
	}

	private static void corner(GuiGraphicsExtractor graphics, int x, int y, int dx, int dy, int color) {
		// an L of two 3px arms from the pixel (x, y), pointing along dx and dy
		int x0 = dx > 0 ? x : x - 2;
		int y0 = dy > 0 ? y : y - 2;
		graphics.fill(x0, y, x0 + 3, y + 1, color);
		graphics.fill(x, y0, x + 1, y0 + 3, color);
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, PowerKey key, long left, int total) {
		int h = 11;
		boolean cooling = left > 0;
		graphics.fill(x, y, x + w, y + h, cooling ? 0x90000000 : 0x60000000);
		if (cooling) {
			int filled = (int)Math.ceil(w * Math.min(1.0, left / (double)total));
			graphics.fill(x, y + h - 2, x + filled, y + h, 0xFFE0303A);
		} else {
			graphics.fill(x, y + h - 1, x + w, y + h, 0xC0FFFFFF);
		}
		Component label = JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage();
		String text = label.getString();
		if (text.length() > 3) {
			text = text.substring(0, 3);
		}
		graphics.centeredText(font, text, x + w / 2, y + 1, cooling ? 0xFF8A8A8A : 0xFFFFFFFF);
	}
}
