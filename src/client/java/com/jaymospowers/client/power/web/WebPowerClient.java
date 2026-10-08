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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
		// every player's arms and legs (theirs and others'), whether or not we're a web slinger ourselves
		ClientTickEvents.END_CLIENT_TICK.register(WebArms::tick);
		ClientPlayNetworking.registerGlobalReceiver(WebNet.SwingReject.TYPE, (payload, context) -> {
			if (isActive(context.player())) {
				INSTANCE.swing.rejected(context.player(), payload.hand());
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
		if (player.isSpectator()) {
			return;
		}
		int hand;
		if (key == PowerKey.ABILITY_1) {
			hand = WebNet.MAIN;
		} else if (key == PowerKey.ABILITY_2 && (!down || WebPower.offHandFree(player))) {
			// G is the off-hand web while the off hand is empty (otherwise the server zips)
			hand = WebNet.OFF;
		} else {
			return;
		}
		if (down) {
			swing.press(player, hand);
		} else {
			swing.releaseKey(player, hand);
		}
	}

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		swing.tick(minecraft, player);
		if (swing.attached() || swing.pull != null || player.isSpectator()) {
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
		if (swing.attached() || swing.pull != null) {
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

		// cooldown chips right of the hotbar (same corner and style as Wind Control's)
		WebCooldowns cooldowns = player.getAttached(WebPower.COOLDOWNS);
		if (cooldowns == null) {
			cooldowns = WebCooldowns.NONE;
		}
		long now = player.level().getGameTime();
		Font font = minecraft.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int total = CHIP * 4 + CHIP_GAP * 3;
		// past the off-hand slot / attack indicator; above the hunger bar on narrow screens
		int x = cx + 91 + 30;
		int y = height - CHIP - 1;
		if (x + total > width - 2) {
			x = cx + 91 - total;
			y = height - 22 - 30 - CHIP;
		}
		// web wings (hold Jump in mid-air): lit while spread, shaded through their cooldown
		wingsChip(graphics, font, minecraft, x, y);
		x += CHIP + CHIP_GAP;
		// G: the off-hand web (no cooldown; lit while it's out) or, with something in the off hand, Zip
		if (WebPower.offHandFree(player)) {
			chip(graphics, font, x, y, PowerKey.ABILITY_2, Items.STRING, 0L, 1);
			if (swing.strands[WebNet.OFF].attached) {
				graphics.fill(x + 1, y + CHIP - 3, x + CHIP - 1, y + CHIP - 1, 0xFF000000 | CHIP_COLOR);
			}
		} else {
			chip(graphics, font, x, y, PowerKey.ABILITY_2, Items.FISHING_ROD, cooldowns.zip() - now, WebPower.ZIP_COOLDOWN);
		}
		chip(graphics, font, x + CHIP + CHIP_GAP, y, PowerKey.ABILITY_3, Items.COBWEB, cooldowns.shot() - now, WebPower.SHOT_COOLDOWN);
		chip(graphics, font, x + (CHIP + CHIP_GAP) * 2, y, PowerKey.ABILITY_4, Items.LEAD, cooldowns.yank() - now, WebPower.YANK_COOLDOWN);
	}

	private void wingsChip(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, int x, int y) {
		boolean open = swing.wingsTicks > 0;
		boolean cooling = swing.wingsCooldown > 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, open || !cooling ? 0xFF000000 | CHIP_COLOR : 0xA0505050);
		graphics.item(new ItemStack(Items.PHANTOM_MEMBRANE), x + 2, y + 2);
		if (open) {
			int w = (int)Math.ceil((CHIP - 2) * swing.wingsTicks / (double)WebPower.WINGS_TICKS);
			graphics.fill(x + 1, y + CHIP - 3, x + 1 + w, y + CHIP - 1, 0xFF000000 | CHIP_COLOR);
		} else if (cooling) {
			int shade = (int)Math.ceil(16 * swing.wingsCooldown / (double)WebPower.WINGS_COOLDOWN);
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = String.format(Locale.ROOT, "%.1f", swing.wingsCooldown / 20.0);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		String label = font.plainSubstrByWidth(minecraft.options.keyJump.getTranslatedKeyMessage().getString(), CHIP - 2);
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, cooling ? 0xFF909090 : 0xFFFFFFFF);
	}

	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int CHIP_COLOR = 0xE0303A;

	private static void corner(GuiGraphicsExtractor graphics, int x, int y, int dx, int dy, int color) {
		// an L of two 3px arms from the pixel (x, y), pointing along dx and dy
		int x0 = dx > 0 ? x : x - 2;
		int y0 = dy > 0 ? y : y - 2;
		graphics.fill(x0, y, x0 + 3, y + 1, color);
		graphics.fill(x, y0, x + 1, y0 + 3, color);
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, PowerKey key, Item icon, long left, int total) {
		if (!JaymosPowersClient.isUnlocked(key)) {
			// not mastered yet: an empty dark slot with just the key
			graphics.fill(x, y, x + CHIP, y + CHIP, 0xB0000000);
			graphics.outline(x, y, CHIP, CHIP, 0x60505050);
			String label = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString(), CHIP - 2);
			graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, 0xFF505050);
			return;
		}
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | CHIP_COLOR : 0xA0505050);
		graphics.item(new ItemStack(icon), x + 2, y + 2);
		if (!ready) {
			int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)total));
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = left >= 200 ? Long.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		// the bound key, bottom-right like an item count
		String label = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString(), CHIP - 2);
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}
}
