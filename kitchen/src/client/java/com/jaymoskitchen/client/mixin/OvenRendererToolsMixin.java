package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.JaymosKitchen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.blay09.mods.cookingforblockheads.client.render.OvenRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Cooking for Blockheads draws the four tools on an oven's top with the FIXED display context, tilted 45 degrees.
 * FIXED is also the item-frame context, so for our own cookware we resolve the model with HEAD instead: our 3D
 * cookware models carry the "stand upright on the oven top" correction in their "head" display entry and keep a
 * normal "fixed" entry for item frames. Other mods' utensils (and the oven's food slots) are left on FIXED.
 */
@Mixin(OvenRenderer.class)
abstract class OvenRendererToolsMixin {
	@WrapOperation(method = "extractRenderState(Lnet/blay09/mods/cookingforblockheads/block/entity/OvenBlockEntity;"
		+ "Lnet/blay09/mods/cookingforblockheads/client/render/OvenRenderer$OvenRenderState;FLnet/minecraft/world/phys/Vec3;"
		+ "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		slice = @Slice(
			from = @At(value = "INVOKE", target = "Lnet/blay09/mods/cookingforblockheads/block/entity/OvenBlockEntity;getToolItem(I)Lnet/minecraft/world/item/ItemStack;"),
			to = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;asLong()J")),
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForTopItem("
			+ "Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;"
			+ "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;"
			+ "Lnet/minecraft/world/entity/ItemOwner;I)V"))
	private void jaymoskitchen$cookwareContext(ItemModelResolver resolver, ItemStackRenderState state, ItemStack stack,
		ItemDisplayContext context, Level level, ItemOwner owner, int seed, Operation<Void> original) {
		if (!stack.isEmpty() && JaymosKitchen.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
			context = ItemDisplayContext.HEAD;
		}
		original.call(resolver, state, stack, context, level, owner, seed);
	}
}
