package com.jaymospowers.dino.rig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Skeleton {
   public final String[] names;
   public final int[] parent;
   public final float[] pivotX;
   public final float[] pivotY;
   public final float[] pivotZ;
   public final float[] axisX;
   public final float[] axisY;
   public final float[] axisZ;
   public final String[] glowChannels;
   public final String[] anchorNames;
   public final int[] anchorBone;
   public final float[] anchorX;
   public final float[] anchorY;
   public final float[] anchorZ;
   private final Map<String, Integer> byName = new HashMap<>();
   private final Map<String, Integer> anchorByName = new HashMap<>();

   public Skeleton(String[] names, int[] parent, float[] pivots, float[] axes, String[] glowChannels, String[] anchorNames, int[] anchorBone, float[] anchorPos) {
      int n = names.length;
      this.names = names;
      this.parent = parent;
      this.pivotX = new float[n];
      this.pivotY = new float[n];
      this.pivotZ = new float[n];
      this.axisX = new float[n];
      this.axisY = new float[n];
      this.axisZ = new float[n];

      for (int i = 0; i < n; i++) {
         this.pivotX[i] = pivots[i * 3];
         this.pivotY[i] = pivots[i * 3 + 1];
         this.pivotZ[i] = pivots[i * 3 + 2];
         this.axisX[i] = axes[i * 3];
         this.axisY[i] = axes[i * 3 + 1];
         this.axisZ[i] = axes[i * 3 + 2];
         this.byName.put(names[i], i);
         if (parent[i] >= i) {
            throw new IllegalArgumentException("bone " + names[i] + " comes before its parent");
         }
      }

      this.glowChannels = glowChannels;
      this.anchorNames = anchorNames;
      this.anchorBone = anchorBone;
      int a = anchorNames.length;
      this.anchorX = new float[a];
      this.anchorY = new float[a];
      this.anchorZ = new float[a];

      for (int i = 0; i < a; i++) {
         this.anchorX[i] = anchorPos[i * 3];
         this.anchorY[i] = anchorPos[i * 3 + 1];
         this.anchorZ[i] = anchorPos[i * 3 + 2];
         this.anchorByName.put(anchorNames[i], i);
      }
   }

   public int size() {
      return this.names.length;
   }

   public int index(String name) {
      Integer i = this.byName.get(name);
      return i == null ? -1 : i;
   }

   public int anchorIndex(String name) {
      Integer i = this.anchorByName.get(name);
      return i == null ? -1 : i;
   }

   public int glowChannel(String name) {
      for (int i = 0; i < this.glowChannels.length; i++) {
         if (this.glowChannels[i].equals(name)) {
            return i;
         }
      }

      return -1;
   }

   public int[] chain(String prefix) {
      List<Integer> out = new ArrayList<>();
      int start = this.byName.containsKey(prefix + "0") ? 0 : 1;
      int k = start;

      while (true) {
         Integer i = this.byName.get(prefix + k);
         if (i == null) {
            int[] r = new int[out.size()];

            for (int ix = 0; ix < r.length; ix++) {
               r[ix] = out.get(ix);
            }

            return r;
         }

         out.add(i);
         k++;
      }
   }
}
