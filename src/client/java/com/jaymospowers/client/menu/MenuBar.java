package com.jaymospowers.client.menu;

import com.jaymospowers.client.PowerScreen;
import com.jaymospowers.client.UiStyle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Draws the menu ({@link MenuTabs}) over a page and takes the clicks on it: the bar with a plate and a dropdown list for
 * each group, or the side list of every page down the left. Everything is laid out from the screen's size each time it
 * is drawn or clicked, so nothing is kept but which dropdown is open and how far the lists are scrolled.
 */
final class MenuBar {
	/** A dropdown row's height, a side list row's, and a group heading's. */
	private static final int ROW = 18, SIDE_ROW = 12, HEAD = 14;
	/** The gap between plates, a plate's padding round its name, the look button's width, and the room a hint needs. */
	private static final int GAP = 6, PAD = 8, BTN_W = 18, HINT_PAD = 10;
	/** The side list's header, and its footer (where the look button sits). */
	private static final int SIDE_TOP = 20, SIDE_FOOT = 22;
	private static final int GOLD_EDGE = 0xFFB89B3F, GOLD_FILL = 0x40FFD54A, LOCKED = 0xFF5C5C68;

	/** The group whose dropdown is open (by hover, or by a click that pinned it), and the one a click pinned. */
	private static @Nullable String open, pinned;
	private static int dropScroll, sideScroll;
	private static WeakReference<Screen> scrolledFor = new WeakReference<>(null);

	private MenuBar() {
	}

	/** A group's plate on the bar. */
	private record Plate(String group, int x, int w) {
	}

	/** The bar's plates, where the open page's name goes (-1: no room for it), and the look button. */
	private record Bar(List<Plate> plates, int nameX, int buttonX) {
	}

	/** Whether {@code page} gets the side list (a full-width page keeps the bar, as the side list would cover it). */
	private static boolean sideFor(MenuTabs.Tab page) {
		return MenuTabs.side() && !MenuTabs.isWide(page);
	}

	/** The look button is on the bar only while the bar is the pick (a full-width page keeps the bar with the list picked). */
	private static boolean showButton() {
		return !MenuTabs.side();
	}

	private static Component groupName(String group) {
		return Component.translatable("menu.jaymospowers.group." + group);
	}

	private static Bar bar(Screen screen, MenuTabs.Tab page) {
		Font font = Minecraft.getInstance().font;
		List<Plate> plates = new ArrayList<>();
		int total = 0;
		for (String group : MenuTabs.GROUPS) {
			if (!MenuTabs.listed(group).isEmpty()) {
				int w = font.width(groupName(group)) + 2 * PAD + 10;
				plates.add(new Plate(group, total, w));
				total += w + GAP;
			}
		}
		int buttonX = screen.width - 4 - BTN_W;
		int nameW = font.width(page.label()) + 2 * GAP;
		boolean name = total + nameW <= buttonX - 8;
		int width = total + (name ? nameW : 0);
		int x0 = Math.max(4, Math.min((screen.width - width) / 2, buttonX - 4 - width));
		List<Plate> placed = new ArrayList<>();
		for (Plate p : plates) {
			placed.add(new Plate(p.group(), x0 + p.x(), p.w()));
		}
		return new Bar(placed, name ? x0 + total : -1, buttonX);
	}

	/** A dropdown's box: where it is and how many rows show. */
	private record Drop(int x, int y, int w, int h, int rows, List<MenuTabs.Tab> tabs) {
	}

	private static @Nullable Drop drop(Screen screen, Bar bar, @Nullable String group) {
		if (group == null) {
			return null;
		}
		Font font = Minecraft.getInstance().font;
		for (Plate p : bar.plates()) {
			if (p.group().equals(group)) {
				List<MenuTabs.Tab> tabs = MenuTabs.listed(group);
				int w = 110;
				for (MenuTabs.Tab t : tabs) {
					w = Math.max(w, font.width(t.label()) + 16 + 20);
				}
				int rows = Math.max(1, Math.min(tabs.size(), (screen.height - MenuTabs.BAR_H - 8) / ROW));
				return new Drop(Math.max(2, Math.min(p.x(), screen.width - w - 2)), MenuTabs.BAR_H, w, rows * ROW + 4, rows, tabs);
			}
		}
		return null;
	}

