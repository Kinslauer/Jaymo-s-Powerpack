package com.jaymospowers.dino.entity.ai;

import com.jaymospowers.dino.DinoConfig;
import com.jaymospowers.dino.entity.Dinosaur;
import com.jaymospowers.dino.entity.Species;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

public class DinoHuntGoal extends NearestAttackableTargetGoal<LivingEntity> {
   private final Dinosaur dino;

   public DinoHuntGoal(Dinosaur dino) {
      super(dino, LivingEntity.class, 20, true, false, (target, level) -> wants(dino, target, level));
      this.dino = dino;
   }

   public boolean canUse() {
      Species s = this.dino.species();
      return !this.dino.isTame() && !this.dino.isKnockedOut() && !this.dino.isBaby() && (s.predator() || s == Species.TRICERATOPS) ? super.canUse() : false;
   }

   private static boolean wants(Dinosaur d, LivingEntity target, ServerLevel level) {
      Species s = d.species();
      double distSq = d.distanceToSqr(target);
      if (target instanceof Player) {
         return switch (s) {
            case TYRANNOSAURUS, VELOCIRAPTOR, SPINOSAURUS, CARNOTAURUS -> true;
            case DILOPHOSAURUS -> distSq < 144.0;
            default -> false;
         };
      } else if (target instanceof Dinosaur other) {
         if (!DinoConfig.DINOSAURS_FIGHT || other.isKnockedOut() || other.isTame() || other.species() == s) {
            return false;
         } else {
            return s == Species.TRICERATOPS
               ? other.species().predator() && other.species().big() && !other.isTame() && distSq < 144.0
               : d.isHungry() && s.hunts(other.species(), other.isBaby());
         }
      } else {
         boolean owned = target instanceof OwnableEntity o && o.getOwnerReference() != null;
         if (d.isHungry() && target instanceof Animal && !owned && !(target instanceof TamableAnimal pet && pet.isTame())) {
            return switch (s) {
               case TYRANNOSAURUS, SPINOSAURUS, CARNOTAURUS -> true;
               case VELOCIRAPTOR -> target.getMaxHealth() <= 40.0F;
               case DILOPHOSAURUS -> target.getMaxHealth() <= 20.0F;
               default -> false;
            };
         } else {
            return false;
         }
      }
   }
}
