package com.jaymoskingdoms.mixin.classtrees;

import com.jaymoskingdoms.FaithExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Monk's Light Step never tramples a crop ({@link FaithExtras#lightStep}): the farmland under their landing stays farmland. */
@Mixin(FarmlandBlock.class)
abstract class LightStepMixin {
	@Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$lightStep(@Nullable Entity sourceEntity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (sourceEntity instanceof Player player && FaithExtras.lightStep(player)) {
			ci.cancel();
		}
	}
}
