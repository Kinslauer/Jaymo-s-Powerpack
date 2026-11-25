package com.jaymospowers.dino.rig;

public final class DinoAnim {
   public static final int NONE = 0;
   public static final int BITE = 1;
   public static final int ROAR = 2;
   public static final int SPECIAL = 3;
   public static final int EAT = 4;
   private final DinoSpec spec;
   private final int body;
   private final int chest;
   private final int head;
   private final int jaw;
   private final int saddle;
   private final int[] neck;
   private final int[] tail;
   private final int[] thigh = new int[2];
   private final int[] shin = new int[2];
   private final int[] ankle = new int[2];
   private final int[] foot = new int[2];
   private final int[] arm = new int[2];
   private final int[] forearm = new int[2];
   private final int[] hand = new int[2];
   private final int[] frill = new int[2];

   public DinoAnim(Skeleton sk, DinoSpec spec) {
      this.spec = spec;
      this.body = sk.index("body");
      this.chest = sk.index("chest");
      this.head = sk.index("head");
      this.jaw = sk.index("jaw");
      this.saddle = sk.index("saddle");
      this.neck = sk.chain("neck");
      this.tail = sk.chain("tail");
      String[] side = new String[]{"_l", "_r"};

      for (int s = 0; s < 2; s++) {
         this.thigh[s] = sk.index("thigh" + side[s]);
         this.shin[s] = sk.index("shin" + side[s]);
         this.ankle[s] = sk.index("ankle" + side[s]);
         this.foot[s] = sk.index("foot" + side[s]);
         this.arm[s] = sk.index("arm" + side[s]);
         this.forearm[s] = sk.index("forearm" + side[s]);
         this.hand[s] = sk.index("hand" + side[s]);
         this.frill[s] = sk.index("frill" + side[s]);
      }
   }

   public DinoSpec spec() {
      return this.spec;
   }

   public float duration(int action) {
      return switch (action) {
         case 1 -> this.spec.biteTime;
         case 2 -> this.spec.roarTime;
         case 3 -> this.spec.specialTime;
         case 4 -> this.spec.eatTime;
         default -> 0.0F;
      };
   }

   private void neckPitch(Pose p, float total) {
      int n = this.neck.length;

      for (int i = 0; i < n; i++) {
         p.rot(this.neck[i], total / Math.max(1, n), 0.0F, 0.0F);
      }
   }

   private void neckYaw(Pose p, float total) {
      int n = this.neck.length;

      for (int i = 0; i < n; i++) {
         p.rot(this.neck[i], 0.0F, total / Math.max(1, n), 0.0F);
      }
   }

