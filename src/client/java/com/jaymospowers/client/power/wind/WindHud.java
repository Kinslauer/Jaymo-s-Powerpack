package com.jaymospowers.client.power.wind;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.power.wind.WindAbility;
import com.jaymospowers.power.wind.WindFlight;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Wind Control's HUD, kept clear of the core's top-left badge:
 * <ul>
 * <li>above the G / V / B chips while flying: the flight state (+ LOCKED), speed, and a speed bar with the sonic mark;</li>
 * <li>over the crosshair while channelling: the ability name and a charge meter;</li>
 * <li>right of the hotbar: G / V / B chips with their bound keys and cooldowns.</li>
 * </ul>
 */
final class WindHud {
	private static final int BAR_WIDTH = 64;
	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int HOVER_COLOR = 0xBFF4EA;
	private static final int CRUISE_COLOR = 0x7FD4FF;
	private static final int SONIC_COLOR = 0xFFFFFF;
	private static final int LOCK_COLOR = 0xFFD45A;

	private WindHud() {
	}

	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player, WindPowerClient client) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int cx = width / 2;
		int cy = height / 2;
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		int[] chipsAt = chipsOrigin(width, height);
		flight(graphics, font, client.flight(), chipsAt[0], chipsAt[1], time);
		channel(graphics, font, client, cx, cy, time);
		chips(graphics, font, client, chipsAt[0], chipsAt[1], time);
	}

	private static int chipsWidth() {
		int count = WindAbility.values().length;
		return count * CHIP + (count - 1) * CHIP_GAP;
	}

	/** Top-left of the chip row: right of the hotbar, past the off-hand slot / attack indicator; above the hunger bar on narrow screens. */
	private static int[] chipsOrigin(int width, int height) {
		int cx = width / 2;
		int x = cx + 91 + 30;
		int y = height - CHIP - 1;
		if (x + chipsWidth() > width - 2) {
			x = cx + 91 - chipsWidth();
			y = height - 22 - 30 - CHIP;
		}
		return new int[] {x, y};
	}

	/** The flight readout, stacked just above the chip row and right-aligned with it. */
	private static void flight(GuiGraphicsExtractor graphics, Font font, WindFlightController flight, int chipsX, int chipsY, float time) {
		WindFlight state = flight.state();
		if (state == WindFlight.TUMBLE) {
			// out of control: a warning and how long until you can catch yourself
			int right = chipsX + chipsWidth();
			int y = chipsY - 4;
			float left = flight.tumbleTicks() / (float)WindFlightController.TUMBLE_TICKS;
			graphics.fill(right - BAR_WIDTH - 1, y - 1, right + 1, y + 3, 0x90000000);
			graphics.fill(right - BAR_WIDTH, y, right - BAR_WIDTH + Mth.ceil(BAR_WIDTH * left), y + 2, 0xFFE04040);
			Component warning = Component.translatable("hud.jaymospowers.wind.tumble").withColor((int)time % 6 < 3 ? 0xFF6060 : 0xFFFFFF);
			graphics.text(font, warning, right - font.width(warning), y - 11, 0xE0FFFFFF);
			return;
		}
		if (!state.flying()) {
			return;
		}
		int color = switch (state) {
			case CRUISE -> CRUISE_COLOR;
			case SONIC -> SONIC_COLOR;
			default -> HOVER_COLOR;
		};
		int right = chipsX + chipsWidth();
		int y = chipsY - 4;
		if (state.fast()) {
			// speed bar with a tick at the sonic threshold, as wide as the chip row
			int x0 = right - BAR_WIDTH;
			graphics.fill(x0 - 1, y - 1, x0 + BAR_WIDTH + 1, y + 3, 0x90000000);
			int filled = Mth.ceil(BAR_WIDTH * flight.speedFraction());
			int barColor = state == WindFlight.SONIC ? pulse(0xFFFFFF, 0xBFF4EA, time * 0.5F) : CRUISE_COLOR;
			graphics.fill(x0, y, x0 + filled, y + 2, 0xFF000000 | barColor);
			int mark = x0 + (int)Math.round(BAR_WIDTH * WindFlightController.SONIC_ENTER);
			graphics.fill(mark, y - 1, mark + 1, y + 3, 0xFFFFD45A);
		}
		y -= 11;
		MutableComponent label = Component.translatable("hud.jaymospowers.wind." + state.name().toLowerCase()).withColor(color);
		if (state.fast()) {
			int metresPerSecond = (int)Math.round(flight.currentSpeed() * 20.0);
			label.append(Component.literal("  " + metresPerSecond + " m/s").withColor(0xB0B0B0));
		}
		if (flight.locked()) {
			int lockColor = flight.lockFlash() > 0 && (flight.lockFlash() / 3) % 2 == 0 ? 0xFFFFFF : LOCK_COLOR;
			label.append(Component.literal("  ")).append(Component.translatable("hud.jaymospowers.wind.locked").withColor(lockColor));
		} else if (flight.lockFlash() > 0 && state.fast()) {
			label.append(Component.literal("  ")).append(Component.translatable("hud.jaymospowers.wind.unlocked").withColor(0x909090));
		}
		graphics.text(font, label, right - font.width(label), y, 0xE0FFFFFF);
	}

	private static void channel(GuiGraphicsExtractor graphics, Font font, WindPowerClient client, int cx, int cy, float time) {
		WindAbility ability = client.channel();
		if (ability == null) {
			return;
		}
		float charge = Mth.clamp(client.channelTicks() / (float)ability.maxChannel, 0.0F, 1.0F);
		int barWidth = 52;
		int x0 = cx - barWidth / 2;
		int y = cy - 20;
		graphics.fill(x0 - 1, y - 1, x0 + barWidth + 1, y + 4, 0x90000000);
		int color = charge >= 1.0F ? pulse(0xFFFFFF, ability.color, time * 0.6F) : ability.color;
		graphics.fill(x0, y, x0 + Mth.ceil(barWidth * charge), y + 3, 0xFF000000 | color);
		graphics.centeredText(font, Component.translatable(ability.langKey()).withColor(ability.color), cx, y - 11, 0xFFFFFFFF);
	}

	private static void chips(GuiGraphicsExtractor graphics, Font font, WindPowerClient client, int x, int y, float time) {
		for (WindAbility ability : WindAbility.values()) {
			chip(graphics, font, client, ability, x, y, time);
			x += CHIP + CHIP_GAP;
		}
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, WindPowerClient client, WindAbility ability, int x, int y, float time) {
		if (!JaymosPowersClient.isUnlocked(ability.key)) {
			// not mastered yet: an empty dark slot with just the key
			graphics.fill(x, y, x + CHIP, y + CHIP, 0xB0000000);
			graphics.outline(x, y, CHIP, CHIP, 0x60505050);
			String key = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(ability.key).getTranslatedKeyMessage().getString(), CHIP - 2);
			graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, 0xFF505050);
			return;
		}
		int cooldown = client.cooldown(ability);
		boolean channelling = client.channel() == ability;
		boolean ready = cooldown <= 0;
		int border = channelling ? pulse(0xFFFFFF, ability.color, time * 0.5F) : ready ? ability.color : 0x505050;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, (ready ? 0xFF000000 : 0xA0000000) | border);
		graphics.item(new ItemStack(ability.icon), x + 2, y + 2);
		if (!ready) {
			float share = Mth.clamp(cooldown / (float)client.cooldownTotal(ability), 0.0F, 1.0F);
			int shade = Mth.ceil(16 * share);
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = cooldown >= 200 ? Integer.toString(Mth.ceil(cooldown / 20.0F)) : String.format(Locale.ROOT, "%.1f", cooldown / 20.0F);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		} else if (channelling) {
			float charge = Mth.clamp(client.channelTicks() / (float)ability.maxChannel, 0.0F, 1.0F);
			graphics.fill(x + 1, y + CHIP - 3, x + 1 + Mth.ceil((CHIP - 2) * charge), y + CHIP - 1, 0xFF000000 | ability.color);
		}
		// the bound key, bottom-right like an item count
		String key = JaymosPowersClient.keyMapping(ability.key).getTranslatedKeyMessage().getString();
		key = font.plainSubstrByWidth(key, CHIP - 2);
		graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	/** Blend between two 0xRRGGBB colours on a sine wave. */
	private static int pulse(int a, int b, float phase) {
		float t = 0.5F + 0.5F * Mth.sin(phase);
		int r = (int)Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int)Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int)Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}
}
