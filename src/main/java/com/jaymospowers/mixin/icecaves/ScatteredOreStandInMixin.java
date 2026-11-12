package com.jaymospowers.mixin.icecaves;

import com.jaymospowers.frost.IceCaves;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link OreStandInMixin} for the scattered ores (buried diamonds and the like). */
@Mixin(ScatteredOreFeature.class)
abstract class ScatteredOreStandInMixin {
	@WrapOperation(method = "place", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/feature/ScatteredOreFeature;canPlaceOre(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/BlockReplacement;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Z"))
	private boolean jaymospowers$iceCaveOre(ScatteredOreFeature self, BlockState state, Function<BlockPos, BlockState> getter, RandomSource random,
		BlockReplacement target, BlockPos.MutableBlockPos pos, Operation<Boolean> original, @Local(argsOnly = true) WorldGenLevel level) {
		if (!original.call(self, state, getter, random, target, pos)) {
			BlockState stone = IceCaves.standIn(level, pos, state);
			if (stone == null || !original.call(self, stone, getter, random, target, pos)) {
				return false;
			}
		}
		IceCaves.pocket(level, getter, pos.immutable(), (at, rock) -> {
			if (IceCaves.writable(level, at) && level.getBlockState(at).is(IceCaves.ROCK)) {
				level.setBlock(at, rock, Block.UPDATE_CLIENTS);
			}
		});
		return true;
	}
}
