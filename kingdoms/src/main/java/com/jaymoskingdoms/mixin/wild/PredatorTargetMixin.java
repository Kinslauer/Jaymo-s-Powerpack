package com.jaymoskingdoms.mixin.wild;

import com.jaymoskingdoms.wild.FedPredators;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A hunter's own search for quarry, once it has found an animal, goes through {@link FedPredators#choose}: a fed
 * predator takes none, a hungry one takes the young first. Searches that found a player, a villager or a monster, and
 * hunters outside the {@code sated_predators} tag, are left exactly as they were.
 */
@Mixin(NearestAttackableTargetGoal.class)
abstract class PredatorTargetMixin extends TargetGoal {
	@Shadow
	protected LivingEntity target;
	@Shadow
	@Final
	protected Class<? extends LivingEntity> targetType;
	@Shadow
	@Final
	protected TargetingConditions targetConditions;

	private PredatorTargetMixin(Mob mob, boolean mustSee) {
		super(mob, mustSee);
	}

	@Shadow
	protected abstract AABB getTargetSearchArea(double range);

	@Inject(method = "findTarget", at = @At("TAIL"))
	private void jaymoskingdoms$fedOrYoungFirst(CallbackInfo ci) {
		if (this.target instanceof Animal) {
			this.target = FedPredators.choose(this.mob, this.target, this.targetType, this.targetConditions,
				this.getTargetSearchArea(this.getFollowDistance()));
		}
	}
}
