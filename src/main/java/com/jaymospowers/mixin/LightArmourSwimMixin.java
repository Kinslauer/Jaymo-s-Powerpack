package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourEase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Plate weakens the stroke in water (see {@link LightArmour#plateEase}): the push of each swimming tick is scaled down. */
@Mixin(LivingEntity.class)
abstract class LightArmourSwimMixin {
	@ModifyArg(method = "travelInWater", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"), index = 0)
	private float jaymospowers$plateSwim(float speed) {
		return (Object) this instanceof Player player ? speed * ArmourEase.plateEase(player, ArmourEase.PLATE_SWIM) : speed;
	}
}
