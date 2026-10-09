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
		WebSymbioteClient.init();
		EntityRenderers.register(WebPower.WEB_BALL, context -> new ThrownItemRenderer<>(context, 0.85F, false));
		LevelRenderEvents.COLLECT_SUBMITS.register(WebLineRenderer::render);
		// every player's arms and legs (theirs and others'), whether or not we're a web slinger ourselves
		ClientTickEvents.END_CLIENT_TICK.register(WebArms::tick);
		ClientTickEvents.END_CLIENT_TICK.register(WebLineRenderer::tick);
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
		WebSymbioteClient.reset();
	}

	/** Game time the local player started charging a Web Shot (-1 when not), and when the last shot went off. */
	private long shotChargeStart = -1;
	private long shotFiredAt;

	@Override
	public void onKey(LocalPlayer player, PowerKey key, boolean down) {
		if (player.isSpectator()) {
			return;
		}
		if (key == PowerKey.ABILITY_3) {
			long now = player.level().getGameTime();
			if (down) {
				shotChargeStart = now;
			} else {
				if (shotChargeStart >= 0) {
					shotFiredAt = now;
				}
				shotChargeStart = -1;
			}
			return;
		}
		int hand;
		boolean tendrils = com.jaymospowers.power.web.WebSymbiote.suitShown(player);
		if (key == PowerKey.ABILITY_1) {
			hand = WebNet.MAIN;
		} else if (key == PowerKey.ABILITY_2 && (!down || tendrils || WebPower.offHandFree(player))) {
			// G is the off-hand web (it needs an empty off hand)
			hand = WebNet.OFF;
		} else {
			return;
		}
		// the suit on: the keys are tendrils. On something grabbable, they grab it (and throw it on release)
		if (tendrils && down) {
			net.minecraft.world.entity.Entity target = grabTarget(player);
			if (target != null) {
				grabbing[hand] = true;
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.jaymospowers.power.web.WebTendrils.Grab(hand, target.getId()));
				return;
			}
		}
		if (!down && grabbing[hand]) {
			grabbing[hand] = false;
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.jaymospowers.power.web.WebTendrils.Grab(hand, -1));
			return;
		}
		if (down) {
			swing.press(player, hand);
		} else {
			swing.releaseKey(player, hand);
		}
	}

	/** Which tendril is holding something (so its key's release throws instead of ending a swing). */
	private final boolean[] grabbing = new boolean[2];

	/** What a tendril would grab: the creature, player, item or corpse under the crosshair, close by. */
	private static net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity grabTarget(LocalPlayer player) {
		net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
		net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F);
		net.minecraft.world.phys.Vec3 end = eye.add(look.scale(com.jaymospowers.power.web.WebTendrils.GRAB_RANGE));
		net.minecraft.world.phys.BlockHitResult block = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			end = block.getLocation();
		}
		// a generous aim: every candidate's box is fattened up for the tendrils
		net.minecraft.world.entity.Entity best = null;
		double bestDistance = eye.distanceToSqr(end);
		for (net.minecraft.world.entity.Entity e : player.level().getEntities(player,
			player.getBoundingBox().expandTowards(look.scale(com.jaymospowers.power.web.WebTendrils.GRAB_RANGE)).inflate(GRAB_AIM_INFLATE + 1.0),
			e -> com.jaymospowers.power.web.WebTendrils.grabbable(player, e))) {
			java.util.Optional<net.minecraft.world.phys.Vec3> at = e.getBoundingBox().inflate(GRAB_AIM_INFLATE).clip(eye, end);
			if (at.isPresent() && eye.distanceToSqr(at.get()) < bestDistance) {
				bestDistance = eye.distanceToSqr(at.get());
				best = e;
			}
		}
		return best;
	}

	/** How much fatter things are to the tendrils' aim (blocks, each side). */
	private static final double GRAB_AIM_INFLATE = 0.7;
	/** What the tendrils would grab right now (worked out each tick, for the red crosshair). */
	private net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity grabAim;

	@Override
	public void tick(Minecraft minecraft, LocalPlayer player) {
		swing.tick(minecraft, player);
		grabAim = com.jaymospowers.power.web.WebSymbiote.suitShown(player) ? grabTarget(player) : null;
		WebSymbioteClient.tick(minecraft, player, swing);
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
			// nothing in reach: no box at all
			color = 0;
			r = 7;
		} else {
			color = preview.assisted() ? RETICLE_ASSIST : RETICLE_LIVE;
			r = 6;
		}
		corner(graphics, cx - r, cy - r, 1, 1, color);
		corner(graphics, cx + r, cy - r, -1, 1, color);
		corner(graphics, cx - r, cy + r, 1, -1, color);
		corner(graphics, cx + r, cy + r, -1, -1, color);
		if (grabAim != null) {
			// something the tendrils can take: the crosshair goes red
			graphics.fill(cx - 5, cy, cx + 6, cy + 1, 0xFFE02020);
			graphics.fill(cx, cy - 5, cx + 1, cy + 6, 0xFFE02020);
			graphics.outline(cx - 3, cy - 3, 7, 7, 0xC0E02020);
		}

		// cooldown chips right of the hotbar (same corner and style as Wind Control's)
		WebCooldowns cooldowns = player.getAttached(WebPower.COOLDOWNS);
		if (cooldowns == null) {
			cooldowns = WebCooldowns.NONE;
		}
		long now = player.level().getGameTime();
		Font font = minecraft.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		WebSymbioteClient.renderHud(graphics, player);
		// the ascended ability (Web Strike) joins the end of the row once you have it
		boolean ascended = com.jaymospowers.core.Fate.isAscended(player);
		int chips = ascended ? 6 : 5;
		int total = CHIP * chips + CHIP_GAP * (chips - 1);
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
		// G: the off-hand web (no cooldown; lit while it's out, dimmed while the off hand is full)
		chip(graphics, font, x, y, PowerKey.ABILITY_2, Items.STRING, 0L, 1);
		if (swing.strands[WebNet.OFF].attached) {
			graphics.fill(x + 1, y + CHIP - 3, x + CHIP - 1, y + CHIP - 1, 0xFF000000 | CHIP_COLOR);
		} else if (!WebPower.offHandFree(player)) {
			graphics.fill(x + 1, y + 1, x + CHIP - 1, y + CHIP - 1, 0x90000000);
		}
		// Z: Web Zip, at the end of the row (then C, once ascended)
		zipChip(graphics, font, x + (CHIP + CHIP_GAP) * 3, y, cooldowns.zip() - now, com.jaymospowers.power.web.WebSymbiote.zipCooldown(player));
		if (ascended) {
			Long ready = player.getAttached(com.jaymospowers.power.web.WebAscension.STRIKE_READY);
			strikeChip(graphics, font, x + (CHIP + CHIP_GAP) * 4, y, ready == null ? 0L : ready - now);
		}
		chip(graphics, font, x + CHIP + CHIP_GAP, y, PowerKey.ABILITY_3, Items.COBWEB, cooldowns.shot() - now, (int)Math.max(1L, cooldowns.shot() - shotFiredAt));
		if (shotChargeStart >= 0 && now >= cooldowns.shot()) {
			// charging: a bar fills under the chip, flashing when full
			float charge = Math.min(1.0F, (now - shotChargeStart) / (float)WebPower.SHOT_CHARGE_TICKS);
			int bx = x + CHIP + CHIP_GAP;
			int barColor = charge >= 1.0F && (now / 3) % 2 == 0 ? 0xFFFFFFFF : 0xFF000000 | CHIP_COLOR;
			graphics.fill(bx + 1, y + CHIP - 3, bx + 1 + Math.round((CHIP - 2) * charge), y + CHIP - 1, barColor);
		}
		chip(graphics, font, x + (CHIP + CHIP_GAP) * 2, y, PowerKey.ABILITY_4, Items.LEAD, cooldowns.yank() - now, WebPower.YANK_COOLDOWN);
		if (player.getAttached(WebPower.TETHER) != null) {
			// holding something on the line
			int bx = x + (CHIP + CHIP_GAP) * 2;
			graphics.fill(bx + 1, y + CHIP - 3, bx + CHIP - 1, y + CHIP - 1, 0xFF000000 | CHIP_COLOR);
		}
	}

	/** Web Strike's chip (the ascended ability, on the ultimate key). */
	private static void strikeChip(GuiGraphicsExtractor graphics, Font font, int x, int y, long left) {
		String label = font.plainSubstrByWidth(JaymosPowersClient.ultimateKey().getTranslatedKeyMessage().getString(), CHIP - 2);
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFFFFD040 : 0xA0505050);
		graphics.item(new ItemStack(Items.LEATHER_BOOTS), x + 2, y + 2);
		if (!ready) {
			int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)com.jaymospowers.power.web.WebAscension.STRIKE_COOLDOWN));
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = String.format(Locale.ROOT, "%.1f", left / 20.0);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	/** Web Zip's chip: its own key, unlocked along with G. */
	private static void zipChip(GuiGraphicsExtractor graphics, Font font, int x, int y, long left, int totalTicks) {
		String label = font.plainSubstrByWidth(JaymosPowersClient.modeKey().getTranslatedKeyMessage().getString(), CHIP - 2);
		if (!JaymosPowersClient.isUnlocked(PowerKey.ABILITY_2)) {
			graphics.fill(x, y, x + CHIP, y + CHIP, 0xB0000000);
			graphics.outline(x, y, CHIP, CHIP, 0x60505050);
			graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, 0xFF505050);
			return;
		}
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | CHIP_COLOR : 0xA0505050);
		graphics.item(new ItemStack(Items.FISHING_ROD), x + 2, y + 2);
		if (!ready) {
			int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)Math.max(1, totalTicks)));
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = String.format(Locale.ROOT, "%.1f", left / 20.0);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	private void wingsChip(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, int x, int y) {
		boolean open = swing.wingsTicks > 0;
		boolean cooling = false;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, 0xFF000000 | CHIP_COLOR);
		graphics.item(new ItemStack(Items.PHANTOM_MEMBRANE), x + 2, y + 2);
		if (open) {
			int w = (int)Math.ceil((CHIP - 2) * swing.wingsTicks / (double)WebPower.WINGS_TICKS);
			graphics.fill(x + 1, y + CHIP - 3, x + 1 + w, y + CHIP - 1, 0xFF000000 | CHIP_COLOR);
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
