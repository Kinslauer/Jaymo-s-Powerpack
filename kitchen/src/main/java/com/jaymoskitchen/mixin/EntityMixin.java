package com.jaymoskitchen.mixin;

import com.jaymoskitchen.corpse.CorpseCapture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every item an entity drops goes through here; a dying corpse-leaver's drops are caught for its corpse. */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(
		method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
		at = @At("HEAD"),
		cancellable = true)
	private void jaymoskitchen$catchDeathLoot(ServerLevel level, ItemStack stack, Vec3 offset,
		CallbackInfoReturnable<ItemEntity> cir) {
		if ((Object) this instanceof CorpseCapture capture && capture.jaymoskitchen$isCapturing() && !stack.isEmpty()) {
			capture.jaymoskitchen$capture(stack);
			cir.setReturnValue(null);
		}
	}
}
