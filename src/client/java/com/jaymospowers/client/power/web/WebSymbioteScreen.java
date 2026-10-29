package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteCollect;
import com.jaymospowers.power.web.WebSymbioteState;
import com.jaymospowers.client.UiStyle;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The symbiote's radial menu: hold X, point the mouse toward an ability, let go to use it (or click). Doesn't pause
 * the game. (0.11.0) Styled like the H screen: its dark panel colours for the disc and tiles, its bar, and a small
 * panel under the ring (accent stripe, the pick's name in the accent, what's stopping it in dim) where a tooltip
 * would be. (0.17.165) Reworked, Andy: "It's a bit cluttered, and the living armour can be condensed to a suit button
 * that activates your large suit once you hit max bond." The ring holds six tiles again, 60 degrees apart: the
 * <b>Suit</b> at the top (one button for every form: a pick climbs a form, a sneak-pick comes down one; the little
 * mask that used to sit in the middle and the Living Armor tile are both folded into it), then Tendril Grab, Wall
 * Crawl, Consume, Spike Burst and Shroud clockwise. The three switches that were ring tiles (Mask, Tendrils, Collect)
 * are a row of small chips inside the ring under the bond line, with the Mind chip under them.
 */
final class WebSymbioteScreen extends Screen {
	/** Ring radius and segment tile size; the inner ring, and the dead zone inside it (no pick but the chips). */
	private static final int RADIUS = 66;
	private static final int TILE = 26;
	private static final int INNER = RADIUS - TILE / 2 - 6;
	private static final int DEAD_ZONE = INNER - 3;
	/** The switch chips in the middle: size, gap, and their row's top (offset from the centre). */
	private static final int CHIP = 18;
	private static final int CHIP_GAP = 6;
	private static final int CHIP_Y = 8;
	/** The middle's text: the pick's name above, the hunger bar and the bond under it (offsets from the centre). */
	private static final int NAME_Y = -25;
	private static final int BAR_Y = -11;
	private static final int BOND_Y = -6;
	private static final int ACCENT = 0xFF8A5AB0;
	/** The panel under the ring that names the pick and says what's stopping it. */
	private static final int INFO_W = 190;
	private static final int INFO_H = 30;
	/**
	 * The ring's tiles clockwise from the top, each an ability index ({@link WebSymbiote#ARMOR} is the Suit: see
	 * {@link WebSymbiote#suitPress}), and the abilities' icons and names by ability index.
	 */
	private static final int[] RING = {WebSymbiote.ARMOR, WebSymbiote.TENDRIL, WebSymbiote.CRAWL, WebSymbiote.CONSUME, WebSymbiote.SPIKES, WebSymbiote.SHROUD};
	private static final double SLOT_DEGREES = 360.0 / RING.length;
	private static final Item[] ICONS = {Items.TRIPWIRE_HOOK, Items.NETHERITE_CHESTPLATE, Items.SPIDER_EYE, Items.ROTTEN_FLESH,
		Items.POINTED_DRIPSTONE, Items.INK_SAC};
	private static final String[] NAMES = {"tendril", "suit", "crawl", "consume", "spikes", "shroud"};
	/** The switch chips left to right: the Mask, the Tendrils, Collect (their pick values, icons and names). */
	private static final int[] CHIPS = {WebSymbiote.MASK_TOGGLE, WebSymbiote.TENDRILS_TOGGLE, WebSymbiote.COLLECT_TOGGLE};
	private static final Item[] CHIP_ICONS = {Items.PLAYER_HEAD, Items.WEEPING_VINES, Items.BUNDLE};
	private static final String[] CHIP_NAMES = {"mask", "tendrils", "collect"};
	private static final String[] FORMS = {"skin", "suit", "armor", "bulk"};

	private int selected = -1;
	private boolean chosen;

