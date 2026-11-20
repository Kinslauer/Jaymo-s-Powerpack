package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.fire.simulation.FireCell;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class ThermalScannerItem extends Item {
   public ThermalScannerItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
         HitResult hit = player.pick(8.0, 0.0F, false);
         BlockPos pos = hit.getType() == Type.BLOCK ? BlockPos.containing(hit.getLocation()) : player.blockPosition();
         FireCell cell = FireSimulation.get(serverLevel).getCell(pos);
         if (cell == null) {
            cell = FireSimulation.get(serverLevel).getCell(pos.below());
         }

         if (cell == null) {
            player.sendSystemMessage(Component.literal("Surface temperature: COLD"));
            player.sendSystemMessage(Component.literal("Reignition risk: NONE"));
            player.sendSystemMessage(Component.literal("Recommended action: Continue search"));
            return InteractionResult.CONSUME;
         } else {
            FireCell.ThermalReading reading = cell.thermalReading();

            String risk = switch (reading) {
               case COLD -> "NONE";
               case WARM -> "LOW";
               case HOT -> "HIGH";
               case CRITICAL -> "CRITICAL";
            };

            String action = switch (reading) {
               case COLD -> "Continue patrol";
               case WARM -> "Monitor and cool if needed";
               case HOT -> "Apply water and expose material";
               case CRITICAL -> "Immediate cooling + dig overhaul";
            };
            player.sendSystemMessage(Component.literal("Surface temperature: " + reading.name()));
            player.sendSystemMessage(Component.literal("Reignition risk: " + risk));
            player.sendSystemMessage(Component.literal("Stage: " + cell.stage().name() + " heat=" + (cell.heat & 0xFF)));
            player.sendSystemMessage(Component.literal("Recommended action: " + action));
            return InteractionResult.CONSUME;
         }
      } else {
         return InteractionResult.SUCCESS;
      }
   }
}
