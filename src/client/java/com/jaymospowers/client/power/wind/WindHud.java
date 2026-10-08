package com.jaymospowers.client.power.wind;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.wind.WindAbility;
import com.jaymospowers.power.wind.WindFlight;
import com.jaymospowers.power.wind.WindTempest;
import com.jaymospowers.power.wind.WindWeather;
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
import net.minecraft.world.item.Items;

/**
 * Wind Control's HUD, kept clear of the core's top-left badge:
 * <ul>
 * <li>above the G / V / B chips while flying: the flight state (+ LOCKED), speed, and a speed bar with the sonic mark;</li>
 * <li>over the crosshair while channelling: the ability name and a charge meter;</li>
 * <li>right of the hotbar: G / V / B chips with their bound keys and cooldowns, plus the Cyclone Lance chip (C) once
 * ascended;</li>
 * <li>at the right end of the flight readout's row: a tiny weather glyph (sun, moon, roof, rain, storm...) with a thin
 * line under it showing how strong the wind is right now.</li>
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
	/** The weather glyph: 7Ã—7 pixels, and the room it takes at the end of the readout row. */
	private static final int GLYPH = 7;
	private static final int GLYPH_SPACE = GLYPH + 3;
	private static final int LANCE_COLOR = 0xE6F7FF;
	private static final int STORMBOUND_COLOR = 0xB070FF;
	private static final String[][] GLYPHS = {
		// CLEAR_DAY: sun
		{"...#...", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "...#..."},
		// CLEAR_NIGHT: moon
		{"..###..", ".##....", "##.....", "##.....", "##.....", ".##....", "..###.."},
		// SHELTERED: a roof
		{"...#...", "..#.#..", ".#...#.", "#.....#", ".#...#.", ".#...#.", ".#####."},
		// OTHERWORLD: a hollow diamond
		{"...#...", "..#.#..", ".#...#.", "#..#..#", ".#...#.", "..#.#..", "...#..."},
		// RAIN: cloud and drops
		{".####..", "######.", "######.", ".......", "#.#.#..", ".#.#.#.", "#.#.#.."},
		// STORM: cloud and a bolt
		{".####..", "######.", "######.", "..##...", ".##....", "..##...", ".#....."},
		// STORMBOUND: a bolt alone
		{"...##..", "..##...", ".##....", "#####..", "..##...", ".##....", "##....."},
	};
	private static final int[] GLYPH_COLORS = {0xFFD45A, 0xC8D0FF, 0x9A9A9A, 0xB088FF, 0x7FB8FF, 0xFFF27A, STORMBOUND_COLOR};

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
		boolean lance = Fate.isAscended(player);
		int count = WindAbility.values().length + (lance ? 1 : 0);
		int[] chipsAt = chipsOrigin(width, height, count);
		int right = chipsAt[0] + chipsWidth(count);
		weather(graphics, player, right, chipsAt[1] - 15, time);
		flight(graphics, font, client.flight(), right - GLYPH_SPACE, chipsAt[1], time);
		channel(graphics, font, client, cx, cy, time);
		chips(graphics, font, client, chipsAt[0], chipsAt[1], time);
		if (lance) {
			lanceChip(graphics, font, client, chipsAt[0] + WindAbility.values().length * (CHIP + CHIP_GAP), chipsAt[1], time);
		}
	}

	private static int chipsWidth(int count) {
		return count * CHIP + (count - 1) * CHIP_GAP;
	}

	/** The weather glyph, its colour, and a line under it as long as the wind is strong (eased, so it slides). */
	private static void weather(GuiGraphicsExtractor graphics, LocalPlayer player, int right, int y, float time) {
		WindWeather.Kind kind = WindWeather.kind(player);
		int color = GLYPH_COLORS[kind.ordinal()];
		if (kind == WindWeather.Kind.STORMBOUND || kind == WindWeather.Kind.STORM) {
			// a storm flickers
			color = pulse(color, 0xFFFFFF, time * 0.9F);
		}
		int x0 = right - GLYPH;
		String[] rows = GLYPHS[kind.ordinal()];
		for (int row = 0; row < GLYPH; row++) {
			for (int col = 0; col < GLYPH; col++) {
				if (rows[row].charAt(col) == '#') {
					graphics.fill(x0 + col, y + row, x0 + col + 1, y + row + 1, 0xE0000000 | color);
				}
			}
		}
		float strength = Mth.clamp(WindWeather.factor(player) / WindWeather.STORM, 0.0F, 1.0F);
		graphics.fill(x0, y + GLYPH + 1, x0 + Mth.ceil(GLYPH * strength), y + GLYPH + 2, 0xB0000000 | color);
	}

	/** Cyclone Lance (ascended): its chip, with the ultimate key and the cooldown. */
	private static void lanceChip(GuiGraphicsExtractor graphics, Font font, WindPowerClient client, int x, int y, float time) {
		int cooldown = client.lanceCooldown();
		boolean ready = cooldown <= 0;
		int border = ready ? pulse(LANCE_COLOR, 0xFFD45A, time * 0.2F) : 0x505050;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, (ready ? 0xFF000000 : 0xA0000000) | border);
		graphics.item(new ItemStack(Items.BREEZE_ROD), x + 2, y + 2);
		if (!ready) {
			float share = Mth.clamp(cooldown / (float)WindTempest.LANCE_COOLDOWN, 0.0F, 1.0F);
			int shade = Mth.ceil(16 * share);
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = String.format(Locale.ROOT, "%.1f", cooldown / 20.0F);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		String key = font.plainSubstrByWidth(JaymosPowersClient.ultimateKey().getTranslatedKeyMessage().getString(), CHIP - 2);
		graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	/** Top-left of the chip row: right of the hotbar, past the off-hand slot / attack indicator; above the hunger bar on narrow screens. */
	private static int[] chipsOrigin(int width, int height, int count) {
		int cx = width / 2;
		int x = cx + 91 + 30;
		int y = height - CHIP - 1;
		if (x + chipsWidth(count) > width - 2) {
			x = cx + 91 - chipsWidth(count);
			y = height - 22 - 30 - CHIP;
		}
		return new int[] {x, y};
	}

	/** The flight readout, stacked just above the chip row, right-aligned to {@code readoutRight} (left of the weather glyph). */
	private static void flight(GuiGraphicsExtractor graphics, Font font, WindFlightController flight, int readoutRight, int chipsY, float time) {
		WindFlight state = flight.state();
		if (state == WindFlight.TUMBLE) {
			// out of control: a warning and how long until you can catch yourself
			int right = readoutRight;
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
		int right = readoutRight;
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
