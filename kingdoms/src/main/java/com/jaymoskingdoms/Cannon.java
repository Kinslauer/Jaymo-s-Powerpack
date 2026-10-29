package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.64) A field cannon of one of the richest kingdoms', set on a wall, a tower or beside the gate ({@link Cannons}):
 * a wooden carriage on two wheels and an iron barrel. One of the kingdom's archers sits on its trail as its gunner; the
 * cannon swings round to his foe, raises its barrel for the distance and fires a {@link Cannonball} every so often,
 * from {@link #NEAREST} to {@link #FURTHEST} blocks (a foe closer than that gets his arrows). It doesn't move, nor block
 * anyone; players can break it with {@link #HEALTH} worth of blows. Should its kingdom grow poor, it's sold off.
 */
public final class Cannon extends Entity {
	/** It fires at a foe from this far to this far (blocks). */
	public static final double NEAREST = 7.0, FURTHEST = Folk.ARCHER_RANGE + 4.0;
	/** Between shots (ticks), plus up to {@link #JITTER} more. */
	static final int RELOAD = 160, JITTER = 60;
	/** How far the barrel swings round per tick (degrees), and how close to its mark it must be to fire. */
	static final float TRAVERSE = 6.0F, ON_MARK = 8.0F;
	/** How wide a shot may stray (blocks per block flown). */
	static final double SPREAD = 0.03;
	/** The barrel's pivot above the ground, and its muzzle's distance out from that (blocks). (0.7.326) Its trunnions sit
	 * this low on the Canon model's carriage. */
	public static final double PIVOT = 0.75, MUZZLE = 1.35;
	/** Its barrel can't be raised beyond this nor lowered below that (degrees). */
	static final float MAX_PITCH = 40.0F, MIN_PITCH = -20.0F;
	/** Blows it takes from players before it breaks. */
	static final float HEALTH = 40.0F;
	/** How often it looks for a gunner nearby, and how far (ticks, blocks); how often it checks its kingdom can keep it. */
	static final int CREW_CHECK = 40, UPKEEP_CHECK = 200;
	static final double CREW_REACH = 3.5;
	/** The entity event that kicks its barrel back on the client. */
	static final byte FIRED = 60;
	/** How long the barrel's kick lasts (ticks). */
	public static final int RECOIL_TICKS = 8;

	public static EntityType<Cannon> TYPE;

	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Cannon.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(Cannon.class, EntityDataSerializers.FLOAT);

	private int faction = -1;
	private @Nullable BlockPos post;
	private long next;
	private float damage;

	/** Client side: ticks since it last fired, and its barrel's pitch last tick. */
	public int sinceFired = RECOIL_TICKS;
	public float pitchO;

	public Cannon(EntityType<? extends Cannon> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("cannon"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Cannon>of(Cannon::new, MobCategory.MISC)
			.noLootTable()
			.sized(1.3F, 1.1F)
			.clientTrackingRange(10)
			.updateInterval(2)
			.build(key));
	}

	/** A new cannon of {@code faction}'s at {@code at}, facing {@code yaw}. */
	static Cannon place(ServerLevel level, Faction faction, BlockPos at, float yaw) {
		Cannon c = new Cannon(TYPE, level);
		c.faction = faction.id();
		c.post = at;
		c.entityData.set(COLOR, faction.styleColor(level.getServer()).getId());
		c.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		c.next = level.getGameTime() + RELOAD;
		level.addFreshEntity(c);
		return c;
	}

	public int colorId() {
		return this.entityData.get(COLOR);
	}

	/** The barrel's elevation (degrees, up is positive). */
	public float barrelPitch() {
		return this.entityData.get(PITCH);
	}

	int faction() {
		return this.faction;
	}

	@Nullable
	BlockPos post() {
		return this.post;
	}

	/** Its gunner (a folk archer, or (0.7.295) an MCA one), or null. */
	@Nullable
	PathfinderMob gunner() {
		return this.getFirstPassenger() instanceof PathfinderMob m && gunnerOf(m) ? m : null;
	}

	/** {@code e} is an archer (a folk or a villager) of its kingdom's. */
	private boolean gunnerOf(Entity e) {
		return e instanceof Worker w && w.role() == Folk.ARCHER && w.factionId() == this.faction;
	}

	/** An archer of its kingdom's, near it and on no cannon, who'd man it; or null. */
	@Nullable
	PathfinderMob crew(ServerLevel level, double reach) {
		for (Worker w : Workers.near(level, this.getBoundingBox().inflate(reach),
			w -> w.isAlive() && w.role() == Folk.ARCHER && w.factionId() == this.faction && !w.mob().isPassenger() && !w.mob().isSleeping())) {
			return w.mob();
		}
		return null;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public void tick() {
		super.tick();
		this.pitchO = this.barrelPitch();
		if (!(this.level() instanceof ServerLevel level)) {
			if (this.sinceFired < RECOIL_TICKS) {
				this.sinceFired++;
			}
			return;
		}
		long now = level.getGameTime();
		if ((now + this.getId()) % UPKEEP_CHECK == 0 && !Muskets.afford(Factions.get(level.getServer(), this.faction), Muskets.KEEP_TREASURY)) {
			// its kingdom can't keep it any more: sold
			this.ejectPassengers();
			this.discard();
			return;
		}
		PathfinderMob gunner = this.gunner();
		if (gunner instanceof Folk folk && (now + this.getId()) % CREW_CHECK == 0 && folk.getTarget() == null
			&& Factions.get(level.getServer(), this.faction) instanceof Faction f && McaGunner.seat(level, f, this)) {
			// (0.7.295) with MCA, its folk gunner hands his place to one of the realm's villagers
			folk.stopRiding();
			folk.discard();
			return;
		}
		if (gunner == null) {
			if ((now + this.getId()) % CREW_CHECK == 0) {
				PathfinderMob crew = this.crew(level, CREW_REACH);
				if (crew != null) {
					crew.startRiding(this);
				}
			}
			this.settle();
			return;
		}
		// ((0.7.295) a villager's foe is its brain's)
		LivingEntity foe = Rulers.target(gunner);
		if (foe == null || !foe.isAlive()) {
			this.settle();
			return;
		}
		Vec3 pivot = this.position().add(0.0, PIVOT, 0.0);
		Vec3 aimAt = foe.position().add(0.0, foe.getBbHeight() * 0.4, 0.0);
		double dx = aimAt.x - pivot.x, dz = aimAt.z - pivot.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat < NEAREST || flat > FURTHEST) {
			return;
		}
		// swing round to the foe, the barrel raised for the distance
		float want = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		float turn = Mth.clamp(Mth.wrapDegrees(want - this.getYRot()), -TRAVERSE, TRAVERSE);
		this.setYRot(this.getYRot() + turn);
		float pitch = Mth.clamp(elevation(flat, aimAt.y - pivot.y), MIN_PITCH, MAX_PITCH);
		float lift = Mth.clamp(pitch - this.barrelPitch(), -TRAVERSE, TRAVERSE);
		this.entityData.set(PITCH, this.barrelPitch() + lift);
		if (now >= this.next && Math.abs(Mth.wrapDegrees(want - this.getYRot())) < ON_MARK && Math.abs(pitch - this.barrelPitch()) < ON_MARK
			&& gunner.getSensing().hasLineOfSight(foe)) {
			this.next = now + RELOAD + level.getRandom().nextInt(JITTER);
			this.fire(level, gunner, pivot, flat);
		}
	}

	/** At rest: the barrel lowered back to level. */
	private void settle() {
		float p = this.barrelPitch();
		if (p != 0.0F) {
			this.entityData.set(PITCH, Math.abs(p) < 1.0F ? 0.0F : p - Math.signum(p));
		}
	}

	/** The barrel's elevation (degrees) that lands a ball {@code flat} blocks off and {@code up} blocks higher: the low arc, or 45 if out of reach. */
	static float elevation(double flat, double up) {
		double v = Cannonball.SPEED, g = Cannonball.GRAVITY;
		double root = v * v * v * v - g * (g * flat * flat + 2.0 * up * v * v);
		if (root < 0.0) {
			return 45.0F;
		}
		return (float) (Math.atan((v * v - Math.sqrt(root)) / (g * flat)) * Mth.RAD_TO_DEG);
	}

	/** The barrel's direction now. */
	public Vec3 barrel() {
		return Vec3.directionFromRotation(-this.barrelPitch(), this.getYRot());
	}

	private void fire(ServerLevel level, PathfinderMob gunner, Vec3 pivot, double flat) {
		Vec3 dir = this.barrel();
		double stray = SPREAD * flat / Cannonball.SPEED;
		dir = dir.add(level.getRandom().triangle(0.0, stray), level.getRandom().triangle(0.0, stray * 0.5), level.getRandom().triangle(0.0, stray)).normalize();
		Vec3 muzzle = pivot.add(dir.scale(MUZZLE));
		Cannonball.fire(level, gunner, muzzle, dir);
		level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 14, 0.25, 0.25, 0.25, 0.03);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.1, 0.1, 0.1, 0.02);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F,
			0.6F + level.getRandom().nextFloat() * 0.15F);
		level.broadcastEntityEvent(this, FIRED);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == FIRED) {
			this.sinceFired = 0;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.gunnerOf(passenger) && this.getPassengers().isEmpty();
	}

	/** The gunner sits on the trail, behind the breech. */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0.0, 0.3, -0.95).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return this.position().add(new Vec3(0.0, 0.0, -1.6).yRot(-this.getYRot() * Mth.DEG_TO_RAD));
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isRemoved() || !(source.getEntity() instanceof Player player) || player.isSpectator()) {
			return false;
		}
		this.damage += player.isCreative() ? HEALTH : amount;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.4F, 1.4F);
		if (this.damage >= HEALTH) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.6, this.getZ(), 12, 0.4, 0.3, 0.4, 0.02);
			this.ejectPassengers();
			this.discard();
		}
		return true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(COLOR, -1);
		entityData.define(PITCH, 0.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.faction = input.getIntOr("faction", -1);
		this.post = input.getLong("post").map(BlockPos::of).orElse(null);
		this.entityData.set(COLOR, input.getIntOr("color", -1));
		this.damage = input.getFloatOr("damage", 0.0F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("faction", this.faction);
		if (this.post != null) {
			output.putLong("post", this.post.asLong());
		}
		output.putInt("color", this.colorId());
		output.putFloat("damage", this.damage);
	}
}
