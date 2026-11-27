package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Scout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Every explorer map the loot system makes (cartographer and wandering trader trades, shipwreck and ruin chests, ancient
 * city maps, a downed trader's never-made offers) looks its structure up with vanilla's findNearestMapStructure, which makes
 * each likely chunk on the calling thread to be sure. On the server thread that waited behind the world generation queue
 * past the 60 s watchdog (Deck crashes 2026-11-24 10:45 PM, a wandering trader finished off while downed, and 11:12 PM, a
 * roaming caravan's trader's offers). On the server thread the place is predicted with {@link Scout} instead (no chunk
 * made, and the draw of the shared village pool checked, so a village map points at a village that will really stand),
 * over the same search radius; anywhere else (world generation's own threads) vanilla's search runs as before. Scout can't
 * add a reference (that needs the chunk), but with skipKnownStructures it leaves out the starts the structure cache or
 * saved chunks already show as claimed.
 * <p>
 * This lives in Kingdoms, not Powers, because the winner check ({@code PoolPick}) is Kingdoms'.
 */
@Mixin(ExplorationMapFunction.class)
abstract class ExplorationMapScoutMixin {
	@Redirect(method = "run", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;findNearestMapStructure(Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"))
	private BlockPos jaymoskingdoms$scoutNotMake(ServerLevel level, HolderSet<Structure> destination, BlockPos origin, int radius, boolean skipKnown) {
		if (level.getServer().isSameThread()) {
			return Scout.nearest(level, destination, origin, radius, skipKnown);
		}
		return level.findNearestMapStructure(destination, origin, radius, skipKnown);
	}
}
