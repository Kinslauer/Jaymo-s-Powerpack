package com.jaymoskingdoms.client;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ground our own maps draw (the journal's, the Last Bell's) in a One Dimension world (Kingdoms' {@code Stacked}): the
 * stacked Overworld level's heightmaps see the whole column, so a column's top block is an Aether island or the Cloud
 * Sea's overcast, not the land you stand on (Andy: "i think the map is showing the aether?"). Here a column is read from
 * the top of the band the player stands in down to its floor, so the Overworld is mapped under the Aether and the Aether
 * once you're up in it. The band floors come from CustomChunks' stack layout (synced to the client), by reflection, the
 * same way as Jaymo's Powers' {@code AetherVeil} does for Xaero's Minimap. In an ordinary world nothing changes.
 */
public final class MapBand {
	private static final boolean STACKS = FabricLoader.getInstance().isModLoaded("customchunks");
	private static boolean reflected;
	private static Method layoutOf, layers, layerMin, layerShift;
	/** The level the floors were read for, every band's floor in it (lowest first) and each band's shift, same order. */
	private static ClientLevel readFor;
	private static int[] floors = new int[0], shifts = new int[0];

	/** The band at one height: its floor, its top (exclusive) and its shift (what its own heights are moved by). */
	public record Band(int bottom, int top, int shift) {
		/** The whole level, unshifted: an ordinary world. */
		static Band whole(ClientLevel level) {
			return new Band(level.getMinY(), level.getMaxY() + 1, 0);
		}
	}

	private MapBand() {
	}

	/** The band of this level that holds height y (the whole level when it isn't stacked). */
	public static Band at(ClientLevel level, double y) {
		read(level);
		int i = -1;
		for (int k = 0; k < floors.length; k++) {
			if (floors[k] <= y) {
				i = k;
			}
		}
		if (i < 0) {
			return Band.whole(level);
		}
		return new Band(floors[i], i + 1 < floors.length ? floors[i + 1] : level.getMaxY() + 1, shifts[i]);
	}

	/**
	 * The top block of (x, z) inside the band (as {@code getHeight(WORLD_SURFACE) - 1} answers in an ordinary world), or
	 * below the band's floor when the band is empty there. The chunk must be loaded.
	 */
	public static int surface(ClientLevel level, Band band, int x, int z) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		if (top < band.top()) {
			return Math.max(top, band.bottom() - 1);
		}
		// something above the band (another band's ground): walk down from the band's top, skipping empty sections
		LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
		LevelChunkSection[] sections = chunk.getSections();
		int y = band.top() - 1;
		while (y >= band.bottom()) {
			int index = chunk.getSectionIndex(y);
			if (index < 0 || index >= sections.length) {
				return band.bottom() - 1;
			}
			LevelChunkSection section = sections[index];
			if (section.hasOnlyAir()) {
				y = (y & ~15) - 1;
				continue;
			}
			if (!section.getBlockState(x & 15, y & 15, z & 15).isAir()) {
				return y;
			}
			y--;
		}
		return band.bottom() - 1;
	}

	private static void read(ClientLevel level) {
		if (!STACKS || level == readFor) {
			return;
		}
		floors = new int[0];
		shifts = new int[0];
		if (!api()) {
			return;
		}
		try {
			Object layout = layoutOf.invoke(null, level);
			if (layout == null) {
				return; // not synced yet (or not stacked): asked again next time
			}
			List<?> all = (List<?>) layers.invoke(layout);
			long[] bands = new long[all.size()];
			for (int k = 0; k < bands.length; k++) {
				Object layer = all.get(k);
				int min = (Integer) layerMin.invoke(layer), shift = (Integer) layerShift.invoke(layer);
				bands[k] = ((long) min << 32) | (shift & 0xFFFFFFFFL);
			}
			Arrays.sort(bands);
			floors = new int[bands.length];
			shifts = new int[bands.length];
			for (int k = 0; k < bands.length; k++) {
				floors[k] = (int) (bands[k] >> 32);
				shifts[k] = (int) bands[k];
			}
			if (floors.length > 0) {
				readFor = level;
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			floors = new int[0];
			shifts = new int[0];
		}
	}

	private static boolean api() {
		if (!reflected) {
			reflected = true;
			try {
				Class<?> layout = Class.forName("codx.customchunks.StackLayout");
				Class<?> layer = Class.forName("codx.customchunks.StackLayout$Layer");
				layoutOf = layout.getMethod("of", LevelHeightAccessor.class);
				layers = layout.getMethod("layers");
				layerMin = layer.getMethod("minY");
				layerShift = layer.getMethod("shift");
			} catch (ReflectiveOperationException | LinkageError e) {
				layoutOf = null;
			}
		}
		return layoutOf != null;
	}
}
