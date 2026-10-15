package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebSymbiote;
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
 * the game. Six segments clockwise from the top, each with its icon and cooldown; the middle shows the pick's name and
 * how fed the symbiote is. (0.11.0) Restyled like the H screen: the H screen's dark panel colours for the disc and tiles,
 * its bar, and a small panel under the ring (accent stripe, the pick's name in the accent, what's stopping it in dim)
 * where a tooltip would be.
 */
final class WebSymbioteScreen extends Screen {
	/** Ring radius, segment tile size, and the dead zone in the middle (no pick). */
	private static final int RADIUS = 58;
	private static final int TILE = 26;
	private static final int DEAD_ZONE = 24;
	/** The suit toggle: a little mask under the satiety bar (offset from the centre, and size). */
	private static final int MASK_Y = 11;
	private static final int MASK_W = 14;
	private static final int MASK_H = 10;
	private static final int ACCENT = 0xFF8A5AB0;
	private static final int DIM = 0xFF505050;
	/** The panel under the ring that names the pick and says what's stopping it. */
	private static final int INFO_W = 190;
	private static final int INFO_H = 30;
	/** The abilities' icons, in menu order. */
	private static final Item[] ICONS = {Items.TRIPWIRE_HOOK, Items.NETHERITE_CHESTPLATE, Items.SPIDER_EYE, Items.ROTTEN_FLESH,
		Items.POINTED_DRIPSTONE, Items.INK_SAC};
	private static final String[] NAMES = {"tendril", "armor", "crawl", "consume", "spikes", "shroud"};

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
		if (selected >= 0) {
			ClientPlayNetworking.send(new WebNet.SymbioteUse(selected));
		}
		onClose();
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
		int maskX = cx - MASK_W / 2;
		int maskY = cy + MASK_Y;
		if (mouseX >= maskX - 1 && mouseX <= maskX + MASK_W && mouseY >= maskY - 1 && mouseY <= maskY + MASK_H) {
			selected = WebSymbiote.SUIT_TOGGLE;
		} else if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			selected = Math.floorMod((int)Math.round(angle / 60.0), WebSymbiote.ABILITIES);
		}
		if (selected != before && selected >= 0) {
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
		for (int k = 0; k < 96; k++) {
			double t = k * Math.PI * 2.0 / 96.0;
			int x = cx + (int)Math.round(Math.cos(t) * (RADIUS - TILE / 2 - 6));
			int y = cy + (int)Math.round(Math.sin(t) * (RADIUS - TILE / 2 - 6));
			graphics.fill(x, y, x + 1, y + 1, UiStyle.ROW);
		}
		// pointer: dots from the middle toward the mouse
		if (selected >= 0 && selected < WebSymbiote.ABILITIES) {
			double t = Math.atan2(dy, dx);
			for (int r = DEAD_ZONE; r < RADIUS - TILE / 2 - 6; r += 4) {
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
		for (int i = 0; i < WebSymbiote.ABILITIES; i++) {
			double t = Math.toRadians(i * 60.0 - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int)Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			long left = cooldowns.end(i) - now;
			boolean ready = left <= 0;
			boolean affordable = state.satiety() >= WebSymbiote.COST[i] || player.isCreative();
			boolean on = i == WebSymbiote.ARMOR && state.has(WebSymbioteState.ARMOR) || i == WebSymbiote.CRAWL && state.has(WebSymbioteState.CRAWL);
			boolean picked = i == selected;
			graphics.fill(x, y, x + TILE, y + TILE, UiStyle.CHIP);
			graphics.fill(x, y, x + TILE, y + TILE, picked ? UiStyle.ROW_HOVER : i % 2 == 0 ? UiStyle.ROW : UiStyle.ROW_ALT);
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
			if (!WebSymbiote.unlocked(player, i)) {
				// not given to you yet: nearly black, with the bond it takes
				graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xD8000000);
				String need = Integer.toString(WebSymbiote.UNLOCK_BOND[i]);
				graphics.text(font, need, x + TILE / 2 - font.width(need) / 2, y + TILE / 2 - 4, 0xFF8070A0);
			}
			if (on) {
				// a toggle that's on: a lit strip along the bottom
				graphics.fill(x + 2, y + TILE - 3, x + TILE - 2, y + TILE - 1, ACCENT);
			}
		}

		// the middle: the pick's name, and how fed it is
		Component name = null;
		if (selected >= 0) {
			// in the big body, the Spike Burst is a roar
			String key = selected >= NAMES.length ? "suit" : selected == WebSymbiote.SPIKES && WebSymbiote.bulked(player) ? "roar" : NAMES[selected];
			name = Component.translatable("ability.jaymospowers.web_symbiote." + key);
			UiStyle.lineCentred(graphics, font, name, cx, cy - 10, 2 * DEAD_ZONE + 20, UiStyle.TEXT);
		}
		// the suit toggle: a little black mask, lit while the suit shows
		boolean suit = state.has(WebSymbioteState.SUIT);
		boolean maskPicked = selected == WebSymbiote.SUIT_TOGGLE;
		graphics.fill(maskX, maskY, maskX + MASK_W, maskY + MASK_H, suit ? 0xFF100C16 : 0xFF3A3640);
		graphics.outline(maskX - 1, maskY - 1, MASK_W + 2, MASK_H + 2, maskPicked ? ACCENT : suit ? 0xA0A080C0 : DIM);
		int eye = suit ? 0xFFEEEEF4 : 0xFF808088;
		graphics.fill(maskX + 2, maskY + 3, maskX + 5, maskY + 5, eye);
		graphics.fill(maskX + 3, maskY + 5, maskX + 5, maskY + 6, eye);
		graphics.fill(maskX + MASK_W - 5, maskY + 3, maskX + MASK_W - 2, maskY + 5, eye);
		graphics.fill(maskX + MASK_W - 5, maskY + 5, maskX + MASK_W - 3, maskY + 6, eye);
		if (!suit && !WebSymbiote.suitAllowed(player)) {
			// it won't come yet: shaded, with how many abilities it has given you of the ones it takes
			graphics.fill(maskX, maskY, maskX + MASK_W, maskY + MASK_H, 0xD8000000);
			String need = WebSymbiote.unlockedCount(player) + "/" + WebSymbiote.SUIT_ABILITIES;
			graphics.text(font, need, maskX + MASK_W / 2 - font.width(need) / 2, maskY + MASK_H + 2, 0xFF8070A0);
		}
		int barWidth = 34;
		float fed = Mth.clamp(state.satiety() / (float)WebSymbiote.SATIETY_MAX, 0.0F, 1.0F);
		boolean starving = state.satiety() <= WebSymbiote.STARVING;
		int barColor = starving && (now / 6) % 2 == 0 ? 0xFFB02030 : ACCENT;
		UiStyle.bar(graphics, cx - barWidth / 2, cy + 5, barWidth, 2, fed, barColor);
		// the bond, under the hunger
		Component bond = Component.translatable("gui.jaymospowers.web_symbiote.bond", state.bond(), WebSymbiote.BOND_MAX);
		graphics.centeredText(font, bond, cx, cy + 11, UiStyle.DIM);

		// under the ring: the pick's name and what's stopping it (or what this is)
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, height - 4 - INFO_H);
		UiStyle.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		UiStyle.line(graphics, font, name != null ? name : title, ix + 8, iy + 6, INFO_W - 16, ACCENT);
		UiStyle.line(graphics, font, status(player, state, cooldowns, now), ix + 8, iy + 17, INFO_W - 16, name != null ? UiStyle.DIM : UiStyle.HINT);
	}

	/** One dim line on the pick: ready, on, cooling, too hungry, or the bond it takes; with nothing picked, how to pick. */
	private Component status(final LocalPlayer player, final WebSymbioteState state, final WebSymbioteState.Cooldowns cooldowns, final long now) {
		if (selected < 0) {
			return Component.translatable("gui.jaymospowers.web_symbiote.hint");
		}
		if (selected == WebSymbiote.SUIT_TOGGLE) {
			if (state.has(WebSymbioteState.SUIT)) {
				return Component.translatable("gui.jaymospowers.web_symbiote.suit_on");
			}
			return WebSymbiote.suitAllowed(player) ? Component.translatable("gui.jaymospowers.web_symbiote.suit_off")
				: Component.translatable("gui.jaymospowers.web_symbiote.suit_wait", WebSymbiote.unlockedCount(player), WebSymbiote.SUIT_ABILITIES);
		}
		int i = selected;
		if (!WebSymbiote.unlocked(player, i)) {
			return Component.translatable("gui.jaymospowers.web_symbiote.unlock", WebSymbiote.UNLOCK_BOND[i]);
		}
		long left = cooldowns.end(i) - now;
		boolean on = i == WebSymbiote.ARMOR && state.has(WebSymbioteState.ARMOR) || i == WebSymbiote.CRAWL && state.has(WebSymbioteState.CRAWL);
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
