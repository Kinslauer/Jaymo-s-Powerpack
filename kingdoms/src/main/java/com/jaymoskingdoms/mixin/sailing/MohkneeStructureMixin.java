package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.seas.IslandStructure;
import com.jaymoskingdoms.seas.Mohknee;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mohknee ({@link Mohknee}) is the blossom folk's own country: the home continent's villages, and the towers, taverns
 * and keeps the pack's other structure mods scatter over the land, are not started there. What stays is the game's own
 * (ruins, wrecks, mines, strongholds and the rest, villages aside) and ours; the Blossom settlements raise themselves.
 * Each island of the crossing takes its own village ({@link Mohknee#ISLANDS}) and no other.
 * Only the Overworld's own generator is asked: the sky and the other worlds have no Mohknee.
 */
@Mixin(Structure.class)
public abstract class MohkneeStructureMixin {
	@Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$notOnMohknee(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		Optional<Structure.GenerationStub> stub = cir.getReturnValue();
		if (stub.isEmpty()) {
			return;
		}
		var at = stub.get().position();
		boolean land = Mohknee.on(at.getX(), at.getZ());
		if (!land && !Mohknee.island(at.getX(), at.getZ())) {
			return;
		}
		Structure self = (Structure)(Object)this;
		var structures = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Identifier id = structures.getKey(self);
		// only the Overworld has this structure set against this land: its generator is the one with the vanilla village
		if (id == null || !Mohknee.overworld(context.chunkGenerator())) {
			return;
		}
		Holder<Structure> holder = structures.wrapAsHolder(self);
		if (!land) {
			// an island of the crossing: its own village and no other (the shared village pool could land a second on it)
			if (Mohknee.OWN_VILLAGE.get() == null && !(self instanceof IslandStructure) && holder.is(StructureTags.VILLAGE)) {
				cir.setReturnValue(Optional.empty());
			}
			return;
		}
		boolean foreign = !id.getNamespace().equals("minecraft") && !id.getNamespace().startsWith("jaymos");
		if (holder.is(StructureTags.VILLAGE) || foreign && self.step() == GenerationStep.Decoration.SURFACE_STRUCTURES) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
