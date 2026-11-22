package com.jaymospowers.freerun.moves.mixin;

import com.jaymospowers.freerun.moves.NpcClimb;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A climber's way ({@link NpcClimb}): up edges as high as {@link NpcClimb#CLIMB_HEIGHT} (two blocks of steps where vanilla
 * takes one), and over fences and walls. Whether the mob climbs is read once per search, when it starts.
 */
@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeClimbMixin extends NodeEvaluator {
	@Unique
	private boolean jaymospowers$climber;

	@Inject(method = "prepare", at = @At("TAIL"))
	private void jaymospowers$climber(PathNavigationRegion level, Mob entity, CallbackInfo ci) {
		this.jaymospowers$climber = NpcClimb.climber(entity);
	}

	@Inject(method = "getMobJumpHeight", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$climbHeight(CallbackInfoReturnable<Double> cir) {
		if (this.jaymospowers$climber) {
			cir.setReturnValue(Math.max(NpcClimb.CLIMB_HEIGHT, (double)this.mob.maxUpStep()));
		}
	}

	/** Two blocks of steps up (each a block), not one. */
	@ModifyExpressionValue(method = "getNeighbors", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(F)I"))
	private int jaymospowers$climbSteps(int jumpSize) {
		return this.jaymospowers$climber ? Math.max(jumpSize, 2) : jumpSize;
	}

	@ModifyExpressionValue(method = "findAcceptedNode",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/pathfinder/WalkNodeEvaluator;canWalkOverFences()Z"))
	private boolean jaymospowers$overFences(boolean can) {
		return can || this.jaymospowers$climber;
	}
}
