package com.jaymospowers.mixin.alexscaves;

import com.jaymospowers.compat.AlexsCavesCompat;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Alex's Caves' buried ruins (the Magnetic Caves' among them) are placed with {@link AlexsCavesCompat#RUIN_STONE}, which
 * swaps its galena and red and blue magnetic blocks for our ferrosite and ichorite. Ruins without them are untouched.
 */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.level.feature.UndergroundRuinsFeature", remap = false)
abstract class UndergroundRuinsStoneMixin {
	@Inject(method = "modifyPlacementSettings", at = @At("RETURN"), require = 0)
	private void jaymospowers$ourStone(StructurePlaceSettings settings, CallbackInfoReturnable<StructurePlaceSettings> cir) {
		cir.getReturnValue().addProcessor(AlexsCavesCompat.RUIN_STONE);
	}
}
