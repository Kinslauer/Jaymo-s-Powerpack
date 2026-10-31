package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoForms;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.Locale;
import java.util.Map;
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
 * Xenoform's HUD, right of the hotbar (the Wind chips' spot):
 * <ul>
 * <li>as a human: the dial chip (Z) with the dialled alien's icon in its colour, shaded through the band's recharge, with
 * the alien's name and how many the band holds above it; the scan chip (X) filling while a scan runs;</li>
 * <li>as an alien: four chips, R G V B, with the move's icon and cooldown; above them the form's timer, a bar in its
 * colour that goes red through the beeps, with the seconds left.</li>
 * </ul>
 * And a flash of the form's colour round the screen's edge when the shape changes.
 */
final class XenoformHud {
	private static final int CHIP = 20;
	private static final int CHIP_GAP = 2;
	private static final int BAR_WIDTH = 4 * CHIP + 3 * CHIP_GAP;
	private static final int WARN_COLOR = 0xFF4040;
	/** Each move's icon, per alien (R G V B). */
	private static final Map<String, Item[]> ICONS = Map.of(
		"bulwark", new Item[] {Items.IRON_INGOT, Items.HEAVY_CORE, Items.LEAD, Items.ANVIL},
		"pyre", new Item[] {Items.FIRE_CHARGE, Items.BLAZE_POWDER, Items.MAGMA_CREAM, Items.MAGMA_BLOCK},
		"blur", new Item[] {Items.SUGAR, Items.PISTON, Items.WIND_CHARGE, Items.ENDER_PEARL},
		"prism", new Item[] {Items.AMETHYST_SHARD, Items.AMETHYST_BLOCK, Items.SHIELD, Items.POINTED_DRIPSTONE},
		"wisp", new Item[] {Items.PHANTOM_MEMBRANE, Items.GLASS, Items.SCULK_SHRIEKER, Items.ELYTRA},
		"gremlin", new Item[] {Items.SLIME_BALL, Items.LEVER, Items.ROTTEN_FLESH, Items.VINE},
		"knell", new Item[] {Items.ECHO_SHARD, Items.SCULK_SENSOR, Items.SCULK_SHRIEKER, Items.DIRT},
		"wyrm", new Item[] {Items.DRAGON_BREATH, Items.ELYTRA, Items.WIND_CHARGE, Items.ENDER_EYE},
		"duplex", new Item[] {Items.COPPER_INGOT, Items.LIGHTNING_ROD.asList().get(0), Items.COPPER_BULB.asList().get(0), Items.HONEYCOMB},
		"nadir", new Item[] {Items.ENDER_EYE, Items.BARRIER, Items.BARRIER, Items.BARRIER});
	/** The slam's burst of light: where the band is on screen (shares of the width and height), how big it blooms (share of the height), its layers and row height. */
	private static final float BURST_X = 0.52F, BURST_Y = 0.74F, BURST_SIZE = 0.4F;
	private static final int BURST_LAYERS = 5, BURST_STEP = 3, BURST_ALPHA = 30;
	private static final Item[] NO_ICONS = {Items.STONE_BUTTON, Items.STONE_BUTTON, Items.STONE_BUTTON, Items.STONE_BUTTON};

