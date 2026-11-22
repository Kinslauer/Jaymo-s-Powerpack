package com.peterwolf.forestfire.client.hose;

import com.peterwolf.forestfire.config.ForestFireConfig;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class HoseRenderer {
   private static final int SAMPLES_PER_SEGMENT = 8;

   private HoseRenderer() {
   }

   public static void register() {
      LevelRenderEvents.BEFORE_GIZMOS.register(HoseRenderer::beforeGizmos);
   }

   private static void beforeGizmos(LevelRenderContext context) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         Collection<ClientHoseState.HoseRenderData> hoses = ClientHoseState.all();
         if (!hoses.isEmpty()) {
            CameraRenderState camera = context.levelState().cameraRenderState;
            if (camera != null && camera.initialized) {
               double renderDist = ForestFireConfig.get().hoseRenderDistance;
               double renderDistSq = renderDist * renderDist;
               Vec3 camPos = camera.pos;
               DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
               boolean any = false;

               for (ClientHoseState.HoseRenderData hose : hoses) {
                  if (hose.points() != null && hose.points().size() >= 2) {
                     Vec3 mid = hose.points().get(hose.points().size() / 2);
                     if (!(camPos.distanceToSqr(mid) > renderDistSq)) {
                        double sag = computeSag(hose);
                        List<Vec3> spline = HoseSpline.smooth(hose.points(), 8, sag);
                        if (spline.size() >= 2) {
                           int colour = colourFor(hose);
                           int colourDark = darken(colour);
                           int colourRim = lighten(colour);
                           float width = hose.pressurized() ? 4.0F : 3.0F;
                           drawStrand(gizmos, spline, colour, width);
                           drawOffsetStrand(gizmos, spline, colourDark, width * 0.72F, 0.028, 0.0);
                           drawOffsetStrand(gizmos, spline, colourRim, width * 0.55F, -0.022, 0.018);
                           drawCouplings(gizmos, spline, colourDark, hose.pressurized());
                           any = true;
                        }
                     }
                  }
               }

               if (any) {
                  gizmos.submit(context.submitNodeCollector(), camera, false);
               }
            }
         }
      }
   }

   private static double computeSag(ClientHoseState.HoseRenderData hose) {
      float deployed = hose.deployedLength();
      float path = hose.pathLength();
      double slack = Math.max(0.0, (double)(deployed - path));
      double base = hose.pressurized() ? 0.06 : 0.18;
      double fromSlack = Math.min(0.55, slack * 0.07);
      return base + fromSlack;
   }

   private static void drawStrand(DrawableGizmoPrimitives gizmos, List<Vec3> pts, int colour, float width) {
      for (int i = 0; i < pts.size() - 1; i++) {
         gizmos.addLine(pts.get(i), pts.get(i + 1), colour, width);
      }
   }

   private static void drawOffsetStrand(DrawableGizmoPrimitives gizmos, List<Vec3> pts, int colour, float width, double upBias, double sideBias) {
      for (int i = 0; i < pts.size() - 1; i++) {
         Vec3 a = pts.get(i);
         Vec3 b = pts.get(i + 1);
         Vec3 tan = b.subtract(a);
         double len = tan.length();
         if (!(len < 1.0E-4)) {
            tan = tan.scale(1.0 / len);
            Vec3 side = tan.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() < 1.0E-6) {
               side = tan.cross(new Vec3(1.0, 0.0, 0.0));
            }

            side = side.normalize();
            Vec3 up = side.cross(tan).normalize();
            Vec3 off = up.scale(upBias).add(side.scale(sideBias));
            gizmos.addLine(a.add(off), b.add(off), colour, width);
         }
      }
   }

   private static void drawCouplings(DrawableGizmoPrimitives gizmos, List<Vec3> pts, int colour, boolean pressurized) {
      int step = pressurized ? 10 : 8;
      float w = pressurized ? 3.2F : 2.6F;

      for (int i = step; i < pts.size() - 1; i += step) {
         Vec3 p = pts.get(i);
         Vec3 prev = pts.get(i - 1);
         Vec3 next = pts.get(Math.min(i + 1, pts.size() - 1));
         Vec3 tan = next.subtract(prev);
         if (!(tan.lengthSqr() < 1.0E-6)) {
            tan = tan.normalize();
            Vec3 side = tan.cross(new Vec3(0.0, 1.0, 0.0));
            if (side.lengthSqr() < 1.0E-6) {
               side = new Vec3(1.0, 0.0, 0.0);
            }

            side = side.normalize().scale(0.07);
            Vec3 up = side.cross(tan).normalize().scale(0.07);
            gizmos.addLine(p.subtract(side), p.add(side), colour, w);
            gizmos.addLine(p.subtract(up), p.add(up), colour, w * 0.85F);
         }
      }
   }

   private static int colourFor(ClientHoseState.HoseRenderData hose) {
      if ("INTAKE".equals(hose.type())) {
         return hose.pressurized() ? -12940033 : -14001510;
      } else if (!"SUPPLY".equals(hose.type()) && !"SPLITTER_BRANCH".equals(hose.type())) {
         return hose.pressurized() ? -2088928 : -7662315;
      } else {
         return hose.pressurized() ? -21965 : -5605598;
      }
   }

   private static int darken(int argb) {
      int a = argb >> 24 & 0xFF;
      int r = (int)((argb >> 16 & 0xFF) * 0.72);
      int g = (int)((argb >> 8 & 0xFF) * 0.72);
      int b = (int)((argb & 0xFF) * 0.72);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static int lighten(int argb) {
      int a = argb >> 24 & 0xFF;
      int r = Mth.clamp((int)((argb >> 16 & 0xFF) * 1.15 + 18.0), 0, 255);
      int g = Mth.clamp((int)((argb >> 8 & 0xFF) * 1.15 + 18.0), 0, 255);
      int b = Mth.clamp((int)((argb & 0xFF) * 1.15 + 18.0), 0, 255);
      return a << 24 | r << 16 | g << 8 | b;
   }
}
