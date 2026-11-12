package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.HydrologyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpringFeature.class)
public class SpringFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void dynamicwaters$noSpringsOnGorgeWalls(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (!HydrologyManager.riversIn(level.getLevel()) || !com.AdamNew.dynamicwaters.compat.OneDimension.carves(generator)) {
            return;
        }
        final BlockPos pos = origin;
        if (pos.getY() >= HydrologyManager.getSeaLevel() && (HydrologyManager.inGorgeReach(pos.getX(), pos.getZ())
                || HydrologyManager.inRavine(pos.getX(), pos.getZ()))) {
            cir.setReturnValue(false);
        }
    }
}
