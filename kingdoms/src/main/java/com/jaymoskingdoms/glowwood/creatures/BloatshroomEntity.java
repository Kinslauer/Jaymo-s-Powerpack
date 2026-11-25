package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.EnumSet;

/**
 * A waddling puffball. Close in on a player and it swells over 30 ticks, then bursts into a cloud of spores (Mycosis,
 * Nausea, a moment of Blindness). No block damage. Fire (or flint and steel) sets it off.
 */
public class BloatshroomEntity extends Monster {
	public static final int FUSE = 30;
	private static final EntityDataAccessor<Integer> DATA_SWELL_DIR = SynchedEntityData.defineId(BloatshroomEntity.class,
			EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_IGNITED = SynchedEntityData.defineId(BloatshroomEntity.class,
			EntityDataSerializers.BOOLEAN);
	private int oldSwell;
	private int swell;

	public BloatshroomEntity(EntityType<? extends BloatshroomEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SWELL_DIR, -1);
		builder.define(DATA_IGNITED, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SwellGoal());
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
	}

	public int getSwellDir() {
		return this.entityData.get(DATA_SWELL_DIR);
	}

	public void setSwellDir(int dir) {
		this.entityData.set(DATA_SWELL_DIR, dir);
	}

	public boolean isIgnited() {
		return this.entityData.get(DATA_IGNITED);
	}

	public void ignite() {
		this.entityData.set(DATA_IGNITED, true);
	}

	/** Fuse progress 0..1, interpolated. */
	public float getSwelling(float partialTick) {
		return Mth.lerp(partialTick, (float) this.oldSwell, (float) this.swell) / (float) (FUSE - 2);
	}

	@Override
	public void tick() {
		if (this.isAlive()) {
			this.oldSwell = this.swell;
			if (this.isIgnited() || this.isOnFire()) {
				this.setSwellDir(1);
			}
			int dir = this.getSwellDir();
			if (dir > 0 && this.swell == 0) {
				this.playSound(GlowSounds.BLOATSHROOM_SWELL, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
				this.gameEvent(GameEvent.PRIME_FUSE);
			}
			this.swell = Math.max(0, this.swell + dir);
			if (this.swell >= FUSE) {
				this.swell = FUSE;
				this.burst();
			}
		}
		super.tick();
	}

	private void burst() {
		if (this.level() instanceof ServerLevel level) {
			this.dead = true;
			SporeClouds.spawn(level, this.position().add(0.0, 0.6, 0.0), 3.5F, 100, this,
					Mycosis.instance(400, 0),
					new MobEffectInstance(MobEffects.NAUSEA, 120),
					new MobEffectInstance(MobEffects.BLINDNESS, 40));
			level.sendParticles(GlowFx.spore(), this.getX(), this.getY() + 0.7, this.getZ(), 60, 0.6, 0.5, 0.6, 0.12);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), GlowSounds.BLOATSHROOM_BURST,
					this.getSoundSource(), 1.4F, 0.9F + this.random.nextFloat() * 0.2F);
			this.triggerOnDeathMobEffects(level, Entity.RemovalReason.KILLED);
			this.discard();
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// It never bites: getting close is the attack.
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(ItemTags.CREEPER_IGNITERS)) {
			SoundEvent sound = stack.is(net.minecraft.world.item.Items.FIRE_CHARGE) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE;
			this.level().playSound(player, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), 1.0F,
					this.random.nextFloat() * 0.4F + 0.8F);
			if (!this.level().isClientSide()) {
				this.ignite();
				if (!stack.isDamageableItem()) {
					stack.shrink(1);
				} else {
					stack.hurtAndBreak(1, player, hand);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void addAdditionalSaveData(ValueOutput tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Ignited", this.isIgnited());
	}

	@Override
	public void readAdditionalSaveData(ValueInput tag) {
		super.readAdditionalSaveData(tag);
		if (tag.getBooleanOr("Ignited", false)) {
			this.ignite();
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return GlowSounds.BLOATSHROOM_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return GlowSounds.BLOATSHROOM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return GlowSounds.BLOATSHROOM_DEATH;
	}

	/** Swells while a target is within 3 blocks and in sight; deflates when it gets away. */
	private class SwellGoal extends Goal {
		private LivingEntity target;

		SwellGoal() {
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = BloatshroomEntity.this.getTarget();
			return BloatshroomEntity.this.getSwellDir() > 0 || t != null && BloatshroomEntity.this.distanceToSqr(t) < 9.0;
		}

		@Override
		public void start() {
			BloatshroomEntity.this.getNavigation().stop();
			this.target = BloatshroomEntity.this.getTarget();
		}

		@Override
		public void stop() {
			this.target = null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			BloatshroomEntity self = BloatshroomEntity.this;
			if (self.isIgnited()) {
				self.setSwellDir(1);
			} else if (this.target == null || self.distanceToSqr(this.target) > 49.0 || !self.getSensing().hasLineOfSight(this.target)) {
				self.setSwellDir(-1);
			} else {
				self.setSwellDir(1);
				self.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
			}
		}
	}
}
