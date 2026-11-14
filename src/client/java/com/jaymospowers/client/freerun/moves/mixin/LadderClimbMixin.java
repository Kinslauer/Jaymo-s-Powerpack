package com.jaymospowers.client.freerun.moves.mixin;

import com.jaymospowers.client.freerun.moves.LadderMoves;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Ladders, vines, scaffolding and ropes, on vanilla's own climbing ({@link LadderMoves}). Ported in shape from Better
 * Climbing (CC0, Artemis): sideways at walking pace, the look-down drop, and Jump from the foot of one is a real jump.
 * The first two read the local player only, so mobs and other players climb as they always did.
 */
@Mixin(LivingEntity.class)
abstract class LadderClimbMixin {
	/** Vanilla clamps sideways speed on a climbable to 0.15 (a crawl): not any more, unless sneaking in the air. */
	@WrapOperation(method = "handleOnClimbable(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D"))
	private double jaymospowers$walkingPace(double value, double min, double max, Operation<Double> original) {
		if ((Object)this instanceof LocalPlayer player && LadderMoves.walksFree(player)) {
			return value;
		}
		return original.call(value, min, max);
	}

	/** The slowest it may fall: looking steeply down raises it (Sneak still holds you, vanilla checks that after). */
	@WrapOperation(method = "handleOnClimbable(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
		at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
	private double jaymospowers$lookDown(double y, double vanillaMin, Operation<Double> original) {
		if ((Object)this instanceof LocalPlayer player) {
			return original.call(y, LadderMoves.dropSpeed(player, vanillaMin));
		}
		return original.call(y, vanillaMin);
	}

	/**
	 * Vanilla sets the climb speed (0.2) over whatever the body was doing when Jump is held or you press into the wall,
	 * so a jump from the foot of a ladder never left the ground: keep the rise if it's already faster.
	 */
	@ModifyArg(method = "handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;<init>(DDD)V"), index = 1)
	private double jaymospowers$realJump(double climb) {
		return (Object)this instanceof LocalPlayer player ? Math.max(player.getDeltaMovement().y, climb) : climb;
	}
}
