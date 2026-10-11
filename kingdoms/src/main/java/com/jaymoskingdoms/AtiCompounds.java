package com.jaymoskingdoms;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * ATi Structures (Vanilla Edition) in compounds: where one of its above-ground buildings would have stood alone, three
 * or four now stand together round an open middle, so a kingdom has a bigger place to grow into (and its builders room
 * to clear). Only with ATi loaded (its id {@link #ATI_MOD}); Kingdoms never needs it.
 * <ul>
 * <li>The compound itself is data in a built-in, always-on pack ({@code resourcepacks/ati_compounds}): the structure
 * {@code jaymoskingdoms:ati_compound} ({@link CompoundStructure}, listing ATi's large and medium above-ground
 * structures by id) and its structure set {@code jaymoskingdoms:ati_compounds} (its spacing lives there).</li>
 * <li>ATi's own {@link #ABSORBED} sets stop placing singles while the compounds' set is in the game: they're taken out
 * of the world's structure placement in code ({@code ChunkGeneratorStructureStateMixin}), not by overriding ATi's
 * files, because a built-in pack isn't sure to load above a mod's own data.</li>
 * </ul>
 * ATi's small, rare and underground sets are left as they are.
 */
public final class AtiCompounds {
	/** ATi Structures - Vanilla Edition's mod id. */
	static final String ATI_MOD = "mr_ati_structuresvanillaedition";
	/** The compounds' structure set (in the built-in pack). */
	static final Identifier COMPOUND_SET = JaymosKingdoms.id("ati_compounds");
	/** ATi's sets whose structures now come in compounds instead. */
	static final Set<Identifier> ABSORBED = Set.of(Identifier.fromNamespaceAndPath("ati_structures", "aboveground_large"),
		Identifier.fromNamespaceAndPath("ati_structures", "aboveground_medium"));

	private AtiCompounds() {
	}

	static void register() {
		CompoundStructure.TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, JaymosKingdoms.id("compound"), () -> CompoundStructure.CODEC);
		if (FabricLoader.getInstance().isModLoaded(ATI_MOD)) {
			boolean ok = ResourceLoader.registerBuiltinPack(JaymosKingdoms.id("ati_compounds"),
				FabricLoader.getInstance().getModContainer(JaymosKingdoms.MOD_ID).orElseThrow(),
				Component.literal("Jaymo's Kingdoms: ATi compounds"), PackActivationType.ALWAYS_ENABLED);
			JaymosKingdoms.LOGGER.info("ATi Structures found: its above-ground structures generate in compounds ({})", ok ? "pack on" : "pack missing!");
		}
	}

	/** The world's structure sets, less ATi's {@link #ABSORBED} ones when the compounds' set is among them. */
	public static List<Holder<StructureSet>> filter(List<Holder<StructureSet>> sets) {
		if (sets.stream().noneMatch(h -> is(h, COMPOUND_SET))) {
			return sets;
		}
		return sets.stream().filter(h -> h.unwrapKey().map(k -> !ABSORBED.contains(k.identifier())).orElse(true)).toList();
	}

	private static boolean is(Holder<StructureSet> h, Identifier id) {
		return h.unwrapKey().map(k -> k.identifier().equals(id)).orElse(false);
	}
}
