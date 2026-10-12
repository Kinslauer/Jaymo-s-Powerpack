package com.jaymospowers.mixin;

import com.jaymospowers.core.CarryBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A block a player places is remembered as theirs to carry ({@link CarryBlock}). */
@Mixin(BlockItem.class)
abstract class CarryBlockItemMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void jaymospowers$placed(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getPlayer() != null) {
			CarryBlock.markPlaced(context.getLevel(), context.getClickedPos());
		}
	}
}