	private XenoformHud() {
	}

	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta, LocalPlayer player) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		flash(graphics, width, height, delta);
		burst(graphics, width, height, delta);
		XenoformState.Hud hud = XenoformState.hud(player);
		XenoForm form = XenoformState.form(player);
		long now = player.level().getGameTime();
		if (form != null) {
			int[] at = chipsOrigin(width, height, 4);
			Item[] icons = ICONS.getOrDefault(form.id, NO_ICONS);
			for (int i = 0; i < 4; i++) {
				moveChip(graphics, font, hud, form, i, icons[i], at[0] + i * (CHIP + CHIP_GAP), at[1], now, time);
			}
			timer(graphics, font, hud, form, at[0], at[1] - 14, time);
			return;
		}
		int[] at = chipsOrigin(width, height, 2);
		dialChip(graphics, font, player, hud, at[0], at[1], time);
		scanChip(graphics, font, minecraft, hud, at[0] + CHIP + CHIP_GAP, at[1], time);
		charge(graphics, player, hud, at[0], at[1] - 15, time);
	}

	/** (0.18.88) As yourself: the band's charge over the chips, its colour while a slam is possible, grey below that, purple jammed. */
	private static void charge(GuiGraphicsExtractor graphics, LocalPlayer player, XenoformState.Hud hud, int x, int y, float time) {
		int width = chipsWidth(2);
		float share = Mth.clamp(hud.formTicks() / (float)Math.max(1, hud.formTotal()), 0.0F, 1.0F);
		boolean jammed = com.jaymospowers.power.xenoform.XenoJam.jammed(player);
		int color = jammed ? 0x6A2CA0 : hud.recharge() > 0 ? 0x707070 : XenoformPower.wild(player) ? pulse(0xC060FF, XenoformPower.COLOR, time * 0.2F) : XenoformPower.COLOR;
		graphics.fill(x, y, x + width, y + 2, 0x90000000);
		graphics.fill(x, y, x + Mth.ceil(width * share), y + 2, 0xFF000000 | color);
	}

	/** The flash: the form's colour round the edges, fading. */
	private static void flash(GuiGraphicsExtractor graphics, int width, int height, DeltaTracker delta) {
		if (XenoformPowerClient.flash <= 0) {
			return;
		}
		float left = (XenoformPowerClient.flash - delta.getGameTimeDeltaPartialTick(false)) / (float)XenoformPowerClient.FLASH_TICKS;
		int alpha = Mth.clamp((int)(left * 120.0F), 0, 255);
		int color = alpha << 24 | XenoformPowerClient.flashColor & 0xFFFFFF;
		int edge = Mth.ceil(Math.min(width, height) * 0.08F * left) + 2;
		graphics.fill(0, 0, width, edge, color);
		graphics.fill(0, height - edge, width, height, color);
		graphics.fill(0, edge, edge, height - edge, color);
		graphics.fill(width - edge, edge, width, height - edge, color);
	}

	/** The slam landing in first person: green light blooming out from the band and fading, as nested soft ellipses. */
	private static void burst(GuiGraphicsExtractor graphics, int width, int height, DeltaTracker delta) {
		if (XenoformPowerClient.burst <= 0) {
			return;
		}
		float left = (XenoformPowerClient.burst - delta.getGameTimeDeltaPartialTick(false)) / (float)XenoformPowerClient.BURST_TICKS;
		if (left <= 0.0F) {
			return;
		}
		float bloom = 0.25F + 0.75F * (1.0F - left);
		int cx = Math.round(width * BURST_X);
		int cy = Math.round(height * BURST_Y);
		for (int layer = BURST_LAYERS; layer >= 1; layer--) {
			float ry = height * BURST_SIZE * bloom * layer / BURST_LAYERS;
			float rx = ry * 1.5F;
			int color = Mth.clamp((int)(BURST_ALPHA * left * 1.3F), 0, 255) << 24 | (layer == 1 ? 0xD8FFE0 : XenoformPower.COLOR);
			for (float dy = -ry; dy < ry; dy += BURST_STEP) {
				int half = (int)(rx * Mth.sqrt(1.0F - dy * dy / (ry * ry)));
				graphics.fill(cx - half, cy + (int)dy, cx + half, cy + (int)dy + BURST_STEP, color);
			}
		}
	}

	/** Z: the dialled alien, and the band's state. */
	private static void dialChip(GuiGraphicsExtractor graphics, Font font, LocalPlayer player, XenoformState.Hud hud, int x, int y, float time) {
		XenoformState.Band band = XenoformState.band(player);
		XenoForm dialled = XenoForms.get(band.dialled());
		boolean charged = hud.recharge() <= 0;
		int color = dialled != null ? dialled.color : XenoformPower.COLOR;
		int border = charged ? pulse(color, 0xFFFFFF, time * 0.3F) : 0x505050;
		graphics.fill(x, y, x + CHIP, y + CHIP, charged ? 0xB0203020 : 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, (charged ? 0xFF000000 : 0xA0000000) | border);
		graphics.item(new ItemStack(dialled != null ? dialled.icon : Items.BARRIER), x + 2, y + 2);
		if (!charged) {
			float share = Mth.clamp(hud.recharge() / (float)Math.max(1, hud.rechargeTotal()), 0.0F, 1.0F);
			int shade = Mth.ceil(16 * share);
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = String.valueOf(Mth.ceil(hud.recharge() / 20.0F));
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		String key = font.plainSubstrByWidth(JaymosPowersClient.modeKey().getTranslatedKeyMessage().getString(), CHIP - 2);
		graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, charged ? 0xFFFFFFFF : 0xFF909090);
		// the name over it, and how many the band holds
		if (dialled != null) {
			String name = dialled.name().getString();
			String count = band.unlocked().size() + "/" + XenoForms.all().size();
			graphics.text(font, name, x, y - 11, 0xFF000000 | color);
			graphics.text(font, count, x + 2 * CHIP + CHIP_GAP - font.width(count), y - 11, 0xFFB0B0B0);
		}
	}

	/** X: the scan, filling green while it runs. */
	private static void scanChip(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, XenoformState.Hud hud, int x, int y, float time) {
		boolean scanning = hud.scan() > 0;
		XenoForm found = XenoForms.get(hud.scanForm());
		int color = found != null ? found.color : XenoformPower.COLOR;
		int border = scanning ? pulse(color, 0xFFFFFF, time * 0.8F) : 0x707070;
		graphics.fill(x, y, x + CHIP, y + CHIP, scanning ? 0xB0203020 : 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, 0xFF000000 | border);
		graphics.item(new ItemStack(Items.SPYGLASS), x + 2, y + 2);
		if (scanning) {
			float share = Mth.clamp(hud.scan() / (float)Math.max(1, hud.scanTotal()), 0.0F, 1.0F);
			int fill = Mth.ceil(16 * share);
			graphics.fill(x + 2, y + 2 + 16 - fill, x + 18, y + 18, 0x80000000 | color);
			if (found != null) {
				String name = found.name().getString();
				graphics.text(font, name, x + CHIP - font.width(name), y - 11 - 10, 0xFF000000 | color);
			}
		}
		String key = font.plainSubstrByWidth(XenoformPowerClient.skillKeyLabel(minecraft), CHIP - 2);
		graphics.text(font, key, x + CHIP - 1 - font.width(key), y + CHIP - 8, 0xFFFFFFFF);
	}

	/** One of the alien's moves: its icon, shaded through its cooldown, with its key. */
	private static void moveChip(GuiGraphicsExtractor graphics, Font font, XenoformState.Hud hud, XenoForm form, int slot, Item icon, int x, int y, long now, float time) {
		long left = hud.cooldownLeft(slot, now);
		boolean ready = left <= 0;
		int border = ready ? form.color : 0x505050;
		graphics.fill(x, y, x + CHIP, y + CHIP, 0x90000000);
		graphics.outline(x, y, CHIP, CHIP, (ready ? 0xFF000000 : 0xA0000000) | border);
		graphics.item(new ItemStack(icon), x + 2, y + 2);
		if (!ready) {
			float share = Mth.clamp(left / (float)Math.max(1, hud.length()[slot]), 0.0F, 1.0F);
			int shade = Mth.ceil(16 * share);
			graphics.fill(x + 2, y + 2 + 16 - shade, x + 18, y + 18, 0xB0000000);
			String seconds = left >= 200 ? String.valueOf(left / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0F);
			graphics.text(font, seconds, x + CHIP / 2 - font.width(seconds) / 2, y + 4, 0xFFFFFFFF);
		}
		PowerKey key = PowerKey.values()[slot];
		String label = font.plainSubstrByWidth(JaymosPowersClient.keyMapping(key).getTranslatedKeyMessage().getString(), CHIP - 2);
		graphics.text(font, label, x + CHIP - 1 - font.width(label), y + CHIP - 8, ready ? 0xFFFFFFFF : 0xFF909090);
	}

	/** The form's clock: a bar in its colour, red and pulsing through the beeps, and the seconds. */
	private static void timer(GuiGraphicsExtractor graphics, Font font, XenoformState.Hud hud, XenoForm form, int x, int y, float time) {
		float share = Mth.clamp(hud.formTicks() / (float)Math.max(1, hud.formTotal()), 0.0F, 1.0F);
		boolean warn = hud.formTicks() <= XenoformPower.BEEP_TICKS;
		int color = warn ? pulse(WARN_COLOR, 0xFFFFFF, time * 1.2F) : form.color;
		graphics.fill(x, y + 8, x + BAR_WIDTH, y + 11, 0x90000000);
		graphics.fill(x, y + 8, x + Mth.ceil(BAR_WIDTH * share), y + 11, 0xFF000000 | color);
		Component name = form == com.jaymospowers.power.xenoform.XenoForms.DUPLEX ? com.jaymospowers.power.xenoform.forms.Duplex.title(1 + doubles()) : form.name();
		graphics.text(font, name.getString(), x, y - 2, 0xFF000000 | form.color);
		String seconds = String.valueOf(Mth.ceil(hud.formTicks() / 20.0F));
		graphics.text(font, seconds, x + BAR_WIDTH - font.width(seconds), y - 2, warn ? 0xFF000000 | color : 0xFFFFFFFF);
	}

	/** How many of Duplex's doubles of yours this client can see. */
	private static int doubles() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return 0;
		}
		int count = 0;
		for (net.minecraft.world.entity.Entity e : minecraft.level.entitiesForRendering()) {
			if (e instanceof com.jaymospowers.power.xenoform.XenoDouble twin && twin.isAlive() && twin.isDoubleOf(minecraft.player)) {
				count++;
			}
		}
		return count;
	}

	private static int chipsWidth(int count) {
		return count * CHIP + (count - 1) * CHIP_GAP;
	}

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

	private static int pulse(int a, int b, float phase) {
		float t = 0.5F + 0.5F * Mth.sin(phase);
		int r = (int)Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int)Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int)Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}
}
