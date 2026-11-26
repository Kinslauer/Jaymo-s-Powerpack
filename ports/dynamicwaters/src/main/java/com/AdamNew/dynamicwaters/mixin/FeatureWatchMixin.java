package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.ColumnWatch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Column watch: names each feature that changes a watched soil top while a chunk is decorated. */
@Mixin(ChunkGenerator.class)
public class FeatureWatchMixin {
    @Unique
    private static final ThreadLocal<Object[]> DYNAMICWATERS$DECORATING = new ThreadLocal<>();

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void dynamicwaters$watchStart(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo ci) {
        ColumnWatch.stage(chunk, "decoration start");
        DYNAMICWATERS$DECORATING.set(ColumnWatch.near(chunk.getPos().x(), chunk.getPos().z())
                ? new Object[] {level, chunk.getPos().x(), chunk.getPos().z()} : null);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void dynamicwaters$watchEnd(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo ci) {
        DYNAMICWATERS$DECORATING.remove();
        ColumnWatch.stage(chunk, "decoration end");
    }

    @WrapOperation(method = "applyBiomeDecoration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/placement/FeaturePlacer;placeWithBiomeCheck(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean dynamicwaters$watchFeature(FeaturePlacer placer, PlacedFeature feature, RandomSource random, BlockPos origin,
            Operation<Boolean> original) {
        final Object[] on = DYNAMICWATERS$DECORATING.get();
        if (on == null) return original.call(placer, feature, random, origin);
        final WorldGenLevel level = (WorldGenLevel) on[0];
        final Object[] snapshot = ColumnWatch.before(level, (Integer) on[1], (Integer) on[2]);
        final boolean placed = original.call(placer, feature, random, origin);
        if (snapshot != null) {
            final net.minecraft.resources.Identifier id = level.registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE).getKey(feature);
            ColumnWatch.after(level, snapshot, "feature " + (id != null ? id.toString() : feature.feature().unwrapKey()
                    .map(k -> k.identifier().toString()).orElse(String.valueOf(feature.feature().value()))));
        }
        return placed;
    }
}
