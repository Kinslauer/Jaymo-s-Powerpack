package com.jaymospowers.power.arsenal.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * WeaponMod's thrown weapons and darts save their item with {@code fromNonEmptyStack}, so one whose item has gone empty
 * (a dart already picked up, say) crashes the world save when its chunk unloads. Such a projectile now just saves
 * without an item.
 */
@Mixin(targets = "ckathode.weaponmod.entity.projectile.EntityMaterialProjectile")
abstract class WeaponModProjectileSaveMixin {
	@WrapOperation(method = "addAdditionalSaveData", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ItemStackTemplate;fromNonEmptyStack(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStackTemplate;"))
	private ItemStackTemplate jaymospowers$emptySafe(ItemStack stack, Operation<ItemStackTemplate> original) {
		return stack.isEmpty() ? null : original.call(stack);
	}

	@WrapWithCondition(method = "addAdditionalSaveData", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/storage/ValueOutput;store(Ljava/lang/String;Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V"))
	private boolean jaymospowers$skipEmpty(ValueOutput output, String key, Codec<?> codec, Object value) {
		return value != null;
	}
}
