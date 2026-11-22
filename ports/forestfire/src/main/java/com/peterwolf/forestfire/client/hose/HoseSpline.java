package com.peterwolf.forestfire.client.hose;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class HoseSpline {
   private HoseSpline() {
   }

   public static List<Vec3> smooth(List<Vec3> control, int samplesPerSegment, double sagBlocks) {
      if (control != null && control.size() >= 2) {
         if (control.size() == 2) {
            return densifyLinear(control.get(0), control.get(1), Math.max(4, samplesPerSegment), sagBlocks);
         } else {
            int segs = control.size() - 1;
            int samples = Mth.clamp(samplesPerSegment, 2, 16);
            List<Vec3> out = new ArrayList<>(segs * samples + 1);
            out.add(control.getFirst());

            for (int i = 0; i < segs; i++) {
               Vec3 p0 = control.get(Math.max(0, i - 1));
               Vec3 p1 = control.get(i);
               Vec3 p2 = control.get(i + 1);
               Vec3 p3 = control.get(Math.min(control.size() - 1, i + 2));

               for (int s = 1; s <= samples; s++) {
                  float t = (float)s / samples;
                  out.add(catmullRom(t, p0, p1, p2, p3));
               }
            }

            if (sagBlocks > 1.0E-4) {
               applySag(out, sagBlocks);
            }

            return out;
         }
      } else {
         return (List<Vec3>)(control == null ? List.of() : new ArrayList<>(control));
      }
   }

   private static List<Vec3> densifyLinear(Vec3 a, Vec3 b, int samples, double sagBlocks) {
      List<Vec3> out = new ArrayList<>(samples + 1);

      for (int i = 0; i <= samples; i++) {
         float t = (float)i / samples;
         out.add(a.lerp(b, t));
      }

      if (sagBlocks > 1.0E-4) {
         applySag(out, sagBlocks);
      }

      return out;
   }

   private static Vec3 catmullRom(float t, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
      float t2 = t * t;
      float t3 = t2 * t;
      double x = 0.5 * (2.0 * p1.x + (-p0.x + p2.x) * t + (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2 + (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3);
      double y = 0.5 * (2.0 * p1.y + (-p0.y + p2.y) * t + (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2 + (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3);
      double z = 0.5 * (2.0 * p1.z + (-p0.z + p2.z) * t + (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2 + (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3);
      return new Vec3(x, y, z);
   }

   private static void applySag(List<Vec3> points, double sagBlocks) {
      int n = points.size();
      if (n >= 3) {
         for (int i = 1; i < n - 1; i++) {
            double along = (double)i / (n - 1);
            double envelope = Math.sin(along * Math.PI);
            envelope *= 0.85 + 0.15 * Math.sin(along * Math.PI * 2.0);
            Vec3 p = points.get(i);
            points.set(i, new Vec3(p.x, p.y - sagBlocks * envelope, p.z));
         }
      }
   }
}
