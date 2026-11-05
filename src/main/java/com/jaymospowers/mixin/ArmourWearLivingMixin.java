package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourWear;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.20.68) {@link ArmourWear}: armour absorbs more of each hit, heavy and blunt weapons wear it faster, and NPCs' armour
 * wears too (vanilla's {@code LivingEntity#hurtArmor} does nothing; only {@code Player} overrides it).
 */
@Mixin(LivingEntity.class)
abstract class ArmourWearLivingMixin {
	@Shadow
	protected abstract void doHurtEquipment(DamageSource source, float damage, EquipmentSlot... slots);

	@Inject(method = "hurtArmor", at = @At("HEAD"))
	private void jaymospowers$npcArmourWears(DamageSource source, float damage, CallbackInfo ci) {
		if (!((Object) this instanceof Player)) {
			doHurtEquipment(source, damage, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
		}
	}

	@ModifyVariable(method = "doHurtEquipment", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$heavyWear(float damage, @Local(argsOnly = true) DamageSource source) {
		return ArmourWear.wear(source, damage);
	}

	@ModifyArg(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"),
		index = 3)
	private float jaymospowers$armourAbsorbs(float armor) {
		return armor * ArmourWear.ARMOR_ABSORB;
	}

	@ModifyArg(method = "getDamageAfterArmorAbsorb", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"),
		index = 4)
	private float jaymospowers$toughnessAbsorbs(float toughness) {
		return toughness * ArmourWear.TOUGHNESS_ABSORB;
	}
}
