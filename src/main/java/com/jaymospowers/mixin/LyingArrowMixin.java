package com.jaymospowers.mixin;

import com.jaymospowers.core.LimbBoxes;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Arrows and other projectiles hit someone downed where they lie ({@link LimbBoxes#lyingHit}), not where the standing
 * box the game tests would have them.
 */
@Mixin(ProjectileUtil.class)
abstract class LyingArrowMixin {
	@Inject(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/EntityHitResult;",
		at = @At("RETURN"), cancellable = true)
	private static void jaymospowers$lyingShot(Level level, Projectile projectile, Vec3 from, Vec3 to, AABB area, Predicate<Entity> filter,
		CallbackInfoReturnable<EntityHitResult> cir) {
		cir.setReturnValue(LimbBoxes.lyingHit(level, projectile, from, to, area, filter, cir.getReturnValue(), -1.0));
	}

	@Inject(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
		at = @At("RETURN"), cancellable = true)
	private static void jaymospowers$lyingShot(Level level, Entity source, Vec3 from, Vec3 to, AABB area, Predicate<Entity> filter, float margin,
		CallbackInfoReturnable<EntityHitResult> cir) {
		cir.setReturnValue(LimbBoxes.lyingHit(level, source, from, to, area, filter, cir.getReturnValue(), -1.0));
	}
}