	private static boolean in(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	/** Opens the dropdown the mouse is over (or keeps the one it is in, or the one a click pinned). */
	private static void track(Screen screen, Bar bar, int mouseX, int mouseY) {
		for (Plate p : bar.plates()) {
			if (in(mouseX, mouseY, p.x(), MenuTabs.STRIP_Y, p.w(), MenuTabs.STRIP_H)) {
				if (!p.group().equals(open)) {
					dropScroll = 0;
				}
				open = p.group();
				return;
			}
		}
		Drop d = drop(screen, bar, open);
		if (d != null && in(mouseX, mouseY, d.x(), d.y(), d.w(), d.h())) {
			return;
		}
		open = pinned;
	}

	private static void go(MenuTabs.Tab page, MenuTabs.Tab to) {
		open = null;
		pinned = null;
		if (to != page) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			to.open().run();
		}
	}

	/** The tab {@code step} along from {@code page} among the ones open to you (round the ends). */
	private static MenuTabs.Tab along(MenuTabs.Tab page, int step) {
		List<MenuTabs.Tab> tabs = MenuTabs.shown();
		return tabs.isEmpty() ? page : tabs.get(Math.floorMod(Math.max(0, tabs.indexOf(page)) + step, tabs.size()));
	}

	private static ItemStack icon(MenuTabs.Tab tab) {
		try {
			ItemStack icon = tab.icon().get();
			return icon == null ? ItemStack.EMPTY : icon;
		} catch (RuntimeException | LinkageError e) {
			return ItemStack.EMPTY;
		}
	}

