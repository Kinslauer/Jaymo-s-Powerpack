package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.DamageKinds;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.resources.Identifier;

/**
 * The troll, a bog brute. It heals fast ({@link #HEAL}, every {@link #HEAL_EVERY} ticks), cut limbs crawl back to it
 * ({@link TrollLimb}: the arm rejoins and heals it; one that is killed or burned is gone for good and the troll stays
 * the weaker), and a troll brought to nothing does not die: it falls and lies for {@link #LIE_TICKS}, then gets up
 * again. Only fire or acid stops this. A blow of either (the damage kinds, {@link DamageKinds}) stops its healing for
 * {@link #BURN_TICKS}, and a troll that is burning when it is brought to nothing stays dead.
 */
public class Troll extends Beast {
	static final double HEALTH = 60.0, SPEED = 0.26, DAMAGE = 7.0, KNOCKBACK_RESISTANCE = 0.5;
	/** It heals this much every this many ticks. */
	static final float HEAL = 1.5F;
	static final int HEAL_EVERY = 10;
	/** Fire or acid stops the healing this long (ticks), and a troll hit by either this recently dies for good. */
	static final int BURN_TICKS = 100;
	/** A fallen troll lies this long (ticks) and rises with this share of its health. */
	static final int LIE_TICKS = 20 * 12;
	static final float RISES_WITH = 0.3F;
	/** A cutting blow of at least this much has this chance to sever an arm, and a troll has at most this many to lose. */
	static final float SEVER_AT = 4.0F, SEVER_CHANCE = 0.3F;
	static final int ARMS = 2;
	/** A lost limb: the health it takes off for good, and what each limb away takes from its blows (share). */
	static final double LIMB_LOST = 4.0;
	static final double LIMB_AWAY = 0.2;
	private static final Identifier LOST_ID = Identifier.fromNamespaceAndPath("jaymoskingdoms", "troll_limbs_lost");
	private static final Identifier AWAY_ID = Identifier.fromNamespaceAndPath("jaymoskingdoms", "troll_limbs_away");
	/** Look: standing, or fallen. */
	public static final int STANDING = 0, FALLEN = 1;

	private long noHealUntil, burnedUntil, risesAt;
	private int away, lost;

	public Troll(EntityType<? extends Troll> type, Level level) {
		super(type, level);
		this.xpReward = 25;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.FOLLOW_RANGE, 24.0).add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true) {
			@Override
			public boolean canUse() {
				return !Troll.this.fallen() && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.7));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, (target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
	}

	public boolean fallen() {
		return this.look() == FALLEN;
	}

	/** Whether fire or acid has hurt it lately, so it will stay dead. */
	public boolean burned() {
		return this.level().getGameTime() < this.burnedUntil || this.isOnFire();
	}

	/** A limb of it has crawled back, healing it. */
	void limbBack(boolean wasLost) {
		if (wasLost) {
			this.lost++;
			this.limbCount(-1);
			this.refit();
		} else {
			this.heal(HEAL * 4.0F);
			this.limbCount(-1);
		}
	}

	private void limbCount(int by) {
		this.away = Math.max(0, this.away + by);
		this.refit();
	}

	private void refit() {
		var health = this.getAttribute(Attributes.MAX_HEALTH);
		var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
		if (health != null) {
			health.removeModifier(LOST_ID);
			if (this.lost > 0) {
				health.addPermanentModifier(new AttributeModifier(LOST_ID, -LIMB_LOST * this.lost, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		if (damage != null) {
			damage.removeModifier(AWAY_ID);
			if (this.away > 0) {
				damage.addTransientModifier(new AttributeModifier(AWAY_ID, -LIMB_AWAY * this.away, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
		if (this.getHealth() > this.getMaxHealth()) {
			this.setHealth(this.getMaxHealth());
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		DamageKinds.Kind kind = DamageKinds.of(this, source);
		if (kind == DamageKinds.Kind.FIRE || kind == DamageKinds.Kind.ACID) {
			long until = level.getGameTime() + BURN_TICKS;
			this.noHealUntil = Math.max(this.noHealUntil, until);
			this.burnedUntil = Math.max(this.burnedUntil, until);
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(0.6), this.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && kind == DamageKinds.Kind.SLASHING && damage >= SEVER_AT && this.away + this.lost < ARMS && !this.fallen()
			&& this.random.nextFloat() < SEVER_CHANCE) {
			this.sever(level);
		}
		return hurt;
	}

	/** An arm comes away and starts to crawl back. */
	private void sever(ServerLevel level) {
		TrollLimb limb = Creatures.TROLL_LIMB.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
		if (limb == null) {
			return;
		}
		limb.snapTo(this.getX(), this.getY() + 0.8, this.getZ(), this.random.nextFloat() * 360.0F, 0.0F);
		limb.belongsTo(this);
		level.addFreshEntity(limb);
		this.limbCount(1);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	/** One of its limbs was destroyed away from it. */
	void limbDestroyed() {
		this.limbCount(-1);
		this.lost++;
		this.refit();
	}

	/** Called as it would die: true if it dies, false if it falls instead. */
	public boolean mayDie(ServerLevel level) {
		if (this.burned()) {
			return true;
		}
		this.setHealth(1.0F);
		this.setLook(FALLEN);
		this.risesAt = level.getGameTime() + LIE_TICKS;
		this.setTarget(null);
		this.getNavigation().stop();
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_DEATH, SoundSource.HOSTILE, 1.0F, 0.6F);
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		long now = level.getGameTime();
		if (this.fallen()) {
			this.getNavigation().stop();
			this.setDeltaMovement(0.0, Math.min(0.0, this.getDeltaMovement().y), 0.0);
			if (this.burned()) {
				this.hurtServer(level, level.damageSources().onFire(), 1000.0F);
			} else if (now >= this.risesAt) {
				this.setLook(STANDING);
				this.setHealth(Math.max(1.0F, this.getMaxHealth() * RISES_WITH));
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 0.7F);
			}
			return;
		}
		if (this.tickCount % HEAL_EVERY == 0 && now >= this.noHealUntil && this.getHealth() < this.getMaxHealth()) {
			this.heal(HEAL);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY(0.6), this.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
		}
	}

	@Override
	public boolean isPushable() {
		return !this.fallen() && super.isPushable();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Lost", this.lost);
		output.putInt("Away", this.away);
		output.putInt("Pose", this.look());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.lost = input.getIntOr("Lost", 0);
		this.away = input.getIntOr("Away", 0);
		this.setLook(input.getIntOr("Pose", 0));
		this.risesAt = this.level().getGameTime() + 40;
		this.refit();
	}
}
