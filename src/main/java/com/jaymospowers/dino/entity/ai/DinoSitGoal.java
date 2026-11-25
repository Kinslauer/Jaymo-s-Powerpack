package com.jaymospowers.dino.entity.ai;

import com.jaymospowers.dino.entity.Dinosaur;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;

public class DinoSitGoal extends Goal {
   private final Dinosaur dino;

   public DinoSitGoal(Dinosaur dino) {
      this.dino = dino;
      this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
   }

   private boolean threatened() {
      LivingEntity t = this.dino.getTarget();
      return t != null && t.isAlive() && this.dino.distanceToSqr(t) < 100.0;
   }

   public boolean canUse() {
      return this.dino.isTame()
         && this.dino.isOrderedToSit()
         && !this.dino.isVehicle()
         && !this.dino.isKnockedOut()
         && !this.dino.isInWater()
         && this.dino.onGround()
         && !this.threatened();
   }

   public boolean canContinueToUse() {
      return this.canUse();
   }

   public void start() {
      this.dino.getNavigation().stop();
      this.dino.setInSittingPose(true);
   }

   public void stop() {
      this.dino.setInSittingPose(false);
   }
}
