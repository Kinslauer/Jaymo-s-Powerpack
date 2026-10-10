package com.jaymoskitchen.kitchen;

import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;

/** Cooking for Blockheads' oven tool slots (SlotOvenTool), told which oven they belong to (see the OvenMenu mixin). */
public interface HobToolSlot {
	void jaymoskitchen$setOven(OvenBlockEntity oven);
}
