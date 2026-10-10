package com.jaymoskitchen.table;

import com.jaymoskitchen.JaymosKitchen;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** The crafting table's grid that stays put (see {@link CraftingTableEntity}). */
public final class Tables {
	public static BlockEntityType<CraftingTableEntity> CRAFTING_TABLE;

	private Tables() {
	}

	public static void register() {
		CRAFTING_TABLE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("crafting_table"),
			new BlockEntityType<>(CraftingTableEntity::new, Set.of(Blocks.CRAFTING_TABLE)));
	}
}
