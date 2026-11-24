package com.jaymoskingdoms.illagers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class ThrownHatchet extends ThrownTrident implements ItemSupplier {
   public ThrownHatchet(EntityType<? extends ThrownHatchet> entityType, Level level) {
      super(entityType, level);
   }

   public ThrownHatchet(Level level, LivingEntity owner, ItemStack pickupItemStack) {
      this(level, owner.getX(), owner.getEyeY() - 0.1F, owner.getZ(), pickupItemStack);
      this.setOwner(owner);
   }

   public ThrownHatchet(Level level, double x, double y, double z, ItemStack pickupItemStack) {
      this(ModEntityTypes.HATCHET_ENTITY_TYPE, level);
      this.setPickupItemStack(pickupItemStack.copy());
      this.setCustomName((Component)pickupItemStack.get(DataComponents.CUSTOM_NAME));
      if (pickupItemStack.remove(DataComponents.INTANGIBLE_PROJECTILE) != null) {
         this.pickup = Pickup.CREATIVE_ONLY;
      }

      this.setPos(x, y, z);
      this.entityData.set(com.jaymoskingdoms.mixin.illagers.ThrownTridentAccessor.jaymoskingdoms$foil(), pickupItemStack.hasFoil());
   }

   protected ItemStack getDefaultPickupItem() {
      return new ItemStack(Items.IRON_AXE);
   }

   protected void onHitEntity(EntityHitResult result) {
      super.onHitEntity(result);
      this.setDeltaMovement(this.getDeltaMovement().scale(-0.5));
   }

   public boolean isInGround() {
      return super.isInGround();
   }

   public ItemStack getItem() {
      return this.getPickupItemStackOrigin();
   }
}
