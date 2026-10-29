package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.WillSkill;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * (0.17.183) Kingdoms' Will Over Flesh: another player's power moves a player with it less ({@link WillSkill#pushed}).
 * Every push, knockback and fling comes through here.
 */
@Mixin(Entity.class)
abstract class WillPushMixin {
	@ModifyVariable(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true)
	private Vec3 jaymospowers$willOverFlesh(Vec3 motion) {
		return WillSkill.pushed((Entity) (Object) this, motion);
	}
}
