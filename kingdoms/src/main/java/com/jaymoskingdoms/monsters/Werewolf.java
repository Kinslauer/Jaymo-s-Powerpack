package com.jaymoskingdoms.monsters;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.56) The werewolf (after Mo' Creatures'): by day a plain-looking wanderer in a smock who runs if you hurt them; when
 * night falls they writhe, smoke and become the beast, which hunts players and villagers. The beast shrugs off most
 * blows: only gold or silver bites deep (a weapon with either in its name). At dawn it turns back. Four faces by day,
 * four coats by night (the fiery one rare, and it burns what it bites).
 */
public class Werewolf extends LegendMonster {
	static final double HEALTH = 40.0, HUMAN_SPEED = 0.25, WOLF_SPEED = 0.36, HUMAN_DAMAGE = 2.0, WOLF_DAMAGE = 7.0;
	/** What a blow from anything but gold or silver does to the beast, and what silver does. */
	static final float HIDE = 0.25F, SILVER = 2.0F;
	/** How long the change takes (ticks). */
	static final int CHANGE_TICKS = 60;
	/** One coat in this many is the fiery one. */
	static final int FIRE_ODDS = 10;
	public static final int FACES = 4, COATS = 4, FIRE_COAT = 3;
	/** How long the fiery one's bite burns (seconds). */
	static final float FIRE_SECONDS = 4.0F;

	private static final EntityDataAccessor<Boolean> HUMAN = SynchedEntityData.defineId(Werewolf.class, EntityDataSerializers.BOOLEAN);

	private int changing;

	public Werewolf(EntityType<? extends Werewolf> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, HUMAN_SPEED)
			.add(Attributes.ATTACK_DAMAGE, HUMAN_DAMAGE).add(Attributes.FOLLOW_RANGE, 32.0);
	}

	@Override
	protected int variants() {
		return FACES * COATS;
	}

	@Override
	protected int pickVariant(ServerLevelAccessor level) {
		int coat = this.random.nextInt(FIRE_ODDS) == 0 ? FIRE_COAT : this.random.nextInt(FIRE_COAT);
		return this.random.nextInt(FACES) * COATS + coat;
	}

	/** Its face by day (weredude, werehuman, wereoldie, werewoman). */
	public int face() {
		return this.variant() / COATS;
	}

	/** Its coat by night (black, brown, timber, fire). */
	public int coat() {
		return this.variant() % COATS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4) {
			@Override
			public boolean canUse() {
				return Werewolf.this.isHuman() && super.canUse();
			}
		});
		this.goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45F));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				return !Werewolf.this.isHuman() && super.canUse();
			}
		});
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> !this.isHuman() && level.getDifficulty() != Difficulty.PEACEFUL));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true, (target, level) -> !this.isHuman()));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(HUMAN, true);
	}

	public boolean isHuman() {
		return this.entityData.get(HUMAN);
	}

	/** Mid-change (it shakes and smokes). */
	public boolean changing() {
		return this.changing > 0;
	}

	private void setHuman(boolean human) {
		this.entityData.set(HUMAN, human);
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(human ? HUMAN_SPEED : WOLF_SPEED);
		this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(human ? HUMAN_DAMAGE : WOLF_DAMAGE);
		if (human) {
			this.setTarget(null);
			this.setAggressive(false);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		boolean night = level.isDarkOutside();
		if (this.changing > 0) {
			this.getNavigation().stop();
			if (this.random.nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 3, 0.3, 0.5, 0.3, 0.02);
			}
			if (--this.changing == 0) {
				this.setHuman(!night);
				this.playSound(night ? sounds(SoundSet.ANGRY).growlSound().value() : SoundEvents.VILLAGER_HURT, 1.5F, night ? 0.6F : 1.0F);
			}
		} else if (night == this.isHuman()) {
			this.changing = CHANGE_TICKS;
			this.playSound(night ? sounds(SoundSet.BIG).whineSound().value() : sounds(SoundSet.BIG).pantSound().value(), 1.5F, 0.6F);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (!this.isHuman() && source.getEntity() instanceof LivingEntity attacker) {
			String weapon = source.getDirectEntity() == attacker ? BuiltInRegistries.ITEM.getKey(attacker.getMainHandItem().getItem()).getPath() : "";
			damage *= weapon.contains("silver") ? SILVER : weapon.contains("gold") ? 1.0F : HIDE;
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && this.coat() == FIRE_COAT) {
			target.igniteForSeconds(FIRE_SECONDS);
		}
		return hit;
	}

	@Override
	public boolean fireImmune() {
		return this.coat() == FIRE_COAT && !this.isHuman() || super.fireImmune();
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return !this.isHuman() && super.removeWhenFarAway(distSqr);
	}

	static WolfSoundVariant.WolfSoundSet sounds(SoundSet set) {
		return SoundEvents.WOLF_SOUNDS.get(set).adultSounds();
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		if (this.isHuman()) {
			return null;
		}
		return this.getTarget() != null ? sounds(SoundSet.ANGRY).growlSound().value() : sounds(SoundSet.BIG).ambientSound().value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.isHuman() ? SoundEvents.VILLAGER_HURT : sounds(SoundSet.BIG).hurtSound().value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.isHuman() ? SoundEvents.VILLAGER_DEATH : sounds(SoundSet.BIG).deathSound().value();
	}

	@Override
	public float getVoicePitch() {
		return this.isHuman() ? super.getVoicePitch() : 0.6F;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("human", this.isHuman());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setHuman(input.getBooleanOr("human", true));
	}
}