	static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		MenuTabs.Tab page = MenuTabs.of(screen);
		if (page == null) {
			return;
		}
		graphics.nextStratum();
		if (sideFor(page)) {
			drawSide(screen, graphics, mouseX, mouseY, page);
		} else {
			drawBar(screen, graphics, mouseX, mouseY, page);
		}
	}

	private static void triangle(GuiGraphicsExtractor graphics, int x, int y, int color) {
		graphics.fill(x, y, x + 5, y + 1, color);
		graphics.fill(x + 1, y + 1, x + 4, y + 2, color);
		graphics.fill(x + 2, y + 2, x + 3, y + 3, color);
	}

	/** A small button with a drawn glyph: {@code list} three lines (to the side list), else a bar over two ticks (to the bar). */
	private static void lookButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean list, boolean hover) {
		graphics.fill(x, y, x + w, y + h, hover ? GOLD_EDGE : PowerScreen.COLOR_LIGHT);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? PowerScreen.COLOR_WELL_LIGHT : PowerScreen.COLOR_TAB);
		int color = hover ? PowerScreen.COLOR_GOLD : PowerScreen.COLOR_TEXT, gx = x + (w - 8) / 2, gy = y + (h - 7) / 2;
		if (list) {
			graphics.fill(gx, gy, gx + 8, gy + 1, color);
			graphics.fill(gx, gy + 3, gx + 8, gy + 4, color);
			graphics.fill(gx, gy + 6, gx + 8, gy + 7, color);
		} else {
			graphics.fill(gx, gy, gx + 8, gy + 2, color);
			graphics.fill(gx + 1, gy + 4, gx + 3, gy + 7, color);
			graphics.fill(gx + 5, gy + 4, gx + 7, gy + 7, color);
		}
	}

	private static void drawBar(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, MenuTabs.Tab page) {
		Font font = Minecraft.getInstance().font;
		Bar bar = bar(screen, page);
		track(screen, bar, mouseX, mouseY);
		graphics.fill(0, 0, screen.width, MenuTabs.BAR_H, PowerScreen.COLOR_EDGE | 0xFF000000);
		graphics.fill(0, MenuTabs.BAR_H - 1, screen.width, MenuTabs.BAR_H, GOLD_EDGE);
		String pageGroup = MenuTabs.groupOf(page);
		for (Plate p : bar.plates()) {
			boolean picked = p.group().equals(pageGroup), hot = p.group().equals(open);
			int y = MenuTabs.STRIP_Y, h = MenuTabs.STRIP_H;
			graphics.fill(p.x(), y, p.x() + p.w(), y + h, picked || hot ? GOLD_EDGE : PowerScreen.COLOR_LIGHT);
			graphics.fill(p.x() + 1, y + 1, p.x() + p.w() - 1, y + h - 1, hot ? PowerScreen.COLOR_WELL_LIGHT : picked ? PowerScreen.COLOR_BODY : PowerScreen.COLOR_TAB);
			int color = picked ? PowerScreen.COLOR_GOLD : PowerScreen.COLOR_TEXT;
			graphics.text(font, groupName(p.group()), p.x() + PAD, y + (h - 8) / 2, color, false);
			triangle(graphics, p.x() + p.w() - PAD - 5, y + (h - 3) / 2, color);
		}
		if (bar.nameX() >= 0) {
			graphics.text(font, page.label(), bar.nameX() + GAP, MenuTabs.STRIP_Y + (MenuTabs.STRIP_H - 8) / 2, PowerScreen.COLOR_DIM, false);
		}
		Component hint = Component.translatable("menu.jaymospowers.bar.hint");
		int hintX = bar.plates().isEmpty() ? 0 : bar.plates().getLast().x() + bar.plates().getLast().w() + GAP;
		if (bar.nameX() >= 0) {
			hintX = bar.nameX() + font.width(page.label()) + 3 * GAP;
		}
		int hintW = font.width(hint);
		if (hintX + hintW + HINT_PAD < bar.buttonX()) {
			graphics.text(font, hint, bar.buttonX() - HINT_PAD - hintW, MenuTabs.STRIP_Y + (MenuTabs.STRIP_H - 8) / 2, PowerScreen.COLOR_DIM, false);
		}
		boolean overButton = showButton() && in(mouseX, mouseY, bar.buttonX(), MenuTabs.STRIP_Y, BTN_W, MenuTabs.STRIP_H);
		if (showButton()) {
			lookButton(graphics, bar.buttonX(), MenuTabs.STRIP_Y, BTN_W, MenuTabs.STRIP_H, true, overButton);
		}
		Component tip = overButton ? Component.translatable("menu.jaymospowers.bar.to_side") : null;
		Drop d = drop(screen, bar, open);
		if (d != null) {
			dropScroll = Math.clamp(dropScroll, 0, Math.max(0, d.tabs().size() - d.rows()));
			graphics.fill(d.x(), d.y(), d.x() + d.w(), d.y() + d.h(), GOLD_EDGE);
			graphics.fill(d.x() + 1, d.y(), d.x() + d.w() - 1, d.y() + d.h() - 1, PowerScreen.COLOR_CANVAS | 0xFF000000);
			for (int i = 0; i < d.rows(); i++) {
				MenuTabs.Tab t = d.tabs().get(i + dropScroll);
				int ry = d.y() + 2 + i * ROW;
				Component lock = MenuTabs.lock(t);
				boolean hover = in(mouseX, mouseY, d.x() + 2, ry, d.w() - 4, ROW), here = t == page;
				if (here) {
					graphics.fill(d.x() + 2, ry, d.x() + d.w() - 2, ry + ROW, GOLD_FILL);
				} else if (hover && lock == null) {
					graphics.fill(d.x() + 2, ry, d.x() + d.w() - 2, ry + ROW, PowerScreen.COLOR_WELL_LIGHT);
				}
				ItemStack icon = icon(t);
				if (!icon.isEmpty()) {
					graphics.item(icon, d.x() + 4, ry + 1);
					if (lock != null) {
						graphics.fill(d.x() + 4, ry + 1, d.x() + 20, ry + 17, 0xB0141418);
					}
				}
				UiStyle.line(graphics, font, t.label(), d.x() + 24, ry + (ROW - 8) / 2, d.w() - 30,
					lock != null ? LOCKED : here ? PowerScreen.COLOR_GOLD : PowerScreen.COLOR_TEXT);
				if (hover && lock != null && !lock.getString().isEmpty()) {
					tip = lock;
				}
			}
			if (d.tabs().size() > d.rows()) {
				UiStyle.scrollbar(graphics, d.x() + d.w() - 4, d.y() + 2, d.h() - 4, d.rows(), d.tabs().size(), dropScroll);
			}
		}
		if (tip != null) {
			UiStyle.tip(graphics, font, tip, mouseX, mouseY);
		}
	}

	/** One line of the side list: a group's heading, or a page. */
	private record Entry(@Nullable String group, MenuTabs.@Nullable Tab tab) {
		int height() {
			return tab == null ? HEAD : SIDE_ROW;
		}
	}

	private static List<Entry> entries() {
		List<Entry> out = new ArrayList<>();
		for (String group : MenuTabs.GROUPS) {
			List<MenuTabs.Tab> tabs = MenuTabs.listed(group);
			if (!tabs.isEmpty()) {
				out.add(new Entry(group, null));
				for (MenuTabs.Tab t : tabs) {
					out.add(new Entry(group, t));
				}
			}
		}
		return out;
	}

	private static int total(List<Entry> entries) {
		int total = 0;
		for (Entry e : entries) {
			total += e.height();
		}
		return total;
	}

	private static void drawSide(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, MenuTabs.Tab page) {
		Font font = Minecraft.getInstance().font;
		int w = MenuTabs.SIDE_W, top = SIDE_TOP, bottom = screen.height - SIDE_FOOT;
		graphics.fill(0, 0, w, screen.height, PowerScreen.COLOR_EDGE | 0xFF000000);
		graphics.fill(w - 1, 0, w, screen.height, GOLD_EDGE);
		graphics.text(font, Component.translatable("menu.jaymospowers.side.title"), 8, 6, PowerScreen.COLOR_GOLD, false);
		List<Entry> entries = entries();
		int total = total(entries), view = Math.max(1, bottom - top);
		if (scrolledFor.get() != screen) {
			scrolledFor = new WeakReference<>(screen);
			int y = 0;
			for (Entry e : entries) {
				if (e.tab() == page) {
					sideScroll = y + e.height() - view + SIDE_ROW;
				}
				y += e.height();
			}
		}
		sideScroll = Math.clamp(sideScroll, 0, Math.max(0, total - view));
		Component tip = null;
		graphics.enableScissor(0, top, w - 1, bottom);
		int y = top - sideScroll;
		for (Entry e : entries) {
			if (y + e.height() > top && y < bottom) {
				if (e.tab() == null) {
					graphics.text(font, Component.literal(groupName(e.group()).getString().toUpperCase(java.util.Locale.ROOT)), 8, y + 4, PowerScreen.COLOR_DIM, false);
				} else {
					Component lock = MenuTabs.lock(e.tab());
					boolean here = e.tab() == page, hover = in(mouseX, mouseY, 0, y, w - 1, SIDE_ROW) && mouseY >= top && mouseY < bottom;
					if (here) {
						graphics.fill(2, y, w - 5, y + SIDE_ROW, GOLD_FILL);
						graphics.fill(2, y, 4, y + SIDE_ROW, PowerScreen.COLOR_GOLD);
					} else if (hover && lock == null) {
						graphics.fill(2, y, w - 5, y + SIDE_ROW, PowerScreen.COLOR_WELL_LIGHT);
					}
					UiStyle.line(graphics, font, e.tab().label(), 10, y + 2, w - 20,
						lock != null ? LOCKED : here ? PowerScreen.COLOR_GOLD : PowerScreen.COLOR_TEXT);
					if (hover && lock != null && !lock.getString().isEmpty()) {
						tip = lock;
					}
				}
			}
			y += e.height();
		}
		graphics.disableScissor();
		if (total > view) {
			UiStyle.scrollbar(graphics, w - 4, top, view, view, total, sideScroll);
		}
		int fy = screen.height - SIDE_FOOT + 4;
		boolean overButton = in(mouseX, mouseY, 6, fy, w - 12, 14);
		lookButton(graphics, 6, fy, 18, 14, false, overButton);
		graphics.text(font, Component.translatable("menu.jaymospowers.side.to_bar"), 28, fy + 3, overButton ? PowerScreen.COLOR_GOLD : PowerScreen.COLOR_DIM, false);
		if (tip != null) {
			UiStyle.tip(graphics, font, tip, mouseX, mouseY);
		}
	}

	/** A click on the menu goes to the menu; true if it was on it (the page doesn't get it). */
	static boolean click(Screen screen, int mouseX, int mouseY) {
		MenuTabs.Tab page = MenuTabs.of(screen);
		if (page == null) {
			return false;
		}
		return sideFor(page) ? clickSide(screen, mouseX, mouseY, page) : clickBar(screen, mouseX, mouseY, page);
	}

	private static boolean clickBar(Screen screen, int mouseX, int mouseY, MenuTabs.Tab page) {
		Bar bar = bar(screen, page);
		track(screen, bar, mouseX, mouseY);
		Drop d = drop(screen, bar, open);
		if (d != null && in(mouseX, mouseY, d.x(), d.y(), d.w(), d.h())) {
			int row = (mouseY - d.y() - 2) / ROW;
			if (mouseY >= d.y() + 2 && row < d.rows()) {
				MenuTabs.Tab t = d.tabs().get(row + dropScroll);
				if (MenuTabs.lock(t) == null) {
					go(page, t);
				}
			}
			return true;
		}
		if (mouseY < MenuTabs.BAR_H) {
			for (Plate p : bar.plates()) {
				if (in(mouseX, mouseY, p.x(), MenuTabs.STRIP_Y, p.w(), MenuTabs.STRIP_H)) {
					pinned = p.group().equals(pinned) ? null : p.group();
					open = pinned;
					return true;
				}
			}
			if (showButton() && in(mouseX, mouseY, bar.buttonX(), MenuTabs.STRIP_Y, BTN_W, MenuTabs.STRIP_H)) {
				MenuTabs.setLook(true);
			}
			return true;
		}
		if (pinned != null) {
			pinned = null;
			open = null;
		}
		return false;
	}

	private static boolean clickSide(Screen screen, int mouseX, int mouseY, MenuTabs.Tab page) {
		int w = MenuTabs.SIDE_W, top = SIDE_TOP, bottom = screen.height - SIDE_FOOT;
		if (mouseX >= w) {
			return false;
		}
		if (mouseY >= bottom) {
			if (in(mouseX, mouseY, 6, bottom + 4, w - 12, 14)) {
				MenuTabs.setLook(false);
			}
			return true;
		}
		if (mouseY >= top) {
			int y = top - sideScroll;
			for (Entry e : entries()) {
				if (e.tab() != null && mouseY >= y && mouseY < y + SIDE_ROW && MenuTabs.lock(e.tab()) == null) {
					go(page, e.tab());
					break;
				}
				y += e.height();
			}
		}
		return true;
	}

	/** The wheel over the bar turns pages, over a dropdown or the side list scrolls it; true if the mouse was on the menu. */
	static boolean scroll(Screen screen, int mouseX, int mouseY, double amount) {
		MenuTabs.Tab page = MenuTabs.of(screen);
		if (page == null) {
			return false;
		}
		if (sideFor(page)) {
			if (mouseX >= MenuTabs.SIDE_W) {
				return false;
			}
			sideScroll = Math.max(0, sideScroll - (int)Math.signum(amount) * SIDE_ROW * 2);
			return true;
		}
		Drop d = drop(screen, bar(screen, page), open);
		if (d != null && in(mouseX, mouseY, d.x(), d.y(), d.w(), d.h())) {
			dropScroll = Math.max(0, dropScroll - (int)Math.signum(amount));
			return true;
		}
		if (mouseY >= MenuTabs.BAR_H) {
			return false;
		}
		if (amount != 0.0) {
			go(page, along(page, amount > 0 ? -1 : 1));
		}
		return true;
	}
}
