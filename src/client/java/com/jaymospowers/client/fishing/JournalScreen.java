package com.jaymospowers.client.fishing;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.fishing.FishLog;
import com.jaymospowers.fishing.Species;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Track D: the fisher's journal. Every kind of fish there is, a line each: the ones you've caught with how many (and how
 * many went back) and your personal best; the rest a blank to fill. Pick one for its page: the best's weight, the day,
 * the bait and the water it came from, and the hours that kind has bitten for you. At the foot, the legends you've
 * landed and your contests.
 */
final class JournalScreen extends Screen {
	private static final int W = 320, H = 230, ACCENT = 0xFFC8A060, PAD = 8, ROW_H = 11, LIST_W = 130;
	private static final String[] QUARTERS = {"dawn", "day", "dusk", "night"};

	private final List<Species> kinds = new ArrayList<>();
	private int scroll;
	private @Nullable Species picked;

	JournalScreen() {
		super(Component.translatable("item.jaymospowers.fishers_journal"));
		for (Species sp : Species.values()) {
			if (sp != Species.OTHER && sp != Species.SHARK) {
				kinds.add(sp);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private FishLog.Log log() {
		return minecraft == null || minecraft.player == null ? FishLog.Log.EMPTY : FishLog.of(minecraft.player);
	}

	private int left() {
		return (width - W) / 2;
	}

	private int topY() {
		return Math.max(2, (height - H) / 2);
	}

	private int rows() {
		return (H - UiStyle.HEADER - 2 * PAD - 24) / ROW_H;
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		FishLog.Log log = log();
		int x = left(), y = topY();
		int known = 0;
		for (Species sp : kinds) {
			if (log.entry(sp).caught() > 0) {
				known++;
			}
		}
		UiStyle.panel(graphics, x, y, W, H, ACCENT);
		UiStyle.header(graphics, font, x, y, W, new ItemStack(com.jaymospowers.fishing.FishLog.JOURNAL), title,
			Component.translatable("gui.jaymospowers.journal.sub", known, kinds.size()), ACCENT, 0);
		int lx = x + PAD, ly = y + UiStyle.HEADER;
		int shown = rows();
		for (int i = 0; i < shown && scroll + i < kinds.size(); i++) {
			Species sp = kinds.get(scroll + i);
			FishLog.Entry e = log.entry(sp);
			int ry = ly + i * ROW_H;
			boolean hover = UiStyle.over(mouseX, mouseY, lx, ry, LIST_W, ROW_H);
			if (sp == picked || hover) {
				graphics.fill(lx, ry, lx + LIST_W, ry + ROW_H, sp == picked ? UiStyle.ROW_PICKED : UiStyle.ROW_HOVER);
			}
			Component name = e.caught() > 0 ? Component.translatable(sp.nameKey()) : Component.literal("? ? ?");
			UiStyle.line(graphics, font, name, lx + 2, ry + 1, LIST_W - 40, e.caught() > 0 ? UiStyle.TEXT : UiStyle.HINT);
			if (e.caught() > 0) {
				UiStyle.lineRight(graphics, font, Component.literal(String.format(Locale.ROOT, "%.1f kg", e.best())), lx + LIST_W - 2, ry + 1, 40,
					UiStyle.DIM);
			}
		}
		if (kinds.size() > shown) {
			UiStyle.scrollbar(graphics, lx + LIST_W + 1, ly, shown * ROW_H, shown, kinds.size(), scroll);
		}
		// the picked kind's page
		int px = x + PAD + LIST_W + 10, py = ly, pw = W - LIST_W - 2 * PAD - 10;
		if (picked == null) {
			UiStyle.wrapped(graphics, font, Component.translatable("gui.jaymospowers.journal.pick"), px, py, pw, 6, UiStyle.LINE, UiStyle.DIM, false);
		} else {
			FishLog.Entry e = log.entry(picked);
			if (e.caught() <= 0) {
				UiStyle.wrapped(graphics, font, Component.translatable("gui.jaymospowers.journal.unknown"), px, py, pw, 6, UiStyle.LINE, UiStyle.DIM, false);
			} else {
				UiStyle.line(graphics, font, Component.translatable(picked.nameKey()), px, py, pw, ACCENT);
				py += 12;
				py = page(graphics, Component.translatable("gui.jaymospowers.journal.caught", e.caught(), e.released()), px, py, pw);
				py = page(graphics, Component.translatable("gui.jaymospowers.journal.best", String.format(Locale.ROOT, "%.1f", e.best()),
					picked.lengthCm(e.best()), e.bestDay()), px, py, pw);
				py = page(graphics, Component.translatable("gui.jaymospowers.journal.bait", bait(e.bestBait())), px, py, pw);
				py = page(graphics, Component.translatable("gui.jaymospowers.journal.water", water(e.bestWater())), px, py, pw);
				int most = 0;
				for (int q = 1; q < 4 && q < e.hours().size(); q++) {
					if (e.hours().get(q) > e.hours().get(most)) {
						most = q;
					}
				}
				py = page(graphics, Component.translatable("gui.jaymospowers.journal.hours",
					Component.translatable("gui.jaymospowers.journal.quarter." + QUARTERS[most])), px, py, pw);
			}
		}
		// legends and contests
		int fy = y + H - PAD - 20;
		UiStyle.rule(graphics, x + 6, x + W - 6, fy - 3);
		Component legends = log.legends().isEmpty() ? Component.translatable("gui.jaymospowers.journal.no_legends")
			: Component.translatable("gui.jaymospowers.journal.legends", String.join(", ", log.legends()));
		UiStyle.line(graphics, font, legends, x + PAD, fy, W - 2 * PAD, UiStyle.DIM);
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.journal.contests", log.contests(), log.won()), x + PAD, fy + 10,
			W - 2 * PAD, UiStyle.DIM);
	}

	private int page(GuiGraphicsExtractor graphics, Component text, int x, int y, int w) {
		return y + UiStyle.wrapped(graphics, font, text, x, y, w, 3, UiStyle.LINE, UiStyle.TEXT, false) * UiStyle.LINE + 3;
	}

	private static Component bait(String id) {
		if (id.isEmpty()) {
			return Component.translatable("gui.jaymospowers.journal.none");
		}
		Identifier key = Identifier.tryParse(id);
		Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		return item == null ? Component.literal(id) : new ItemStack(item).getHoverName();
	}

	private static Component water(String id) {
		Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
		return key == null ? Component.translatable("gui.jaymospowers.journal.none")
			: Component.translatable("biome." + key.getNamespace() + "." + key.getPath());
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		int mouseX = (int) event.x(), mouseY = (int) event.y();
		int lx = left() + PAD, ly = topY() + UiStyle.HEADER;
		for (int i = 0; i < rows() && scroll + i < kinds.size(); i++) {
			if (UiStyle.over(mouseX, mouseY, lx, ly + i * ROW_H, LIST_W, ROW_H)) {
				picked = kinds.get(scroll + i);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(Math.max(0, kinds.size() - rows()), scroll - (int) Math.signum(scrollY)));
		return true;
	}
}
