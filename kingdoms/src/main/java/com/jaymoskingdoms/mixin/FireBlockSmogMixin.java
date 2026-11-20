package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.disasters.FireSmog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every fire's tick is counted, so the smog over a large flame can be seen from afar ({@link FireSmog}). */
@Mixin(FireBlock.class)
abstract class FireBlockSmogMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void jaymoskingdoms$smog(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		FireSmog.burning(level, pos);
	}
}
