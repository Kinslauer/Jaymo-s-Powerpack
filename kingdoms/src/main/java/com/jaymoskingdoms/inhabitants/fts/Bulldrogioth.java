package com.jaymoskingdoms.inhabitants.fts;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Bulldrogioth: a crab-like beast under a crimson shell, out of the demons' country, come up into the Nether's crimson,
 * warped and basalt country (its shell takes the colour of where it was born). It slams, claws, combos with its great
 * claws, bites, breaks the rock about it and, hurt, throws thorns off its shell. Hostile to everything alive in the Nether.
 * Ported from From the Shadows Reborn 3.0 (Sonmok14, L_Ender; AGPL v3): its model, textures, animations and sounds are theirs
 * (licenses/from-the-shadows/CREDITS-nether-beasts.md); the logic and numbers are theirs, the code is ours (one hitbox in
 * place of its multipart one). Like the {@link Nehemoth}, it is a synced {@link Act} with an age in ticks.
 */
public class Bulldrogioth extends Monster implements GeoEntity, NetherBeast {
	static final double HEALTH = 100.0, SPEED = 0.3, DAMAGE = 15.0, ARMOR = 15.0, TOUGHNESS = 10.0, FOLLOW = 32.0, ATTACK_KNOCKBACK = 2.0;
	/** Rare in the wild: its weight, the chance a spawn goes ahead, and the blocks it keeps from its own kind. */
	public static final int SPAWN_WEIGHT = 2;
	static final int SPAWN_ONE_IN = 3;
	static final double SPAWN_APART = 96.0;
	/** Ticks until it may combo, bite, or throw thorns again, and the breath between acts. */
	static final int COMBO_COOLDOWN = 200, BITE_COOLDOWN = 150, THORN_COOLDOWN = 400, THORN_COMBO_COOLDOWN = 600, GAP = 8;
	/** How far (blocks) and across what arc (cosine of the half-angle, 40 degrees) a blow reaches; a bite and a combo's choice distances. */
	static final double SLAM_REACH = 3.0, CLAW_REACH = 3.5, ARC = 0.766, MELEE_REACH = 3.2, COMBO_REACH = 4.5, BITE_REACH = 6.0;
	/** A shield it breaks stays down this many ticks. */
	static final int SHIELD_DOWN = 100;
	/** What it breaks about it: a block of this much hardness or less (the original broke anything it could). */
	static final float BREAKS_UP_TO = 5.0F;
	/** Thorns: speed (blocks a tick), spread (inaccuracy at normal difficulty is 16 minus 4 a level), how far apart a fan's thorns are (degrees). */
	static final float THORN_SPEED = 1.5F;
	static final double THORN_FAN = 20.0, THORN_COMBO_FAN = 15.0;

	/** What it is doing, and for how many ticks it takes. */
	public enum Act {
		NONE(0), SLAM(55), CLAW(46), COMBO(70), BITE(56), THORN(60), THORN_COMBO(95);

		final int ticks;

		Act(int ticks) {
			this.ticks = ticks;
		}
	}

	static final String P = "animation.bulldrogioth.";
	static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
	static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
	static final RawAnimation SWIM = RawAnimation.begin().thenLoop(P + "swim");
	static final RawAnimation DEATH = RawAnimation.begin().thenLoop(P + "death");
	static final RawAnimation HURT = RawAnimation.begin().thenLoop(P + "hurt");
	static final RawAnimation SLAM_ANIM = RawAnimation.begin().thenPlayAndHold(P + "normal");
	static final RawAnimation CLAW_LEFT = RawAnimation.begin().thenPlayAndHold(P + "claw_left");
	static final RawAnimation CLAW_RIGHT = RawAnimation.begin().thenPlayAndHold(P + "claw_right");
	static final RawAnimation COMBO_LEFT = RawAnimation.begin().thenPlayAndHold(P + "combo_left");
	static final RawAnimation COMBO_RIGHT = RawAnimation.begin().thenPlayAndHold(P + "combo_right");
	static final RawAnimation BITE_LEFT = RawAnimation.begin().thenPlayAndHold(P + "bite_left");
	static final RawAnimation BITE_RIGHT = RawAnimation.begin().thenPlayAndHold(P + "bite_right");
	static final RawAnimation THORN_LEFT = RawAnimation.begin().thenPlayAndHold(P + "thorn_left");
	static final RawAnimation THORN_RIGHT = RawAnimation.begin().thenPlayAndHold(P + "thorn_right");
	static final RawAnimation THORN_COMBO_LEFT = RawAnimation.begin().thenPlayAndHold(P + "thorn_combo_left");
	static final RawAnimation THORN_COMBO_RIGHT = RawAnimation.begin().thenPlayAndHold(P + "thorn_combo_right");

