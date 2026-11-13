package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.creatures.Beast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Snow Widow: a tall woman in white who walks the snowed-over cherry land on winter nights. A great foe (her own
 * bar, and a posture to break, {@code BossPosture}). The cold comes with her: whoever is within {@link #COLD_RANGE}
 * blocks freezes as if in powder snow, and nearer they slow. Her touch freezes too. Fire is what she cannot stand
 * ({@link #FIRE_HARM} times the harm). She is gone with the first light.
 */
public class SnowWidow extends Beast {
	static final double HEALTH = 120.0, SPEED = 0.27, DAMAGE = 7.0, ARMOUR = 4.0;
	static final double COLD_RANGE = 7.0, SLOW_RANGE = 4.0;
	static final int COLD_EVERY = 10, COLD = 30, TOUCH_COLD = 120;
	static final float FIRE_HARM = 2.0F;

	private final ServerBossEvent bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);

	public SnowWidow(EntityType<? extends SnowWidow> type, Level level) {
		super(type, level);
		this.xpReward = 60;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.ARMOR, ARMOUR).add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % COLD_EVERY == 0) {
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(COLD_RANGE), e -> e != this && e.isAlive() && e.canFreeze())) {
				near.setTicksFrozen(Math.min(near.getTicksRequiredToFreeze() + 60, near.getTicksFrozen() + COLD));
				if (near.distanceToSqr(this) <= SLOW_RANGE * SLOW_RANGE) {
					near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0), this);
				}
			}
			level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 1.0, this.getZ(), 6, 2.0, 1.0, 2.0, 0.01);
		}
		if (this.tickCount % 5 == 0) {
			this.bar.setProgress(this.getHealth() / this.getMaxHealth());
			BossBars.show(level, this, this.bar);
		}
		if (level.isBrightOutside() && this.tickCount % 20 == 0 && level.canSeeSky(this.blockPosition())) {
			// morning: she is not there any more
			level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 1.0, this.getZ(), 60, 0.4, 1.0, 0.4, 0.05);
			this.discard();
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living && living.canFreeze()) {
			living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze() + 100, living.getTicksFrozen() + TOUCH_COLD));
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return super.hurtServer(level, source, source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) ? amount * FIRE_HARM : amount);
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.POWDER_SNOW_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GLASS_BREAK;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GLASS_BREAK;
	}
}
