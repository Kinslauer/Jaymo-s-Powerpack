package com.jaymospowers.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The step a world-gen region is making, from outside it: it says how far from the centre the region holds chunks and
 * how far along each ring is ({@code InStructureRegionMixin} reads structures only from there).
 */
@Mixin(WorldGenRegion.class)
public interface WorldGenRegionStepAccessor {
	@Accessor("generatingStep")
	ChunkStep jaymospowers$step();
}
