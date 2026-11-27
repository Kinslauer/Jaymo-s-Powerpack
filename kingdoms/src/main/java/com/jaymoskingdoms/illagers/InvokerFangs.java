package com.jaymoskingdoms.illagers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public class InvokerFangs extends EvokerFangs {
   public InvokerFangs(EntityType<? extends InvokerFangs> entityType, Level level) {
      super(entityType, level);
   }

   public InvokerFangs(Level level, double x, double y, double z, float yRot, int warmupDelay, LivingEntity owner) {
      this(ModEntityTypes.INVOKER_FANGS_ENTITY_TYPE, level);
      ((com.jaymoskingdoms.mixin.illagers.EvokerFangsAccessor)this).jaymoskingdoms$setWarmupDelayTicks(warmupDelay);
      this.setOwner(owner);
      this.setYRot(yRot * (180.0F / (float)Math.PI));
      this.setPos(x, y, z);
   }

   /** In place of vanilla's private bite (mixin/illagers/EvokerFangsBiteMixin). */
   public void bite(LivingEntity target) {
      LivingEntity owner = this.getOwner();
      if (target.isAlive() && !target.isInvulnerable() && target != owner) {
         if (owner == null) {
            target.hurt(this.damageSources().magic(), 10.0F);
            target.push(0.0, 1.7, 0.0);
         } else if (!owner.isAlliedTo(target)) {
            DamageSource damageSource = this.damageSources().indirectMagic(this, owner);
            if (this.level() instanceof ServerLevel serverLevel && target.hurtServer(serverLevel, damageSource, 6.0F)) {
               EnchantmentHelper.doPostAttackEffects(serverLevel, target, damageSource);
               target.push(0.0, 0.6, 0.0);
            }
         }
      }
   }

   public void handleEntityEvent(byte id) {
      super.handleEntityEvent(id);
      if (id == 4) {
         ((com.jaymoskingdoms.mixin.illagers.EvokerFangsAccessor)this).jaymoskingdoms$setClientSideAttackStarted(true);
         if (!this.isSilent()) {
            this.level()
               .playLocalSound(
                  this.getX(),
                  this.getY(),
                  this.getZ(),
                  (SoundEvent)ModSoundEvents.INVOKER_FANGS_SOUND_EVENT.value(),
                  this.getSoundSource(),
                  1.0F,
                  this.random.nextFloat() * 0.2F + 0.85F,
                  false
               );
         }
      }
   }
}
