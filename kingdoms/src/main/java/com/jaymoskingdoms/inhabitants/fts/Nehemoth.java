package com.jaymoskingdoms.inhabitants.fts;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.Fx;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Nehemoth: a hulking beast out of the demons' country that has come up into the Nether. It roars once at each
 * thing it meets, swipes with its arms, leaps at what keeps its distance and lands with a stomp; the fire kind seizes
 * what is small enough and bites (and heals on it), the soul kind of the soul sand valleys charges a beam of souls.
 * Hostile to everything alive in the Nether. Ported from From the Shadows Reborn 3.0 (Sonmok14, L_Ender; AGPL v3): its
 * model, textures, animations and sounds are theirs (licenses/from-the-shadows/CREDITS-nether-beasts.md); the logic
 * and numbers are theirs, the code is ours (Fabric 26.3, one hitbox in place of its multipart one).
 *
 * <p>What it is doing is a synced {@link Act} with an age in ticks: the server runs the act's beats by age, and the
 * client plays the act's animation and shakes the camera from the same two values.
 */
public class Nehemoth extends Monster implements GeoEntity, NetherBeast {
	static final double HEALTH = 150.0, SPEED = 0.35, DAMAGE = 10.0, ARMOR = 10.0, TOUGHNESS = 5.0, FOLLOW = 26.0;
	/** Rare in the wild: its weight among the Nether's monsters, the chance a spawn goes ahead, and the blocks it keeps from its own kind. */
	public static final int SPAWN_WEIGHT = 1;
	static final int SPAWN_ONE_IN = 3;
	static final double SPAWN_APART = 96.0;
	/** Ticks until it may bite, leap or charge its beam again, and the breath between one act and the next. */
	static final int BITE_COOLDOWN = 250, LEAP_COOLDOWN = 200, BEAM_COOLDOWN = 250, GAP = 8;
	/** Its reach (blocks, centre to the edge of the foe) for a swipe and for a bite; a swipe's arc (cosine of its half-angle) and height. */
	static final double SWIPE_REACH = 3.0, BITE_REACH = 3.0, SWIPE_HEIGHT = 2.5;
	static final double SWIPE_ARC = 0.35;
	/** What a swipe does to the one it hits: pushes it away and up. */
	static final double SWIPE_PUSH = 0.6, SWIPE_UP = 0.25;
	/** A landing's damage to all within this many blocks, how long it disables a shield, and a leap's top speed (blocks a tick) and range of choice. */
	static final float LAND_DAMAGE = 3.0F;
	static final double LAND_RADIUS = 3.0, LEAP_MAX_SPEED = 2.0, LEAP_MIN = 5.0, LEAP_VELOCITY = 0.21, LEAP_UP = 0.9;
	static final int SHIELD_DOWN = 100;
	/** The roar: how far it pushes things (blocks), how far it is heard as meeting a witness (blocks). */
	static final double ROAR_RADIUS = 8.0, WITNESS_RADIUS = 40.0;
	/** The soul beam: damage a hit, seconds of fire it leaves, how far it reaches and how wide it is (blocks). */
	static final float BEAM_DAMAGE = 3.0F;
	static final float BEAM_FIRE_SECONDS = 5.0F;
	static final double BEAM_RANGE = 30.0, BEAM_WIDTH = 0.7;
	static final int BEAM_CHARGE_FROM = 3, BEAM_FIRES_FROM = 23, BEAM_FIRES_TO = 34;
	/** The bite's beats (ticks into it). */
	static final int BITE_WARN = 1, BITE_LUNGE = 12, BITE_SEIZE = 16, BITE_GIVE_UP = 20, BITE_SOUND = 34, BITE_HURT = 37, BITE_LET_GO = 42;
	/** Where the seized one is held: blocks ahead of it and up from its feet. */
	static final double HOLD_AHEAD = 1.1, HOLD_UP = 1.6;

	/** Its soul kind's colour for the beam. */
	static final int BEAM_COLOUR = 0x5FE6F0;

