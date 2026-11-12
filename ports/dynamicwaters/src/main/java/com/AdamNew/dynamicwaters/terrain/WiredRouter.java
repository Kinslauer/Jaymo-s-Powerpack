package com.AdamNew.dynamicwaters.terrain;

import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

public interface WiredRouter {
    NoiseRouter dynamicwaters$wiredRouter();

    /** The random state's noise router; sample its functions with {@code randomState.getSampler(f)} (26.3). */
    static NoiseRouter of(RandomState randomState) {
        return ((WiredRouter) (Object) randomState).dynamicwaters$wiredRouter();
    }
}
