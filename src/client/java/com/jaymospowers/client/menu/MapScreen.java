package com.jaymospowers.client.menu;

import com.jaymospowers.client.PowerScreen;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.power.arsenal.ArsenalPlaces;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.60) The character menu's Map page ({@link MenuTabs}): the land about you as a map draws it (only what your game has
 * loaded; the rest is blank), with you and your waypoints on it, and the waypoints in a list beside it, nearest first, with
 * how far each is. Waypoints: an Arsenal user's remembered places ({@link ArsenalPlaces}), and whatever Jaymo's Kingdoms
 * adds (the settlements you know, your party). The wheel zooms. With Xaero's World Map installed, a button opens it.
 */
public class MapScreen extends Screen {
	static final int W = 300, H = 196;
	/** The map: its size on screen and one drawn cell's size (px); the zooms (blocks per cell). */
	static final int MAP_X = 8, MAP_Y = 20, MAP_SIZE = 168, CELL = 2;
	static final int[] ZOOMS = {1, 2, 4, 8, 16};
	static final int DEFAULT_ZOOM = 2;
	/** The list beside the map: a row's height. */
	static final int LIST_ROW = 11;
	/** Arsenal's marks on the map (blue), unloaded land, and how often the map is drawn again (ticks). */
	static final int COLOR_MARK = 0xFF6AA8FF, COLOR_BLANK = 0xFF2A2A30, REDRAW_TICKS = 20;
	/** Xaero's World Map's open-map key (its name in the controls list). */
	static final String XAERO_MAP_KEY = "gui.xaero_open_map";

	private record Pin(Component name, BlockPos pos, int color) {
	}

	private static int lastZoom = DEFAULT_ZOOM;
	private int zoom = lastZoom, scroll, age;
	private int[] cells;
	private int drawnX, drawnZ, drawnZoom = -1;

