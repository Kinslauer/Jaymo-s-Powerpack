package com.jaymoskingdoms.illagers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;

public class RangedBowAttackWithoutStrafingGoal<T extends Monster & RangedAttackMob> extends RangedBowAttackGoal<T> {
   private final T mob;
   private int attackIntervalBase;

   public RangedBowAttackWithoutStrafingGoal(T mob, double speedModifier, int attackIntervalMin, float attackRadius) {
      super(mob, speedModifier, attackIntervalMin, attackRadius);
      this.mob = mob;
      this.attackIntervalBase = attackIntervalMin;
   }

   public void setMinAttackInterval(int attackCooldown) {
      super.setMinAttackInterval(attackCooldown);
      this.attackIntervalBase = attackCooldown;
   }

   public void tick() {
      com.jaymoskingdoms.mixin.illagers.RangedBowAttackGoalAccessor goal = (com.jaymoskingdoms.mixin.illagers.RangedBowAttackGoalAccessor)this;
      goal.jaymoskingdoms$setStrafingTime(Integer.MIN_VALUE);
      LivingEntity livingEntity = this.mob.getTarget();
      if (livingEntity != null) {
         double distanceToTargetSqr = this.mob.distanceToSqr(livingEntity);
         goal.jaymoskingdoms$setAttackIntervalMin(this.attackIntervalBase - (int)((1.0 - Math.min(distanceToTargetSqr / goal.jaymoskingdoms$attackRadiusSqr(), 1.0)) * 20.0));
      }

      super.tick();
   }
}
