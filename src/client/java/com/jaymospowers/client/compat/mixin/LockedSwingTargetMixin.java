package com.jaymospowers.client.compat.mixin;

import com.jaymospowers.client.LockOnClient;
import java.util.ArrayList;
import java.util.List;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.collision.TargetFinder;
import net.bettercombat.logic.TargetHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Andy (2026-11-24): "baby zombies are unfightable". Better Combat's swing hits what its flat hitbox, laid along the
 * crosshair, passes through: a side cut's is about a block tall. The lock-on camera eases after its target, so on a
 * baby zombie (under a block tall, quick, close in and below you) it trails behind, the cut passes over its head, and
 * no blow lands. So whatever you're locked onto is struck by your swing whenever it's within the swing's reach, in front
 * of you within its arc ({@link #MIN_ARC} each side at least), in sight, and Better Combat would let you hit it at all.
 */
@Mixin(value = TargetFinder.class, remap = false)
abstract class LockedSwingTargetMixin {
	/** The locked target counts as in front this far round from where you face (degrees each side), or the swing's own half arc if wider. */
	@Unique
	private static final double MIN_ARC = 50.0, MAX_ARC = 90.0;

	@Inject(method = "findAttackTargetResult", at = @At("RETURN"))
	private static void jaymospowers$lockedTarget(Player player, Entity cursorTarget, WeaponAttributes.Attack attack, double attackRange,
		CallbackInfoReturnable<TargetFinder.TargetResult> cir) {
		TargetFinder.TargetResult result = cir.getReturnValue();
		LivingEntity locked = LockOnClient.locked();
		if (result == null || locked == null || player != Minecraft.getInstance().player || result.entities.contains(locked) || !locked.isAlive()
			|| !locked.isAttackable() || !locked.isPickable() || locked.isSpectator() || locked == player.getVehicle()
			|| !TargetHelper.isHitAllowed(false, TargetHelper.getRelation(player, locked))) {
			return;
		}
		Vec3 from = TargetFinder.getInitialTracingPoint(player);
		AABB box = locked.getBoundingBox();
		Vec3 nearest = new Vec3(Mth.clamp(from.x, box.minX, box.maxX), Mth.clamp(from.y, box.minY, box.maxY), Mth.clamp(from.z, box.minZ, box.maxZ));
		if (nearest.distanceTo(from) > attackRange) {
			return;
		}
		Vec3 to = box.getCenter().subtract(from).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() > 1.0E-4) {
			double arc = Mth.clamp(attack.angle() / 2.0, MIN_ARC, MAX_ARC);
			if (to.normalize().dot(Vec3.directionFromRotation(0.0F, player.getYRot())) < Math.cos(Math.toRadians(arc))) {
				return;
			}
		}
		if (!player.hasLineOfSight(locked)) {
			return;
		}
		List<Entity> hit = new ArrayList<>(result.entities);
		hit.add(locked);
		result.entities = hit;
	}
}
