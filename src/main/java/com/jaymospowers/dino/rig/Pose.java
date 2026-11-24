package com.jaymospowers.dino.rig;

import java.util.Arrays;

public final class Pose {
   public final Skeleton skeleton;
   public final float[] rx;
   public final float[] ry;
   public final float[] rz;
   public final float[] tx;
   public final float[] ty;
   public final float[] tz;
   public final float[] sx;
   public final float[] sy;
   public final float[] sz;
   public final float[][] glow;
   private final Xf[] world;

   public Pose(Skeleton skeleton) {
      this.skeleton = skeleton;
      int n = skeleton.size();
      this.rx = new float[n];
      this.ry = new float[n];
      this.rz = new float[n];
      this.tx = new float[n];
      this.ty = new float[n];
      this.tz = new float[n];
      this.sx = new float[n];
      this.sy = new float[n];
      this.sz = new float[n];
      this.glow = new float[Math.max(1, skeleton.glowChannels.length)][n];
      this.world = new Xf[n];

      for (int i = 0; i < n; i++) {
         this.world[i] = new Xf();
      }

      this.reset();
   }

   public Pose reset() {
      Arrays.fill(this.rx, 0.0F);
      Arrays.fill(this.ry, 0.0F);
      Arrays.fill(this.rz, 0.0F);
      Arrays.fill(this.tx, 0.0F);
      Arrays.fill(this.ty, 0.0F);
      Arrays.fill(this.tz, 0.0F);
      Arrays.fill(this.sx, 1.0F);
      Arrays.fill(this.sy, 1.0F);
      Arrays.fill(this.sz, 1.0F);

      for (float[] g : this.glow) {
         Arrays.fill(g, 0.0F);
      }

      return this;
   }

   public Pose rot(int bone, float x, float y, float z) {
      if (bone >= 0) {
         this.rx[bone] = this.rx[bone] + x;
         this.ry[bone] = this.ry[bone] + y;
         this.rz[bone] = this.rz[bone] + z;
      }

      return this;
   }

   public Pose rot(String bone, double x, double y, double z) {
      return this.rot(this.skeleton.index(bone), (float)x, (float)y, (float)z);
   }

   public Pose move(int bone, float x, float y, float z) {
      if (bone >= 0) {
         this.tx[bone] = this.tx[bone] + x;
         this.ty[bone] = this.ty[bone] + y;
         this.tz[bone] = this.tz[bone] + z;
      }

      return this;
   }

   public Pose move(String bone, double x, double y, double z) {
      return this.move(this.skeleton.index(bone), (float)x, (float)y, (float)z);
   }

   public Pose slide(int bone, float distance) {
      if (bone >= 0) {
         this.tx[bone] = this.tx[bone] + this.skeleton.axisX[bone] * distance;
         this.ty[bone] = this.ty[bone] + this.skeleton.axisY[bone] * distance;
         this.tz[bone] = this.tz[bone] + this.skeleton.axisZ[bone] * distance;
      }

      return this;
   }

   public Pose scale(int bone, float s) {
      if (bone >= 0) {
         this.sx[bone] = this.sx[bone] * s;
         this.sy[bone] = this.sy[bone] * s;
         this.sz[bone] = this.sz[bone] * s;
      }

      return this;
   }

   public Pose glow(int channel, int bone, float level) {
      if (bone >= 0 && channel >= 0 && channel < this.glow.length) {
         this.glow[channel][bone] = level;
      }

      return this;
   }

   public Pose glowAll(int channel, float level) {
      if (channel >= 0 && channel < this.glow.length) {
         Arrays.fill(this.glow[channel], level);
      }

      return this;
   }

   public Xf[] solve() {
      Skeleton s = this.skeleton;

      for (int i = 0; i < this.world.length; i++) {
         Xf w = this.world[i];
         int p = s.parent[i];
         if (p < 0) {
            w.identity();
         } else {
            w.set(this.world[p]);
         }

         float px = s.pivotX[i];
         float py = s.pivotY[i];
         float pz = s.pivotZ[i];
         w.translate(px + this.tx[i], py + this.ty[i], pz + this.tz[i]);
         w.rotateZYX(this.rz[i], this.ry[i], this.rx[i]);
         if (this.sx[i] != 1.0F || this.sy[i] != 1.0F || this.sz[i] != 1.0F) {
            w.scale(this.sx[i], this.sy[i], this.sz[i]);
         }

         w.translate(-px, -py, -pz);
      }

      return this.world;
   }

   public float[] anchor(String name) {
      int a = this.skeleton.anchorIndex(name);
      return a < 0
         ? new float[]{0.0F, 0.0F, 0.0F}
         : this.world[this.skeleton.anchorBone[a]].point(this.skeleton.anchorX[a], this.skeleton.anchorY[a], this.skeleton.anchorZ[a]);
   }

   public float[] pivot(int bone) {
      return bone < 0
         ? new float[]{0.0F, 0.0F, 0.0F}
         : this.world[bone].point(this.skeleton.pivotX[bone], this.skeleton.pivotY[bone], this.skeleton.pivotZ[bone]);
   }

   public Xf[] world() {
      return this.world;
   }
}
