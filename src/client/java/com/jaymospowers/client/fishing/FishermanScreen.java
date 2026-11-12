package com.jaymospowers.client.fishing;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.fishing.Fishermen;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Track D: a fisherman's screen. Left, the market: every fish in your pack with what they'll give for it (sell one, or all);
 * right, the village contest (when, enter, your weight, the results) and what they say keeps the water here.
 */
final class FishermanScreen extends Screen {
	private static final int W = 340, H = 220, ACCENT = 0xFF4FB4D8, PAD = 8, ROW_H = 20;

	private Fishermen.View view;
	private int scroll;

	private FishermanScreen(Fishermen.View view) {
		super(Component.translatable("screen.jaymospowers.fisherman"));
		this.view = view;
	}

	static void receive(Minecraft minecraft, Fishermen.View view) {
		if (minecraft.gui.screen() instanceof FishermanScreen open) {
			open.view = view;
			open.scroll = Math.min(open.scroll, Math.max(0, view.offers().size() - 1));
		} else {
			minecraft.gui.setScreen(new FishermanScreen(view));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (width - W) / 2;
	}

	private int topY() {
		return Math.max(2, (height - H) / 2);
	}

	private static String kg(float kg) {
		return String.format(Locale.ROOT, "%.1f", kg);
	}

	private static String price(long milli) {
		return String.format(Locale.ROOT, "%.2f", milli / 1000.0);
	}

	private int marketW() {
		return W / 2 + 10;
	}

	private int rowsShown() {
		return (H - UiStyle.HEADER - 2 * PAD - 18) / ROW_H;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		int x = left(), y = topY();
		UiStyle.panel(graphics, x, y, W, H, ACCENT);
		UiStyle.header(graphics, font, x, y, W, new ItemStack(Items.FISHING_ROD), Component.translatable("screen.jaymospowers.fisherman"),
			Component.translatable("gui.jaymospowers.fisherman.sub"), ACCENT, 0);
		// the market
		int mx = x + PAD, my = y + UiStyle.HEADER, mw = marketW() - PAD;
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.fisherman.market"), mx, my, mw, ACCENT);
		my += 12;
		if (view.offers().isEmpty()) {
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.fisherman.nothing"), mx, my + 4, mw, UiStyle.DIM);
		}
		int shown = rowsShown();
		for (int i = 0; i < shown && scroll + i < view.offers().size(); i++) {
			Fishermen.Offer o = view.offers().get(scroll + i);
			int ry = my + i * ROW_H;
			boolean hover = UiStyle.over(mouseX, mouseY, mx, ry, mw, ROW_H - 2);
			UiStyle.row(graphics, mx, ry, mx + mw, ry + ROW_H - 2, i, hover);
			graphics.item(o.stack(), mx + 2, ry + 1);
			UiStyle.line(graphics, font, o.stack().getHoverName(), mx + 22, ry + 1, mw - 70, UiStyle.TEXT);
			Float w = com.jaymospowers.fishing.FishCatch.weight(o.stack());
			com.jaymospowers.fishing.FishCatch.Info info = com.jaymospowers.fishing.FishCatch.info(o.stack());
			float kg = info != null ? info.kg() : w == null ? 0.0F : w;
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.fisherman.weighs", kg(kg)), mx + 22, ry + 10, mw - 70, UiStyle.DIM);
			UiStyle.lineRight(graphics, font, Component.translatable("gui.jaymospowers.fisherman.price", price(o.milli())), mx + mw - 4, ry + 5, 60,
				hover ? ACCENT : UiStyle.TEXT);
		}
		if (view.offers().size() > shown) {
			UiStyle.scrollbar(graphics, mx + mw + 1, my, shown * ROW_H, shown, view.offers().size(), scroll);
		}
		if (!view.offers().isEmpty()) {
			button(graphics, mouseX, mouseY, mx, y + H - PAD - 14, mw, Component.translatable("gui.jaymospowers.fisherman.sell_all"));
		}
		// the contest
		int cx = x + marketW() + PAD, cy = y + UiStyle.HEADER, cw = W - marketW() - 2 * PAD;
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.fisherman.contest"), cx, cy, cw, ACCENT);
		cy += 12;
		Component status = switch (view.contest()) {
			case 1 -> Component.translatable("gui.jaymospowers.fisherman.contest.entered", view.day() - view.today());
			case 2 -> Component.translatable("gui.jaymospowers.fisherman.contest.running");
			case 3 -> Component.translatable(view.won() ? "gui.jaymospowers.fisherman.contest.won" : "gui.jaymospowers.fisherman.contest.results");
			default -> view.day() == view.today() ? Component.translatable("gui.jaymospowers.fisherman.contest.today")
				: Component.translatable("gui.jaymospowers.fisherman.contest.next", view.day() - view.today());
		};
		cy += UiStyle.wrapped(graphics, font, status, cx, cy, cw, 4, UiStyle.LINE, UiStyle.TEXT, false) * UiStyle.LINE;
		cy += 4;
		for (int i = 0; i < view.standings().size() && i < 6; i++) {
			Fishermen.Standing s = view.standings().get(i);
			Component line = Component.translatable("gui.jaymospowers.fisherman.standing", view.contest() == 3 ? (i + 1) + ". " : "", s.name(), kg(s.kg()),
				s.species().isEmpty() ? Component.literal("-") : Component.translatable(s.species()));
			UiStyle.line(graphics, font, line, cx, cy, cw, s.you() ? ACCENT : UiStyle.DIM);
			cy += 10;
		}
		if (view.contest() == 0) {
			button(graphics, mouseX, mouseY, cx, cy + 2, cw, Component.translatable("gui.jaymospowers.fisherman.enter"));
			cy += 18;
		}
		// the water's legend
		cy += 6;
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.fisherman.water"), cx, cy, cw, ACCENT);
		cy += 12;
		Component rumour = view.legend().isEmpty() ? Component.translatable("gui.jaymospowers.fisherman.no_legend")
			: Component.translatable("gui.jaymospowers.fisherman.legend", view.legend(), Component.translatable(view.legendSpecies()),
				String.valueOf(Math.round(view.legendKg())));
		UiStyle.wrapped(graphics, font, rumour, cx, cy, cw, 6, UiStyle.LINE, UiStyle.DIM, false);
	}

	private void button(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, Component label) {
		boolean hover = UiStyle.over(mouseX, mouseY, x, y, w, 14);
		graphics.fill(x, y, x + w, y + 14, hover ? UiStyle.ROW_HOVER : UiStyle.ROW);
		UiStyle.lineCentred(graphics, font, label, x + w / 2, y + 3, w - 6, hover ? ACCENT : UiStyle.TEXT);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		int mouseX = (int) event.x(), mouseY = (int) event.y();
		int x = left(), y = topY();
		int mx = x + PAD, my = y + UiStyle.HEADER + 12, mw = marketW() - PAD;
		int shown = rowsShown();
		for (int i = 0; i < shown && scroll + i < view.offers().size(); i++) {
			if (UiStyle.over(mouseX, mouseY, mx, my + i * ROW_H, mw, ROW_H - 2)) {
				ClientPlayNetworking.send(new Fishermen.Action(view.villager(), Fishermen.Action.SELL, view.offers().get(scroll + i).slot()));
				return true;
			}
		}
		if (!view.offers().isEmpty() && UiStyle.over(mouseX, mouseY, mx, y + H - PAD - 14, mw, 14)) {
			ClientPlayNetworking.send(new Fishermen.Action(view.villager(), Fishermen.Action.SELL_ALL, 0));
			return true;
		}
		if (view.contest() == 0) {
			int cx = x + marketW() + PAD, cw = W - marketW() - 2 * PAD;
			// the enter button sits under the status line(s): find it by its row
			int cy = y + UiStyle.HEADER + 12;
			Component status = view.day() == view.today() ? Component.translatable("gui.jaymospowers.fisherman.contest.today")
				: Component.translatable("gui.jaymospowers.fisherman.contest.next", view.day() - view.today());
			int lines = Math.min(4, font.split(status, cw).size());
			cy += lines * UiStyle.LINE + 4 + Math.min(6, view.standings().size()) * 10 + 2;
			if (UiStyle.over(mouseX, mouseY, cx, cy, cw, 14)) {
				ClientPlayNetworking.send(new Fishermen.Action(view.villager(), Fishermen.Action.ENTER, 0));
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(Math.max(0, view.offers().size() - rowsShown()), scroll - (int) Math.signum(scrollY)));
		return true;
	}
}
