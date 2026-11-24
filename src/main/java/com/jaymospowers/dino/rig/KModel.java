package com.jaymospowers.dino.rig;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class KModel {
   public static final int MAGIC = 1263354956;
   public static final int VERSION = 2;
   public final String name;
   public final Skeleton skeleton;
   public final int texWidth;
   public final int texHeight;
   public final int quadCount;
   public final short[] quadBone;
   public final float[] pos;
   public final float[] uv;
   public final float[] normal;
   public final int[][] glowQuads;
   public final float[] bounds;

   private KModel(
      String name,
      Skeleton skeleton,
      int texWidth,
      int texHeight,
      int quadCount,
      short[] quadBone,
      float[] pos,
      float[] uv,
      float[] normal,
      int[][] glowQuads,
      float[] bounds
   ) {
      this.name = name;
      this.skeleton = skeleton;
      this.texWidth = texWidth;
      this.texHeight = texHeight;
      this.quadCount = quadCount;
      this.quadBone = quadBone;
      this.pos = pos;
      this.uv = uv;
      this.normal = normal;
      this.glowQuads = glowQuads;
      this.bounds = bounds;
   }

   public static KModel read(InputStream raw, boolean geometry) throws IOException {
      try (DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 65536))) {
         if (in.readInt() != 1263354956) {
            throw new IOException("not a kaiju model");
         }

         int version = in.readInt();
         if (version != 2) {
            throw new IOException("kaiju model version " + version + ", expected 2");
         }

         String name = in.readUTF();
         int texW = in.readInt();
         int texH = in.readInt();
         int nb = in.readInt();
         String[] names = new String[nb];
         int[] parent = new int[nb];
         float[] pivots = new float[nb * 3];
         float[] axes = new float[nb * 3];

         for (int i = 0; i < nb; i++) {
            names[i] = in.readUTF();
            parent[i] = in.readShort();

            for (int k = 0; k < 3; k++) {
               pivots[i * 3 + k] = in.readFloat();
            }

            for (int k = 0; k < 3; k++) {
               axes[i * 3 + k] = in.readFloat();
            }
         }

         int ng = in.readInt();
         String[] glowNames = new String[ng];

         for (int i = 0; i < ng; i++) {
            glowNames[i] = in.readUTF();
         }

         int na = in.readInt();
         String[] anchorNames = new String[na];
         int[] anchorBone = new int[na];
         float[] anchorPos = new float[na * 3];

         for (int i = 0; i < na; i++) {
            anchorNames[i] = in.readUTF();
            anchorBone[i] = in.readShort();

            for (int k = 0; k < 3; k++) {
               anchorPos[i * 3 + k] = in.readFloat();
            }
         }

         float[] bounds = new float[6];

         for (int k = 0; k < 6; k++) {
            bounds[k] = in.readFloat();
         }

         Skeleton skeleton = new Skeleton(names, parent, pivots, axes, glowNames, anchorNames, anchorBone, anchorPos);
         int nq = in.readInt();
         if (!geometry) {
            return new KModel(name, skeleton, texW, texH, 0, new short[0], new float[0], new float[0], new float[0], new int[ng][0], bounds);
         }

         short[] quadBone = new short[nq];
         float[] pos = new float[nq * 12];
         float[] uv = new float[nq * 8];
         float[] normal = new float[nq * 3];

         for (int q = 0; q < nq; q++) {
            quadBone[q] = in.readShort();

            for (int k = 0; k < 12; k++) {
               pos[q * 12 + k] = in.readFloat();
            }

            for (int k = 0; k < 8; k++) {
               uv[q * 8 + k] = in.readFloat();
            }

            for (int k = 0; k < 3; k++) {
               normal[q * 3 + k] = in.readFloat();
            }
         }

         int[][] glowQuads = new int[ng][];

         for (int g = 0; g < ng; g++) {
            int n = in.readInt();
            glowQuads[g] = new int[n];

            for (int i = 0; i < n; i++) {
               glowQuads[g][i] = in.readInt();
            }
         }

         return new KModel(name, skeleton, texW, texH, nq, quadBone, pos, uv, normal, glowQuads, bounds);
      }
   }

   public static KModel load(String name, boolean geometry) {
      String path = "/assets/jaymospowers/dino/" + name + ".kmdl";

      try (InputStream in = KModel.class.getResourceAsStream(path)) {
         if (in == null) {
            throw new IllegalStateException("missing model " + path);
         } else {
            return read(in, geometry);
         }
      } catch (IOException e) {
         throw new IllegalStateException("cannot read model " + path, e);
      }
   }
}
