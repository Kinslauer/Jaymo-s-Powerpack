package com.jaymoskitchen.mixin;

import com.jaymoskitchen.kitchen.HobLocks;
import com.jaymoskitchen.kitchen.HobToolSlot;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.blay09.mods.cookingforblockheads.menu.slot.SlotOvenTool;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * A tool slot in the oven screen holding a pot or pan that a dish is cooking in can't be emptied (see
 * {@link HobLocks}). Vanilla checks mayPickup for clicking, shift-clicking (quick move), number-key swaps, dropping
 * and double-click collecting, so overriding it covers them all. SlotOvenTool doesn't declare mayPickup, so this adds
 * an override of Slot#mayPickup. The menu's tool slots sit at 16..19 of the oven's internal container (= tools 0..3).
 */
@Mixin(SlotOvenTool.class)
abstract class SlotOvenToolMixin implements HobToolSlot {
	@Unique
	private static final int FIRST_TOOL = 16;

	@Unique
	private @Nullable OvenBlockEntity jaymoskitchen$oven;

	@Override
	public void jaymoskitchen$setOven(OvenBlockEntity oven) {
		this.jaymoskitchen$oven = oven;
	}

	public boolean mayPickup(Player player) {
		OvenBlockEntity oven = jaymoskitchen$oven;
		return oven == null || !HobLocks.isLocked(oven, ((Slot) (Object) this).getContainerSlot() - FIRST_TOOL);
	}
}
