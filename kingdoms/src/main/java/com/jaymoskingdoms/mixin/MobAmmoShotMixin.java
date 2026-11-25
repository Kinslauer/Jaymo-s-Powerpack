package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.MobAmmo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game's bowmen and crossbowmen spend an arrow for every shot, and loose none with none left ({@link MobAmmo}). */
@Mixin({AbstractSkeleton.class, Pillager.class, Piglin.class, Illusioner.class})
abstract class MobAmmoShotMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$spendArrow(LivingEntity target, float power, CallbackInfo ci) {
		// (a crossbow with nothing loaded looses nothing: no arrow spent for it)
		net.minecraft.world.item.ItemStack crossbow = ((Mob) (Object) this).getItemInHand(
			net.minecraft.world.entity.projectile.ProjectileUtil.getWeaponHoldingHand((Mob) (Object) this, net.minecraft.world.item.Items.CROSSBOW));
		if (crossbow.getItem() instanceof net.minecraft.world.item.CrossbowItem && !net.minecraft.world.item.CrossbowItem.isCharged(crossbow)) {
			ci.cancel();
			return;
		}
		// (and none with the shield held up: MobShields#holdsFire)
		if (com.jaymoskingdoms.MobShields.holdsFire((Mob) (Object) this, false) || !MobAmmo.spend((Mob) (Object) this, MobAmmo.Kind.ARROWS)) {
			ci.cancel();
		}
	}
}
