package com.AdamNew.dynamicwaters.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MaterialSystem.class)
public interface SurfaceSystemAccessor {
    @Invoker("getBand")
    BlockState dynamicwaters$getBand(int x, int y, int z);
}
