package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.521) Andy (2026-11-04): ChoiceTheorem's Overhauled Village (CTOV, CC BY-NC-ND 4.0, shipped unchanged in the
 * pack) has ready-made trade buildings for every kind of land, better looking than drawing more of our own. So a town's
 * butcher, fishery, hunting lodge, smithy and mason's yard are now CTOV's own building for that trade and land
 * ({@code ctov:village/<land>/jobsite/<trade>}), read whole from its structure file and put up by the builders block by
 * block like any design. Nothing of CTOV's is changed or copied: its files are read in the game, as /place template
 * would. Its entrance (the jigsaw that joins it to the street) is turned to the north, our door side.
 * <p>
 * Our own halls ({@link JobHalls}, {@link Buildings}) stay: in the cherry groves (Blossom; CTOV has no such land), in
 * the havens (but those founded in a CTOV village, {@link Havens#ctov}), when CTOV isn't loaded or has no building for that trade and land, and on the plain design (variant 0).
 * A variant staked from this version on carries {@link #MARK} in its top bits (every second try, so a site too tight for
 * CTOV's larger building can still take ours); a project or finished building from before keeps drawing as it was, so
 * nothing half built or standing is redrawn under it.
 */
final class CtovHalls {
	/** The top 16 bits of a variant that may be drawn as CTOV's building (1 in 65536 older variants share them by chance). */
	static final long MARK = 0xC70AL << 48, MARK_BITS = 0xFFFFL << 48;
	/** CTOV's buildings with an entrance higher than this above their lowest layer stand on stilts or dig in; skipped. */
	static final int MAX_ENTRANCE_Y = 2;
	/** The largest one a crew puts up: across and high. */
	static final int MAX_SIDE = Plots.MAX_SIDE, MAX_HEIGHT = 40;

	/** Our building -> CTOV's trade building. */
	private static final Map<String, String> TRADES = Map.of("butcher", "butcher", "fishery", "fishing_hut", "hunting_lodge", "hunter", "smithy",
		"smith", "mason", "mason");

	/** Read buildings by template id and turn (empty: none, or not usable). */
	private static final Map<String, Optional<Blueprint>> CACHE = new HashMap<>();

	private CtovHalls() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> CACHE.clear());
	}

	static boolean loaded() {
		return FabricLoader.getInstance().isModLoaded("ctov");
	}

	/** {@code entry} has a CTOV building. */
	static boolean covers(String entry) {
		return TRADES.containsKey(entry);
	}

	/** A variant just picked for {@code entry} ({@link Planner#variant}), marked for CTOV on even tries when it can be. */
	static long tag(String entry, long variant, int attempt) {
		if (!covers(entry) || attempt % 2 != 0 || !loaded()) {
			return variant;
		}
		return variant & ~MARK_BITS | MARK;
	}

	static boolean marked(long variant) {
		return (variant & MARK_BITS) == MARK;
	}

	/**
	 * The building for {@code entry} in {@code f} on {@code variant}: CTOV's for that trade and land if the variant is
	 * marked and one fits, else ours.
	 */
	static Blueprint draw(ServerLevel level, Faction f, long variant, String entry, Buildings.Design ours) {
		Buildings.Kit k = Buildings.Kit.of(level, f, variant, variant);
		// (a haven of our own keeps its own way of building; one founded in a CTOV village takes CTOV's)
		if (marked(variant) && !k.blossom && (k.haven.isEmpty() || Havens.ctovHaven(level.getServer(), f)) && loaded()) {
			Blueprint bp = pick(level.getServer(), entry, k.land, k.village, variant);
			if (bp != null) {
				return bp;
			}
		}
		return ours.draw(k);
	}

	/** CTOV's lands for ours, best first: a realm favours the fortified towns, a village the plain ones. */
	static List<String> lands(Palette land, boolean village) {
		List<String> out;
		if (land == Palette.TAIGA) {
			out = List.of("taiga_fortified", "taiga", "mountain");
		} else if (land == Palette.SNOWY) {
			out = village ? List.of("snowy_igloo", "mountain_alpine") : List.of("mountain_alpine", "snowy_igloo");
		} else if (land == Palette.SAVANNA) {
			out = List.of("savanna", "savanna_na");
		} else if (land == Palette.DESERT) {
			out = List.of("desert", "desert_oasis");
		} else if (land == Palette.BADLANDS) {
			out = List.of("mesa_fortified", "mesa", "desert");
		} else if (land == Palette.JUNGLE) {
			out = List.of("jungle", "jungle_tree");
		} else if (land == Palette.SWAMP) {
			out = List.of("swamp_fortified", "swamp");
		} else {
			out = List.of("plains_fortified", "plains", "mountain");
		}
		if (village && out.getFirst().endsWith("_fortified")) {
			List<String> plain = new ArrayList<>(out);
			plain.add(0, plain.remove(1));
			return plain;
		}
		return out;
	}

	/** CTOV's building for {@code entry} in {@code land}: the variant picks first or second choice, then the rest in order. */
	private static @Nullable Blueprint pick(MinecraftServer server, String entry, Palette land, boolean village, long variant) {
		String trade = TRADES.get(entry);
		List<String> order = new ArrayList<>(lands(land, village));
		if ((variant >>> 9 & 3L) == 0L && order.size() > 1) {
			// now and then the land's other kind of town
			order.add(0, order.remove(1));
		}
		for (String l : order) {
			Blueprint bp = read(server, entry, "village/" + l + "/jobsite/" + trade);
			if (bp != null) {
				return bp;
			}
		}
		return null;
	}

	private static @Nullable Blueprint read(MinecraftServer server, String entry, String path) {
		return CACHE.computeIfAbsent(entry + "|" + path, key -> Optional.ofNullable(load(server, entry, path))).orElse(null);
	}

	private static @Nullable Blueprint load(MinecraftServer server, String entry, String path) {
		Identifier id = Identifier.fromNamespaceAndPath("ctov", path);
		Optional<StructureTemplate> template;
		try {
			template = server.getStructureTemplateManager().get(id);
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("CTOV building {} can't be read: {}", id, e.toString());
			return null;
		}
		if (template.isEmpty()) {
			return null;
		}
		try {
			Blueprint bp = convert(entry, template.get().save(new CompoundTag()));
			if (bp == null) {
				JaymosKingdoms.LOGGER.info("CTOV building {} skipped for {}", id, entry);
			}
			return bp;
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("CTOV building {} can't be used: {}", id, e.toString());
			return null;
		}
	}

	/**
	 * A CTOV structure file as a blueprint: its jigsaws as the blocks they become, air, voids and structure blocks
	 * dropped, containers empty, no entities, turned so its street entrance faces north, its lowest layer the ground.
	 * Null if it has no level street entrance near the ground or is too big for a crew.
	 */
	static @Nullable Blueprint convert(String entry, CompoundTag tag) {
		return convert(entry, tag, MAX_SIDE);
	}

	/** (0.7.629) As {@link #convert(String, CompoundTag)}, up to {@code maxSide} across. */
	static @Nullable Blueprint convert(String entry, CompoundTag tag, int maxSide) {
		ListTag palette = tag.getList("palette").orElseGet(() -> tag.getListOrEmpty("palettes").getListOrEmpty(0));
		List<BlockState> states = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, palette.getCompoundOrEmpty(i)));
		}
		ListTag sizeTag = tag.getListOrEmpty("size");
		int w = sizeTag.getIntOr(0, 0), d = sizeTag.getIntOr(2, 0);
		if (w <= 0 || d <= 0) {
			return null;
		}
		List<Blueprint.Placement> raw = new ArrayList<>();
		String front = null;
		int entranceY = 0;
		int minY = Integer.MAX_VALUE;
		ListTag blocks = tag.getListOrEmpty("blocks");
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompoundOrEmpty(i);
			int s = b.getIntOr("state", -1);
			if (s < 0 || s >= states.size()) {
				continue;
			}
			BlockState state = states.get(s);
			ListTag pos = b.getListOrEmpty("pos");
			BlockPos at = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
			if (state.is(Blocks.JIGSAW)) {
				CompoundTag nbt = b.getCompoundOrEmpty("nbt");
				String orientation = state.getValue(BlockStateProperties.ORIENTATION).getSerializedName();
				if (front == null && nbt.getStringOr("name", "").equals("minecraft:building_entrance") && orientation.endsWith("_up")) {
					front = orientation.substring(0, orientation.length() - 3);
					entranceY = at.getY();
				}
				state = finalState(nbt.getStringOr("final_state", "minecraft:air"));
			}
			if (state.isAir() || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)) {
				continue;
			}
			raw.add(new Blueprint.Placement(at, state));
			minY = Math.min(minY, at.getY());
		}
		if (raw.isEmpty() || entranceY - minY > MAX_ENTRANCE_Y) {
			return null;
		}
		Rotation turn = switch (front == null ? "north" : front) {
			case "south" -> Rotation.CLOCKWISE_180;
			case "west" -> Rotation.CLOCKWISE_90;
			case "east" -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		List<Blueprint.Placement> placed = new ArrayList<>(raw.size());
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, maxY = 0;
		for (Blueprint.Placement pl : raw) {
			BlockPos o = turn(pl.offset(), w, d, turn).offset(0, -minY, 0);
			placed.add(new Blueprint.Placement(o, pl.state().rotate(turn)));
			minX = Math.min(minX, o.getX());
			minZ = Math.min(minZ, o.getZ());
			maxX = Math.max(maxX, o.getX());
			maxZ = Math.max(maxZ, o.getZ());
			maxY = Math.max(maxY, o.getY());
		}
		List<Blueprint.Placement> trimmed = new ArrayList<>(placed.size());
		for (Blueprint.Placement pl : placed) {
			trimmed.add(new Blueprint.Placement(pl.offset().offset(-minX, 0, -minZ), pl.state()));
		}
		int width = maxX - minX + 1, depth = maxZ - minZ + 1, height = maxY + 1;
		if (width > maxSide || depth > maxSide || height > MAX_HEIGHT || trimmed.size() > Designs.MAX_BLOCKS) {
			return null;
		}
		return new Blueprint(entry, width, depth, height, List.copyOf(trimmed));
	}

	/** {@code p} in a {@code w} x {@code d} footprint, turned about it (clockwise looking down: north goes east). */
	static BlockPos turn(BlockPos p, int w, int d, Rotation r) {
		return switch (r) {
			case CLOCKWISE_90 -> new BlockPos(d - 1 - p.getZ(), p.getY(), p.getX());
			case CLOCKWISE_180 -> new BlockPos(w - 1 - p.getX(), p.getY(), d - 1 - p.getZ());
			case COUNTERCLOCKWISE_90 -> new BlockPos(p.getZ(), p.getY(), w - 1 - p.getX());
			default -> p;
		};
	}

	/** The block a jigsaw becomes ({@code final_state}, its properties left as the block's own); air if unknown. */
	private static BlockState finalState(String text) {
		int bracket = text.indexOf('[');
		String name = bracket >= 0 ? text.substring(0, bracket) : text;
		Identifier id = Identifier.tryParse(name.trim());
		if (id == null) {
			return Blocks.AIR.defaultBlockState();
		}
		Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR);
		return block.defaultBlockState();
	}
}
