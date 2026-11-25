package com.jaymospowers.dino.entity;

import com.jaymospowers.dino.DinoConfig;
import com.jaymospowers.dino.DinoEntities;
import com.jaymospowers.dino.DinoItems;
import com.jaymospowers.dino.DinoSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

public class TranqDart extends AbstractArrow {
   private float torpor = 20.0F;

   public TranqDart(EntityType<? extends TranqDart> type, Level level) {
      super(type, level);
      this.setBaseDamage(0.4);
   }

   public TranqDart(Level level, LivingEntity owner, ItemStack dart, @Nullable ItemStack weapon, float torpor) {
      super((EntityType)DinoEntities.TRANQ_DART, owner, level, dart, weapon);
      this.torpor = torpor;
      this.setBaseDamage(0.4);
   }

   protected void onHitEntity(EntityHitResult hit) {
      Entity struck = hit.getEntity();
      Dinosaur dino = struck instanceof Dinosaur d ? d : null;
      super.onHitEntity(hit);
      if (!this.level().isClientSide()) {
         if (dino != null && dino.isAlive()) {
            dino.addTorpor(this.torpor * (float)DinoConfig.DART_STRENGTH, this.getOwner());
         }
      }
   }

   protected void doPostHurtEffects(LivingEntity living) {
      super.doPostHurtEffects(living);
      if (!(living instanceof Dinosaur)) {
         living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 2), this.getEffectSource());
         living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0), this.getEffectSource());
      }
   }

   protected ItemStack getDefaultPickupItem() {
      return new ItemStack((ItemLike)DinoItems.TRANQ_DART);
   }

   protected SoundEvent getDefaultHitGroundSoundEvent() {
      return (SoundEvent)DinoSounds.DART_HIT;
   }

   protected void addAdditionalSaveData(ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.putFloat("Torpor", this.torpor);
   }

   protected void readAdditionalSaveData(ValueInput input) {
      super.readAdditionalSaveData(input);
      this.torpor = input.getFloatOr("Torpor", 20.0F);
   }
}
