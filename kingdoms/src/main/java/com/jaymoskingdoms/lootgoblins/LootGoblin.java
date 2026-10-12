package com.jaymoskingdoms.lootgoblins;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A loot goblin: a little goblin lugging a treasure chest. It wanders, runs from players (and anything hurting it), sparks
 * as it goes, and when a player kills it bursts into its own treasure and a pile of loot ({@link LootGoblins#burst}).
 * Eight looks ({@link #VARIANTS}), each with its own treasure: the plain goblin, nether (fireproof), ender, armourer,
 * cake, plant, Easter and witch.
 */
public class LootGoblin extends PathfinderMob {
	public static final int VARIANTS = 8, NETHER = 1;
	/** Health, speed; how near a player has to be to send it running, and how fast it runs. */
	static final double HEALTH = 10.0, SPEED = 0.35, FLEE_RANGE = 8.0, FLEE_SPEED = 1.0, PANIC_SPEED = 1.25;
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(LootGoblin.class, EntityDataSerializers.INT);

	public LootGoblin(EntityType<? extends LootGoblin> type, Level level) {
		super(type, level);
		this.xpReward = LootGoblins.GOBLIN_XP;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, PANIC_SPEED));
		this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, (float)FLEE_RANGE, FLEE_SPEED, FLEE_SPEED));
		this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(VARIANT, 0);
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	public void setVariant(int variant) {
		this.entityData.set(VARIANT, Math.floorMod(variant, VARIANTS));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("variant", this.variant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setVariant(input.getIntOr("variant", 0));
	}

	@Override
	public boolean fireImmune() {
		return this.variant() == NETHER || super.fireImmune();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		LootGoblin.spark(this);
	}

	/** A goblin's sparkle: a couple of electric sparks a tick (client side). */
	static void spark(net.minecraft.world.entity.LivingEntity mob) {
		Level level = mob.level();
		if (level.isClientSide()) {
			for (int i = 0; i < 2; i++) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, mob.getRandomX(0.5), mob.getRandomY() - 0.25, mob.getRandomZ(0.5),
					mob.getRandom().nextDouble() * 0.5 - 0.25, mob.getRandom().nextDouble() * 0.5 - 0.25, mob.getRandom().nextDouble() * 0.5 - 0.25);
			}
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		if (killedByPlayer) {
			LootGoblins.burst(level, this, LootGoblins.GOBLIN_TREASURE[this.variant()]);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.WITCH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WITCH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WITCH_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 2.0F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.AMETHYST_BLOCK_STEP, 1.0F, 2.0F);
		super.playStepSound(pos, state);
	}
}
