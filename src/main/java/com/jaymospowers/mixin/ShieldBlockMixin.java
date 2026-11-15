package com.jaymospowers.mixin;

import com.jaymospowers.combat.ShieldCraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shields (ShieldCraft): a raised shield blocks at once, without vanilla's quarter-second delay (a weapon's guard had
 * none already), and a Barbed shield bites whoever strikes it.
 */
@Mixin(LivingEntity.class)
abstract class ShieldBlockMixin {
	@Redirect(method = "getItemBlockingWith", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/component/BlocksAttacks;blockDelayTicks()I"))
	private int jaymospowers$instantBlock(BlocksAttacks blocks) {
		return 0;
	}

	@Inject(method = "blockUsingItem", at = @At("HEAD"))
	private void jaymospowers$barbed(ServerLevel level, LivingEntity attacker, DamageSource source, float damage, boolean fullyBlocked, CallbackInfo ci) {
		ShieldCraft.barbs((LivingEntity) (Object) this, attacker);
	}
}
