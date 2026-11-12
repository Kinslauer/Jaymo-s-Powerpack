package com.AdamNew.dynamicwaters.mixin;

import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseGeneratorSettings.class)
public class NoiseGeneratorSettingsMixin {
    @Inject(method = "noiseRouter", at = @At("RETURN"), cancellable = true)
    private void dynamicwaters$forceRidges(CallbackInfoReturnable<NoiseRouter> cir) {
        if (com.AdamNew.dynamicwaters.Config.vanillaNormalRivers()) {
            return;
        }
        NoiseRouter original = cir.getReturnValue();
        // 26.3 reads noiseRouter() for every chunk, so the rewritten router is kept per original (1.21.1 read it once).
        final NoiseRouter known = DYNAMICWATERS$ROUTERS.get(original);
        if (known != null) {
            cir.setReturnValue(known);
            return;
        }

        // 26.3: DensityFunction#mapAll(Visitor) is DensityFunction#rewriteChildren(DfRewriteRule), and a rule recurses
        // itself. HolderHolder#rewriteChildren still steps into the referenced function, as mapAll did. The 1.21.1 router's
        // initial_density_without_jaggedness is 26.3's chunk_surface_level; the aquifer and vein noises left the router.
        final net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule riverFlattener = new net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule() {
            @Override
            public DensityFunction rewrite(DensityFunction fn) {
                if (fn instanceof DensityFunctions.HolderHolder holder && holder.function().unwrapKey().isPresent()) {
                    String path = holder.function().unwrapKey().get().identifier().getPath();
                    if (path.equals("overworld/ridges") || path.endsWith("/ridges") || path.endsWith("ridges_folded")) {
                        return dynamicwaters$flattenRidges(fn);
                    }
                }
                return fn.rewriteChildren(this);
            }
        };

        DensityFunction newRidges = dynamicwaters$flattenRidges(original.ridges());

        DensityFunction newSurfaceLevel = riverFlattener.rewrite(original.chunkSurfaceLevel());
        DensityFunction newFinalDensity = riverFlattener.rewrite(original.finalDensity());

        NoiseRouter newRouter = new NoiseRouter(
                original.temperature(),
                original.vegetation(),
                original.continents(),
                original.erosion(),
                original.depth(),
                newRidges,
                newSurfaceLevel,
                newFinalDensity);

        DYNAMICWATERS$ROUTERS.put(original, newRouter);
        cir.setReturnValue(newRouter);
    }

    @org.spongepowered.asm.mixin.Unique
    private static final java.util.Map<NoiseRouter, NoiseRouter> DYNAMICWATERS$ROUTERS =
            java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    private DensityFunction dynamicwaters$flattenRidges(DensityFunction originalRidges) {
        DensityFunction arg2 = DensityFunctions.rangeChoice(
                originalRidges, -1000000.0F, 0.0F,
                DensityFunctions.constant(-1.0F),
                DensityFunctions.constant(1.0F));

        DensityFunction whenInRange = DensityFunctions.mul(DensityFunctions.constant(0.1F), arg2);

        return DensityFunctions.rangeChoice(
                originalRidges, -0.15F, 0.15F,
                whenInRange,
                originalRidges);
    }
}
