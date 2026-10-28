package com.jaymospowers.mixin;

import com.jaymospowers.armory.HammerSlam;
import com.jaymospowers.armory.Lash;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.15.148) Balkon's flail is held in use while it whirls (its melee base class, which the flail doesn't override,
 * decides how long use lasts and what letting go does). (0.17.94) The warhammer charges held overhead
 * ({@link HammerSlam}). Every other WeaponMod weapon is left as it was.
 */
@Mixin(targets = "ckathode.weaponmod.item.ItemMelee")
abstract class WeaponModMeleeUseMixin {
	private boolean jaymospowers$flail() {
		return Lash.isFlail((Item)(Object)this);
	}

	@Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$whirlDuration(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
		if (this.jaymospowers$flail()) {
			cir.setReturnValue(Lash.useDuration());
		}
	}

	@Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$whirlPose(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
		if (this.jaymospowers$flail()) {
			cir.setReturnValue(Lash.useAnimation(Lash.FLAIL));
		} else if (HammerSlam.isWarhammer((Item)(Object)this)) {
			// (0.17.94) the smash charges with the hammer held up overhead
			cir.setReturnValue(HammerSlam.chargePose());
		}
	}

	@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$letGo(ItemStack stack, Level level, LivingEntity entity, int remaining, CallbackInfoReturnable<Boolean> cir) {
		if (this.jaymospowers$flail()) {
			Lash.release(level, entity);
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$whirlTick(Level level, LivingEntity entity, ItemStack stack, int remaining, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (this.jaymospowers$flail()) {
			// the whirl is run by Lash's own tick; the melee component's use tick is for blocking
			ci.cancel();
		}
	}
}
