package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.PowerClient;
import com.jaymospowers.client.PowerClients;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.jaymospowers.power.zfighter.ZFighterState;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Z Fighter's client half, right of the hotbar in Wind's chip style: the ki bar (gold while Super, with the combo
 * count over it), then R G V B, Z (Flight) once grown into, and C (Super) once ascended. Over the crosshair: the Ki
 * Wave gathering.
 */
public final class ZFighterPowerClient implements PowerClient {
	public static final ZFighterPowerClient INSTANCE = new ZFighterPowerClient();

	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int CHIP_COLOR = ZFighterPower.COLOR;
	private static final int SUPER_COLOR = ZFighterPower.SUPER_COLOR;
	private static final int WAVE_COLOR = 0x8FD3FF;
	/** Ticks a Ki Wave takes to gather in full (mirrors the server's). */
	private static final int WAVE_FULL = 60;

	private ZFighterPowerClient() {
	}

	public static void init() {
		PowerClients.register(ZFighterPower.ID, INSTANCE);
		ZFighterInputClient.init();
	}

	@Override
	public void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		ZFighterState state = ZFighterPower.state(player);
		wave(graphics, font, state, now, time);
		chips(graphics, font, player, state, now, time);
	}

	private static void wave(GuiGraphicsExtractor graphics, Font font, ZFighterState state, long now, float time) {
		if (state.waveFrom() < 0) {
			return;
		}
		float fill = Mth.clamp((now - state.waveFrom()) / (float)WAVE_FULL, 0.0F, 1.0F);
		int color = state.has(ZFighterState.SUPER) ? SUPER_COLOR : WAVE_COLOR;
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		int width = 52;
		int x0 = cx - width / 2;
		int y = cy - 28;
		graphics.fill(x0 - 1, y - 1, x0 + width + 1, y + 4, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(width * fill), y + 3, 0xFF000000 | (fill >= 1.0F ? pulse(0xFFFFFF, color, time * 0.6F) : color));
		graphics.centeredText(font, Component.translatable("hud.jaymospowers.zfighter.wave").copy().withColor(color), cx, y - 11, 0xFFFFFFFF);
	}

	private static void chips(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, ZFighterState state, long now, float time) {
		boolean flight = ZFighterPower.INSTANCE.slotUnlocked(player, "mode");
		boolean ultimate = Fate.isAscended(player);
		boolean super_ = state.has(ZFighterState.SUPER);
		int count = 5 + (flight ? 1 : 0) + (ultimate ? 1 : 0);
		int total = CHIP * count + CHIP_GAP * (count - 1);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int x = width / 2 + 91 + 30;
		int y = height - CHIP - 1;
		if (x + total > width - 2) {
			x = width / 2 + 91 - total;
			y = height - 22 - 30 - CHIP;
		}
		kiBar(graphics, font, state, x, y - 6, total, super_, time);
		int step = CHIP + CHIP_GAP;
		float charge = state.has(ZFighterState.CHARGING) ? 0.5F + 0.5F * Mth.sin(time * 0.5F) : -1.0F;
		chip(graphics, font, x, y, PowerKey.ABILITY_1, Items.GLOWSTONE_DUST, state, -1, now, charge);
		chip(graphics, font, x + step, y, PowerKey.ABILITY_2, Items.FIRE_CHARGE, state, ZFighterState.BLAST, now, -1.0F);
		float wave = state.waveFrom() >= 0 ? Mth.clamp((now - state.waveFrom()) / (float)WAVE_FULL, 0.0F, 1.0F) : -1.0F;
		chip(graphics, font, x + step * 2, y, PowerKey.ABILITY_3, Items.HEART_OF_THE_SEA, state, ZFighterState.WAVE, now, wave);
		chip(graphics, font, x + step * 3, y, PowerKey.ABILITY_4, Items.FEATHER, state, ZFighterState.STEP, now, -1.0F);
		int next = 4;
		if (flight) {
			boolean flying = state.has(ZFighterState.FLYING);
			special(graphics, font, x + step * next++, y, new ItemStack(Items.PHANTOM_MEMBRANE), 0, 1,
				JaymosPowersClient.modeKey().getTranslatedKeyMessage().getString(), flying ? pulse(0xFFFFFF, CHIP_COLOR, time * 0.4F) : CHIP_COLOR);
		}
		boolean limit = state.limit() > 0;
		special(graphics, font, x + step * next++, y, new ItemStack(limit ? Items.BLAZE_POWDER : Items.WRITABLE_BOOK), 0, 1,
			ZFighterInputClient.keyLabel(Minecraft.getInstance()), limit ? pulse(0xFF3A2A, 0xFFB0A0, time * 0.6F) : CHIP_COLOR);
		if (ultimate) {
			special(graphics, font, x + step * next, y, new ItemStack(Items.NETHER_STAR), state.left(ZFighterState.SUPER_SLOT, now),
				state.length()[ZFighterState.SUPER_SLOT], JaymosPowersClient.ultimateKey().getTranslatedKeyMessage().getString(),
				super_ ? pulse(0xFFFFFF, SUPER_COLOR, time * 0.5F) : SUPER_COLOR);
		}
	}

	/** The ki meter over the chips, with the number and, mid-combo, the hit count. */
	private static void kiBar(GuiGraphicsExtractor graphics, Font font, ZFighterState state, int x, int y, int width, boolean super_, float time) {
		float fill = Mth.clamp(state.ki() / (float)Math.max(1, state.maxKi()), 0.0F, 1.0F);
		int color = super_ ? pulse(SUPER_COLOR, 0xFFF4C0, time * 0.3F) : CHIP_COLOR;
		graphics.fill(x, y, x + width, y + 4, 0x90000000);
		graphics.fill(x + 1, y + 1, x + 1 + Math.round((width - 2) * fill), y + 3, 0xFF000000 | color);
		String ki = state.ki() + "/" + state.maxKi();
		graphics.text(font, ki, x + width - font.width(ki), y - 9, 0xFF000000 | color);
		Component label = Component.translatable("hud.jaymospowers.zfighter.ki");
		if (state.form() > 0) {
			label = Component.translatable("form.jaymospowers.zfighter." + state.form()).copy().withColor(color);
		}
		if (state.limit() > 0) {
			label = label.copy().append(Component.translatable("hud.jaymospowers.zfighter.limit", state.limit()).copy().withColor(0xFF5A40));
		}
		graphics.text(font, label, x, y - 9, 0xFFFFFFFF);
		if (state.combo() >= 2 && state.form() == 0 && state.limit() == 0) {
			Component combo = Component.translatable("hud.jaymospowers.zfighter.combo", state.combo());
			graphics.text(font, combo, x + width / 2 - font.width(combo) / 2, y - 9, 0xFFFFE08A);
		}
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, PowerKey key, Item icon, ZFighterState state, int slot,
			long now, float bar) {
		String label = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString(), CHIP - 2);
		if (!JaymosPowersClient.isUnlocked(key)) {
			graphics.fill(x, y, x + CHIP, y + CHIP, 0xB0000000);
			graphics.outline(x, y, CHIP, CHIP, 0x60505050);
			graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, 0xFF505050);
			return;
		}
		long left = slot < 0 ? 0 : state.left(slot, now);
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | CHIP_COLOR : 0xA0505050);
		graphics.item(new ItemStack(icon), x + 2, y + 2);
		if (slot >= 0) {
			shade(graphics, font, x, y, left, state.length()[slot]);
		}
		if (bar >= 0.0F) {
			graphics.fill(x + 1, y + CHIP - 3, x + 1 + Math.round((CHIP - 2) * bar), y + CHIP - 1, 0xFF000000 | CHIP_COLOR);
		}
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	/** Z or C: their own key. */
	private static void special(GuiGraphicsExtractor graphics, Font font, int x, int y, ItemStack icon, long left, int total, String key, int color) {
		boolean ready = left <= 0;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, ready ? 0xFF000000 | color : 0xA0505050);
		graphics.item(icon, x + 2, y + 2);
		shade(graphics, font, x, y, left, total);
		key = font.plainSubstrByWidth(key, CHIP - 2);
		graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	private static void shade(GuiGraphicsExtractor graphics, Font font, int x, int y, long left, int total) {
		if (left <= 0) {
			return;
		}
		int shade = (int)Math.ceil(16 * Math.min(1.0, left / (double)Math.max(1, total)));
		graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
		String seconds = left >= 200 ? Long.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
		graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
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