	public MapScreen() {
		super(Component.translatable("menu.jaymospowers.tab.map"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}

	int[] corner() {
		return new int[] {left(), top()};
	}

	@Override
	protected void init() {
		KeyMapping xaero = xaeroKey();
		if (xaero != null) {
			int lx = left() + MAP_X + MAP_SIZE + 8;
			addRenderableWidget(Button.builder(Component.translatable("menu.jaymospowers.map.full"), b -> {
				InputConstants.Key key = InputConstants.getKey(xaero.saveString());
				onClose();
				KeyMapping.click(key);
			}).bounds(lx, top() + H - 26, W - (lx - left()) - 8, 16).build());
		}
	}

	private @Nullable KeyMapping xaeroKey() {
		for (KeyMapping mapping : this.minecraft.options.keyMappings) {
			if (XAERO_MAP_KEY.equals(mapping.getName()) && !mapping.isUnbound()) {
				return mapping;
			}
		}
		return null;
	}

	@Override
	public void tick() {
		if (++this.age % REDRAW_TICKS == 0) {
			this.drawnZoom = -1;
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		int mx = left() + MAP_X, my = top() + MAP_Y;
		if (PowerScreen.over((int)x, (int)y, mx, my, MAP_SIZE, MAP_SIZE)) {
			int i = Mth.clamp(indexOf(this.zoom) - (int)Math.signum(scrollY), 0, ZOOMS.length - 1);
			this.zoom = ZOOMS[i];
			lastZoom = this.zoom;
		} else {
			this.scroll = Math.max(0, this.scroll - (int)Math.signum(scrollY));
		}
		return true;
	}

	private static int indexOf(int zoom) {
		for (int i = 0; i < ZOOMS.length; i++) {
			if (ZOOMS[i] == zoom) {
				return i;
			}
		}
		return 1;
	}

	/** Every waypoint in the world you stand in, nearest first. */
	private List<Pin> pins(LocalPlayer player) {
		List<Pin> pins = new ArrayList<>();
		String here = player.level().dimension().identifier().toString();
		List<ArsenalPlaces.Place> marks = ArsenalPlaces.marks(player);
		for (int i = 0; i < marks.size(); i++) {
			ArsenalPlaces.Place p = marks.get(i);
			if (p.dimension().equals(here)) {
				pins.add(new Pin(Component.translatable("menu.jaymospowers.map.mark", i + 1), BlockPos.containing(p.x(), p.y(), p.z()), COLOR_MARK));
			}
		}
		for (Map.Entry<Component, BlockPos> e : MenuTabs.pins()) {
			TextColor color = e.getKey().getStyle().getColor();
			pins.add(new Pin(e.getKey(), e.getValue(), color == null ? PowerScreen.COLOR_GOLD : 0xFF000000 | color.getValue()));
		}
		BlockPos at = player.blockPosition();
		pins.sort(Comparator.comparingDouble(p -> p.pos().distSqr(at)));
		return pins;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		LocalPlayer player = this.minecraft.player;
		int x = left(), y = top();
		CharacterScreen.frame(graphics, x, y, W, H);
		graphics.text(this.font, this.title, x + 8, y + 6, PowerScreen.COLOR_TEXT, false);
		if (player == null || !(player.level() instanceof ClientLevel level)) {
			return;
		}
		int mx = x + MAP_X, my = y + MAP_Y;
		CharacterScreen.recess(graphics, mx - 1, my - 1, MAP_SIZE + 2, MAP_SIZE + 2);
		int n = MAP_SIZE / CELL;
		if (this.drawnZoom != this.zoom || this.cells == null) {
			this.drawnX = player.getBlockX();
			this.drawnZ = player.getBlockZ();
			this.drawnZoom = this.zoom;
			this.cells = draw(level, this.drawnX, this.drawnZ, this.zoom, n);
		}
		for (int row = 0; row < n; row++) {
			for (int col = 0; col < n; col++) {
				int px = mx + col * CELL, pz = my + row * CELL;
				graphics.fill(px, pz, px + CELL, pz + CELL, this.cells[row * n + col]);
			}
		}
		double perPixel = this.zoom / (double)CELL, half = MAP_SIZE / 2.0;
		List<Pin> pins = pins(player);
		List<Component> hover = new ArrayList<>();
		for (Pin p : pins) {
			int[] at = marker(mx, my, p.pos(), perPixel, half);
			graphics.fill(at[0] - 3, at[1] - 3, at[0] + 3, at[1] + 3, 0xFF000000);
			graphics.fill(at[0] - 2, at[1] - 2, at[0] + 2, at[1] + 2, p.color());
			if (Math.abs(mouseX - at[0]) <= 3 && Math.abs(mouseY - at[1]) <= 3) {
				hover.add(p.name());
			}
		}
		// you: a white arrowhead, the way you face
		int[] me = marker(mx, my, player.blockPosition(), perPixel, half);
		double yaw = Math.toRadians(player.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		for (int pass = 0; pass < 2; pass++) {
			int r = pass == 0 ? 2 : 1;
			for (int i = -1; i <= 3; i++) {
				int px = me[0] + (int)Math.round(fx * i), pz = me[1] + (int)Math.round(fz * i);
				graphics.fill(px - r, pz - r, px + r, pz + r, pass == 0 ? 0xFF000000 : 0xFFFFFFFF);
			}
		}
		// the list
		int lx = mx + MAP_SIZE + 8, lw = x + W - 8 - lx, ly = my;
		UiStyle.line(graphics, this.font, Component.translatable("menu.jaymospowers.map.waypoints"), lx, ly, lw, PowerScreen.COLOR_GOLD);
		ly += 12;
		int listBottom = y + H - (xaeroKey() != null ? 30 : 8);
		int rows = Math.max(1, (listBottom - ly) / LIST_ROW);
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, pins.size() - rows));
		if (pins.isEmpty()) {
			UiStyle.wrapped(graphics, this.font, Component.translatable("menu.jaymospowers.map.none"), lx, ly, lw, 6, UiStyle.LINE, PowerScreen.COLOR_DIM, false);
		}
		BlockPos here = player.blockPosition();
		for (int i = 0; i < rows && i + this.scroll < pins.size(); i++) {
			Pin p = pins.get(i + this.scroll);
			int ry = ly + i * LIST_ROW;
			boolean over = PowerScreen.over(mouseX, mouseY, lx, ry - 1, lw, LIST_ROW);
			UiStyle.row(graphics, lx - 2, ry - 2, lx + lw, ry + LIST_ROW - 2, i, over);
			graphics.fill(lx, ry + 1, lx + 5, ry + 6, p.color());
			Component far = distance(Math.sqrt(p.pos().distSqr(here)));
			int farW = this.font.width(far);
			UiStyle.line(graphics, this.font, p.name(), lx + 8, ry, lw - farW - 12, PowerScreen.COLOR_TEXT);
			graphics.text(this.font, far, lx + lw - farW - 1, ry, PowerScreen.COLOR_DIM, false);
			if (over) {
				hover.add(p.name());
				hover.add(Component.translatable("menu.jaymospowers.map.at", p.pos().getX(), p.pos().getY(), p.pos().getZ()).withColor(PowerScreen.COLOR_DIM));
			}
		}
		if (pins.size() > rows) {
			UiStyle.scrollbar(graphics, x + W - 6, ly - 2, rows * LIST_ROW, rows, pins.size(), this.scroll);
		}
		graphics.text(this.font, Component.translatable("menu.jaymospowers.map.zoom", this.zoom), mx + 2, my + MAP_SIZE - 9, PowerScreen.COLOR_DIM, true);
		if (!hover.isEmpty()) {
			graphics.setComponentTooltipForNextFrame(this.font, hover, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private static Component distance(double blocks) {
		return blocks >= 1000.0 ? Component.translatable("menu.jaymospowers.map.km", String.format(java.util.Locale.ROOT, "%.1f", blocks / 1000.0))
			: Component.translatable("menu.jaymospowers.map.m", Math.round(blocks));
	}

	/** Where {@code pos} goes on the map (screen px), held to its edge when it's off it. */
	private int[] marker(int x, int y, BlockPos pos, double perPixel, double half) {
		double px = (pos.getX() - this.drawnX) / perPixel + half, pz = (pos.getZ() - this.drawnZ) / perPixel + half;
		return new int[] {x + (int)Mth.clamp(px, 3, MAP_SIZE - 3), y + (int)Mth.clamp(pz, 3, MAP_SIZE - 3)};
	}

	/** The land about ({@code cx}, {@code cz}): one sample per cell, shaded by the slope from the north. */
	private static int[] draw(ClientLevel level, int cx, int cz, int scale, int n) {
		int[] out = new int[n * n];
		int[] heights = new int[n];
		for (int row = -1; row < n; row++) {
			int z = cz + (row - n / 2) * scale;
			for (int col = 0; col < n; col++) {
				int x = cx + (col - n / 2) * scale;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					if (row >= 0) {
						out[row * n + col] = COLOR_BLANK;
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
					out[row * n + col] = COLOR_BLANK;
					continue;
				}
				MapColor.Brightness light = above == Integer.MIN_VALUE || color == MapColor.WATER ? MapColor.Brightness.NORMAL
					: top > above ? MapColor.Brightness.HIGH : top < above ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				out[row * n + col] = 0xFF000000 | color.calculateARGBColor(light);
			}
		}
		return out;
	}
}
