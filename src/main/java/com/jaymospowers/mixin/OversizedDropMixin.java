package com.jaymospowers.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A dropped stack bigger than its item's stack size is split into full stacks as it enters the world. An item entity
 * holding more can't be saved (the count must stay within 1-99) and is lost when its chunk unloads: Droppable Villager
 * Trades drops up to five stacks' worth of a dead villager's trade in one entity (152 bricks on the Deck, 2026-11-18
 * 11:09 AM), and any other mod that does the same is caught here too. The rest is dropped only once the first full stack
 * is let into the world (another mod may turn a spawn away); turned away, it keeps its whole stack. Only the position and
 * motion carry over to the rest.
 */
@Mixin(ServerLevel.class)
abstract class OversizedDropMixin {
	@WrapMethod(method = "addFreshEntity")
	private boolean jaymospowers$splitOversized(Entity entity, Operation<Boolean> original) {
		if (!(entity instanceof ItemEntity item) || item.getItem().getCount() <= item.getItem().getMaxStackSize()) {
			return original.call(entity);
		}
		ItemStack whole = item.getItem();
		int max = whole.getMaxStackSize();
		ItemStack rest = whole.copy();
		item.setItem(rest.split(max));
		if (!original.call(entity)) {
			item.setItem(whole);
			return false;
		}
		ServerLevel level = (ServerLevel)(Object)this;
		while (!rest.isEmpty()) {
			ItemEntity more = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), rest.split(max));
			more.setDeltaMovement(item.getDeltaMovement());
			more.setDefaultPickUpDelay();
			// (each part is at most a full stack, so this passes straight through)
			level.addFreshEntity(more);
		}
		return true;
	}
}
