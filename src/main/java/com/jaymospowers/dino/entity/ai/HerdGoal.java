package com.jaymospowers.dino.entity.ai;

import com.jaymospowers.dino.entity.Dinosaur;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;

public class HerdGoal extends Goal {
   private final Dinosaur dino;
   private Dinosaur leader;
   private int cooldown;

   public HerdGoal(Dinosaur dino) {
      this.dino = dino;
      this.setFlags(EnumSet.of(Flag.MOVE));
   }

   public boolean canUse() {
      if (!this.dino.isTame() && this.dino.species().packMax() >= 2 && !this.dino.isKnockedOut() && this.dino.getTarget() == null) {
         if (--this.cooldown > 0) {
            return false;
         }

         this.cooldown = 60 + this.dino.getRandom().nextInt(60);
         List<Dinosaur> kin = this.dino
            .level()
            .getEntitiesOfClass(
               Dinosaur.class,
               this.dino.getBoundingBox().inflate(32.0, 8.0, 32.0),
               ox -> ox != this.dino && ox.species() == this.dino.species() && !ox.isTame() && !ox.isKnockedOut() && ox.isAlive()
            );
         Dinosaur nearest = null;
         double best = Double.MAX_VALUE;

         for (Dinosaur o : kin) {
            double d = this.dino.distanceToSqr(o);
            if (d < best) {
               best = d;
               nearest = o;
            }
         }

         double close = 6.0F + this.dino.getBbWidth() * 3.0F;
         if (nearest != null && !(best < close * close)) {
            this.leader = nearest;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean canContinueToUse() {
      double close = 4.0F + this.dino.getBbWidth() * 2.0F;
      return this.leader != null
         && this.leader.isAlive()
         && !this.dino.getNavigation().isDone()
         && this.dino.distanceToSqr(this.leader) > close * close
         && this.dino.getTarget() == null;
   }

   public void start() {
      this.dino.getNavigation().moveTo(this.leader, 1.1);
   }

   public void stop() {
      this.leader = null;
   }
}
