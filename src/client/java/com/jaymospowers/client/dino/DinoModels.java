package com.jaymospowers.client.dino;

import com.jaymospowers.dino.Dino;
import com.jaymospowers.dino.entity.Species;
import com.jaymospowers.dino.rig.DinoAnim;
import com.jaymospowers.dino.rig.KModel;
import com.jaymospowers.dino.rig.Skeleton;
import com.jaymospowers.dino.rig.Xf;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class DinoModels {
   private static final Map<Species, DinoModels.Entry> ENTRIES = new EnumMap<>(Species.class);

   private DinoModels() {
   }

   public static DinoModels.Entry get(Species s) {
      return ENTRIES.computeIfAbsent(s, DinoModels.Entry::new);
   }

   public static void draw(KModel model, float[] bones, Pose pose, VertexConsumer vc, int color, int overlay, int light) {
      Matrix4f mat = pose.pose();
      Matrix3f nm = pose.normal();
      int nb = bones.length / 12;
      float[] m = new float[nb * 12];
      float[] nr = new float[nb * 9];

      for (int b = 0; b < nb; b++) {
         int o = b * 12;
         float x00 = bones[o];
         float x01 = bones[o + 1];
         float x02 = bones[o + 2];
         float x03 = bones[o + 3];
         float x10 = bones[o + 4];
         float x11 = bones[o + 5];
         float x12 = bones[o + 6];
         float x13 = bones[o + 7];
         float x20 = bones[o + 8];
         float x21 = bones[o + 9];
         float x22 = bones[o + 10];
         float x23 = bones[o + 11];
         m[o] = mat.m00() * x00 + mat.m10() * x10 + mat.m20() * x20;
         m[o + 1] = mat.m00() * x01 + mat.m10() * x11 + mat.m20() * x21;
         m[o + 2] = mat.m00() * x02 + mat.m10() * x12 + mat.m20() * x22;
         m[o + 3] = mat.m00() * x03 + mat.m10() * x13 + mat.m20() * x23 + mat.m30();
         m[o + 4] = mat.m01() * x00 + mat.m11() * x10 + mat.m21() * x20;
         m[o + 5] = mat.m01() * x01 + mat.m11() * x11 + mat.m21() * x21;
         m[o + 6] = mat.m01() * x02 + mat.m11() * x12 + mat.m21() * x22;
         m[o + 7] = mat.m01() * x03 + mat.m11() * x13 + mat.m21() * x23 + mat.m31();
         m[o + 8] = mat.m02() * x00 + mat.m12() * x10 + mat.m22() * x20;
         m[o + 9] = mat.m02() * x01 + mat.m12() * x11 + mat.m22() * x21;
         m[o + 10] = mat.m02() * x02 + mat.m12() * x12 + mat.m22() * x22;
         m[o + 11] = mat.m02() * x03 + mat.m12() * x13 + mat.m22() * x23 + mat.m32();
         int n = b * 9;
         nr[n] = nm.m00() * x00 + nm.m10() * x10 + nm.m20() * x20;
         nr[n + 1] = nm.m00() * x01 + nm.m10() * x11 + nm.m20() * x21;
         nr[n + 2] = nm.m00() * x02 + nm.m10() * x12 + nm.m20() * x22;
         nr[n + 3] = nm.m01() * x00 + nm.m11() * x10 + nm.m21() * x20;
         nr[n + 4] = nm.m01() * x01 + nm.m11() * x11 + nm.m21() * x21;
         nr[n + 5] = nm.m01() * x02 + nm.m11() * x12 + nm.m21() * x22;
         nr[n + 6] = nm.m02() * x00 + nm.m12() * x10 + nm.m22() * x20;
         nr[n + 7] = nm.m02() * x01 + nm.m12() * x11 + nm.m22() * x21;
         nr[n + 8] = nm.m02() * x02 + nm.m12() * x12 + nm.m22() * x22;
      }

      float[] pos = model.pos;
      float[] uv = model.uv;
      float[] normal = model.normal;
      short[] quadBone = model.quadBone;

      for (int q = 0; q < model.quadCount; q++) {
         int bone = quadBone[q];
         int o = bone * 12;
         int n = bone * 9;
         float nx = normal[q * 3];
         float ny = normal[q * 3 + 1];
         float nz = normal[q * 3 + 2];
         float tx = nr[n] * nx + nr[n + 1] * ny + nr[n + 2] * nz;
         float ty = nr[n + 3] * nx + nr[n + 4] * ny + nr[n + 5] * nz;
         float tz = nr[n + 6] * nx + nr[n + 7] * ny + nr[n + 8] * nz;
         float len = (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
         if (len > 1.0E-6F) {
            tx /= len;
            ty /= len;
            tz /= len;
         }

         for (int k = 0; k < 4; k++) {
            int pi = q * 12 + k * 3;
            float x = pos[pi];
            float y = pos[pi + 1];
            float z = pos[pi + 2];
            vc.addVertex(
               m[o] * x + m[o + 1] * y + m[o + 2] * z + m[o + 3],
               m[o + 4] * x + m[o + 5] * y + m[o + 6] * z + m[o + 7],
               m[o + 8] * x + m[o + 9] * y + m[o + 10] * z + m[o + 11],
               color,
               uv[q * 8 + k * 2],
               uv[q * 8 + k * 2 + 1],
               overlay,
               light,
               tx,
               ty,
               tz
            );
         }
      }
   }

   public static final class Entry {
      public final Species species;
      public final KModel model;
      public final com.jaymospowers.dino.rig.Pose pose;
      public final DinoAnim anim;
      public final Identifier skin;
      public final Identifier skeleton;
      private DinoModels.SkullMesh skull;

      Entry(Species s) {
         this.species = s;
         this.model = KModel.load(s.id, true);
         this.pose = new com.jaymospowers.dino.rig.Pose(this.model.skeleton);
         this.anim = new DinoAnim(this.model.skeleton, s.spec());
         this.skin = Dino.id("textures/entity/dino/" + s.id + ".png");
         this.skeleton = Dino.id("textures/entity/dino/" + s.id + "_skeleton.png");
      }

      public DinoModels.SkullMesh skull() {
         if (this.skull == null) {
            this.skull = new DinoModels.SkullMesh(this.model);
         }

         return this.skull;
      }

      public float[] solveInto(float[] out) {
         Xf[] w = this.pose.solve();
         int n = w.length * 12;
         if (out == null || out.length != n) {
            out = new float[n];
         }

         for (int b = 0; b < w.length; b++) {
            Xf x = w[b];
            int o = b * 12;
            out[o] = x.m00;
            out[o + 1] = x.m01;
            out[o + 2] = x.m02;
            out[o + 3] = x.m03;
            out[o + 4] = x.m10;
            out[o + 5] = x.m11;
            out[o + 6] = x.m12;
            out[o + 7] = x.m13;
            out[o + 8] = x.m20;
            out[o + 9] = x.m21;
            out[o + 10] = x.m22;
            out[o + 11] = x.m23;
         }

         return out;
      }
   }

   public static final class SkullMesh {
      public final int quads;
      public final float width;
      public final float height;
      public final float length;
      private final float[] pos;
      private final float[] uv;
      private final float[] normal;

      SkullMesh(KModel model) {
         Skeleton sk = model.skeleton;
         int head = sk.index("head");
         boolean[] in = new boolean[sk.size()];

         for (int b = 0; b < in.length; b++) {
            in[b] = b == head || sk.parent[b] >= 0 && in[sk.parent[b]];
         }

         int n = 0;
         float x0 = Float.MAX_VALUE;
         float y0 = Float.MAX_VALUE;
         float z0 = Float.MAX_VALUE;
         float x1 = -Float.MAX_VALUE;
         float y1 = -Float.MAX_VALUE;
         float z1 = -Float.MAX_VALUE;

         for (int q = 0; q < model.quadCount; q++) {
            if (in[model.quadBone[q]]) {
               n++;

               for (int k = 0; k < 4; k++) {
                  float x = model.pos[q * 12 + k * 3];
                  float y = model.pos[q * 12 + k * 3 + 1];
                  float z = model.pos[q * 12 + k * 3 + 2];
                  x0 = Math.min(x0, x);
                  y0 = Math.min(y0, y);
                  z0 = Math.min(z0, z);
                  x1 = Math.max(x1, x);
                  y1 = Math.max(y1, y);
                  z1 = Math.max(z1, z);
               }
            }
         }

         this.quads = n;
         this.pos = new float[n * 12];
         this.uv = new float[n * 8];
         this.normal = new float[n * 3];
         this.width = n == 0 ? 0.0F : x1 - x0;
         this.height = n == 0 ? 0.0F : y1 - y0;
         this.length = n == 0 ? 0.0F : z1 - z0;
         float cz = (z0 + z1) * 0.5F;
         int i = 0;

         for (int q = 0; q < model.quadCount; q++) {
            if (in[model.quadBone[q]]) {
               for (int k = 0; k < 4; k++) {
                  this.pos[i * 12 + k * 3] = model.pos[q * 12 + k * 3];
                  this.pos[i * 12 + k * 3 + 1] = model.pos[q * 12 + k * 3 + 1] - y0;
                  this.pos[i * 12 + k * 3 + 2] = model.pos[q * 12 + k * 3 + 2] - cz;
               }

               System.arraycopy(model.uv, q * 8, this.uv, i * 8, 8);
               System.arraycopy(model.normal, q * 3, this.normal, i * 3, 3);
               i++;
            }
         }
      }

      public void draw(Pose pose, VertexConsumer vc, int light) {
         Matrix4f mat = pose.pose();
         Matrix3f nm = pose.normal();
         Vector3f v = new Vector3f();
         Vector3f nv = new Vector3f();

         for (int q = 0; q < this.quads; q++) {
            nm.transform(this.normal[q * 3], this.normal[q * 3 + 1], this.normal[q * 3 + 2], nv).normalize();

            for (int k = 0; k < 4; k++) {
               mat.transformPosition(this.pos[q * 12 + k * 3], this.pos[q * 12 + k * 3 + 1], this.pos[q * 12 + k * 3 + 2], v);
               vc.addVertex(v.x, v.y, v.z, -1, this.uv[q * 8 + k * 2], this.uv[q * 8 + k * 2 + 1], OverlayTexture.NO_OVERLAY, light, nv.x, nv.y, nv.z);
            }
         }
      }
   }
}
