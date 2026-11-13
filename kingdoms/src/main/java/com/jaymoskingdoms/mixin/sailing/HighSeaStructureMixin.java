package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.seas.HighSea;
import java.util.Optional;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Under the higher sea ({@link HighSea}) land the biomes still call plains or desert can lie under water: a village (or
 * any jigsaw building that stands on the ground) isn't started where the ground at its middle is under the sea, so none
 * floats on it.
 */
@Mixin(Structure.class)
public abstract class HighSeaStructureMixin {
	@Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$notUnderTheSea(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		Structure self = (Structure)(Object)this;
		Optional<Structure.GenerationStub> stub = cir.getReturnValue();
		if (stub.isEmpty() || !(self instanceof JigsawStructure) || self.step() != GenerationStep.Decoration.SURFACE_STRUCTURES
			|| !(context.chunkGenerator() instanceof NoiseBasedChunkGenerator noise) || !HighSea.raised(noise.generatorSettings().value())) {
			return;
		}
		var at = stub.get().position();
		int ground = context.chunkGenerator().getBaseHeight(at.getX(), at.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
			context.randomState());
		if (ground < context.chunkGenerator().getSeaLevel()) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
