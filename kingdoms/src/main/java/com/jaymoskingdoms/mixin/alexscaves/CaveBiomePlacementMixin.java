package com.jaymoskingdoms.mixin.alexscaves;

import com.github.alexmodguy.alexscaves.citadel.server.event.EventReplaceBiome;
import com.github.alexmodguy.alexscaves.server.level.biome.ACBiomeRarity;
import com.github.alexmodguy.alexscaves.server.misc.VoronoiGenerator;
import com.jaymoskingdoms.AlexsCavesCompat;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Alex's Caves' cave biomes go mostly under Mohknee and the sea round it ({@link AlexsCavesCompat#keeps}): each biome's
 * rule turns a cell down before Alex's own climate checks when the cell lies where we keep none.
 */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.config.BiomeGenerationNoiseCondition", remap = false)
abstract class CaveBiomePlacementMixin {
	@Shadow
	@Final
	private int alexscavesRarityOffset;

	@Inject(method = "test(IIIFLnet/minecraft/world/level/biome/Climate$Sampler;Lnet/minecraft/resources/ResourceKey;Lcom/github/alexmodguy/alexscaves/server/misc/VoronoiGenerator$VoronoiInfo;)Z",
		at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskingdoms$farLands(int x, int y, int z, float depth, Climate.Sampler sampler, ResourceKey<Level> dimension,
			VoronoiGenerator.VoronoiInfo cell, CallbackInfoReturnable<Boolean> cir) {
		if (Level.OVERWORLD.equals(dimension) && !this.jaymoskingdoms$keeps(cell)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "test(Lcom/github/alexmodguy/alexscaves/citadel/server/event/EventReplaceBiome;Lcom/github/alexmodguy/alexscaves/server/misc/VoronoiGenerator$VoronoiInfo;)Z",
		at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskingdoms$farLandsEvent(EventReplaceBiome event, VoronoiGenerator.VoronoiInfo cell, CallbackInfoReturnable<Boolean> cir) {
		if (Level.OVERWORLD.equals(event.getWorldDimension()) && !this.jaymoskingdoms$keeps(cell)) {
			cir.setReturnValue(false);
		}
	}

	private boolean jaymoskingdoms$keeps(VoronoiGenerator.VoronoiInfo cell) {
		Vec3 centre = cell == null ? null : ACBiomeRarity.getRareBiomeCenter(cell);
		return centre == null || AlexsCavesCompat.keeps(centre, this.alexscavesRarityOffset);
	}
}
