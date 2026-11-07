package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.ChestNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.538) While someone opens a chest or barrel, its creak is as loud as their Light Fingers let it be ({@link ChestNoise#hush}). */
@Mixin(ContainerOpenersCounter.class)
abstract class ContainerOpenersHushMixin {
	@Inject(method = "incrementOpeners", at = @At("HEAD"))
	private void jaymoskingdoms$hushStart(LivingEntity user, Level level, BlockPos pos, BlockState state, double range, CallbackInfo ci) {
		ChestNoise.opening(user);
	}

	@Inject(method = "incrementOpeners", at = @At("RETURN"))
	private void jaymoskingdoms$hushEnd(LivingEntity user, Level level, BlockPos pos, BlockState state, double range, CallbackInfo ci) {
		ChestNoise.opening(null);
	}
}
