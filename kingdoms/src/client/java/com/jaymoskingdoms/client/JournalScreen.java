package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FactionType;
import com.jaymoskingdoms.Journal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * (0.5.49) The traveller's journal ({@link Journal}): three pages. <b>Quests</b>: each quest you carry, who gave it, its
 * story, what's left and which way it lies. <b>Map</b>: the land about you as a map draws it (only what your eyes have
 * loaded; the rest is blank parchment), with you, your quests' places and the settlements on it, and zoom. <b>Reputation</b>:
 * what each settlement you know thinks of you. (0.7.38) Restyled like the H screen: a header, striped rows that light up
 * under the mouse, and on the quests page a box at the bottom with the hovered (or pressed) quest in full.
 */
final class JournalScreen extends Screen {
	static final int PANEL_W = 360, PANEL_H = 230, HEADER = 34, TAB_H = 16, BODY_Y = 56, FOOTER = 22, QUEST_ROW = 16, REP_ROW = 22, DETAIL_LINES = 6;
	/** The map: its size on screen (px), the size of one drawn cell (px), and the zooms (blocks per cell). */
	static final int MAP_SIZE = 150, CELL = 2;
	static final int[] ZOOMS = {1, 2, 4, 8, 16, 32};
	static final int DEFAULT_ZOOM = 2;
	static final int COLOR_TEXT = Ui.TEXT, COLOR_DIM = Ui.DIM, COLOR_ROYAL = 0xFFFF6A50, COLOR_GOLD = Ui.GOLD, COLOR_DONE = 0xFF66EE66,
		COLOR_PARCHMENT = 0xFFD8C8A0, COLOR_HATED = 0xFFE04040;
	static final int QUESTS = 0, MAP = 1, REPUTATION = 2;

	private static int lastPage = QUESTS, lastZoom = DEFAULT_ZOOM;

	private final Journal.Book book;
	private final KeyMapping key;
	private int page = lastPage, zoom = lastZoom, scroll;
	/** The quest last pressed, shown in the box when none is pointed at. */
	private int selected;
	/** The drawn map (ARGB per cell, row by row), its centre when drawn and the zoom it was drawn at. */
	private int[] cells;
	private int drawnX, drawnZ, drawnZoom = -1;

