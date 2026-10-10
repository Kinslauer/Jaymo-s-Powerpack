package com.jaymoskitchen.mixin;

import com.jaymoskitchen.archery.LegShots;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob knocked down by a leg hit can't shoot, throw or fling anything until it's back up. */
@Mixin({AbstractSkeleton.class, Witch.class, Drowned.class, Pillager.class, Piglin.class, Illusioner.class})
abstract class RangedAttackMobMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$downed(LivingEntity target, float power, CallbackInfo ci) {
		if (LegShots.isCrawling((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}
