package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.MobAmmo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A witch throws only the splash potions she carries ({@link MobAmmo.Kind#POTIONS}). With none left she throws nothing
 * and backs away from whoever she was aiming at, until she has brewed more while nothing is after her.
 */
@Mixin(Witch.class)
abstract class WitchPotionAmmoMixin {
	/** How far a witch with no potions backs off from her foe (blocks). */
	private static final double JAYMOSKINGDOMS$BACK_OFF = 10.0;

	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$spendPotion(LivingEntity target, float power, CallbackInfo ci) {
		Witch self = (Witch) (Object) this;
		if (ci.isCancelled() || !(self.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		// a throw she holds back for a friend in the blast (WitchBlastMixin) spends nothing
		if (!(target instanceof net.minecraft.world.entity.raid.Raider)
			&& com.jaymoskingdoms.Dynamite.friendNear(level, self, target, target.position(), 4.0, e -> false)) {
			return;
		}
		if (MobAmmo.spend(self, MobAmmo.Kind.POTIONS)) {
			return;
		}
		ci.cancel();
		Vec3 away = self.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 to = self.position().add(away.normalize().scale(JAYMOSKINGDOMS$BACK_OFF));
		self.getNavigation().moveTo(to.x, to.y, to.z, 1.2);
	}
}
