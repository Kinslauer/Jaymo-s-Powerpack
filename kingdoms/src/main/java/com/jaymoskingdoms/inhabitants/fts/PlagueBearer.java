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
import com.jaymoskingdoms.nightfall.Nightfall;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Plague Bearer: a sick orc in rags and a plague doctor's mask, kept at the edge of an orc camp to walk into enemy
 * towns and spread the plague, or, when his camp is gone (or he was driven out of it), an exile wandering alone with it
 * (Andy: "An orc camp asset/ exile"). Ported from PlagueBearerEntity in From the Shadows Reborn by Sonmok14 and L_Ender
 * (AGPL v3; model, textures, animations and sounds are theirs, the code is our own, see licenses/from-the-shadows/).
 *
 * <p>He cleaves with a heavy blow, and hops back to throw three knives ({@link PlagueKnife}) that give the plague; and
 * where he dies he leaves a cloud of it hanging (Andy: "he should only spread it with knives or a cloud upon his death").
 * The plague is Nightfall's (Atrophy, {@link Nightfall#infect}), never a second one of its own; a plague doctor's mask, and
 * Immunity, keep it out. He is immune to it himself. His mask drops now and then.
 */
public class PlagueBearer extends Monster implements GeoEntity {
	static final double HEALTH = 30.0, DAMAGE = 7.0, ARMOR = 2.0, SPEED = 0.3, FOLLOW = 32.0;
	/** How far from the fire a camp keeps its bearer (blocks), how far he roams, how far a camp counts him. */
	static final int EDGE = 18, HOME_RADIUS = 40;
	static final double COUNT_RANGE = 80.0;
	/** The chance a new orc camp keeps one, and a camp without one takes one in each day it grows. */
	static final float RISE_CHANCE = 0.5F, GROW_CHANCE = 0.15F;
	/** The cloud he leaves in dying: how wide (blocks), how high, how long it hangs (ticks), how often it infects, the plague it gives (ticks), its puffs a tick, its colour. */
	static final double CLOUD_RADIUS = 3.0, CLOUD_HEIGHT = 2.5;
	static final int CLOUD_TICKS = 20 * 8, CLOUD_EVERY = 10, CLOUD_PLAGUE_TICKS = 20 * 15, CLOUD_PUFFS = 6;
	static final int CLOUD_COLOUR = 0xFF8A9A2B;
	/** The knife volley: wait between (ticks), how far he throws, the spread (degrees), the speed. */
	static final int KNIFE_COOLDOWN = 200;
	static final double KNIFE_RANGE = 20.0;
	static final float KNIFE_SPREAD_DEGREES = 5.0F, KNIFE_SPEED = 2.0F;
	/** The chance his mask drops, and the odds of a natural spawn (one in so many, with the Monster rules). */
	static final float MASK_DROP = 0.15F;
	public static final int SPAWN_WEIGHT = 1, SPAWN_ONE_IN = 8;

	public static final byte MELEE = 1, THROW = 2;
	private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(PlagueBearer.class, EntityDataSerializers.INT);

	/** Animation names as in its .animation.json. */
	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.plague_bearer.idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.plague_bearer.walk");
	static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.plague_bearer.run");
	static final RawAnimation SWING = RawAnimation.begin().thenPlayAndHold("animation.plague_bearer.melee_attack");
	static final RawAnimation THROW_READY = RawAnimation.begin().thenPlayAndHold("animation.plague_bearer.throw_dagger_ready");
	static final RawAnimation THROWING = RawAnimation.begin().thenPlayAndHold("animation.plague_bearer.throw_dagger");
	static final RawAnimation MUMBLE = RawAnimation.begin().thenLoop("animation.plague_bearer.mumble");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private int attackTick;
	private int mumble = 200;
	private int knifeCooldown;
	/** The camp keeping him; -1 for an exile. */
	private int camp = -1;

