package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Dynamite;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A witch holds a harmful splash while one of her own stands where it would break (docs/Pawn-Plan.md, what not to copy:
 * a friend in the blast). The potions she throws to heal raiders go as before.
 */
@Mixin(Witch.class)
abstract class WitchBlastMixin {
	/** A splash potion's reach (blocks). */
	private static final double SPLASH = 4.0;

	@Inject(method = "performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$holdForFriends(LivingEntity target, float power, CallbackInfo ci) {
		Witch witch = (Witch) (Object) this;
		if (target instanceof Raider || !(witch.level() instanceof ServerLevel level)) {
			return;
		}
		if (Dynamite.friendNear(level, witch, target, target.position(), SPLASH, e -> false)) {
			ci.cancel();
		}
	}
}
