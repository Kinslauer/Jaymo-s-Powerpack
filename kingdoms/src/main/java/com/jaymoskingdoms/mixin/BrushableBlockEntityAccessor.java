package com.jaymoskingdoms.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.5.33) What suspicious gravel holds, so a dig quest can bury its keepsake in a trail ruin. */
@Mixin(BrushableBlockEntity.class)
public interface BrushableBlockEntityAccessor {
	@Accessor("item")
	void jaymoskingdoms$setItem(ItemStack item);

	@Accessor("lootTable")
	void jaymoskingdoms$setLootTable(@Nullable ResourceKey<LootTable> lootTable);
}
