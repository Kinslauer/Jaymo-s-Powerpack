package com.jaymoskingdoms.mixin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.6.7) A structure template's palette caches its blocks by kind in a plain HashMap, filled on first use with
 * computeIfAbsent. Structure starts are generated on several worker threads at once, so two of them opening the same
 * template piece for the first time (the pack's big towns share pieces between many villages) changed the map under
 * each other: a ConcurrentModificationException in {@code Palette.blocks}, "Exception generating new chunk", and the
 * server down. The cache is made a ConcurrentHashMap; what it holds is the same.
 */
@Mixin(StructureTemplate.Palette.class)
abstract class StructurePaletteMixin {
	@Shadow
	@Final
	@Mutable
	private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void jaymoskingdoms$threadSafe(CallbackInfo ci) {
		this.cache = new ConcurrentHashMap<>();
	}
}
