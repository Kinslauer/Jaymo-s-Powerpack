package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.627) Andy (2026-11-06), of Dungeons and Taverns' buildings: "same with the tavern ... do them all". With D&T
 * installed ({@link DntMine#MOD}, All Rights Reserved; read at run time from the player's own copy, as /place template
 * would: nothing of theirs copied into ours), the builders put up D&T's own buildings, block by block from storage like
 * any design ({@link CtovHalls#convert}):
 * <ul>
 * <li>a kingdom's <b>tavern</b>: D&T's tavern house in the wood of the land round the seat (thirteen of them);</li>
 * <li>the <b>well</b>: D&T's well in the land's wood;</li>
 * <li>the <b>smithy</b>, on the tries CTOV's ({@link CtovHalls}) doesn't take: D&T's forest smith or ruined smith;</li>
 * <li>new buildings, only while D&T is installed: a <b>sawmill</b>, a <b>farmhouse</b> (the ruined farmer's), an
 * <b>apiary</b> (the bee keeper's) and, in realms, a <b>firewatch tower</b> in the land's wood and, in kingdoms, a
 * <b>graveyard</b> (bigger than a plot: {@link #MAX_SIDE}).</li>
 * </ul>
 * D&T scatters most of these as overgrown ruins; ours go up whole, without the cobwebs, leaves and weeds. The yard pieces
 * D&T joins round its taverns and wells in the wild (carts, stalls, the shaft under a well) aren't built. Too big for a
 * crew to put up: the dappled town hall (40 x 48, and D&T joins some twenty more pieces onto it); the hamlet is a piglin bunker.
 * <p>
 * Like CTOV's halls, a variant staked from this version on carries {@link #MARK} (every second try for the tavern, well
 * and smithy, so a site too tight for D&T's can still take ours); older projects and buildings keep drawing as they were.
 */
final class DntBuildings {
	static final long MARK = 0xD7A5L << 48;
	/** Our buildings D&T has one for, and the ones only D&T draws. */
	static final Set<String> SHARED = Set.of("tavern", "well", "smithy"), OWN = Set.of("sawmill", "farmhouse", "apiary", "firewatch_tower",
		"graveyard");
	/** Andy (2026-11-06) let the graveyard (27 x 37) past the plot size: buildings only D&T draws may be this big across. */
	static final int MAX_SIDE = 40;

	private static final Map<String, Optional<Blueprint>> CACHE = new HashMap<>();

	private DntBuildings() {
	}

	static void clear() {
		CACHE.clear();
	}

	static boolean loaded() {
		return FabricLoader.getInstance().isModLoaded(DntMine.MOD);
	}

	/** A variant just picked for {@code entry} ({@link Planner#variant}), marked for D&T when it may be. */
	static long tag(String entry, long variant, int attempt) {
		if (!loaded() || CtovHalls.marked(variant)) {
			return variant;
		}
		boolean mark = OWN.contains(entry)
			|| SHARED.contains(entry) && (entry.equals("smithy") ? attempt % 2 == 1 : attempt % 2 == 0);
		return mark ? variant & ~CtovHalls.MARK_BITS | MARK : variant;
	}

	static boolean marked(long variant) {
		return (variant & CtovHalls.MARK_BITS) == MARK;
	}

	/** D&T's building for {@code entry} if the variant is marked and one can be read, else {@code ours}. */
	static Blueprint draw(ServerLevel level, Faction f, long variant, String entry, Supplier<Blueprint> ours) {
		if (marked(variant) && loaded()) {
			Blueprint bp = pick(level, f, entry, variant);
			if (bp != null) {
				return bp;
			}
		}
		return ours.get();
	}

	/** D&T's building for {@code entry} round {@code f}'s seat, or null. */
	static @Nullable Blueprint pick(ServerLevel level, Faction f, String entry, long variant) {
		String wood = wood(level.getBiome(f.seat()));
		return switch (entry) {
			case "tavern" -> first(level.getServer(), entry, "tavern/tavern_house_" + wood, "tavern/tavern_house_oak");
			case "well" -> first(level.getServer(), entry, "well/main/well_archy_" + (wood.equals("acacia") ? "savanna" : wood), "well/main/well_archy_oak");
			case "smithy" -> first(level.getServer(), entry, (variant >>> 11 & 1L) == 0L ? "remnant/forrest_smith" : "remnant/ruin_smith", "remnant/forrest_smith");
			case "sawmill" -> first(level.getServer(), entry, "remnant/sawmill");
			case "farmhouse" -> first(level.getServer(), entry, "remnant/ruin_farmer");
			case "apiary" -> first(level.getServer(), entry, "remnant/bee_keeper");
			case "graveyard" -> first(level.getServer(), entry, "remnant/graveyard");
			case "firewatch_tower" -> first(level.getServer(), entry, "firewatch_tower/firewatch_" + firewatch(wood), "firewatch_tower/firewatch_forest");
			default -> null;
		};
	}

	private static @Nullable Blueprint first(MinecraftServer server, String entry, String... paths) {
		for (String p : paths) {
			Blueprint bp = read(server, entry, p);
			if (bp != null) {
				return bp;
			}
		}
		return null;
	}

	/** Which of D&T's woods suits a biome (its taverns and wells come in these). */
	static String wood(Holder<Biome> biome) {
		String id = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
		if (id.contains("snow") || id.contains("frozen") || id.contains("ice") || id.contains("grove") || id.contains("peaks")) {
			return "snowy";
		}
		if (id.contains("desert") || id.contains("badlands")) {
			return "desert";
		}
		if (id.contains("mangrove")) {
			return "mangrove";
		}
		if (id.contains("swamp")) {
			return "swamp";
		}
		if (id.contains("jungle") || id.contains("bamboo")) {
			return "jungle";
		}
		if (id.contains("savanna")) {
			return "acacia";
		}
		if (id.contains("cherry")) {
			return "cherry";
		}
		if (id.contains("pale")) {
			return "pale";
		}
		if (id.contains("dark_forest")) {
			return "dark_oak";
		}
		if (id.contains("birch")) {
			return "birch";
		}
		if (id.contains("taiga")) {
			return "spruce";
		}
		return "oak";
	}

	/** The firewatch towers' names for the woods. */
	private static String firewatch(String wood) {
		return switch (wood) {
			case "oak" -> "forest";
			case "spruce", "snowy" -> "taiga";
			case "pale" -> "pale_oak";
			case "acacia", "desert" -> "savanna";
			default -> wood;
		};
	}

	private static @Nullable Blueprint read(MinecraftServer server, String entry, String path) {
		return CACHE.computeIfAbsent(entry + "|" + path, k -> Optional.ofNullable(load(server, entry, path))).orElse(null);
	}

	private static @Nullable Blueprint load(MinecraftServer server, String entry, String path) {
		Identifier id = Identifier.fromNamespaceAndPath("nova_structures", path);
		Optional<StructureTemplate> template;
		try {
			template = server.getStructureTemplateManager().get(id);
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Dungeons and Taverns' {} can't be read: {}", id, e.toString());
			return null;
		}
		if (template.isEmpty()) {
			return null;
		}
		try {
			Blueprint bp = CtovHalls.convert(entry, template.get().save(new CompoundTag()), OWN.contains(entry) ? MAX_SIDE : CtovHalls.MAX_SIDE);
			if (bp == null) {
				JaymosKingdoms.LOGGER.info("Dungeons and Taverns' {} skipped for {} (too big for a crew)", id, entry);
				return null;
			}
			return tidy(bp);
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Dungeons and Taverns' {} can't be used: {}", id, e.toString());
			return null;
		}
	}

	/** A ruin built whole: no cobwebs, leaves, vines or weeds; suspicious gravel and sand as plain. */
	private static Blueprint tidy(Blueprint bp) {
		List<Blueprint.Placement> out = new ArrayList<>(bp.blocks().size());
		for (Blueprint.Placement pl : bp.blocks()) {
			BlockState s = pl.state();
			if (s.is(Blocks.COBWEB) || s.is(BlockTags.LEAVES) || s.is(Blocks.VINE) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)
				|| s.is(Blocks.GLOW_LICHEN)) {
				continue;
			}
			if (s.is(Blocks.SUSPICIOUS_GRAVEL)) {
				s = Blocks.GRAVEL.defaultBlockState();
			} else if (s.is(Blocks.SUSPICIOUS_SAND)) {
				s = Blocks.SAND.defaultBlockState();
			}
			out.add(new Blueprint.Placement(pl.offset(), s));
		}
		return new Blueprint(bp.id(), bp.width(), bp.depth(), bp.height(), List.copyOf(out));
	}
}
