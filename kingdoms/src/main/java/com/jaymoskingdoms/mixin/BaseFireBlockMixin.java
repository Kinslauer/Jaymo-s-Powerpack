package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Firefighting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.11) A fire lit near a settlement is watched, so its people answer it ({@link Firefighting}). */
@Mixin(BaseFireBlock.class)
abstract class BaseFireBlockMixin {
	@Inject(method = "onPlace", at = @At("HEAD"))
	private void jaymoskingdoms$lit(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, CallbackInfo ci) {
		Firefighting.lit(level, pos);
	}
}