	JournalScreen(Journal.Book book, KeyMapping key) {
		super(Component.translatable("item.jaymoskingdoms.journal"));
		this.book = book;
		this.key = key;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelW() {
		return Math.min(PANEL_W, this.width - 12);
	}

	private int left() {
		return (this.width - panelW()) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	private int detailY() {
		return top() + PANEL_H - FOOTER - (10 + DETAIL_LINES * Ui.LINE + 2);
	}

	private int questRows() {
		return Math.max(1, (detailY() - 4 - top() - BODY_Y) / QUEST_ROW);
	}

	private int repRows() {
		return Math.max(1, (PANEL_H - FOOTER - 4 - BODY_Y) / REP_ROW);
	}

	@Override
	protected void init() {
		String[] tabs = {"quests", "map", "reputation"};
		int w = 92;
		int x = left() + (panelW() - w * 3 - 8) / 2, y = top() + HEADER + 2;
		for (int i = 0; i < 3; i++) {
			int which = i;
			Button tab = Button.builder(Component.translatable("journal.jaymoskingdoms.tab." + tabs[i]), b -> show(which)).bounds(x + i * (w + 4), y, w, TAB_H).build();
			tab.active = i != page;
			addRenderableWidget(tab);
		}
		int fy = top() + PANEL_H - FOOTER + 3;
		if (page == MAP) {
			int bx = left() + 9 + MAP_SIZE + 12;
			Button in = Button.builder(Component.literal("+"), b -> zoom(-1)).bounds(bx, top() + BODY_Y, 20, 16).build();
			Button out = Button.builder(Component.literal("-"), b -> zoom(1)).bounds(bx + 24, top() + BODY_Y, 20, 16).build();
			in.active = zoom > 0;
			out.active = zoom < ZOOMS.length - 1;
			addRenderableWidget(in);
			addRenderableWidget(out);
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left() + panelW() - 66, fy, 60, 16).build());
	}

	private void show(int which) {
		page = lastPage = which;
		scroll = 0;
		rebuildWidgets();
	}

	private void zoom(int by) {
		zoom = lastZoom = Mth.clamp(zoom + by, 0, ZOOMS.length - 1);
		rebuildWidgets();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (key.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (page == MAP) {
			if (scrollY != 0) {
				zoom(scrollY > 0 ? -1 : 1);
			}
			return true;
		}
		int rows = page == QUESTS ? book.entries().size() - questRows() : (int)book.places().stream().filter(Journal.Place::rated).count() - repRows();
		scroll = Mth.clamp(scroll - (int)Math.signum(scrollY), 0, Math.max(0, rows));
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (page == QUESTS) {
			int index = questAt((int)event.x(), (int)event.y());
			if (index >= 0) {
				selected = index;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top(), w = panelW();
		Ui.panel(graphics, x, y, w, PANEL_H, COLOR_GOLD);
		// the header: the journal, and what this page holds
		graphics.item(new ItemStack(Journal.JOURNAL), x + 8, y + 8);
		Ui.line(graphics, this.font, this.title, x + 28, y + 7, w - 36, COLOR_GOLD);
		long rated = book.places().stream().filter(Journal.Place::rated).count();
		Component sub = switch (page) {
			case MAP -> Component.translatable("journal.jaymoskingdoms.sub.map", MAP_SIZE * ZOOMS[zoom] / CELL);
			case REPUTATION -> Component.translatable("journal.jaymoskingdoms.sub.reputation", rated);
			default -> Component.translatable("journal.jaymoskingdoms.sub.quests", book.entries().size());
		};
		Ui.line(graphics, this.font, sub, x + 28, y + 18, w - 36, COLOR_DIM);
		Ui.rule(graphics, x + 6, x + w - 6, y + HEADER - 4);
		int bodyTop = y + BODY_Y;
		switch (page) {
			case MAP -> map(graphics, x + 9, bodyTop, mouseX, mouseY);
			case REPUTATION -> reputation(graphics, x, bodyTop, mouseX, mouseY);
			default -> quests(graphics, x, bodyTop, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	// ---- quests ----------------------------------------------------------------------------------------------------------

	private int questAt(int mouseX, int mouseY) {
		int x = left(), top = top() + BODY_Y;
		int end = Math.min(book.entries().size(), scroll + questRows());
		for (int i = scroll; i < end; i++) {
			if (Ui.over(mouseX, mouseY, x + 6, top + (i - scroll) * QUEST_ROW, panelW() - 12, QUEST_ROW - 1)) {
				return i;
			}
		}
		return -1;
	}

	private static int questColor(Journal.Entry e) {
		return e.ready() ? COLOR_DONE : e.royal() ? COLOR_ROYAL : COLOR_GOLD;
	}

	/** A row per quest (its name, what's left, which way it lies), and the hovered or pressed one in full under them. */
	private void quests(GuiGraphicsExtractor graphics, int x, int top, int mouseX, int mouseY) {
		int w = panelW();
		List<Journal.Entry> entries = book.entries();
		if (entries.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("journal.jaymoskingdoms.no_quests"), x + w / 2, top + 20, COLOR_DIM);
			return;
		}
		scroll = Mth.clamp(scroll, 0, Math.max(0, entries.size() - questRows()));
		selected = Mth.clamp(selected, 0, entries.size() - 1);
		LocalPlayer player = this.minecraft.player;
		int hovered = questAt(mouseX, mouseY);
		int end = Math.min(entries.size(), scroll + questRows());
		int nameW = Math.min(150, (w - 24) / 2);
		for (int i = scroll; i < end; i++) {
			Journal.Entry e = entries.get(i);
			int ry = top + (i - scroll) * QUEST_ROW;
			int color = questColor(e);
			graphics.fill(x + 6, ry, x + w - 6, ry + QUEST_ROW - 1, i == hovered ? Ui.ROW_HOVER : i == selected ? Ui.ROW_PICKED : i % 2 == 0 ? Ui.ROW : Ui.ROW_ALT);
			graphics.fill(x + 6, ry, x + 8, ry + QUEST_ROW - 1, color);
			Ui.line(graphics, this.font, e.title(), x + 12, ry + 4, nameW, color);
			int right = x + w - 10;
			if (e.target().isPresent() && player != null && book.here()) {
				Component where = where(player, e.target().get());
				int ww = this.font.width(where);
				graphics.text(this.font, where, right - ww, ry + 4, COLOR_DIM);
				right -= ww + 8;
			}
			int textX = x + 12 + nameW + 8;
			Ui.line(graphics, this.font, e.objective(), textX, ry + 4, right - textX, e.ready() ? COLOR_DONE : COLOR_DIM);
		}
		Ui.scrollbar(graphics, x + w - 4, top, questRows() * QUEST_ROW, questRows(), entries.size(), scroll);
		Journal.Entry shown = entries.get(hovered >= 0 ? hovered : selected);
		List<Component> text = new ArrayList<>();
		text.add(shown.giver().copy().withColor(COLOR_DIM));
		text.add(shown.text());
		text.add(shown.objective().copy().withColor(shown.ready() ? COLOR_DONE : COLOR_GOLD));
		Ui.detail(graphics, this.font, x, detailY(), w, DETAIL_LINES, shown.title(), questColor(shown), text);
	}

	/** How far and which way {@code pos} lies from the player. */
	static Component where(LocalPlayer player, BlockPos pos) {
		double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
		int distance = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
		double angle = Math.toDegrees(Math.atan2(dx, dz));
		String[] points = {"s", "se", "e", "ne", "n", "nw", "w", "sw"};
		int i = (int)Math.floorMod(Math.round(angle / 45.0), 8L);
		return Component.translatable("quest.jaymoskingdoms.tracker.where", distance, Component.translatable("quest.jaymoskingdoms.tracker." + points[i]));
	}

	// ---- map -------------------------------------------------------------------------------------------------------------

	private void map(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
		LocalPlayer player = this.minecraft.player;
		ClientLevel level = this.minecraft.level;
		if (player == null || level == null) {
			return;
		}
		int n = MAP_SIZE / CELL;
		int scale = ZOOMS[zoom];
		if (cells == null || drawnZoom != zoom) {
			cells = draw(level, player.getBlockX(), player.getBlockZ(), scale, n);
			drawnX = player.getBlockX();
			drawnZ = player.getBlockZ();
			drawnZoom = zoom;
		}
		Ui.well(graphics, x - 1, y - 1, MAP_SIZE + 2, MAP_SIZE + 2);
		for (int row = 0; row < n; row++) {
			int run = 0;
			// (a row's cells of one colour are filled as one)
			for (int col = 1; col <= n; col++) {
				if (col == n || cells[row * n + col] != cells[row * n + run]) {
					graphics.fill(x + run * CELL, y + row * CELL, x + col * CELL, y + (row + 1) * CELL, cells[row * n + run]);
					run = col;
				}
			}
		}
		List<Component> hover = new ArrayList<>();
		double half = MAP_SIZE / 2.0;
		double perPixel = scale / (double)CELL;
		if (book.here()) {
			for (Journal.Place p : book.places()) {
				int color = 0xFF000000 | p.color();
				int[] at = marker(x, y, p.seat(), perPixel, half);
				boolean camp = p.type() == FactionType.BARBARIAN.ordinal();
				int size = p.type() == FactionType.KINGDOM.ordinal() ? 4 : 3;
				graphics.fill(at[0] - size - 1, at[1] - size - 1, at[0] + size + 1, at[1] + size + 1, camp ? 0xFF8A1010 : 0xFF000000);
				graphics.fill(at[0] - size, at[1] - size, at[0] + size, at[1] + size, color);
				if (Math.abs(mouseX - at[0]) <= size + 1 && Math.abs(mouseY - at[1]) <= size + 1) {
					hover.add(Component.literal(p.name()));
					hover.add(Component.translatable("journal.jaymoskingdoms.kind." + FactionType.values()[p.type()].id()).withColor(COLOR_DIM));
					if (p.rated()) {
						hover.add(Component.translatable("journal.jaymoskingdoms.rep", Component.translatable("journal.jaymoskingdoms.word." + p.word()), p.reputation())
							.withColor(repColor(p.reputation())));
					}
				}
			}
			int number = 0;
			for (Journal.Entry e : book.entries()) {
				number++;
				if (e.target().isEmpty()) {
					continue;
				}
				int[] at = marker(x, y, e.target().get(), perPixel, half);
				int color = e.ready() ? COLOR_DONE : e.royal() ? COLOR_ROYAL : COLOR_GOLD;
				graphics.fill(at[0] - 4, at[1] - 5, at[0] + 4, at[1] + 5, 0xFF000000);
				String label = Integer.toString(number);
				graphics.centeredText(this.font, label, at[0], at[1] - 4, color);
				if (Math.abs(mouseX - at[0]) <= 4 && Math.abs(mouseY - at[1]) <= 5) {
					hover.add(e.title().copy().withColor(color));
					hover.add(e.objective().copy().withColor(COLOR_DIM));
				}
			}
		}
		// you: a white arrowhead, pointing the way you face
		int[] me = marker(x, y, player.blockPosition(), perPixel, half);
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		for (int i = -1; i <= 3; i++) {
			int px = me[0] + (int)Math.round(fx * i), pz = me[1] + (int)Math.round(fz * i);
			graphics.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFF000000);
		}
		for (int i = -1; i <= 3; i++) {
			int px = me[0] + (int)Math.round(fx * i), pz = me[1] + (int)Math.round(fz * i);
			graphics.fill(px - 1, pz - 1, px + 1, pz + 1, 0xFFFFFFFF);
		}
		// beside it: the legend, where you stand
		int lx = x + MAP_SIZE + 12, ly = y + 24;
		legend(graphics, lx, ly, 0xFFFFFFFF, "you");
		legend(graphics, lx, ly += 12, COLOR_GOLD, "quest");
		legend(graphics, lx, ly += 12, COLOR_ROYAL, "decree");
		legend(graphics, lx, ly += 12, COLOR_DONE, "ready");
		legend(graphics, lx, ly += 12, 0xFF808080, "settlement");
		ly += 18;
		graphics.text(this.font, Component.translatable("journal.jaymoskingdoms.at", player.getBlockX(), player.getBlockZ()), lx, ly, COLOR_DIM);
		if (!book.here()) {
			for (FormattedCharSequence line : this.font.split(Component.translatable("journal.jaymoskingdoms.elsewhere"), left() + panelW() - 9 - lx)) {
				graphics.text(this.font, line, lx, ly += 10, COLOR_DIM);
			}
		}
		if (!hover.isEmpty()) {
			graphics.setComponentTooltipForNextFrame(this.font, hover, mouseX, mouseY);
		}
	}

	private void legend(GuiGraphicsExtractor graphics, int x, int y, int color, String what) {
		graphics.fill(x, y, x + 7, y + 7, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + 6, y + 6, color);
		Ui.line(graphics, this.font, Component.translatable("journal.jaymoskingdoms.legend." + what), x + 11, y, left() + panelW() - 9 - x - 11, COLOR_TEXT);
	}

	/** Where {@code pos} goes on the map (screen px), held to its edge when it's off it. */
	private int[] marker(int x, int y, BlockPos pos, double perPixel, double half) {
		double px = (pos.getX() - drawnX) / perPixel + half, pz = (pos.getZ() - drawnZ) / perPixel + half;
		return new int[] {x + (int)Mth.clamp(px, 3, MAP_SIZE - 3), y + (int)Mth.clamp(pz, 4, MAP_SIZE - 4)};
	}

	/** The land about ({@code cx}, {@code cz}) as a map draws it: one sample per cell, shaded by the slope from the north. */
	private static int[] draw(ClientLevel level, int cx, int cz, int scale, int n) {
		int[] out = new int[n * n];
		int[] heights = new int[n];
		for (int row = -1; row < n; row++) {
			int z = cz + (row - n / 2) * scale;
			for (int col = 0; col < n; col++) {
				int x = cx + (col - n / 2) * scale;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					if (row >= 0) {
						out[row * n + col] = COLOR_PARCHMENT;
					}
					heights[col] = Integer.MIN_VALUE;
					continue;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				BlockPos pos = new BlockPos(x, top, z);
				BlockState state = level.getBlockState(pos);
				MapColor color = state.getMapColor(level, pos);
				int above = heights[col];
				heights[col] = top;
				if (row < 0) {
					continue;
				}
				if (color == MapColor.NONE || top <= level.getMinY()) {
					out[row * n + col] = COLOR_PARCHMENT;
					continue;
				}
				MapColor.Brightness light = above == Integer.MIN_VALUE || color == MapColor.WATER ? MapColor.Brightness.NORMAL
					: top > above ? MapColor.Brightness.HIGH : top < above ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				out[row * n + col] = 0xFF000000 | color.calculateARGBColor(light);
			}
		}
		return out;
	}

	// ---- reputation ------------------------------------------------------------------------------------------------------

	/** A row per settlement that rates you: its colour, name, kind and way, and what it thinks of you on a bar. */
	private void reputation(GuiGraphicsExtractor graphics, int x, int top, int mouseX, int mouseY) {
		List<Journal.Place> rated = book.places().stream().filter(Journal.Place::rated).toList();
		int w = panelW();
		if (rated.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("journal.jaymoskingdoms.no_places"), x + w / 2, top + 20, COLOR_DIM);
			return;
		}
		scroll = Mth.clamp(scroll, 0, Math.max(0, rated.size() - repRows()));
		LocalPlayer player = this.minecraft.player;
		int end = Math.min(rated.size(), scroll + repRows());
		for (int i = scroll; i < end; i++) {
			Journal.Place p = rated.get(i);
			int ty = top + (i - scroll) * REP_ROW;
			boolean hover = Ui.over(mouseX, mouseY, x + 6, ty, w - 12, REP_ROW - 2);
			Ui.row(graphics, x + 6, ty, x + w - 6, ty + REP_ROW - 2, i, hover);
			graphics.fill(x + 6, ty, x + 8, ty + REP_ROW - 2, 0xFF000000 | p.color());
			int bw = 90, bx = x + w - bw - 12;
			Ui.line(graphics, this.font, Component.literal(p.name()), x + 12, ty + 2, bx - x - 20, COLOR_TEXT);
			Component kind = Component.translatable("journal.jaymoskingdoms.kind." + FactionType.values()[p.type()].id());
			if (player != null && book.here()) {
				kind = Component.translatable("journal.jaymoskingdoms.kind_where", kind, where(player, p.seat()));
			}
			Ui.line(graphics, this.font, kind, x + 12, ty + 11, bx - x - 20, COLOR_DIM);
			// the bar: -1000 at its left, 1000 at its right, the middle marked
			int by = ty + 13;
			graphics.fill(bx, by, bx + bw, by + 3, Ui.BAR_BACK);
			int mid = bx + bw / 2;
			int at = bx + (int)((Mth.clamp(p.reputation(), -1000, 1000) + 1000) / 2000.0 * bw);
			graphics.fill(Math.min(mid, at), by, Math.max(mid, at) + 1, by + 3, repColor(p.reputation()));
			graphics.fill(mid, by - 1, mid + 1, by + 4, COLOR_DIM);
			Component word = Component.translatable("journal.jaymoskingdoms.rep", Component.translatable("journal.jaymoskingdoms.word." + p.word()), p.reputation());
			graphics.text(this.font, word, bx + bw - this.font.width(word), ty + 2, repColor(p.reputation()));
		}
		Ui.scrollbar(graphics, x + w - 4, top, repRows() * REP_ROW, repRows(), rated.size(), scroll);
	}

	static int repColor(int rep) {
		return rep <= -100 ? COLOR_HATED : rep < 100 ? COLOR_DIM : rep < 400 ? 0xFFB0E070 : COLOR_DONE;
	}
}
