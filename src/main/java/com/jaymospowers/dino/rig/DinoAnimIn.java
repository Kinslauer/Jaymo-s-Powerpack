package com.jaymospowers.dino.rig;

public final class DinoAnimIn {
   public float time;
   public float walkPhase;
   public float walk;
   public float run;
   public float turn;
   public float lookYaw;
   public float lookPitch;
   public int action;
   public float actionT;
   public float sleep;
   public float sit;
   public float death = -1.0F;
   public float air;
   public float display;
   public float charge;
   public float swim;
   public float hurt;
   public boolean saddled;
   public boolean baby;
   public float seed;
   public static final float TAU = (float) (Math.PI * 2);

   public DinoAnimIn reset() {
      this.time = this.walkPhase = this.walk = this.run = this.turn = this.lookYaw = this.lookPitch = this.actionT = this.sleep = this.sit = this.air = this.display = this.charge = this.swim = this.hurt = this.seed = 0.0F;
      this.action = 0;
      this.death = -1.0F;
      this.saddled = false;
      this.baby = false;
      return this;
   }

   public static float clamp01(float x) {
      return x < 0.0F ? 0.0F : Math.min(x, 1.0F);
   }

   public static float ramp(float a, float b, float x) {
      float t = clamp01((x - a) / (b - a));
      return t * t * (3.0F - 2.0F * t);
   }

   public static float window(float a, float b, float c, float d, float x) {
      return ramp(a, b, x) * (1.0F - ramp(c, d, x));
   }

   public static float sin(float x) {
      return (float)Math.sin(x);
   }

   public static float cos(float x) {
      return (float)Math.cos(x);
   }

   public static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }
}
