package com.jaymoskingdoms.beasts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * (0.7.329) The skulk golem, drawn as elma_taburlas' Skulk golem (Blockbench Workshop, CC BY 4.0): the deep dark's
 * guardian, a figure of deepslate overgrown with sculk. It stands as still as a statue (and is as hard to hurt) until
 * someone comes near without creeping or strikes it; then it wakes and hunts them, its blows bringing the darkness
 * down on them. With nobody about for a while it stands still again.
 */
public class SkulkGolem extends ActingMonster {
	static final double HEALTH = 90.0, ARMOR = 8.0, DAMAGE = 12.0, SPEED = 0.26;
	/** Someone this near (blocks) who isn't sneaking wakes it; sneaking, this near. */
	static final double WAKE_RANGE = 10.0, SNEAK_WAKE_RANGE = 3.0;
	/** Standing still, it takes only this share of a blow. */
	static final float STATUE_GUARD = 0.25F;
	/** Its blow: how long (ticks), when it lands, its reach; the darkness it brings (ticks). */
	static final int BLOW_TICKS = 30, BLOW_HIT = 15, DARKNESS_TICKS = 160;
	static final double BLOW_REACH = 2.2;
	/** With no foe this long (ticks) it goes still again. */
	static final int REST_TICKS = 400;
	/** Rare in the deep dark (one in this many spawn checks passes), and never another within {@link #ALONE_RANGE} blocks. */
	static final int RARITY = 20;
	static final double ALONE_RANGE = 48.0;

	private static final String[] ACTIONS = {"estatue", "attack"};

	private boolean statue = true;
	private int quiet;

	public SkulkGolem(EntityType<? extends SkulkGolem> type, Level level) {
		super(type, level);
		this.xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	/** The deep dark's: rare, and alone. */
	public static boolean checkSpawn(EntityType<SkulkGolem> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return (reason != EntitySpawnReason.NATURAL || random.nextInt(RARITY) == 0 && Beasts.alone(level, pos, SkulkGolem.class, ALONE_RANGE))
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
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL
				&& target.distanceTo(this) < (target.isShiftKeyDown() ? SNEAK_WAKE_RANGE : WAKE_RANGE) || !this.statue));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (this.statue) {
			this.getNavigation().stop();
			if (!this.acting("estatue")) {
				this.act("estatue", Integer.MAX_VALUE);
			}
			if (target != null) {
				this.wake(level);
			}
			return;
		}
		if (target == null || !target.isAlive()) {
			if (++this.quiet > REST_TICKS) {
				this.statue = true;
			}
			return;
		}
		this.quiet = 0;
		if (this.acting("attack")) {
			this.getNavigation().stop();
			this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (this.actionRun() == BLOW_HIT && this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), BLOW_REACH, true, 0.8, 0.1) > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS), this);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0F, 1.2F);
			}
			return;
		}
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (this.distanceTo(target) < BLOW_REACH + 0.8) {
			this.act("attack", BLOW_TICKS);
		} else {
			this.getNavigation().moveTo(target, 1.1);
		}
	}

	private void wake(ServerLevel level) {
		this.statue = false;
		this.quiet = 0;
		this.stopActing();
		level.sendParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 1.5, this.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.5F, 0.7F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean was = this.statue;
		boolean hurt = super.hurtServer(level, source, was ? amount * STATUE_GUARD : amount);
		if (was && source.getEntity() instanceof LivingEntity) {
			this.wake(level);
		}
		return hurt;
	}

	@Override
	public boolean isPushable() {
		return !this.statue && super.isPushable();
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.statue ? null : SoundEvents.SCULK_CATALYST_BLOOM;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SCULK_BLOCK_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SCULK_SHRIEKER_BREAK;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.SCULK_BLOCK_STEP, 1.0F, 0.7F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("statue", this.statue);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.statue = input.getBooleanOr("statue", true);
	}
}
