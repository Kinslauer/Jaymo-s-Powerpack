package com.jaymospowers.dino.rig;

public final class Animators {
   private Animators() {
   }

   public static void pose(String model, String clip, float t, Pose p) {
      DinoSpec spec = DinoSpec.of(model);
      DinoAnim a = new DinoAnim(p.skeleton, spec);
      DinoAnimIn in = new DinoAnimIn();
      in.time = t;
      in.saddled = true;
      switch (clip) {
         case "walk":
            in.walk = 1.0F;
            in.walkPhase = t * 0.9F;
            break;
         case "run":
            in.walk = 1.0F;
            in.run = 1.0F;
            in.walkPhase = t * 1.8F;
            break;
         case "bite":
            act(in, 1, t);
            break;
         case "roar":
            act(in, 2, t);
            break;
         case "special":
            act(in, 3, t);
            if (spec.specialStyle == 3) {
               in.charge = 1.0F;
               in.walk = 1.0F;
               in.run = 1.0F;
               in.walkPhase = t * 1.8F;
            }

            if (spec.specialStyle == 4) {
               in.air = DinoAnimIn.window(0.05F, 0.2F, 0.6F, 0.8F, t);
            }
            break;
         case "eat":
            act(in, 4, t);
            break;
         case "sleep":
            in.sleep = DinoAnimIn.ramp(0.0F, 1.0F, t);
            break;
         case "sit":
            in.sit = DinoAnimIn.ramp(0.0F, 1.0F, t);
            break;
         case "death":
            in.death = t;
            break;
         case "look":
            in.lookYaw = 0.8F * DinoAnimIn.sin(t * 2.0F);
            in.lookPitch = 0.4F * DinoAnimIn.cos(t * 2.0F);
            break;
         case "leap":
            in.air = 1.0F;
            break;
         case "display":
            in.display = DinoAnimIn.ramp(0.0F, 0.6F, t);
            break;
         case "charge":
            in.charge = 1.0F;
            in.walk = 1.0F;
            in.run = 1.0F;
            in.walkPhase = t * 1.8F;
            break;
         case "nosaddle":
            in.saddled = false;
      }

      a.apply(in, p);
   }

   private static void act(DinoAnimIn in, int action, float t) {
      in.action = action;
      in.actionT = t;
   }
}
