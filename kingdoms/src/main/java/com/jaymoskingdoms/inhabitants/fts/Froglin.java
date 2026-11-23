package com.jaymoskingdoms.inhabitants.fts;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.CampBeasts;
import com.jaymoskingdoms.Loaded;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The froglin: a squat, wet frog-legged thing the goblin villages and orc camps keep about their fires like dogs (Andy:
 * "Should live among goblins and orc"). Ported from FroglinEntity in From the Shadows Reborn by Sonmok14 and L_Ender
 * (AGPL v3; model, textures and animations are theirs, the code is our own, see licenses/from-the-shadows/).
 *
 * <p>It claws, strikes, leaps and swallows small game whole (a chicken, a fish, an axolotl, a rabbit); once it has
 * swallowed it is full, and a full froglin spits its meal at what is far off (the vomit, {@link FrogVomit}) and is hungry
 * again. A camp's froglins never turn on the camp's own people and go for what the camp would go for ({@link CampBeasts}).
 * When their camp is cleared they have no one to keep them: they go rogue and take any player for prey.
 *
 * <p>Living things: <b>sympathy</b> (a frog eats what fits in its mouth and croaks), <b>feedback</b> (a full froglin hunts
 * nothing small until it has spat or digested; hunting thins the camp's chickens), <b>stewardship</b> (a fish held out
 * to one is a meal: it is full, calm and leaves players alone for {@link #FED_TICKS}; nobody is punished for leaving
 * one be).
 */
public class Froglin extends Monster implements GeoEntity {
	static final double HEALTH = 25.0, DAMAGE = 5.0, ARMOR = 2.0, SPEED = 0.3, FOLLOW = 16.0;
	/** How many a new camp takes on, the most it keeps, how near its fire they stay, how far a camp counts them. */
	public static final int START = 2;
	static final int MAX = 3, HOME_RADIUS = 24;
	static final double COUNT_RANGE = 48.0;
	/** The chance a camp short of them takes one on, each day it grows. */
	static final float HIRE_CHANCE = 0.3F;
	/** A fish fed to one: it leaves players alone this long (ticks), and a froglin's own claws hit this much harder in an arc. */
	static final int FED_TICKS = 20 * 60 * 5;
	static final float ARC_DAMAGE = 1.3F;
	/** The jump's wait (ticks), and its reach (blocks). */
	static final int JUMP_COOLDOWN = 300;
	static final double VOMIT_RANGE = 5.0, SWALLOW_RANGE = 4.0, MELEE_REACH = 3.5;

	public static final byte MELEE = 1, VOMIT = 2, JUMP = 3, STRIKE = 4, SWALLOW = 5;

	private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(Froglin.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RIGHT = SynchedEntityData.defineId(Froglin.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> FULL = SynchedEntityData.defineId(Froglin.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Froglin.class, EntityDataSerializers.INT);

	/** Animation names as in its .animation.json. */
	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.frog.idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.frog.walkslow");
	static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.frog.run");
	static final RawAnimation DASH = RawAnimation.begin().thenLoop("animation.frog.dash");
	static final RawAnimation SWALLOWING = RawAnimation.begin().thenLoop("animation.frog.swallow");
	static final RawAnimation CROUCH = RawAnimation.begin().thenLoop("animation.frog.crouched");
	static final RawAnimation VOMIT_ATTACK = RawAnimation.begin().thenLoop("animation.frog.vomitattack");
	static final RawAnimation VOMIT_READY = RawAnimation.begin().thenLoop("animation.frog.vomitready");
	static final RawAnimation STRIKING = RawAnimation.begin().thenPlayAndHold("animation.frog.strike");
	static final RawAnimation CLAW_RIGHT = RawAnimation.begin().thenPlayAndHold("animation.frog.mtright");
	static final RawAnimation CLAW_LEFT = RawAnimation.begin().thenPlayAndHold("animation.frog.mtleft");
	static final RawAnimation BLINK = RawAnimation.begin().thenLoop("animation.frog.eye");
	static final RawAnimation BELLY = RawAnimation.begin().thenLoop("animation.frog.full");
	static final RawAnimation CROAK = RawAnimation.begin().thenLoop("animation.frog.croaking");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** Ticks into the move it is making (both sides count), and the counters of its blink and croak. */
	private int attackTick;
	private int blink, croak;
	private int jumpCooldown;
	/** The camp keeping it; -1 for none. And how long a fed one is calm (ticks). */
	private int camp = -1;
	private int fed;

	public Froglin(EntityType<? extends Froglin> type, Level level) {
		super(type, level);
		this.xpReward = 5;
		this.setPathfindingMalus(PathType.WATER, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.ATTACK_KNOCKBACK, 0.0).add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ARMOR, ARMOR).add(Attributes.STEP_HEIGHT, 1.0);
	}

	// ---- state ---------------------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ATTACK, 0);
		entityData.define(RIGHT, false);
		entityData.define(FULL, false);
		entityData.define(VARIANT, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (ATTACK.equals(accessor)) {
			this.attackTick = 0;
		}
	}

	public int attack() {
		return this.entityData.get(ATTACK);
	}

	private void setAttack(int id) {
		this.entityData.set(ATTACK, id);
		this.attackTick = 0;
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	public boolean isFull() {
		return this.entityData.get(FULL);
	}

	private void setFull(boolean full) {
		this.entityData.set(FULL, full);
	}

	public void setCamp(int camp) {
		this.camp = camp;
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		this.entityData.set(VARIANT, this.getRandom().nextInt(3));
		this.setFull(this.getRandom().nextBoolean());
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("variant", this.variant());
		output.putBoolean("full", this.isFull());
		output.putInt("camp", this.camp);
		output.putInt("fed", this.fed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(VARIANT, Mth.clamp(input.getIntOr("variant", 0), 0, 2));
		this.setFull(input.getBooleanOr("full", false));
		this.camp = input.getIntOr("camp", -1);
		this.fed = input.getIntOr("fed", 0);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return this.camp < 0 && super.removeWhenFarAway(distSqr);
	}

	@Override
	protected int calculateFallDamage(double fallDistance, float damageModifier) {
		return 0;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
	}

	// ---- who it goes for -----------------------------------------------------------------------------------------------

	/** Small game a froglin swallows whole. */
	static boolean smallGame(LivingEntity e) {
		return e instanceof Chicken || e instanceof AbstractFish || e instanceof Axolotl || e instanceof Rabbit;
	}

	/** On its side: its own kind, the camp's people, any goblin or orc. */
	private boolean ally(LivingEntity e) {
		return e instanceof Froglin || CampBeasts.ally(this.camp, e);
	}

	private boolean prey(ServerLevel level, LivingEntity target) {
		if (this.ally(target)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (target instanceof Player player) {
			if (this.fed > 0 || player.isCreative() || player.isSpectator()) {
				return false;
			}
			return this.camp < 0 || CampBeasts.prey(server, this.camp, player);
		}
		if (this.camp >= 0 && CampBeasts.prey(server, this.camp, target)) {
			return true;
		}
		return !this.isFull() && smallGame(target);
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		// (never its own side, even when one of them hits it by mistake)
		super.setTarget(target != null && this.ally(target) ? null : target);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(0, new Swallow());
		this.goalSelector.addGoal(0, new Vomit());
		this.goalSelector.addGoal(0, new Claw());
		this.goalSelector.addGoal(0, new Strike());
		this.goalSelector.addGoal(0, new Leap());
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, false));
		this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.6, 25));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, (target, level) -> this.prey(level, target)));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// (the blow itself lands when its animation does: Claw, Strike)
		if (this.attack() == 0) {
			this.setAttack(this.getRandom().nextInt(4) != 0 ? MELEE : STRIKE);
		}
		return true;
	}

	/** A fish held out to it is a meal (stewardship). */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(ItemTags.FISHES)) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel) {
			if (!player.isCreative()) {
				stack.shrink(1);
			}
			this.playSound(SoundEvents.GENERIC_EAT, this.getSoundVolume(), this.getVoicePitch());
			this.gameEvent(GameEvent.EAT);
			this.fed = FED_TICKS;
			this.setFull(true);
			if (this.getTarget() instanceof Player) {
				this.setTarget(null);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.camp >= 0 && this.tickCount % 100 == 0) {
			if (!CampBeasts.standing(level.getServer(), this.camp)) {
				// its camp is gone: no one keeps it now
				this.camp = -1;
				this.clearHome();
			} else if (!this.hasHome()) {
				BlockPos fire = CampBeasts.fire(level.getServer(), this.camp);
				if (fire != null) {
					this.setHomeTo(fire, HOME_RADIUS);
				}
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.fed > 0) {
			this.fed--;
		}
		if (this.jumpCooldown > 0) {
			this.jumpCooldown--;
		}
		this.blink = this.blink <= 0 ? 100 : this.blink - 1;
		this.croak = this.croak <= 0 ? 50 : this.croak - 1;
		if (this.attack() != 0) {
			this.yBodyRot = this.yHeadRot;
			this.setYRot(this.yBodyRot);
			this.attackTick++;
			if (this.getTarget() != null) {
				this.getLookControl().setLookAt(this.getTarget(), 30.0F, 90.0F);
			}
		} else if (this.entityData.get(RIGHT)) {
			this.entityData.set(RIGHT, false);
		}
	}

	@Override
	public void travel(Vec3 input) {
		if (this.attack() != 0) {
			this.getNavigation().stop();
			super.travel(Vec3.ZERO);
		} else {
			super.travel(input);
		}
	}

	// ---- its moves -----------------------------------------------------------------------------------------------------

	/** A move that plays out over {@code length} ticks, striking at its own ticks. */
	private abstract class Act extends Goal {
		final int id, length;

		Act(int id, int length) {
			this.id = id;
			this.length = length;
			this.setFlags(EnumSet.of(Flag.JUMP, Flag.LOOK, Flag.MOVE));
		}

		@Override
		public void start() {
			if (Froglin.this.attack() != this.id) {
				Froglin.this.setAttack(this.id);
			}
		}

		@Override
		public void stop() {
			Froglin.this.setAttack(0);
		}

		@Override
		public boolean canContinueToUse() {
			return Froglin.this.attackTick < this.length && Froglin.this.getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = Froglin.this.getTarget();
			if (target != null) {
				this.step(Froglin.this.attackTick, target);
			}
		}

		abstract void step(int tick, LivingEntity target);

		boolean idle() {
			return Froglin.this.getTarget() != null && Froglin.this.attack() == 0;
		}
	}

	/** Pushes the froglin forward along where it faces. */
	private void lunge(double power) {
		float yaw = (float)Math.toRadians(this.getYRot() + 90.0F);
		this.push(Mth.cos(yaw) * power, 0.0, Mth.sin(yaw) * power);
	}

	/** Its claws, in an arc ahead of it: everything but its own side. */
	private void sweep(ServerLevel level) {
		float range = 2.0F, arc = 80.0F;
		AABB box = this.getBoundingBox().inflate(range, 3.5, range);
		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && !this.ally(e) && this.distanceTo(e) <= range + e.getBbWidth() / 2.0F)) {
			float hitAngle = (float)(Math.atan2(hit.getZ() - this.getZ(), hit.getX() - this.getX()) * (180.0 / Math.PI) - 90.0);
			float diff = Mth.wrapDegrees(hitAngle - this.yHeadRot);
			if (Math.abs(diff) <= arc / 2.0F) {
				hit.hurtServer(level, this.damageSources().mobAttack(this), (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE) * ARC_DAMAGE);
			}
		}
	}

	/** A raking blow with one hand. */
	private final class Claw extends Act {
		Claw() {
			super(MELEE, 20);
		}

		@Override
		public boolean canUse() {
			return Froglin.this.getTarget() != null && Froglin.this.attack() == MELEE;
		}

		@Override
		public void start() {
			Froglin.this.entityData.set(RIGHT, Froglin.this.getRandom().nextBoolean());
			super.start();
		}

		@Override
		void step(int tick, LivingEntity target) {
			if (tick == 4) {
				Froglin.this.lunge(0.3);
			}
			if (tick == 9 && Froglin.this.distanceTo(target) <= MELEE_REACH && Froglin.this.level() instanceof ServerLevel level) {
				target.hurtServer(level, Froglin.this.damageSources().mobAttack(Froglin.this), (float)Froglin.this.getAttributeValue(Attributes.ATTACK_DAMAGE));
			}
		}
	}

	/** A sweeping strike with both. */
	private final class Strike extends Act {
		Strike() {
			super(STRIKE, 22);
		}

		@Override
		public boolean canUse() {
			return Froglin.this.getTarget() != null && Froglin.this.attack() == STRIKE;
		}

		@Override
		void step(int tick, LivingEntity target) {
			if (tick == 5) {
				Froglin.this.lunge(0.3);
			}
			if (tick == 9 && Froglin.this.level() instanceof ServerLevel level) {
				Froglin.this.sweep(level);
			}
		}
	}

	/** A leap at what is out of reach, to land with a sweep. */
	private final class Leap extends Act {
		Leap() {
			super(JUMP, 20);
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Froglin.this.getTarget();
			return this.idle() && Froglin.this.distanceTo(target) > 4.0 && Froglin.this.onGround() && Froglin.this.jumpCooldown == 0
				&& Froglin.this.getRandom().nextInt(15) == 0;
		}

		@Override
		void step(int tick, LivingEntity target) {
			if (tick == 2) {
				Froglin.this.lunge(0.4);
			}
			if (tick == 9) {
				Froglin.this.setDeltaMovement((target.getX() - Froglin.this.getX()) * 0.21, 0.5, (target.getZ() - Froglin.this.getZ()) * 0.21);
			}
			if (tick >= 14 && Froglin.this.jumpCooldown == 0 && Froglin.this.level() instanceof ServerLevel level) {
				Froglin.this.sweep(level);
				Froglin.this.jumpCooldown = JUMP_COOLDOWN;
			}
		}
	}

	/** Gulping small game whole: the froglin is full for it. */
	private final class Swallow extends Act {
		Swallow() {
			super(SWALLOW, 20);
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Froglin.this.getTarget();
			return this.idle() && smallGame(target) && !Froglin.this.isFull() && Froglin.this.distanceTo(target) <= SWALLOW_RANGE
				&& Froglin.this.getRandom().nextInt(10) == 0;
		}

		@Override
		void step(int tick, LivingEntity target) {
			if (tick == 2) {
				Froglin.this.lunge(0.4);
			}
			if (tick == 9) {
				Froglin.this.setDeltaMovement((target.getX() - Froglin.this.getX()) * 0.21, 0.5, (target.getZ() - Froglin.this.getZ()) * 0.21);
			}
			if (tick >= 14 && Froglin.this.distanceTo(target) <= SWALLOW_RANGE && smallGame(target)) {
				Froglin.this.setFull(true);
				Froglin.this.gameEvent(GameEvent.EAT);
				Froglin.this.playSound(SoundEvents.GENERIC_EAT, Froglin.this.getSoundVolume(), Froglin.this.getVoicePitch());
				target.discard();
				Froglin.this.setTarget(null);
			}
		}
	}

	/** Spitting up its meal at what is far off, twice; then it is hungry again. */
	private final class Vomit extends Act {
		Vomit() {
			super(VOMIT, 30);
		}

		@Override
		public boolean canUse() {
			LivingEntity target = Froglin.this.getTarget();
			return this.idle() && Froglin.this.isFull() && !smallGame(target) && Froglin.this.distanceTo(target) > VOMIT_RANGE
				&& Froglin.this.onGround() && Froglin.this.getRandom().nextInt(5) == 0;
		}

		@Override
		void step(int tick, LivingEntity target) {
			if (tick == 10 || tick == 20) {
				Froglin.this.playSound(SoundEvents.PLAYER_BURP, tick == 10 ? 2.0F : 1.0F, (tick == 10 ? 0.2F : 0.8F) + Froglin.this.getRandom().nextFloat() * 0.1F);
				if (Froglin.this.level() instanceof ServerLevel level) {
					FrogVomit vomit = new FrogVomit(level, Froglin.this);
					vomit.setPos(Froglin.this.getX(), Froglin.this.getEyeY() - 0.1, Froglin.this.getZ());
					double dx = target.getX() - Froglin.this.getX(), dz = target.getZ() - Froglin.this.getZ();
					double dy = target.getY(0.3333333333333333) - vomit.getY();
					vomit.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.4, dz, 1.0F, 5.0F);
					level.addFreshEntity(vomit);
				}
				if (tick == 20) {
					Froglin.this.setFull(false);
				}
			}
		}
	}

	// ---- how it looks --------------------------------------------------------------------------------------------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Froglin>("move", 4, test -> {
			Froglin froglin = test.animatable();
			int attack = froglin.attack();
			int tick = froglin.attackTick;
			if (attack == 0) {
				if (!test.isMoving()) {
					return test.setAndContinue(IDLE);
				}
				return test.setAndContinue(froglin.isAggressive() ? RUN : WALK);
			}
			return switch (attack) {
				case JUMP -> test.setAndContinue(tick > 9 ? DASH : CROUCH);
				case SWALLOW -> test.setAndContinue(tick > 9 ? SWALLOWING : CROUCH);
				case VOMIT -> test.setAndContinue(tick > 10 ? VOMIT_ATTACK : VOMIT_READY);
				case STRIKE -> test.setAndContinue(STRIKING);
				case MELEE -> test.setAndContinue(froglin.entityData.get(RIGHT) ? CLAW_RIGHT : CLAW_LEFT);
				default -> PlayState.CONTINUE;
			};
		}));
		controllers.add(new AnimationController<Froglin>("eye", 20, test -> test.animatable().blink <= 5 ? test.setAndContinue(BLINK) : PlayState.STOP));
		controllers.add(new AnimationController<Froglin>("full", 20, test -> test.animatable().isFull() ? test.setAndContinue(BELLY) : PlayState.STOP));
		controllers.add(new AnimationController<Froglin>("croak", 20, test -> test.animatable().croak <= 5 ? test.setAndContinue(CROAK) : PlayState.STOP));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.FROG_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.FROG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.FROG_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.6F;
	}

	// ---- the camp keeps them -------------------------------------------------------------------------------------------

	/** Camp {@code camp} (fire at {@code fire}) takes on froglins: {@code count} of them, never past {@link #MAX} about its fire. */
	public static void hire(ServerLevel level, int camp, BlockPos fire, int count) {
		if (Fts.FROGLIN == null || !Loaded.made(level, fire)) {
			return;
		}
		int near = level.getEntitiesOfClass(Froglin.class, new AABB(fire).inflate(COUNT_RANGE), f -> f.camp == camp).size();
		RandomSource random = level.getRandom();
		for (int i = 0; i < count && near < MAX; i++, near++) {
			Froglin froglin = Fts.FROGLIN.create(level, EntitySpawnReason.EVENT);
			if (froglin == null) {
				return;
			}
			BlockPos at = CampBeasts.spot(level, camp, fire, random);
			froglin.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			froglin.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			froglin.camp = camp;
			froglin.setHomeTo(fire, HOME_RADIUS);
			froglin.setPersistenceRequired();
			level.addFreshEntity(froglin);
		}
	}

	/** {@code days}' growth of camp {@code camp}: a camp short of froglins now and then takes one on. */
	public static void grow(ServerLevel level, int camp, BlockPos fire, int days) {
		if (level.getRandom().nextFloat() < 1.0F - Math.pow(1.0F - HIRE_CHANCE, days)) {
			hire(level, camp, fire, 1);
		}
	}
}
