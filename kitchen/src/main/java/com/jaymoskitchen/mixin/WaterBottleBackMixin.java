package com.jaymoskitchen.mixin;

import com.jaymoskitchen.climate.Thirst;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Andy: "water bottles should return the glass bottle after you drink". A water bottle or a Clean Water always leaves its
 * glass behind. Both items name the glass bottle as what they turn into, so this only steps in when a stack comes to the
 * mouth without that (a stack from an older world or another mod's hands) and the drink would otherwise eat the glass.
 */
@Mixin(ItemStack.class)
abstract class WaterBottleBackMixin {
	@WrapMethod(method = "finishUsingItem")
	private ItemStack jaymoskitchen$bottleBack(Level level, LivingEntity entity, Operation<ItemStack> original) {
		ItemStack self = (ItemStack) (Object) this;
		boolean lost = Thirst.bottledWater(self) && !self.has(DataComponents.USE_REMAINDER);
		int before = self.getCount();
		var item = self.getItem();
		ItemStack result = original.call(level, entity);
		if (!lost || entity.hasInfiniteMaterials()) {
			return result;
		}
		if (result.isEmpty()) {
			return new ItemStack(Items.GLASS_BOTTLE);
		}
		if (!level.isClientSide() && result.is(item) && result.getCount() < before && entity instanceof Player player) {
			player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), net.minecraft.util.Prediction.SERVER_ONLY);
		}
		return result;
	}
}