	/** What it is doing, and for how many ticks it takes (the leap lasts until it lands). */
	public enum Act {
		NONE(0), SWIPE_TWICE(60), SWIPE_ONCE(46), BITE(58), ROAR(58), LEAP(80), LAND(23), BEAM(43), GUARD(20);

		final int ticks;

		Act(int ticks) {
			this.ticks = ticks;
		}
	}

	static final String P = "animation.nehemoth.";
	static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
	static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
	static final RawAnimation CLIMB_NONE = RawAnimation.begin().thenLoop(P + "none");
	static final RawAnimation EYE_GLOW = RawAnimation.begin().thenLoop(P + "eyeglow");
	static final RawAnimation GROWL = RawAnimation.begin().thenLoop(P + "growl");
	static final RawAnimation LEFT_MELEE = RawAnimation.begin().thenPlayAndHold(P + "left_melee");
	static final RawAnimation RIGHT_MELEE = RawAnimation.begin().thenPlayAndHold(P + "right_melee");
	static final RawAnimation LEFT_ONCE = RawAnimation.begin().thenPlayAndHold(P + "left_melee_once");
	static final RawAnimation RIGHT_ONCE = RawAnimation.begin().thenPlayAndHold(P + "right_melee_once");
	static final RawAnimation BITE_ANIM = RawAnimation.begin().thenPlayAndHold(P + "bite");
	static final RawAnimation ROAR_ANIM = RawAnimation.begin().thenLoop(P + "roar");
	static final RawAnimation SMASH_START = RawAnimation.begin().thenLoop(P + "smash_start");
	static final RawAnimation JUMP = RawAnimation.begin().thenLoop(P + "jump");
	static final RawAnimation SMASH = RawAnimation.begin().thenLoop(P + "smash");
	static final RawAnimation BEAM_READY = RawAnimation.begin().thenLoop(P + "breathready");
	static final RawAnimation BEAM_ANIM = RawAnimation.begin().thenLoop(P + "breath");
	static final RawAnimation GUARD_ANIM = RawAnimation.begin().thenLoop(P + "guard");