	private static final EntityDataAccessor<Integer> ACT = SynchedEntityData.defineId(Bulldrogioth.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RIGHT = SynchedEntityData.defineId(Bulldrogioth.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Bulldrogioth.class, EntityDataSerializers.INT);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int actAge;
	private int comboCooldown, biteCooldown, thornCooldown, thornComboCooldown, gap;

	public Bulldrogioth(EntityType<? extends Bulldrogioth> type, Level level) {
		super(type, level);
		this.xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, DAMAGE)
			.add(Attributes.ARMOR, ARMOR).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	/** Its natural spawn: rare, in the Nether, and none of its kind within a hundred blocks. */
	public static boolean checkSpawn(EntityType<Bulldrogioth> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		if (!(level instanceof ServerLevel server) || server.dimension() != Level.NETHER || random.nextInt(SPAWN_ONE_IN) != 0
			|| !Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) {
			return false;
		}
		return server.getEntitiesOfClass(Bulldrogioth.class, new AABB(pos).inflate(SPAWN_APART), Entity::isAlive).isEmpty();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ACT, 0);
		entityData.define(RIGHT, false);
		entityData.define(VARIANT, 0);
	}

	public Act act() {
		Act[] acts = Act.values();
		return acts[Mth.clamp(this.entityData.get(ACT), 0, acts.length - 1)];
	}

	public int actAge() {
		return this.actAge;
	}

	public boolean isRight() {
		return this.entityData.get(RIGHT);
	}

	/** 0: crimson (the crimson forests and wastes), 1: warped (the warped forests), 2: basalt (the basalt deltas). */
	public int variant() {
		return this.entityData.get(VARIANT);
	}

