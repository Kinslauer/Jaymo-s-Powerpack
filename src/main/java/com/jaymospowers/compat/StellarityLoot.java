package com.jaymospowers.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Stellarity (optional; read by loot table id, never linked), the pack's End: its strongholds and End cities stand where
 * the vanilla ones (and Enderscape's, {@link EnderscapeLoot}) stood and fill nearly every chest from its own tables, so
 * relics that live in stronghold corridors and End city treasure look in these too. Without Stellarity the tables never
 * roll, so nothing changes.
 */
public final class StellarityLoot {
	/** The stronghold's corridors: the Grand Shield's home, after the vanilla corridor. */
	public static final ResourceKey<LootTable> STRONGHOLD_CORRIDOR = key("stronghold/corridor");
	/** The End ship's treasure: the Voidreaver's home, after vanilla End city treasure. */
	public static final ResourceKey<LootTable> END_SHIP_TREASURE = key("end_city/ship_treasure");

	private StellarityLoot() {
	}

	private static ResourceKey<LootTable> key(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("stellarity", path));
	}
}
