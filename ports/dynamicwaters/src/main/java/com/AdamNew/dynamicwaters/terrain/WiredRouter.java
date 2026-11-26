package com.AdamNew.dynamicwaters.terrain;

import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;

public interface WiredRouter {
    NoiseRouter dynamicwaters$wiredRouter();

    WiredRouter.Samplers dynamicwaters$samplers();

    /** The random state's noise router; sample its functions with {@code randomState.getSampler(f)} (26.3). */
    static NoiseRouter of(RandomState randomState) {
        return ((WiredRouter) (Object) randomState).dynamicwaters$wiredRouter();
    }

    /**
     * The compiled samplers of the router functions Dynamic Waters reads, looked up once per random state. 26.3's
     * {@code RandomState#getSampler} finds a function's sampler in a map keyed by the function itself, so every call
     * hashes the whole density function tree (records hash every child): asked per column, that was half the time
     * spent working out river heights.
     */
    static WiredRouter.Samplers samplers(RandomState randomState) {
        return ((WiredRouter) (Object) randomState).dynamicwaters$samplers();
    }

    /** What a cave in the terrain is filled with here: well over 0, solid rock. */
    float CAVES_FILLED = 64.0F;

    /**
     * The terrain without its caves (Jaymo's Powers): every density function the router reads from a {@code caves/}
     * file (the vanilla overworld's entrances, noodles, pillars, ...) stands in as solid rock. The river planner walks
     * each column down from the sky to its first solid block; through a cave entrance it found the cave's floor instead
     * of the ground round it, so a meadow at Y 77 read as Y 20-40, streams were routed into those holes, and the
     * stream was cut down to them as a sheer stone slot (Andy, 2026-11-26: "ravine river hybrids"). Without the caves
     * the walk stops at the ground, as on land with no cave under it.
     */
    static DensityFunction withoutCaves(DensityFunction function) {
        final DfRewriteRule[] fill = new DfRewriteRule[1];
        fill[0] = f -> {
            if (f instanceof DensityFunctions.HolderHolder held && held.function().unwrapKey()
                    .map(key -> key.identifier().getPath().contains("caves/")).orElse(false)) {
                return DensityFunctions.constant(CAVES_FILLED);
            }
            return f.rewriteChildren(fill[0]);
        };
        return fill[0].rewrite(function);
    }

    record Samplers(DensitySampler surface, DensitySampler density, DensitySampler ridges, DensitySampler continents,
            DensitySampler vegetation) {
        public static WiredRouter.Samplers of(RandomState randomState, NoiseRouter router) {
            return new WiredRouter.Samplers(randomState.getSampler(router.chunkSurfaceLevel()),
                    randomState.getSampler(withoutCaves(router.finalDensity())),
                    randomState.getSampler(router.ridges()), randomState.getSampler(router.continents()),
                    randomState.getSampler(router.vegetation()));
        }
    }
}
