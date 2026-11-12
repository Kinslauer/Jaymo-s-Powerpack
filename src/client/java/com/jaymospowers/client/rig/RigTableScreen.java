package com.jaymospowers.client.rig;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.xenoform.forms.HiveSwarm;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigTable;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wrist table: a table on the forearm (opened from the Rig menu, Sneak + J). Three leaves, picked along the top:
 * <ul>
 * <li><b>Map</b>: a small raised map of the land the client has loaded round you, one column to every {@link #STEP} blocks,
 * each in the colour of its top block, drawn from the back to the front so hills hide what is behind them. You, your
 * drones, the enemies loaded near you and your patrol marks are pinned on it.</li>
 * <li><b>Studied</b>: the enemy kinds you have studied (read with the Scrying Lens, or fought {@code RigStudy.STUDY_KILLS}
 * times), a list that scrolls.</li>
 * <li><b>Orders</b>: the drones' formations, shell, missile, duties and patrol, the decoy's kind, the Bank, the field.</li>
 * </ul>
 * Long text slides or scrolls ({@link UiStyle}), never cut.
 */
final class RigTableScreen extends Screen {
	private static final int W = 336, H = 246, ACCENT = 0xFF5FE6FF, HULL_DARK = 0xFF1E4A5A;
	private static final int TAB_H = 15, PAD = 8;
	/** The map: cells to a side, blocks to a cell, pixels to a cell, pixels a block of height lifts a column, ticks between re-reads of the land. */
	private static final int GRID = 40, STEP = 2, CELL = 3, REREAD = 20;
	private static final double LIFT = 0.9;
	private static final int ROW_H = 14, REFRESH = 10;
	private static final String[] TAB_NAMES = {"map", "studied", "orders"};

	private record Button(int x, int y, int w, int h, Component label, String tip, int code, int arg, boolean on) {
	}

	private RigTable.Data data;
	private List<String> ids = new ArrayList<>();
	private List<Integer> kills = new ArrayList<>();
	private List<Boolean> lens = new ArrayList<>();
	private int tab;
	private int scroll;
	private int age;
	// the land
	private final int[] top = new int[GRID * GRID];
	private final short[] relief = new short[GRID * GRID];
	private int readX = Integer.MIN_VALUE, readZ, readAge = REREAD;
	private int baseY;
	private @Nullable String tipShown;

	RigTableScreen(RigTable.Data data) {
		super(Component.translatable("screen.jaymospowers.rig_table"));
		update(data);
	}

	/** The table's contents from the server: open the screen, or refresh the one that is open. */
	static void receive(Minecraft minecraft, RigTable.Show show) {
		if (show.open()) {
			minecraft.gui.setScreen(new RigTableScreen(show.data()));
		} else if (minecraft.gui.screen() instanceof RigTableScreen open) {
			open.update(show.data());
		}
	}

	void update(RigTable.Data fresh) {
		if (!fresh.ids().isEmpty() || this.data == null) {
			this.ids = new ArrayList<>(fresh.ids());
			this.kills = new ArrayList<>(fresh.kills());
			this.lens = new ArrayList<>(fresh.lens());
		}
		this.data = fresh;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (width - W) / 2;
	}

	private int topY() {
		return Math.max(2, (height - H) / 2);
	}

	// ---- the land ------------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isAlive() || !Rig.wearsAny(player)) {
			onClose();
			return;
		}
		if (++age % REFRESH == 0) {
			ClientPlayNetworking.send(new RigTable.Order(RigTable.REFRESH, 0));
		}
		int px = Mth.floor(player.getX()), pz = Mth.floor(player.getZ());
		if (++readAge >= REREAD || Math.abs(px - readX) >= 6 || Math.abs(pz - readZ) >= 6) {
			read(player, px, pz);
		}
	}

	/** Reads the top of every column round {@code (px, pz)} that the client has loaded. */
	private void read(LocalPlayer player, int px, int pz) {
		ClientLevel level = (ClientLevel)player.level();
		readX = px;
		readZ = pz;
		readAge = 0;
		baseY = Mth.floor(player.getY());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int gz = 0; gz < GRID; gz++) {
			for (int gx = 0; gx < GRID; gx++) {
				int x = px + (gx - GRID / 2) * STEP, z = pz + (gz - GRID / 2) * STEP;
				int i = gz * GRID + gx;
				if (!level.hasChunkAt(pos.set(x, baseY, z))) {
					top[i] = 0;
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
				BlockState state = level.getBlockState(pos.set(x, y, z));
				top[i] = 0xFF000000 | state.getMapColor(level, pos).col;
				relief[i] = (short)y;
			}
		}
	}

	/** Where a world point lands on the map, relative to the map's middle. */
	private double[] project(double x, double y, double z) {
		double u = (x - readX) / STEP, v = (z - readZ) / STEP;
		return new double[] {(u - v) * CELL, (u + v) * CELL * 0.5 - (y - baseY) * LIFT};
	}

	private static int shade(int argb, float f) {
		int r = Mth.clamp((int)(((argb >> 16) & 255) * f), 0, 255), g = Mth.clamp((int)(((argb >> 8) & 255) * f), 0, 255), b = Mth.clamp((int)((argb & 255) * f), 0, 255);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	// ---- drawing -------------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		int x = left(), y = topY();
		UiStyle.panel(graphics, x, y, W, H, ACCENT);
		UiStyle.header(graphics, font, x, y, W, new ItemStack(Items.CLOCK), Component.translatable("screen.jaymospowers.rig_table"),
			Component.translatable("gui.jaymospowers.rig_table.sub", Math.max(1, data.count()), data.out() ? Component.translatable("gui.jaymospowers.rig_table.out")
				: Component.translatable("gui.jaymospowers.rig_table.home")), ACCENT, 0);
		// the leaves
		int tabW = (W - 2 * PAD - 8) / 3;
		for (int t = 0; t < 3; t++) {
			int tx = x + PAD + t * (tabW + 4), ty = y + UiStyle.HEADER;
			boolean hover = UiStyle.over(mouseX, mouseY, tx, ty, tabW, TAB_H);
			graphics.fill(tx, ty, tx + tabW, ty + TAB_H, t == tab ? 0x605FE6FF : hover ? UiStyle.ROW_HOVER : UiStyle.ROW);
			if (t == tab) {
				graphics.fill(tx, ty + TAB_H - 2, tx + tabW, ty + TAB_H, ACCENT);
			}
			UiStyle.lineCentred(graphics, font, Component.translatable("gui.jaymospowers.rig_table.tab." + TAB_NAMES[t]), tx + tabW / 2, ty + 3, tabW - 6,
				t == tab ? UiStyle.TEXT : UiStyle.DIM);
		}
		int cx = x + PAD, cy = y + UiStyle.HEADER + TAB_H + 6, cw = W - 2 * PAD, ch = H - UiStyle.HEADER - TAB_H - 6 - PAD;
		tipShown = null;
		switch (tab) {
			case 0 -> map(graphics, player, cx, cy, cw, ch);
			case 1 -> studied(graphics, mouseX, mouseY, cx, cy, cw, ch);
			default -> orders(graphics, mouseX, mouseY, cx, cy, cw, ch);
		}
	}

	private void map(GuiGraphicsExtractor graphics, LocalPlayer player, int cx, int cy, int cw, int ch) {
		int legendH = 12;
		int mapH = ch - legendH - 2;
		graphics.fill(cx, cy, cx + cw, cy + mapH, 0xFF0A0A10);
		int mx = cx + cw / 2, my = cy + mapH / 2 + 8;
		graphics.enableScissor(cx, cy, cx + cw, cy + mapH);
		for (int s = 0; s <= 2 * GRID - 2; s++) {
			for (int gx = Math.max(0, s - GRID + 1); gx <= Math.min(s, GRID - 1); gx++) {
				int gz = s - gx;
				int i = gz * GRID + gx;
				if (top[i] == 0) {
					continue;
				}
				int u = gx - GRID / 2, v = gz - GRID / 2;
				int h = relief[i];
				int sx = mx + (u - v) * CELL;
				int sy = my + (int)Math.round((u + v) * CELL * 0.5 - (h - baseY) * LIFT);
				// light from the back-left: a column higher than the one behind it is bright, lower is dim
				int behind = gx > 0 && top[i - 1] != 0 ? relief[i - 1] : h;
				float light = h > behind ? 1.15F : h < behind ? 0.82F : 1.0F;
				int frontLow = h - 2;
				if (gx + 1 < GRID && top[i + 1] != 0) {
					frontLow = Math.min(h, relief[i + 1]);
				}
				if (gz + 1 < GRID && top[i + GRID] != 0) {
					frontLow = Math.min(frontLow, relief[i + GRID]);
				}
				int side = Math.max(1, (int)Math.round((h - frontLow) * LIFT));
				graphics.fill(sx - CELL, sy + CELL, sx + CELL, sy + CELL + side, shade(top[i], 0.6F));
				graphics.fill(sx - CELL, sy, sx + CELL, sy + CELL, shade(top[i], light));
			}
		}
		// pins
		ClientLevel level = (ClientLevel)player.level();
		double range = GRID * STEP * 0.5;
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof Enemy && e.isAlive() && e.distanceToSqr(player) < range * range) {
				pin(graphics, mx, my, e.getX(), e.getY() + 1.0, e.getZ(), 0xFFE04040, 2, 0);
			}
		}
		for (Vec3 drone : data.drones()) {
			pin(graphics, mx, my, drone.x, drone.y, drone.z, 0xFF5FE6FF, 2, 1);
		}
		for (int k = 0; k < data.route().size(); k++) {
			Vec3 mark = data.route().get(k);
			double[] at = project(mark.x, mark.y, mark.z);
			int px = mx + (int)at[0], py = my + (int)at[1];
			graphics.fill(px - 3, py - 3, px + 4, py + 4, 0xFF1E4A5A);
			graphics.fill(px - 2, py - 2, px + 3, py + 3, 0xFF5FE6FF);
			graphics.text(font, Integer.toString(k + 1), px - 2, py - 4, 0xFF20140A, false);
		}
		pin(graphics, mx, my, player.getX(), player.getY() + 1.0, player.getZ(), 0xFFFFFFFF, 3, 2);
		graphics.disableScissor();
		graphics.outline(cx, cy, cw, mapH, HULL_DARK);
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.rig_table.legend"), cx, cy + mapH + 3, cw, UiStyle.DIM);
	}

	/** A pin on the map: a small square with a stalk up from the ground. */
	private void pin(GuiGraphicsExtractor graphics, int mx, int my, double x, double y, double z, int color, int size, int stalk) {
		double[] at = project(x, y, z);
		int px = mx + (int)at[0], py = my + (int)at[1];
		if (stalk > 0) {
			graphics.fill(px, py, px + 1, py + 5, color & 0xB0FFFFFF);
		}
		graphics.fill(px - size / 2, py - size / 2 - 2, px - size / 2 + size, py - size / 2 - 2 + size, color);
	}

	private void studied(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
		if (ids.isEmpty()) {
			UiStyle.wrapped(graphics, font, Component.translatable("gui.jaymospowers.rig_table.studied.none"), cx + 2, cy + 4, cw - 8, ch / 10, 10, UiStyle.DIM, false);
			return;
		}
		UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.rig_table.studied.count", ids.size()), cx, cy, cw, UiStyle.DIM);
		int top = cy + 12, rows = (ch - 12) / ROW_H;
		scroll = Mth.clamp(scroll, 0, Math.max(0, ids.size() - rows));
		for (int r = 0; r < rows && scroll + r < ids.size(); r++) {
			int i = scroll + r, ry = top + r * ROW_H;
			UiStyle.row(graphics, cx, ry, cx + cw - 6, ry + ROW_H - 1, i, UiStyle.over(mouseX, mouseY, cx, ry, cw - 6, ROW_H - 1));
			UiStyle.line(graphics, font, nameOf(ids.get(i)), cx + 4, ry + 3, cw - 118, UiStyle.TEXT);
			Component right = Component.translatable(lens.get(i) ? "gui.jaymospowers.rig_table.studied.row_lens" : "gui.jaymospowers.rig_table.studied.row", kills.get(i));
			UiStyle.lineRight(graphics, font, right, cx + cw - 10, ry + 3, 106, lens.get(i) ? ACCENT : UiStyle.DIM);
		}
		UiStyle.scrollbar(graphics, cx + cw - 3, top, rows * ROW_H, rows, ids.size(), scroll);
	}

	private static Component nameOf(String id) {
		Identifier key = Identifier.tryParse(id);
		if (key != null) {
			var type = BuiltInRegistries.ENTITY_TYPE.get(key);
			if (type.isPresent()) {
				return type.get().value().getDescription();
			}
		}
		return Component.literal(id);
	}

	// ---- orders --------------------------------------------------------------------------------------------------------

	private List<Button> buttons(int cx, int cy, int cw) {
		List<Button> list = new ArrayList<>();
		int w4 = (cw - 3 * 4) / 4, w3 = (cw - 2 * 4) / 3, h = 16, row = 34;
		boolean out = data.out();
		boolean fetch = (data.flags() & RigTable.Data.FETCHING) != 0, mend = (data.flags() & RigTable.Data.MENDING) != 0, patrol = (data.flags() & RigTable.Data.PATROLLING) != 0;
		int y = cy + 11;
		list.add(new Button(cx, y, w3, h, Component.translatable(out ? "gui.jaymospowers.rig_table.stow" : "gui.jaymospowers.rig_table.deploy"), out ? "stow" : "deploy",
			out ? RigTable.STOW : RigTable.DEPLOY, 0, out));
		list.add(new Button(cx + w3 + 4, y, w3, h, Component.translatable("gui.jaymospowers.rig_table.shell"), "shell", RigTable.SHELL, 0, data.shell()));
		list.add(new Button(cx + 2 * (w3 + 4), y, w3, h, Component.translatable("gui.jaymospowers.rig_table.missile"), "missile", RigTable.MISSILE, 0, false));
		y += row;
		String[] forms = {"close", "surround", "strafe", "scout"};
		for (int f = 0; f < HiveSwarm.FORMATIONS; f++) {
			list.add(new Button(cx + f * (w4 + 4), y, w4, h, Component.translatable("gui.jaymospowers.rig_table.form." + forms[f]), "form_" + forms[f], RigTable.FORMATION, f,
				data.formation() == f));
		}
		y += row;
		list.add(new Button(cx, y, w4, h, Component.translatable("gui.jaymospowers.rig_table.fetch"), "fetch", RigTable.FETCH, 0, fetch));
		list.add(new Button(cx + (w4 + 4), y, w4, h, Component.translatable("gui.jaymospowers.rig_table.mend"), "mend", RigTable.MEND, 0, mend));
		list.add(new Button(cx + 2 * (w4 + 4), y, w4, h, Component.translatable("gui.jaymospowers.rig_table.patrol"), "patrol", RigTable.PATROL, 0, patrol));
		list.add(new Button(cx + 3 * (w4 + 4), y, w4, h, Component.translatable("gui.jaymospowers.rig_table.clear_route", data.route().size()), "clear_route", RigTable.CLEAR_ROUTE, 0, false));
		y += row;
		list.add(new Button(cx, y, w4, h, Component.translatable(data.trio() ? "gui.jaymospowers.rig_table.kind_trio" : "gui.jaymospowers.rig_table.kind_lure"), "kind",
			RigTable.DECOY_KIND, 0, false));
		list.add(new Button(cx + (w4 + 4), y, w4, h, Component.translatable("gui.jaymospowers.rig_table.decoy"), "decoy", RigTable.DECOY, 0, false));
		list.add(new Button(cx + 2 * (w4 + 4), y, w4, h, Component.translatable("gui.jaymospowers.rig_table.bank"), "bank", RigTable.BANK, 0, data.bank()));
		list.add(new Button(cx + 3 * (w4 + 4), y, w4, h, Component.translatable("gui.jaymospowers.rig_table.field", data.fieldBlows()), "field", RigTable.FIELD, 0, data.fieldBlows() > 0));
		return list;
	}

	private void orders(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int cx, int cy, int cw, int ch) {
		String[] titles = {"drones", "formation", "duties", "devices"};
		List<Button> list = buttons(cx, cy, cw);
		for (int t = 0; t < titles.length; t++) {
			UiStyle.line(graphics, font, Component.translatable("gui.jaymospowers.rig_table.section." + titles[t]), cx, cy + t * 34, cw, UiStyle.DIM);
		}
		for (Button b : list) {
			boolean hover = UiStyle.over(mouseX, mouseY, b.x, b.y, b.w, b.h);
			graphics.fill(b.x, b.y, b.x + b.w, b.y + b.h, b.on ? 0x705FE6FF : hover ? UiStyle.ROW_HOVER : UiStyle.ROW);
			graphics.outline(b.x, b.y, b.w, b.h, hover ? ACCENT : b.on ? 0xFF3FA0B8 : HULL_DARK);
			UiStyle.lineCentred(graphics, font, b.label, b.x + b.w / 2, b.y + 4, b.w - 6, b.on ? UiStyle.TEXT : 0xFFD8D8E0);
			if (hover) {
				tipShown = b.tip;
			}
		}
		int boxY = cy + 4 * 34 + 2;
		int lines = Math.max(2, (cy + ch - boxY - 12) / UiStyle.LINE);
		Component name = tipShown == null ? null : Component.translatable("gui.jaymospowers.rig_table.tip." + tipShown + ".name");
		Component text = Component.translatable(tipShown == null ? "gui.jaymospowers.rig_table.tip.hint" : "gui.jaymospowers.rig_table.tip." + tipShown);
		UiStyle.detail(graphics, font, cx - PAD + 2, boxY, cw + 2 * PAD - 4, lines, name, ACCENT, List.of(text));
	}

	// ---- input ---------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		int mouseX = (int)event.x(), mouseY = (int)event.y();
		int x = left(), y = topY();
		int tabW = (W - 2 * PAD - 8) / 3;
		for (int t = 0; t < 3; t++) {
			if (UiStyle.over(mouseX, mouseY, x + PAD + t * (tabW + 4), y + UiStyle.HEADER, tabW, TAB_H)) {
				tab = t;
				return true;
			}
		}
		if (tab == 2) {
			int cx = x + PAD, cy = y + UiStyle.HEADER + TAB_H + 6, cw = W - 2 * PAD;
			for (Button b : buttons(cx, cy, cw)) {
				if (UiStyle.over(mouseX, mouseY, b.x, b.y, b.w, b.h)) {
					ClientPlayNetworking.send(new RigTable.Order(b.code, b.arg));
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (tab == 1) {
			scroll = Math.max(0, scroll - (int)Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}
}
