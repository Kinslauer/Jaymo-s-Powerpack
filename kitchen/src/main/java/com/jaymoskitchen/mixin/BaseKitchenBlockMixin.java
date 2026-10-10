package com.jaymoskitchen.mixin;

import com.jaymoskitchen.kitchen.OvenHob;
import net.blay09.mods.cookingforblockheads.block.BaseKitchenBlock;
import net.blay09.mods.cookingforblockheads.block.OvenBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An oven's outline (and so what can be aimed at) also covers each utensil standing on top, one box per tool spot
 * (see {@link OvenHob#withCookware}). getShape is declared on BaseKitchenBlock, which OvenBlock inherits; other
 * kitchen blocks are left alone. Collision stays the plain oven: see {@link OvenBlockCollisionMixin}.
 */
@Mixin(BaseKitchenBlock.class)
abstract class BaseKitchenBlockMixin {
	@Inject(method = "getShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;"
		+ "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
		at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$cookwareShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
		CallbackInfoReturnable<VoxelShape> cir) {
		if ((Object) this instanceof OvenBlock) {
			cir.setReturnValue(OvenHob.withCookware(cir.getReturnValue(), state, level, pos));
		}
	}
}
