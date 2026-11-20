package com.peterwolf.forestfire.client.hud;

import com.peterwolf.forestfire.network.IncidentHudPayload;
import com.peterwolf.forestfire.network.NozzleHudPayload;
import com.peterwolf.forestfire.network.WindSyncPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class FirefighterHud {
   private static IncidentHudPayload incident;
   private static WindSyncPayload wind;
   private static NozzleHudPayload nozzle;
   private static boolean showDedicationSplash = true;
   private static long dedicationUntilMs;

   private FirefighterHud() {
   }

   public static void updateIncident(IncidentHudPayload payload) {
      incident = payload;
   }

   public static void updateWind(WindSyncPayload payload) {
      wind = payload;
   }

   public static void updateNozzle(NozzleHudPayload payload) {
      nozzle = payload;
   }

   public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         int y = 8;
         int x = 8;
         if (showDedicationSplash) {
            if (dedicationUntilMs == 0L) {
               dedicationUntilMs = System.currentTimeMillis() + 8000L;
            }

            if (System.currentTimeMillis() < dedicationUntilMs) {
               graphics.text(
                  mc.font,
                  Component.literal("Dedicated to all firefighters who risk their lives protecting forests, wildlife and communities."),
                  x,
                  y,
                  -13210,
                  true
               );
               y += 12;
            } else {
               showDedicationSplash = false;
            }
         }

         if (incident != null && incident.incidentId() > 0) {
            graphics.text(mc.font, Component.literal("#" + incident.incidentId() + " " + incident.name() + " [" + incident.status() + "]"), x, y, -21965, true);
            y += 10;
            graphics.text(
               mc.font,
               Component.literal("Burning: " + incident.burningBlocks() + "  Contain: " + String.format("%.0f%%", incident.containment())),
               x,
               y,
               -1118482,
               true
            );
            y += 10;
            if (!incident.nozzleMode().isEmpty()) {
               graphics.text(mc.font, Component.literal(incident.nozzleMode()), x, y, -39356, true);
               y += 10;
            }
         }

         if (wind != null) {
            graphics.text(mc.font, Component.literal(String.format("Wind: %.2f @ %.0f°", wind.speed(), wind.yawDegrees())), x, y, -5583634, true);
            y += 10;
         }

         if (nozzle != null && nozzle.connected()) {
            graphics.text(mc.font, Component.literal("Nozzle: " + nozzle.mode()), x, y, -10040065, true);
            y += 10;
            graphics.text(
               mc.font,
               Component.literal(String.format("Pressure: %.0f%%", nozzle.pressure01() * 100.0F)),
               x,
               y,
               nozzle.pressure01() < 0.35F ? -21965 : -7798870,
               true
            );
            y += 10;
            graphics.text(
               mc.font, Component.literal("Flow: " + (nozzle.flowActive() ? "Active" : "Closed")), x, y, nozzle.flowActive() ? -10035713 : -5592406, true
            );
            y += 10;
            String var6 = nozzle.tension();

            int tensionColor = switch (var6) {
               case "MAXIMUM" -> -48060;
               case "TIGHT" -> -21965;
               case "NORMAL" -> -1118584;
               default -> -7798904;
            };
            graphics.text(
               mc.font, Component.literal("Hose: " + nozzle.tension() + "  Remaining: " + nozzle.remainingDistance() + " blocks"), x, y, tensionColor, true
            );
         }
      }
   }
}
