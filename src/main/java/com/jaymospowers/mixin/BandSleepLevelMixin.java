package com.jaymospowers.mixin;

import com.jaymospowers.compat.BandSleep;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps entities and random ticks still in distant bands, so loaded columns do not decay unattended islands. */
@Mixin(ServerLevel.class)
abstract class BandSleepLevelMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$sleepEntity(Entity entity, CallbackInfo ci) {
		if (!(entity instanceof Player) && BandSleep.asleep((ServerLevel) (Object) this, entity.getX(), entity.getY(), entity.getZ())) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "tickChunk", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
	private void jaymospowers$sleepBlock(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
		if (!BandSleep.asleep(level, pos.getX(), pos.getY(), pos.getZ())) {
			original.call(state, level, pos, random);
		}
	}

	@WrapOperation(method = "tickChunk", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
	private void jaymospowers$sleepFluid(FluidState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
		if (!BandSleep.asleep(level, pos.getX(), pos.getY(), pos.getZ())) {
			original.call(state, level, pos, random);
		}
	}
}
