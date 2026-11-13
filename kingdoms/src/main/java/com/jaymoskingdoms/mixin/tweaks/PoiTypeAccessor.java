package com.jaymoskingdoms.mixin.tweaks;

import java.util.Set;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a block of ours join a vanilla point of interest (the futon is a home: {@code BlossomFurnishings}). */
@Mixin(PoiType.class)
public interface PoiTypeAccessor {
	@Mutable
	@Accessor("matchingStates")
	void jaymoskingdoms$matchingStates(Set<BlockState> states);
}
