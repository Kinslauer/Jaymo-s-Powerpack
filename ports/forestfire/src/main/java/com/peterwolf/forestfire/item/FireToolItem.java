package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.fire.simulation.FireCell;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.fire.simulation.FuelMaterial;
import com.peterwolf.forestfire.world.FirebreakTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FireToolItem extends Item {
   private final FireToolItem.ToolType toolType;

   public FireToolItem(Properties properties, FireToolItem.ToolType toolType) {
      super(properties.stacksTo(1).durability(500));
      this.toolType = toolType;
   }

   public InteractionResult useOn(UseOnContext context) {
      FireToolItem.LevelAccess access = FireToolItem.LevelAccess.of(context);
      if (access == null) {
         return InteractionResult.PASS;
      } else {
         ServerLevel level = access.level;
         ServerPlayer player = access.player;
         BlockPos pos = context.getClickedPos();
         BlockState state = level.getBlockState(pos);
         FireSimulation sim = FireSimulation.get(level);
         boolean acted = false;
         switch (this.toolType) {
            case AXE:
               if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_DOORS)) {
                  level.destroyBlock(pos, true, player);
                  FireCell cell = sim.getCell(pos);
                  if (cell != null) {
                     cell.addHeat(-20.0F);
                     cell.addMoisture(5.0F);
                  }

                  acted = true;
               }
               break;
            case PULASKI:
               if (state.is(BlockTags.REPLACEABLE)
                  || state.is(Blocks.GRASS_BLOCK)
                  || state.is(Blocks.DIRT)
                  || state.is(Blocks.ROOTED_DIRT)
                  || state.is(BlockTags.LEAVES)) {
                  if (state.is(Blocks.GRASS_BLOCK)) {
                     level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                  } else if (!state.is(Blocks.DIRT) && !state.is(Blocks.ROOTED_DIRT)) {
                     level.destroyBlock(pos, false, player);
                  } else {
                     level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                  }

                  sim.firebreaks().mark(pos, FirebreakTracker.Type.TRENCH, -1);
                  exposeHotspot(sim, pos);
                  acted = true;
               }
               break;
            case SHOVEL:
               if (!state.is(Blocks.GRASS_BLOCK)
                  && !state.is(Blocks.DIRT)
                  && !state.is(Blocks.COARSE_DIRT)
                  && !state.is(Blocks.SAND)
                  && !state.is(Blocks.GRAVEL)) {
                  if (state.is(BlockTags.REPLACEABLE)) {
                     level.destroyBlock(pos, false, player);
                     sim.firebreaks().mark(pos, FirebreakTracker.Type.CLEARED_GRASS, -1);
                     acted = true;
                  }
               } else {
                  if (state.is(Blocks.GRASS_BLOCK)) {
                     level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                  }

                  sim.applyWater(pos.above(), 0.4F, 1.0F);
                  sim.firebreaks().mark(pos, FirebreakTracker.Type.BARE_DIRT, -1);
                  BlockPos above = pos.above();
                  if (level.getBlockState(above).is(Blocks.FIRE)) {
                     level.removeBlock(above, false);
                  }

                  acted = true;
               }
               break;
            case RAKE:
               if (state.is(BlockTags.REPLACEABLE)
                  || state.is(BlockTags.LEAVES)
                  || state.is(Blocks.SHORT_GRASS)
                  || state.is(Blocks.TALL_GRASS)
                  || state.is(Blocks.FERN)
                  || FuelMaterial.of(state) == FuelMaterial.DEAD_BUSH) {
                  level.destroyBlock(pos, false, player);
                  sim.firebreaks().mark(pos, FirebreakTracker.Type.CLEARED_GRASS, -1);
                  acted = true;
               }
         }

         if (acted) {
            context.getItemInHand().hurtAndBreak(1, player, context.getHand());
            player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.tool_used", new Object[]{this.toolType.name()}));
            return InteractionResult.CONSUME;
         } else {
            return InteractionResult.PASS;
         }
      }
   }

   private static void exposeHotspot(FireSimulation sim, BlockPos pos) {
      FireCell cell = sim.getCell(pos);
      if (cell != null && cell.stage().isHotspotRisk()) {
         cell.addHeat(5.0F);
      }

      FireCell below = sim.getCell(pos.below());
      if (below != null && below.stage().isHotspotRisk()) {
         below.addHeat(8.0F);
      }
   }

   private record LevelAccess(ServerLevel level, ServerPlayer player) {
      static FireToolItem.LevelAccess of(UseOnContext context) {
         return context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player
            ? new FireToolItem.LevelAccess(level, player)
            : null;
      }
   }

   public static enum ToolType {
      AXE,
      PULASKI,
      SHOVEL,
      RAKE;
   }
}
