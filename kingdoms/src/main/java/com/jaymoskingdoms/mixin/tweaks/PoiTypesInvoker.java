package com.jaymoskingdoms.mixin.tweaks;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The lookup from a block state to its point of interest, for states added after vanilla's ({@code BlossomFurnishings}). */
@Mixin(PoiTypes.class)
public interface PoiTypesInvoker {
	@Invoker("registerBlockStates")
	static void jaymoskingdoms$registerBlockStates(Holder<PoiType> type, Set<BlockState> states) {
		throw new AssertionError();
	}
}
