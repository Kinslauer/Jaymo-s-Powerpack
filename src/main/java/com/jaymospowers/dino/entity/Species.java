package com.jaymospowers.dino.entity;

import com.jaymospowers.dino.rig.DinoSpec;
import com.jaymospowers.dino.rig.KModel;
import com.jaymospowers.dino.rig.Skeleton;

public enum Species {
   TYRANNOSAURUS("tyrannosaurus", Species.Diet.CARNIVORE, 2.9F, 4.5F, 4.1F, 220.0, 18.0, 2.2, 6.2, 8.0, 1.0, 300.0F, 60.0F, 60, 1.6F, 3.4F, 7.0F),
   VELOCIRAPTOR("velociraptor", Species.Diet.CARNIVORE, 0.95F, 1.9F, 1.75F, 36.0, 7.0, 2.6, 8.0, 2.0, 0.2, 55.0F, 16.0F, 12, 1.0F, 1.5F, 30.0F),
   DILOPHOSAURUS("dilophosaurus", Species.Diet.CARNIVORE, 1.5F, 2.8F, 2.5F, 60.0, 8.0, 2.3, 6.5, 2.0, 0.3, 90.0F, 24.0F, 16, 1.1F, 1.9F, 22.0F),
   GALLIMIMUS("gallimimus", Species.Diet.OMNIVORE, 1.1F, 2.6F, 2.5F, 30.0, 3.0, 3.0, 11.0, 0.0, 0.0, 44.0F, 14.0F, 6, 1.0F, 1.5F, 30.0F),
   TRICERATOPS("triceratops", Species.Diet.HERBIVORE, 2.9F, 3.0F, 2.3F, 160.0, 14.0, 1.8, 5.0, 12.0, 0.9, 220.0F, 44.0F, 30, 1.3F, 3.0F, 9.0F),
   PARASAUROLOPHUS("parasaurolophus", Species.Diet.HERBIVORE, 2.4F, 3.3F, 2.9F, 110.0, 7.0, 2.0, 6.5, 4.0, 0.6, 140.0F, 30.0F, 20, 1.3F, 2.8F, 10.0F),
   BRACHIOSAURUS("brachiosaurus", Species.Diet.HERBIVORE, 4.8F, 7.2F, 6.8F, 480.0, 26.0, 1.6, 3.0, 10.0, 1.0, 600.0F, 100.0F, 80, 2.1F, 4.6F, 4.0F),
   SPINOSAURUS("spinosaurus", Species.Diet.CARNIVORE, 3.0F, 5.2F, 4.0F, 260.0, 20.0, 2.2, 6.0, 8.0, 1.0, 340.0F, 70.0F, 70, 1.6F, 3.6F, 6.0F),
   CARNOTAURUS("carnotaurus", Species.Diet.CARNIVORE, 1.9F, 3.2F, 2.9F, 130.0, 12.0, 2.4, 8.5, 4.0, 0.6, 170.0F, 36.0F, 30, 1.3F, 2.4F, 12.0F),
   DODO("dodo", Species.Diet.HERBIVORE, 0.6F, 0.95F, 0.8F, 10.0, 1.0, 1.2, 3.0, 0.0, 0.0, 10.0F, 4.0F, 2, 0.6F, 0.6F, 30.0F);

   public final String id;
   public final Species.Diet diet;
   public final float width;
   public final float height;
   public final float eyeHeight;
   public final double health;
   public final double damage;
   public final double armor;
   public final double knockbackResistance;
   public final double walkBps;
   public final double runBps;
   public final double speed;
   public final double runBoost;
   public final float torpor;
   public final float tameNeed;
   public final int xp;
   public final float stepHeight;
   public final float reach;
   public final float turnRate;
   private DinoSpec spec;
   private float[] seat;
   private float[] mouth;

   Species(
      String id,
      Species.Diet diet,
      float width,
      float height,
      float eyeHeight,
      double health,
      double damage,
      double walkBps,
      double runBps,
      double armor,
      double knockbackResistance,
      float torpor,
      float tameNeed,
      int xp,
      float stepHeight,
      float reach,
      float turnRate
   ) {
      this.id = id;
      this.diet = diet;
      this.width = width;
      this.height = height;
      this.eyeHeight = eyeHeight;
      this.health = health;
      this.damage = damage;
      this.walkBps = walkBps;
      this.runBps = runBps;
      this.speed = Math.sqrt(walkBps / 43.17);
      this.runBoost = Math.sqrt(runBps / walkBps);
      this.armor = armor;
      this.knockbackResistance = knockbackResistance;
      this.torpor = torpor;
      this.tameNeed = tameNeed;
      this.xp = xp;
      this.stepHeight = stepHeight;
      this.reach = reach;
      this.turnRate = turnRate;
   }

   public boolean big() {
      return this == TYRANNOSAURUS || this == TRICERATOPS || this == PARASAUROLOPHUS || this == BRACHIOSAURUS || this == SPINOSAURUS || this == CARNOTAURUS;
   }