	WebSymbioteScreen() {
		super(Component.translatable("screen.jaymospowers.web_symbiote"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		// no blur: the world stays in view
	}

	@Override
	public void tick() {
		// let go of the key: use what's picked (polled, in case the release went elsewhere)
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(WebSymbioteClient.menuKey);
		if (key.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(key.getValue())) {
			choose();
		}
		LocalPlayer player = minecraft.player;
		if (player == null || !WebSymbiote.isBonded(player) || !player.isAlive()) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(final KeyEvent event) {
		if (WebSymbioteClient.menuKey.matches(event)) {
			choose();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		choose();
		return true;
	}

	private void choose() {
		if (chosen) {
			return;
		}
		chosen = true;
		if (selected == WebSymbioteMindUi.PICK) {
			// (0.17.150) the Mind chip: its status page and your standing orders
			WebSymbioteMindUi.open();
			return;
		}
		if (selected >= 0) {
			LocalPlayer player = minecraft.player;
			// (0.17.165) the Suit picked while sneaking: one form down
			int use = selected == WebSymbiote.ARMOR && player != null && player.isShiftKeyDown() ? WebSymbiote.SUIT_TOGGLE : selected;
			ClientPlayNetworking.send(new WebNet.SymbioteUse(use));
		}
		onClose();
	}

	/** The left edge of switch chip {@code i} (0-2), centred as a row on {@code cx}. */
	private static int chipX(final int cx, final int i) {
		int row = CHIPS.length * CHIP + (CHIPS.length - 1) * CHIP_GAP;
		return cx - row / 2 + i * (CHIP + CHIP_GAP);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		int cx = width / 2;
		int cy = height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;
		int before = selected;
		int chipY = cy + CHIP_Y;
		int overChip = -1;
		for (int i = 0; i < CHIPS.length; i++) {
			if (UiStyle.over(mouseX, mouseY, chipX(cx, i) - 1, chipY - 1, CHIP + 2, CHIP + 2)) {
				overChip = i;
			}
		}
		if (overChip >= 0) {
			selected = CHIPS[overChip];
		} else if (WebSymbioteMindUi.overChip(mouseX, mouseY, cx, cy)) {
			selected = WebSymbioteMindUi.PICK;
		} else if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			selected = RING[Math.floorMod((int)Math.round(angle / SLOT_DEGREES), RING.length)];
		}
		if (selected != before && (selected >= 0 || selected == WebSymbioteMindUi.PICK)) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS,
				0.3F, 1.4F + selected * 0.05F, false);
		}

		// a dark disc behind the ring
		int outer = RADIUS + TILE / 2 + 6;
		for (int y = -outer; y <= outer; y++) {
			int half = (int)Math.sqrt(outer * outer - y * y);
			graphics.fill(cx - half, cy + y, cx + half, cy + y + 1, 0xA0101018);
		}
		// a thin ring
		for (int k = 0; k < 120; k++) {
			double t = k * Math.PI * 2.0 / 120.0;
			int x = cx + (int)Math.round(Math.cos(t) * INNER);
			int y = cy + (int)Math.round(Math.sin(t) * INNER);
			graphics.fill(x, y, x + 1, y + 1, UiStyle.ROW);
		}
		// pointer: dots from the inner ring toward the picked tile
		if (selected >= 0 && selected < WebSymbiote.ABILITIES) {
			double t = Math.atan2(dy, dx);
			for (int r = INNER + 4; r < RADIUS - TILE / 2 - 2; r += 4) {
				int x = cx + (int)Math.round(Math.cos(t) * r);
				int y = cy + (int)Math.round(Math.sin(t) * r);
				graphics.fill(x, y, x + 2, y + 2, ACCENT);
			}
		}

		WebSymbioteState state = WebSymbiote.state(player);
		WebSymbioteState.Cooldowns cooldowns = player.getAttached(WebSymbiote.COOLDOWNS);
		if (cooldowns == null) {
			cooldowns = WebSymbioteState.Cooldowns.NONE;
		}
		long now = player.level().getGameTime();
		boolean dormant = com.jaymospowers.power.web.WebSymbioteBond.dormant(player);
		int form = WebSymbiote.form(player);
		int top = WebSymbiote.topForm(player);
		for (int slot = 0; slot < RING.length; slot++) {
			int i = RING[slot];
			double t = Math.toRadians(slot * SLOT_DEGREES - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int)Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			boolean suit = i == WebSymbiote.ARMOR;
			long left = cooldowns.end(i) - now;
			// (the Suit only cools while its next pick is Living Armor going on)
			boolean ready = left <= 0 || suit && !(form == WebSymbiote.FORM_SUIT && top >= WebSymbiote.FORM_ARMOR);
			boolean affordable = state.satiety() >= WebSymbiote.COST[i] || player.isCreative();
			boolean on = suit ? form > WebSymbiote.FORM_SKIN : i == WebSymbiote.CRAWL && state.has(WebSymbioteState.CRAWL);
			boolean picked = i == selected;
			graphics.fill(x, y, x + TILE, y + TILE, UiStyle.CHIP);
			graphics.fill(x, y, x + TILE, y + TILE, picked ? UiStyle.ROW_HOVER : slot % 2 == 0 ? UiStyle.ROW : UiStyle.ROW_ALT);
			graphics.outline(x, y, TILE, TILE, picked ? ACCENT : ready && affordable ? 0x60FFFFFF : UiStyle.ROW_ALT);
			graphics.item(new ItemStack(ICONS[i]), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
			if (!ready) {
				int shade = (int)Math.ceil((TILE - 2) * Math.min(1.0, left / (double)WebSymbiote.COOLDOWN[i]));
				graphics.fill(x + 1, y + 1 + (TILE - 2) - shade, x + TILE - 1, y + TILE - 1, 0xB0000000);
				String seconds = left >= 200 ? Long.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
				graphics.text(font, seconds, x + TILE / 2 - font.width(seconds) / 2, y + TILE / 2 - 4, 0xFFFFFFFF);
			} else if (!affordable) {
				graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0x90000000);
			}
			if (suit) {
				if (form == WebSymbiote.FORM_SKIN && top == WebSymbiote.FORM_SKIN) {
					// it won't come yet: shaded, with how many abilities it has given you of the ones it takes
					graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xD8000000);
					String need = WebSymbiote.unlockedCount(player) + "/" + WebSymbiote.SUIT_ABILITIES;
					graphics.text(font, need, x + TILE / 2 - font.width(need) / 2, y + TILE / 2 - 4, 0xFF8070A0);
				} else {
					// the forms as pips up the right edge: lit up to the one worn, dim up to the one it will give you now,
					// dark for the ones the bond hasn't earned
					for (int f = WebSymbiote.FORM_SUIT; f <= WebSymbiote.FORM_BULK; f++) {
						int py = y + TILE - 4 - (f - WebSymbiote.FORM_SUIT) * 4;
						int color = f <= form ? ACCENT : f <= top ? 0x80FFFFFF : 0x40000000;
						graphics.fill(x + TILE - 4, py, x + TILE - 2, py + 2, color);
					}
				}
			} else if (!WebSymbiote.unlocked(player, i)) {
				// not given to you yet: nearly black, with the bond it takes
				graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xD8000000);
				String need = Integer.toString(WebSymbiote.UNLOCK_BOND[i]);
				graphics.text(font, need, x + TILE / 2 - font.width(need) / 2, y + TILE / 2 - 4, 0xFF8070A0);
			}
			if (dormant) {
				// still settling in after the first bond: nothing answers yet
				graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xB0000000);
			}
			if (on) {
				// a toggle that's on: a lit strip along the bottom
				graphics.fill(x + 2, y + TILE - 3, x + TILE - 2, y + TILE - 1, ACCENT);
			}
		}

		// the switch chips: the Mask (lit while off), the Tendrils (lit while out), Collect (lit while on); dim until usable
		for (int c = 0; c < CHIPS.length; c++) {
			int x = chipX(cx, c);
			int y = chipY;
			boolean picked = selected == CHIPS[c];
			boolean usable = switch (c) {
				case 0 -> WebSymbiote.suitShown(player) && !WebSymbiote.bulked(player);
				case 1 -> WebSymbiote.tendrilsUnlocked(player);
				default -> WebSymbioteCollect.unlocked(player);
			};
			boolean lit = switch (c) {
				case 0 -> WebSymbiote.unmasked(player);
				case 1 -> WebSymbiote.armored(player);
				default -> usable && state.has(WebSymbioteState.COLLECT);
			};
			graphics.fill(x, y, x + CHIP, y + CHIP, UiStyle.CHIP);
			graphics.fill(x, y, x + CHIP, y + CHIP, picked ? UiStyle.ROW_HOVER : UiStyle.ROW_ALT);
			graphics.outline(x, y, CHIP, CHIP, picked ? ACCENT : usable ? 0x60FFFFFF : UiStyle.ROW_ALT);
			graphics.item(new ItemStack(CHIP_ICONS[c]), x + (CHIP - 16) / 2, y + (CHIP - 16) / 2);
			if (!usable) {
				graphics.fill(x + 1, y + 1, x + CHIP - 1, y + CHIP - 1, 0x90000000);
			}
			if (lit) {
				graphics.fill(x + 2, y + CHIP - 3, x + CHIP - 2, y + CHIP - 1, ACCENT);
			}
		}
		WebSymbioteMindUi.drawChip(graphics, font, cx, cy, selected == WebSymbioteMindUi.PICK);

		// the middle: the pick's name, and how fed it is
		Component name = null;
		if (selected == WebSymbioteMindUi.PICK) {
			name = Component.translatable("screen.jaymospowers.web_symbiote_status");
		} else if (selected >= 0) {
			// in the big body, the Spike Burst is a roar
			String key = selected >= WebSymbiote.ABILITIES ? chipName(selected)
				: selected == WebSymbiote.SPIKES && WebSymbiote.bulked(player) ? "roar" : NAMES[selected];
			name = Component.translatable("ability.jaymospowers.web_symbiote." + key);
			UiStyle.lineCentred(graphics, font, name, cx, cy + NAME_Y, 2 * INNER - 24, UiStyle.TEXT);
		}
		int barWidth = 34;
		float fed = Mth.clamp(state.satiety() / (float)WebSymbiote.SATIETY_MAX, 0.0F, 1.0F);
		boolean starving = state.satiety() <= WebSymbiote.STARVING;
		int barColor = starving && (now / 6) % 2 == 0 ? 0xFFB02030 : ACCENT;
		UiStyle.bar(graphics, cx - barWidth / 2, cy + BAR_Y, barWidth, 2, fed, barColor);
		// the bond, under the hunger
		Component bond = Component.translatable("gui.jaymospowers.web_symbiote.bond", state.bond(), WebSymbiote.BOND_MAX);
		graphics.centeredText(font, bond, cx, cy + BOND_Y, UiStyle.DIM);

		// under the ring: the pick's name and what's stopping it (or what this is)
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, height - 4 - INFO_H);
		UiStyle.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		UiStyle.line(graphics, font, name != null ? name : WebSymbioteMindUi.heading(player, title), ix + 8, iy + 6, INFO_W - 16, ACCENT);
		UiStyle.line(graphics, font, status(player, state, cooldowns, now, form, top), ix + 8, iy + 17, INFO_W - 16, name != null ? UiStyle.DIM : UiStyle.HINT);
	}

	/** The lang key of a switch chip's name, by its pick value. */
	private static String chipName(final int pick) {
		for (int c = 0; c < CHIPS.length; c++) {
			if (CHIPS[c] == pick) {
				return CHIP_NAMES[c];
			}
		}
		return "suit";
	}

	private static Component form(final int form) {
		return Component.translatable("gui.jaymospowers.web_symbiote.form." + FORMS[Mth.clamp(form, 0, FORMS.length - 1)]);
	}

	/** One dim line on the pick: ready, on, cooling, too hungry, or the bond it takes; with nothing picked, how to pick. */
	private Component status(final LocalPlayer player, final WebSymbioteState state, final WebSymbioteState.Cooldowns cooldowns, final long now,
		final int form, final int top) {
		if (com.jaymospowers.power.web.WebSymbioteStrains.asleep(player)) {
			// the white one, spent from mending: asleep until it's fed (WebSymbioteStrains.sleep)
			return Component.translatable("gui.jaymospowers.web_symbiote.asleep");
		}
		if (com.jaymospowers.power.web.WebSymbioteBond.dormant(player)) {
			// dormant after the first bond: how long it has left settling in (minutes, rounded up)
			int ticks = com.jaymospowers.power.web.WebSymbioteBond.dormantTicks(player);
			return Component.translatable("gui.jaymospowers.web_symbiote.dormant", (ticks + 1199) / 1200);
		}
		if (selected == WebSymbioteMindUi.PICK) {
			return Component.translatable("gui.jaymospowers.web_symbiote.status_hint");
		}
		if (selected < 0) {
			return Component.translatable("gui.jaymospowers.web_symbiote.hint");
		}
		if (selected == WebSymbiote.MASK_TOGGLE) {
			if (!WebSymbiote.suitShown(player) || WebSymbiote.bulked(player)) {
				return Component.translatable("gui.jaymospowers.web_symbiote.mask_no_suit");
			}
			return Component.translatable(WebSymbiote.unmasked(player) ? "gui.jaymospowers.web_symbiote.mask_off" : "gui.jaymospowers.web_symbiote.mask_on");
		}
		if (selected == WebSymbiote.COLLECT_TOGGLE) {
			if (!WebSymbioteCollect.unlocked(player)) {
				return Component.translatable("gui.jaymospowers.web_symbiote.collect_locked", WebSymbioteCollect.COLLECT_BOND);
			}
			return Component.translatable(WebSymbiote.state(player).has(WebSymbioteState.COLLECT) ? "gui.jaymospowers.web_symbiote.collect_on"
				: "gui.jaymospowers.web_symbiote.collect_off");
		}
		if (selected == WebSymbiote.TENDRILS_TOGGLE) {
			if (!WebSymbiote.tendrilsUnlocked(player)) {
				return Component.translatable("gui.jaymospowers.web_symbiote.tendrils_locked");
			}
			return Component.translatable(WebSymbiote.armored(player) ? "gui.jaymospowers.web_symbiote.tendrils_out" : "gui.jaymospowers.web_symbiote.tendrils_in");
		}
		if (selected == WebSymbiote.ARMOR) {
			// (0.17.165) the Suit: what a pick gives (a form up, or down from the top), what a sneak-pick takes off
			if (form == WebSymbiote.FORM_SKIN) {
				if (top == WebSymbiote.FORM_SKIN) {
					return Component.translatable("gui.jaymospowers.web_symbiote.suit_wait", WebSymbiote.unlockedCount(player), WebSymbiote.SUIT_ABILITIES);
				}
				return Component.translatable("gui.jaymospowers.web_symbiote.suit_pick", form(WebSymbiote.FORM_SUIT));
			}
			if (form >= top) {
				int next = top == WebSymbiote.FORM_SUIT ? WebSymbiote.FORM_ARMOR : top == WebSymbiote.FORM_ARMOR ? WebSymbiote.FORM_BULK : -1;
				return next < 0 ? Component.translatable("gui.jaymospowers.web_symbiote.suit_top", form(form - 1))
					: Component.translatable("gui.jaymospowers.web_symbiote.suit_top_bond", form(form - 1), form(next),
						next == WebSymbiote.FORM_BULK ? WebSymbiote.BOND_MAX : WebSymbiote.UNLOCK_BOND[WebSymbiote.ARMOR]);
			}
			long left = cooldowns.end(WebSymbiote.ARMOR) - now;
			if (form == WebSymbiote.FORM_SUIT && left > 0) {
				return Component.translatable("gui.jaymospowers.web_symbiote.cooling", String.format(Locale.ROOT, "%.1f", left / 20.0));
			}
			return Component.translatable("gui.jaymospowers.web_symbiote.suit_climb", form(form + 1), form(form - 1));
		}
		int i = selected;
		if (!WebSymbiote.unlocked(player, i)) {
			return Component.translatable("gui.jaymospowers.web_symbiote.unlock", WebSymbiote.UNLOCK_BOND[i]);
		}
		long left = cooldowns.end(i) - now;
		boolean on = i == WebSymbiote.CRAWL && state.has(WebSymbioteState.CRAWL);
		Component status;
		if (left > 0) {
			status = Component.translatable("gui.jaymospowers.web_symbiote.cooling", String.format(Locale.ROOT, "%.1f", left / 20.0));
		} else if (state.satiety() < WebSymbiote.COST[i] && !player.isCreative()) {
			status = Component.translatable("gui.jaymospowers.web_symbiote.hungry");
		} else {
			status = Component.translatable(on ? "gui.jaymospowers.web_symbiote.on" : "gui.jaymospowers.web_symbiote.ready");
		}
		return WebSymbiote.COST[i] > 0 ? Component.translatable("gui.jaymospowers.web_symbiote.cost", status, WebSymbiote.COST[i]) : status;
	}
}
