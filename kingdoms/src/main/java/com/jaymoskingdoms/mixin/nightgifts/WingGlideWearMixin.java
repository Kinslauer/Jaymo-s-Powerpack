package com.jaymoskingdoms.mixin.nightgifts;

import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * (0.7.61) Gliding wears the glider it's done on; gliding on bat wings ({@code NightGifts}) there's none, so vanilla's
 * pick from an empty list would throw: then it picks the (empty) chest slot, and wearing that does nothing. The wings
 * tire their own way.
 */
@Mixin(LivingEntity.class)
abstract class WingGlideWearMixin {
	@Redirect(method = "updateFallFlying",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getRandom(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/lang/Object;"))
	private Object jaymoskingdoms$noGlider(List<?> slots, RandomSource random) {
		return slots.isEmpty() ? EquipmentSlot.CHEST : Util.getRandom(slots, random);
	}
}
