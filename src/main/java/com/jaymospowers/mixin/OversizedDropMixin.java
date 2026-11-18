package com.jaymospowers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A dropped stack bigger than its item's stack size is split into full stacks as it enters the world. An item entity
 * holding more can't be saved (the count must stay within 1-99) and is lost when its chunk unloads: Droppable Villager
 * Trades drops up to five stacks' worth of a dead villager's trade in one entity (152 bricks on the Deck, 2026-11-18
 * 11:09 AM), and any other mod that does the same is caught here too.
 */
@Mixin(ServerLevel.class)
abstract class OversizedDropMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"))
	private void jaymospowers$splitOversized(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!(entity instanceof ItemEntity item)) {
			return;
		}
		ItemStack stack = item.getItem();
		int max = stack.getMaxStackSize();
		if (stack.getCount() <= max) {
			return;
		}
		ServerLevel level = (ServerLevel)(Object)this;
		ItemStack rest = stack.copy();
		item.setItem(rest.split(max));
		while (!rest.isEmpty()) {
			ItemEntity more = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), rest.split(max));
			more.setDeltaMovement(item.getDeltaMovement());
			more.setDefaultPickUpDelay();
			// (each part is at most a full stack, so this doesn't come back round)
			level.addFreshEntity(more);
		}
	}
}
