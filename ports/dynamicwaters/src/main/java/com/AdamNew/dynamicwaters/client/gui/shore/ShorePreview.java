package com.AdamNew.dynamicwaters.client.gui.shore;

import com.AdamNew.dynamicwaters.shore.ShoreSkin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

final class ShorePreview {
    static final int RIVER = 0, STREAM = 1, LAKE = 2;

    static final String[] MODES = { "river", "stream", "lake" };

    private static final int[] GROUND = { 10, 10, 10, 9, 7, 3, 2, 1, 2, 3, 4, 5, 5, 6, 6, 7, 8 };

    private static final boolean[] BAR = {
            false, false, false, false, false, false, false, false, false, false, true, true, true, false, false, false, false };

    private static final int UNTOUCHED = 2;
    private static final int WATER = 5;

    private static final int MIDDLE = 7;
    static final int COLS = GROUND.length, ROWS = 12;

    private ShorePreview() {
    }

    static void render(GuiGraphicsExtractor g, RiverbankPaletteDraft d, long seed, int mode, int x, int y, int w, int h) {
        final int s = Math.max(4, Math.min(w / COLS, h / ROWS));
        final int ox = x + (w - s * COLS) / 2, oy = y + (h - s * ROWS) / 2;

        g.fillGradient(ox, oy, ox + s * COLS, oy + s * ROWS, 0xFF8FC3F0, 0xFFBFDDF7);

        final int row = (int) (seed * 7919L);
        for (int c = 0; c < COLS; c++) {
            final int sy = GROUND[c];
            final int exposure = exposure(c);
            final boolean touched = c >= UNTOUCHED && c < COLS - UNTOUCHED;
            final String top = touched && d.enabled ? skin(d, c, sy, row, mode) : null;

            final int coat = top != null && !BlockSwatch.loose(top) ? Mth.clamp(exposure, 2, 8) : 0;
            for (int yy = 0; yy < ROWS; yy++) {
                final int px = ox + c * s, py = oy + (ROWS - 1 - yy) * s;
                if (yy > sy) {
                    if (yy <= WATER) g.fill(px, py, px + s, py + s, yy == WATER ? 0xC04A86E8 : 0xD03567C9);
                    continue;
                }
                final String id;
                if (yy == sy) id = top != null ? top : naturalTop(sy);
                else if (yy >= sy - coat) id = top;
                else id = yy >= sy - 2 ? "minecraft:dirt" : "minecraft:stone";
                BlockSwatch.draw(g, id, px, py, s);
                if (yy == sy && sy < WATER) {
                    g.fill(px, py, px + s, py + s, 0x303567C9);
                }
            }
        }
        g.outline(ox - 1, oy - 1, s * COLS + 2, s * ROWS + 2, 0xFF000000);
    }

    private static String naturalTop(int sy) {
        return sy >= WATER ? "minecraft:grass_block" : "minecraft:dirt";
    }

    private static int exposure(int c) {
        int e = 0;
        if (c > 0) e = Math.max(e, GROUND[c] - GROUND[c - 1]);
        if (c < COLS - 1) e = Math.max(e, GROUND[c] - GROUND[c + 1]);
        return e;
    }

    private static String skin(RiverbankPaletteDraft d, int c, int sy, int row, int mode) {
        final boolean bar = BAR[c] && mode != LAKE;
        String id = null;
        if (sy < WATER) {
            final int depth = WATER - sy;

            if (depth <= 1 && !bar) id = laid(d, RiverbankPaletteDraft.SHORE, c, row);
            if (id == null) {
                double run = switch (mode) {
                    case STREAM -> ShoreSkin.streamRun(depth);
                    case LAKE -> ShoreSkin.lakeRun(depth);
                    default -> ShoreSkin.lowlandRun(Math.min(1.0, Math.abs(c - MIDDLE) / 2.5), false);
                };
                if (bar) run *= 0.5;
                id = laid(d, ShoreSkin.sortDraw(c, row, d.patchSize) < run ? RiverbankPaletteDraft.RAPIDS : RiverbankPaletteDraft.SILT, c, row);
            }
        } else if (bar) {
            id = laid(d, RiverbankPaletteDraft.BARS, c, row);
        } else {
            id = laid(d, sy - WATER <= d.shoreHeight + ShoreSkin.shoreWaver(c, row) ? RiverbankPaletteDraft.SHORE : RiverbankPaletteDraft.SLOPE, c, row);
        }
        return id;
    }

    private static String laid(RiverbankPaletteDraft d, int place, int c, int row) {
        final String id = d.pick(place, c, row, ShoreSkin.spotSize(d.patchSize, place), ShoreSkin.placeSalt(place));
        return id == null || RiverbankPaletteDraft.KEEP.equals(id) || !BlockSwatch.exists(id) ? null : id;
    }
}
