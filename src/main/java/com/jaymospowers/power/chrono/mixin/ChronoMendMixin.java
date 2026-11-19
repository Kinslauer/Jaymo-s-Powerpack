package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoMend;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells Chrono's "Put it back as it was" ({@link ChronoMend}) about each block about to change, so it can keep the old one. Does nothing unless a Chrono has the ability. */
@Mixin(Level.class)
abstract class ChronoMendMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
	private void jaymospowers$chronoMendLog(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
		ChronoMend.changed((Level)(Object)this, pos, state);
	}
}