   public void apply(DinoAnimIn in, Pose p) {
      p.reset();
      DinoSpec sp = this.spec;
      float t = in.time + in.seed * 7.0F;
      float at = in.actionT;
      float w = in.walk;
      float run = in.run * w;
      float dead = in.death < 0.0F ? 0.0F : DinoAnimIn.ramp(0.0F, 0.9F, in.death);
      float lie = Math.max(in.sleep, dead);
      float sit = in.sit * (1.0F - lie);
      float awake = 1.0F - lie;
      float up = awake * (1.0F - sit);
      w *= up;
      run *= up;
      float breath = DinoAnimIn.sin(t * (lie > 0.5F ? 0.9F : 1.6F));
      p.rot(this.chest, -0.015F * breath, 0.0F, 0.0F);
      p.rot(this.body, 0.0F, 0.015F * DinoAnimIn.sin(t * 0.37F) * up, 0.008F * DinoAnimIn.sin(t * 0.5F) * up);
      float jawOpen = 0.02F * Math.max(0.0F, breath) * awake;
      float display = in.display;
      float ph = in.walkPhase * (float) (Math.PI * 2);
      if (w > 0.001F) {
         float amp = 1.0F + 0.35F * run;
         p.move(this.body, 0.0F, w * sp.bob * amp * (DinoAnimIn.cos(ph * 2.0F) - 0.6F), 0.0F);
         p.rot(this.body, w * 0.02F + run * sp.runLean, w * sp.sway * DinoAnimIn.sin(ph), w * -sp.sway * 0.6F * DinoAnimIn.sin(ph));
         p.rot(this.chest, w * 0.015F * DinoAnimIn.cos(ph * 2.0F), w * -sp.sway * 0.8F * DinoAnimIn.sin(ph), 0.0F);
         this.neckPitch(p, w * -0.05F * DinoAnimIn.cos(ph * 2.0F + 0.5F) - run * sp.runLean * 0.9F);
         p.rot(this.head, w * 0.05F * DinoAnimIn.cos(ph * 2.0F + 1.1F), w * 0.02F * DinoAnimIn.sin(ph), 0.0F);

         for (int s = 0; s < 2; s++) {
            float lp = ph + (s == 0 ? 0.0F : (float) Math.PI);
            float swing = DinoAnimIn.sin(lp);
            float lift = Math.max(0.0F, DinoAnimIn.cos(lp));
            float push = Math.max(0.0F, -DinoAnimIn.cos(lp));
            p.rot(this.thigh[s], w * amp * (-sp.thighSwing * swing - 0.2F * sp.thighSwing * lift), 0.0F, 0.0F);
            p.rot(this.shin[s], w * amp * (sp.shinFlex * lift * lift + 0.08F * push), 0.0F, 0.0F);
            p.rot(this.ankle[s], w * amp * (-sp.ankleFlex * lift * lift + 0.35F * sp.thighSwing * swing), 0.0F, 0.0F);
            p.rot(this.foot[s], w * amp * (sp.footFlex * lift - 0.25F * sp.footFlex * push * Math.max(0.0F, -swing)), 0.0F, 0.0F);
            if (sp.quadruped) {
               float fp = ph + (s == 0 ? (float) Math.PI : 0.0F) - 0.35F;
               float fs = DinoAnimIn.sin(fp);
               float fl = Math.max(0.0F, DinoAnimIn.cos(fp));
               p.rot(this.arm[s], w * amp * (-sp.armSwing * fs - 0.15F * sp.armSwing * fl), 0.0F, 0.0F);
               p.rot(this.forearm[s], w * amp * (sp.forearmFlex * fl * fl), 0.0F, 0.0F);
               p.rot(this.hand[s], w * amp * (0.5F * sp.forearmFlex * fl + 0.3F * sp.armSwing * fs), 0.0F, 0.0F);
            } else {
               float m = s == 0 ? 1.0F : -1.0F;
               p.rot(this.arm[s], w * (sp.armSwing * DinoAnimIn.sin(lp + 0.6F) + 0.25F * run), 0.0F, m * w * 0.02F);
               p.rot(this.forearm[s], w * (-sp.forearmFlex * 0.5F * DinoAnimIn.sin(lp + 1.0F) - 0.2F * run), 0.0F, 0.0F);
            }
         }
      }

      int nt = this.tail.length;

      for (int i = 0; i < nt; i++) {
         float f = (float)(i + 1) / Math.max(1, nt);
         float gain = 0.4F + f * 0.9F;
         float idle = sp.tailIdle * DinoAnimIn.sin(t * 0.8F - i * 0.5F) * gain * awake;
         float wave = w * sp.tailWalk * DinoAnimIn.sin(ph - 0.8F - i * 0.55F) * gain;
         float drag = -in.turn * 0.12F * (0.4F + f) * up;
         p.rot(
            this.tail[i],
            awake * (0.012F * DinoAnimIn.sin(t * 0.9F - i * 0.6F) * gain - run * 0.02F) + w * 0.012F * DinoAnimIn.cos(ph * 2.0F - i * 0.6F),
            idle + wave + drag,
            0.0F
         );
      }

      if (in.air > 0.001F) {
         float a = in.air * up;
         p.rot(this.body, -0.38F * a, 0.0F, 0.0F);
         this.neckPitch(p, 0.3F * a);

         for (int s = 0; s < 2; s++) {
            float m = s == 0 ? 1.0F : -1.0F;
            p.rot(this.thigh[s], -0.95F * a, 0.0F, 0.0F);
            p.rot(this.shin[s], 0.55F * a, 0.0F, 0.0F);
            p.rot(this.ankle[s], -0.45F * a, 0.0F, 0.0F);
            p.rot(this.foot[s], -0.5F * a, 0.0F, 0.0F);
            p.rot(this.arm[s], -0.9F * a, 0.0F, m * 0.25F * a);
            p.rot(this.forearm[s], -0.3F * a, 0.0F, 0.0F);
         }

         for (int i = 0; i < nt; i++) {
            p.rot(this.tail[i], -0.05F * a, 0.0F, 0.0F);
         }

         jawOpen = Math.max(jawOpen, 0.7F * a);
      }

      if (in.charge > 0.001F) {
         float c = in.charge * up;
         p.rot(this.chest, 0.05F * c, 0.0F, 0.0F);
         this.neckPitch(p, 0.3F * c);
         p.rot(this.head, 0.28F * c, 0.0F, 0.0F);
      }

      float lookGain = up;
      switch (awake > 0.5F ? in.action : 0) {
         case 1:
            float ux = at / sp.biteTime;
            float hit = sp.biteHit / sp.biteTime;
            switch (sp.attackStyle) {
               case 1:
                  float dip = DinoAnimIn.window(0.0F, hit * 0.6F, hit * 0.75F, hit * 1.05F, ux);
                  float thrust = DinoAnimIn.window(hit * 0.8F, hit * 1.1F, hit * 1.5F, 1.0F, ux);
                  float lift = sp.headLift;
                  p.rot(this.chest, 0.06F * dip - 0.08F * thrust * lift, 0.0F, 0.0F);
                  this.neckPitch(p, 0.35F * dip - 0.3F * thrust * lift);
                  p.rot(this.head, 0.35F * dip - 0.4F * thrust * lift, 0.0F, 0.0F);
                  p.move(this.body, 0.0F, 0.0F, 0.25F * thrust);
                  break;
               case 2:
                  float rear = DinoAnimIn.window(0.0F, hit * 0.55F, hit * 0.8F, hit * 1.02F, ux);
                  float land = DinoAnimIn.window(hit * 0.98F, hit * 1.1F, hit * 1.3F, 1.0F, ux);
                  p.rot(this.body, -sp.rear * 0.6F * rear + 0.03F * land, 0.0F, 0.0F);
                  this.neckPitch(p, sp.rear * 0.45F * rear);
                  p.move(this.body, 0.0F, -0.12F * land, 0.0F);

                  for (int s = 0; s < 2; s++) {
                     p.rot(this.arm[s], -0.5F * rear, 0.0F, 0.0F);
                     p.rot(this.forearm[s], 0.7F * rear, 0.0F, 0.0F);
                     p.rot(this.thigh[s], sp.rear * 0.6F * rear, 0.0F, 0.0F);
                  }
                  break;
               case 3:
                  float back = DinoAnimIn.window(0.0F, hit * 0.6F, hit * 0.7F, hit * 0.95F, ux);
                  float swing = DinoAnimIn.window(hit * 0.75F, hit * 1.05F, hit * 1.4F, 1.0F, ux);
                  float yaw = 0.55F * back - 0.75F * swing;
                  p.rot(this.body, 0.0F, 0.15F * yaw, 0.0F);
                  p.rot(this.chest, 0.0F, 0.25F * yaw, 0.0F);
                  this.neckYaw(p, 0.8F * yaw);
                  this.neckPitch(p, 0.2F * swing);
                  break;
               default:
                  float wind = DinoAnimIn.window(0.0F, hit * 0.5F, hit * 0.6F, hit * 0.9F, ux);
                  float lunge = DinoAnimIn.window(hit * 0.6F, hit * 1.0F, hit * 1.25F, 1.0F, ux);
                  p.rot(this.body, -0.03F * wind + 0.07F * lunge, 0.0F, 0.0F);
                  p.rot(this.chest, -0.05F * wind + 0.09F * lunge, 0.0F, 0.0F);
                  this.neckPitch(p, -0.22F * wind + 0.42F * lunge);
                  p.rot(this.head, -0.15F * wind - 0.2F * lunge, 0.0F, 0.0F);
                  p.move(this.body, 0.0F, -0.04F * sp.hipHeight * lunge, 0.1F * sp.hipHeight * lunge);
                  jawOpen = Math.max(jawOpen, DinoAnimIn.window(0.0F, hit * 0.7F, hit * 0.98F, hit * 1.08F, ux) * 0.9F);
            }

            lookGain *= 0.6F;
            break;
         case 2:
            jawOpen = Math.max(jawOpen, this.roar(p, at / sp.roarTime, at, 1.0F));
            break;
         case 3:
            float specU = at / sp.specialTime;
            float specHit = sp.specialHit / sp.specialTime;
            switch (sp.specialStyle) {
               case 1:
                  display = Math.max(display, DinoAnimIn.window(0.0F, 0.12F, 0.85F, 1.0F, specU));
                  float back = DinoAnimIn.window(0.1F, specHit * 0.8F, specHit * 0.9F, specHit * 1.0F, specU);
                  float snap = DinoAnimIn.window(specHit * 0.9F, specHit * 1.02F, specHit * 1.2F, 0.95F, specU);
                  this.neckPitch(p, -0.35F * back + 0.35F * snap);
                  p.rot(this.head, -0.2F * back + 0.1F * snap, 0.0F, 0.04F * DinoAnimIn.sin(at * 38.0F) * back);
                  jawOpen = Math.max(jawOpen, 0.5F * back + 0.9F * snap);
                  break;
               case 2:
                  float rear = DinoAnimIn.window(0.05F, specHit * 0.45F, specHit * 0.9F, specHit * 1.0F, specU);
                  float land = DinoAnimIn.window(specHit * 0.98F, specHit * 1.05F, specHit * 1.2F, 1.0F, specU);
                  p.rot(this.body, -sp.rear * rear + 0.03F * land, 0.0F, 0.0F);
                  this.neckPitch(p, sp.rear * 0.7F * rear);
                  p.move(this.body, 0.0F, -0.2F * land, 0.0F);

                  for (int s = 0; s < 2; s++) {
                     p.rot(this.arm[s], -0.55F * rear + 0.25F * rear * DinoAnimIn.sin(at * 5.0F + s * 2), 0.0F, 0.0F);
                     p.rot(this.forearm[s], 0.8F * rear, 0.0F, 0.0F);
                     p.rot(this.thigh[s], sp.rear * rear, 0.0F, 0.0F);
                  }

                  for (int i = 0; i < nt; i++) {
                     p.rot(this.tail[i], sp.rear * 0.9F * rear / Math.max(1, nt) * (i < 3 ? 2.2F : 0.3F), 0.0F, 0.0F);
                  }

                  jawOpen = Math.max(jawOpen, 0.6F * rear);
               case 3:
               case 4:
                  break;
               default:
                  jawOpen = Math.max(jawOpen, this.roar(p, specU, at, 1.25F));
            }

            lookGain *= 0.5F;
            break;
         case 4:
            float u = at / sp.eatTime;
            float down = DinoAnimIn.window(0.0F, 0.25F, 0.8F, 1.0F, u);
            p.rot(this.body, 0.06F * down, 0.0F, 0.0F);
            p.rot(this.chest, 0.1F * down, 0.0F, 0.0F);
            this.neckPitch(p, 0.75F * down);
            p.rot(this.head, 0.25F * down, 0.0F, 0.0F);
            jawOpen = Math.max(jawOpen, down * 0.35F * Math.max(0.0F, DinoAnimIn.sin(at * 14.0F)));
            lookGain = 0.0F;
      }

      if (in.hurt > 0.001F) {
         float h = in.hurt * awake;
         p.rot(this.chest, -0.04F * h, 0.0F, 0.03F * h);
         this.neckPitch(p, -0.12F * h);
         p.rot(this.head, -0.1F * h, 0.1F * h, 0.0F);
         jawOpen = Math.max(jawOpen, 0.35F * h);
      }

      if (sit > 0.001F) {
         p.move(this.body, 0.0F, -sit * sp.sitDrop, 0.0F);
         this.neckPitch(p, sit * sp.restNeck * 0.6F);

         for (int s = 0; s < 2; s++) {
            p.rot(this.thigh[s], sit * sp.sitThigh, 0.0F, 0.0F);
            p.rot(this.shin[s], sit * sp.sitShin, 0.0F, 0.0F);
            p.rot(this.ankle[s], sit * sp.sitAnkle, 0.0F, 0.0F);
            p.rot(this.foot[s], sit * sp.sitFoot, 0.0F, 0.0F);
            if (sp.quadruped) {
               p.rot(this.arm[s], sit * sp.sitArm, 0.0F, 0.0F);
               p.rot(this.forearm[s], sit * sp.sitForearm, 0.0F, 0.0F);
               p.rot(this.hand[s], sit * sp.sitHand, 0.0F, 0.0F);
            } else {
               p.rot(this.arm[s], -sit * sp.bipedSitArm, 0.0F, 0.0F);
               p.rot(this.forearm[s], -sit * sp.bipedSitArm * 0.8F, 0.0F, 0.0F);
            }
         }

         for (int i = 0; i < nt; i++) {
            p.rot(this.tail[i], sit * (i == 0 ? -0.12F : 0.03F), sit * 0.1F, 0.0F);
         }
      }

      if (lie > 0.001F) {
         float settle = dead > 0.0F ? 1.0F + 0.06F * DinoAnimIn.sin(Math.min(in.death, 1.2F) * 9.0F) * (1.0F - DinoAnimIn.ramp(0.6F, 1.3F, in.death)) : 1.0F;
         p.rot(this.body, 0.0F, 0.0F, lie * 1.5F * settle);
         p.move(this.body, 0.0F, -lie * (sp.hipHeight - sp.lieHeight), 0.0F);
         this.neckPitch(p, lie * sp.restNeck);
         this.neckYaw(p, -lie * sp.lieNeck);
         p.rot(this.head, 0.0F, -lie * sp.lieHead, 0.0F);

         for (int i = 0; i < nt; i++) {
            p.rot(this.tail[i], 0.0F, lie * sp.lieTail, 0.0F);
         }

         for (int s = 0; s < 2; s++) {
            boolean upper = s == 0;
            float stiff = dead * 0.25F;
            p.rot(this.thigh[s], lie * (-0.3F + stiff), 0.0F, upper ? -lie * 0.3F : lie * 0.06F);
            p.rot(this.shin[s], lie * (0.45F - stiff), 0.0F, 0.0F);
            p.rot(this.ankle[s], lie * -0.3F, 0.0F, 0.0F);
            p.rot(this.foot[s], lie * 0.45F, 0.0F, 0.0F);
            p.rot(this.arm[s], lie * (sp.quadruped ? -0.3F : 0.15F), 0.0F, upper ? -lie * 0.3F : lie * 0.06F);
            p.rot(this.forearm[s], lie * (sp.quadruped ? 0.45F : -0.2F), 0.0F, 0.0F);
            p.rot(this.hand[s], lie * (sp.quadruped ? 0.3F : 0.2F), 0.0F, 0.0F);
         }

         p.scale(this.chest, 1.0F + 0.012F * breath * in.sleep * (1.0F - dead));
         jawOpen = Math.max(jawOpen * awake, lie * (dead > 0.0F ? 0.28F : 0.1F + 0.03F * breath));
         display *= awake;
      }

      if (lookGain > 0.001F) {
         float yaw = in.lookYaw * lookGain;
         float pitch = in.lookPitch * lookGain;
         int n = this.neck.length;
         float neckShare = n == 0 ? 0.0F : 0.6F;

         for (int i = 0; i < n; i++) {
            p.rot(this.neck[i], -pitch * neckShare / n, yaw * neckShare / n, 0.0F);
         }

         p.rot(this.head, -pitch * (1.0F - neckShare), yaw * (1.0F - neckShare), 0.0F);
      }

      for (int s = 0; s < 2; s++) {
         if (this.frill[s] >= 0) {
            float m = s == 0 ? 1.0F : -1.0F;
            float d = Math.min(1.0F, display);
            float shake = d * 0.07F * DinoAnimIn.sin(t * 34.0F + s);
            p.rot(this.frill[s], 0.0F, m * (1.25F * (1.0F - d) + shake), 0.0F);
            p.scale(this.frill[s], Math.max(0.001F, DinoAnimIn.ramp(0.0F, 0.5F, d)));
         }
      }

      p.rot(this.jaw, Math.min(1.0F, jawOpen) * sp.jawMax, 0.0F, 0.0F);
      if (!in.saddled) {
         p.scale(this.saddle, 0.0F);
      }

      if (in.baby) {
         p.scale(this.head, 1.3F);
      }
   }

