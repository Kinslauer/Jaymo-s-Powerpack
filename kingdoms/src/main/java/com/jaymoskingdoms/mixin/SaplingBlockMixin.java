package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Trees;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A sapling ready to grow sprouts one of the Kingdoms' growing trees instead. Bone meal too (the GT New Horizons picks:
 * bone meal adds a growth step, never a whole tree at once).
 */
@Mixin(SaplingBlock.class)
abstract class SaplingBlockMixin {
	@Inject(method = "advanceTree", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$sprout(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, CallbackInfo ci) {
		if (state.getValue(SaplingBlock.STAGE) == 1 && Trees.sprout(level, pos, state)) {
			ci.cancel();
		}
	}

	/**
	 * Bone meal on a sapling at its last stage sprouts a Kingdoms growing tree where one grows, and otherwise adds
	 * nothing: the saplings the Kingdoms' trees don't grow (dark oak and other 2x2s, azaleas, mangroves, saplings off
	 * the Overworld) would otherwise come up as a whole tree at once.
	 */
	@Inject(method = "performBonemeal", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noBoneMealTree(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source, CallbackInfo ci) {
		if (state.getValue(SaplingBlock.STAGE) == 1) {
			Trees.sprout(level, pos, state);
			ci.cancel();
		}
	}
}
