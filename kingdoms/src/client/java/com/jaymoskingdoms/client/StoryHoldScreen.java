package com.jaymoskingdoms.client;

import com.jaymoskingdoms.StoryGround;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * (0.7.236) Andy: "can the player be held in a stasis menu or faux load screen at the beginning while everything
 * loads?" A loading screen that can't be shut, up while the server holds a newcomer ({@link StoryGround.Hold}): their
 * realm's capital being raised, a whaler being fitted out, the future being made, the land where they woke loading.
 * MCA's creator waits behind it (it opens only on an empty screen), and the life story replaces it when it's asked.
 */
final class StoryHoldScreen extends Screen {
	/** Each dot of the "..." this long (ms), and the bar's sweep this long. */
	private static final long DOT_MS = 400L, SWEEP_MS = 1800L;
	private static final int BAR_W = 160, BAR_H = 4, BACKGROUND = 0xFF0C0C12, BAR_BACK = 0xFF2A2A34;

	/** What the server holds this client for (a lang key), or "" for nothing. */
	private static String holding = "";

	private StoryHoldScreen() {
		super(Component.translatable("gui.jaymoskingdoms.story.hold.title"));
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(StoryGround.Hold.TYPE, (payload, context) -> {
			holding = payload.key();
			Minecraft minecraft = context.client();
			if (!holding.isEmpty()) {
				StoryCurtain.raise();
			}
			if (holding.isEmpty()) {
				if (minecraft.gui.screen() instanceof StoryHoldScreen) {
					minecraft.gui.setScreen(null);
				}
			} else if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new StoryHoldScreen());
			}
		});
		// (up again if anything took its place and went, while they're still held)
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!holding.isEmpty() && minecraft.player != null && minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new StoryHoldScreen());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> holding = "");
	}

	static boolean holding() {
		return !holding.isEmpty();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		StoryCurtain.background(graphics, this);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (holding.isEmpty()) {
			this.onClose();
			return;
		}
		graphics.fill(0, 0, this.width, this.height, BACKGROUND);
		int cx = this.width / 2, cy = this.height / 2;
		long now = System.currentTimeMillis();
		graphics.centeredText(this.font, this.title, cx, cy - 24, Ui.GOLD);
		String dots = ".".repeat((int)(now / DOT_MS % 4));
		graphics.centeredText(this.font, Component.translatable(holding).append(dots), cx, cy - 6, Ui.TEXT);
		// a bar that sweeps back and forth: something's happening, not how much is left
		int x0 = cx - BAR_W / 2, y0 = cy + 12;
		graphics.fill(x0, y0, x0 + BAR_W, y0 + BAR_H, BAR_BACK);
		double t = now % SWEEP_MS / (double)SWEEP_MS;
		double phase = t < 0.5 ? t * 2.0 : 2.0 - t * 2.0;
		int seg = BAR_W / 4, sx = x0 + (int)((BAR_W - seg) * phase);
		graphics.fill(sx, y0, sx + seg, y0 + BAR_H, Ui.GOLD);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
