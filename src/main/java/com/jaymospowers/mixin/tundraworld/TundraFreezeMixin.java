package com.jaymospowers.mixin.tundraworld;

import com.jaymospowers.tundra.Tundra;
import com.jaymospowers.tundra.world.TundraWorld;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Water that freezes in the tundra, whenever it turns up (a lake a player dug, a spring's run-off, water from a bucket),
 * freezes to white ice, not plain ice: the game's own freeze check in {@code tickPrecipitation} (it runs on a random
 * column of every loaded chunk) lays {@code Blocks.ICE}, and this swaps it. The chunk's first making is the
 * {@code tundra_white_ice} feature's.
 */
@Mixin(ServerLevel.class)
abstract class TundraFreezeMixin {
	@WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 0))
	private boolean jaymospowers$freezeWhite(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
		if (state.is(Blocks.ICE) && Tundra.WHITE_ICE != null && level.getBiome(pos).is(TundraWorld.TUNDRA)) {
			state = Tundra.WHITE_ICE.defaultBlockState();
		}
		return original.call(level, pos, state);
	}
}
