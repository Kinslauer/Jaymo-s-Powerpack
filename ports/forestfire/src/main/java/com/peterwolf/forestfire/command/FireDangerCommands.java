package com.peterwolf.forestfire.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.weather.FireDangerLevel;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class FireDangerCommands {
   private FireDangerCommands() {
   }

   public static void register() {
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)Commands.literal("firedanger")
                  .executes(
                     ctx -> {
                        ServerLevel level = ((CommandSourceStack)ctx.getSource()).getLevel();
                        BlockPos pos = BlockPos.containing(((CommandSourceStack)ctx.getSource()).getPosition());
                        WindSystem wind = IncidentManager.get(level).wind();
                        FireDangerLevel danger = WeatherFireModel.danger(level, pos, wind);
                        float humidity = WeatherFireModel.humidity01(level, pos);
                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Fire danger: " + danger.name()), false);
                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal(String.format("Humidity: %.0f%%", humidity * 100.0F)), false);
                        ((CommandSourceStack)ctx.getSource())
                           .sendSuccess(() -> Component.literal(String.format("Wind: %.0f blocks/%s", wind.speed() * 30.0F, wind.compassLabel())), false);
                        ((CommandSourceStack)ctx.getSource())
                           .sendSuccess(() -> Component.literal("Recent rainfall: " + WeatherFireModel.recentRainfallLabel(level, pos)), false);
                        return 1;
                     }
                  )
            )
         );
   }
}