	private static final EntityDataAccessor<Integer> ACT = SynchedEntityData.defineId(Nehemoth.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> RIGHT = SynchedEntityData.defineId(Nehemoth.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(Nehemoth.class, EntityDataSerializers.INT);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** Ticks into the current act (both sides count it; the client restarts it when the act changes). */
	private int actAge;
	private int biteCooldown, leapCooldown, beamCooldown, gap;
	/** (Server) the yaw it holds through a roar, and who it has already roared at. */
	private float roarYaw;
	private final Set<UUID> met = new HashSet<>();
	/** Its targetable parts (never in the world: the level hands them out near it), and whether the blow now landing came through one. */
	private final NehemothPart[] parts;
	private boolean viaPart;

	public Nehemoth(EntityType<? extends Nehemoth> type, Level level) {
		super(type, level);
		this.xpReward = 30;
		this.setPathfindingMalus(PathType.LAVA, 8.0F);
		this.setPathfindingMalus(PathType.FIRE, 0.0F);
		this.setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 0.0F);
		NehemothPart.Kind[] kinds = NehemothPart.Kind.values();
		this.parts = new NehemothPart[kinds.length];
		for (int i = 0; i < kinds.length; i++) {
			this.parts[i] = new NehemothPart(this, kinds[i]);
		}
	}

	public NehemothPart[] parts() {
		return this.parts;
	}

	/** Its parts take the ids just after its own, as the ender dragon's do, so the client and the server name the same one. */
	@Override
	public void recreateFromPacket(ClientboundAddEntityPacket packet) {
		super.recreateFromPacket(packet);
		for (int i = 0; i < this.parts.length; i++) {
			this.parts[i].setId(packet.getId() + 1 + i);
		}
	}

	/** Its own body takes no aim (a crosshair and an arrow find its parts instead). */
	@Override
	public boolean isPickable() {
		return false;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ATTACK_DAMAGE, DAMAGE).add(Attributes.ATTACK_KNOCKBACK, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.ARMOR, ARMOR).add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	/** Its natural spawn: rare, in the Nether, and none of its kind within a hundred blocks. */
	public static boolean checkSpawn(EntityType<Nehemoth> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random) {
		if (EntitySpawnReason.isSpawner(reason)) {
			return true;
		}
		if (!(level instanceof ServerLevel server) || server.dimension() != Level.NETHER || random.nextInt(SPAWN_ONE_IN) != 0
			|| !Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) {
			return false;
		}
		return server.getEntitiesOfClass(Nehemoth.class, new AABB(pos).inflate(SPAWN_APART), Entity::isAlive).isEmpty();
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

	/** 0: the fire kind (the demon realm's), 1: the soul kind (the soul sand valleys'). */
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
		this.entityData.set(VARIANT, level.getBiome(this.blockPosition()).is(Biomes.SOUL_SAND_VALLEY) ? 1 : 0);
		return super.finalizeSpawn(level, difficulty, reason, data);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new FightGoal());
		this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.5, 0.02F));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this, NetherBeast.class).setAlertOthers(Nehemoth.class));
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
		for (NehemothPart part : this.parts) {
			part.place(this.getX(), this.getY(), this.getZ(), this.yBodyRot);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.biteCooldown > 0) {
			this.biteCooldown--;
		}
		if (this.leapCooldown > 0) {
			this.leapCooldown--;
		}
		if (this.beamCooldown > 0) {
			this.beamCooldown--;
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
		LivingEntity foe = this.getTarget();
		if (act == Act.ROAR && foe != null) {
			this.roarYaw = this.yawTo(foe);
			this.met.add(foe.getUUID());
			for (LivingEntity witness : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(WITNESS_RADIUS),
				e -> e != this && e.isAlive())) {
				this.met.add(witness.getUUID());
			}
		} else if (act == Act.BITE) {
			this.biteCooldown = BITE_COOLDOWN;
		} else if (act == Act.LEAP) {
			this.leapCooldown = LEAP_COOLDOWN;
		} else if (act == Act.BEAM) {
			this.beamCooldown = BEAM_COOLDOWN;
		}
	}

	private void finish() {
		this.setAct(Act.NONE);
		this.gap = GAP;
		this.ejectPassengers();
	}

	private float yawTo(Entity foe) {
		return (float)(Mth.atan2(foe.getZ() - this.getZ(), foe.getX() - this.getX()) * 180.0 / Math.PI) - 90.0F;
	}

	/** Turns it, head and body, toward {@code foe}. */
	private void face(Entity foe, float speed) {
		float yaw = Mth.approachDegrees(this.getYRot(), this.yawTo(foe), speed);
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.setYHeadRot(yaw);
	}

	private void lunge(double power) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, this.getYRot());
		this.push(ahead.x * power, 0.0, ahead.z * power);
	}

	private void acting(ServerLevel level, Act act) {
		LivingEntity foe = this.getTarget();
		if (act != Act.LEAP && act != Act.LAND) {
			this.getNavigation().stop();
		}
		int age = this.actAge;
		switch (act) {
			case SWIPE_TWICE -> {
				if (foe != null && age < 41) {
					this.face(foe, 40.0F);
				}
				if (age == 17 || age == 35) {
					this.lunge(0.3);
				}
				if (age == 19 || age == 41) {
					this.swipe(level);
				}
			}
			case SWIPE_ONCE -> {
				if (foe != null && age < 19) {
					this.face(foe, 40.0F);
				}
				if (age == 17) {
					this.lunge(0.3);
				}
				if (age == 19) {
					this.swipe(level);
				}
			}
			case BITE -> this.biting(level, foe, age);
			case ROAR -> this.roaring(level, age);
			case LEAP -> this.leaping(level, foe, age);
			case BEAM -> this.beaming(level, foe, age);
			case GUARD -> {
				if (foe != null && foe.isAlive()) {
					this.face(foe, 30.0F);
				}
			}
			default -> {
			}
		}
		if (act != Act.LEAP && age >= act.ticks - 1 || act == Act.LEAP && age >= act.ticks) {
			this.finish();
		}
	}

	/** A swipe: whatever stands in the arc ahead of it is hurt, torn, and thrown back. */
	private void swipe(ServerLevel level) {
		this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.5F + this.random.nextFloat() * 0.1F);
		this.playSound(NetherBeasts.NEHEMOTH_ATTACK, 1.5F, 0.9F);
		Vec3 ahead = Vec3.directionFromRotation(0.0F, this.getYRot());
		float damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(SWIPE_REACH, SWIPE_HEIGHT, SWIPE_REACH),
			NetherBeasts::fair)) {
			Vec3 to = new Vec3(e.getX() - this.getX(), 0.0, e.getZ() - this.getZ());
			double d = to.length();
			if (d > SWIPE_REACH + e.getBbWidth() / 2.0 || d > 0.001 && to.scale(1.0 / d).dot(ahead) < SWIPE_ARC) {
				continue;
			}
			boolean blocking = e.isBlocking();
			if (NetherBeasts.tear(level, this, e, damage) && !blocking) {
				e.playSound(NetherBeasts.NEHEMOTH_GORE, 1.0F, 1.0F + this.random.nextFloat() * 0.1F);
			}
			if (d > 0.001) {
				e.push(to.x / d * SWIPE_PUSH, SWIPE_UP, to.z / d * SWIPE_PUSH);
			}
		}
	}

	private void biting(ServerLevel level, @Nullable LivingEntity foe, int age) {
		boolean held = !this.getPassengers().isEmpty();
		if (foe != null && age < BITE_SEIZE) {
			this.face(foe, 40.0F);
		}
		if (age == BITE_WARN) {
			this.playSound(NetherBeasts.NEHEMOTH_BITE_WARN, 2.0F, 0.6F + this.random.nextFloat() * 0.1F);
		}
		if (age == BITE_LUNGE) {
			this.lunge(0.5);
		}
		if (age == BITE_SEIZE && foe != null && this.distanceTo(foe) < BITE_REACH + 0.5 && this.hasLineOfSight(foe) && !foe.isBlocking()
			&& foe.getBbWidth() < this.getBbWidth() && !foe.isShiftKeyDown()) {
			foe.startRiding(this);
		}
		if (age == BITE_GIVE_UP && !held && this.getPassengers().isEmpty()) {
			this.finish();
			return;
		}
		if (age == BITE_SOUND && held) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), NetherBeasts.NEHEMOTH_BITE, this.getSoundSource(), 2.0F,
				0.8F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
		}
		if (age == BITE_HURT && held) {
			float damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
			for (Entity rider : new ArrayList<>(this.getPassengers())) {
				if (rider instanceof LivingEntity victim && NetherBeasts.tear(level, this, victim, damage)) {
					// the original also puts Heal Block on it; that effect is a later track's (see the change note)
					this.heal(damage / 2.0F);
				}
			}
		}
		if (age == BITE_LET_GO) {
			this.ejectPassengers();
		}
	}

	private void roaring(ServerLevel level, int age) {
		this.setYRot(this.roarYaw);
		this.yBodyRot = this.roarYaw;
		this.setYHeadRot(this.roarYaw);
		if (age == 8) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), NetherBeasts.NEHEMOTH_ROAR, SoundSource.HOSTILE, 4.0F, 0.9F);
		}
		if (age > 8 && age < 52) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(ROAR_RADIUS, 1.0, ROAR_RADIUS),
				NetherBeasts::fair)) {
				this.shove(e);
			}
		}
	}

	/** The original's strong knockback: away along the ground by one over the square of the distance, and a little up. */
	private void shove(Entity e) {
		double dx = e.getX() - this.getX(), dz = e.getZ() - this.getZ();
		double d2 = Math.max(dx * dx + dz * dz, 0.001);
		e.push(dx / d2, 0.1, dz / d2);
	}

	private void leaping(ServerLevel level, @Nullable LivingEntity foe, int age) {
		if (foe != null && age < 9) {
			this.face(foe, 90.0F);
		}
		if (age == 2) {
			this.lunge(0.4);
		}
		if (age == 9 && foe != null) {
			this.playSound(NetherBeasts.NEHEMOTH_CHARGE, 2.0F, 0.7F + this.random.nextFloat() * 0.1F);
			double vx = (foe.getX() - this.getX()) * LEAP_VELOCITY, vz = (foe.getZ() - this.getZ()) * LEAP_VELOCITY;
			double speed = Math.sqrt(vx * vx + vz * vz);
			if (speed > LEAP_MAX_SPEED) {
				vx *= LEAP_MAX_SPEED / speed;
				vz *= LEAP_MAX_SPEED / speed;
			}
			this.setDeltaMovement(vx, LEAP_UP, vz);
		}
		if (age > 10 && this.onGround()) {
			this.land(level);
		}
	}

	/** It comes down: everything near is hurt and thrown, a shield is knocked from a hand, the ground rings. */
	private void land(ServerLevel level) {
		this.setAct(Act.LAND);
		this.leapCooldown = LEAP_COOLDOWN;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(LAND_RADIUS), NetherBeasts::fair)) {
			e.hurtServer(level, level.damageSources().mobAttack(this), LAND_DAMAGE);
			NetherBeasts.breakShield(e, SHIELD_DOWN);
			double dx = e.getX() - this.getX(), dz = e.getZ() - this.getZ();
			double d2 = Math.max(dx * dx + dz * dz, 0.001);
			e.push(dx / d2, 0.1, dz / d2);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F,
			0.2F + this.random.nextFloat() * 0.1F);
		BlockState under = level.getBlockState(this.blockPosition().below());
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), this.getX(), this.getY() + 0.2, this.getZ(), 40, 1.5, 0.2, 1.5, 0.2);
		level.sendParticles(this.variant() == 1 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, this.getX(), this.getY() + 0.3, this.getZ(),
			40, 1.5, 0.2, 1.5, 0.05);
		Fx.ring(level, this.position().add(0.0, 0.1, 0.0), (float)LAND_RADIUS + 1.0F, this.variant() == 1 ? BEAM_COLOUR : 0xFF8A2A);
	}

	private Vec3 muzzle() {
		return this.getEyePosition().add(this.getViewVector(1.0F).scale(1.0));
	}

	private void beaming(ServerLevel level, @Nullable LivingEntity foe, int age) {
		if (age == 1) {
			this.playSound(NetherBeasts.NEHEMOTH_BEAM_READY, 2.0F, 0.3F + this.random.nextFloat() * 0.1F);
		}
		if (foe != null && foe.isAlive()) {
			this.getLookControl().setLookAt(foe.getX(), foe.getY(0.5), foe.getZ(), 30.0F, 30.0F);
			this.yBodyRot = this.getYHeadRot();
		}
		if (age >= BEAM_CHARGE_FROM && age < BEAM_FIRES_FROM) {
			// the souls gather at its mouth, ever tighter
			Vec3 mouth = this.muzzle();
			double reach = 2.5 * (1.0 - (age - BEAM_CHARGE_FROM) / (double)(BEAM_FIRES_FROM - BEAM_CHARGE_FROM));
			level.sendParticles(ParticleTypes.SOUL, mouth.x + this.random.nextGaussian() * reach, mouth.y + this.random.nextGaussian() * reach,
				mouth.z + this.random.nextGaussian() * reach, 2, 0.0, 0.0, 0.0, 0.0);
		}
		if (age == 12) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), NetherBeasts.NEHEMOTH_BEAM, SoundSource.HOSTILE, 4.0F,
				1.0F + this.random.nextFloat() * 0.1F);
		}
		if (age >= BEAM_FIRES_FROM && age < BEAM_FIRES_TO) {
			this.fireBeam(level);
		}
	}

	/** The beam: a line of souls from its mouth along its gaze, stopped by the first block, hurting and burning what it crosses. */
	private void fireBeam(ServerLevel level) {
		Vec3 from = this.muzzle();
		Vec3 to = from.add(this.getViewVector(1.0F).scale(BEAM_RANGE));
		HitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		Fx.beam(level, from, end, 0.25F, BEAM_COLOUR, 3);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.05);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(BEAM_WIDTH + 1.0), NetherBeasts::fair)) {
			if (e.getBoundingBox().inflate(BEAM_WIDTH).clip(from, end).isPresent()
				&& e.hurtServer(level, level.damageSources().indirectMagic(this, this), BEAM_DAMAGE)) {
				e.igniteForSeconds(BEAM_FIRE_SECONDS);
			}
		}
	}

	/** A blow that landed on one of its parts: it counts for that part's share (the head twice, the neck one and a half, the rest half). */
	boolean hurtFromPart(NehemothPart part, ServerLevel level, DamageSource source, float amount) {
		this.viaPart = true;
		try {
			boolean hit = this.hurtServer(level, source, amount * part.kind.damageMultiplier);
			if (hit && part.kind == NehemothPart.Kind.HEAD) {
				this.playSound(NetherBeasts.NEHEMOTH_CRITICAL, 3.0F, 1.0F + this.random.nextFloat() * 0.1F);
			}
			return hit;
		} finally {
			this.viaPart = false;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Vec3 origin = source.getSourcePosition();
		if (!this.viaPart && origin != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			// not through a part (an explosion, a mob's blow): it counts for the part nearest where it came from, as in the original
			NehemothPart nearest = null;
			double best = Double.MAX_VALUE;
			for (NehemothPart part : this.parts) {
				double d = part.position().distanceToSqr(origin);
				if (d < best) {
					best = d;
					nearest = part;
				}
			}
			if (nearest != null) {
				return this.hurtFromPart(nearest, level, source, amount);
			}
		}
		if (this.act() == Act.GUARD && source.getEntity() instanceof LivingEntity attacker && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			Vec3 toAttacker = attacker.position().subtract(this.position()).normalize();
			if (toAttacker.dot(this.getLookAngle().normalize()) > 0.5) {
				// it takes the blow on its guard and is the readier for it
				this.playSound(NetherBeasts.NEHEMOTH_BITE_WARN, 2.0F, 0.5F + this.random.nextFloat() * 0.1F);
				this.biteCooldown = 0;
				this.beamCooldown = 0;
				return false;
			}
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			amount /= 6.0F;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected int calculateFallDamage(double fallDistance, float damageModifier) {
		return 0;
	}

	@Override
	public void travel(Vec3 input) {
		Act act = this.act();
		super.travel(act == Act.NONE || act == Act.LEAP ? input : Vec3.ZERO);
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction move) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, this.getYRot());
		move.accept(passenger, this.getX() + ahead.x * HOLD_AHEAD, this.getY() + HOLD_UP, this.getZ() + ahead.z * HOLD_AHEAD);
	}

	// ---- the hunt --------------------------------------------------------------------------------------------------------

	/** It runs its foe down, and picks what to do when it is near: roar, bite, guard, charge the beam, leap, swipe. */
	final class FightGoal extends Goal {
		FightGoal() {
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			LivingEntity foe = Nehemoth.this.getTarget();
			return foe != null && foe.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			Nehemoth.this.setAggressive(false);
			Nehemoth.this.getNavigation().stop();
		}

		@Override
		public void tick() {
			Nehemoth self = Nehemoth.this;
			LivingEntity foe = self.getTarget();
			if (foe == null || self.act() != Act.NONE) {
				return;
			}
			self.setAggressive(true);
			self.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			if (self.gap > 0 || !self.onGround()) {
				return;
			}
			double d = self.distanceTo(foe);
			RandomSource random = self.random;
			if (!self.met.contains(foe.getUUID())) {
				self.start(Act.ROAR);
			} else if (self.variant() == 0 && d <= BITE_REACH && self.biteCooldown <= 0 && foe.getBbWidth() <= self.getBbWidth()
				&& self.getPassengers().isEmpty() && !foe.isShiftKeyDown()) {
				self.start(Act.BITE);
			} else if (d <= 5.0 && (foe.isSwinging() || self.hurtTime > 0) && random.nextInt(2) == 0) {
				self.start(Act.GUARD);
			} else if (self.variant() == 1 && self.beamCooldown <= 0 && d <= BEAM_RANGE && self.hasLineOfSight(foe) && random.nextInt(8) == 0) {
				self.start(Act.BEAM);
			} else if (self.leapCooldown <= 0 && d > LEAP_MIN && random.nextInt(2) == 0 && !self.horizontalCollision && self.roomToLeap()) {
				self.start(Act.LEAP);
			} else if (d <= SWIPE_REACH + foe.getBbWidth() / 2.0) {
				self.start(random.nextBoolean() ? Act.SWIPE_TWICE : Act.SWIPE_ONCE);
			} else if (self.tickCount % 5 == 0 || self.getNavigation().isDone()) {
				self.getNavigation().moveTo(foe, 1.35);
			}
		}
	}

	/** A leap needs open air over it: none of the seven blocks above its head solid. */
	private boolean roomToLeap() {
		BlockPos at = this.blockPosition();
		for (int y = 1; y <= 7; y++) {
			if (!this.level().getBlockState(at.above(y)).isAir()) {
				return false;
			}
		}
		return true;
	}

	// ---- looks and sounds ------------------------------------------------------------------------------------------------

	/** What its act looks like, for both sides (null: none, the walk or idle shows). */
	@Nullable RawAnimation actAnimation() {
		return switch (this.act()) {
			case NONE -> null;
			case SWIPE_TWICE -> this.isRight() ? RIGHT_MELEE : LEFT_MELEE;
			case SWIPE_ONCE -> this.isRight() ? RIGHT_ONCE : LEFT_ONCE;
			case BITE -> BITE_ANIM;
			case ROAR -> ROAR_ANIM;
			case LEAP -> this.onGround() && this.actAge < 15 ? SMASH_START : JUMP;
			case LAND -> SMASH;
			case BEAM -> this.actAge < 18 ? BEAM_READY : BEAM_ANIM;
			case GUARD -> GUARD_ANIM;
		};
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Nehemoth>("eyes", 5, test -> test.setAndContinue(EYE_GLOW)));
		controllers.add(new AnimationController<Nehemoth>("growl", 15, test -> {
			Nehemoth n = test.animatable();
			return n.isAlive() && n.act() == Act.NONE && !n.isAggressive() && n.tickCount % 150 < 60 ? test.setAndContinue(GROWL) : PlayState.STOP;
		}));
		controllers.add(new AnimationController<Nehemoth>("move", 6, test -> {
			Nehemoth n = test.animatable();
			if (n.act() != Act.NONE) {
				return test.setAndContinue(CLIMB_NONE);
			}
			if (test.isMoving()) {
				return test.setAndContinue(n.isAggressive() ? RUN : WALK);
			}
			return test.setAndContinue(IDLE);
		}));
		controllers.add(new AnimationController<Nehemoth>("action", 4, test -> {
			RawAnimation animation = test.animatable().actAnimation();
			return animation == null ? PlayState.STOP : test.setAndContinue(animation);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return NetherBeasts.NEHEMOTH_IDLE;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return NetherBeasts.NEHEMOTH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return NetherBeasts.NEHEMOTH_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 1.5F;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(NetherBeasts.NEHEMOTH_STOMP, 1.0F, 0.8F + this.random.nextFloat() * 0.1F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("variant", this.variant());
		output.store("met", UUIDUtil.CODEC.listOf(), new ArrayList<>(this.met));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(VARIANT, Mth.clamp(input.getIntOr("variant", 0), 0, 1));
		this.met.clear();
		List<UUID> met = input.read("met", UUIDUtil.CODEC.listOf()).orElse(List.of());
		this.met.addAll(met);
	}
}
