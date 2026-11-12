package com.AdamNew.dynamicwaters.mixin;

import com.AdamNew.dynamicwaters.terrain.WiredRouter;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Jaymo's Powers 26.3 port: 1.21.1 kept the noise-wired router RandomState built in its constructor. A 26.3 RandomState
 * keeps the raw router and compiles samplers from it on demand ({@code RandomState#getSampler}), and has no public
 * getter, so this only hands out that router.
 */
@Mixin(RandomState.class)
public class RandomStateMixin implements WiredRouter {
    @Shadow
    @Final
    private NoiseRouter router;

    @Override
    public NoiseRouter dynamicwaters$wiredRouter() {
        return this.router;
    }
}
