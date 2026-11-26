package com.jaymoskitchen.mixin;

import com.jaymoskitchen.corpse.Corpses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What fell off an eaten or torn body ({@link Corpses#REMAINS_TAG}: its gear, its pockets, its bones) is never taken up
 * by a mob: a zombie at the meal doesn't walk off in the dead man's helmet or with his spear. Players take it as usual.
 */
@Mixin(Mob.class)
abstract class RemainsPickupMixin {
	@Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$leaveRemains(ServerLevel level, ItemEntity item, CallbackInfo ci) {
		if (item.entityTags().contains(Corpses.REMAINS_TAG)) {
			ci.cancel();
		}
	}
}
