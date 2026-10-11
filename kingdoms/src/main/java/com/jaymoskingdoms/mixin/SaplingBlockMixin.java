package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Trees;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A sapling ready to grow by itself sprouts one of the Kingdoms' growing trees instead (bone meal still grows it at once). */
@Mixin(SaplingBlock.class)
abstract class SaplingBlockMixin {
	@Unique
	private static boolean jaymoskingdoms$bonemeal;

	@Inject(method = "performBonemeal", at = @At("HEAD"))
	private void jaymoskingdoms$bonemealStart(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
			net.minecraft.world.level.block.BonemealSource source, CallbackInfo ci) {
		jaymoskingdoms$bonemeal = true;
	}

	@Inject(method = "performBonemeal", at = @At("RETURN"))
	private void jaymoskingdoms$bonemealEnd(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
			net.minecraft.world.level.block.BonemealSource source, CallbackInfo ci) {
		jaymoskingdoms$bonemeal = false;
	}

	@Inject(method = "advanceTree", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$sprout(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, CallbackInfo ci) {
		if (!jaymoskingdoms$bonemeal && state.getValue(SaplingBlock.STAGE) == 1 && Trees.sprout(level, pos, state)) {
			ci.cancel();
		}
	}
}
