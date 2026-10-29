package com.jaymospowers.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Enderscape (optional; read by loot table id, never linked): its strongholds and End cities replace the vanilla ones
 * and fill their chests from its own tables, so relics that live in vanilla stronghold corridors and End city treasure
 * look in these too. Without Enderscape the tables never roll, so nothing changes.
 */
public final class EnderscapeLoot {
	/** The stronghold armoury: the Grand Shield's home, after the vanilla corridor. */
	public static final ResourceKey<LootTable> STRONGHOLD_ARMORY = key("stronghold/chest/armory");
	/** End city chests: the Voidreaver's home, after vanilla End city treasure. */
	public static final ResourceKey<LootTable> END_CITY_CHEST = key("end_city/chest");

	private EnderscapeLoot() {
	}

	private static ResourceKey<LootTable> key(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("enderscape", path));
	}
}
