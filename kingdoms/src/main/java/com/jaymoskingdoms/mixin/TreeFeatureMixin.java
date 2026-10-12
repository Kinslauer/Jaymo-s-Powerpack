package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.GrownTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.10) The world's trees (and the trees saplings grow into) are grown branch by branch ({@link GrownTrees}). */
@Mixin(TreeFeature.class)
abstract class TreeFeatureMixin {
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$grown(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
		CallbackInfoReturnable<Boolean> cir) {
		if (GrownTrees.place((TreeFeature)(Object)this, level, random, origin)) {
			cir.setReturnValue(true);
		}
	}
}
