package com.jaymoskingdoms.monsters;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.56) The silver skeleton (after Mo' Creatures'): a skeleton warrior that sprints at you from afar and cuts at you;
 * daylight burns it. Now and then one drops its silver sword, the bane of werewolves. (0.7.326) Drawn as polyjo's Dead
 * Traveler, with one sword and a buckler: it cuts with the sword and now and then takes a blow from the front on the
 * buckler instead; an axe knocks its guard down for a while.
 */
public class SilverSkeleton extends LegendMonster {
	static final double HEALTH = 25.0, SPEED = 0.27, DAMAGE = 4.0;
	/** It sprints at anyone farther than this (blocks). */
	static final double SPRINT_FROM = 4.0;
	/** (0.7.326) How often it takes a blow from in front on its buckler, and how wide "in front" is (degrees either side). */
	static final float BLOCK_CHANCE = 0.3F, BLOCK_ARC = 70.0F;
	/** How long an axe blow on the buckler leaves it unable to block (ticks). */
	static final int GUARD_BROKEN_TICKS = 100;
	/** How long its sword cut shows (ticks): the Dead Traveler's slash, played through. */
	static final int SLASH_TICKS = 25;
	public static final byte BLOCKED = 92, GUARD_BROKEN = 93;

	private int guardBroken;
	/** (Client) the tick it last blocked, and the tick its guard was last broken. */
	private int blockedAt = -1000, brokenAt = -1000;

	public SilverSkeleton(EntityType<? extends SilverSkeleton> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
		// (0.7.217) villagers too, for less ({@link com.jaymoskingdoms.VillagerHunters#VILLAGER_DAMAGE})
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.npc.villager.AbstractVillager.class, true));
	}

	/** (0.7.326) Its sword is in its right hand. */
	@Override
	protected int nextArm() {
		return RIGHT;
	}

	@Override
	protected int swingTicks() {
		return SLASH_TICKS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.blocks(source)) {
			Entity attacker = source.getEntity();
			boolean axe = attacker instanceof LivingEntity living && living.getMainHandItem().is(ItemTags.AXES);
			if (axe) {
				this.guardBroken = GUARD_BROKEN_TICKS;
			}
			level.playSound(null, this.getX(), this.getY(), this.getZ(), axe ? SoundEvents.SHIELD_BREAK.value() : SoundEvents.SHIELD_BLOCK.value(),
				SoundSource.HOSTILE, 1.0F, 0.8F + this.random.nextFloat() * 0.4F);
			level.broadcastEntityEvent(this, axe ? GUARD_BROKEN : BLOCKED);
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	/** Whether it takes {@code source} on its buckler: a blow or a shot from in front, now and then, while its guard is up. */
	private boolean blocks(DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null || this.guardBroken > 0 || source.getEntity() == null || this.random.nextFloat() >= BLOCK_CHANCE
			|| source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) {
			return false;
		}
		Vec3 to = from.subtract(this.position());
		float angle = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		return Math.abs(net.minecraft.util.Mth.wrapDegrees(angle - this.yBodyRot)) <= BLOCK_ARC;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == BLOCKED) {
			this.blockedAt = this.tickCount;
		} else if (id == GUARD_BROKEN) {
			this.blockedAt = this.tickCount;
			this.brokenAt = this.tickCount;
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** (Client) how long since it last blocked, and since its guard was last broken (ticks). */
	public int sinceBlocked() {
		return this.tickCount - this.blockedAt;
	}

	public int sinceGuardBroken() {
		return this.tickCount - this.brokenAt;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.guardBroken > 0) {
			this.guardBroken--;
		}
		LivingEntity target = this.getTarget();
		this.setSprinting(target != null && this.distanceToSqr(target) > SPRINT_FROM * SPRINT_FROM);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SKELETON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SKELETON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SKELETON_DEATH;
	}

	@Override
	protected void playStepSound(net.minecraft.core.BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SKELETON_STEP, 0.15F, 1.0F);
	}
}
