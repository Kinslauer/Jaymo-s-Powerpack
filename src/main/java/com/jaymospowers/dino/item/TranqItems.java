package com.jaymospowers.dino.item;

import com.jaymospowers.dino.DinoEntities;
import com.jaymospowers.dino.DinoItems;
import com.jaymospowers.dino.DinoSounds;
import com.jaymospowers.dino.entity.FossilSkeleton;
import com.jaymospowers.dino.entity.ModTriggers;
import com.jaymospowers.dino.entity.Species;
import com.jaymospowers.dino.entity.TranqDart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TranqItems {
   public static final float HAND_TORPOR = 14.0F;
   public static final float RIFLE_TORPOR = 24.0F;

   private TranqItems() {
   }

   public static class Dart extends Item {
      public Dart(Properties properties) {
         super(properties);
      }

      public InteractionResult use(Level level, Player player, InteractionHand hand) {
         ItemStack stack = player.getItemInHand(hand);
         level.playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.SNOWBALL_THROW,
            SoundSource.PLAYERS,
            0.5F,
            0.6F + level.getRandom().nextFloat() * 0.2F
         );
         if (level instanceof ServerLevel server) {
            Projectile.spawnProjectileFromRotation(
               (l, owner, s) -> new TranqDart(l, owner, s.copyWithCount(1), null, 14.0F), server, stack, player, 0.0F, 1.5F, 1.0F
            );
         }

         player.getCooldowns().addCooldown(stack, 12);
         stack.consume(1, player);
         return InteractionResult.SUCCESS;
      }
   }

   public static class Rifle extends Item {
      public Rifle(Properties properties) {
         super(properties);
      }

      private static ItemStack findDart(Player player) {
         for (InteractionHand h : InteractionHand.values()) {
            if (player.getItemInHand(h).is((Item)DinoItems.TRANQ_DART)) {
               return player.getItemInHand(h);
            }
         }

         for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is((Item)DinoItems.TRANQ_DART)) {
               return s;
            }
         }

         return ItemStack.EMPTY;
      }

      public InteractionResult use(Level level, Player player, InteractionHand hand) {
         ItemStack rifle = player.getItemInHand(hand);
         ItemStack dart = findDart(player);
         boolean free = player.hasInfiniteMaterials();
         if (dart.isEmpty() && !free) {
            if (player instanceof ServerPlayer sp) {
               sp.sendOverlayMessage(Component.translatable("message.jaymospowers.dino.no_darts"));
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
            return InteractionResult.FAIL;
         } else {
            level.playSound(
               null,
               player.getX(),
               player.getY(),
               player.getZ(),
               (SoundEvent)DinoSounds.DART_FIRE,
               SoundSource.PLAYERS,
               1.0F,
               0.95F + level.getRandom().nextFloat() * 0.1F
            );
            if (level instanceof ServerLevel server) {
               ItemStack shot = new ItemStack((ItemLike)DinoItems.TRANQ_DART);
               TranqDart fired = (TranqDart)Projectile.spawnProjectileFromRotation(
                  (l, owner, s) -> new TranqDart(l, owner, s, rifle, 24.0F), server, shot, player, 0.0F, 3.4F, 0.3F
               );
               if (free) {
                  fired.pickup = Pickup.CREATIVE_ONLY;
               } else {
                  dart.shrink(1);
               }

               rifle.hurtAndBreak(1, player, hand);
            }

            player.getCooldowns().addCooldown(rifle, 22);
            return InteractionResult.SUCCESS;
         }
      }
   }

   public static class SkeletonKit extends Item {
      private final Species species;

      public SkeletonKit(Species species, Properties properties) {
         super(properties);
         this.species = species;
      }

      public InteractionResult useOn(UseOnContext context) {
         if (context.getClickedFace() == Direction.DOWN) {
            return InteractionResult.FAIL;
         }

         Level level = context.getLevel();
         BlockPos at = new BlockPlaceContext(context).getClickedPos();
         Vec3 pos = Vec3.atBottomCenterOf(at);
         double half = Math.min(this.species.width, 1.6) * 0.5;
         AABB box = new AABB(pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + Math.min(this.species.height, 3.0), pos.z + half);
         if (level.noCollision(null, box) && level.getEntities(null, box).isEmpty()) {
            if (level instanceof ServerLevel server) {
               FossilSkeleton mount = (FossilSkeleton)((EntityType)DinoEntities.FOSSIL_SKELETON).create(server, EntitySpawnReason.SPAWN_ITEM_USE);
               if (mount == null) {
                  return InteractionResult.FAIL;
               }

               mount.setSpecies(this.species);
               float yaw = Mth.floor((Mth.wrapDegrees(context.getRotation() - 180.0F) + 11.25F) / 22.5F) * 22.5F;
               mount.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
               server.addFreshEntity(mount);
               level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.9F, 0.6F);
               if (context.getPlayer() instanceof ServerPlayer sp) {
                  ModTriggers.award(sp, "mount_skeleton");
               }
            }

            context.getItemInHand().shrink(1);
            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.FAIL;
         }
      }
   }
}
