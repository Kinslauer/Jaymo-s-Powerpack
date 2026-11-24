package com.jaymospowers.client.air.mixin;

import com.jaymospowers.client.air.BlockSettle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A block the local player places settles into place ({@link BlockSettle}): the placing as the client makes it. */
@Mixin(BlockItem.class)
abstract class BlockSettleMixin {
	@Inject(method = "place", at = @At("RETURN"))
	private void jaymospowers$settle(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getLevel() instanceof ClientLevel level && cir.getReturnValue().consumesAction()) {
			BlockSettle.placed(level, context.getClickedPos(), level.getBlockState(context.getClickedPos()));
		}
	}
}