   public boolean rideable() {
      return this != DODO;
   }

   public boolean fightsBack() {
      return this != GALLIMIMUS && this != PARASAUROLOPHUS && this != DODO;
   }

   public boolean hunts(Species prey, boolean preyIsBaby) {
      if (this.predator() && !prey.predator() && prey != this) {
         return switch (this) {
            case TYRANNOSAURUS, SPINOSAURUS -> true;
            case VELOCIRAPTOR, CARNOTAURUS -> prey != BRACHIOSAURUS || preyIsBaby;
            case DILOPHOSAURUS -> prey == DODO || prey == GALLIMIMUS || prey == PARASAUROLOPHUS || preyIsBaby;
            default -> false;
         };
      } else {
         return false;
      }
   }

   public boolean fears(Species other) {
      return other.predator() && other != this && other.health >= this.health * 2.0 && (!this.big() || !this.fightsBack());
   }

   public boolean hasParts() {
      return this.big() || this == DILOPHOSAURUS;
   }

   public boolean eatsMeat() {
      return this.diet != Species.Diet.HERBIVORE;
   }

   public boolean eatsPlants() {
      return this.diet != Species.Diet.CARNIVORE;
   }

   public boolean predator() {
      return this.diet == Species.Diet.CARNIVORE;
   }

   public boolean jumps() {
      return this == VELOCIRAPTOR || this == GALLIMIMUS || this == DILOPHOSAURUS;
   }

   public boolean breaksLeaves() {
      return this.big();
   }

   public float cameraDistance() {
      return switch (this) {
         case TYRANNOSAURUS -> 10.0F;
         default -> 4.5F;
         case DILOPHOSAURUS -> 6.5F;
         case GALLIMIMUS -> 5.5F;
         case TRICERATOPS -> 8.5F;
         case PARASAUROLOPHUS -> 8.5F;
         case BRACHIOSAURUS -> 18.0F;
         case SPINOSAURUS -> 11.5F;
         case CARNOTAURUS -> 7.5F;
      };
   }

   public int attackCooldown() {
      return switch (this) {
         case TYRANNOSAURUS, SPINOSAURUS -> 34;
         default -> 18;
         case TRICERATOPS, PARASAUROLOPHUS -> 30;
         case BRACHIOSAURUS -> 50;
         case CARNOTAURUS -> 24;
      };
   }

   public int specialCooldown() {
      return switch (this) {
         case TYRANNOSAURUS -> 400;
         case VELOCIRAPTOR -> 100;
         case DILOPHOSAURUS -> 120;
         case GALLIMIMUS -> 300;
         case TRICERATOPS -> 240;
         case PARASAUROLOPHUS -> 500;
         case BRACHIOSAURUS -> 300;
         case SPINOSAURUS -> 320;
         case CARNOTAURUS -> 200;
         case DODO -> 600;
      };
   }

   public int packMin() {
      return switch (this) {
         case VELOCIRAPTOR -> 2;
         default -> 1;
         case GALLIMIMUS, DODO -> 3;
         case TRICERATOPS, PARASAUROLOPHUS -> 2;
      };
   }

   public int packMax() {
      return switch (this) {
         case VELOCIRAPTOR -> 4;
         case DILOPHOSAURUS, BRACHIOSAURUS, CARNOTAURUS -> 2;
         case GALLIMIMUS, DODO -> 6;
         case TRICERATOPS -> 3;
         case PARASAUROLOPHUS -> 4;
         default -> 1;
      };
   }

   public DinoSpec spec() {
      if (this.spec == null) {
         this.spec = DinoSpec.of(this.id);
      }

      return this.spec;
   }

   private void loadAnchors() {
      if (this.seat == null) {
         float[] s = new float[]{0.0F, this.height, 0.0F};
         float[] m = new float[]{0.0F, this.eyeHeight, this.width};

         try {
            Skeleton sk = KModel.load(this.id, false).skeleton;
            int a = sk.anchorIndex("seat");
            if (a >= 0) {
               s = new float[]{sk.anchorX[a], sk.anchorY[a], sk.anchorZ[a]};
            }

            a = sk.anchorIndex("mouth");
            if (a >= 0) {
               m = new float[]{sk.anchorX[a], sk.anchorY[a], sk.anchorZ[a]};
            }
         } catch (RuntimeException var5) {
         }

         this.seat = s;
         this.mouth = m;
      }
   }

   public float[] seat() {
      this.loadAnchors();
      return this.seat;
   }

   public float[] mouth() {
      this.loadAnchors();
      return this.mouth;
   }

   public static Species byId(String id) {
      for (Species s : values()) {
         if (s.id.equals(id)) {
            return s;
         }
      }

      return VELOCIRAPTOR;
   }

   public enum Diet {
      CARNIVORE,
      HERBIVORE,
      OMNIVORE;
   }
}
