package com.AdamNew.dynamicwaters.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CanyonWorldCarver.class)
public class CanyonCarverMixin {
    @Inject(method = "carve", at = @At("HEAD"), cancellable = true)
    private void dynamicwaters$cancelCanyonCarving(WorldGenerationContext context, RandomSource random, ChunkPos chunkPos,
            ChunkPos sourceChunkPos, CarverOutput output, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
