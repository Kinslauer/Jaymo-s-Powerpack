package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.Lean;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** An NPC leaning out past a corner ({@link Lean#lean}) sees from where its head has leaned out to, so it can shoot from there. */
@Mixin(LivingEntity.class)
abstract class LeanSightMixin {
	@ModifyVariable(
		method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Z",
		at = @At("STORE"), ordinal = 0)
	private Vec3 jaymoskingdoms$leanedEye(Vec3 eye) {
		return (Object)this instanceof Mob mob && Lean.dir(mob) != 0 ? eye.add(Lean.shift(mob, 1.0)) : eye;
	}
}
