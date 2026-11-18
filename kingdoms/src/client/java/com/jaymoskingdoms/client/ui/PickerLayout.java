package com.jaymoskingdoms.client.ui;

import com.jaymoskingdoms.client.Ui;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The picker screens' shared layout (heritage, class, patrons, the life story's questions), in three columns across the whole
 * screen: the figure of you on the LEFT (with a button or two under it), a compact list of the choices in the middle (one or
 * two columns of icon, name and a one-line tag; the rows shrink and the list scrolls when there are many), and a big info
 * panel on the right (icon, name, tag, pills, the story, then labelled sections and the paths the choice grows into; it
 * scrolls with the wheel and a thin bar). Small windows get one list column and a narrower figure. Text is never cut: a long
 * line slides ({@link Ui#line}), a full panel scrolls.
 *
 * <p>A screen owns one of these, calls {@link #fit} when it sets up and every frame before drawing, then {@link #header},
 * {@link #backdrop}, draws the figure in {@link #figureBox}, {@link #caption}, {@link #list} and {@link #info}.
 */
public final class PickerLayout {
	/** Outer margin, the gap between columns, the title strip's height, and the least a list column or an info panel may be. */
	public static final int MARGIN = 8, GAP = 6, TITLE_H = 28, MIN_COL = 100, MIN_INFO = 130;
	/** A list row's tallest and shortest height, and the height at which a row has room for its tag under its name. */
	public static final int ROW_MAX = 24, ROW_MIN = 13, ROW_TAGGED = 21;
	/** What one notch of the wheel moves the info panel (pixels). */
	public static final int SCROLL_STEP = 12;
	/** The height of a button under the figure, and the gap between two. */
	public static final int BUTTON_H = 16, BUTTON_GAP = 3;

	/** Paints a picture that isn't an item (a faith's glyph) with its top-left at x, y. */
	@FunctionalInterface
	public interface Painter {
		void paint(GuiGraphicsExtractor graphics, int x, int y);
	}

	/** One choice in the list: its picture, name, tag and the colour of its rim; {@code locked} when it can't be picked (dim). */
	public record Entry(ItemStack icon, @Nullable Painter glyph, Component name, @Nullable Component tag, int rim, boolean locked) {
	}

	/** A small tag under the header: label dim, value in the colour. */
	public record Pill(String label, String value, int color) {
	}

	/** A labelled block of the info panel; a line with a colour of its own keeps it, the rest are plain. */
	public record Section(Component label, int color, List<Component> lines) {
	}

	/** One path: a row of small pictures and its name; {@code id} 0 or more makes it pressable (a pick the screen reads back from {@link #pathAt}). */
	public record Path(List<ItemStack> icons, Component name, int id) {
	}

	/** Everything the info panel shows. */
	public record Info(ItemStack icon, @Nullable Painter glyph, Component name, @Nullable Component tag, int accent, List<Pill> pills, List<Component> story,
		List<Section> sections, @Nullable Component pathsNote, List<Path> paths, int pickedPath) {
	}

	// ---- geometry (set by fit) -------------------------------------------------------------------------------------

	public int width, height;
	/** The figure column, the list and the info panel: x and width of each, and the body's top and bottom. */
	public int figX, figW, listX, listW, infoX, infoW, bodyTop, bodyBottom;
	public int cols = 1;
	/**
	 * A row's tallest height. The life story raises it ({@link #FILL_ROW_MAX}) so its few answers fill the middle column as big
	 * boxes, with a bigger icon box and the name and tag wrapped in full (Andy: "there shouldnt be so much unused space").
	 */
	public int rowMax = ROW_MAX;
	/** The tallest a row may grow when a screen lets the list fill its column, and the biggest its icon box gets. */
	public static final int FILL_ROW_MAX = 80, FILL_BOX_MAX = 40;
	private int buttons, rowH, rows;
	private int listScroll;
	private int infoScroll, infoContentH, infoViewH;
	private @Nullable Object infoKey;
	/** Where the pressable path rows were drawn last: x, y, w, h, id; and the view they scroll in. */
	private final List<int[]> pathRects = new java.util.ArrayList<>();
	private int viewTop, viewBottom, hoverX = -1, hoverY = -1;

	/**
	 * Lays the columns out for a window {@code w} by {@code h}, with {@code top} pixels reserved above the title (a menu bar) and
	 * {@code buttons} buttons stacked under the figure.
	 */
	public void fit(int w, int h, int top, int buttons) {
		this.width = w;
		this.height = h;
		this.buttons = buttons;
		int total = w - 2 * MARGIN;
		boolean small = w < 400;
		int fig = Math.clamp((int)(total * 0.2F), small ? 70 : 96, 150);
		int info = Math.clamp((int)(total * 0.4F), MIN_INFO, 380);
		int list = total - fig - info - 2 * GAP;
		if (list < MIN_COL) {
			int need = MIN_COL - list;
			int giveInfo = Math.min(need, info - MIN_INFO);
			info -= giveInfo;
			need -= giveInfo;
			int giveFig = Math.min(need, Math.max(0, fig - 60));
			fig -= giveFig;
			list = total - fig - info - 2 * GAP;
		}
		this.cols = list >= 2 * MIN_COL + GAP ? 2 : 1;
		this.figX = MARGIN;
		this.figW = fig;
		this.listX = this.figX + fig + GAP;
		this.listW = list;
		this.infoX = this.listX + list + GAP;
		this.infoW = info;
		this.bodyTop = top + TITLE_H;
		this.bodyBottom = h - MARGIN;
	}

	/** The title and the line under it, centred over the whole screen. */
	public void header(GuiGraphicsExtractor graphics, Font font, Component title, @Nullable Component sub, int accent) {
		header(graphics, font, title, sub, accent, Ui.DIM);
	}

	/** The same with the line under the title in {@code subColor}. */
	public void header(GuiGraphicsExtractor graphics, Font font, Component title, @Nullable Component sub, int accent, int subColor) {
		int room = this.width - 2 * MARGIN;
		Ui.lineCentred(graphics, font, title, this.width / 2, this.bodyTop - TITLE_H + 5, room, accent);
		if (sub != null) {
			Ui.lineCentred(graphics, font, sub, this.width / 2, this.bodyTop - TITLE_H + 16, room, subColor);
		}
	}

	// ---- the figure column ---------------------------------------------------------------------------------------------

	/** Where a button under the figure sits: {@code index} 0 is the lowest. {x, y, w, h}. */
	public int[] button(int index) {
		return new int[] {this.figX + 6, this.bodyBottom - 6 - BUTTON_H - index * (BUTTON_H + BUTTON_GAP), this.figW - 12, BUTTON_H};
	}

	/** The box the figure is drawn in: {x0, y0, x1, y1}, above the caption and the buttons. */
	public int[] figureBox() {
		int bottom = this.bodyBottom - 6 - this.buttons * (BUTTON_H + BUTTON_GAP) - 24;
		return new int[] {this.figX + 4, this.bodyTop + 6, this.figX + this.figW - 4, Math.max(this.bodyTop + 40, bottom)};
	}

	/** The framed figure column with a soft glow behind the figure and a shadow under its feet; draw the figure after this. */
	public void backdrop(GuiGraphicsExtractor graphics, int glow, float figureScale) {
		Ui.panelFramed(graphics, this.figX, this.bodyTop, this.figW, this.bodyBottom - this.bodyTop, glow);
		int[] box = figureBox();
		int cx = (box[0] + box[2]) / 2, h = box[3] - box[1];
		int r = Math.min((box[2] - box[0]) / 2, (int)(h * 0.46F));
		int cy = box[1] + (int)(h * 0.48F);
		int rgb = glow & 0x00FFFFFF;
		int rings = 7;
		for (int k = 0; k < rings; k++) {
			int rr = r - r * k / rings;
			for (int dy = -rr; dy <= rr; dy++) {
				int half = (int)Math.sqrt((double)rr * rr - (double)dy * dy);
				graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0x0C000000 | rgb);
			}
		}
		// the feet are where StoryFigure puts them ({@code figureScale}: the figure's size as a fraction of its box's height)
		int feet = (box[1] + box[3]) / 2 + (int)(h * figureScale * 0.9625F);
		int a = Math.max(8, (box[2] - box[0]) / 4);
		for (int k = 0; k < 2; k++) {
			int aa = a - k * a / 3, b = 3 - k;
			for (int dy = -b; dy <= b; dy++) {
				int half = (int)(aa * Math.sqrt(1.0 - (double)dy * dy / ((b + 1.0) * (b + 1.0))));
				graphics.fill(cx - half, feet + dy, cx + half, feet + dy + 1, 0x50000000);
			}
		}
	}

	/** Under the figure: who you'd be (centred, sliding when long) and, under it, a dim line. */
	public void caption(GuiGraphicsExtractor graphics, Font font, @Nullable Component line, @Nullable Component dim) {
		int box = figureBox()[3];
		if (line != null) {
			Ui.lineCentred(graphics, font, line, this.figX + this.figW / 2, box + 4, this.figW - 12, Ui.TEXT);
		}
		if (dim != null) {
			Ui.lineCentred(graphics, font, dim, this.figX + this.figW / 2, box + 15, this.figW - 12, Ui.HINT);
		}
	}

	// ---- the list ----------------------------------------------------------------------------------------------------

	private void rowsFor(int n) {
		this.rows = (n + this.cols - 1) / this.cols;
		int avail = this.bodyBottom - this.bodyTop;
		this.rowH = Math.clamp(avail / Math.max(1, this.rows), ROW_MIN, Math.max(ROW_MIN, this.rowMax));
		this.listScroll = Math.clamp(this.listScroll, 0, Math.max(0, this.rows - visibleRows()));
	}

	private int visibleRows() {
		return Math.max(1, (this.bodyBottom - this.bodyTop) / this.rowH);
	}

	private int colW() {
		int bar = this.rows > visibleRows() ? 5 : 0;
		return (this.listW - bar - (this.cols - 1) * 3) / this.cols;
	}

	/** The index of the entry at the mouse among {@code n} entries, or -1. */
	public int rowAt(int n, int mouseX, int mouseY) {
		rowsFor(n);
		int cw = colW();
		for (int i = 0; i < n; i++) {
			int r = i / this.cols - this.listScroll;
			if (r < 0 || r >= visibleRows()) {
				continue;
			}
			int x = this.listX + i % this.cols * (cw + 3), y = this.bodyTop + r * this.rowH;
			if (Ui.over(mouseX, mouseY, x, y, cw, this.rowH - 2)) {
				return i;
			}
		}
		return -1;
	}

	/** The rows: icon in a rim of its colour, name, a one-line tag when the row is tall enough; the picked one has a gold rim. */
	public void list(GuiGraphicsExtractor graphics, Font font, List<Entry> entries, int picked, int hover, int accent) {
		list(graphics, font, entries, picked, hover, accent, false);
	}

	/** The same; with {@code crossed} the picked row's box is also struck through with a hand-drawn X ({@link #cross}). */
	public void list(GuiGraphicsExtractor graphics, Font font, List<Entry> entries, int picked, int hover, int accent, boolean crossed) {
		int n = entries.size();
		rowsFor(n);
		int cw = colW(), vis = visibleRows();
		boolean tagged = this.rowH >= ROW_TAGGED;
		for (int i = 0; i < n; i++) {
			int r = i / this.cols - this.listScroll;
			if (r < 0 || r >= vis) {
				continue;
			}
			Entry e = entries.get(i);
			int x = this.listX + i % this.cols * (cw + 3), y = this.bodyTop + r * this.rowH, h = this.rowH - 2;
			boolean isPicked = i == picked, isHover = i == hover && !e.locked();
			graphics.fill(x, y, x + cw, y + h, isPicked ? Ui.ROW_PICKED : isHover ? Ui.ROW_HOVER : Ui.ROW_ALT);
			if (isPicked) {
				rim(graphics, x, y, cw, h, accent);
			} else {
				rim(graphics, x, y, cw, h, 0x30FFFFFF);
			}
			boolean big = h > ROW_MAX;
			int box = big ? Math.min(h - 8, FILL_BOX_MAX) : Math.min(h - 4, 18);
			int bx = x + (big ? 5 : 3), by = y + (h - box) / 2;
			rim(graphics, bx, by, box, box, e.locked() ? 0x40FFFFFF : e.rim());
			if (!e.locked() && (e.glyph() != null || !e.icon().isEmpty())) {
				// in a big box the picture grows with it
				float k = big ? Math.max(1.0F, (box - 6) / 16.0F) : 1.0F;
				graphics.pose().pushMatrix();
				graphics.pose().translate(bx + (box - 16 * k) / 2.0F, by + (box - 16 * k) / 2.0F);
				graphics.pose().scale(k, k);
				if (e.glyph() != null) {
					e.glyph().paint(graphics, 0, 0);
				} else {
					graphics.item(e.icon(), 0, 0);
				}
				graphics.pose().popMatrix();
			}
			if (crossed && isPicked) {
				cross(graphics, bx, by, box);
			}
			int tx = bx + box + (big ? 7 : 4), room = x + cw - (big ? 6 : 3) - tx;
			int nameColor = e.locked() ? Ui.HINT : isPicked ? accent : Ui.TEXT;
			int tagColor = e.locked() ? 0xFF50505C : Ui.DIM;
			List<FormattedCharSequence> nameLines = big ? font.split(e.name(), room) : List.of();
			List<FormattedCharSequence> tagLines = big && e.tag() != null ? font.split(e.tag(), room) : List.of();
			int textH = (nameLines.size() + tagLines.size()) * Ui.LINE + (tagLines.isEmpty() ? 0 : 2);
			if (big && textH <= h - 6) {
				// a big row: the name and the tag wrapped in full, centred in the row's height
				int ty = y + (h - textH) / 2 + 1;
				for (FormattedCharSequence l : nameLines) {
					graphics.text(font, l, tx, ty, nameColor);
					ty += Ui.LINE;
				}
				ty += 2;
				for (FormattedCharSequence l : tagLines) {
					graphics.text(font, l, tx, ty, tagColor);
					ty += Ui.LINE;
				}
			} else if (tagged && e.tag() != null) {
				Ui.line(graphics, font, e.name(), tx, y + 4, room, nameColor);
				Ui.line(graphics, font, e.tag(), tx, y + 4 + Ui.LINE + 1, room, tagColor);
			} else {
				Ui.line(graphics, font, e.name(), tx, y + (h - 8) / 2 + 1, room, nameColor);
			}
		}
		if (this.rows > vis) {
			Ui.scrollbar(graphics, this.listX + this.listW - 3, this.bodyTop, vis * this.rowH - 2, vis, this.rows, this.listScroll);
		}
	}

	/** The hand-drawn X's ink: a quill's red, with a darker edge under each stroke. */
	public static final int CROSS_INK = 0xFFD8322A, CROSS_EDGE = 0xFF5A0E0A;

	/**
	 * Andy's mark for a picked box: an X struck across the square {@code size} box at x, y as if by hand, two slightly wavering
	 * strokes, the second running a little past the box's corner. The waver is fixed, so the mark doesn't shimmer.
	 */
	public static void cross(GuiGraphicsExtractor graphics, int x, int y, int size) {
		int over = Math.max(1, size / 6);
		stroke(graphics, x + 1, y, x + size - 1 + over / 2, y + size - 1 + over / 2, size, 0.0);
		stroke(graphics, x + size - 1, y - over / 2, x - over, y + size - 1 + over, size, 1.7);
	}

	/** One stroke of the X, from (x0, y0) to (x1, y1), wavering up to a pixel off its line in bigger boxes. */
	private static void stroke(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, double phase) {
		double dx = x1 - x0, dy = y1 - y0, len = Math.sqrt(dx * dx + dy * dy);
		int steps = Math.max(1, (int)Math.ceil(len * 2));
		double wave = size >= 12 ? 1.0 : 0.0;
		// a big box gets a broader nib
		int pen = size >= 24 ? 2 : 1;
		int lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;
		for (int i = 0; i <= steps; i++) {
			double t = (double)i / steps, off = wave * Math.sin(t * Math.PI * 1.6 + phase) * Math.sin(t * Math.PI);
			int px = (int)Math.round(x0 + dx * t - dy / len * off), py = (int)Math.round(y0 + dy * t + dx / len * off);
			if (px != lastX || py != lastY) {
				graphics.fill(px, py + pen, px + pen, py + 2 * pen, CROSS_EDGE);
				graphics.fill(px, py, px + pen, py + pen, CROSS_INK);
				lastX = px;
				lastY = py;
			}
		}
	}

	private static void rim(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y + 1, x + 1, y + h - 1, color);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	// ---- the info panel ----------------------------------------------------------------------------------------------

	/**
	 * The info panel for {@code info}. It starts at the top again whenever {@code key} (what it is about) changes.
	 */
	public void info(GuiGraphicsExtractor graphics, Font font, Info info, Object key, int mouseX, int mouseY) {
		this.hoverX = mouseX;
		this.hoverY = mouseY;
		if (!Objects.equals(key, this.infoKey)) {
			this.infoKey = key;
			this.infoScroll = 0;
		}
		int x = this.infoX, y = this.bodyTop, w = this.infoW, h = this.bodyBottom - this.bodyTop;
		Ui.panelFramed(graphics, x, y, w, h, info.accent());
		int tx = x + 10;
		boolean icon = info.glyph() != null || !info.icon().isEmpty();
		if (icon) {
			Ui.well(graphics, x + 8, y + 9, 26, 26);
			if (info.glyph() != null) {
				info.glyph().paint(graphics, x + 13, y + 14);
			} else {
				graphics.item(info.icon(), x + 13, y + 14);
			}
			tx = x + 42;
		}
		int room = x + w - 12 - tx;
		Ui.line(graphics, font, info.name(), tx, y + 11, room, info.accent());
		if (info.tag() != null) {
			Ui.line(graphics, font, info.tag(), tx, y + 22, room, Ui.DIM);
		}
		// the pills, wrapped
		int py = y + (info.tag() != null ? 34 : 24), px = tx;
		for (Pill p : info.pills()) {
			int pw = Ui.pillWidth(font, p.label(), p.value());
			if (px + pw > x + w - 10 && px > tx) {
				px = tx;
				py += Ui.PILL_H + 2;
			}
			Ui.pill(graphics, font, px, py, p.label(), p.value(), p.color());
			px += pw + 4;
		}
		int headEnd = Math.max(y + 40, py + (info.pills().isEmpty() ? 0 : Ui.PILL_H) + 4);
		Ui.rule(graphics, x + 8, x + w - 8, headEnd);
		int viewTop = headEnd + 4, viewBottom = y + h - 6;
		this.infoViewH = viewBottom - viewTop;
		this.viewTop = viewTop;
		this.viewBottom = viewBottom;
		this.pathRects.clear();
		this.infoContentH = body(graphics, font, info, x + 10, viewTop, w - 24, viewTop, viewBottom, false);
		this.infoScroll = Math.clamp(this.infoScroll, 0, Math.max(0, this.infoContentH - this.infoViewH));
		graphics.enableScissor(x + 1, viewTop, x + w - 1, viewBottom);
		body(graphics, font, info, x + 10, viewTop - this.infoScroll, w - 24, viewTop, viewBottom, true);
		graphics.disableScissor();
		Ui.scrollbar(graphics, x + w - 6, viewTop, this.infoViewH, this.infoViewH, this.infoContentH, this.infoScroll);
	}

	/** Lays the body out from {@code y}; draws it when {@code draw}. Returns its height. */
	private int body(GuiGraphicsExtractor graphics, Font font, Info info, int x, int y, int w, int clipTop, int clipBottom, boolean draw) {
		int start = y;
		for (Component para : info.story()) {
			y = paragraph(graphics, font, para, x, y, w, Ui.TEXT, clipTop, clipBottom, draw) + 3;
		}
		for (Section s : info.sections()) {
			if (s.lines().isEmpty()) {
				continue;
			}
			y += 5;
			if (draw && y + Ui.LINE > clipTop && y < clipBottom) {
				Ui.line(graphics, font, Component.literal(s.label().getString().toUpperCase(Locale.ROOT)), x, y, w, s.color());
			}
			y += Ui.LINE + 2;
			for (Component line : s.lines()) {
				int color = line.getStyle().getColor() != null ? 0xFF000000 | line.getStyle().getColor().getValue() : Ui.TEXT;
				y = paragraph(graphics, font, line, x, y, w, color, clipTop, clipBottom, draw) + 2;
			}
		}
		if (!info.paths().isEmpty()) {
			y += 5;
			if (draw && y + Ui.LINE > clipTop && y < clipBottom) {
				Ui.line(graphics, font, Component.literal(Component.translatable("gui.jaymoskingdoms.picker.paths").getString().toUpperCase(Locale.ROOT)), x, y, w, Ui.DIM);
			}
			y += Ui.LINE + 2;
			if (info.pathsNote() != null) {
				y = paragraph(graphics, font, info.pathsNote(), x, y, w, Ui.HINT, clipTop, clipBottom, draw) + 3;
			}
			for (Path p : info.paths()) {
				int n = Math.min(p.icons().size(), Math.max(1, (w - 60) / 22));
				if (draw && p.id() >= 0) {
					this.pathRects.add(new int[] {x - 3, y, w + 6, 22, p.id()});
				}
				if (draw && y + 20 > clipTop && y < clipBottom) {
					if (p.id() >= 0) {
						boolean on = p.id() == info.pickedPath(), over = !on && Ui.over(this.hoverX, this.hoverY, x - 3, y, w + 6, 22)
							&& this.hoverY >= clipTop && this.hoverY < clipBottom;
						if (on || over) {
							graphics.fill(x - 3, y, x + w + 3, y + 21, on ? Ui.ROW_PICKED : Ui.ROW_HOVER);
						}
						if (on) {
							graphics.fill(x - 3, y, x - 1, y + 21, info.accent());
						}
					}
					for (int i = 0; i < n; i++) {
						Ui.itemWell(graphics, font, p.icons().get(i), x + 1 + i * 22, y + 2);
					}
					int nx = x + n * 22 + 6;
					Ui.line(graphics, font, p.name(), nx, y + 7, x + w - nx, Ui.TEXT);
				}
				y += 22;
			}
		}
		return y - start;
	}

	private static int paragraph(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int w, int color, int clipTop, int clipBottom,
		boolean draw) {
		for (FormattedCharSequence seq : font.split(text, w)) {
			if (draw && y + Ui.LINE > clipTop && y < clipBottom) {
				graphics.text(font, seq, x, y, color);
			}
			y += Ui.LINE;
		}
		return y;
	}

	/** The id of the pressable path row at the mouse, or -1. */
	public int pathAt(int mouseX, int mouseY) {
		if (mouseY < this.viewTop || mouseY >= this.viewBottom) {
			return -1;
		}
		for (int[] r : this.pathRects) {
			if (Ui.over(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
				return r[4];
			}
		}
		return -1;
	}

	// ---- the wheel -----------------------------------------------------------------------------------------------------

	/** A wheel step in pixels, at least one in its direction (a trackpad's small steps would round to nothing). */
	private static int wheel(double pixels) {
		int step = (int)Math.round(pixels);
		return step == 0 ? (int)Math.signum(pixels) : step;
	}

	/** Turns the wheel over the list or the info panel; true if it did something. {@code n} is how many entries the list has. */
	public boolean scrolled(double mouseX, double mouseY, double scrollY, int n) {
		if (scrollY == 0.0) {
			return false;
		}
		if (mouseX >= this.infoX) {
			if (this.infoContentH > this.infoViewH) {
				this.infoScroll = Math.clamp(this.infoScroll - wheel(scrollY * SCROLL_STEP), 0, this.infoContentH - this.infoViewH);
			}
			return true;
		}
		if (mouseX >= this.listX && mouseX < this.listX + this.listW) {
			rowsFor(n);
			this.listScroll = Math.clamp(this.listScroll - (int)Math.signum(scrollY), 0, Math.max(0, this.rows - visibleRows()));
			return true;
		}
		return false;
	}

	/** Scrolls the list so the entry {@code index} shows (the screen opened on a choice far down it). */
	public void reveal(int n, int index) {
		rowsFor(n);
		int row = index / this.cols;
		if (row < this.listScroll) {
			this.listScroll = row;
		} else if (row >= this.listScroll + visibleRows()) {
			this.listScroll = row - visibleRows() + 1;
		}
	}

	/** The one-line tag at lang key {@code key}, or null if the language has none. */
	public static @Nullable Component tag(String key) {
		return Language.getInstance().has(key) ? Component.translatable(key) : null;
	}

	/** {@code name} with its article: "a Stoneborn", "an Earthling". */
	public static String withArticle(String name) {
		return (!name.isEmpty() && "AEIOUaeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
	}
}
