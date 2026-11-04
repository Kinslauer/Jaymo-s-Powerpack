package com.jaymospowers.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * (HUD rework) Andy: "rework the hud so nothing is clashing, there are alot of new elements now, so everything needs to
 * feel coherent". Every HUD piece of Powers, Kingdoms and Kitchen that isn't a full-screen wash asks one of these zones
 * where to draw, instead of picking its own spot:
 * <ul>
 * <li>{@link #DOCK_RIGHT}: right of the hotbar, rows stacked up from the bottom (power chips, mounts, the Rig panel).</li>
 * <li>{@link #DOCK_LEFT}: left of the hotbar, rows stacked up, right-aligned to it (stomach, symbiote arm, the body row:
 * stance, wounds doll, body heat).</li>
 * <li>{@link #FOCUS}: centred, above the action-bar line, stacked up (struggle bars, the forms belt, the spirit line).</li>
 * <li>{@link #ABOVE}: centred over the crosshair, stacked up (charge bars, the awareness eye).</li>
 * <li>{@link #BELOW}: centred under the crosshair (and under the attack indicator when it shows), stacked down (guard,
 * finisher, roll pips, horse tag, smithing).</li>
 * <li>{@link #INSPECT}: right of the crosshair, past the bars, stacked down (combo count, lens, scouter).</li>
 * <li>{@link #RAIL}: top-left, under Xaero's minimap, stacked down (power badge, kin sense, party).</li>
 * </ul>
 * A piece calls {@link #place} each frame it draws, with its row (lower rows sit nearer the zone's anchor) and size, and
 * gets back its top-left corner; pieces in the same row sit side by side. Positions come from every piece seen in the last
 * {@link #FRESH_NANOS}, so the order the mods draw in doesn't matter and nothing has to know when anything else shows.
 * <p>
 * The three mods don't depend on each other, so each has its own copy of this class (Kingdoms {@code client.hud.HudZones},
 * Kitchen {@code client.hud.HudZones}); they share one table through Fabric Loader's object share. Keep the copies the same.
 * <p>
 * While a film plays (an ultimate, a beam or spirit clash, a handshake, a curtain) {@link #hideFor} makes {@link #place}
 * answer {@code null}: the piece doesn't draw.
 */
public final class HudZones {
	public static final String DOCK_RIGHT = "dock_right", DOCK_LEFT = "dock_left", FOCUS = "focus", ABOVE = "above", BELOW = "below",
		INSPECT = "inspect", RAIL = "rail";
	/** Gap between rows, and between pieces in a row (gui pixels). */
	public static final int GAP = 2;
	/** The right dock starts this far right of the screen's middle: past the hotbar (91) and the off-hand slot / hotbar attack indicator. */
	public static final int DOCK_RIGHT_X = 91 + 30;
	/** The left dock ends this far left of the middle: past the hotbar and the off-hand slot. */
	public static final int DOCK_LEFT_X = 91 + 31;
	/** A dock narrower than this (gui pixels between the hotbar and the screen edge) moves up over the hotbar instead. */
	public static final int DOCK_MIN_WIDTH = 150;
	/** The focus band's bottom: just over vanilla's action-bar line (drawn at height - 68, text up to height - 72). */
	public static final int FOCUS_BOTTOM = 76;
	/** On a narrow screen the docks sit over the hotbar, their bottom here (from the screen's bottom). */
	public static final int NARROW_DOCK_BOTTOM = 76;
	/** Over the crosshair: the lowest row's bottom is this far above the middle. */
	public static final int ABOVE_BOTTOM = 12;
	/** Under the crosshair: the first row's top this far below the middle, or {@link #BELOW_TOP_INDICATOR} when vanilla's attack indicator shows there. */
	public static final int BELOW_TOP = 10, BELOW_TOP_INDICATOR = 20;
	/** Right of the crosshair: left edge and top, from the middle (clear of the widest bars over and under the crosshair). */
	public static final int INSPECT_X = 48, INSPECT_TOP = -4;
	/** The top-left rail's left edge, and its top: under Xaero's minimap (and its coordinates line) when that's loaded. */
	public static final int RAIL_X = 4, RAIL_TOP = 4, RAIL_TOP_UNDER_MINIMAP = 112;
	/** A piece not seen this long is gone (ns). */
	static final long FRESH_NANOS = 150_000_000L;

	private static final String SHARE_TABLE = "jaymos:hud_zones", SHARE_HIDE = "jaymos:hud_hidden_until";
	/** zone -> piece id -> {row, width, height, last seen (ns)}. */
	private static @Nullable Map<String, Map<String, long[]>> table;
	private static @Nullable AtomicLong hiddenUntil;
	private static int railTop = -1;

	private HudZones() {
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Map<String, long[]>> table() {
		if (table == null) {
			Object shared = FabricLoader.getInstance().getObjectShare().get(SHARE_TABLE);
			if (shared == null) {
				FabricLoader.getInstance().getObjectShare().putIfAbsent(SHARE_TABLE, new ConcurrentHashMap<String, Map<String, long[]>>());
				shared = FabricLoader.getInstance().getObjectShare().get(SHARE_TABLE);
			}
			table = (Map<String, Map<String, long[]>>)shared;
		}
		return table;
	}

	private static AtomicLong hiddenClock() {
		if (hiddenUntil == null) {
			Object shared = FabricLoader.getInstance().getObjectShare().get(SHARE_HIDE);
			if (shared == null) {
				FabricLoader.getInstance().getObjectShare().putIfAbsent(SHARE_HIDE, new AtomicLong());
				shared = FabricLoader.getInstance().getObjectShare().get(SHARE_HIDE);
			}
			hiddenUntil = (AtomicLong)shared;
		}
		return hiddenUntil;
	}

	/** A film is up: the zoned HUD stays hidden for this long (call it every frame the film draws). */
	public static void hideFor(long millis) {
		long until = System.nanoTime() + millis * 1_000_000L;
		AtomicLong hidden = hiddenClock();
		hidden.accumulateAndGet(until, Math::max);
	}

	/** Whether a film is hiding the zoned HUD. */
	public static boolean hidden() {
		return System.nanoTime() - hiddenClock().get() < 0L;
	}

	/**
	 * Where this piece draws this frame: its top-left corner {x, y} in gui pixels, or {@code null} while a film hides the
	 * HUD. {@code row}: lower rows sit nearer the zone's anchor; the same row puts pieces side by side (ordered by id).
	 */
	public static int @Nullable [] place(String zone, String id, int row, int width, int height) {
		long now = System.nanoTime();
		Map<String, long[]> pieces = table().computeIfAbsent(zone, z -> new ConcurrentHashMap<>());
		pieces.put(id, new long[] {row, width, height, now});
		if (hidden()) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		int w = minecraft.getWindow().getGuiScaledWidth(), h = minecraft.getWindow().getGuiScaledHeight();
		return locate(zone, id, w, h, now);
	}

	/** Forget a piece at once (it stopped drawing and its slot shouldn't linger). Optional: stale pieces go by themselves. */
	public static void clear(String zone, String id) {
		Map<String, long[]> pieces = table().get(zone);
		if (pieces != null) {
			pieces.remove(id);
		}
	}

	/** The rows of a zone: row -> pieces in it (id order), only those seen lately. */
	private static TreeMap<Long, List<Map.Entry<String, long[]>>> rows(String zone, long now) {
		TreeMap<Long, List<Map.Entry<String, long[]>>> rows = new TreeMap<>();
		Map<String, long[]> pieces = table().get(zone);
		if (pieces == null) {
			return rows;
		}
		for (Map.Entry<String, long[]> piece : pieces.entrySet()) {
			if (now - piece.getValue()[3] <= FRESH_NANOS) {
				rows.computeIfAbsent(piece.getValue()[0], r -> new ArrayList<>()).add(piece);
			}
		}
		for (List<Map.Entry<String, long[]>> row : rows.values()) {
			row.sort(Map.Entry.comparingByKey());
		}
		return rows;
	}

	/** How tall a zone's stack is now (0: empty). */
	public static int stackHeight(String zone) {
		int total = 0;
		for (List<Map.Entry<String, long[]>> row : rows(zone, System.nanoTime()).values()) {
			total += rowHeight(row) + GAP;
		}
		return Math.max(0, total - GAP);
	}

	private static int rowHeight(List<Map.Entry<String, long[]>> row) {
		int height = 0;
		for (Map.Entry<String, long[]> piece : row) {
			height = Math.max(height, (int)piece.getValue()[2]);
		}
		return height;
	}

	private static int rowWidth(List<Map.Entry<String, long[]>> row) {
		int width = 0;
		for (Map.Entry<String, long[]> piece : row) {
			width += (int)piece.getValue()[1] + GAP;
		}
		return Math.max(0, width - GAP);
	}

	/** Whether the docks sit beside the hotbar (true) or, on a narrow screen, over it. */
	public static boolean docksBeside(int guiWidth) {
		return guiWidth - 4 - (guiWidth / 2 + DOCK_RIGHT_X) >= DOCK_MIN_WIDTH;
	}

	private static int @Nullable [] locate(String zone, String id, int w, int h, long now) {
		int cx = w / 2, cy = h / 2;
		boolean beside = docksBeside(w);
		// the anchor, and which way rows stack (+1 down, -1 up) and pieces in a row run (0 centred, +1 right, -1 left)
		int anchorX, anchorY, stack, run;
		switch (zone) {
			case DOCK_RIGHT -> {
				anchorX = beside ? cx + DOCK_RIGHT_X : cx + 2;
				anchorY = beside ? h - 1 : h - NARROW_DOCK_BOTTOM;
				stack = -1;
				run = 1;
			}
			case DOCK_LEFT -> {
				anchorX = beside ? cx - DOCK_LEFT_X : cx - 2;
				anchorY = beside ? h - 1 : h - NARROW_DOCK_BOTTOM;
				stack = -1;
				run = -1;
			}
			case FOCUS -> {
				anchorX = cx;
				anchorY = h - FOCUS_BOTTOM;
				if (!beside) {
					// (narrow: the docks are over the hotbar, so the band goes over them)
					int docks = Math.max(stackHeight(DOCK_LEFT), stackHeight(DOCK_RIGHT));
					if (docks > 0) {
						anchorY = h - NARROW_DOCK_BOTTOM - docks - GAP;
					}
				}
				stack = -1;
				run = 0;
			}
			case ABOVE -> {
				anchorX = cx;
				anchorY = cy - ABOVE_BOTTOM;
				stack = -1;
				run = 0;
			}
			case BELOW -> {
				anchorX = cx;
				anchorY = cy + (indicatorUnderCrosshair() ? BELOW_TOP_INDICATOR : BELOW_TOP);
				stack = 1;
				run = 0;
			}
			case INSPECT -> {
				anchorX = cx + INSPECT_X;
				anchorY = cy + INSPECT_TOP;
				stack = 1;
				run = 1;
			}
			case RAIL -> {
				anchorX = RAIL_X;
				anchorY = railTop();
				stack = 1;
				run = 1;
			}
			default -> {
				return null;
			}
		}
		int y = anchorY;
		for (List<Map.Entry<String, long[]>> row : rows(zone, now).values()) {
			int rowH = rowHeight(row);
			int top = stack < 0 ? y - rowH : y;
			int x = run == 0 ? anchorX - rowWidth(row) / 2 : anchorX;
			for (Map.Entry<String, long[]> piece : row) {
				int pw = (int)piece.getValue()[1], ph = (int)piece.getValue()[2];
				if (piece.getKey().equals(id)) {
					int px = run < 0 ? x - pw : x;
					// (shorter pieces sit on the row's edge nearest the anchor)
					int py = stack < 0 ? top + rowH - ph : top;
					return new int[] {px, py};
				}
				x += run < 0 ? -(pw + GAP) : pw + GAP;
			}
			y += stack < 0 ? -(rowH + GAP) : rowH + GAP;
		}
		return null;
	}

	/** Whether vanilla's attack indicator is showing under the crosshair this frame. */
	private static boolean indicatorUnderCrosshair() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR
			&& minecraft.player.getAttackStrengthScale(0.0F) < 1.0F;
	}

	private static int railTop() {
		if (railTop < 0) {
			railTop = FabricLoader.getInstance().isModLoaded("xaerominimap") ? RAIL_TOP_UNDER_MINIMAP : RAIL_TOP;
		}
		return railTop;
	}
}
