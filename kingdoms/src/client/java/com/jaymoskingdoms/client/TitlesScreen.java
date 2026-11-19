package com.jaymoskingdoms.client;

import com.jaymoskingdoms.TitleChoice;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The H menu's Titles page (You): the titles you have earned (a pillar's, a job's, a finished aim's) in a list, and a press on one
 * wears it under your name. Two rows lead the list: wear what you earned as before, and wear none.
 */
final class TitlesScreen extends Screen {
	private static final int W = 250, H = 196, PAD = 10, ROW = 14, HEAD = 36, FOOT = 24;
	private int scroll;

	TitlesScreen() {
		super(Component.translatable("menu.jaymoskingdoms.tab.titles"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return MenuCompat.centre(this.width, W);
	}

	private int top() {
		return Math.max(MenuCompat.STRIP + 4, (this.height - H) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	@Override
	protected void init() {
		ClientPlayNetworking.send(new TitleChoice.Ask());
	}

	private record Row(String id, Component name, int color) {
	}

	/** The rows: wear as earned, wear none, then each title earned. */
	private List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(TitleChoice.AUTO, Component.translatable("gui.jaymoskingdoms.titles.auto"), Ui.TEXT & 0xFFFFFF));
		rows.add(new Row(TitleChoice.NONE, Component.translatable("gui.jaymoskingdoms.titles.none"), Ui.TEXT & 0xFFFFFF));
		TitleChoice.Info info = TitleChoice.info;
		if (info != null) {
			for (TitleChoice.Entry e : info.titles()) {
				rows.add(new Row(e.id(), e.name(), e.color()));
			}
		}
		return rows;
	}

	private int listTop() {
		return top() + HEAD;
	}

	private int listHeight() {
		return H - HEAD - FOOT;
	}

	private int rowY(int index) {
		return listTop() + index * ROW - this.scroll;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		this.scroll -= (int)Math.signum(scrollY) * ROW;
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int)event.x(), my = (int)event.y();
		if (Ui.over(mx, my, left() + PAD, listTop(), W - 2 * PAD, listHeight())) {
			List<Row> rows = rows();
			for (int i = 0; i < rows.size(); i++) {
				if (Ui.over(mx, my, left() + PAD, rowY(i), W - 2 * PAD - 6, ROW - 1)) {
					ClientPlayNetworking.send(new TitleChoice.Pick(rows.get(i).id()));
					this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = W - 2 * PAD;
		Ui.panel(graphics, x, y, W, H, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("menu.jaymoskingdoms.tab.titles"), x + PAD, y + 8, w, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.titles.hint"), x + PAD, y + 20, w, Ui.DIM);
		Ui.rule(graphics, x + 6, x + W - 6, y + HEAD - 5);
		List<Row> rows = rows();
		String worn = TitleChoice.info == null ? TitleChoice.AUTO : TitleChoice.info.worn();
		int total = rows.size() * ROW;
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, total - listHeight()));
		graphics.enableScissor(x + PAD - 2, listTop(), x + W - PAD + 2, listTop() + listHeight());
		for (int i = 0; i < rows.size(); i++) {
			int ry = rowY(i);
			if (ry + ROW <= listTop() || ry >= listTop() + listHeight()) {
				continue;
			}
			Row r = rows.get(i);
			boolean picked = r.id().equals(worn);
			boolean over = Ui.over(mouseX, mouseY, x + PAD, ry, w - 6, ROW - 1) && mouseY >= listTop() && mouseY < listTop() + listHeight();
			Ui.row(graphics, x + PAD, ry, x + W - PAD - 6, ry + ROW - 1, i, over || picked);
			Ui.line(graphics, this.font, r.name(), x + PAD + 4, ry + 3, w - 60, 0xFF000000 | r.color());
			if (picked) {
				Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.titles.worn"), x + W - PAD - 10, ry + 3, 50, Ui.GOOD);
			}
		}
		graphics.disableScissor();
		Ui.scrollbar(graphics, x + W - PAD - 2, listTop(), listHeight(), listHeight(), total, this.scroll);
		if (TitleChoice.info != null && TitleChoice.info.titles().isEmpty()) {
			Ui.wrapped(graphics, this.font, Component.translatable("gui.jaymoskingdoms.titles.empty"), x + PAD, y + H - FOOT + 4, w, 2, Ui.LINE, Ui.HINT, false);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
