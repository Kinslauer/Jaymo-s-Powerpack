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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * (0.5.49) The traveller's journal ({@link Journal}): three pages. <b>Quests</b>: each quest you carry, who gave it, its
 * story, what's left and which way it lies. <b>Map</b>: the land about you as a map draws it (only what your eyes have
 * loaded; the rest is blank parchment), with you, your quests' places and the settlements on it, and zoom. <b>Reputation</b>:
 * what each settlement you know thinks of you.
 */
final class JournalScreen extends Screen {
	static final int PANEL_W = 340, PANEL_H = 236, PAD = 8, TAB_H = 18;
	/** The map: its size on screen (px), the size of one drawn cell (px), and the zooms (blocks per cell). */
	static final int MAP_SIZE = 200, CELL = 2;
	static final int[] ZOOMS = {1, 2, 4, 8, 16, 32};
	static final int DEFAULT_ZOOM = 2;
	static final int COLOR_PANEL = 0xF0201812, COLOR_CARD = 0xA0302820, COLOR_EDGE = 0xFFB08A3C, COLOR_TEXT = 0xFFF2E6CC, COLOR_DIM = 0xFFB8AA90,
		COLOR_ROYAL = 0xFFFF6A50, COLOR_GOLD = 0xFFFFC21A, COLOR_DONE = 0xFF66EE66, COLOR_PARCHMENT = 0xFFD8C8A0, COLOR_HATED = 0xFFE04040;
	static final int QUESTS = 0, MAP = 1, REPUTATION = 2;

	private static int lastPage = QUESTS, lastZoom = DEFAULT_ZOOM;

