package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.fire.incident.IncidentManager;
import com.peterwolf.forestfire.fire.weather.FireDangerLevel;
import com.peterwolf.forestfire.fire.weather.WeatherFireModel;
import com.peterwolf.forestfire.fire.weather.WindSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class FirefighterInfoItem extends Item {
   public FirefighterInfoItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
         IncidentManager manager = IncidentManager.get(serverLevel);
         WindSystem wind = manager.wind();
         FireDangerLevel danger = WeatherFireModel.danger(serverLevel, player.blockPosition(), wind);
         float humidity = WeatherFireModel.humidity01(serverLevel, player.blockPosition());
         player.sendSystemMessage(Component.literal("Fire danger: " + danger.name()));
         player.sendSystemMessage(Component.literal(String.format("Humidity: %.0f%%", humidity * 100.0F)));
         player.sendSystemMessage(Component.literal(String.format("Wind: %.0f blocks/%s", wind.speed() * 30.0F, wind.compassLabel())));
         player.sendSystemMessage(Component.literal("Recent rainfall: " + WeatherFireModel.recentRainfallLabel(serverLevel, player.blockPosition())));
         player.sendSystemMessage(Component.literal("Open incidents: " + manager.openIncidents().size()));
         return InteractionResult.CONSUME;
      } else {
         return InteractionResult.SUCCESS;
      }
   }
}
