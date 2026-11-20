package com.peterwolf.forestfire.firefighting.hose;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class HosePath {
   public static final Codec<HosePath> CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            HoseControlPoint.CODEC.listOf().fieldOf("points").forGetter(p -> p.points), Codec.DOUBLE.fieldOf("length").forGetter(p -> p.pathLength)
         )
         .apply(instance, HosePath::new)
   );
   private final List<HoseControlPoint> points;
   private final double pathLength;

   public HosePath(List<HoseControlPoint> points, double pathLength) {
      this.points = Collections.unmodifiableList(new ArrayList<>(points));
      this.pathLength = Math.max(0.0, pathLength);
   }

   public static HosePath of(List<HoseControlPoint> points) {
      return new HosePath(points, computeLength(points));
   }

   public static HosePath empty() {
      return new HosePath(List.of(), 0.0);
   }

   public List<HoseControlPoint> points() {
      return this.points;
   }

   public double pathLength() {
      return this.pathLength;
   }

   public int blockLength() {
      return Math.max(1, (int)Math.ceil(this.pathLength));
   }

   public boolean isEmpty() {
      return this.points.isEmpty();
   }

   public Vec3 start() {
      return this.points.isEmpty() ? Vec3.ZERO : this.points.getFirst().position();
   }

   public Vec3 end() {
      return this.points.isEmpty() ? Vec3.ZERO : this.points.getLast().position();
   }

   public HosePath fixedPrefix() {
      return this.points.size() <= 1 ? this : of(this.points.subList(0, this.points.size() - 1));
   }

   public HosePath withDynamicEndpoint(Vec3 endpoint, HosePointType type) {
      if (this.points.isEmpty()) {
         return of(List.of(new HoseControlPoint(endpoint, type)));
      } else {
         List<HoseControlPoint> next = new ArrayList<>(this.points.size());

         for (int i = 0; i < this.points.size() - 1; i++) {
            next.add(this.points.get(i));
         }

         next.add(new HoseControlPoint(endpoint, type));
         return of(next);
      }
   }

   public static double computeLength(List<HoseControlPoint> pts) {
      if (pts != null && pts.size() >= 2) {
         double sum = 0.0;

         for (int i = 1; i < pts.size(); i++) {
            sum += pts.get(i - 1).position().distanceTo(pts.get(i).position());
         }

         return sum;
      } else {
         return 0.0;
      }
   }
}
