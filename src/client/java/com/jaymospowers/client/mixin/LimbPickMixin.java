package com.jaymospowers.client.mixin;

import com.jaymospowers.core.LimbBoxes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The crosshair picks a person only where it crosses their body ({@link LimbBoxes}): between the legs looks past them. */
@Mixin(LocalPlayer.class)
abstract class LimbPickMixin {
	@WrapOperation(method = "pick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"))
	private static EntityHitResult jaymospowers$limbPick(Entity viewer, Vec3 from, Vec3 to, AABB area, Predicate<Entity> canPick, double range,
		Operation<EntityHitResult> original) {
		return original.call(viewer, from, to, area, canPick.and(e -> LimbBoxes.crosses(e, from, to)), range);
	}
}
