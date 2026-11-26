package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.seas.IslandStructure;
import com.jaymoskingdoms.seas.Mohknee;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mohknee ({@link Mohknee}) is the blossom folk's own country, separated from the home continent by distance rather
 * than a hard block: valid mainland villages and other mods' surface structures are kept rare there (one in
 * {@link #VILLAGE_KEEP_ONE_IN} and one in {@link #SURFACE_KEEP_ONE_IN}) instead of banned outright. What stays at full
 * rate is the game's own non-village structures (ruins, wrecks, mines, strongholds and the rest) and ours; Blossom
 * settlements raise themselves. Each island of the crossing still takes its own village ({@link Mohknee#ISLANDS}) and
 * no second one on top of it. Only the Overworld's own generator is asked: the sky and the other worlds have no
 * Mohknee.
 */
@Mixin(Structure.class)
public abstract class MohkneeStructureMixin {
	/** One in this many valid mainland village starts on Mohknee is kept. */
	@Unique
	private static final int VILLAGE_KEEP_ONE_IN = 10;
	/** One in this many valid other-mod surface structure starts on Mohknee is kept. */
	@Unique
	private static final int SURFACE_KEEP_ONE_IN = 12;
	/** Salts for the deterministic per-chunk rarity roll on Mohknee (villages share one per chunk; each foreign mod rolls by namespace). */
	@Unique
	private static final int VILLAGE_SALT = 0x4D6F6856, SURFACE_SALT = 0x4D6F6853;

	@Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$rareOnMohknee(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
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
		if (holder.is(StructureTags.VILLAGE)) {
			if (!jaymoskingdoms$keep(context, VILLAGE_SALT, VILLAGE_KEEP_ONE_IN)) {
				cir.setReturnValue(Optional.empty());
			}
			return;
		}
		boolean foreign = !id.getNamespace().equals("minecraft") && !id.getNamespace().startsWith("jaymos");
		if (foreign && self.step() == GenerationStep.Decoration.SURFACE_STRUCTURES
			&& !jaymoskingdoms$keep(context, SURFACE_SALT ^ id.getNamespace().hashCode(), SURFACE_KEEP_ONE_IN)) {
			cir.setReturnValue(Optional.empty());
		}
	}

	/** Deterministic seed+chunk roll so worldgen and {@code StructureCheck} (/locate) agree and multi-entry sets don't reroll at the same chunk. */
	@Unique
	private static boolean jaymoskingdoms$keep(Structure.GenerationContext context, int salt, int oneIn) {
		if (oneIn <= 1) {
			return true;
		}
		ChunkPos chunk = context.chunkPos();
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
		random.setLargeFeatureWithSalt(context.seed(), chunk.x(), chunk.z(), salt);
		return random.nextInt(oneIn) == 0;
	}
}