   private float roar(Pose p, float u, float at, float strength) {
      float dip = DinoAnimIn.window(0.0F, 0.1F, 0.14F, 0.24F, u);
      float out = DinoAnimIn.window(0.16F, 0.3F, 0.8F, 1.0F, u) * strength;
      float lift = this.spec.headLift;
      float shake = out * DinoAnimIn.sin(at * 44.0F) * 0.014F;
      p.rot(this.body, 0.04F * dip - 0.03F * out, 0.0F, 0.0F);
      p.rot(this.chest, 0.06F * dip - 0.07F * out + shake, 0.0F, 0.0F);
      this.neckPitch(p, 0.22F * dip - 0.26F * out * lift);
      p.rot(this.head, 0.1F * dip - 0.14F * out * lift + shake * 2.0F, 0.1F * out * DinoAnimIn.sin(at * 2.3F), shake * 2.0F);

      for (int s = 0; s < 2; s++) {
         float m = s == 0 ? 1.0F : -1.0F;
         if (!this.spec.quadruped) {
            p.rot(this.arm[s], -0.3F * out, 0.0F, m * 0.2F * out);
         }
      }

      for (int i = 0; i < this.tail.length; i++) {
         p.rot(this.tail[i], 0.02F * out, 0.0F, 0.0F);
      }

      return 0.12F * dip + Math.min(1.0F, out) * (0.95F + 0.05F * DinoAnimIn.sin(at * 31.0F));
   }
}
