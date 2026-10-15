package com.jaymospowers.client.compat.mixin;

import com.jaymospowers.core.TwoHanded;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.collision.WeaponHitBoxes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * A two-handed weapon's swing sweeps a bigger area: its box is wider and taller ({@link TwoHanded#SWEEP}) and its arc
 * wider ({@link TwoHanded#ARC}). The box is already longer, from the weapon's reach ({@code TwoHandedReachMixin}).
 */
@Mixin(targets = "net.bettercombat.client.collision.TargetFinder", remap = false)
abstract class TwoHandedSweepMixin {
	@Redirect(method = "findAttackTargetResult", at = @At(value = "INVOKE",
		target = "Lnet/bettercombat/client/collision/WeaponHitBoxes;createHitbox(Lnet/bettercombat/api/WeaponAttributes$HitBoxShape;DZ)Lnet/minecraft/world/phys/Vec3;"))
	private static Vec3 jaymospowers$widerSwing(WeaponAttributes.HitBoxShape shape, double range, boolean spin, @Local(argsOnly = true) Player player) {
		Vec3 size = WeaponHitBoxes.createHitbox(shape, range, spin);
		if (size == null || player == null || !TwoHanded.is(player.getMainHandItem())) {
			return size;
		}
		return new Vec3(size.x * TwoHanded.SWEEP, size.y * TwoHanded.SWEEP, size.z);
	}

	@ModifyArg(method = "findAttackTargetResult", at = @At(value = "INVOKE",
		target = "Lnet/bettercombat/client/collision/TargetFinder$RadialFilter;<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DD)V"), index = 3)
	private static double jaymospowers$widerArc(double angle, @Local(argsOnly = true) Player player) {
		return player != null && TwoHanded.is(player.getMainHandItem()) ? TwoHanded.arc(angle) : angle;
	}
}
