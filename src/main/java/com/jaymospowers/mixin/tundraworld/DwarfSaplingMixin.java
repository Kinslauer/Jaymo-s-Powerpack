package com.jaymospowers.mixin.tundraworld;

import com.jaymospowers.tundra.world.TundraFeatures;
import com.jaymospowers.tundra.world.TundraWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A spruce sapling in the tundra or the lichen woodland grows into a dwarfed spruce; there is no dwarf sapling. */
@Mixin(SaplingBlock.class)
abstract class DwarfSaplingMixin {
	@Inject(method = "advanceTree", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$dwarfed(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, CallbackInfo ci) {
		if (state.is(Blocks.SPRUCE_SAPLING) && state.getValue(SaplingBlock.STAGE) != 0 && level.getBiome(pos).is(TundraWorld.DWARFS_SPRUCE)) {
			TundraFeatures.DwarfSpruce.grow(level, pos, random, state);
			ci.cancel();
		}
	}
}
