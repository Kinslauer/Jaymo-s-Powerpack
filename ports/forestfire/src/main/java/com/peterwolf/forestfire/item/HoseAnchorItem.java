package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.firefighting.hose.HoseConnectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HoseAnchorItem extends Item {
   public HoseAnchorItem(Properties properties) {
      super(properties.stacksTo(16));
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      Player player = context.getPlayer();
      if (player == null) {
         return InteractionResult.PASS;
      } else if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
         BlockPos surface = context.getClickedPos();
         if (context.getClickedFace() == Direction.UP) {
            surface = surface.above();
         }

         Vec3 anchor = new Vec3(surface.getX() + 0.5, surface.getY() + 0.08, surface.getZ() + 0.5);
         boolean ok = HoseConnectionManager.get(serverLevel).insertAnchorNear(serverLevel, serverPlayer, anchor, 6.0);
         if (ok) {
            if (!player.isCreative()) {
               context.getItemInHand().shrink(1);
            }

            serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_anchor_placed"));
            return InteractionResult.CONSUME;
         } else {
            serverPlayer.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.hose_anchor_none"));
            return InteractionResult.FAIL;
         }
      } else {
         return InteractionResult.PASS;
      }
   }
}
