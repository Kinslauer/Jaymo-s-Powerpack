package com.jaymoskingdoms.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.55) The tarantula (after Better Animals Plus): a dog-sized hairy spider of deserts, badlands and jungles. It
 * climbs walls like a spider, bites, and every {@link #FLICK_TICKS} flicks its stinging hairs at a player within
 * {@link #FLICK_RANGE} blocks it can see (blinding and sickening them for a moment). Six looks: three desert, three
 * jungle.
 */
public class Tarantula extends Monster implements WildLook {
	public static final int LOOKS = 6;
	static final double HEALTH = 20.0, SPEED = 0.3, DAMAGE = 4.0;
	/** The stinging hairs: how often (ticks), how far (blocks), how long they blind and sicken (ticks). */
	static final int FLICK_TICKS = 160, HAIR_TICKS = 60;
	static final double FLICK_RANGE = 12.0;
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Tarantula.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(Tarantula.class, EntityDataSerializers.BOOLEAN);
	private int flickIn = FLICK_TICKS / 2;

	public Tarantula(EntityType<? extends Tarantula> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		// (0.7.217) villagers too, for less ({@link com.jaymoskingdoms.VillagerHunters#VILLAGER_DAMAGE})
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.npc.villager.AbstractVillager.class, true));
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WallClimberNavigation(this, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(VARIANT, 0);
		entityData.define(CLIMBING, false);
	}

	@Override
	public int variant() {
		return this.entityData.get(VARIANT);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			this.entityData.set(CLIMBING, this.horizontalCollision);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (--this.flickIn > 0 || target == null || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		this.flickIn = FLICK_TICKS;
		if (this.distanceTo(target) <= FLICK_RANGE && this.hasLineOfSight(target)) {
			Vec3 from = this.position().add(0.0, 0.6, 0.0), to = target.getEyePosition();
			for (int i = 1; i <= 8; i++) {
				Vec3 at = from.lerp(to, i / 8.0);
				level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.01);
			}
			this.playSound(SoundEvents.LLAMA_SPIT, 1.0F, 0.6F);
			target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, HAIR_TICKS), this);
			target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, HAIR_TICKS * 2), this);
			target.hurtServer(level, this.damageSources().mobAttack(this), 1.0F);
		}
	}

	@Override
	public boolean onClimbable() {
		return this.entityData.get(CLIMBING);
	}

	@Override
	public void makeStuckInBlock(BlockState state, Vec3 speedMultiplier) {
		if (!state.is(Blocks.COBWEB)) {
			super.makeStuckInBlock(state, speedMultiplier);
		}
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		BlockPos pos = this.blockPosition();
		boolean jungle = level.getBiome(pos).is(BiomeTags.IS_JUNGLE);
		this.entityData.set(VARIANT, (jungle ? 3 : 0) + this.random.nextInt(3));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("variant", this.variant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(VARIANT, Math.floorMod(input.getIntOr("variant", 0), LOOKS));
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SPIDER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.7F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SPIDER_STEP, 0.15F, 0.8F);
	}
}
