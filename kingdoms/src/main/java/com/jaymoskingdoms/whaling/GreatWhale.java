package com.jaymoskingdoms.whaling;

import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.206) A great whale of the deep oceans, about fourteen blocks from jaw to flukes. Pods cruise a few fathoms down,
 * drifting together, and every couple of minutes each comes up to blow ({@link #spout}) before sounding again. They keep
 * to deep water and turn back from the shallows.
 *
 * <p>Thick blubber turns most blows ({@link #BLUBBER}); only harpoons ({@link Harpoon}) bite deep. A struck whale bolts
 * from its hunters, dragging their boats after it on the lines, and its pod scatters. Every iron in it bleeds it and
 * tires it faster, and every taut line holds it back, until, spent ({@link #SPENT_AT}), it wallows at the surface
 * blowing blood. A sperm whale (the dark look) may turn on the boat that struck it and stove it in. It dies into a
 * floating {@link WhaleCarcass}, with the irons still in it.
 */
public class GreatWhale extends WaterAnimal {
	/** Its body (blocks; the model's head and flukes reach well past it). */
	static final float WIDTH = 4.0F, HEIGHT = 2.8F;
	static final double HEALTH = 260.0;
	/** Its speeds (blocks/tick): cruising, bolting, spent, and (a sperm whale) charging a boat. */
	static final double CRUISE = 0.09, FLEE = 0.32, SPENT = 0.035, CHARGE = 0.36;
	/** How fast it turns (degrees/tick), cruising and bolting, and the share of the gap to the speed it wants it closes a tick. */
	static final float TURN = 1.2F, FLEE_TURN = 3.0F;
	static final double GRIP = 0.05;
	/** How long it stays down between breaths (ticks), how long it lies at the surface blowing, and how often it blows. */
	static final int DIVE_MIN = 1800, DIVE_MAX = 3200, BLOW_TICKS = 240, SPOUT_EVERY = 45;
	/** How deep it cruises and sounds (blocks under the sea's surface), and its back's height above its keel at the surface. */
	static final int CRUISE_DEPTH = 7, SOUND_DEPTH = 22;
	static final double AWASH = 2.3;
	/** The share of any blow but a harpoon's that gets through its blubber. */
	static final float BLUBBER = 0.3F;
	/** Each iron in it bleeds it this much every this many ticks (counting at most this many irons). */
	static final int BLEED_EVERY = 40, MOST_BLEEDING = 10;
	static final float BLEED = 1.0F;
	/** What bolting takes out of it a tick, more for each iron and each taut line; what rest gives back; spent below this. */
	static final float TIRE = 1.0F / 1400.0F, TIRE_PER_IRON = 1.0F / 2800.0F, TIRE_PER_LINE = 1.0F / 2000.0F, REST = 1.0F / 3000.0F;
	static final float SPENT_AT = 0.12F;
	/** Each taut line takes this share off its bolting speed. */
	static final double LINE_DRAG = 0.12;
	/** How long a fright lasts with no iron in it (ticks), and how far a struck whale's fright spreads through its pod. */
	static final int FRIGHT_TICKS = 900;
	static final double ALARM_RANGE = 40.0;
	/** A bolting whale sounds now and then (a chance a tick, while it has the wind for it), for this long (ticks). */
	static final float SOUND_CHANCE = 1.0F / 500.0F;
	static final int SOUNDING_TICKS = 160;
	/** (Sperm whales) the chance a harpoon makes it turn and charge, how long it keeps at it, and what its blow does. */
	static final float RAM_CHANCE = 0.3F, RAM_DAMAGE = 8.0F;
	static final int RAM_TICKS = 240;
	/** It wants this much water under it (blocks), this far ahead (blocks); pods keep within this of each other. */
	static final int DEEP = 10, LOOKAHEAD = 14;
	static final double POD_RANGE = 48.0;
	/** One in this many likely spots actually gets a pod. */
	static final int RARITY = 3;
	/** Its looks: a grey rorqual and a dark sperm whale. */
	public static final int LOOKS = 2, SPERM = 1;

	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(GreatWhale.class, EntityDataSerializers.INT);

	private float course;
	private int breath = DIVE_MIN, blowing, fright, sounding, ramming;
	private float stamina = 1.0F;
	private int irons, lines;
	private @Nullable Vec3 threat;
	private @Nullable Entity ramTarget;
	/** Pulls on it from harpoon lines this tick (blocks/tick, added to the speed it wants). */
	private Vec3 towed = Vec3.ZERO;
	/** While it bleeds from its irons, so that blow isn't turned by its blubber. */
	private boolean bleeding;

	public GreatWhale(EntityType<? extends GreatWhale> type, Level level) {
		super(type, level);
		this.course = this.random.nextFloat() * 360.0F;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, CRUISE)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 32.0);
	}

	static boolean checkSpawn(EntityType<GreatWhale> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		int sea = level.getSeaLevel();
		return pos.getY() <= sea - 6 && pos.getY() >= sea - 30 && level.getFluidState(pos).is(FluidTags.WATER)
			&& level.getFluidState(pos.above(4)).is(FluidTags.WATER) && level.getFluidState(pos.below(3)).is(FluidTags.WATER)
			&& random.nextInt(RARITY) == 0;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANT, 0);
	}

	public int variant() {
		return this.entityData.get(VARIANT);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData group) {
		this.entityData.set(VARIANT, this.random.nextInt(LOOKS));
		this.breath = this.random.nextInt(DIVE_MAX);
		return super.finalizeSpawn(level, difficulty, reason, group);
	}

	/** Pulls it (a harpoon line, taut), taken up at its next move. */
	void tow(Vec3 pull) {
		this.towed = this.towed.add(pull);
	}

	boolean spent() {
		return this.stamina < SPENT_AT;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level().isClientSide()) {
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
		}
	}

	@Override
	public void travel(Vec3 input) {
		this.move(MoverType.SELF, this.getDeltaMovement());
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.isInWater()) {
			// stranded: it falls and flounders
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.08, 0.0).multiply(0.9, 0.98, 0.9).add(this.towed));
			this.towed = Vec3.ZERO;
			return;
		}
		if (this.tickCount % 20 == 0) {
			this.countIrons(level);
		}
		if (this.irons > 0) {
			this.fright = FRIGHT_TICKS;
		} else if (this.fright > 0) {
			this.fright--;
		}
		boolean fleeing = this.fright > 0;
		if (fleeing && !this.spent()) {
			this.stamina -= TIRE + this.irons * TIRE_PER_IRON + this.lines * TIRE_PER_LINE;
		} else if (this.irons == 0) {
			this.stamina += REST;
		}
		this.stamina = Mth.clamp(this.stamina, 0.0F, 1.0F);
		if (this.irons > 0 && this.tickCount % BLEED_EVERY == 0) {
			this.bleeding = true;
			this.hurtServer(level, this.damageSources().generic(), BLEED * Math.min(this.irons, MOST_BLEEDING));
			this.bleeding = false;
			level.sendParticles(new DustParticleOptions(0x7A0A0A, 2.5F), this.getX(), this.getY() + HEIGHT * 0.5, this.getZ(), 8 * this.irons,
				WIDTH * 0.4, HEIGHT * 0.4, WIDTH * 0.4, 0.0);
		}

		int sea = level.getSeaLevel();
		double surface = sea - AWASH;
		boolean awash = this.getY() > surface - 0.6;
		float yaw = this.getYRot();
		float wantYaw = this.course;
		double speed = CRUISE, wantY = sea - CRUISE_DEPTH;
		float turn = TURN;
		if (this.ramming > 0 && this.ramTarget != null && this.ramTarget.isAlive() && !this.spent()) {
			this.ramming--;
			wantYaw = yawTo(this.ramTarget.position().subtract(this.position()));
			speed = CHARGE;
			wantY = surface;
			turn = FLEE_TURN;
			this.ram(level);
		} else if (fleeing) {
			this.ramming = 0;
			this.ramTarget = null;
			turn = FLEE_TURN;
			if (this.spent()) {
				// spent: it wallows at the surface, blowing blood
				speed = SPENT;
				wantY = surface;
				if (awash && this.tickCount % 30 == 0) {
					this.spout(level, true);
				}
			} else {
				speed = FLEE * Math.max(0.25, 1.0 - LINE_DRAG * this.lines) * (0.5 + 0.5 * this.stamina);
				if (this.threat != null) {
					// away from its hunters, weaving
					wantYaw = yawTo(this.position().subtract(this.threat)) + Mth.sin(this.tickCount * 0.02F) * 35.0F;
				}
				if (this.sounding > 0) {
					this.sounding--;
					wantY = sea - SOUND_DEPTH;
				} else {
					wantY = surface - 1.0;
					if (this.stamina > 0.5F && this.random.nextFloat() < SOUND_CHANCE) {
						this.sounding = SOUNDING_TICKS;
					}
				}
				if (awash && this.tickCount % 60 == 0) {
					this.spout(level, false);
				}
			}
			this.course = wantYaw;
		} else {
			this.wander(level);
			wantYaw = this.course;
			if (this.blowing > 0) {
				wantY = surface;
				if (awash) {
					this.blowing--;
					if (this.blowing % SPOUT_EVERY == 0) {
						this.spout(level, false);
					}
				}
			} else if (--this.breath <= 0) {
				this.blowing = BLOW_TICKS;
				this.breath = DIVE_MIN + this.random.nextInt(DIVE_MAX - DIVE_MIN);
			}
		}
		if (this.tickCount % 10 == 0 && this.shallowAhead(level, wantYaw)) {
			// shoal water ahead: it turns back for the deep
			this.course = wantYaw + 150.0F + this.random.nextFloat() * 60.0F;
			wantYaw = this.course;
		}
		wantY = Math.max(wantY, this.floor(level) + 1.5);
		yaw = Mth.approachDegrees(yaw, wantYaw, turn);
		this.setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
		Vec3 ahead = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0.0, Mth.cos(yaw * Mth.DEG_TO_RAD));
		double rise = Mth.clamp((wantY - this.getY()) * 0.05, -0.12, 0.1);
		Vec3 want = ahead.scale(speed).add(0.0, rise, 0.0);
		Vec3 v = this.getDeltaMovement();
		// its lines hold it back (or haul it in): a pull on the speed it wants, not a shove
		v = v.add(want.add(this.towed).subtract(v).scale(GRIP));
		this.towed = Vec3.ZERO;
		this.setDeltaMovement(v);
		float pitch = (float)(-Mth.atan2(v.y, Math.max(0.05, v.horizontalDistance())) * Mth.RAD_TO_DEG);
		this.setXRot(Mth.clamp(pitch, -35.0F, 35.0F));
	}

	/** Drifting with its pod: now and then it eases toward the course of the nearest of its kind, or closes on one gone far. */
	private void wander(ServerLevel level) {
		if (this.tickCount % 100 != 0) {
			return;
		}
		this.course += (this.random.nextFloat() - 0.5F) * 30.0F;
		GreatWhale near = null;
		double best = POD_RANGE * POD_RANGE;
		for (GreatWhale w : level.getEntitiesOfClass(GreatWhale.class, this.getBoundingBox().inflate(POD_RANGE), w -> w != this && w.isAlive())) {
			double d = w.distanceToSqr(this);
			if (d < best) {
				best = d;
				near = w;
			}
		}
		if (near != null) {
			float toward = best > 24.0 * 24.0 ? yawTo(near.position().subtract(this.position())) : near.course;
			this.course += Mth.wrapDegrees(toward - this.course) * 0.5F;
		}
	}

	/** Whether the water ahead on {@code yaw} is too shallow for it (or isn't water). */
	private boolean shallowAhead(ServerLevel level, float yaw) {
		Vec3 p = this.position().add(-Mth.sin(yaw * Mth.DEG_TO_RAD) * LOOKAHEAD, 0.0, Mth.cos(yaw * Mth.DEG_TO_RAD) * LOOKAHEAD);
		BlockPos top = BlockPos.containing(p.x, level.getSeaLevel() - 1, p.z);
		for (int i = 0; i < DEEP; i++) {
			if (!level.getFluidState(top.below(i)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/** The top of the sea floor under it. */
	private double floor(ServerLevel level) {
		BlockPos p = this.blockPosition();
		for (int i = 0; i < 48 && p.getY() > level.getMinY(); i++, p = p.below()) {
			if (!level.getFluidState(p).is(FluidTags.WATER) && !level.getBlockState(p).isAir()) {
				return p.getY() + 1;
			}
		}
		return level.getMinY();
	}

	/** Its irons and the taut lines on them, and where its hunters are (the ends of its lines). */
	private void countIrons(ServerLevel level) {
		int i = 0, l = 0;
		Vec3 sum = Vec3.ZERO;
		for (Harpoon h : Harpoon.in(level, this)) {
			i++;
			Entity anchor = h.anchor();
			if (anchor != null) {
				l++;
				sum = sum.add(anchor.position());
			}
		}
		this.irons = i;
		this.lines = l;
		if (l > 0) {
			this.threat = sum.scale(1.0 / l);
		}
	}

	/** Blows: a spout of spray from its blowhole (red, once it's spent), and the roar of it. */
	void spout(ServerLevel level, boolean blood) {
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		double x = this.getX() - Mth.sin(yaw) * 4.0, y = this.getY() + HEIGHT, z = this.getZ() + Mth.cos(yaw) * 4.0;
		for (int i = 0; i < 24; i++) {
			level.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, (this.random.nextFloat() - 0.5F) * 0.15, 0.6 + this.random.nextFloat() * 0.4,
				(this.random.nextFloat() - 0.5F) * 0.15, 1.0);
		}
		level.sendParticles(ParticleTypes.SPLASH, x, y + 1.5, z, 60, 0.4, 1.5, 0.4, 0.3);
		level.sendParticles(ParticleTypes.FALLING_WATER, x, y + 4.0, z, 40, 1.2, 1.0, 1.2, 0.0);
		if (blood) {
			level.sendParticles(new DustParticleOptions(0x8A0303, 3.0F), x, y + 2.0, z, 40, 0.5, 2.0, 0.5, 0.0);
		}
		level.playSound(null, x, y, z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 3.0F, 0.5F);
		level.playSound(null, x, y, z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.NEUTRAL, 2.0F, 0.6F);
	}

	/** A charging sperm whale's blow when it reaches what it's after: boats are stove in, ships shoved, men battered. */
	private void ram(ServerLevel level) {
		Entity target = this.ramTarget;
		if (target == null || !this.getBoundingBox().inflate(1.5).intersects(target.getBoundingBox())) {
			return;
		}
		Vec3 push = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0F, 0.6F);
		if (target instanceof AbstractBoat boat) {
			for (Entity rider : List.copyOf(boat.getPassengers())) {
				rider.hurtServer(level, this.damageSources().mobAttack(this), RAM_DAMAGE);
				rider.push(push.x, 0.6, push.z);
			}
			boat.hurtServer(level, this.damageSources().mobAttack(this), 10.0F);
		} else if (target instanceof Ship ship) {
			ship.tow(push.scale(0.8));
			for (Player p : level.players()) {
				if (Ships.aboard(p) == ship) {
					p.hurtServer(level, this.damageSources().mobAttack(this), RAM_DAMAGE * 0.5F);
				}
			}
		} else {
			target.hurtServer(level, this.damageSources().mobAttack(this), RAM_DAMAGE);
			target.push(push.x * 1.5, 0.5, push.z * 1.5);
		}
		this.ramming = 0;
		this.ramTarget = null;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean iron = this.bleeding || source.getDirectEntity() instanceof Harpoon;
		if (!iron && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			damage *= BLUBBER;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (!hurt || this.bleeding) {
			return hurt;
		}
		this.setPersistenceRequired();
		this.fright = FRIGHT_TICKS;
		Entity by = source.getEntity();
		if (by != null) {
			this.threat = by.position();
		}
		// the pod scatters
		for (GreatWhale w : level.getEntitiesOfClass(GreatWhale.class, this.getBoundingBox().inflate(ALARM_RANGE), w -> w != this)) {
			w.fright = Math.max(w.fright, FRIGHT_TICKS / 2);
			w.threat = this.threat;
		}
		// a sperm whale may turn on whoever struck it
		if (iron && by != null && this.variant() == SPERM && this.stamina > 0.4F && this.random.nextFloat() < RAM_CHANCE) {
			Ship ship = Ships.aboard(by);
			this.ramTarget = ship != null ? ship : by.getVehicle() != null ? by.getVehicle() : by;
			this.ramming = RAM_TICKS;
		}
		return true;
	}

	@Override
	protected void tickDeath() {
		// it doesn't roll over and vanish: it becomes a floating carcass, irons and all
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			WhaleCarcass carcass = WhaleCarcass.of(level, this);
			level.addFreshEntity(carcass);
			for (Harpoon h : Harpoon.in(level, this)) {
				h.lodgeIn(carcass);
			}
			this.remove(RemovalReason.KILLED);
		}
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 30;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 600;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ELDER_GUARDIAN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ELDER_GUARDIAN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ELDER_GUARDIAN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.45F;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Variant", this.variant());
		output.putFloat("Course", this.course);
		output.putFloat("Stamina", this.stamina);
		output.putInt("Breath", this.breath);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(VARIANT, Math.floorMod(input.getIntOr("Variant", 0), LOOKS));
		this.course = input.getFloatOr("Course", this.course);
		this.stamina = input.getFloatOr("Stamina", 1.0F);
		this.breath = input.getIntOr("Breath", DIVE_MIN);
	}

	static float yawTo(Vec3 d) {
		return (float)(Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
	}
}
