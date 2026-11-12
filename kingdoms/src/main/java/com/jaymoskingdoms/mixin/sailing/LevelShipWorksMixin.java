package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.ShipWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A furnace or brewing stand aboard a ship under sail ({@link ShipWorks}) works on with its block out of the world: what it
 * does to "its" block (lighting up, a bottle set in) becomes her piece's state, and it touches nothing in the world.
 */
@Mixin(Level.class)
abstract class LevelShipWorksMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$shipWorkSets(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
		if (ShipWorks.ours(pos)) {
			ShipWorks.set(state);
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "blockEntityChanged", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$shipWorkChanged(BlockPos pos, CallbackInfo ci) {
		if (ShipWorks.ours(pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "updateNeighbourForOutputSignal", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$shipWorkSignal(BlockPos pos, Block block, CallbackInfo ci) {
		if (ShipWorks.ours(pos)) {
			ci.cancel();
		}
	}
}