	private final Journal.Book book;
	private final KeyMapping key;
	private int page = lastPage, zoom = lastZoom, scroll;
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

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	@Override
	protected void init() {
		int x = left() + PAD, y = top() + 6;
		String[] tabs = {"quests", "map", "reputation"};
		int w = (PANEL_W - PAD * 2 - 8) / 3;
		for (int i = 0; i < 3; i++) {
			int which = i;
			Button tab = Button.builder(Component.translatable("journal.jaymoskingdoms.tab." + tabs[i]), b -> show(which)).bounds(x + i * (w + 4), y, w, TAB_H).build();
			tab.active = i != page;
			addRenderableWidget(tab);
		}
		if (page == MAP) {
			int bx = left() + PAD + MAP_SIZE + 10, by = top() + TAB_H + 16;
			Button in = Button.builder(Component.literal("+"), b -> zoom(-1)).bounds(bx, by, 20, 18).build();
			Button out = Button.builder(Component.literal("-"), b -> zoom(1)).bounds(bx + 24, by, 20, 18).build();
			in.active = zoom > 0;
			out.active = zoom < ZOOMS.length - 1;
			addRenderableWidget(in);
			addRenderableWidget(out);
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(this.width / 2 - 40, top() + PANEL_H - 24, 80, 18).build());
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
		scroll = Math.max(0, scroll - (int)Math.signum(scrollY) * 20);
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		graphics.fill(x, y, x + PANEL_W, y + PANEL_H, COLOR_PANEL);
		edge(graphics, x, y, PANEL_W, PANEL_H, COLOR_EDGE);
		int bodyTop = y + TAB_H + 12, bodyBottom = y + PANEL_H - 30;
		switch (page) {
			case MAP -> map(graphics, x + PAD, bodyTop, mouseX, mouseY);
			case REPUTATION -> reputation(graphics, x + PAD, bodyTop, bodyBottom);
			default -> quests(graphics, x + PAD, bodyTop, bodyBottom);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	// ---- quests ----------------------------------------------------------------------------------------------------------

	private void quests(GuiGraphicsExtractor graphics, int x, int top, int bottom) {
		int w = PANEL_W - PAD * 2 - 8;
		if (book.entries().isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("journal.jaymoskingdoms.no_quests"), this.width / 2, top + 20, COLOR_DIM);
			return;
		}
		List<Line> lines = new ArrayList<>();
		LocalPlayer player = this.minecraft.player;
		for (Journal.Entry e : book.entries()) {
			lines.add(new Line(e.title().getVisualOrderText(), e.royal() ? COLOR_ROYAL : COLOR_GOLD, true));
			lines.add(new Line(e.giver().getVisualOrderText(), COLOR_DIM, false));
			for (FormattedCharSequence s : this.font.split(e.text(), w)) {
				lines.add(new Line(s, COLOR_TEXT, false));
			}
			for (FormattedCharSequence s : this.font.split(e.objective(), w)) {
				lines.add(new Line(s, e.ready() ? COLOR_DONE : COLOR_DIM, false));
			}
			if (e.target().isPresent() && player != null && book.here()) {
				lines.add(new Line(where(player, e.target().get()).getVisualOrderText(), COLOR_DIM, false));
			}
			lines.add(null);
		}
		int total = lines.size() * 10;
		scroll = Math.min(scroll, Math.max(0, total - (bottom - top)));
		int ty = top - scroll;
		for (Line line : lines) {
			if (line != null && ty >= top && ty + 9 <= bottom) {
				if (line.head()) {
					graphics.fill(x, ty - 1, x + 2, ty + 9, line.color());
				}
				graphics.text(this.font, line.text(), x + 6, ty, line.color());
			}
			ty += line == null ? 6 : 10;
		}
	}

	private record Line(FormattedCharSequence text, int color, boolean head) {
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
		edge(graphics, x - 1, y - 1, MAP_SIZE + 2, MAP_SIZE + 2, COLOR_EDGE);
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
		// beside it: the scale, the legend, where you stand
		int lx = x + MAP_SIZE + 10, ly = y + 26;
		graphics.text(this.font, Component.translatable("journal.jaymoskingdoms.scale", MAP_SIZE * scale / CELL), lx, ly, COLOR_DIM);
		ly += 14;
		legend(graphics, lx, ly, 0xFFFFFFFF, "you");
		legend(graphics, lx, ly += 12, COLOR_GOLD, "quest");
		legend(graphics, lx, ly += 12, COLOR_ROYAL, "decree");
		legend(graphics, lx, ly += 12, COLOR_DONE, "ready");
		legend(graphics, lx, ly += 12, 0xFF808080, "settlement");
		ly += 18;
		graphics.text(this.font, Component.translatable("journal.jaymoskingdoms.at", player.getBlockX(), player.getBlockZ()), lx, ly, COLOR_DIM);
		if (!book.here()) {
			graphics.text(this.font, Component.translatable("journal.jaymoskingdoms.elsewhere"), lx, ly + 12, COLOR_DIM);
		}
		if (!hover.isEmpty()) {
			graphics.setComponentTooltipForNextFrame(this.font, hover, mouseX, mouseY);
		}
	}

	private void legend(GuiGraphicsExtractor graphics, int x, int y, int color, String what) {
		graphics.fill(x, y, x + 7, y + 7, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + 6, y + 6, color);
		graphics.text(this.font, Component.translatable("journal.jaymoskingdoms.legend." + what), x + 11, y, COLOR_TEXT);
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

	private void reputation(GuiGraphicsExtractor graphics, int x, int top, int bottom) {
		List<Journal.Place> rated = book.places().stream().filter(Journal.Place::rated).toList();
		if (rated.isEmpty()) {
			graphics.centeredText(this.font, Component.translatable("journal.jaymoskingdoms.no_places"), this.width / 2, top + 20, COLOR_DIM);
			return;
		}
		int rowH = 22;
		int w = PANEL_W - PAD * 2;
		scroll = Math.min(scroll, Math.max(0, rated.size() * rowH - (bottom - top)));
		int ty = top - scroll;
		LocalPlayer player = this.minecraft.player;
		for (Journal.Place p : rated) {
			if (ty >= top && ty + rowH - 2 <= bottom) {
				graphics.fill(x, ty, x + w, ty + rowH - 2, COLOR_CARD);
				graphics.fill(x, ty, x + 3, ty + rowH - 2, 0xFF000000 | p.color());
				graphics.text(this.font, p.name(), x + 7, ty + 2, COLOR_TEXT);
				Component kind = Component.translatable("journal.jaymoskingdoms.kind." + FactionType.values()[p.type()].id());
				if (player != null && book.here()) {
					kind = Component.translatable("journal.jaymoskingdoms.kind_where", kind, where(player, p.seat()));
				}
				graphics.text(this.font, kind, x + 7, ty + 11, COLOR_DIM);
				// the bar: -1000 at its left, 1000 at its right, the middle marked
				int bw = 90, bx = x + w - bw - 6, by = ty + 12;
				graphics.fill(bx, by, bx + bw, by + 4, 0xFF000000);
				int mid = bx + bw / 2;
				int at = bx + (int)((p.reputation() + 1000) / 2000.0 * bw);
				graphics.fill(Math.min(mid, at), by + 1, Math.max(mid, at) + 1, by + 3, repColor(p.reputation()));
				graphics.fill(mid, by - 1, mid + 1, by + 5, COLOR_DIM);
				Component word = Component.translatable("journal.jaymoskingdoms.rep", Component.translatable("journal.jaymoskingdoms.word." + p.word()), p.reputation());
				graphics.text(this.font, word, x + w - 6 - this.font.width(word), ty + 2, repColor(p.reputation()));
			}
			ty += rowH;
		}
	}

	static int repColor(int rep) {
		return rep <= -100 ? COLOR_HATED : rep < 100 ? COLOR_DIM : rep < 400 ? 0xFFB0E070 : COLOR_DONE;
	}

	private static void edge(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}
}
