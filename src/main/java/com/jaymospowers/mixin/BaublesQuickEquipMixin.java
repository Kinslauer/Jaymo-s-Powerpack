package com.jaymospowers.mixin;

import com.jaymospowers.baubles.BaublesQuickEquip;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift-clicking something wearable in the survival inventory (main slots, hotbar or offhand) puts it on in a free
 * bauble slot instead ({@link BaublesQuickEquip}). Returning empty also ends vanilla's repeat-while-same-item loop, so
 * one click puts on one.
 */
@Mixin(InventoryMenu.class)
public abstract class BaublesQuickEquipMixin extends AbstractContainerMenu {
	protected BaublesQuickEquipMixin(MenuType<?> type, int id) {
		super(type, id);
	}

	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$quickEquip(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
		if (slotIndex >= InventoryMenu.INV_SLOT_START && slotIndex <= InventoryMenu.SHIELD_SLOT && slotIndex < this.slots.size()
			&& BaublesQuickEquip.shiftClick(player, this.slots.get(slotIndex))) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
