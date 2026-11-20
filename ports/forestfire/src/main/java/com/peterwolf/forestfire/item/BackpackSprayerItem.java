package com.peterwolf.forestfire.item;

import com.peterwolf.forestfire.config.ForestFireConfig;
import com.peterwolf.forestfire.fire.simulation.FireSimulation;
import com.peterwolf.forestfire.firefighting.nozzle.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public class BackpackSprayerItem extends Item {
   public BackpackSprayerItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   public static int getWater(ItemStack stack) {
      return (Integer)stack.getOrDefault(ModDataComponents.WATER_AMOUNT, ForestFireConfig.get().backpackCapacity);
   }

   public static void setWater(ItemStack stack, int amount) {
      stack.set(ModDataComponents.WATER_AMOUNT, Math.max(0, Math.min(ForestFireConfig.get().backpackCapacity, amount)));
   }

   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!player.isShiftKeyDown()) {
         if (getWater(stack) <= 0) {
            if (!level.isClientSide()) {
               player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.backpack_empty"));
            }

            return InteractionResult.FAIL;
         } else {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer) {
               HitResult hit = player.pick(4.0, 0.0F, false);
               BlockPos target = hit.getType() == Type.BLOCK ? BlockPos.containing(hit.getLocation()) : player.blockPosition().relative(player.getDirection());
               FireSimulation.get(serverLevel).applyWater(target, 0.55F, 1.0F);
               setWater(stack, getWater(stack) - 1);
               serverLevel.sendParticles(ParticleTypes.SPLASH, target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.02);
               serverLevel.playSound(null, target, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.4F, 1.6F);
               player.sendOverlayMessage(Component.literal("Backpack: " + getWater(stack) + " water"));
            }

            return InteractionResult.SUCCESS;
         }
      } else {
         if (!level.isClientSide()) {
            BlockPos pos = player.blockPosition();
            boolean water = level.getFluidState(pos).is(Fluids.WATER)
               || level.getFluidState(pos.below()).is(Fluids.WATER)
               || level.getBlockState(pos).is(Blocks.WATER);
            if (water) {
               setWater(stack, ForestFireConfig.get().backpackCapacity);
               player.sendOverlayMessage(Component.translatable("message.peterwolfs_forestfire.backpack_refilled"));
            } else {
               player.sendOverlayMessage(
                  Component.translatable(
                     "message.peterwolfs_forestfire.backpack_status", new Object[]{getWater(stack), ForestFireConfig.get().backpackCapacity}
                  )
               );
            }
         }

         return InteractionResult.SUCCESS;
      }
   }
}
