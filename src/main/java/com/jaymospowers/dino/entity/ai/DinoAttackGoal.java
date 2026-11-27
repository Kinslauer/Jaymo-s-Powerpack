package com.jaymospowers.dino.entity.ai;

import com.jaymospowers.dino.entity.Attacks;
import com.jaymospowers.dino.entity.Dinosaur;
import com.jaymospowers.dino.entity.Species;
import java.util.EnumSet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.player.Player;

public class DinoAttackGoal extends Goal {
   private final Dinosaur dino;
   private int repath;
   private int seen;

   public DinoAttackGoal(Dinosaur dino) {
      this.dino = dino;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
   }

   private boolean valid(LivingEntity target) {
      if (target != null && target.isAlive() && target.level() == this.dino.level() && this.dino.canAttack(target)) {
         if (target instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return false;
         } else {
            if (target instanceof Player && !this.dino.isTame() && this.dino.level().getDifficulty() == Difficulty.PEACEFUL) {
               return false;
            }

            if (!this.dino.isTame()
               && !this.dino.species().predator()
               && target instanceof Dinosaur
               && this.dino.getLastHurtByMob() != target
               && this.dino.distanceToSqr(target) > 400.0) {
               return false;
            }

            double range = this.dino.getAttributeValue(Attributes.FOLLOW_RANGE) * 1.5;
            return this.dino.distanceToSqr(target) < range * range;
         }
      } else {
         return false;
      }
   }

   public boolean canUse() {
      LivingEntity target = this.dino.getTarget();
      if (target != null && !this.valid(target)) {
         this.dino.setTarget(null);
         return false;
      } else {
         return target != null
            && !this.dino.isKnockedOut()
            && !this.dino.isVehicle()
            && !this.dino.isBaby()
            && (!this.dino.isTame() || !this.dino.isOrderedToSit() || !(this.dino.distanceToSqr(target) > 100.0));
      }
   }

   public boolean canContinueToUse() {
      return this.canUse();
   }

   public void start() {
      this.repath = 0;
      this.seen = 0;
      if (this.dino.species() == Species.DILOPHOSAURUS) {
         this.dino.display(70);
      }
   }

   public void stop() {
      this.dino.setRunning(false);
      this.dino.getNavigation().stop();
   }

   public boolean requiresUpdateEveryTick() {
      return true;
   }

   public void tick() {
      LivingEntity target = this.dino.getTarget();
      if (target != null) {
         Species s = this.dino.species();
         this.dino.getLookControl().setLookAt(target, 30.0F, 30.0F);
         double gap = Attacks.gap(this.dino, target);
         double reach = s.reach * this.dino.getAgeScale();
         this.seen++;
         if (s == Species.DILOPHOSAURUS && this.seen < 30 && gap > 2.0) {
            this.dino.display(40);
            this.dino.getNavigation().stop();
         } else if (!Attacks.wildSpecial(this.dino, target)) {
            boolean rooted = Attacks.isRoaring(this.dino) || this.dino.isLeaping();
            if (rooted) {
               this.dino.getNavigation().stop();
            } else {
               if (gap <= reach) {
                  this.dino.setRunning(false);
                  if (!this.dino.isCharging()) {
                     this.dino.getNavigation().stop();
                     if (target instanceof Dinosaur && gap < reach * 0.55) {
                        this.dino.lookAt(target, s.turnRate, 30.0F);
                        this.dino.getMoveControl().strafe(-0.6F, 0.0F);
                     }
                  }

                  this.dino.tryStartAttack();
               } else if (--this.repath <= 0) {
                  this.repath = gap > 12.0 ? 10 : 4;
                  this.dino.setRunning(gap > 3.0);
                  this.dino.getNavigation().moveTo(target, gap > 3.0 ? s.runBoost : 1.2);
               }
            }
         }
      }
   }
}
