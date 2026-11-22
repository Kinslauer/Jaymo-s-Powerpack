package com.peterwolf.forestfire.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class WindCommands {
   private WindCommands() {
   }

   public static void register() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                          "wind"
                                       )
                                       .executes(ctx -> status((CommandSourceStack)ctx.getSource())))
                                    .then(Commands.literal("status").executes(ctx -> status((CommandSourceStack)ctx.getSource()))))
                                 .then(
                                    ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("set")
                                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                                             .then(
                                                Commands.literal("speed")
                                                   .then(
                                                      Commands.argument("speed", FloatArgumentType.floatArg(0.0F, 4.0F))
                                                         .executes(
                                                            ctx -> setSpeed((CommandSourceStack)ctx.getSource(), FloatArgumentType.getFloat(ctx, "speed"))
                                                         )
                                                   )
                                             ))
                                          .then(
                                             Commands.literal("direction")
                                                .then(
                                                   Commands.argument("degrees", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                                      .executes(
                                                         ctx -> setDirection((CommandSourceStack)ctx.getSource(), FloatArgumentType.getFloat(ctx, "degrees"))
                                                      )
                                                )
                                          ))
                                       .then(
                                          Commands.literal("fixed")
                                             .then(
                                                Commands.argument("speed", FloatArgumentType.floatArg(0.0F, 4.0F))
                                                   .then(
                                                      Commands.argument("degrees", FloatArgumentType.floatArg(-180.0F, 180.0F))
                                                         .executes(
                                                            ctx -> setFixed(
                                                               (CommandSourceStack)ctx.getSource(),
                                                               FloatArgumentType.getFloat(ctx, "speed"),
                                                               FloatArgumentType.getFloat(ctx, "degrees")
                                                            )
                                                         )
                                                   )
                                             )
                                       )
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("mode")
                                          .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                                       .then(Commands.literal("dynamic").executes(ctx -> setMode((CommandSourceStack)ctx.getSource(), true))))
                                    .then(Commands.literal("fixed").executes(ctx -> setMode((CommandSourceStack)ctx.getSource(), false)))
                              ))
                           .then(
                              ((LiteralArgumentBuilder)Commands.literal("enable").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                                 .executes(ctx -> setEnabled((CommandSourceStack)ctx.getSource(), true))
                           ))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("disable").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                              .executes(ctx -> setEnabled((CommandSourceStack)ctx.getSource(), false))
                        ))
                     .then(
                        ((LiteralArgumentBuilder)Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                           .executes(ctx -> reset((CommandSourceStack)ctx.getSource()))
                     ))
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                        .executes(ctx -> reload((CommandSourceStack)ctx.getSource()))
                  )
            )
         );
   }

   private static int status(CommandSourceStack source) {
      WindSystem wind = wind(source);
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      String state = cfg.windEnabled ? "enabled" : "disabled";
      String mode = cfg.dynamicWind ? "dynamic" : "fixed";
      source.sendSuccess(
         () -> Component.literal(
            String.format(Locale.ROOT, "Wind: %.2f (%s, %.0f°), %s / %s", wind.speed(), wind.compassLabel(), wind.yawDegrees(), state, mode)
         ),
         false
      );
      source.sendSuccess(
         () -> Component.literal(
            String.format(
               Locale.ROOT,
               "Limits %.2f–%.2f; change every %d ticks; speed/direction change %.2f/%.1f°",
               cfg.minimumWindSpeed,
               cfg.maximumWindSpeed,
               cfg.windChangeIntervalTicks,
               cfg.windChangeStrength,
               cfg.windDirectionChangeDegrees
            )
         ),
         false
      );
      return 1;
   }

   private static int setSpeed(CommandSourceStack source, float requestedSpeed) {
      WindSystem wind = wind(source);
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      wind.setSpeed(requestedSpeed);
      if (!cfg.dynamicWind) {
         cfg.configuredWindSpeed = wind.baseSpeed();
         ForestFireConfig.save();
      }

      markDirty(source);
      return changed(source, wind, "Wind speed changed");
   }

   private static int setDirection(CommandSourceStack source, float degrees) {
      WindSystem wind = wind(source);
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      wind.setYawDegrees(degrees);
      if (!cfg.dynamicWind) {
         cfg.configuredWindYawDegrees = wind.yawDegrees();
         ForestFireConfig.save();
      }

      markDirty(source);
      return changed(source, wind, "Wind direction changed");
   }

   private static int setFixed(CommandSourceStack source, float speed, float degrees) {
      WindSystem wind = wind(source);
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      cfg.dynamicWind = false;
      wind.setWind(speed, degrees);
      cfg.configuredWindSpeed = wind.baseSpeed();
      cfg.configuredWindYawDegrees = wind.yawDegrees();
      ForestFireConfig.save();
      markDirty(source);
      return changed(source, wind, "Fixed wind configured");
   }

   private static int setMode(CommandSourceStack source, boolean dynamic) {
      WindSystem wind = wind(source);
      ForestFireConfig.Data cfg = ForestFireConfig.get();
      cfg.dynamicWind = dynamic;
      if (!dynamic) {
         cfg.configuredWindSpeed = wind.baseSpeed();
         cfg.configuredWindYawDegrees = wind.yawDegrees();
      }

      ForestFireConfig.save();
      markDirty(source);
      source.sendSuccess(() -> Component.literal("Wind mode: " + (dynamic ? "dynamic" : "fixed")), true);
      return 1;
   }

   private static int setEnabled(CommandSourceStack source, boolean enabled) {
      ForestFireConfig.get().windEnabled = enabled;
      ForestFireConfig.save();
      markDirty(source);
      source.sendSuccess(() -> Component.literal("Wind simulation " + (enabled ? "enabled" : "disabled") + "."), true);
      return 1;
   }

   private static int reset(CommandSourceStack source) {
      WindSystem wind = wind(source);
      wind.resetToConfigured();
      markDirty(source);
      return changed(source, wind, "Wind reset to configured values");
   }

   private static int reload(CommandSourceStack source) {
      ForestFireConfig.load();
      WindSystem wind = wind(source);
      wind.resetToConfigured();
      markDirty(source);
      source.sendSuccess(() -> Component.literal("Reloaded wind settings from " + ForestFireConfig.path()), true);
      return status(source);
   }

   private static int changed(CommandSourceStack source, WindSystem wind, String action) {
      source.sendSuccess(
         () -> Component.literal(String.format(Locale.ROOT, "%s: %.2f toward %s (%.0f°).", action, wind.speed(), wind.compassLabel(), wind.yawDegrees())), true
      );
      return 1;
   }

   private static WindSystem wind(CommandSourceStack source) {
      return IncidentManager.get(source.getLevel()).wind();
   }

   private static void markDirty(CommandSourceStack source) {
      ServerLevel level = source.getLevel();
      IncidentManager.get(level).markDirty();
   }
}
