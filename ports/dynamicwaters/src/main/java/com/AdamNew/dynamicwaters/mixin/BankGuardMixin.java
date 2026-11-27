package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.BankGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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

/** Runs {@link BankGuard} around the features it keeps off river banks. */
@Mixin(ChunkGenerator.class)
public class BankGuardMixin {
    @Unique
    private static final ThreadLocal<Object[]> DYNAMICWATERS$BANK_CHUNK = new ThreadLocal<>();

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void dynamicwaters$bankStart(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo ci) {
        final boolean rivers = com.AdamNew.dynamicwaters.HydrologyManager.riversIn(level.getLevel())
                && com.AdamNew.dynamicwaters.compat.OneDimension.carves((ChunkGenerator) (Object) this);
        DYNAMICWATERS$BANK_CHUNK.set(rivers ? new Object[] {level, chunk.getPos().x(), chunk.getPos().z()} : null);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void dynamicwaters$bankEnd(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo ci) {
        DYNAMICWATERS$BANK_CHUNK.remove();
    }

    @WrapOperation(method = "applyBiomeDecoration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/placement/FeaturePlacer;placeWithBiomeCheck(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean dynamicwaters$guardBanks(FeaturePlacer placer, PlacedFeature feature, RandomSource random, BlockPos origin,
            Operation<Boolean> original) {
        final Object[] on = DYNAMICWATERS$BANK_CHUNK.get();
        if (on == null) return original.call(placer, feature, random, origin);
        final WorldGenLevel level = (WorldGenLevel) on[0];
        final Identifier id = level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE).getKey(feature);
        if (!BankGuard.guards(id)) return original.call(placer, feature, random, origin);
        final Object[] banks = BankGuard.before(level, (Integer) on[1], (Integer) on[2]);
        final boolean placed = original.call(placer, feature, random, origin);
        BankGuard.after(level, banks);
        return placed;
    }
}
