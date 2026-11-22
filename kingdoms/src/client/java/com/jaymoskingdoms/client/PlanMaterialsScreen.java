package com.jaymoskingdoms.client;

import com.jaymoskingdoms.MasonNet;
import com.jaymoskingdoms.client.ui.FlatButton;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * A building plan's full material list ({@link MasonNet.Materials}): every block it still needs, how many you carry and
 * how many you're short, short first. Opened by using a plan in the air, or the mason's table's Materials button (which
 * this screen goes back to). Scrolls when long.
 */
final class PlanMaterialsScreen extends Screen {
	static final int W = 300, PAD = 8, HEADER = 22, ROW = 18, MAX_ROWS = 12, MIN_ROWS = 3, BUTTON_H = 16, GAP = 4;
	/** The columns' widths: need, carried, short (right-aligned numbers). */
	static final int NUM_W = 46;

	private final MasonNet.Materials list;
	private int scroll;

	private PlanMaterialsScreen(MasonNet.Materials list) {
		super(Component.translatable("gui.jaymoskingdoms.plan_materials.title", list.design()));
		this.list = list;
	}

	static void open(MasonNet.Materials list) {
		net.minecraft.client.Minecraft.getInstance().gui.setScreen(new PlanMaterialsScreen(list));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int rows() {
		return Mth.clamp((this.height - 8 - fixedH()) / ROW, MIN_ROWS, MAX_ROWS);
	}

	/** The panel's height without the rows: header, column heads, the total line, the hint, the buttons. */
	private int fixedH() {
		return HEADER + 12 + 2 + GAP + 10 + 10 + GAP + BUTTON_H + PAD;
	}

	private int panelH() {
		return fixedH() + rows() * ROW;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH()) / 2);
	}

	private int listTop() {
		return top() + HEADER + 12;
	}

	@Override
	protected void init() {
		int x = left() + PAD, by = top() + panelH() - PAD - BUTTON_H, bw = (W - 2 * PAD - GAP) / 2;
		if (this.list.table().isPresent()) {
			// back to the table's screen: the server sends it again
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.plan_materials.back"),
				b -> ClientPlayNetworking.send(new MasonNet.Act(this.list.table().get(), MasonNet.REFRESH, ""))).bounds(x, by, bw, BUTTON_H).build());
		}
		addRenderableWidget(FlatButton.builder(net.minecraft.network.chat.CommonComponents.GUI_DONE, b -> this.onClose()).bounds(x + bw + GAP, by, bw, BUTTON_H).build());
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		this.scroll = Mth.clamp(this.scroll - (int)Math.signum(scrollY), 0, Math.max(0, this.list.mats().size() - rows()));
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x0 = left(), y0 = top(), x = x0 + PAD, w = W - 2 * PAD;
		Ui.panel(graphics, x0, y0, W, panelH(), MasonScreen.ACCENT);
		Ui.line(graphics, this.font, this.title, x, y0 + 8, w, MasonScreen.ACCENT);
		// column heads
		int numX = x + w - 4 - 3 * NUM_W;
		graphics.text(this.font, Component.translatable("gui.jaymoskingdoms.plan_materials.block"), x + 3, y0 + HEADER + 1, Ui.DIM);
		Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.plan_materials.need"), numX + NUM_W, y0 + HEADER + 1, NUM_W, Ui.DIM);
		Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.plan_materials.have"), numX + 2 * NUM_W, y0 + HEADER + 1, NUM_W, Ui.DIM);
		Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.plan_materials.short"), numX + 3 * NUM_W, y0 + HEADER + 1, NUM_W, Ui.DIM);
		int top = listTop(), rows = rows();
		Ui.well(graphics, x - 1, top - 1, w + 2, rows * ROW + 2);
		List<MasonNet.Mat> mats = this.list.mats();
		if (mats.isEmpty()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.plan_materials.none"), x + 3, top + 4, w - 6, Ui.GOOD);
		}
		ItemStack tip = ItemStack.EMPTY;
		for (int i = this.scroll; i < Math.min(mats.size(), this.scroll + rows); i++) {
			int ry = top + (i - this.scroll) * ROW;
			MasonNet.Mat m = mats.get(i);
			boolean hover = Ui.over(mouseX, mouseY, x, ry, w, ROW - 1);
			graphics.fill(x, ry, x + w, ry + ROW - 1, hover ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW : Ui.ROW_ALT);
			graphics.item(m.item(), x + 1, ry);
			Ui.line(graphics, this.font, m.item().getHoverName(), x + 20, ry + 5, numX - x - 24, Ui.TEXT);
			Ui.lineRight(graphics, this.font, Component.literal(Integer.toString(m.need())), numX + NUM_W, ry + 5, NUM_W, Ui.TEXT);
			Ui.lineRight(graphics, this.font, Component.literal(Integer.toString(m.have())), numX + 2 * NUM_W, ry + 5, NUM_W, Ui.DIM);
			int s = m.shortBy();
			Ui.lineRight(graphics, this.font, Component.literal(s > 0 ? Integer.toString(s) : "-"), numX + 3 * NUM_W, ry + 5, NUM_W, s > 0 ? Ui.BAD : Ui.GOOD);
			if (hover) {
				tip = m.item();
			}
		}
		Ui.scrollbar(graphics, x + w - 2, top, rows * ROW, rows, mats.size(), this.scroll);
		// the totals, and what counts
		int need = 0, missing = 0;
		for (MasonNet.Mat m : mats) {
			need += m.need();
			missing += m.shortBy();
		}
		int ty = top + rows * ROW + GAP;
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.plan_materials.total", need, missing), x, ty, w, missing > 0 ? Ui.BAD : Ui.GOOD);
		Ui.line(graphics, this.font, Component.translatable(this.list.laid() ? "gui.jaymoskingdoms.plan_materials.laid" : "message.jaymoskingdoms.plan.how"), x,
			ty + 10, w, Ui.HINT);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!tip.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}
}
