package com.jaymoskingdoms.client.mixin.mca;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Andy: "can these buttons or UI be made any more legible?" MCA's villager screen puts its buttons in one column on the
 * right, 80 wide, so longer labels (Ask about Money, Talk Them Round, Where Were You) scrolled and read cut off, with
 * gaps where hidden buttons sat, grey on a busy world. After every page is laid out (and after our own page mixins have
 * added, hidden and stacked theirs, hence the later priority) the column is tidied: every button is as wide as the
 * longest label needs (still growing leftwards from MCA's right edge, capped so it never covers the middle of the
 * screen; a label still too long keeps its scroll and gets its full text as a tooltip), the shown ones stack with no
 * gaps from the top, the back arrow moves out to the left, and a dark panel sits behind the column. Every button keeps
 * its own press handler, so all options and chat interactions work as before.
 */
@Mixin(value = AbstractDynamicScreen.class, priority = 1100)
abstract class McaButtonColumnMixin extends Screen {
	/** MCA's column: buttons at x -90 from the right edge, 80 wide, so their right edge is 10 in from the screen's. */
	@Unique
	private static final int RIGHT_GAP = 10, MCA_WIDTH = 80;
	/** Room either side of a label, the widest the column may grow, and how much of the screen it may take. */
	@Unique
	private static final int LABEL_PAD = 12, MAX_WIDTH = 150;
	/** The panel's margin round the buttons, and the space between it and the back arrow. */
	@Unique
	private static final int PANEL_PAD = 3, ARROW_GAP = 2;
	/** Panel fill and border, in the dark purple of MCA's info tags on the left. */
	@Unique
	private static final int PANEL_FILL = 0xC0100010, BORDER_TOP = 0x905000FF, BORDER_BOTTOM = 0x9028007F;
	@Unique
	private static final String BACK = "gui.button.backarrow";

	/** The column's buttons on this page, shown or not, for the panel. */
	@Unique
	private final List<AbstractWidget> jaymoskingdoms$column = new ArrayList<>();

	protected McaButtonColumnMixin(Component title) {
		super(title);
	}

	@Inject(method = "setLayout", at = @At("TAIL"))
	private void jaymoskingdoms$tidyColumn(String layout, CallbackInfo ci) {
		this.jaymoskingdoms$column.clear();
		int right = this.width - RIGHT_GAP;
		List<AbstractWidget> arrows = new ArrayList<>();
		for (GuiEventListener l : this.children()) {
			if (!(l instanceof AbstractButton w)) {
				continue;
			}
			if (l instanceof McaButtonExAccessor ex && BACK.equals(ex.jaymoskingdoms$apiButton().identifier())) {
				arrows.add(w);
			} else if (w.getRight() == right && w.getWidth() >= MCA_WIDTH) {
				this.jaymoskingdoms$column.add(w);
			}
		}
		if (this.jaymoskingdoms$column.isEmpty()) {
			return;
		}
		// as wide as the longest shown label needs, never narrower than MCA's, never past a third of the screen
		int cap = Math.max(MCA_WIDTH, Math.min(MAX_WIDTH, this.width / 3));
		int want = MCA_WIDTH;
		for (AbstractWidget w : this.jaymoskingdoms$column) {
			if (w.visible) {
				want = Math.max(want, this.font.width(w.getMessage()) + LABEL_PAD);
			}
		}
		int wide = Math.min(want, cap), left = right - wide;
		for (AbstractWidget w : this.jaymoskingdoms$column) {
			w.setWidth(wide);
			w.setX(left);
			if (this.font.width(w.getMessage()) > wide - 4) {
				w.setTooltip(Tooltip.create(w.getMessage()));
			}
		}
		// the shown ones one under another from the top, no gaps where hidden ones sat
		List<AbstractWidget> shown = new ArrayList<>(this.jaymoskingdoms$column.stream().filter(w -> w.visible).toList());
		shown.sort(Comparator.comparingInt(AbstractWidget::getY));
		if (!shown.isEmpty()) {
			int y = shown.get(0).getY();
			for (AbstractWidget w : shown) {
				w.setY(y);
				y += w.getHeight() + 1;
			}
		}
		for (AbstractWidget a : arrows) {
			a.setX(left - PANEL_PAD - ARROW_GAP - a.getWidth());
		}
	}

	/** The panel behind the column, drawn before the buttons. */
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void jaymoskingdoms$panel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
		for (AbstractWidget w : this.jaymoskingdoms$column) {
			if (w.visible) {
				x0 = Math.min(x0, w.getX());
				y0 = Math.min(y0, w.getY());
				x1 = Math.max(x1, w.getRight());
				y1 = Math.max(y1, w.getBottom());
			}
		}
		if (x0 > x1) {
			return;
		}
		x0 -= PANEL_PAD;
		y0 -= PANEL_PAD;
		x1 += PANEL_PAD;
		y1 += PANEL_PAD;
		g.fill(x0, y0, x1, y1, PANEL_FILL);
		g.fillGradient(x0, y0, x0 + 1, y1, BORDER_TOP, BORDER_BOTTOM);
		g.fillGradient(x1 - 1, y0, x1, y1, BORDER_TOP, BORDER_BOTTOM);
		g.fill(x0, y0, x1, y0 + 1, BORDER_TOP);
		g.fill(x0, y1 - 1, x1, y1, BORDER_BOTTOM);
	}
}
