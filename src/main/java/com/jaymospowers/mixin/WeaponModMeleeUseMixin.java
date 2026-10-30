package com.jaymospowers.mixin;

import com.jaymospowers.armory.HammerSlam;
import com.jaymospowers.armory.KnifeGuard;
import com.jaymospowers.armory.Lash;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * decides how long use lasts and what letting go does). (0.17.98) The warhammer charges held overhead
 * ({@link HammerSlam}), and raises a guard while the smash cools down. (0.18.61) The knife's guard lasts while it's held
 * and letting go throws nothing ({@link KnifeGuard}; combat/Guard raises it). Every other WeaponMod weapon is left as it was.
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
		} else if (KnifeGuard.isKnife((Item)(Object)this) && KnifeGuard.raisedAsGuard(user)) {
			cir.setReturnValue(KnifeGuard.guardDuration());
		}
	}

	@Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$whirlPose(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
		if (this.jaymospowers$flail()) {
			cir.setReturnValue(Lash.useAnimation(Lash.FLAIL));
		} else if (HammerSlam.isWarhammer((Item)(Object)this)) {
			// (0.17.98) the smash charges with the hammer held up overhead
			cir.setReturnValue(HammerSlam.chargePose());
		}
	}

	@Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$letGo(ItemStack stack, Level level, LivingEntity entity, int remaining, CallbackInfoReturnable<Boolean> cir) {
		if (this.jaymospowers$flail()) {
			Lash.release(level, entity);
			cir.setReturnValue(false);
		} else if (HammerSlam.isWarhammer((Item)(Object)this) && HammerSlam.releasesAsGuard(entity)) {
			// (0.17.98) a guard let down: no smash
			cir.setReturnValue(false);
		} else if (KnifeGuard.isKnife((Item)(Object)this) && KnifeGuard.raisedAsGuard(entity)) {
			// (0.18.61) a knife's guard let down: no throw
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$raise(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (HammerSlam.isWarhammer((Item)(Object)this) && !player.getItemInHand(hand).isEmpty()) {
			// (0.17.98) the smash's charge, or the guard while it cools down
			cir.setReturnValue(HammerSlam.use(player, hand));
		}
	}

	@Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$whirlTick(Level level, LivingEntity entity, ItemStack stack, int remaining, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (this.jaymospowers$flail() || KnifeGuard.isKnife((Item)(Object)this) && KnifeGuard.raisedAsGuard(entity)) {
			// the whirl is run by Lash's own tick; the melee component's use tick is for blocking (and a knife's guard is ours)
			ci.cancel();
		}
	}
}
