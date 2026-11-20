package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.LooseWood;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every block change in a live world, whoever made it: wood left on nothing comes down ({@link LooseWood}). */
@Mixin(ServerLevel.class)
abstract class LooseWoodMixin {
	@Inject(method = "updatePOIOnBlockStateChange", at = @At("HEAD"))
	private void jaymoskingdoms$looseWood(BlockPos pos, BlockState was, BlockState now, CallbackInfo ci) {
		LooseWood.changed((ServerLevel)(Object)this, pos, was, now);
	}
}