	public PlagueBearer(EntityType<? extends PlagueBearer> type, Level level) {
		super(type, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.ATTACK_KNOCKBACK, 1.0).add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ARMOR, ARMOR).add(Attributes.STEP_HEIGHT, 1.0);
	}

	/** An exile's natural spawn: in the open, by the usual monster rules, and rarely. */
	public static boolean checkSpawn(EntityType<PlagueBearer> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return EntitySpawnReason.isSpawner(reason) || Monster.checkMonsterSpawnRules(type, level, reason, pos, random) && random.nextInt(SPAWN_ONE_IN) == 0;
	}

	// ---- state ---------------------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(ATTACK, 0);
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

	/** His arms are out (a fight), not folded. */
	public boolean angry() {
		return this.isAggressive() || this.attack() != 0;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("camp", this.camp);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.camp = input.getIntOr("camp", -1);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return this.camp < 0 && super.removeWhenFarAway(distSqr);
	}

	/** Bearing the plague, he does not catch it. */
	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(Nightfall.ATROPHY) && super.canBeAffected(effect);
	}

	@Override
	protected int calculateFallDamage(double fallDistance, float damageModifier) {
		return this.attack() == THROW ? 0 : super.calculateFallDamage(fallDistance, damageModifier);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData data) {
		data = super.finalizeSpawn(level, difficulty, reason, data);
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Fts.PLAGUE_DOCTOR_MASK));
		this.setDropChance(EquipmentSlot.HEAD, MASK_DROP);
		return data;
	}

	// ---- who he goes for -----------------------------------------------------------------------------------------------

	private boolean ally(LivingEntity e) {
		return e instanceof PlagueBearer || CampBeasts.ally(this.camp, e);
	}

	private boolean prey(ServerLevel level, LivingEntity target) {
		if (this.ally(target)) {
			return false;
		}
		if (target instanceof Player player) {
			if (player.isCreative() || player.isSpectator()) {
				return false;
			}
			return this.camp < 0 || CampBeasts.prey(level.getServer(), this.camp, player);
		}
		if (this.camp >= 0) {
			return CampBeasts.prey(level.getServer(), this.camp, target);
		}
		return target instanceof AbstractVillager || target instanceof IronGolem;
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target != null && this.ally(target) ? null : target);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(0, new Throw());
		this.goalSelector.addGoal(0, new Cleave());
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, false));
		this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 1.0));
		this.goalSelector.addGoal(4, new RandomStrollGoal(this, 0.5, 25));
		this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
		this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true, (target, level) -> this.prey(level, target)));
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		if (this.attack() == 0) {
			this.setAttack(MELEE);
		}
		return true;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.camp >= 0 && this.tickCount % 100 == 0) {
			if (!CampBeasts.standing(level.getServer(), this.camp)) {
				// his camp is gone: he is an exile now
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

	/** His death: a cloud of the plague is left where he fell. */
	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			CLOUDS.add(new Cloud(level, this.position(), CLOUD_TICKS));
			level.playSound(null, this.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 0.8F, 0.6F);
		}
	}

	/** The plague clouds hanging where bearers fell. They are not kept across a restart. */
	private static final List<Cloud> CLOUDS = new ArrayList<>();

	private static final class Cloud {
		final ServerLevel level;
		final Vec3 at;
		int left;

		Cloud(ServerLevel level, Vec3 at, int left) {
			this.level = level;
			this.at = at;
			this.left = left;
		}
	}

	/** Hangs the clouds in the air, and gives the plague to the living things standing in them (not past a mask, or Immunity). */
	public static void tickClouds(MinecraftServer server) {
		for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext();) {
			Cloud cloud = it.next();
			if (--cloud.left <= 0 || cloud.level.getServer() != server) {
				it.remove();
				continue;
			}
			// (it thins as it fades)
			double radius = CLOUD_RADIUS * (0.5 + 0.5 * cloud.left / CLOUD_TICKS);
			RandomSource random = cloud.level.getRandom();
			for (int i = 0; i < CLOUD_PUFFS; i++) {
				double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * radius;
				cloud.level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, CLOUD_COLOUR), cloud.at.x + Math.cos(a) * r,
					cloud.at.y + 0.2 + random.nextDouble() * CLOUD_HEIGHT * 0.5, cloud.at.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.0);
			}
			if (cloud.left % CLOUD_EVERY == 0) {
				AABB box = new AABB(cloud.at.x - radius, cloud.at.y - 0.5, cloud.at.z - radius, cloud.at.x + radius, cloud.at.y + CLOUD_HEIGHT, cloud.at.z + radius);
				for (LivingEntity e : cloud.level.getEntitiesOfClass(LivingEntity.class, box, e -> !(e instanceof PlagueBearer))) {
					Nightfall.infect(e, CLOUD_PLAGUE_TICKS, 0);
				}
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.knifeCooldown > 0) {
			this.knifeCooldown--;
		}
		this.mumble = this.mumble <= 0 ? 200 : this.mumble - 1;
		if (this.attack() != 0) {
			this.yBodyRot = this.yHeadRot;
			this.setYRot(this.yBodyRot);
			this.attackTick++;
			if (this.getTarget() != null) {
				this.getLookControl().setLookAt(this.getTarget(), 30.0F, 90.0F);
			}
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

	// ---- his moves -----------------------------------------------------------------------------------------------------

	private abstract class Act extends Goal {
		final int id, length;

		Act(int id, int length) {
			this.id = id;
			this.length = length;
			this.setFlags(EnumSet.of(Flag.JUMP, Flag.LOOK, Flag.MOVE));
		}

		@Override
		public void start() {
			if (PlagueBearer.this.attack() != this.id) {
				PlagueBearer.this.setAttack(this.id);
			}
		}

		@Override
		public void stop() {
			PlagueBearer.this.setAttack(0);
		}

		@Override
		public boolean canContinueToUse() {
			return PlagueBearer.this.attackTick < this.length && PlagueBearer.this.getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = PlagueBearer.this.getTarget();
			if (target != null) {
				if (PlagueBearer.this.attackTick == 1) {
					PlagueBearer.this.playSound(Fts.PLAGUE_BEARER_PREATTACK, 2.0F, 0.3F + PlagueBearer.this.getRandom().nextFloat() * 0.1F);
				}
				this.step(PlagueBearer.this.attackTick, target);
			}
		}

		abstract void step(int tick, LivingEntity target);
	}

	private void push(double power, double lift) {
		float yaw = (float)Math.toRadians(this.getYRot() + 90.0F);
		this.push(Mth.cos(yaw) * power, lift, Mth.sin(yaw) * power);
	}

	/** A heavy blow. */
	private final class Cleave extends Act {
		Cleave() {
			super(MELEE, 28);
		}

		@Override
		public boolean canUse() {
			return PlagueBearer.this.getTarget() != null && PlagueBearer.this.attack() == MELEE;
		}

		@Override
		void step(int tick, LivingEntity target) {
			if (tick == 16) {
				PlagueBearer.this.playSound(Fts.PLAGUE_BEARER_ATTACK, 2.0F, 0.3F + PlagueBearer.this.getRandom().nextFloat() * 0.1F);
				PlagueBearer.this.push(1.0, 0.0);
			}
			if (tick == 20 && PlagueBearer.this.distanceTo(target) <= 3.5F && PlagueBearer.this.level() instanceof ServerLevel level) {
				target.hurtServer(level, PlagueBearer.this.damageSources().mobAttack(PlagueBearer.this),
					(float)PlagueBearer.this.getAttributeValue(Attributes.ATTACK_DAMAGE));
			}
		}
	}

	/** A hop back and three knives. */
	private final class Throw extends Act {
		Throw() {
			super(THROW, 30);
		}

		@Override
		public boolean canUse() {
			LivingEntity target = PlagueBearer.this.getTarget();
			return target != null && PlagueBearer.this.attack() == 0 && PlagueBearer.this.knifeCooldown == 0
				&& PlagueBearer.this.distanceTo(target) <= KNIFE_RANGE && PlagueBearer.this.getRandom().nextInt(8) == 0;
		}

		@Override
		public void stop() {
			super.stop();
			PlagueBearer.this.knifeCooldown = KNIFE_COOLDOWN;
		}

		@Override
		void step(int tick, LivingEntity target) {
			if (tick == 16) {
				PlagueBearer.this.playSound(Fts.PLAGUE_BEARER_ATTACK, 2.0F, 0.3F + PlagueBearer.this.getRandom().nextFloat() * 0.1F);
				PlagueBearer.this.push(-1.0, 0.5);
			}
			if (tick == 18 && PlagueBearer.this.level() instanceof ServerLevel level) {
				PlagueBearer.this.throwKnives(level, target);
			}
		}
	}

	private void throwKnives(ServerLevel level, LivingEntity target) {
		double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
		for (int i = -1; i <= 1; i++) {
			double angle = Math.toRadians(i * KNIFE_SPREAD_DEGREES);
			PlagueKnife knife = new PlagueKnife(level, this);
			double dy = target.getY(0.3333333333333333) - knife.getY();
			double flat = Math.sqrt(dx * dx + dz * dz);
			double x = dx * Math.cos(angle) + dz * Math.sin(angle);
			double z = -dx * Math.sin(angle) + dz * Math.cos(angle);
			knife.shoot(x, dy + flat * 0.1, z, KNIFE_SPEED, 16 - level.getDifficulty().getId() * 4);
			level.addFreshEntity(knife);
		}
	}

	// ---- how he looks and sounds ---------------------------------------------------------------------------------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<PlagueBearer>("move", 7, test -> {
			PlagueBearer bearer = test.animatable();
			if (bearer.attack() == THROW) {
				return test.setAndContinue(bearer.attackTick <= 13 ? THROW_READY : THROWING);
			}
			if (bearer.attack() == MELEE) {
				return test.setAndContinue(SWING);
			}
			if (test.isMoving()) {
				return test.setAndContinue(bearer.isAggressive() ? RUN : WALK);
			}
			return test.setAndContinue(IDLE);
		}));
		controllers.add(new AnimationController<PlagueBearer>("mumble", 15, test -> test.animatable().mumble <= 35 ? test.setAndContinue(MUMBLE) : PlayState.STOP));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return Fts.PLAGUE_BEARER_IDLE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Fts.PLAGUE_BEARER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Fts.PLAGUE_BEARER_DEATH;
	}

	// ---- the camp keeps him --------------------------------------------------------------------------------------------

	/** A spot at the edge of the camp, {@link #EDGE} blocks from its fire in a random direction, on loaded dry ground. */
	private static @Nullable BlockPos edge(ServerLevel level, BlockPos fire, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int x = fire.getX() + (int)Math.round(Math.cos(angle) * EDGE), z = fire.getZ() + (int)Math.round(Math.sin(angle) * EDGE);
		if (!Loaded.made(level, x, z)) {
			return null;
		}
		BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		return level.getFluidState(at.below()).isEmpty() ? at : null;
	}

	/** Orc camp {@code camp} (fire at {@code fire}) keeps a bearer at its edge, if it has none. */
	public static void hire(ServerLevel level, int camp, BlockPos fire) {
		if (Fts.PLAGUE_BEARER == null || !Loaded.made(level, fire)
			|| !level.getEntitiesOfClass(PlagueBearer.class, new AABB(fire).inflate(COUNT_RANGE), b -> b.camp == camp).isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		BlockPos at = edge(level, fire, random);
		PlagueBearer bearer = at == null ? null : Fts.PLAGUE_BEARER.create(level, EntitySpawnReason.EVENT);
		if (bearer == null) {
			return;
		}
		bearer.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		bearer.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		bearer.camp = camp;
		bearer.setHomeTo(fire, HOME_RADIUS);
		bearer.setPersistenceRequired();
		level.addFreshEntity(bearer);
	}

	/** A new orc camp may keep a bearer. */
	public static void rise(ServerLevel level, int camp, BlockPos fire) {
		if (level.getRandom().nextFloat() < RISE_CHANCE) {
			hire(level, camp, fire);
		}
	}

	/** {@code days}' growth of an orc camp: one without a bearer may take one in. */
	public static void grow(ServerLevel level, int camp, BlockPos fire, int days) {
		if (level.getRandom().nextFloat() < 1.0F - Math.pow(1.0F - GROW_CHANCE, days)) {
			hire(level, camp, fire);
		}
	}
}
