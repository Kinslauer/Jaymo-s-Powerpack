package com.jaymoskingdoms.beasts;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.339) The haunted armour stand, drawn as polyjo's Armor Stand (Blockbench Workshop, CC BY 4.0, "this one feels
 * haunted"): it stands in the dark deep under the world among the ruins and caves, and moves only while nobody looks at
 * it. Look away and it glides closer; look back and it's still, unless it's near enough to hide its face (its "shy"
 * pose). Caught moving, it spins round. Close enough and unwatched, it strikes.
 */
public class HauntedStand extends ActingMonster {
	static final double HEALTH = 30.0, ARMOR = 4.0, DAMAGE = 7.0, SPEED = 0.42;
	/** It comes only below this height. One in this many spawn checks passes, never another within {@link #ALONE_RANGE}. */
	static final int MAX_Y = 40, RARITY = 6;
	static final double ALONE_RANGE = 48.0;
	/** A player this near whose look is this close to it (the dot of their look and the way to it) watches it. */
	static final double WATCH_RANGE = 40.0, WATCH_DOT = 0.8;
	/** Watched this near, it hides its face. */
	static final double SHY_RANGE = 4.0;
	/** Its blow: reach and the ticks between blows. */
	static final double REACH = 1.6;
	static final int GAP = 20;
	/** Caught moving: its spin. */
	static final int SPIN_TICKS = 60;

	private static final String[] ACTIONS = {"check_spin", "check_shy"};

	private boolean watched;
	private int gap;

	public HauntedStand(EntityType<? extends HauntedStand> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 40.0);
	}

	/** Deep down, rare, alone. */
	public static boolean checkSpawn(EntityType<HauntedStand> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return pos.getY() < MAX_Y && (reason != EntitySpawnReason.NATURAL || random.nextInt(RARITY) == 0 && Beasts.alone(level, pos, HauntedStand.class, ALONE_RANGE))
			&& checkMonsterSpawnRules(type, level, reason, pos, random);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
	}

	/** Whether any player near looks at it and can see it. */
	private boolean watched(ServerLevel level) {
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(WATCH_RANGE), p -> !p.isSpectator() && p.isAlive())) {
			Vec3 to = this.getEyePosition().subtract(player.getEyePosition());
			if (to.length() < 0.5 || to.normalize().dot(player.getViewVector(1.0F)) > WATCH_DOT && player.hasLineOfSight(this)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		boolean now = this.watched(level);
		if (now && !this.watched && this.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
			// caught in the act
			this.act("check_spin", SPIN_TICKS);
			this.playSound(SoundEvents.ARMOR_STAND_HIT, 1.0F, 0.6F);
		}
		this.watched = now;
		LivingEntity target = this.getTarget();
		if (now) {
			this.getNavigation().stop();
			this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
			if (target != null && this.distanceTo(target) < SHY_RANGE && !this.acting()) {
				this.act("check_shy", 20);
			}
			return;
		}
		if (this.acting("check_shy")) {
			this.stopActing();
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		this.getLookControl().setLookAt(target, 360.0F, 90.0F);
		this.getNavigation().moveTo(target, 1.0);
		if (--this.gap <= 0 && this.distanceTo(target) < REACH + 0.5) {
			this.gap = GAP;
			this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), REACH, true, 0.4, 0.0);
			this.playSound(SoundEvents.ARMOR_STAND_HIT, 1.0F, 0.8F);
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return !this.watched && super.isPushable();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ARMOR_STAND_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ARMOR_STAND_BREAK;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// (it glides)
	}
}
