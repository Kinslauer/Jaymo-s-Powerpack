package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Shared spawn pools (0.7.276): Andy wanted every mod's villages and outposts to "spawn interchangeably". Each structure
 * mod places its own villages through its own structure set (vanilla's villages, which Epic Structures redraws; Towns
 * and Towers'; any other), so each mod filled its own spots. Here every set made only of villages becomes one set (all
 * their entries, at vanilla's village spacing), and every set made only of pillager outposts another: each village spot
 * draws any village that suits its biome, from whichever mod. Done on the world's structure placement in code
 * ({@code ChunkGeneratorStructureStateMixin}), as {@link AtiCompounds} is, so no mod's files are overridden.
 */
public final class SharedPools {
	/** The sets whose spacing the shared ones keep. */
	static final Identifier VILLAGES = Identifier.withDefaultNamespace("villages"), OUTPOSTS = Identifier.withDefaultNamespace("pillager_outposts");

	private SharedPools() {
	}

	public static List<Holder<StructureSet>> merge(List<Holder<StructureSet>> sets) {
		List<Holder<StructureSet>> out = pool(sets, VILLAGES, SharedPools::village);
		return pool(out, OUTPOSTS, SharedPools::outpost);
	}

	/**
	 * A village for pooling: tagged as one, or named one. Towns and Towers' set holds a Nether piglin village that isn't in
	 * the village tag, which would keep its whole set out (it never spawns in the overworld, so pooling it is harmless).
	 */
	static boolean village(Holder<Structure> s) {
		return Seats.village(s) || s.unwrapKey().map(k -> k.identifier().getPath().toLowerCase(Locale.ROOT).contains("village")).orElse(false);
	}

	/** A pillager outpost, vanilla's or a structure mod's (by id). */
	static boolean outpost(Holder<Structure> s) {
		String path = s.unwrapKey().map(k -> k.identifier().getPath().toLowerCase(Locale.ROOT)).orElse("");
		return path.contains("pillager_outpost") || path.contains("outpost") && path.contains("pillager");
	}

	/** {@code sets} with every set made only of structures {@code which} accepts merged into one, at {@code lead}'s spacing (else the first's). */
	private static List<Holder<StructureSet>> pool(List<Holder<StructureSet>> sets, Identifier lead, Predicate<Holder<Structure>> which) {
		List<Holder<StructureSet>> group = new ArrayList<>();
		for (Holder<StructureSet> h : sets) {
			List<StructureSet.StructureSelectionEntry> entries = h.value().structures();
			if (!entries.isEmpty() && entries.stream().allMatch(e -> which.test(e.structure()))) {
				group.add(h);
			}
		}
		if (group.size() < 2) {
			return sets;
		}
		Holder<StructureSet> spacing = group.stream().filter(h -> h.unwrapKey().map(k -> k.identifier().equals(lead)).orElse(false)).findFirst()
			.orElse(group.getFirst());
		List<StructureSet.StructureSelectionEntry> all = new ArrayList<>();
		for (Holder<StructureSet> h : group) {
			for (StructureSet.StructureSelectionEntry e : h.value().structures()) {
				if (all.stream().noneMatch(a -> a.structure().equals(e.structure()))) {
					all.add(e);
				}
			}
		}
		Holder<StructureSet> shared = Holder.direct(new StructureSet(List.copyOf(all), spacing.value().placement()));
		List<Holder<StructureSet>> out = new ArrayList<>();
		boolean placed = false;
		for (Holder<StructureSet> h : sets) {
			if (!group.contains(h)) {
				out.add(h);
			} else if (!placed) {
				out.add(shared);
				placed = true;
			}
		}
		JaymosKingdoms.LOGGER.info("Shared spawn pool: {} sets, {} structures, at {}'s spacing", group.size(), all.size(),
			spacing.unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
		return List.copyOf(out);
	}
}
