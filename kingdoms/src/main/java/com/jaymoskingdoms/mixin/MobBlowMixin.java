package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Blows;
import com.jaymoskingdoms.DualBlades;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A mob's melee blow, as it lands ({@code Mob.doHurtTarget}): noted for spacing, and a heavy weapon's blow falling from a jump
 * for it is a critical ({@link Blows#dealt}); a two-bladed fighter's off-hand turn hits with its off-hand blade: that blade's damage
 * under the blow's multipliers ({@link DualBlades#attackDamage}), and its enchantments, bonus and wear ({@link DualBlades#weapon}).
 */
@Mixin(Mob.class)
abstract class MobBlowMixin {
	@ModifyExpressionValue(method = "doHurtTarget",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double jaymoskingdoms$offDamage(double damage) {
		return DualBlades.attackDamage((Mob) (Object) this, damage);
	}

	@ModifyExpressionValue(method = "doHurtTarget",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getWeaponItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack jaymoskingdoms$offWeapon(ItemStack weapon) {
		return DualBlades.weapon((Mob) (Object) this, weapon);
	}

	@ModifyArg(method = "doHurtTarget",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
		index = 2)
	private float jaymoskingdoms$falling(float damage) {
		return Blows.dealt((Mob) (Object) this, damage);
	}
}
