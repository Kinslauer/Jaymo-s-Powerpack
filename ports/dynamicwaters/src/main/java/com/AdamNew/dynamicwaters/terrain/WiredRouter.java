package com.AdamNew.dynamicwaters.terrain;

import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;

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

    record Samplers(DensitySampler surface, DensitySampler density, DensitySampler ridges, DensitySampler continents,
            DensitySampler vegetation) {
        public static WiredRouter.Samplers of(RandomState randomState, NoiseRouter router) {
            return new WiredRouter.Samplers(randomState.getSampler(router.chunkSurfaceLevel()), randomState.getSampler(router.finalDensity()),
                    randomState.getSampler(router.ridges()), randomState.getSampler(router.continents()),
                    randomState.getSampler(router.vegetation()));
        }
    }
}
