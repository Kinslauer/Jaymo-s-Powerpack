package com.jaymospowers.dino.entity.ai;

import com.jaymospowers.dino.entity.Dinosaur;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;

public class DinoFollowOwnerGoal extends FollowOwnerGoal {
   private final Dinosaur dino;

   public DinoFollowOwnerGoal(Dinosaur dino) {
      super(dino, dino.species().runBoost * 0.85, 7.0F + dino.species().width * 1.5F, 2.5F + dino.species().width);
      this.dino = dino;
   }

   private boolean allowed() {
      return this.dino.isTame() && this.dino.getMode() == 0 && !this.dino.isKnockedOut() && !this.dino.isVehicle() && !this.dino.isBusy();
   }

   public boolean canUse() {
      return this.allowed() && super.canUse();
   }

   public boolean canContinueToUse() {
      return this.allowed() && super.canContinueToUse();
   }

   public void start() {
      super.start();
      this.dino.setRunning(true);
   }

   public void stop() {
      super.stop();
      this.dino.setRunning(false);
   }
}
