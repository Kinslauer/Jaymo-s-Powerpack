package com.jaymoskingdoms.nightfall;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Night Bleeder (our own, after Nightfall Plague's idea): an illager the old magic left undead and thirsty. Rare
 * in the dark, and in a raid's last waves. Every bite heals it and carries the plague ({@link Atrophy}); in a fight it
 * calls up vampire bats ({@link VampireBat}), vanishing for a moment as it does, and it closes the distance in leaps.
 * The sun burns it and it runs for shade, unless it's fighting. A wooden blade hurts it badly. Brought down by anything
 * but a critical hit, it doesn't die: it breaks into a Blightwing ({@link Blightwing}) and flies, and comes back in half
 * a minute. Seen through a spyglass, it isn't there.
 */
public class NightBleeder extends AbstractIllager {
	/** Health, bite, speed. */
	static final double HEALTH = 50.0, DAMAGE = 6.0, SPEED = 0.33;
	/** What a bite heals it. */
	static final float BITE_HEAL = 3.0F;
	/** The plague a bite gives (ticks). */
	static final int ATROPHY_TICKS = 20 * 30;
	/** Its bats: how many, how often at most (ticks), how long it's unseen as they come (ticks). */
	static final int BATS_MIN = 2, BATS_MAX = 3, BATS_EVERY = 20 * 12, VANISH_TICKS = 20;
	/** Its leap: from how far (blocks), landing about this far short, how often at most (ticks). */
	static final double DASH_FROM = 4.5, DASH_TO = 12.0, DASH_SHORT = 3.0;
	static final int DASH_EVERY = 50;
	/** How much worse a wooden blade hurts it (times). */
	static final float WOOD_FACTOR = 2.5F;
	/** How many looks it has (and so its Blightwing). */
	public static final int VARIANTS = 3;
	/** A wounded one keeps fighting rather than running from the sun this long after a blow (ticks). */
	static final int FIGHTING_TICKS = 100;

	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(NightBleeder.class, EntityDataSerializers.INT);

	private int batsReady, dashReady;

	public NightBleeder(EntityType<? extends NightBleeder> type, Level level) {
		super(type, level);
		this.xpReward = 15;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, HEALTH)
			.add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANT, 0);
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	void setVariant(int variant) {
		this.entityData.set(VARIANT, Math.floorMod(variant, VARIANTS));
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new ShadeGoal(this));
		this.goalSelector.addGoal(2, new RestrictSunGoal(this));
		this.goalSelector.addGoal(3, new AbstractIllager.RaiderOpenDoorGoal(this));
		this.goalSelector.addGoal(4, new Raider.HoldGroundAttackGoal(this, 10.0F));
		this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.2, false));
		this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> !Nightfall.kin(target)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true,
			(target, level) -> !Nightfall.kin(target)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.setVariant(level.getRandom().nextInt(VARIANTS));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		if (this.batsReady > 0) {
			this.batsReady--;
		} else if (this.hasLineOfSight(target) && this.distanceTo(target) < 16.0F) {
			this.callBats(level, target);
		}
		if (this.dashReady > 0) {
			this.dashReady--;
		} else if (this.onGround()) {
			double d = this.distanceTo(target);
			if (d > DASH_FROM && d < DASH_TO && this.hasLineOfSight(target)) {
				this.dash(level, target);
			}
		}
	}

	/** Two or three vampire bats out of its cloak, and it's gone from sight a moment. */
	private void callBats(ServerLevel level, LivingEntity target) {
		this.batsReady = BATS_EVERY + this.random.nextInt(BATS_EVERY / 2);
		int n = Mth.nextInt(this.random, BATS_MIN, BATS_MAX);
		for (int i = 0; i < n; i++) {
			VampireBat bat = Nightfall.VAMPIRE_BAT.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (bat == null) {
				continue;
			}
			bat.snapTo(this.getX() + this.random.nextGaussian() * 0.6, this.getY(0.7), this.getZ() + this.random.nextGaussian() * 0.6, this.random.nextFloat() * 360.0F, 0.0F);
			bat.summoned(target);
			level.addFreshEntity(bat);
		}
		this.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, VANISH_TICKS, 0, false, false));
		level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(0.6), this.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 1.0F, 0.7F);
	}

	/** A leap that lands it a few steps short of its prey. */
	private void dash(ServerLevel level, LivingEntity target) {
		this.dashReady = DASH_EVERY + this.random.nextInt(DASH_EVERY);
		Vec3 to = target.position().subtract(this.position());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		double reach = Math.max(0.0, flat - DASH_SHORT);
		Vec3 dir = new Vec3(to.x / flat, 0.0, to.z / flat);
		this.setDeltaMovement(dir.scale(0.25 + reach * 0.11).add(0.0, 0.42, 0.0));
		this.syncVelocity = true;
		level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.3), this.getZ(), 8, 0.2, 0.1, 0.2, 0.02);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.8F, 1.3F);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			this.heal(BITE_HEAL);
			Nightfall.bite(living, ATROPHY_TICKS);
			level.sendParticles(Nightfall.blood(), living.getX(), living.getY(0.7), living.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
		}
		return hit;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (Nightfall.wooden(source)) {
			damage *= WOOD_FACTOR;
		}
		return super.hurtServer(level, source, damage);
	}

	/** Whether the blow that brought it down was a critical hit (a falling strike, a fully drawn arrow): then it dies. */
	static boolean critical(DamageSource source) {
		if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			return arrow.isCritArrow();
		}
		return source.getEntity() instanceof Player p && source.getDirectEntity() == p && p.fallDistance > 0.0 && !p.onGround()
			&& !p.onClimbable() && !p.isInWater() && !p.isPassenger() && !p.hasEffect(MobEffects.BLINDNESS);
	}

	@Override
	public AbstractIllager.IllagerArmPose getArmPose() {
		if (this.isAggressive()) {
			return AbstractIllager.IllagerArmPose.ATTACKING;
		}
		return this.isCelebrating() ? AbstractIllager.IllagerArmPose.CELEBRATING : AbstractIllager.IllagerArmPose.CROSSED;
	}

	@Override
	public void applyRaidBuffs(ServerLevel level, int wave, boolean isCaptain) {
		// (Nightfall.raids gives the ones that come with a raid a little fire resistance on arrival)
	}

	@Override
	public SoundEvent getCelebrateSound() {
		return SoundEvents.EVOKER_CELEBRATE;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.VINDICATOR_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.VINDICATOR_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.EVOKER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.8F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Variant", this.variant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setVariant(input.getIntOr("Variant", 0));
	}

	/** Runs for shade when the sun catches it, unless it's in the middle of a fight. */
	static final class ShadeGoal extends FleeSunGoal {
		ShadeGoal(NightBleeder mob) {
			super(mob, 1.35);
		}

		@Override
		public boolean canUse() {
			if (this.mob.getTarget() != null && this.mob.tickCount - this.mob.getLastHurtByMobTimestamp() < FIGHTING_TICKS) {
				return false;
			}
			LivingEntity target = this.mob.getTarget();
			this.mob.setTarget(null);
			boolean flee = super.canUse();
			this.mob.setTarget(target);
			return flee;
		}
	}
}
