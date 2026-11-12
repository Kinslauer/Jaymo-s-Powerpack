package com.jaymospowers.mixin.icecaves;

import com.jaymospowers.frost.IceCaves;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ore in the ice caves: where an ore blob meets ice cave rock, its targets are tried against the stone (or deepslate)
 * that would have been there ({@link IceCaves#standIn}); if one takes, the ore goes in and the ice round it turns to that
 * stone, a pocket. Works for every ore feature that is an {@link OreFeature}, ours, vanilla's and other mods'.
 */
@Mixin(OreFeature.class)
abstract class OreStandInMixin {
	@WrapOperation(method = "doPlace", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/feature/OreFeature;canPlaceOre(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/BlockReplacement;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Z"))
	private boolean jaymospowers$iceCaveOre(OreFeature self, BlockState state, Function<BlockPos, BlockState> getter, RandomSource random,
		BlockReplacement target, BlockPos.MutableBlockPos pos, Operation<Boolean> original, @Local(argsOnly = true) WorldGenLevel level,
		@Local BulkSectionAccess sections) {
		if (!original.call(self, state, getter, random, target, pos)) {
			BlockState stone = IceCaves.standIn(level, pos, state);
			if (stone == null || !original.call(self, stone, getter, random, target, pos)) {
				return false;
			}
		}
		IceCaves.pocket(level, getter, pos.immutable(), (at, rock) -> {
			if (!IceCaves.writable(level, at)) {
				return;
			}
			LevelChunkSection section = sections.getSection(at);
			if (section == null) {
				return;
			}
			int x = SectionPos.sectionRelative(at.getX()), y = SectionPos.sectionRelative(at.getY()), z = SectionPos.sectionRelative(at.getZ());
			if (section.getBlockState(x, y, z).is(IceCaves.ROCK)) {
				section.setBlockState(x, y, z, rock, false);
			}
		});
		return true;
	}
}
