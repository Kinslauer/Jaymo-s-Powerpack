package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeSource.class)
public class BiomeSourceMixin {
    @Unique
    private static final ThreadLocal<Boolean> DYNAMICWATERS_WAS_GENERATING = new ThreadLocal<>();

    @Inject(method = "findBiomeHorizontal(IIIIILjava/util/function/Predicate;Lnet/minecraft/util/RandomSource;ZLnet/minecraft/world/level/levelgen/RandomState;)Lcom/mojang/datafixers/util/Pair;",
            at = @At("HEAD"))
    private void dynamicwaters$searchWithoutRivers(CallbackInfoReturnable<Pair<BlockPos, Holder<Biome>>> info) {
        DYNAMICWATERS_WAS_GENERATING.set(HydrologyManager.isGeneratingRivers());
        HydrologyManager.setGeneratingRivers(true);
    }

    @Inject(method = "findBiomeHorizontal(IIIIILjava/util/function/Predicate;Lnet/minecraft/util/RandomSource;ZLnet/minecraft/world/level/levelgen/RandomState;)Lcom/mojang/datafixers/util/Pair;",
            at = @At("RETURN"))
    private void dynamicwaters$searchDone(CallbackInfoReturnable<Pair<BlockPos, Holder<Biome>>> info) {
        HydrologyManager.setGeneratingRivers(Boolean.TRUE.equals(DYNAMICWATERS_WAS_GENERATING.get()));
        DYNAMICWATERS_WAS_GENERATING.remove();
    }
}
