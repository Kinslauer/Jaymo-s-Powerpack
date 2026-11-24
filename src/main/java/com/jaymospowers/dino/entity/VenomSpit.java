package com.jaymospowers.dino.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jspecify.annotations.Nullable;

public class VenomSpit extends Projectile {
   private @Nullable Dinosaur spitter;

   public VenomSpit(EntityType<? extends VenomSpit> type, Level level) {
      super(type, level);
   }

   public void setSpitter(Dinosaur spitter) {
      this.spitter = spitter;
   }

   protected double getDefaultGravity() {
      return 0.05;
   }

   public void tick() {
      super.tick();
      Vec3 movement = this.getDeltaMovement();
      HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
      if (hit.getType() != Type.MISS) {
         this.hitTargetOrDeflectSelf(hit);
      }

      double x = this.getX() + movement.x;
      double y = this.getY() + movement.y;
      double z = this.getZ() + movement.z;
      this.updateRotation();
      if (this.level().isClientSide()) {
         this.level().addParticle(ParticleTypes.SPIT, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
         this.level().addParticle(ParticleTypes.ITEM_SLIME, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
      }

      boolean inBlock = this.level().findBlocksIn(this.getBoundingBox()).filterState(BlockStateBase::isAir).noneMatched();
      if (!inBlock && !this.isInWater() && this.tickCount <= 100) {
         this.setDeltaMovement(movement.scale(0.99));
         this.applyGravity();
         this.setPos(x, y, z);
      } else {
         this.discard();
      }
   }

   protected boolean canHitEntity(Entity target) {
      Entity whole = target;
      return this.spitter == null || whole != this.spitter && !this.spitter.isFriend(whole) ? super.canHitEntity(target) : false;
   }

   protected void onHitEntity(EntityHitResult hit) {
      super.onHitEntity(hit);
      if (this.level() instanceof ServerLevel level) {
         Entity target = hit.getEntity();
         LivingEntity credited = (LivingEntity)(this.getOwner() instanceof LivingEntity l ? l : this.spitter);
         DamageSource source = credited != null ? this.damageSources().spit(this, credited) : this.damageSources().magic();
         float damage = this.spitter != null ? 2.0F + this.spitter.getDinoLevel() * 0.1F : 2.0F;
         if (target.hurtServer(level, source, damage) && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0), credited);
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), credited);
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0), credited);
         }

         level.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.0);
         this.discard();
      }
   }

   protected void onHitBlock(BlockHitResult hit) {
      super.onHitBlock(hit);
      if (this.level() instanceof ServerLevel level) {
         level.sendParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY(), this.getZ(), 8, 0.15, 0.15, 0.15, 0.0);
         this.discard();
      }
   }

   protected void defineSynchedData(Builder data) {
   }

   public void recreateFromPacket(ClientboundAddEntityPacket packet) {
      super.recreateFromPacket(packet);
      Vec3 movement = packet.getMovement();

      for (int i = 0; i < 6; i++) {
         double k = 0.4 + 0.1 * i;
         this.level().addParticle(ParticleTypes.SPIT, this.getX(), this.getY(), this.getZ(), movement.x * k, movement.y, movement.z * k);
      }

      this.setDeltaMovement(movement);
   }
}
