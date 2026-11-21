package com.jaymospowers.mixin;

import com.jaymospowers.tinkering.smithing.LightArmour;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mailed and studded leather boots walk over powder snow as leather boots do (vanilla checks for leather boots by item). */
@Mixin(PowderSnowBlock.class)
abstract class LightArmourSnowMixin {
	@ModifyReturnValue(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"))
	private static boolean jaymospowers$lightBoots(boolean original, Entity entity) {
		if (original || !(entity instanceof LivingEntity living)) {
			return original;
		}
		ItemStack boots = living.getItemBySlot(EquipmentSlot.FEET);
		return boots.is(LightArmour.MAILED[3]) || boots.is(LightArmour.STUDDED[3]);
	}
}
