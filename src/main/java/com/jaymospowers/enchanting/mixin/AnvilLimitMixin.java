package com.jaymospowers.enchanting.mixin;

import com.jaymospowers.enchanting.Enchanting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The anvil can't put more enchantments on an item than it holds ({@link Enchanting#limit}). */
@Mixin(AnvilMenu.class)
public abstract class AnvilLimitMixin extends ItemCombinerMenu {
	@Shadow
	@Final
	private DataSlot cost;

	protected AnvilLimitMixin(@Nullable MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access,
		ItemCombinerMenuSlotDefinition slots) {
		super(type, id, inventory, access, slots);
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void jaymospowers$limit(CallbackInfo ci) {
		ItemStack result = resultSlots.getItem(0);
		if (!result.isEmpty() && !player.hasInfiniteMaterials() && Enchanting.overLimit(result, inputSlots.getItem(0))) {
			resultSlots.setItem(0, ItemStack.EMPTY);
			cost.set(0);
			broadcastChanges();
		}
	}
}
