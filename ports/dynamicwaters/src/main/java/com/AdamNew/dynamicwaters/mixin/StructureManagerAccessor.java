package com.AdamNew.dynamicwaters.mixin;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The level a structure manager was made for. One Dimension's CustomChunks builds each band's terrain with no region
 * handed in, but with a structure manager made for that band's region, so the river hooks take the region from here.
 */
@Mixin(StructureManager.class)
public interface StructureManagerAccessor {
    @Accessor("level")
    LevelAccessor dynamicwaters$level();
}
