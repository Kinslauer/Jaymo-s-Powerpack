package com.jaymospowers.mixin;

import com.jaymospowers.armory.HammerSlam;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.17.94) Balkon's warhammer smash swings down from overhead first ({@link HammerSlam}). */
@Mixin(targets = "ckathode.weaponmod.item.MeleeCompWarhammer")
abstract class WeaponModWarhammerMixin {
	/** Set while the delayed smash runs WeaponMod's own, so it isn't taken over again. */
	@Unique
	private static final ThreadLocal<Boolean> jaymospowers$landing = ThreadLocal.withInitial(() -> false);

	@Shadow
	protected abstract void superSmash(ItemStack stack, Level level, LivingEntity entity);

	@Inject(method = "superSmash", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$overhead(ItemStack stack, Level level, LivingEntity entity, CallbackInfo ci) {
		if (jaymospowers$landing.get()) {
			return;
		}
		if (HammerSlam.slam(stack, level, entity, () -> {
			jaymospowers$landing.set(true);
			try {
				this.superSmash(stack, level, entity);
			} finally {
				jaymospowers$landing.set(false);
			}
		})) {
			ci.cancel();
		}
	}
}
