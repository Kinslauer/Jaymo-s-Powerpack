package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers which blocks a Chrono player places (their echoes place the favourite one). Server side only. */
@Mixin(BlockItem.class)
abstract class ChronoBlockItemMixin {
	@Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
	private void jaymospowers$chronoPlaced(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context.getPlayer() instanceof ServerPlayer player && cir.getReturnValue().consumesAction()) {
			ChronoHooks.placed(player, ((BlockItem) (Object) this).getBlock());
		}
	}
}
