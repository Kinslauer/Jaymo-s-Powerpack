package com.jaymoskingdoms.client;

import com.jaymoskingdoms.MasonNet;
import com.mojang.blaze3d.Blaze3D;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.jaymoskingdoms.client.ui.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.488) The mason's table's screen, in the Kingdoms screens' look: on the left the schematic files in your own
 * {@code schematics} folder (a button opens it, to drop downloaded files in), on the right the designs on the world.
 * Pick one and draw it on a blank building plan (a file of yours is sent up first); offer a world design to the
 * settlement you're in; save what you marked with a surveyor's rod. Below, what the table is building, how far it is,
 * what it waits for and the materials still to lay (the Materials button lists them all, {@link PlanMaterialsScreen}).
 * The server decides everything ({@link MasonNet}). The left list's second tab is the masons' pattern books (the
 * building library): pick one and it's copied among your designs and drawn on a plan, like a file of yours.
 */
final class MasonScreen extends Screen {
	static final int W = 360, PAD = 8, HEADER = 22, ROW = 12, ROWS = 8, MIN_ROWS = 3, BUTTON_H = 16, GAP = 4, ICON = 18;
	/** The masons' accent: a dressed-stone grey-blue. */
	static final int ACCENT = 0xFF8FA8C8;
	/** How often an open screen asks again (ticks), so progress moves. */
	static final int REFRESH_TICKS = 40;

	private MasonNet.Open open;
	private List<String> files;
	/** What's picked: a file of yours (true) or a world design, by name; null for nothing. */
	private @Nullable String picked;
	private boolean pickedFile;
	/** The left list shows the pattern books (else your files); a picked book is {@link #picked} by its id. */
	private boolean books, pickedBook;
	private int fileScroll, designScroll, ticks;
	private Component note;
	private @Nullable EditBox saveName;
	private String typedName = "";

