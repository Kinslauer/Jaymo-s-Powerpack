package com.jaymospowers.dino.rig;

public final class Xf {
   public float m00 = 1.0F;
   public float m01;
   public float m02;
   public float m03;
   public float m10;
   public float m11 = 1.0F;
   public float m12;
   public float m13;
   public float m20;
   public float m21;
   public float m22 = 1.0F;
   public float m23;

   public Xf identity() {
      this.m00 = 1.0F;
      this.m01 = 0.0F;
      this.m02 = 0.0F;
      this.m03 = 0.0F;
      this.m10 = 0.0F;
      this.m11 = 1.0F;
      this.m12 = 0.0F;
      this.m13 = 0.0F;
      this.m20 = 0.0F;
      this.m21 = 0.0F;
      this.m22 = 1.0F;
      this.m23 = 0.0F;
      return this;
   }

   public Xf set(Xf o) {
      this.m00 = o.m00;
      this.m01 = o.m01;
      this.m02 = o.m02;
      this.m03 = o.m03;
      this.m10 = o.m10;
      this.m11 = o.m11;
      this.m12 = o.m12;
      this.m13 = o.m13;
      this.m20 = o.m20;
      this.m21 = o.m21;
      this.m22 = o.m22;
      this.m23 = o.m23;
      return this;
   }

   public Xf mul(Xf r) {
      float a00 = this.m00 * r.m00 + this.m01 * r.m10 + this.m02 * r.m20;
      float a01 = this.m00 * r.m01 + this.m01 * r.m11 + this.m02 * r.m21;
      float a02 = this.m00 * r.m02 + this.m01 * r.m12 + this.m02 * r.m22;
      float a03 = this.m00 * r.m03 + this.m01 * r.m13 + this.m02 * r.m23 + this.m03;
      float a10 = this.m10 * r.m00 + this.m11 * r.m10 + this.m12 * r.m20;
      float a11 = this.m10 * r.m01 + this.m11 * r.m11 + this.m12 * r.m21;
      float a12 = this.m10 * r.m02 + this.m11 * r.m12 + this.m12 * r.m22;
      float a13 = this.m10 * r.m03 + this.m11 * r.m13 + this.m12 * r.m23 + this.m13;
      float a20 = this.m20 * r.m00 + this.m21 * r.m10 + this.m22 * r.m20;
      float a21 = this.m20 * r.m01 + this.m21 * r.m11 + this.m22 * r.m21;
      float a22 = this.m20 * r.m02 + this.m21 * r.m12 + this.m22 * r.m22;
      float a23 = this.m20 * r.m03 + this.m21 * r.m13 + this.m22 * r.m23 + this.m23;
      this.m00 = a00;
      this.m01 = a01;
      this.m02 = a02;
      this.m03 = a03;
      this.m10 = a10;
      this.m11 = a11;
      this.m12 = a12;
      this.m13 = a13;
      this.m20 = a20;
      this.m21 = a21;
      this.m22 = a22;
      this.m23 = a23;
      return this;
   }

   public Xf translate(float x, float y, float z) {
      this.m03 = this.m03 + (this.m00 * x + this.m01 * y + this.m02 * z);
      this.m13 = this.m13 + (this.m10 * x + this.m11 * y + this.m12 * z);
      this.m23 = this.m23 + (this.m20 * x + this.m21 * y + this.m22 * z);
      return this;
   }

   public Xf rotateZYX(float rz, float ry, float rx) {
      if (rx == 0.0F && ry == 0.0F && rz == 0.0F) {
         return this;
      }

      float ca = (float)Math.cos(rx);
      float sa = (float)Math.sin(rx);
      float cb = (float)Math.cos(ry);
      float sb = (float)Math.sin(ry);
      float cc = (float)Math.cos(rz);
      float sc = (float)Math.sin(rz);
      float r00 = cc * cb;
      float r01 = -sc * ca + cc * sb * sa;
      float r02 = sc * sa + cc * sb * ca;
      float r10 = sc * cb;
      float r11 = cc * ca + sc * sb * sa;
      float r12 = -cc * sa + sc * sb * ca;
      float r20 = -sb;
      float r21 = cb * sa;
      float r22 = cb * ca;
      float a00 = this.m00 * r00 + this.m01 * r10 + this.m02 * r20;
      float a01 = this.m00 * r01 + this.m01 * r11 + this.m02 * r21;
      float a02 = this.m00 * r02 + this.m01 * r12 + this.m02 * r22;
      float a10 = this.m10 * r00 + this.m11 * r10 + this.m12 * r20;
      float a11 = this.m10 * r01 + this.m11 * r11 + this.m12 * r21;
      float a12 = this.m10 * r02 + this.m11 * r12 + this.m12 * r22;
      float a20 = this.m20 * r00 + this.m21 * r10 + this.m22 * r20;
      float a21 = this.m20 * r01 + this.m21 * r11 + this.m22 * r21;
      float a22 = this.m20 * r02 + this.m21 * r12 + this.m22 * r22;
      this.m00 = a00;
      this.m01 = a01;
      this.m02 = a02;
      this.m10 = a10;
      this.m11 = a11;
      this.m12 = a12;
      this.m20 = a20;
      this.m21 = a21;
      this.m22 = a22;
      return this;
   }

   public Xf rotateY(float ry) {
      return this.rotateZYX(0.0F, ry, 0.0F);
   }

   public Xf scale(float x, float y, float z) {
      this.m00 *= x;
      this.m01 *= y;
      this.m02 *= z;
      this.m10 *= x;
      this.m11 *= y;
      this.m12 *= z;
      this.m20 *= x;
      this.m21 *= y;
      this.m22 *= z;
      return this;
   }

   public float px(float x, float y, float z) {
      return this.m00 * x + this.m01 * y + this.m02 * z + this.m03;
   }

   public float py(float x, float y, float z) {
      return this.m10 * x + this.m11 * y + this.m12 * z + this.m13;
   }

   public float pz(float x, float y, float z) {
      return this.m20 * x + this.m21 * y + this.m22 * z + this.m23;
   }

   public float dx(float x, float y, float z) {
      return this.m00 * x + this.m01 * y + this.m02 * z;
   }

   public float dy(float x, float y, float z) {
      return this.m10 * x + this.m11 * y + this.m12 * z;
   }

   public float dz(float x, float y, float z) {
      return this.m20 * x + this.m21 * y + this.m22 * z;
   }

   public float[] point(float x, float y, float z) {
      return new float[]{this.px(x, y, z), this.py(x, y, z), this.pz(x, y, z)};
   }
}
