package com.jaymospowers.client.power.darktide;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.power.darktide.DarktideFlow;
import com.jaymospowers.power.darktide.DarktideMove;
import com.jaymospowers.power.darktide.DarktidePower;
import com.jaymospowers.power.darktide.DarktideState;
import com.jaymospowers.power.darktide.DarktideWeapons;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
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
 * Darktide's HUD in Wind's chip style, right of the hotbar: the Flow chip (water rising under a line of ink, the number
 * on it), then one chip per key (R G V B, then X Gedan, Z Veil and C once they answer) with its bound key and cooldown.
 * Above the row: "empowered" after a parry, "guard broken" while the guard can't come up. Over the crosshair while
 * holding Gedan: its name and the field spreading.
 */
final class DarktideHud {
	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int FLOW_WIDTH = 44;
	static final int WATER = 0x2F6FB5;
	static final int WATER_LIGHT = 0x7FC4FF;
	static final int INK = 0x0B0F1E;
	static final int DROWNED = 0x3A2A6A;
	static final int EMPOWERED = 0xB8A0FF;

	private record Chip(Item icon, int slot, String key, boolean unlocked, boolean lit) {
	}

	private DarktideHud() {
	}

	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		DarktideState.Hud hud = DarktideState.hud(player);
		boolean drowned = DarktidePower.drowned(player);
		List<Chip> chips = chips(player, hud, drowned);
		int width = FLOW_WIDTH + CHIP_GAP + chips.size() * (CHIP + CHIP_GAP) - CHIP_GAP;
		int cx = graphics.guiWidth() / 2;
		int x = cx + 91 + 30;
		int y = graphics.guiHeight() - CHIP - 1;
		if (x + width > graphics.guiWidth() - 2) {
			x = cx + 91 - width;
			y = graphics.guiHeight() - 22 - 30 - CHIP;
		}
		flowChip(graphics, font, hud, x, y, time, drowned);
		int cx2 = x + FLOW_WIDTH + CHIP_GAP;
		for (Chip chip : chips) {
			chip(graphics, font, chip, hud, now, cx2, y, time, drowned);
			cx2 += CHIP + CHIP_GAP;
		}
		// the state line above the row
		List<Component> lines = new ArrayList<>();
		if (now < hud.empoweredUntil()) {
			lines.add(Component.translatable("hud.jaymospowers.darktide.empowered").withColor(pulse(EMPOWERED, 0xFFFFFF, time * 0.5F)));
		}
		if (now < hud.guardLockedUntil()) {
			lines.add(Component.translatable("hud.jaymospowers.darktide.guard_broken").withColor(0xE06060));
		}
		if (hud.shadows() > 0 && Fate.isAscended(player)) {
			lines.add(Component.translatable("hud.jaymospowers.darktide.shadows", hud.shadows(), hud.risen()).withColor(0x9AA6C8));
		}
		int ly = y - 11;
		for (Component line : lines) {
			graphics.text(font, line, x + width - font.width(line), ly, 0xFFFFFFFF);
			ly -= 10;
		}
		gedanLabel(graphics, font, player, now, time);
	}

	private static List<Chip> chips(LocalPlayer player, DarktideState.Hud hud, boolean drowned) {
		DarktideWeapons.Kit kit = DarktideWeapons.kit(player);
		boolean ascended = Fate.isAscended(player);
		boolean knives = kit == DarktideWeapons.Kit.KNIFE && ascended;
		List<Chip> chips = new ArrayList<>();
		chips.add(new Chip(knives ? Items.PRISMARINE_SHARD : Items.INK_SAC, knives ? DarktideState.POUNCE : DarktideState.STRIKE,
			keyName(PowerKey.ABILITY_1), JaymosPowersClient.isUnlocked(PowerKey.ABILITY_1), false));
		Item g;
		int gSlot;
		boolean gUsable = true;
		if (kit == DarktideWeapons.Kit.KATANA) {
			g = Items.NAUTILUS_SHELL;
			gSlot = DarktideState.CRESCENT;
		} else if (knives) {
			g = Items.PRISMARINE_CRYSTALS;
			gSlot = DarktideState.BARRAGE;
		} else if (kit == DarktideWeapons.Kit.KNIFE) {
			g = Items.FLINT;
			gSlot = DarktideState.INK_CROSS;
		} else {
			g = Items.NAUTILUS_SHELL;
			gSlot = DarktideState.CRESCENT;
			gUsable = false;
		}
		chips.add(new Chip(g, gSlot, keyName(PowerKey.ABILITY_2), JaymosPowersClient.isUnlocked(PowerKey.ABILITY_2) && gUsable, false));
		chips.add(new Chip(Items.HEART_OF_THE_SEA, DarktideState.UNDERTOW, keyName(PowerKey.ABILITY_3), JaymosPowersClient.isUnlocked(PowerKey.ABILITY_3), false));
		chips.add(new Chip(knives ? Items.TRIDENT : Items.WATER_BUCKET, knives ? DarktideState.DASH : DarktideState.BLACK_TIDE, keyName(PowerKey.ABILITY_4),
			JaymosPowersClient.isUnlocked(PowerKey.ABILITY_4), false));
		if (Mastery.unlocked(player) >= Mastery.ALL) {
			KeyMapping skill = DarktidePowerClient.skillKey(Minecraft.getInstance());
			chips.add(new Chip(Items.CONDUIT, DarktideState.GEDAN, skill == null ? "X" : skill.getTranslatedKeyMessage().getString(),
				kit == DarktideWeapons.Kit.KATANA, DarktideMove.current(player) != null && DarktideMove.current(player).kind() == DarktideMove.STANCE));
			chips.add(new Chip(Items.ECHO_SHARD, -1, JaymosPowersClient.modeKey().getTranslatedKeyMessage().getString(), true, hud.veil()));
		}
		String c = JaymosPowersClient.ultimateKey().getTranslatedKeyMessage().getString();
		if (ascended) {
			boolean necro = DarktideFlow.night(player.level()) && hud.shadows() > 0;
			chips.add(new Chip(necro ? Items.SOUL_LANTERN : Items.PRISMARINE_SHARD, necro ? DarktideState.RAISE : DarktideState.ARMS, c,
				DarktideWeapons.loaded() || necro, hud.arms() != DarktideState.ARMS_BARE));
		} else if (drowned) {
			chips.add(new Chip(Items.PUFFERFISH, DarktideState.DROWN, c, true, false));
		}
		return chips;
	}

	private static String keyName(PowerKey key) {
		return JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString();
	}

	/** Flow: water rising in the chip, a line of ink riding on top, the number over it. */
	private static void flowChip(GuiGraphicsExtractor graphics, Font font, DarktideState.Hud hud, int x, int y, float time, boolean drowned) {
		float share = Mth.clamp(hud.flow() / (float) DarktideFlow.MAX, 0.0F, 1.0F);
		int water = drowned ? DROWNED : WATER;
		graphics.fill(x, y, x + FLOW_WIDTH, y + CHIP, 0x90000000);
		graphics.outline(x, y, FLOW_WIDTH, CHIP, 0xFF000000 | (drowned ? 0x6A4AB0 : WATER_LIGHT));
		int inner = CHIP - 2;
		int level = Mth.ceil(inner * share);
		int top = y + 1 + inner - level;
		graphics.fill(x + 1, top, x + FLOW_WIDTH - 1, y + CHIP - 1, 0xC0000000 | water);
		if (level > 0) {
			// the ink: a wavering line along the water's surface
			for (int i = 1; i < FLOW_WIDTH - 1; i++) {
				int wave = Math.round(Mth.sin(time * 0.25F + i * 0.45F));
				int wy = Mth.clamp(top + wave, y + 1, y + CHIP - 2);
				graphics.fill(x + i, wy, x + i + 1, wy + 1, 0xFF000000 | INK);
			}
		}
		String number = Integer.toString(hud.flow());
		graphics.text(font, number, x + FLOW_WIDTH / 2 - font.width(number) / 2, y + 6, 0xFFFFFFFF);
	}

	private static void chip(GuiGraphicsExtractor graphics, Font font, Chip chip, DarktideState.Hud hud, long now, int x, int y, float time,
			boolean drowned) {
		String key = font.plainSubstrByWidth(chip.key, CHIP - 2);
		if (!chip.unlocked) {
			graphics.fill(x, y, x + CHIP, y + CHIP, 0xB0000000);
			graphics.outline(x, y, CHIP, CHIP, 0x60505050);
			graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, 0xFF505050);
			return;
		}
		long left = chip.slot < 0 ? 0L : hud.cooldownLeft(chip.slot, now);
		boolean ready = left <= 0;
		int color = drowned ? 0x8A6AD0 : WATER_LIGHT;
		int border = chip.lit ? pulse(0xFFFFFF, color, time * 0.5F) : ready ? color : 0x505050;
		graphics.fill(x, y, x + CHIP, y + CHIP, chip.lit ? 0xB0102040 : 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, (ready ? 0xFF000000 : 0xA0000000) | border);
		graphics.item(new ItemStack(chip.icon), x + 2, y + 2);
		if (!ready) {
			float share = Mth.clamp(left / (float) Math.max(1, hud.length()[chip.slot]), 0.0F, 1.0F);
			int shade = Mth.ceil(16 * share);
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = left >= 200 ? Integer.toString(Mth.ceil(left / 20.0F)) : String.format(Locale.ROOT, "%.1f", left / 20.0F);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	/** Holding Gedan: its name over the crosshair, and a bar for the field spreading out to its full reach. */
	private static void gedanLabel(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, long now, float time) {
		DarktideState.Move move = DarktideMove.current(player);
		if (move == null || move.kind() != DarktideMove.STANCE) {
			return;
		}
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		int barWidth = 52;
		int x0 = cx - barWidth / 2;
		int y = cy - 20;
		float grow = Mth.clamp((now - move.start()) / 100.0F, 0.0F, 1.0F);
		graphics.fill(x0 - 1, y - 1, x0 + barWidth + 1, y + 4, 0x90000000);
		int color = grow >= 1.0F ? pulse(WATER_LIGHT, 0xFFFFFF, time * 0.4F) : WATER_LIGHT;
		graphics.fill(x0, y, x0 + Mth.ceil(barWidth * grow), y + 3, 0xFF000000 | color);
		int extra = move.variant() >> 2;
		Component label = Component.translatable("hud.jaymospowers.darktide.stance." + (move.variant() & 3)).withColor(WATER_LIGHT);
		if (extra > 0) {
			label = label.copy().append(Component.literal("  +" + extra).withColor(0xFFFFFF));
		}
		graphics.centeredText(font, label, cx, y - 11, 0xFFFFFFFF);
	}

	private static int pulse(int a, int b, float phase) {
		float t = 0.5F + 0.5F * Mth.sin(phase);
		int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}
}
