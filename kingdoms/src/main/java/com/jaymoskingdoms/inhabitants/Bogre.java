package com.jaymoskingdoms.inhabitants;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.PowersCompat;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Bogre: a huge, slow, solitary ogre of the mangrove swamps, ported from Inhabitants (JeremySeq / Team Synapse, MIT;
 * the model, texture and animations are theirs, by Catalyst: kingdoms/art/bogre-CREDITS.md). The behaviour is written
 * fresh for Kingdoms after Inhabitants' own: it leaves the unarmed alone, roars once at anyone who comes near with a
 * weapon out, and fights whoever stays armed and close, or hits it. Its blow is a hammer slam that sends a shockwave
 * along the ground (jump it and it misses you).
 *
 * <p>It keeps to itself: it walks away from villagers and other Bogres, never spawns near a village or another Bogre,
 * sleeps through the night when no one's about, and dances when a jukebox plays near it. Its cooking and the Giant Bone
 * Club are their own tasks (bogre-cooking, bone-club); this is the mob they build on.
 *
 * <p>Every orc camp also keeps one by its fire as its heaviest hand ({@code OgreHolds#joinCamp}): that one has a home, keeps
 * to the camp instead of walking off from its folk, walks back when it strays, and spares whoever the camp's ogres spare.
 */
public class Bogre extends Monster implements GeoEntity {
	static final double HEALTH = 200.0, SPEED = 0.17, ARMOR = 6.0, KNOCKBACK_RESISTANCE = 1.0, FOLLOW = 32.0;
	/** Its chase is this much faster than its walk (a speed modifier), its wander this much slower. */
	static final double RUN = 1.45, WANDER = 0.8;
	/** Anyone in this range with a weapon out gets a roar (blocks); one still armed this close after that is fought. */
	static final double WARN_RANGE = 12.0, ANGER_RANGE = 6.0;
	/** How long it remembers having warned someone (ticks): come back armed after that and it roars again first. */
	static final int WARN_MEMORY = 1200;
	/** It gives up a foe this far away (blocks). */
	static final double FORGET_RANGE = 28.0;
	/** The roar: it stands and roars this long (ticks, the roar animation's 3.5 s is cut short), shaking the view this hard. */
	static final int ROAR_TICKS = 40, ROAR_SHAKE_AT = 10;
	static final double ROAR_SHAKE_RANGE = 16.0;
	static final float ROAR_SHAKE = 0.6F;
	/**
	 * The slam: it swings when its foe is this close (blocks), the hammer lands this many ticks into the swing, and it can
	 * swing again this long after (ticks). The shockwave runs out from a point this far in front of it to this radius,
	 * doing this much at the centre and this share of it at the edge, with a jolt up and away, and shakes the view.
	 */
	static final double SLAM_REACH = 4.5, SLAM_AHEAD = 2.5, SLAM_RADIUS = 6.0, SLAM_JOLT_UP = 0.55, SLAM_JOLT_AWAY = 1.1;
	static final int SLAM_HIT_TICK = 10, SLAM_SWING_TICKS = 25, SLAM_COOLDOWN = 40;
	static final float SLAM_DAMAGE = 18.0F, SLAM_EDGE = 0.4F, SLAM_SHAKE = 0.8F;
	static final double SLAM_SHAKE_RANGE = 14.0;
	/** It walks away from villagers and other Bogres closer than this (blocks), this far each time. */
	static final double COMPANY_RANGE = 20.0;
	static final int AWAY_DISTANCE = 16, AWAY_HEIGHT = 7;
	/** Spawning: never within this of another Bogre or a village (blocks); its weight among the monsters, one at a time. */
	static final double SPAWN_APART = 96.0;
	public static final int SPAWN_WEIGHT = 2;
	/**
	 * Sleep: on a night with no foe it lies down now and then (a chance each second); anyone this close wakes it (sneak
	 * past and they can get this close instead), as does daybreak or a blow.
	 */
	static final float SLEEP_CHANCE = 0.05F;
	static final double WAKE_RANGE = 7.0, WAKE_RANGE_SNEAKING = 2.5;
	/** A jukebox it can hear: it dances while one plays this close (blocks). */
	static final double DANCE_RANGE = 12.0;
	/** Its death animation runs this long (ticks) before it is gone. */
	static final int DEATH_TICKS = 30;

	/** What it is doing, for the client's animations. */
	enum Mood { CALM, SLEEPING, DANCING, ROARING, SLAMMING }

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation RUN_ANIM = RawAnimation.begin().thenLoop("run");
	static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleeping_loop");
	static final RawAnimation DANCE = RawAnimation.begin().thenPlay("dance start").thenLoop("dance");
	static final RawAnimation ROAR = RawAnimation.begin().thenPlay("roar");
	static final RawAnimation SLAM = RawAnimation.begin().thenPlay("attack");
	static final RawAnimation HURT = RawAnimation.begin().thenPlay("taking_damage");
	static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death_anim");

	private static final EntityDataAccessor<Integer> MOOD = SynchedEntityData.defineId(Bogre.class, EntityDataSerializers.INT);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** (Server) who it has warned, and when (game time). */
	private final Map<UUID, Long> warned = new HashMap<>();
	/** (Server) ticks left of its roar, and of its slam's swing; ticks until it may swing again. */
	private int roarLeft, swingLeft, slamCooldown;
	/** (Server) the foe it had last tick, so a new one gets a roar first. */
	private @Nullable LivingEntity lastTarget;
	/** A jukebox playing near it (set by the jukebox, both sides). */
	private @Nullable BlockPos jukebox;

	public Bogre(EntityType<? extends Bogre> type, Level level) {
		super(type, level);
		this.xpReward = 40;
		this.setPersistenceRequired();
		this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 16.0F);
		this.setPathfindingMalus(PathType.FIRE, 16.0F);
		this.setPathfindingMalus(PathType.LAVA, -1.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.ARMOR, ARMOR).add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE).add(Attributes.FOLLOW_RANGE, FOLLOW)
			.add(Attributes.ATTACK_DAMAGE, SLAM_DAMAGE).add(Attributes.STEP_HEIGHT, 1.5);
	}

	/** Its natural spawn: on the ground of its swamp, alone (no other Bogre near, no village near). */
	public static boolean checkSpawn(EntityType<Bogre> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		if (!(level instanceof ServerLevel server) || pos.getY() < level.getSeaLevel() - 2 || !level.getBlockState(pos.below()).isSolid()) {
			return false;
		}
		AABB around = new AABB(pos).inflate(SPAWN_APART);
		return server.getEntitiesOfClass(Bogre.class, around, Entity::isAlive).isEmpty()
			&& server.getEntitiesOfClass(AbstractVillager.class, around.deflate(SPAWN_APART / 2), Entity::isAlive).isEmpty()
			&& !server.isVillage(pos);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(MOOD, Mood.CALM.ordinal());
	}

	Mood mood() {
		return Mood.values()[Mth.clamp(this.entityData.get(MOOD), 0, Mood.values().length - 1)];
	}

	private void setMood(Mood mood) {
		this.entityData.set(MOOD, mood.ordinal());
	}

	/** It is standing still for something of its own (asleep, dancing, roaring or mid-swing): no wandering, no chase. */
	boolean busy() {
		return this.mood() != Mood.CALM;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new ChaseGoal());
		this.goalSelector.addGoal(4, new KeepToItselfGoal());
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, WANDER) {
			@Override
			public boolean canUse() {
				return !Bogre.this.busy() && Bogre.this.getTarget() == null && super.canUse();
			}
		});
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F) {
			@Override
			public boolean canUse() {
				return Bogre.this.mood() != Mood.SLEEPING && super.canUse();
			}
		});
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return Bogre.this.mood() == Mood.CALM && super.canUse();
			}
		});
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.slamCooldown > 0) {
			this.slamCooldown--;
		}
		this.forgetFoe();
		// one living in an orc camp walks back to its fire when it has strayed and has no foe
		if (this.getTarget() == null && this.hasHome() && !this.isWithinHome() && this.tickCount % 40 == 0 && !this.busy()) {
			BlockPos home = this.getHomePosition();
			this.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
		}
		if (this.roarLeft > 0) {
			this.roaring(level);
		} else if (this.swingLeft > 0) {
			this.swinging(level);
		} else if (this.getTarget() != null && this.getTarget() != this.lastTarget) {
			// a new foe: it roars at it first
			this.roar(level);
		} else if (this.getTarget() == null) {
			this.calm(level);
		} else if (this.mood() != Mood.CALM) {
			this.setMood(Mood.CALM);
		}
		this.lastTarget = this.getTarget();
	}

	/** It gives up a foe that is gone, far off, or no fair game (creative, spectating). */
	private void forgetFoe() {
		LivingEntity foe = this.getTarget();
		if (foe != null && (!foe.isAlive() || this.distanceTo(foe) > FORGET_RANGE || foe instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			this.setTarget(null);
		}
	}

	/** No foe: it warns or takes on the armed, sleeps, wakes or dances. */
	private void calm(ServerLevel level) {
		Mood mood = this.mood();
		if (this.tickCount % 10 == 0 && mood != Mood.SLEEPING) {
			Player armed = this.nearestArmed(level);
			if (armed != null) {
				Long when = this.warned.get(armed.getUUID());
				if (when == null || level.getGameTime() - when > WARN_MEMORY) {
					this.warned.put(armed.getUUID(), level.getGameTime());
					this.roar(level);
					this.getLookControl().setLookAt(armed, 60.0F, 60.0F);
					return;
				}
				if (this.distanceTo(armed) <= ANGER_RANGE) {
					this.setTarget(armed);
					return;
				}
			}
		}
		if (mood == Mood.SLEEPING) {
			this.getNavigation().stop();
			if (level.isBrightOutside() || this.someoneClose(level)) {
				this.setMood(Mood.CALM);
			}
			return;
		}
		if (this.jukebox != null && this.jukebox.closerToCenterThan(this.position(), DANCE_RANGE)) {
			this.getNavigation().stop();
			this.setMood(Mood.DANCING);
			return;
		}
		if (mood != Mood.CALM) {
			this.setMood(Mood.CALM);
		}
		if (this.tickCount % 20 == 0 && !level.isBrightOutside() && this.onGround() && !this.isInWater()
			&& this.random.nextFloat() < SLEEP_CHANCE && !this.someoneClose(level)) {
			this.getNavigation().stop();
			this.setMood(Mood.SLEEPING);
		}
	}

	/** The nearest player it can see with a weapon in hand, in warning range (creative and spectating ones never count). */
	private @Nullable Player nearestArmed(ServerLevel level) {
		Player best = null;
		double bestDist = Double.MAX_VALUE;
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(WARN_RANGE),
			p -> !p.isCreative() && !p.isSpectator() && p.isAlive() && armed(p) && !com.jaymoskingdoms.OgreHolds.spares(this, p))) {
			double d = this.distanceToSqr(player);
			if (d < bestDist && d <= WARN_RANGE * WARN_RANGE && this.hasLineOfSight(player)) {
				best = player;
				bestDist = d;
			}
		}
		return best;
	}

	/** Someone close enough to wake it (closer for a sneaking player). */
	private boolean someoneClose(ServerLevel level) {
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(WAKE_RANGE), p -> !p.isSpectator())) {
			double range = player.isShiftKeyDown() ? WAKE_RANGE_SNEAKING : WAKE_RANGE;
			if (this.distanceToSqr(player) <= range * range) {
				return true;
			}
		}
		return false;
	}

	/** A weapon: a sword, axe, spear, bow, crossbow or trident, or anything else that hits harder than a stone tool. */
	static boolean armed(Player player) {
		return weapon(player.getMainHandItem()) || weapon(player.getOffhandItem());
	}

	private static boolean weapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || stack.getItem() instanceof BowItem
			|| stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem) {
			return true;
		}
		double[] damage = {0.0};
		stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
			.forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
				if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
					damage[0] += modifier.amount();
				}
			});
		return damage[0] > 4.0;
	}

	/** It stands and roars: a warning, or before it fights a new foe. */
	private void roar(ServerLevel level) {
		this.roarLeft = ROAR_TICKS;
		this.swingLeft = 0;
		this.getNavigation().stop();
		this.setSprinting(false);
		this.setMood(Mood.ROARING);
		this.triggerAnim("action", "roar");
		this.playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F + this.random.nextFloat() * 0.1F);
	}

	private void roaring(ServerLevel level) {
		this.getNavigation().stop();
		if (this.getTarget() != null) {
			this.getLookControl().setLookAt(this.getTarget(), 60.0F, 60.0F);
		}
		if (ROAR_TICKS - this.roarLeft == ROAR_SHAKE_AT) {
			PowersCompat.shakeAround(level, this.getEyePosition(), ROAR_SHAKE_RANGE, ROAR_SHAKE);
			Vec3 mouth = this.getEyePosition().add(this.getLookAngle().scale(1.5));
			level.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z, 16, 0.4, 0.3, 0.4, 0.08);
		}
		if (--this.roarLeft <= 0) {
			this.setMood(Mood.CALM);
		}
	}

	/** It brings the hammer down on its foe: the swing, then the slam ({@link #slam}). */
	void swing() {
		this.swingLeft = SLAM_SWING_TICKS;
		this.slamCooldown = SLAM_COOLDOWN;
		this.getNavigation().stop();
		this.setSprinting(false);
		this.setMood(Mood.SLAMMING);
		LivingEntity foe = this.getTarget();
		if (foe != null) {
			float yaw = (float) (Mth.atan2(foe.getZ() - this.getZ(), foe.getX() - this.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(yaw);
			this.setYHeadRot(yaw);
			this.yBodyRot = yaw;
		}
		this.triggerAnim("action", "attack");
		this.playSound(SoundEvents.RAVAGER_ATTACK, 2.0F, 0.6F);
	}

	private void swinging(ServerLevel level) {
		this.getNavigation().stop();
		if (SLAM_SWING_TICKS - this.swingLeft == SLAM_HIT_TICK) {
			this.slam(level);
		}
		if (--this.swingLeft <= 0) {
			this.setMood(Mood.CALM);
		}
	}

	/**
	 * The hammer lands in front of it: everyone on the ground in {@link #SLAM_RADIUS} of that spot (not jumping, flying or
	 * riding) is hurt, less towards the edge, and thrown up and away; dust and the ground's bits fly in a ring.
	 */
	private void slam(ServerLevel level) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, this.getYRot()).scale(SLAM_AHEAD);
		Vec3 at = this.position().add(ahead);
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), this.getSoundSource(), 1.2F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_STEP, this.getSoundSource(), 3.0F, 0.4F);
		PowersCompat.shakeAround(level, at, SLAM_SHAKE_RANGE, SLAM_SHAKE);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 60, 1.5, 0.1, 1.5, 0.3);
		}
		for (int i = 0; i < 24; i++) {
			double angle = i * Mth.TWO_PI / 24;
			level.sendParticles(ParticleTypes.POOF, at.x + Math.cos(angle) * SLAM_RADIUS * 0.6, at.y + 0.2, at.z + Math.sin(angle) * SLAM_RADIUS * 0.6,
				1, 0.2, 0.05, 0.2, 0.05);
		}
		AABB box = new AABB(at.x - SLAM_RADIUS, at.y - 2.0, at.z - SLAM_RADIUS, at.x + SLAM_RADIUS, at.y + 2.0, at.z + SLAM_RADIUS);
		for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && !(e instanceof Bogre) && e.isAlive()
			&& e.onGround() && !e.isPassenger() && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			double d = Math.sqrt(Mth.square(hit.getX() - at.x) + Mth.square(hit.getZ() - at.z));
			if (d > SLAM_RADIUS) {
				continue;
			}
			float share = 1.0F - (1.0F - SLAM_EDGE) * (float) (d / SLAM_RADIUS);
			hit.hurtServer(level, this.damageSources().mobAttack(this), SLAM_DAMAGE * share);
			Vec3 away = d < 0.01 ? ahead.normalize() : new Vec3(hit.getX() - at.x, 0.0, hit.getZ() - at.z).scale(1.0 / d);
			hit.push(away.x * SLAM_JOLT_AWAY * share, SLAM_JOLT_UP * share, away.z * SLAM_JOLT_AWAY * share);
			hit.needsSync = true;
		}
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		// its only blow is the slam, which the chase starts ({@link ChaseGoal})
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && this.isAlive()) {
			if (this.mood() == Mood.SLEEPING || this.mood() == Mood.DANCING) {
				this.setMood(Mood.CALM);
			}
			if (this.swingLeft <= 0 && this.roarLeft <= 0) {
				this.triggerAnim("hurt", "hurt");
			}
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		this.roarLeft = 0;
		this.swingLeft = 0;
	}

	/** Its death animation (1.5 s) plays out before it is gone; vanilla's fall is a second. */
	@Override
	protected void tickDeath() {
		if (++this.deathTime >= DEATH_TICKS && this.level() instanceof ServerLevel level && !this.isRemoved()) {
			level.broadcastEntityEvent(this, (byte) 60);
			this.remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	public void setRecordPlayingNearby(BlockPos pos, boolean playing) {
		this.jukebox = playing ? pos : null;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Bogre>("move", 5, test -> {
			Bogre bogre = test.animatable();
			if (bogre.isDeadOrDying()) {
				return PlayState.STOP;
			}
			switch (bogre.mood()) {
				case SLEEPING -> {
					return test.setAndContinue(SLEEP);
				}
				case DANCING -> {
					return test.setAndContinue(DANCE);
				}
				case ROARING, SLAMMING -> {
					return test.setAndContinue(IDLE);
				}
				default -> {
				}
			}
			if (test.isMoving()) {
				return test.setAndContinue(bogre.isSprinting() ? RUN_ANIM : WALK);
			}
			return test.setAndContinue(IDLE);
		}));
		controllers.add(new AnimationController<Bogre>("action", test -> PlayState.STOP).triggerableAnim("roar", ROAR)
			.triggerableAnim("attack", SLAM));
		controllers.add(new AnimationController<Bogre>("hurt", test -> PlayState.STOP).triggerableAnim("hurt", HURT));
		controllers.add(new AnimationController<Bogre>("death", 0, test -> test.animatable().isDeadOrDying() ? test.setAndContinue(DEATH) : PlayState.STOP));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.mood() == Mood.SLEEPING ? SoundEvents.SNIFFER_IDLE : SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.65F;
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
		this.playSound(SoundEvents.RAVAGER_STEP, 0.8F, 0.6F);
	}

	@Override
	public int getMaxHeadYRot() {
		return 120;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("asleep", this.mood() == Mood.SLEEPING);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		if (input.getBooleanOr("asleep", false)) {
			this.setMood(Mood.SLEEPING);
		}
	}

	/** It runs at its foe and swings when close enough (the warning roar and the swing itself stand it still). */
	final class ChaseGoal extends Goal {
		ChaseGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return Bogre.this.getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			Bogre.this.setSprinting(false);
			Bogre.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity foe = Bogre.this.getTarget();
			if (foe == null) {
				return;
			}
			Bogre.this.getLookControl().setLookAt(foe, 60.0F, 60.0F);
			if (Bogre.this.busy()) {
				return;
			}
			double d = Bogre.this.distanceTo(foe);
			if (d <= SLAM_REACH) {
				Bogre.this.getNavigation().stop();
				Bogre.this.setSprinting(false);
				if (Bogre.this.slamCooldown <= 0) {
					Bogre.this.swing();
				}
			} else {
				Bogre.this.setSprinting(true);
				if (Bogre.this.tickCount % 5 == 0 || Bogre.this.getNavigation().isDone()) {
					Bogre.this.getNavigation().moveTo(foe, RUN);
				}
			}
		}
	}

	/** It keeps to itself: villagers or another Bogre near, and it walks off away from them. */
	final class KeepToItselfGoal extends Goal {
		private @Nullable Vec3 to;

		KeepToItselfGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			// (one living in an orc camp keeps to the camp instead: its orcs and ogres are its company)
			if (Bogre.this.hasHome() || Bogre.this.busy() || Bogre.this.getTarget() != null || Bogre.this.random.nextInt(reducedTickDelay(40)) != 0) {
				return false;
			}
			AABB around = Bogre.this.getBoundingBox().inflate(COMPANY_RANGE);
			Entity company = null;
			double nearest = Double.MAX_VALUE;
			for (LivingEntity e : Bogre.this.level().getEntitiesOfClass(LivingEntity.class, around,
				e -> e != Bogre.this && e.isAlive() && (e instanceof Bogre || e instanceof AbstractVillager))) {
				double d = Bogre.this.distanceToSqr(e);
				if (d < nearest) {
					nearest = d;
					company = e;
				}
			}
			if (company == null) {
				return false;
			}
			this.to = DefaultRandomPos.getPosAway(Bogre.this, AWAY_DISTANCE, AWAY_HEIGHT, company.position());
			return this.to != null;
		}

		@Override
		public void start() {
			if (this.to != null) {
				Bogre.this.getNavigation().moveTo(this.to.x, this.to.y, this.to.z, 1.0);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return !Bogre.this.busy() && Bogre.this.getTarget() == null && !Bogre.this.getNavigation().isDone();
		}
	}
}
