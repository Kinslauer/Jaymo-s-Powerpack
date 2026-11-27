package com.jaymospowers.dino.entity;

import com.jaymospowers.dino.DinoItems;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class FossilSkeleton extends Entity {
   public static final int STANCES = 4;
   private static final EntityDataAccessor<Integer> SPECIES = SynchedEntityData.defineId(FossilSkeleton.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> STANCE = SynchedEntityData.defineId(FossilSkeleton.class, EntityDataSerializers.INT);

   public FossilSkeleton(EntityType<? extends FossilSkeleton> type, Level level) {
      super(type, level);
      this.blocksBuilding = true;
   }

   protected void defineSynchedData(Builder data) {
      data.define(SPECIES, 0);
      data.define(STANCE, 0);
   }

   public Species species() {
      Species[] all = Species.values();
      return all[Math.floorMod((Integer)this.entityData.get(SPECIES), all.length)];
   }

   public void setSpecies(Species species) {
      this.entityData.set(SPECIES, species.ordinal());
      this.refreshDimensions();
   }

   public int stance() {
      return Math.floorMod((Integer)this.entityData.get(STANCE), 4);
   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (SPECIES.equals(key)) {
         this.refreshDimensions();
      }
   }

   public EntityDimensions getDimensions(Pose pose) {
      Species s = this.species();
      return EntityDimensions.fixed(s.width, s.height);
   }

   public boolean isPickable() {
      return !this.isRemoved();
   }

   public boolean canBeCollidedWith(@Nullable Entity other) {
      return true;
   }

   public boolean isPushable() {
      return false;
   }

   public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
      if (!this.level().isClientSide()) {
         this.entityData.set(STANCE, (this.stance() + 1) % 4);
         this.playSound(SoundEvents.SKELETON_STEP, 1.0F, 0.7F);
      }

      return InteractionResult.SUCCESS;
   }

   public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
      if (!this.isRemoved() && !this.isInvulnerableToBase(source)) {
         if (source.getDirectEntity() instanceof Player player
            && source.getEntity() == player
            && player.mayBuild()
            && level.mayInteract(player, this.blockPosition())) {
            if (!player.hasInfiniteMaterials()) {
               this.spawnAtLocation(level, this.kit());
            }

            level.sendParticles(
               new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
               this.getX(),
               this.getY() + this.getBbHeight() * 0.5,
               this.getZ(),
               40,
               this.getBbWidth() * 0.4,
               this.getBbHeight() * 0.3,
               this.getBbWidth() * 0.4,
               0.05
            );
            this.playSound(SoundEvents.SKELETON_DEATH, 1.0F, 0.6F);
            this.discard();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public ItemStack kit() {
      return new ItemStack(DinoItems.skeleton(this.species()));
   }

   public @Nullable ItemStack getPickResult() {
      return this.kit();
   }

   protected void readAdditionalSaveData(ValueInput input) {
      this.setSpecies(Species.byId(input.getStringOr("Species", Species.VELOCIRAPTOR.id)));
      this.entityData.set(STANCE, input.getIntOr("Stance", 0));
   }

   protected void addAdditionalSaveData(ValueOutput output) {
      output.putString("Species", this.species().id);
      output.putInt("Stance", this.stance());
   }
}
