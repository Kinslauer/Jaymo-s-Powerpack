package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Affinity;
import com.jaymospowers.core.Powers;
import com.jaymospowers.net.PowerPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The affinity choice, shown the first time a player is in survival without a power: which power will awaken in them
 * (it still takes its 3-5 days), or "let fate decide". Made once; Esc doesn't dodge it (H brings it back otherwise).
 * (0.10.x) Restyled like the H screen: a striped row per power (two columns where there's room), click one to pick it,
 * the box at the bottom shows the hovered (or picked) power in full, and Lean / Let fate decide along the foot.
 */
public class AffinityScreen extends Screen {
	private static final int MAX_W = 380;
	private static final int CELL_H = 20;
	/** The widest the name column gets, before the description cut short beside it. */
	private static final int NAME_W = 84;
	private static final int FOOTER = 24;
	private static final int DETAIL_MIN_LINES = 3;
	private static final int DETAIL_MAX_LINES = 6;
	/** The stripe's colour while nothing is picked: fate's. */
	private static final int COLOR_FATE = 0xFFC8B8FF;

	private final List<Power> powers = new ArrayList<>(Powers.fated());
	private @Nullable Power picked;
	/** Grid rows scrolled past, when they don't all fit. */
	private int scroll;
	private @Nullable Button leanButton;

	public AffinityScreen() {
		super(Component.translatable("screen.jaymospowers.affinity.title"));
	}

	private int panelW() {
		return Math.min(MAX_W, this.width - 12);
	}

	private int cols() {
		return panelW() >= 300 ? 2 : 1;
	}

	private int gridRows() {
		return (powers.size() + cols() - 1) / cols();
	}

	private int cellW() {
		return (panelW() - 12) / cols();
	}

	/** The longest description's lines, so the box doesn't jump as you look along the rows. */
	private int detailWanted() {
		int most = 0;
		for (Power power : powers) {
			most = Math.max(most, 1 + this.font.split(power.description(), panelW() - 18).size());
		}
		return Mth.clamp(most, DETAIL_MIN_LINES, DETAIL_MAX_LINES);
	}

	private int panelH() {
		int h = UiStyle.HEADER + gridRows() * CELL_H + 6 + UiStyle.detailHeight(detailWanted()) + FOOTER;
		return Math.min(h, this.height - 8);
	}

	private int detailLines() {
		int room = (panelH() - UiStyle.HEADER - Math.min(gridRows(), 2) * CELL_H - 6 - FOOTER - 9) / UiStyle.LINE;
		return Math.max(2, Math.min(detailWanted(), room));
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int rowsY() {
		return top() + UiStyle.HEADER;
	}

	private int detailY() {
		return top() + panelH() - FOOTER - UiStyle.detailHeight(detailLines());
	}

	private int visibleRows() {
		return Math.max(1, (detailY() - 4 - rowsY()) / CELL_H);
	}

	private int maxScroll() {
		return Math.max(0, gridRows() - visibleRows());
	}

	private int cellX(int i) {
		return left() + 6 + (i % cols()) * cellW();
	}

	/** Where cell {@code i} sits now, or -1 when it's scrolled out of view. */
	private int cellY(int i) {
		int row = i / cols() - scroll;
		return row < 0 || row >= visibleRows() ? -1 : rowsY() + row * CELL_H;
	}

	@Override
	protected void init() {
		int y = top() + panelH() - FOOTER + 4;
		int half = (panelW() - 18) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.affinity.fate"), b -> choose(Affinity.FATE.toString()))
			.bounds(left() + 6, y, half, 16)
			.build());
		leanButton = addRenderableWidget(Button.builder(Component.translatable("screen.jaymospowers.affinity.choose"), b -> {
			if (picked != null) {
				choose(picked.id().toString());
			}
		}).bounds(left() + panelW() - 6 - half, y, half, 16).build());
	}

	private void choose(String id) {
		ClientPlayNetworking.send(new PowerPayloads.ChooseAffinity(id));
		onClose();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private @Nullable Power at(int mouseX, int mouseY) {
		for (int i = 0; i < powers.size(); i++) {
			int cy = cellY(i);
			if (cy >= 0 && UiStyle.over(mouseX, mouseY, cellX(i), cy, cellW() - 2, CELL_H - 2)) {
				return powers.get(i);
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Power power = at((int)event.x(), (int)event.y());
		if (power != null) {
			picked = power;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int)Math.signum(scrollY), 0, maxScroll());
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		scroll = Mth.clamp(scroll, 0, maxScroll());
		int x = left();
		int y = top();
		int w = panelW();
		Power hovered = at(mouseX, mouseY);
		int accent = picked != null ? 0xFF000000 | picked.color() : COLOR_FATE;
		UiStyle.panel(graphics, x, y, w, panelH(), accent);
		UiStyle.header(graphics, this.font, x, y, w, null, this.title, Component.translatable("screen.jaymospowers.affinity.sub"), accent, 0);

		int cw = cellW();
		int nameW = 40;
		for (Power power : powers) {
			nameW = Math.max(nameW, this.font.width(power.name()));
		}
		nameW = Math.min(NAME_W, nameW);
		for (int i = 0; i < powers.size(); i++) {
			int cy = cellY(i);
			if (cy < 0) {
				continue;
			}
			Power power = powers.get(i);
			int cx = cellX(i);
			int color = 0xFF000000 | power.color();
			boolean isPicked = power == picked;
			if (isPicked) {
				graphics.fill(cx, cy, cx + cw - 2, cy + CELL_H - 2, UiStyle.ROW_PICKED);
				graphics.fill(cx, cy, cx + 2, cy + CELL_H - 2, color);
			} else {
				UiStyle.row(graphics, cx, cy, cx + cw - 2, cy + CELL_H - 2, i / cols() + i % cols(), power == hovered);
			}
			graphics.item(power.icon(), cx + 4, cy + 1);
			UiStyle.line(graphics, this.font, power.name(), cx + 23, cy + 5, nameW, color);
			int textX = cx + 23 + nameW + 6;
			UiStyle.line(graphics, this.font, power.description(), textX, cy + 5, cx + cw - 6 - textX, UiStyle.DIM);
		}
		UiStyle.scrollbar(graphics, x + w - 4, rowsY(), visibleRows() * CELL_H, visibleRows(), gridRows(), scroll);

		// the box at the bottom: the hovered power (or the one picked) in full, or what this is
		Power shown = hovered != null ? hovered : picked;
		if (shown != null) {
			UiStyle.detail(graphics, this.font, x, detailY(), w, detailLines(), shown.name(), 0xFF000000 | shown.color(), List.of(shown.description()));
		} else {
			UiStyle.detail(graphics, this.font, x, detailY(), w, detailLines(), null, accent,
				List.of(Component.translatable("screen.jaymospowers.affinity.hint"),
					Component.translatable("screen.jaymospowers.affinity.pick").withColor(UiStyle.HINT & 0xFFFFFF)));
		}
		if (leanButton != null) {
			leanButton.active = picked != null;
			leanButton.setMessage(picked != null ? Component.translatable("screen.jaymospowers.affinity.lean", picked.name())
				: Component.translatable("screen.jaymospowers.affinity.choose"));
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
