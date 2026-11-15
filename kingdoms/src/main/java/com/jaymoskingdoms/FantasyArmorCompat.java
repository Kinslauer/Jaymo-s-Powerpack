package com.jaymoskingdoms;

import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

/**
 * Fantasy Armor (mod id {@value #MOD}, kenddie's Medieval Series; all rights reserved, nothing of it copied here) puts its
 * sets on the world's people where it's loaded, by registry id, so there's no dependency. Only on monsters, the undead
 * and kings: its pieces are made over netherite, the crown's tier (Andy, 2026-10-29: kings wear netherite, nobody below
 * them may), so royal tiers still hold. The risen dead now and then come up in a whole dark set at the higher world tiers
 * ({@link MonsterGear}); a king in gilded attire wears a gilded set instead of his trimmed netherite ({@link Regalia}).
 * The Grave Sentinel's helm is never put on anyone (Andy, 2026-11-15: "the grave sentinel helmet should be removed").
 */
public final class FantasyArmorCompat {
	public static final String MOD = "fantasy_armor";
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);
	/** The sets the risen dead come up in, and the ones a king wears. */
	static final List<String> UNDEAD_SETS = List.of("dead_gladiator", "fog_guard", "dark_cover", "grave_sentinel");
	static final List<String> KING_SETS = List.of("gilded_hunt", "dawncrest");
	/** A set whose helm nobody wears (its slot is left as it was). */
	static final String NO_HELM = "grave_sentinel";
	private static final String[] PIECES = {"_helmet", "_chestplate", "_leggings", "_boots"};
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private FantasyArmorCompat() {
	}

	/** One of {@code sets}, at random, or null where the mod isn't loaded. */
	static String pick(List<String> sets, RandomSource random) {
		return LOADED ? sets.get(random.nextInt(sets.size())) : null;
	}

	/** {@code set}'s piece for slot {@code i} (0 the helm to 3 the boots), if it's there and anyone may wear it. */
	static Optional<Item> piece(String set, int i) {
		if (set == null || i == 0 && set.equals(NO_HELM)) {
			return Optional.empty();
		}
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(MOD, set + PIECES[i]));
	}
}
