package com.jaymoskingdoms.beasts;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.WorldTiers;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The cyclops (Andy, 2026-11-14, after Mythika's; our own make: none of Mythika's code, art or sound): a one-eyed giant
 * shepherd of the peaks and the stony shores, over four blocks tall, with an olive-wood club. It keeps a hall cut into the
 * mountainside ({@link CyclopsCaves}): a bonfire under a smoke hole, a fold of sheep, a shelf of the bones of whatever it
 * ate. It roams near it by day and sleeps by its fire at night.
 * <p>
 * It fights with its club: a sweeping swing at whoever is close, and the slam, brought down on the ground before it so a
 * shockwave runs out through the earth ({@link #WAVE_REACH} blocks, a block a tick) and throws everyone it reaches off
 * their feet. Whoever is in the air when it passes is spared, so the slam can be jumped.
 * <p>
 * Its one eye is its weakness, as the old story has it. An arrow, bolt or thrown spear that strikes it in the eye, or any
 * blow while it sleeps, blinds it for {@link #BLIND_TICKS}: it clutches its face and swings its club at every footstep it
 * hears near it. Whoever creeps (sneaks) is not heard. And it counts its sheep: whoever leads one of its flock off on a
 * lead it goes for.
 * <p>
 * It comes down out of the peaks only where a player near has reached Borderlands ({@link #ROAM_TIER}); its hall is cut
 * where it first comes into the world ({@link CyclopsCaves}), and from then on it keeps to it.
 */
public class Cyclops extends ActingMonster implements GeoEntity {
	static final double HEALTH = 170.0, ARMOR = 4.0, DAMAGE = 15.0, SPEED = 0.25, KNOCKBACK_RESIST = 0.9, FOLLOW = 28.0;
	/** Its swing: how long (ticks), when it lands, its reach. */
	static final int SWING_TICKS = 20, SWING_HIT = 11;
	static final double SWING_REACH = 3.5;
	/** Its slam: how long, when the club lands, how often, and from how close it slams. */
	static final int SLAM_TICKS = 40, SLAM_HIT = 22, SLAM_COOLDOWN = 160;
	static final double SLAM_FROM = 6.0;
	/** The shockwave: how far it runs (blocks, one a tick), what it does at its heart and at its edge, how hard it throws. */
	static final double WAVE_REACH = 8.0, WAVE_KNOCK = 1.6, WAVE_UP = 0.65;
	static final float WAVE_DAMAGE = 18.0F, WAVE_EDGE_DAMAGE = 6.0F;
	/** Blinded: for how long (ticks), how near a footstep it hears (blocks), the share of eye shots that blind it. */
	static final int BLIND_TICKS = 240;
	static final double HEAR_RANGE = 10.0, EYE_SLACK = 0.55;
	static final float EYE_SHOT_CHANCE = 0.6F;
	/** How far from its fire it roams by day, and how near it lies down at night (blocks). */
	static final int HOME_RANGE = 24;
	static final double SLEEP_NEAR = 4.0;
	/** It wakes to a footstep this near (blocks) unless the walker sneaks; how often it counts its sheep (ticks). */
	static final double WAKE_RANGE = 7.0;
	static final int FLOCK_CHECK = 40;
	static final double FLOCK_RANGE = 48.0;
	/** The world tier a player near it needs before a cyclops comes out. */
	static final WorldTiers.Tier ROAM_TIER = WorldTiers.Tier.BORDERLANDS;
	static final double ROAM_TIER_RANGE = 128.0;
	static final int GAP = 14, ANIMATION_BLEND = 5;
	/** On its sheep: whose flock they are. */
	public static final String FLOCK_TAG = "jaymoskingdoms.cyclops_flock";

	private static final String[] ACTIONS = {"swing", "slam", "roar"};
	private static final EntityDataAccessor<Boolean> BLIND = SynchedEntityData.defineId(Cyclops.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> ASLEEP = SynchedEntityData.defineId(Cyclops.class, EntityDataSerializers.BOOLEAN);
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
	private static final RawAnimation BLINDED = RawAnimation.begin().thenLoop("blinded");
	private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleep");
	private static final RawAnimation SWING = RawAnimation.begin().thenPlay("swing");
	private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("slam");
	private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("roar");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int gap, slamCooldown = SLAM_COOLDOWN / 2, blindLeft;
	/** The running shockwave: where it began, how far it has run (-1: none), and who it has thrown. */
	private Vec3 waveAt = Vec3.ZERO;
	private int wave = -1;
	private final Set<Integer> waveHit = new HashSet<>();
	/** Its hall's fire, if it has one. */
	private @Nullable BlockPos home;

	public Cyclops(EntityType<? extends Cyclops> type, Level level) {
		super(type, level);
		this.xpReward = 45;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESIST).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.STEP_HEIGHT, 1.5);
	}

	/** Whether a roaming cyclops may come out at {@code pos}: a player within {@link #ROAM_TIER_RANGE} has reached {@link #ROAM_TIER}. */
	public static boolean roamTierOpen(ServerLevel level, BlockPos pos) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(Vec3.atCenterOf(pos)) < ROAM_TIER_RANGE * ROAM_TIER_RANGE && WorldTiers.of(player).ordinal() >= ROAM_TIER.ordinal()) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(BLIND, false);
		entityData.define(ASLEEP, false);
	}

	@Override
	protected String[] actions() {
		return ACTIONS;
	}

	@Override
	protected void act(String anim, int ticks) {
		super.act(anim, ticks);
		if (!this.level().isClientSide()) {
			this.triggerAnim("action", anim);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Cyclops>("move", ANIMATION_BLEND, test -> {
			Cyclops cyclops = test.animatable();
			if (cyclops.entityData.get(ASLEEP)) {
				return test.setAndContinue(SLEEP);
			}
			if (cyclops.entityData.get(BLIND)) {
				return test.setAndContinue(BLINDED);
			}
			if (test.isMoving()) {
				return test.setAndContinue(cyclops.isAggressive() ? RUN : WALK);
			}
			return test.setAndContinue(IDLE);
		}));
		controllers.add(new AnimationController<Cyclops>("action", ANIMATION_BLEND, test -> PlayState.STOP)
			.triggerableAnim("swing", SWING).triggerableAnim("slam", SLAM).triggerableAnim("roar", ROAR));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6) {
			@Override
			public boolean canUse() {
				return !Cyclops.this.blind() && !Cyclops.this.asleep() && super.canUse();
			}
		});
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && !this.blind() && !this.asleep()));
	}

	public boolean blind() {
		return this.entityData.get(BLIND);
	}

	public boolean asleep() {
		return this.entityData.get(ASLEEP);
	}

	/** Its hall's fire, if it has one. */
	public @Nullable BlockPos home() {
		return this.home;
	}

	/** Its hall is made: it keeps to it from now on. */
	public void setHome(BlockPos fire) {
		this.home = fire.immutable();
		this.setHomeTo(this.home, HOME_RANGE);
		this.setPersistenceRequired();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.slamCooldown--;
		if (this.wave >= 0) {
			this.waveStep(level);
		}
		if (this.blindLeft > 0 && --this.blindLeft == 0) {
			this.entityData.set(BLIND, false);
			this.act("roar", 30);
			this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.6F);
		}
		if (this.tickCount % FLOCK_CHECK == 0) {
			this.countSheep(level);
		}
		this.sleepOrWake(level);
		if (this.asleep()) {
			this.getNavigation().stop();
			return;
		}
		LivingEntity target = this.blind() ? this.heard(level) : this.getTarget();
		if (this.acting()) {
			this.getNavigation().stop();
			if (target != null) {
				this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
			if (this.acting("swing") && this.actionRun() == SWING_HIT) {
				this.strike(level, (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE), SWING_REACH, true, 1.5, 0.2);
				this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.5F);
			} else if (this.acting("slam") && this.actionRun() == SLAM_HIT) {
				this.slam(level);
			}
			return;
		}
		if (target == null || !target.isAlive()) {
			if (this.blind()) {
				this.getNavigation().stop();
			}
			return;
		}
		double d = this.distanceTo(target);
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (--this.gap > 0) {
			if (!this.blind()) {
				this.getNavigation().moveTo(target, 1.15);
			}
			return;
		}
		if (d < SLAM_FROM && this.slamCooldown <= 0 && this.onGround()) {
			this.slamCooldown = SLAM_COOLDOWN;
			this.act("slam", SLAM_TICKS);
		} else if (d < SWING_REACH + 1.0) {
			this.act("swing", SWING_TICKS);
		} else {
			if (!this.blind()) {
				this.getNavigation().moveTo(target, 1.15);
			}
			return;
		}
		this.gap = GAP;
	}

	/** Blind, it goes by its ears: the nearest who walks, runs or fights near it without creeping. */
	private @Nullable LivingEntity heard(ServerLevel level) {
		LivingEntity best = null;
		double bestD = HEAR_RANGE * HEAR_RANGE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(HEAR_RANGE),
			e -> e != this && e.isAlive() && !(e instanceof Sheep) && !e.isShiftKeyDown() && !e.isSilent()
				&& !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			double d = e.distanceToSqr(this);
			if (moved(e) && d < bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/** Whether {@code e} went anywhere this tick (a player's own movement isn't in its motion on the server). */
	private static boolean moved(LivingEntity e) {
		double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
		return dx * dx + dz * dz > 0.0025;
	}

	/** At night, home and with nobody to fight, it lies down by its fire; a blow or a footstep near wakes it. */
	private void sleepOrWake(ServerLevel level) {
		boolean asleep = this.asleep();
		if (asleep) {
			if (level.isBrightOutside() || this.getTarget() != null || this.blind()) {
				this.entityData.set(ASLEEP, false);
				return;
			}
			for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(WAKE_RANGE),
				p -> !p.isShiftKeyDown() && !p.isCreative() && !p.isSpectator() && moved(p))) {
				this.entityData.set(ASLEEP, false);
				this.setTarget(p);
				this.act("roar", 30);
				this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.7F);
				return;
			}
			return;
		}
		if (this.home == null || level.isBrightOutside() || this.getTarget() != null || this.blind() || this.acting()) {
			return;
		}
		if (this.distanceToSqr(Vec3.atCenterOf(this.home)) > SLEEP_NEAR * SLEEP_NEAR) {
			if (this.tickCount % 20 == 0) {
				this.getNavigation().moveTo(this.home.getX() + 2.5, this.home.getY(), this.home.getZ() + 0.5, 0.8);
			}
		} else {
			this.getNavigation().stop();
			this.entityData.set(ASLEEP, true);
		}
	}

	/** Its sheep: whoever leads one off on a lead it goes for. */
	private void countSheep(ServerLevel level) {
		if (this.blind() || this.getTarget() != null) {
			return;
		}
		String mine = FLOCK_TAG + ":" + this.getUUID();
		for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, this.getBoundingBox().inflate(FLOCK_RANGE), s -> s.entityTags().contains(mine))) {
			if (sheep.getLeashHolder() instanceof Player thief && !thief.isCreative() && !thief.isSpectator()) {
				this.entityData.set(ASLEEP, false);
				this.setTarget(thief);
				this.act("roar", 30);
				this.playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.7F);
				return;
			}
		}
	}

	/** The club comes down: the ground breaks before it, and the wave starts out from there. */
	private void slam(ServerLevel level) {
		Vec3 front = Vec3.directionFromRotation(0.0F, this.getYRot()).scale(2.0);
		this.waveAt = this.position().add(front);
		this.wave = 0;
		this.waveHit.clear();
		BlockState ground = level.getBlockState(BlockPos.containing(this.waveAt).below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), this.waveAt.x, this.waveAt.y + 0.1, this.waveAt.z, 50, 0.8, 0.2, 0.8, 0.2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, this.waveAt.x, this.waveAt.y + 0.2, this.waveAt.z, 2, 0.4, 0.1, 0.4, 0.0);
		level.playSound(null, this.waveAt.x, this.waveAt.y, this.waveAt.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.5F, 0.5F);
	}

	/** One tick of the shockwave: the ring a block further out, dust kicked up along it, and whoever stands on it thrown. */
	private void waveStep(ServerLevel level) {
		double r = ++this.wave;
		if (r > WAVE_REACH) {
			this.wave = -1;
			return;
		}
		int points = (int) (6 + r * 5);
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2.0 * i / points;
			double x = this.waveAt.x + Math.cos(a) * r, z = this.waveAt.z + Math.sin(a) * r;
			BlockPos below = BlockPos.containing(x, this.waveAt.y - 0.5, z);
			BlockState ground = level.getBlockState(below);
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, below.getY() + 1.05, z, 2, 0.15, 0.05, 0.15, 0.15);
			}
		}
		if (this.wave % 2 == 0) {
			level.playSound(null, this.waveAt.x, this.waveAt.y, this.waveAt.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
		}
		float damage = (float) (WAVE_DAMAGE + (WAVE_EDGE_DAMAGE - WAVE_DAMAGE) * (r / WAVE_REACH));
		AABB box = new AABB(this.waveAt, this.waveAt).inflate(r + 1.0, 2.0, r + 1.0);
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !this.waveHit.contains(e.getId())
			&& !(e instanceof Sheep && e.entityTags().contains(FLOCK_TAG + ":" + this.getUUID())));
		for (LivingEntity e : near) {
			double d = Math.sqrt(e.distanceToSqr(this.waveAt.x, e.getY(), this.waveAt.z));
			if (d > r + 0.5 || d < r - 1.5 || Math.abs(e.getY() - this.waveAt.y) > 1.5) {
				continue;
			}
			this.waveHit.add(e.getId());
			// (a wave through the earth: whoever is off the ground when it passes is spared)
			if (!e.onGround() || e instanceof Player p && (p.isCreative() || p.isSpectator())) {
				continue;
			}
			DamageSource source = this.damageSources().mobAttack(this);
			if (e.hurtServer(level, source, damage)) {
				Vec3 away = e.position().subtract(this.waveAt).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, this.getYRot()) : away.normalize();
				e.setDeltaMovement(e.getDeltaMovement().add(away.x * WAVE_KNOCK, WAVE_UP, away.z * WAVE_KNOCK));
				e.needsSync = true;
			}
		}
	}

	@Override
	protected boolean strikes(LivingEntity e) {
		// blind, it swings at whatever it hears; awake, at its foes
		return this.blind() ? !(e instanceof Sheep) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())) : super.strikes(e);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean wasAsleep = this.asleep();
		boolean eye = !this.blind() && (wasAsleep && source.getEntity() instanceof LivingEntity
			|| source.getDirectEntity() instanceof Projectile shot && Math.abs(shot.getY() - this.getEyeY()) < EYE_SLACK
				&& this.getRandom().nextFloat() < EYE_SHOT_CHANCE);
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive()) {
			this.entityData.set(ASLEEP, false);
			if (eye) {
				this.blinded();
			}
		}
		return hurt;
	}

	/** Struck in the eye: it drops whoever it was after and fights by ear. */
	public void blinded() {
		this.blindLeft = BLIND_TICKS;
		this.entityData.set(BLIND, true);
		this.setTarget(null);
		this.getNavigation().stop();
		this.playSound(SoundEvents.RAVAGER_STUNNED, 2.5F, 0.5F);
		this.playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.9F);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// blind, it sees nobody to go after (it swings by ear instead)
		super.setTarget(this.blind() ? null : target);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// (its blows are its own moves)
		return false;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		data = super.finalizeSpawn(level, difficulty, reason, data);
		CyclopsCaves.cyclops(level, this, reason);
		return data;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.home != null) {
			output.putLong("home", this.home.asLong());
		}
		output.putInt("blind", this.blindLeft);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		long home = input.getLongOr("home", Long.MIN_VALUE);
		if (home != Long.MIN_VALUE) {
			this.setHome(BlockPos.of(home));
		}
		this.blindLeft = input.getIntOr("blind", 0);
		this.entityData.set(BLIND, this.blindLeft > 0);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return this.home == null && super.removeWhenFarAway(distance);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.asleep() ? SoundEvents.FOX_SLEEP : SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.7F;
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
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.RAVAGER_STEP, 1.0F, 0.7F);
	}
}
