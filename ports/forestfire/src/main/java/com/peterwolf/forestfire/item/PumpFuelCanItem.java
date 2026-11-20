package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.firefighting.pump.PortablePumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;

public class PumpFuelCanItem extends Item {
   public PumpFuelCanItem(Properties properties) {
      super(properties.stacksTo(16));
   }

   public InteractionResult useOn(UseOnContext context) {
      BlockPos pos = context.getClickedPos();
      if (context.getLevel().getBlockEntity(pos) instanceof PortablePumpBlockEntity pump) {
         if (!context.getLevel().isClientSide()) {
            pump.addFuel(250);
            context.getItemInHand().shrink(1);
            if (context.getPlayer() != null) {
               context.getPlayer().sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.pump_refueled"));
            }
         }

         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }
}
