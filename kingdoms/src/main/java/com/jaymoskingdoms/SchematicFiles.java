package com.jaymoskingdoms;

import com.mojang.datafixers.DataFixer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.datafix.DataFixTypes;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.488) Reads the schematic files people share (sites like Structura offer every build in all three) into the shape a
 * structure block saves, so {@link Designs} and {@link Blueprint#fromTemplate} need only know that one:
 * <ul>
 * <li><b>.nbt</b>: a structure block's own file (as is).</li>
 * <li><b>.schem</b>: the Sponge schematic, versions 1 to 3 (WorldEdit, Amulet, most sites).</li>
 * <li><b>.litematic</b>: Litematica's, every region of it.</li>
 * </ul>
 * Files made for an older game are brought up to date by the game's own data fixer (renamed blocks and the like);
 * old MCEdit {@code .schematic} files (numeric block ids, before 1.13) can't be read. Only blocks are taken: no block
 * entities (a chest comes empty, a sign blank) and no entities. Written from the formats' public descriptions.
 */
public final class SchematicFiles {
	/** The extensions a design file may have, the game's own first. */
	public static final List<String> EXTENSIONS = List.of(".nbt", ".schem", ".litematic");
	/**
	 * The most block positions (air too) a file may span before it's refused unread: the largest box a design may fill
	 * ({@link Designs#PLAN_SIDE} square, {@link Designs#PLAN_HEIGHT} high), so an oversized upload isn't built in full first.
	 */
	static final long MAX_VOLUME = (long)Designs.PLAN_SIDE * Designs.PLAN_SIDE * Designs.PLAN_HEIGHT;
	/** The longest side a Litematica region may claim (its sizes are ints, so their product could overflow). */
	static final int MAX_REGION_SIDE = 4096;
	/** Sponge version 1 files carry no data version: taken to be from 1.13.2. */
	static final int SPONGE_V1_DATA_VERSION = 1631;

	private SchematicFiles() {
	}

	/** The extension of {@code fileName} if it's one we read (lower case), else null. */
	public static @Nullable String extension(String fileName) {
		String lower = fileName.toLowerCase(java.util.Locale.ROOT);
		for (String ext : EXTENSIONS) {
			if (lower.endsWith(ext)) {
				return ext;
			}
		}
		return null;
	}

	/**
	 * {@code root} (a file's whole tag, whichever format) as a structure template's tag, brought up to this game's data
	 * version; null if it's none of the formats, or too big. The template keeps an {@code author} if the file named one.
	 */
	public static @Nullable CompoundTag toTemplate(CompoundTag root, @Nullable DataFixer fixer) {
		CompoundTag template;
		if (root.getCompound("Regions").isPresent()) {
			template = litematic(root);
		} else if (root.getCompound("Schematic").isPresent()) {
			template = sponge(root.getCompoundOrEmpty("Schematic"));
		} else if (root.contains("Palette") || root.getCompound("Blocks").isPresent() || root.getByteArray("BlockData").isPresent()) {
			template = sponge(root);
		} else if (root.contains("blocks") && (root.contains("palette") || root.contains("palettes"))) {
			template = root.copy();
		} else {
			return null;
		}
		if (template == null) {
			return null;
		}
		int current = SharedConstants.getCurrentVersion().dataVersion().version();
		int version = NbtUtils.getDataVersion(template, 500);
		if (fixer != null && version < current) {
			String author = template.getStringOr("author", "");
			template = DataFixTypes.STRUCTURE.updateToCurrentVersion(fixer, template, version);
			if (!author.isEmpty()) {
				template.putString("author", author);
			}
		}
		normalise(template.getListOrEmpty("palette"));
		ListTag palettes = template.getListOrEmpty("palettes");
		for (int i = 0; i < palettes.size(); i++) {
			normalise(palettes.getListOrEmpty(i));
		}
		return template;
	}

	/**
	 * 26.3 names a palette entry's block {@code id} and its {@code properties}; the schematic formats (and tools that
	 * write structure files themselves, even marked for this version) still use {@code Name} and {@code Properties}.
	 */
	private static void normalise(ListTag palette) {
		for (int i = 0; i < palette.size(); i++) {
			CompoundTag e = palette.getCompoundOrEmpty(i);
			if (!e.contains("id") && e.contains("Name")) {
				e.putString("id", e.getStringOr("Name", "minecraft:air"));
				e.remove("Name");
				e.getCompound("Properties").ifPresent(props -> {
					e.put("properties", props);
					e.remove("Properties");
				});
			}
		}
	}

	// ---- Sponge (.schem) --------------------------------------------------------------------------------------------

	private static @Nullable CompoundTag sponge(CompoundTag s) {
		int version = s.getIntOr("Version", 2);
		int w = s.getShortOr("Width", (short)0) & 0xFFFF, h = s.getShortOr("Height", (short)0) & 0xFFFF, l = s.getShortOr("Length", (short)0) & 0xFFFF;
		if (w == 0 || h == 0 || l == 0 || (long)w * h * l > MAX_VOLUME) {
			return null;
		}
		// v3 keeps its blocks in a compound of their own; v1 and v2 at the top
		CompoundTag blocks = version >= 3 ? s.getCompoundOrEmpty("Blocks") : s;
		CompoundTag palette = blocks.getCompoundOrEmpty("Palette");
		byte[] data = (version >= 3 ? blocks.getByteArray("Data") : blocks.getByteArray("BlockData")).orElse(null);
		if (palette.isEmpty() || data == null) {
			return null;
		}
		Map<Integer, CompoundTag> states = new HashMap<>();
		for (String key : palette.keySet()) {
			states.put(palette.getIntOr(key, -1), stateTag(key));
		}
		Builder out = new Builder();
		int i = 0, index = 0;
		long volume = (long)w * h * l;
		while (i < data.length && index < volume) {
			// each block's palette index is a var-int
			int value = 0, shift = 0;
			byte b;
			do {
				if (i >= data.length) {
					return null;
				}
				b = data[i++];
				value |= (b & 0x7F) << shift;
				shift += 7;
			} while ((b & 0x80) != 0 && shift < 35);
			int x = index % w, z = index / w % l, y = index / (w * l);
			CompoundTag state = states.get(value);
			if (state != null) {
				out.add(x, y, z, state);
			}
			index++;
		}
		out.size(w, h, l);
		CompoundTag t = out.build(s.getIntOr("DataVersion", SPONGE_V1_DATA_VERSION));
		String author = s.getCompoundOrEmpty("Metadata").getStringOr("Author", "");
		if (!author.isEmpty()) {
			t.putString("author", author);
		}
		return t;
	}

	/** A block state written {@code minecraft:oak_stairs[facing=north,half=top]} as a template palette entry. */
	static CompoundTag stateTag(String text) {
		CompoundTag tag = new CompoundTag();
		int open = text.indexOf('[');
		String name = open < 0 ? text : text.substring(0, open);
		tag.putString("Name", name.trim());
		if (open >= 0 && text.endsWith("]")) {
			CompoundTag props = new CompoundTag();
			for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
				int eq = pair.indexOf('=');
				if (eq > 0) {
					props.putString(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
				}
			}
			if (!props.isEmpty()) {
				tag.put("Properties", props);
			}
		}
		return tag;
	}

	// ---- Litematica (.litematic) ------------------------------------------------------------------------------------

	private static @Nullable CompoundTag litematic(CompoundTag root) {
		CompoundTag regions = root.getCompoundOrEmpty("Regions");
		Builder out = new Builder();
		// first the regions' bounds (each may run either way from its position), so all sit from 0
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE,
			maxZ = Integer.MIN_VALUE;
		long volume = 0;
		for (String name : regions.keySet()) {
			CompoundTag r = regions.getCompoundOrEmpty(name);
			int[] lo = regionMin(r), size = regionSize(r);
			if (size[0] < 0 || size[1] < 0 || size[2] < 0 || size[0] > MAX_REGION_SIDE || size[1] > MAX_REGION_SIDE || size[2] > MAX_REGION_SIDE) {
				return null;
			}
			minX = Math.min(minX, lo[0]);
			minY = Math.min(minY, lo[1]);
			minZ = Math.min(minZ, lo[2]);
			maxX = Math.max(maxX, lo[0] + size[0] - 1);
			maxY = Math.max(maxY, lo[1] + size[1] - 1);
			maxZ = Math.max(maxZ, lo[2] + size[2] - 1);
			volume += (long)size[0] * size[1] * size[2];
		}
		if (volume == 0 || volume > MAX_VOLUME) {
			return null;
		}
		for (String name : regions.keySet()) {
			CompoundTag r = regions.getCompoundOrEmpty(name);
			int[] lo = regionMin(r), size = regionSize(r);
			ListTag palette = r.getListOrEmpty("BlockStatePalette");
			long[] bits = r.getLongArray("BlockStates").orElse(null);
			if (palette.isEmpty() || bits == null) {
				continue;
			}
			int perEntry = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
			long mask = (1L << perEntry) - 1L;
			int sx = size[0], sy = size[1], sz = size[2];
			for (int y = 0; y < sy; y++) {
				for (int z = 0; z < sz; z++) {
					for (int x = 0; x < sx; x++) {
						long index = ((long)y * sz + z) * sx + x;
						long start = index * perEntry;
						int word = (int)(start >> 6), endWord = (int)((index + 1) * perEntry - 1 >> 6), offset = (int)(start & 63);
						if (endWord >= bits.length) {
							continue;
						}
						long value = word == endWord ? bits[word] >>> offset & mask : (bits[word] >>> offset | bits[endWord] << 64 - offset) & mask;
						if (value < palette.size()) {
							out.add(lo[0] - minX + x, lo[1] - minY + y, lo[2] - minZ + z, palette.getCompoundOrEmpty((int)value));
						}
					}
				}
			}
		}
		out.size(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
		CompoundTag t = out.build(root.getIntOr("MinecraftDataVersion", SharedConstants.getCurrentVersion().dataVersion().version()));
		String author = root.getCompoundOrEmpty("Metadata").getStringOr("Author", "");
		if (!author.isEmpty()) {
			t.putString("author", author);
		}
		return t;
	}

	private static int[] regionSize(CompoundTag r) {
		CompoundTag s = r.getCompoundOrEmpty("Size");
		return new int[] {Math.abs(s.getIntOr("x", 0)), Math.abs(s.getIntOr("y", 0)), Math.abs(s.getIntOr("z", 0))};
	}

	/** A region's lowest corner: its position, less its size where the size runs negative. */
	private static int[] regionMin(CompoundTag r) {
		CompoundTag p = r.getCompoundOrEmpty("Position"), s = r.getCompoundOrEmpty("Size");
		int[] out = new int[3];
		String[] axes = {"x", "y", "z"};
		for (int a = 0; a < 3; a++) {
			int pos = p.getIntOr(axes[a], 0), size = s.getIntOr(axes[a], 0);
			out[a] = size < 0 ? pos + size + 1 : pos;
		}
		return out;
	}

	// ---- the template written ---------------------------------------------------------------------------------------

	/** Collects blocks into a structure template's palette and block list (air left out). */
	private static final class Builder {
		private final ListTag palette = new ListTag();
		private final Map<String, Integer> indices = new HashMap<>();
		private final List<CompoundTag> blocks = new ArrayList<>();
		private int w, h, l;

		void add(int x, int y, int z, CompoundTag state) {
			String name = state.getStringOr("Name", state.getStringOr("id", "minecraft:air"));
			if (name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air")) {
				return;
			}
			String key = state.toString();
			Integer i = this.indices.get(key);
			if (i == null) {
				i = this.palette.size();
				this.palette.add(state.copy());
				this.indices.put(key, i);
			}
			CompoundTag b = new CompoundTag();
			b.put("pos", ints(x, y, z));
			b.putInt("state", i);
			this.blocks.add(b);
		}

		void size(int w, int h, int l) {
			this.w = w;
			this.h = h;
			this.l = l;
		}

		CompoundTag build(int dataVersion) {
			CompoundTag t = new CompoundTag();
			t.put("size", ints(this.w, this.h, this.l));
			t.put("palette", this.palette);
			ListTag list = new ListTag();
			list.addAll(this.blocks);
			t.put("blocks", list);
			t.put("entities", new ListTag());
			t.putInt("DataVersion", dataVersion);
			return t;
		}
	}

	private static ListTag ints(int... values) {
		ListTag list = new ListTag();
		for (int v : values) {
			list.add(IntTag.valueOf(v));
		}
		return list;
	}
}
