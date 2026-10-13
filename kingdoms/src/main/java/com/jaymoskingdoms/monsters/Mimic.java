package com.jaymoskingdoms.monsters;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Containers;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.56) The chest mimic (Kingdoms' own): a chest that isn't. Shut, it sits as still as any chest; touch it, strike
 * it or step right up to it and it gapes open, all teeth and tongue, and bounds after you, biting. Left alone long
 * enough it shuts and waits again. A loot chest out in the wild is now and then one ({@link Monsters#MIMIC_CHANCE}):
 * it holds the chest's loot, and drops it when it dies.
 */
public class Mimic extends LegendMonster {
	static final double HEALTH = 30.0, SPEED = 0.3, DAMAGE = 7.0;
	/** It wakes for a player this close (blocks) who isn't sneaking. */
	static final double WAKE_RANGE = 1.6;
	/** With no one to bite for this long (ticks), it shuts and waits again. */
	static final int SLEEP_TICKS = 600;

	private static final EntityDataAccessor<Boolean> SHUT = SynchedEntityData.defineId(Mimic.class, EntityDataSerializers.BOOLEAN);

	private final List<ItemStack> loot = new ArrayList<>();
	private int idle;

	public Mimic(EntityType<? extends Mimic> type, Level level) {
		super(type, level);
		this.xpReward = 12;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.FOLLOW_RANGE, 20.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4F) {
			@Override
			public boolean canUse() {
				return !Mimic.this.shut() && super.canUse();
			}
		});
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.3, true) {
			@Override
			public boolean canUse() {
				return !Mimic.this.shut() && super.canUse();
			}
		});
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> !this.shut() && level.getDifficulty() != Difficulty.PEACEFUL));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(SHUT, true);
	}

	/** Shut and still, a chest to look at. */
	public boolean shut() {
		return this.entityData.get(SHUT);
	}

	/** Gapes open and goes for {@code player} (if any). */
	public void wake(@Nullable Player player) {
		if (this.shut()) {
			this.entityData.set(SHUT, false);
			this.playSound(SoundEvents.CHEST_OPEN, 1.0F, 0.6F);
			this.playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.0F, 0.8F);
		}
		this.idle = 0;
		if (player != null && !player.isCreative() && !player.isSpectator()) {
			this.setTarget(player);
		}
	}

	private void sleep() {
		this.entityData.set(SHUT, true);
		this.setTarget(null);
		this.getNavigation().stop();
		// squares itself up like a placed chest
		float yaw = Direction.fromYRot(this.getYRot()).toYRot();
		this.setYRot(yaw);
		this.yBodyRot = this.yHeadRot = yaw;
		this.playSound(SoundEvents.CHEST_CLOSE, 1.0F, 0.6F);
	}

	/** Holds what the chest held, to drop when it dies. */
	public void hold(List<ItemStack> stacks) {
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				this.loot.add(stack.copy());
			}
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.shut()) {
			this.getNavigation().stop();
			this.setYHeadRot(this.yBodyRot);
			if (this.tickCount % 10 == 0) {
				Player near = level.getNearestPlayer(this, WAKE_RANGE + this.getBbWidth() / 2);
				if (near != null && !near.isShiftKeyDown() && !near.isCreative() && !near.isSpectator()) {
					this.wake(near);
				}
			}
			return;
		}
		if (this.getTarget() != null && this.getTarget().isAlive()) {
			this.idle = 0;
		} else if (++this.idle > SLEEP_TICKS) {
			this.sleep();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.shut()) {
			this.yHeadRot = this.yBodyRot;
			this.setYRot(this.yBodyRot);
		}
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.shut()) {
			if (!this.level().isClientSide()) {
				this.wake(player);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive()) {
			this.wake(source.getEntity() instanceof Player p ? p : null);
		}
		return hurt;
	}

	@Override
	public boolean isPushable() {
		return !this.shut() && super.isPushable();
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (ItemStack stack : this.loot) {
			Containers.dropItemStack(level, this.getX(), this.getY() + 0.5, this.getZ(), stack);
		}
		this.loot.clear();
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return this.shut() ? null : SoundEvents.CHEST_LOCKED;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WOOD_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WOOD_BREAK;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("shut", this.shut());
		output.store("loot", ItemStack.CODEC.listOf(), List.copyOf(this.loot));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(SHUT, input.getBooleanOr("shut", true));
		this.loot.clear();
		input.read("loot", ItemStack.CODEC.listOf()).ifPresent(this.loot::addAll);
	}
}