	private void setAct(Act act) {
		this.entityData.set(ACT, act.ordinal());
		this.actAge = 0;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (ACT.equals(accessor)) {
			this.actAge = 0;
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		var biome = level.getBiome(this.blockPosition());
		this.entityData.set(VARIANT, biome.is(Biomes.WARPED_FOREST) ? 1 : biome.is(Biomes.BASALT_DELTAS) ? 2 : 0);
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new FightGoal());
		this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.7, 0.02F));
		this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, NetherBeast.class).setAlertOthers());
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
			(target, level) -> level.getDifficulty() != Difficulty.PEACEFUL && NetherBeasts.fair(target)));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, NetherBeasts::prey));
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return NetherBeasts.fair(target) && super.canAttack(target);
	}

	// ---- the act ---------------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (this.act() != Act.NONE) {
			this.actAge++;
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.comboCooldown > 0) {
			this.comboCooldown--;
		}
		if (this.biteCooldown > 0) {
			this.biteCooldown--;
		}
		if (this.thornCooldown > 0) {
			this.thornCooldown--;
		}
		if (this.thornComboCooldown > 0) {
			this.thornComboCooldown--;
		}
		if (this.gap > 0) {
			this.gap--;
		}
		Act act = this.act();
		if (act != Act.NONE) {
			this.acting(level, act);
		}
	}

	private void start(Act act) {
		this.getNavigation().stop();
		this.entityData.set(RIGHT, this.random.nextBoolean());
		this.setAct(act);
		switch (act) {
			case COMBO -> this.comboCooldown = COMBO_COOLDOWN;
			case BITE -> this.biteCooldown = BITE_COOLDOWN;
			case THORN -> this.thornCooldown = THORN_COOLDOWN;
			case THORN_COMBO -> this.thornComboCooldown = THORN_COMBO_COOLDOWN;
			default -> {
			}
		}
		if (act == Act.CLAW || act == Act.BITE || act == Act.COMBO || act == Act.SLAM) {
			this.playSound(NetherBeasts.BULLDROGIOTH_ATTACK, 1.5F, this.getVoicePitch());
		}
	}

	private void finish() {
		this.setAct(Act.NONE);
		this.gap = GAP;
	}

	private void face(Entity foe) {
		float yaw = (float)(Mth.atan2(foe.getZ() - this.getZ(), foe.getX() - this.getX()) * 180.0 / Math.PI) - 90.0F;
		yaw = Mth.approachDegrees(this.getYRot(), yaw, 30.0F);
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.setYHeadRot(yaw);
	}

	private void lunge(double power) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, this.getYRot());
		this.push(ahead.x * power, 0.0, ahead.z * power);
	}

	private void acting(ServerLevel level, Act act) {
		this.getNavigation().stop();
		LivingEntity foe = this.getTarget();
		int age = this.actAge;
		if (foe != null && foe.isAlive() && age < 45) {
			this.face(foe);
		}
		switch (act) {
			case SLAM -> {
				if (age == 29) {
					this.lunge(1.0);
				}
				if (age == 32) {
					this.breakBlocks(level);
					this.arc(level, SLAM_REACH, (float)DAMAGE, true);
					this.playSound(SoundEvents.WITHER_BREAK_BLOCK, 1.0F, 1.0F);
				}
			}
			case CLAW -> {
				if (age == 17) {
					this.lunge(-0.2);
				}
				if (age == 20) {
					this.lunge(0.4);
				}
				if (age == 23) {
					this.arc(level, CLAW_REACH, (float)DAMAGE, false);
					this.breakBlocks(level);
				}
			}
			case COMBO -> {
				if (age == 25 || age == 40) {
					this.lunge(0.4);
				}
				if (age == 28 || age == 43) {
					this.arc(level, CLAW_REACH, (float)DAMAGE, false);
					this.breakBlocks(level);
				}
			}
			case BITE -> {
				if (age == 30) {
					this.lunge(1.5);
				}
				if (age == 33) {
					// the original's blow of 15 and then a third more to all it catches
					this.arc(level, SLAM_REACH, (float)DAMAGE * 4.0F / 3.0F, true);
					this.breakBlocks(level);
				}
			}
			case THORN -> {
				if (age == 25) {
					this.lunge(1.0);
				}
				if (age == 28 && foe != null) {
					this.thorns(level, foe, 3, THORN_FAN);
				}
			}
			case THORN_COMBO -> {
				if (age == 30 || age == 49) {
					this.lunge(1.0);
				}
				if ((age == 33 || age == 52) && foe != null) {
					this.thorns(level, foe, 6, THORN_COMBO_FAN);
				}
			}
			default -> {
			}
		}
		if (age >= act.ticks - 1) {
			this.finish();
		}
	}

	/** A blow with a claw: all in the arc ahead of it within {@code reach} are hurt, torn, and (a slam) thrown back and robbed of their shields. */
	private void arc(ServerLevel level, double reach, float damage, boolean heavy) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, this.getYRot());
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(reach, 3.5, reach), NetherBeasts::fair)) {
			Vec3 to = new Vec3(e.getX() - this.getX(), 0.0, e.getZ() - this.getZ());
			double d = to.length();
			if (d > reach + e.getBbWidth() / 2.0 || d > 0.001 && to.scale(1.0 / d).dot(ahead) < ARC) {
				continue;
			}
			boolean blocking = e.isBlocking();
			boolean hurt = NetherBeasts.tear(level, this, e, damage);
			if (heavy && d > 0.001) {
				double d2 = Math.max(d * d, 0.001);
				e.push(to.x / d2 * 2.0, 0.2, to.z / d2 * 2.0);
				if (!hurt || blocking) {
					NetherBeasts.breakShield(e, SHIELD_DOWN);
				}
			}
		}
	}

	/** It breaks the rock in its way: the three by three about its feet, five up, whatever is soft enough (only where mobs may grief). */
	private void breakBlocks(ServerLevel level) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		boolean broke = false;
		BlockPos at = this.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 4, 1))) {
			BlockState state = level.getBlockState(pos);
			float hardness = state.getDestroySpeed(level, pos);
			if (!state.isAir() && hardness >= 0.0F && hardness <= BREAKS_UP_TO && !state.is(BlockTags.WITHER_IMMUNE)) {
				broke = level.destroyBlock(pos.immutable(), true, this) || broke;
			}
		}
		if (broke) {
			level.levelEvent(null, 1022, this.blockPosition(), 0);
		}
	}

	/** A fan of thorns off its shell at its foe. */
	private void thorns(ServerLevel level, LivingEntity foe, int count, double fanDegrees) {
		double offset = Math.toRadians(fanDegrees);
		double dx = foe.getX() - this.getX(), dz = foe.getZ() - this.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		float spread = 16.0F - level.getDifficulty().getId() * 4.0F;
		for (int i = 0; i < count; i++) {
			double angle = (i - (count - 1) / 4) * offset;
			CoralThorn thorn = new CoralThorn(level, this);
			thorn.setPos(this.getX(), this.getY() + this.getBbHeight() * 0.6, this.getZ());
			double dy = foe.getY(0.3333333333333333) - thorn.getY();
			double x = dx * Math.cos(angle) + dz * Math.sin(angle);
			double z = -dx * Math.sin(angle) + dz * Math.cos(angle);
			thorn.shoot(x, dy + flat * 0.2, z, THORN_SPEED, spread);
			level.addFreshEntity(thorn);
		}
		this.playSound(SoundEvents.POINTED_DRIPSTONE_BREAK, 1.5F, 0.7F);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (source.is(DamageTypeTags.IS_PROJECTILE)) {
				amount /= 2.0F;
			} else if (source.is(DamageTypeTags.IS_LIGHTNING)) {
				amount *= 4.0F;
			} else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
				amount /= 4.0F;
			}
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void travel(Vec3 input) {
		super.travel(this.act() == Act.NONE ? input : Vec3.ZERO);
	}

	// ---- the hunt --------------------------------------------------------------------------------------------------------

	/** It runs its foe down; near, it picks a blow, and from afar, or hurt, it throws thorns. */
	final class FightGoal extends Goal {
		FightGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			LivingEntity foe = Bulldrogioth.this.getTarget();
			return foe != null && foe.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			Bulldrogioth.this.setAggressive(false);
			Bulldrogioth.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			Bulldrogioth self = Bulldrogioth.this;
			LivingEntity foe = self.getTarget();
			if (foe == null || self.act() != Act.NONE) {
				return;
			}
			self.setAggressive(true);
			self.getLookControl().setLookAt(foe, 20.0F, 20.0F);
			if (self.gap > 0) {
				return;
			}
			double d = self.distanceTo(foe);
			RandomSource random = self.random;
			boolean sees = self.hasLineOfSight(foe);
			if (sees && self.thornComboCooldown <= 0 && random.nextInt(16) == 0 && self.getHealth() <= self.getMaxHealth() / 2.0F) {
				self.start(Act.THORN_COMBO);
			} else if (sees && self.thornCooldown <= 0 && random.nextInt(8) == 0 && d <= FOLLOW) {
				self.start(Act.THORN);
			} else if (d <= BITE_REACH && random.nextInt(2) == 0 && self.biteCooldown <= 0) {
				self.start(Act.BITE);
			} else if (d <= COMBO_REACH && random.nextInt(4) == 0 && self.comboCooldown <= 0) {
				self.start(Act.COMBO);
			} else if (d <= MELEE_REACH + foe.getBbWidth() / 2.0 + self.getBbWidth() / 2.0) {
				self.start(random.nextInt(4) != 0 ? Act.CLAW : Act.SLAM);
			} else if (self.tickCount % 5 == 0 || self.getNavigation().isDone()) {
				self.getNavigation().moveTo(foe, 1.0);
			}
		}
	}

	// ---- looks and sounds ------------------------------------------------------------------------------------------------

	@Nullable RawAnimation actAnimation() {
		boolean right = this.isRight();
		return switch (this.act()) {
			case NONE -> null;
			case SLAM -> SLAM_ANIM;
			case CLAW -> right ? CLAW_RIGHT : CLAW_LEFT;
			case COMBO -> right ? COMBO_RIGHT : COMBO_LEFT;
			case BITE -> right ? BITE_RIGHT : BITE_LEFT;
			case THORN -> right ? THORN_RIGHT : THORN_LEFT;
			case THORN_COMBO -> right ? THORN_COMBO_RIGHT : THORN_COMBO_LEFT;
		};
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Bulldrogioth>("move", 7, test -> {
			Bulldrogioth b = test.animatable();
			if (b.isDeadOrDying()) {
				return test.setAndContinue(DEATH);
			}
			if (b.isInWater() && b.act() == Act.NONE) {
				return test.setAndContinue(SWIM);
			}
			if (b.act() == Act.NONE && test.isMoving()) {
				return test.setAndContinue(b.isAggressive() ? RUN : WALK);
			}
			return test.setAndContinue(IDLE);
		}));
		controllers.add(new AnimationController<Bulldrogioth>("action", 6, test -> {
			RawAnimation animation = test.animatable().actAnimation();
			return animation == null || !test.animatable().isAlive() ? PlayState.STOP : test.setAndContinue(animation);
		}));
		controllers.add(new AnimationController<Bulldrogioth>("hurt", 20, test ->
			test.animatable().hurtTime > 0 && test.animatable().isAlive() ? test.setAndContinue(HURT) : PlayState.STOP));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return NetherBeasts.BULLDROGIOTH_IDLE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return NetherBeasts.BULLDROGIOTH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return NetherBeasts.BULLDROGIOTH_HURT;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.8F, 0.6F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("variant", this.variant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(VARIANT, Mth.clamp(input.getIntOr("variant", 0), 0, 2));
	}
}
