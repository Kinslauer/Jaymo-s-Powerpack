package com.jaymospowers.mixin;

import com.jaymospowers.armory.Lash;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.15.148) Balkon's flail no longer throws its ball, on right-click or on every hit: right-click is held to whirl it
 * and let go to bring it down ({@link Lash}), and a hit is a plain blunt blow. With {@link WeaponModMeleeUseMixin}.
 */
@Mixin(targets = "ckathode.weaponmod.item.ItemFlail")
abstract class WeaponModFlailMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$whirl(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		cir.setReturnValue(Lash.use(level, player, hand, Lash.FLAIL));
	}

	@Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noThrow(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfo ci) {
		ci.cancel();
	}
}
