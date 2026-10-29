package com.jaymoskingdoms.beasts;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.329) The redstone golem, drawn as polyjo's Redstone Golem (Minecraft Dungeons) (Blockbench Workshop, CC BY 4.0):
 * a great foe of the deep mines, a hulk of stone and live redstone that walks the caves below the world's zero. It
 * pounds the ground with both fists, and sets the rock under its foes alight with redstone that bursts a moment later.
 * Its core glows in the dark. It shrugs off knockback and falls, and leaves redstone and, now and then, a diamond.
 */
public class RedstoneGolem extends ActingMonster {
	static final double HEALTH = 220.0, ARMOR = 14.0, DAMAGE = 18.0, SPEED = 0.22;
	/** It only comes below this height, and in the dark. */
	static final int MAX_Y = 0;
	/** And rare: one in this many spawn checks passes, never another within {@link #ALONE_RANGE} blocks. */
	static final int RARITY = 6;
	static final double ALONE_RANGE = 64.0;
	/** Its pound: how long (ticks), when it lands, how far it reaches. */
	static final int POUND_TICKS = 30, POUND_HIT = 18;
	static final double POUND_REACH = 4.0;
	/** Its eruption: how long, when the marked spots burst, how many, how far it reaches for them, their damage and how often. */
	static final int ERUPT_TICKS = 40, ERUPT_HIT = 28, ERUPT_SPOTS = 4, ERUPT_COOLDOWN = 160;
	static final double ERUPT_RANGE = 14.0, ERUPT_RADIUS = 1.8;
	static final float ERUPT_DAMAGE = 10.0F;
	static final int GAP = 20;
	static final DustParticleOptions REDSTONE = new DustParticleOptions(0xFF2010, 1.6F);

	private static final String[] ACTIONS = {"pound", "erupt"};

	private int gap, eruptCooldown = ERUPT_COOLDOWN / 2;
	private final List<Vec3> spots = new ArrayList<>();

	public RedstoneGolem(EntityType<? extends RedstoneGolem> type, Level level) {
		super(type, level);
		this.xpReward = 60;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.STEP_HEIGHT, 1.0);
	}

	/** Below the world's zero and in the dark. */
	public static boolean checkSpawn(EntityType<RedstoneGolem> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return pos.getY() < MAX_Y && (reason != EntitySpawnReason.NATURAL || random.nextInt(RARITY) == 0 && Beasts.alone(level, pos, RedstoneGolem.class, ALONE_RANGE))
			&& checkMonsterSpawnRules(type, level, reason, pos, random);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.eruptCooldown--;
		if (this.tickCount % 10 == 0) {
			level.sendParticles(REDSTONE, this.getX(), this.getY() + 1.8, this.getZ(), 2, 0.5, 0.4, 0.5, 0.0);
		}
		LivingEntity target = this.getTarget();
		if (this.acting()) {
			this.getNavigation().stop();
			if (target != null) {
				this.getLookControl().setLookAt(target, 20.0F, 20.0F);
			}
			if (this.acting("pound") && this.actionRun() == POUND_HIT) {
				this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), POUND_REACH, true, 1.0, 0.5);
				level.sendParticles(REDSTONE, this.getX(), this.getY() + 0.2, this.getZ(), 40, 2.0, 0.2, 2.0, 0.0);
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0F, 0.5F);
			} else if (this.acting("erupt")) {
				this.erupt(level);
			}
			return;
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		double d = this.distanceTo(target);
		this.getLookControl().setLookAt(target, 20.0F, 20.0F);
		if (--this.gap > 0) {
			this.getNavigation().moveTo(target, 1.0);
			return;
		}
		if (d < ERUPT_RANGE && d > 4.0 && this.eruptCooldown <= 0) {
			this.eruptCooldown = ERUPT_COOLDOWN;
			this.mark(level, target);
			this.act("erupt", ERUPT_TICKS);
		} else if (d < POUND_REACH) {
			this.act("pound", POUND_TICKS);
		} else {
			this.getNavigation().moveTo(target, 1.0);
			return;
		}
		this.gap = GAP;
	}

	/** Marks where the rock will burst: under its foe, and about them. */
	private void mark(ServerLevel level, LivingEntity target) {
		this.spots.clear();
		this.spots.add(target.position());
		for (int i = 1; i < ERUPT_SPOTS; i++) {
			this.spots.add(target.position().add(this.random.nextGaussian() * 2.5, 0.0, this.random.nextGaussian() * 2.5));
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 0.5F);
	}

	private void erupt(ServerLevel level) {
		int run = this.actionRun();
		for (Vec3 spot : this.spots) {
			if (run < ERUPT_HIT && run % 4 == 0) {
				level.sendParticles(REDSTONE, spot.x, spot.y + 0.1, spot.z, 8, ERUPT_RADIUS * 0.4, 0.05, ERUPT_RADIUS * 0.4, 0.0);
			} else if (run == ERUPT_HIT) {
				level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 0.5, spot.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(REDSTONE, spot.x, spot.y + 0.5, spot.z, 30, 0.6, 1.0, 0.6, 0.0);
				level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.2F);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(ERUPT_RADIUS, 2.0,
					ERUPT_RADIUS), e -> e != this && this.strikes(e))) {
					DamageSource source = this.damageSources().indirectMagic(this, this);
					if (e.hurtServer(level, source, ERUPT_DAMAGE)) {
						e.setDeltaMovement(e.getDeltaMovement().add(0.0, 0.7, 0.0));
						e.needsSync = true;
					}
				}
			}
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity target) {
		return false;
	}

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.IRON_GOLEM_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.IRON_GOLEM_STEP, 1.2F, 0.6F);
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}
}
