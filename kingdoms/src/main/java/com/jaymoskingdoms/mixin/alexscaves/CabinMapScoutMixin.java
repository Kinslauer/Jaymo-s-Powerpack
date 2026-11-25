package com.jaymoskingdoms.mixin.alexscaves;

import com.jaymoskingdoms.Scout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Alex's Caves adds an underground cabin map to many chests with its own loot modifier, which calls vanilla's
 * findNearestMapStructure directly (so {@code ExplorationMapScoutMixin} never sees it). Like that one, on the server
 * thread the cabin is predicted with {@link Scout}, no chunk made, so opening a chest can't freeze the server.
 */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.misc.CabinMapLootModifier", remap = false)
abstract class CabinMapScoutMixin {
	@Redirect(method = "doApply", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;findNearestMapStructure(Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/BlockPos;IZ)Lnet/minecraft/core/BlockPos;"))
	private BlockPos jaymoskingdoms$scoutNotMake(ServerLevel level, TagKey<Structure> tag, BlockPos origin, int radius, boolean skipKnown) {
		if (level.getServer().isSameThread()) {
			HolderSet<Structure> set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(tag).orElse(null);
			return set == null ? null : Scout.nearest(level, set, origin, radius, skipKnown);
		}
		return level.findNearestMapStructure(tag, origin, radius, skipKnown);
	}
}