	private MasonScreen(MasonNet.Open open) {
		super(Component.translatable("block.jaymoskingdoms.mason_table"));
		this.open = open;
		this.files = DesignUploadClient.files();
		this.note = open.note();
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MasonNet.Open.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.gui.screen() instanceof MasonScreen screen && screen.open.pos().equals(payload.pos())) {
				screen.update(payload);
			} else {
				mc.gui.setScreen(new MasonScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(MasonNet.Materials.TYPE, (payload, context) -> PlanMaterialsScreen.open(payload));
	}

	private void update(MasonNet.Open payload) {
		this.open = payload;
		this.files = DesignUploadClient.files();
		if (!payload.note().getString().isEmpty()) {
			this.note = payload.note();
		}
		if (this.saveName != null) {
			this.typedName = this.saveName.getValue();
		}
		rebuildWidgets();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (++this.ticks % REFRESH_TICKS == 0) {
			send(MasonNet.REFRESH, "");
			// (files dropped in the folder show up too)
			this.files = DesignUploadClient.files();
		}
	}

	private void send(int action, String name) {
		ClientPlayNetworking.send(new MasonNet.Act(this.open.pos(), action, name));
	}

	// ---- layout ---------------------------------------------------------------------------------------------------

	private int colW() {
		return (W - 2 * PAD - GAP) / 2;
	}

	private int listTop() {
		return top() + HEADER + 12;
	}

	private int listBottom() {
		return listTop() + rows() * ROW;
	}

	/** List rows: as many as fit the screen (at a large GUI scale), at most ROWS. */
	private int rows() {
		return Mth.clamp((this.height - 8 - fixedH()) / ROW, MIN_ROWS, ROWS);
	}

	/** The panel's height without the lists. */
	private int fixedH() {
		return HEADER + 12 + GAP + BUTTON_H + GAP + BUTTON_H + 8 + 12 + 10 + ICON + 4 + BUTTON_H + GAP + 12 + PAD;
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

	/** The first button row (under the lists), the save row, the table's own section. */
	private int actionsY() {
		return listBottom() + GAP;
	}

	private int saveY() {
		return actionsY() + BUTTON_H + GAP;
	}

	private int tableY() {
		return saveY() + BUTTON_H + 8;
	}

	@Override
	protected void init() {
		int x = left() + PAD, cw = colW();
		// the left list's tabs: your files, the pattern books
		Button filesTab = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mason.tab_files"), b -> tab(false)).bounds(x, top() + HEADER - 1, 52, 12)
			.build();
		filesTab.active = this.books;
		addRenderableWidget(filesTab);
		Button booksTab = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mason.tab_books"), b -> tab(true)).bounds(x + 54, top() + HEADER - 1, 74, 12)
			.build();
		booksTab.active = !this.books;
		addRenderableWidget(booksTab);
		if (!this.books) {
			// open the folder, beside the left list's heading
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mason.folder"), b -> Blaze3D.openPath(DesignUploadClient.ensureFolder()))
				.bounds(x + cw - 40, top() + HEADER - 1, 40, 12).build());
		}
		// draw on a plan; offer to the settlement
		Button draw = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mason.draw"), b -> draw()).bounds(x, actionsY(), cw, BUTTON_H).build();
		draw.active = this.picked != null && this.open.blankPlans() > 0;
		addRenderableWidget(draw);
		MasonNet.Design design = pickedDesign();
		Button submit = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mason.submit"), b -> send(MasonNet.SUBMIT, this.picked))
			.bounds(x + cw + GAP, actionsY(), cw, BUTTON_H).build();
		submit.active = design != null && design.crews();
		addRenderableWidget(submit);
		// save what's marked with the rod, under a name
		this.saveName = new EditBox(this.font, x, saveY(), cw, BUTTON_H, Component.translatable("gui.jaymoskingdoms.mason.save_hint"));
		this.saveName.setMaxLength(32);
		this.saveName.setHint(Component.translatable(this.open.marked() ? "gui.jaymoskingdoms.mason.save_hint" : "gui.jaymoskingdoms.mason.save_unmarked"));
		this.saveName.setValue(this.typedName);
		this.saveName.active = this.open.marked();
		this.saveName.setEditable(this.open.marked());
		addRenderableWidget(this.saveName);
		Button save = FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mason.save"), b -> {
			String n = this.saveName.getValue().strip();
			if (!n.isEmpty()) {
				send(MasonNet.SAVE, n);
			}
		}).bounds(x + cw + GAP, saveY(), cw, BUTTON_H).build();
		save.active = this.open.marked();
		addRenderableWidget(save);
		// the table's plan
		int by = top() + panelH() - PAD - 12 - GAP - BUTTON_H;
		if (!this.open.plan().isEmpty()) {
			boolean retry = this.open.done() && this.open.skipped() > 0;
			// (three to the row when there's a retry)
			int bw = retry ? (W - 2 * PAD - 2 * GAP) / 3 : cw;
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mason.take"), b -> send(MasonNet.TAKE, "")).bounds(x, by, bw, BUTTON_H)
				.build());
			// every block still needed, against what you carry
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mason.materials"), b -> send(MasonNet.MATERIALS, ""))
				.bounds(x + bw + GAP, by, bw, BUTTON_H).build());
			if (retry) {
				addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.mason.retry", this.open.skipped()), b -> send(MasonNet.RETRY, ""))
					.bounds(x + 2 * (bw + GAP), by, bw, BUTTON_H).build());
			}
		}
	}

	private void tab(boolean books) {
		this.books = books;
		this.fileScroll = 0;
		if (this.saveName != null) {
			this.typedName = this.saveName.getValue();
		}
		rebuildWidgets();
	}

	/** The rows of the left list: your files, or the pattern books. */
	private int leftSize() {
		return this.books ? this.open.books().size() : this.files.size();
	}

	private MasonNet.@Nullable Design pickedDesign() {
		if (this.picked == null || this.pickedFile || this.pickedBook) {
			return null;
		}
		for (MasonNet.Design d : this.open.designs()) {
			if (d.name().equals(this.picked)) {
				return d;
			}
		}
		return null;
	}

	private void draw() {
		if (this.picked == null) {
			return;
		}
		if (this.pickedBook) {
			send(MasonNet.COPY, this.picked);
		} else if (this.pickedFile) {
			Component problem = DesignUploadClient.upload(this.picked, this.open.pos());
			this.note = problem != null ? problem.copy().withColor(Ui.BAD) : Component.translatable("gui.jaymoskingdoms.mason.sending", this.picked);
		} else {
			send(MasonNet.DRAW, this.picked);
		}
	}

	// ---- picking --------------------------------------------------------------------------------------------------

	/** The row under the mouse in a list: {0 files | 1 designs, index}, or null. */
	private int @Nullable [] rowAt(double mx, double my) {
		int x = left() + PAD, cw = colW(), top = listTop();
		if (my < top || my >= listBottom()) {
			return null;
		}
		int row = (int)((my - top) / ROW);
		if (mx >= x && mx < x + cw) {
			int i = this.fileScroll + row;
			return i < leftSize() ? new int[] {0, i} : null;
		}
		if (mx >= x + cw + GAP && mx < x + 2 * cw + GAP) {
			int i = this.designScroll + row;
			return i < this.open.designs().size() ? new int[] {1, i} : null;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int[] row = rowAt(event.x(), event.y());
		if (row != null) {
			this.pickedBook = row[0] == 0 && this.books;
			this.pickedFile = row[0] == 0 && !this.books;
			if (this.pickedBook) {
				MasonNet.Book b = this.open.books().get(row[1]);
				this.picked = b.id();
				// who drew it, its land and size, at the bottom
				this.note = Component.translatable(b.builders() ? "gui.jaymoskingdoms.mason.book_builders" : "gui.jaymoskingdoms.mason.book", b.name(),
					b.author().isEmpty() ? "?" : b.author(), b.land().isEmpty() ? "-" : b.land().toLowerCase(java.util.Locale.ROOT), b.w() + "x" + b.d() + "x" + b.h());
			} else {
				this.picked = this.pickedFile ? this.files.get(row[1]) : this.open.designs().get(row[1]).name();
			}
			if (this.saveName != null) {
				this.typedName = this.saveName.getValue();
			}
			rebuildWidgets();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int x = left() + PAD, cw = colW();
		int step = -(int)Math.signum(scrollY);
		if (mx < x + cw) {
			this.fileScroll = Mth.clamp(this.fileScroll + step, 0, Math.max(0, leftSize() - rows()));
		} else {
			this.designScroll = Mth.clamp(this.designScroll + step, 0, Math.max(0, this.open.designs().size() - rows()));
		}
		return true;
	}

	// ---- drawing --------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x0 = left(), y0 = top(), x = x0 + PAD, cw = colW();
		Ui.panel(graphics, x0, y0, W, panelH(), ACCENT);
		Ui.line(graphics, this.font, this.title, x, y0 + 8, W / 2, ACCENT);
		Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.mason.plans", this.open.blankPlans()), x0 + W - PAD, y0 + 7, W / 2,
			this.open.blankPlans() > 0 ? Ui.DIM : Ui.BAD);
		// the two lists
		graphics.text(this.font, Component.translatable("gui.jaymoskingdoms.mason.designs"), x + cw + GAP, y0 + HEADER + 1, Ui.DIM);
		int[] hover = rowAt(mouseX, mouseY);
		int top = listTop();
		Ui.well(graphics, x - 1, top - 1, cw + 2, rows() * ROW + 2);
		Ui.well(graphics, x + cw + GAP - 1, top - 1, cw + 2, rows() * ROW + 2);
		if (this.books) {
			List<MasonNet.Book> books = this.open.books();
			if (books.isEmpty()) {
				Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.mason.no_books"), x + 3, top + 2, cw - 6, Ui.HINT);
			}
			for (int i = this.fileScroll; i < Math.min(books.size(), this.fileScroll + rows()); i++) {
				int ry = top + (i - this.fileScroll) * ROW;
				MasonNet.Book b = books.get(i);
				boolean on = this.pickedBook && b.id().equals(this.picked);
				graphics.fill(x, ry, x + cw, ry + ROW - 1, on ? Ui.ROW_PICKED : hover != null && hover[0] == 0 && hover[1] == i ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW
					: Ui.ROW_ALT);
				Component size = Component.literal(b.w() + "x" + b.d() + "x" + b.h());
				int sw = this.font.width(size);
				graphics.text(this.font, size, x + cw - 3 - sw, ry + 2, Ui.DIM);
				Ui.line(graphics, this.font, Component.literal(b.name()), x + 3, ry + 2, cw - 10 - sw, on ? ACCENT : b.builders() ? Ui.TEXT : Ui.DIM);
			}
		} else if (this.files.isEmpty()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.mason.no_files"), x + 3, top + 2, cw - 6, Ui.HINT);
		}
		for (int i = this.fileScroll; !this.books && i < Math.min(this.files.size(), this.fileScroll + rows()); i++) {
			int ry = top + (i - this.fileScroll) * ROW;
			String f = this.files.get(i);
			boolean on = this.pickedFile && f.equals(this.picked);
			graphics.fill(x, ry, x + cw, ry + ROW - 1, on ? Ui.ROW_PICKED : hover != null && hover[0] == 0 && hover[1] == i ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW
				: Ui.ROW_ALT);
			Ui.line(graphics, this.font, Component.literal(f), x + 3, ry + 2, cw - 6, on ? ACCENT : Ui.TEXT);
		}
		List<MasonNet.Design> designs = this.open.designs();
		int dx = x + cw + GAP;
		if (designs.isEmpty()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.mason.no_designs"), dx + 3, top + 2, cw - 6, Ui.HINT);
		}
		for (int i = this.designScroll; i < Math.min(designs.size(), this.designScroll + rows()); i++) {
			int ry = top + (i - this.designScroll) * ROW;
			MasonNet.Design d = designs.get(i);
			boolean on = !this.pickedFile && !this.pickedBook && d.name().equals(this.picked);
			graphics.fill(dx, ry, dx + cw, ry + ROW - 1, on ? Ui.ROW_PICKED : hover != null && hover[0] == 1 && hover[1] == i ? Ui.ROW_HOVER : i % 2 == 0 ? Ui.ROW
				: Ui.ROW_ALT);
			Component size = Component.literal(d.w() + "x" + d.d() + "x" + d.h());
			int sw = this.font.width(size);
			graphics.text(this.font, size, dx + cw - 3 - sw, ry + 2, Ui.DIM);
			Ui.line(graphics, this.font, Component.literal(d.name()), dx + 3, ry + 2, cw - 10 - sw, on ? ACCENT : Ui.TEXT);
		}
		Ui.scrollbar(graphics, x + cw - 2, top, rows() * ROW, rows(), leftSize(), this.fileScroll);
		Ui.scrollbar(graphics, dx + cw - 2, top, rows() * ROW, rows(), designs.size(), this.designScroll);
		// the table's own work
		int ty = tableY();
		Ui.rule(graphics, x0 + 6, x0 + W - 6, ty - 5);
		if (this.open.plan().isEmpty()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.mason.idle"), x, ty, W - 2 * PAD, Ui.DIM);
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.mason.idle_how"), x, ty + 11, W - 2 * PAD, Ui.HINT);
		} else {
			Component head = this.open.done() ? Component.translatable("gui.jaymoskingdoms.mason.finished", this.open.plan())
				: Component.translatable("gui.jaymoskingdoms.mason.building", this.open.plan(), this.open.laid(), this.open.total());
			Ui.line(graphics, this.font, head, x, ty, W - 2 * PAD, this.open.done() ? Ui.GOOD : ACCENT);
			Ui.bar(graphics, x, ty + 10, W - 2 * PAD, 3, this.open.total() == 0 ? 0.0F : this.open.laid() / (float)this.open.total(), Ui.GOOD);
		}
		int iy = ty + 16;
		ItemStack tip = ItemStack.EMPTY;
		if (!this.open.waiting().isEmpty()) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.mason.waiting", this.open.waiting().getHoverName()), x, iy + 4, 130, Ui.BAD);
		}
		int ix = x + (this.open.waiting().isEmpty() ? 0 : 134);
		for (MasonNet.Need n : this.open.needs()) {
			if (ix + ICON > x0 + W - PAD) {
				break;
			}
			Ui.well(graphics, ix - 1, iy - 1, ICON, ICON);
			graphics.item(n.item(), ix, iy);
			String count = n.count() > 999 ? (n.count() / 1000) + "k" : Integer.toString(n.count());
			graphics.text(this.font, Component.literal(count), ix + 17 - this.font.width(count), iy + 9, Ui.TEXT);
			if (Ui.over(mouseX, mouseY, ix, iy, 16, 16)) {
				tip = n.item();
			}
			ix += ICON + 6;
		}
		// the last answer
		Ui.line(graphics, this.font, this.note, x, y0 + panelH() - PAD - 10, W - 2 * PAD, Ui.TEXT);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (!tip.isEmpty()) {
			graphics.setTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}
}
