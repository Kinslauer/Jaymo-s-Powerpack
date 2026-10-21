package com.jaymoskingdoms.dragons;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.137) A wind dragon ({@code docs/Dragons-Plan.md}), our own design: what Notus, Boreas and Zephyrus share. Each
 * species ({@link NotusDragon}, {@link BoreasDragon}, {@link ZephyrusDragon}) adds its breath, its favourite food, who it
 * bonds with, its home and its body; the tuning numbers here are the shared ones (a species overrides the hooks below).
 * <p>
 * <b>Wild:</b> a miniboss (a boss bar for whoever hits it, {@code c:bosses}). It circles high over its home and perches
 * on the best spot about; it swoops on players who come near, hovers to breathe on them, and on the ground bites and
 * sweeps its tail.
 * <p>
 * <b>Taming:</b> worn below a third of its health it crashes and can't fly for a while. Crouch near it with empty hands
 * (or holding its favourite food) and its trust (a meter only you see) fills; offering its favourite fills it faster,
 * and the heritages (or powers) it bonds with twice as fast. A drawn weapon, sprinting at it or anyone hurting it drains
 * the meter and makes it lash out; emptied, it bolts, heals and flies off. Full, it's yours. Its trust is its own (not
 * Equestrian's {@code Bond}).
 * <p>
 * <b>Tamed:</b> sneak-use with empty hands sits it or lets it follow; sneak-use holding anything opens its saddle slot;
 * a saddle in hand puts one on. Saddled, its owner rides it ({@code DragonsClient} flies it: Space climbs, W flies where
 * you look, S brakes to a hover, Shift dives) and a second player can ride behind. It tires (stamina; meat feeds it,
 * its favourite best) and an exhausted dragon glides down. R breathes where the rider looks.
 */
public abstract class WindDragon extends AbstractHorse {
	// ---- body (the defaults are Notus's; a species overrides the hooks) ----
	/** Where its riders sit: height, and how far forward the rider and back the passenger. */
	static final double SEAT_Y = 1.85, SEAT_FRONT = 0.3, SEAT_BACK = -0.6;
	/** Where its mouth is (ahead of its middle, up from its feet) on the ground and in flight. */
	static final double MOUTH_AHEAD = 3.6, MOUTH_UP = 2.4, MOUTH_AHEAD_FLYING = 3.9, MOUTH_UP_FLYING = 1.4;

	// ---- wild ----
	/** Players this near are attacked (blocks); one further than {@link #LOSE_RANGE} is let go. */
	static final double AGGRO_RANGE = 22.0, LOSE_RANGE = 64.0;
	/** Circling: how high over the ground, how wide, how fast; how long it circles before it looks for a perch. */
	static final double CRUISE_HEIGHT = 22.0, NETHER_CRUISE_HEIGHT = 12.0, CIRCLE_RADIUS = 28.0, CRUISE_SPEED = 0.55;
	static final int CIRCLE_MIN = 500, CIRCLE_MAX = 1000;
	/** Perching: how far it looks for a spire, how long it sits on one. */
	static final int PERCH_SEARCH = 40, PERCH_TRIES = 28, PERCH_MIN = 500, PERCH_MAX = 1200;
	/** How hard it turns in the air (share of the way to its wanted velocity each tick); the least clearance it keeps. */
	static final double TURN_EASE = 0.1, MIN_CLEARANCE = 5.0;
	/** The swoop: how fast it dives, its claws' damage, how hard they throw. */
	static final double SWOOP_SPEED = 1.15, SWOOP_THROW = 1.3;
	static final float CLAW = 10.0F;
	/** Hovering to breathe: how far off and how high over its foe; how long the breath lasts. */
	static final double HOVER_OFF = 9.0, HOVER_UP = 6.0;
	static final int BREATH_TICKS = 50;
	/** On the ground: the bite's reach and pace, the tail sweep's reach, damage, throw and pace, the ground breath's pace. */
	static final double BITE_REACH = 3.2, SWEEP_RADIUS = 5.5, SWEEP_THROW = 1.6;
	static final float SWEEP = 8.0F;
	static final int BITE_EVERY = 30, SWEEP_EVERY = 70, GROUND_BREATH_EVERY = 140;
	/** How long it fights on the ground before taking off again; the share of attacks that are swoops and hovers. */
	static final int GROUND_FIGHT_MIN = 200, GROUND_FIGHT_MAX = 320;
	static final float SWOOP_ODDS = 0.45F, HOVER_ODDS = 0.35F;

	// ---- taming ----
	/** Below this share of its health it crashes, and stays down this long (ticks). */
	static final float CRASH_AT = 1.0F / 3.0F;
	static final int GROUNDED_TICKS = 3600;
	/** Who counts as near (blocks); trust a second crouching with empty hands; one of its favourite; a bonded one's factor. */
	static final double TAME_RANGE = 8.0;
	public static final float TRUST_FULL = 100.0F;
	static final float CALM_TRUST = 2.5F, OFFER_TRUST = 12.0F, BOND_FACTOR = 2.0F;
	static final int OFFER_EVERY = 30;
	/** Lost a second to a drawn weapon or sprinting near it, per blow, and drifting away when no one is calming it. */
	static final float WEAPON_DRAIN = 8.0F, SPRINT_DRAIN = 10.0F, HURT_DRAIN = 25.0F, DECAY = 0.4F;
	/** How near a sprinter must be to count; how long it lashes out at whoever upset it (ticks). */
	static final double SPRINT_RANGE = 10.0;
	static final int LASH_TICKS = 60;
	/** Bolting: the share of its health it gets back, and how long it flies away. */
	static final float BOLT_HEAL = 0.6F;
	static final int FLEE_TICKS = 300;

	// ---- tamed ----
	public static final float STAMINA = 100.0F;
	/** Stamina a second: flapping (climbing or flying fast), gliding, breathing fire; back on the ground. */
	static final float FLAP_DRAIN = 1.6F, GLIDE_DRAIN = 0.25F, BREATH_DRAIN = 6.0F, REST_REGEN = 3.0F;
	/** Exhausted at none, it may fly again from this much. */
	public static final float RECOVER = 25.0F;
	/** What a meal gives back: its favourite, any other meat; and health. */
	static final float FAVOURITE_STAMINA = 40.0F, MEAT_STAMINA = 20.0F, MEAL_HEAL = 6.0F;
	/** How long one breath packet keeps the rider's fire going (ticks). */
	static final int RIDER_BREATH_HOLD = 6;
	/** Following: it comes from this far, stops this near, and is brought over from beyond this. */
	static final double FOLLOW_START = 10.0, FOLLOW_STOP = 5.0, FOLLOW_TELEPORT = 40.0;

	/** Synced flags: breathing, crashed (grounded), sitting, exhausted. */
	public static final int BREATHING = 1, GROUNDED = 2, SITTING = 4, EXHAUSTED = 8;
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(WindDragon.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Float> DATA_STAMINA = SynchedEntityData.defineId(WindDragon.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_TRUST = SynchedEntityData.defineId(WindDragon.class, EntityDataSerializers.FLOAT);
	/** The player winning its trust (entity id; -1 none): only they see the meter. */
	private static final EntityDataAccessor<Integer> DATA_TAMER = SynchedEntityData.defineId(WindDragon.class, EntityDataSerializers.INT);
	/** Its {@link DragonLook}, encoded. */
	private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(WindDragon.class, EntityDataSerializers.INT);

	/** What a wild one is about. */
	protected enum Wild { CIRCLE, PERCH_GO, PERCHED, FLEE, SWOOP_RISE, SWOOP_DIVE, HOVER, GROUND }

	protected Wild wild = Wild.CIRCLE;
	protected int wildTicks, wildFor;
	private boolean flying;
	protected @Nullable Vec3 home;
	private @Nullable BlockPos perch;
	private double circleAngle;
	private @Nullable Vec3 swoopFrom;
	private int breathTicks, biteIn, sweepIn, breathIn;
	private @Nullable LivingEntity foe;
	private int lashUntil;
	private int groundedLeft;
	private int offeredAt = -OFFER_EVERY;
	private @Nullable UUID tamer;
	private int riderBreathUntil = -1;
	private Vec3 riderAim = Vec3.ZERO;
	private @Nullable Vec3 lastPos;
	private double floorY = Double.NaN;
	private @Nullable ServerBossEvent bar;

	// ---- the client's smoothed animation (see the model) ----
	public float flyAnim, flyAnimO, breathAnim, breathAnimO, landAnim, landAnimO, sitAnim, sitAnimO, crashAnim, crashAnimO;
	public float flapPhase, flapPhaseO, glideAnim, glideAnimO, pitchAnim, pitchAnimO, swimAnim, swimAnimO;

	protected WindDragon(EntityType<? extends WindDragon> type, Level level) {
		super(type, level);
		this.xpReward = 60;
	}

	/** A species' attributes: its health, hide (armour, toughness), pace on the ground and bite. */
	protected static AttributeSupplier.Builder dragonAttributes(double health, double armor, double toughness, double speed, double bite) {
		return createBaseHorseAttributes()
			.add(Attributes.MAX_HEALTH, health)
			.add(Attributes.ARMOR, armor)
			.add(Attributes.ARMOR_TOUGHNESS, toughness)
			.add(Attributes.MOVEMENT_SPEED, speed)
			.add(Attributes.ATTACK_DAMAGE, bite)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.JUMP_STRENGTH, 0.5)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.STEP_HEIGHT, 1.6)
			.add(Attributes.SAFE_FALL_DISTANCE, 16.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FLAGS, (byte)0);
		builder.define(DATA_STAMINA, STAMINA);
		builder.define(DATA_TRUST, 0.0F);
		builder.define(DATA_TAMER, -1);
		builder.define(DATA_LOOK, 0);
	}

	@Override
	protected void registerGoals() {
		// it moves itself (customServerAiStep): no horse goals (no panicking, no bucking)
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		this.entityData.set(DATA_LOOK, DragonLook.roll(this.random).encode());
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
		this.setAge(0);
		return data;
	}

	// ---- reading ----------------------------------------------------------------------------------------------------

	public boolean flag(int f) {
		return (this.entityData.get(DATA_FLAGS) & f) != 0;
	}

	private void setDragonFlag(int f, boolean on) {
		byte b = this.entityData.get(DATA_FLAGS);
		byte now = (byte)(on ? b | f : b & ~f);
		if (now != b) {
			this.entityData.set(DATA_FLAGS, now);
		}
	}

	public boolean grounded() {
		return flag(GROUNDED);
	}

	public boolean sitting() {
		return flag(SITTING);
	}

	public boolean exhausted() {
		return flag(EXHAUSTED);
	}

	public float stamina() {
		return this.entityData.get(DATA_STAMINA);
	}

	public float trust() {
		return this.entityData.get(DATA_TRUST);
	}

	/** The entity id of the player winning its trust, or -1. */
	public int tamerId() {
		return this.entityData.get(DATA_TAMER);
	}

	public DragonLook look() {
		return DragonLook.decode(this.entityData.get(DATA_LOOK));
	}

	public boolean saddled() {
		return !this.getItemBySlot(EquipmentSlot.SADDLE).isEmpty();
	}

	/** In the air on its wings (what the model draws as flying). */
	public boolean airborne() {
		return this.isNoGravity() && !this.onGround();
	}

	/** Where its breath comes from. */
	public Vec3 mouth() {
		boolean air = this.airborne();
		float yaw = this.getYHeadRot() * Mth.DEG_TO_RAD;
		double ahead = mouthAhead(air);
		return new Vec3(this.getX() - Mth.sin(yaw) * ahead, this.getY() + mouthUp(air), this.getZ() + Mth.cos(yaw) * ahead);
	}

	// ---- what sets a species apart (the hooks) --------------------------------------------------------------------------

	/** Its favourite food: offered while it's grounded, it wins its trust faster; tamed, it feeds it best. */
	public abstract boolean favourite(ItemStack stack);

	/** One tick of its breath from {@code mouth} along {@code aim} (a unit vector); {@code griefing}: whether it may change blocks. */
	protected abstract void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing);

	/** How much faster {@code player} wins its trust (the heritages and powers it bonds with); 1 for anyone else. */
	protected float bondFactor(Player player) {
		return 1.0F;
	}

	/** How far its breath reaches (it breathes from the ground at foes nearer than this). */
	protected double breathRange() {
		return DragonBreath.RANGE;
	}

	/** Its mouth: how far ahead of its middle and up from its feet, on the ground or in flight. */
	protected double mouthAhead(boolean flying) {
		return flying ? MOUTH_AHEAD_FLYING : MOUTH_AHEAD;
	}

	protected double mouthUp(boolean flying) {
		return flying ? MOUTH_UP_FLYING : MOUTH_UP;
	}

	/** Its riders' seats (0 the rider, 1 the passenger): height, and how far forward (+) or back (-). */
	protected Vec3 seat(int index) {
		return new Vec3(0.0, SEAT_Y, index == 0 ? SEAT_FRONT : SEAT_BACK);
	}

	/** How fast it cruises wild, and dives in a swoop. */
	protected double cruiseSpeed() {
		return CRUISE_SPEED;
	}

	protected double swoopSpeed() {
		return SWOOP_SPEED;
	}

	/** Stamina a second ridden: flapping (climbing or flying fast), gliding, breathing. */
	protected float flapDrain() {
		return FLAP_DRAIN;
	}

	protected float glideDrain() {
		return GLIDE_DRAIN;
	}

	protected float breathDrain() {
		return BREATH_DRAIN;
	}

	/** How loud its wingbeats are (a silent diver beats softly). */
	protected float flapVolume() {
		return 2.0F;
	}

	/** Whether it roars as it dives on a foe. */
	protected boolean roarsBeforeSwoop() {
		return true;
	}

	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	/** Called every tick it sits on its perch, wild (a species that rests in the water keeps there). */
	protected void perchedTick(ServerLevel level) {
	}

	/** Whether {@code top} (the block it would stand on) will do for a perch. */
	protected boolean perchable(ServerLevel level, BlockPos top) {
		return level.getBlockState(top).isSolid() && level.getFluidState(top).isEmpty() && room(level, top);
	}

	/** Three blocks of air over {@code top}. */
	protected static boolean room(ServerLevel level, BlockPos top) {
		return level.getBlockState(top.above()).isAir() && level.getBlockState(top.above(2)).isAir() && level.getBlockState(top.above(3)).isAir();
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return favourite(stack);
	}

	// ---- ticking ----------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			animate();
			return;
		}
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.resetFallDistance();
		Vec3 here = this.position();
		Vec3 moved = this.lastPos == null ? Vec3.ZERO : here.subtract(this.lastPos);
		this.lastPos = here;
		if (this.isTamed()) {
			tamedTick(level, moved);
		}
		// breath, the wild one's and the rider's
		boolean riderBreath = this.isTamed() && this.riderBreathUntil >= this.tickCount && !exhausted();
		if (this.breathTicks > 0 || riderBreath) {
			Vec3 aim = riderBreath ? this.riderAim : this.breathAim();
			boolean griefing = riderBreath || level.getGameRules().get(GameRules.MOB_GRIEFING);
			breathe(level, mouth(), aim, griefing);
			if (this.breathTicks > 0) {
				this.breathTicks--;
			}
			if (riderBreath) {
				spend(breathDrain() / 20.0F);
			}
		}
		setDragonFlag(BREATHING, this.breathTicks > 0 || riderBreath);
	}

	/** Its riders' stamina and its wings: the owner flies it; it tires and rests. */
	private void tamedTick(ServerLevel level, Vec3 moved) {
		LivingEntity rider = this.getControllingPassenger();
		boolean ridden = rider instanceof Player;
		this.flying = false;
		this.setNoGravity(ridden && !grounded() && saddled());
		for (Entity passenger : this.getPassengers()) {
			passenger.resetFallDistance();
		}
		if (ridden && !this.onGround()) {
			boolean hard = moved.y > 0.05 || moved.horizontalDistance() > 0.5;
			spend((hard ? flapDrain() : glideDrain()) / 20.0F);
		} else if (this.onGround() || !ridden) {
			setStamina(stamina() + REST_REGEN / 20.0F);
		}
		if (exhausted() && stamina() >= RECOVER) {
			setDragonFlag(EXHAUSTED, false);
		}
		if (this.tickCount % 20 == 0 && this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
	}

	private void spend(float amount) {
		setStamina(stamina() - amount);
		if (stamina() <= 0.0F && !exhausted()) {
			setDragonFlag(EXHAUSTED, true);
			if (this.getControllingPassenger() instanceof ServerPlayer rider) {
				rider.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.dragon.exhausted", this.getDisplayName())
					.withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private void setStamina(float value) {
		this.entityData.set(DATA_STAMINA, Mth.clamp(value, 0.0F, STAMINA));
	}

	/** (From the Breathe packet) its rider holds R, looking along {@code yaw}/{@code pitch}. */
	public void riderBreath(float yaw, float pitch) {
		if (exhausted() || stamina() <= 0.0F) {
			return;
		}
		this.riderBreathUntil = this.tickCount + RIDER_BREATH_HOLD;
		this.riderAim = DragonBreath.aim(yaw, pitch);
		this.setYHeadRot(yaw);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.isTamed()) {
			if (!this.isVehicle()) {
				tamedAi(level);
			}
			return;
		}
		boss(level);
		if (grounded()) {
			groundedAi(level);
			return;
		}
		// (bolting, it flies off whoever's about)
		LivingEntity target = this.wild == Wild.FLEE ? null : wildFoe(level);
		if (target != null) {
			fight(level, target);
		} else {
			roam(level);
		}
	}

	// ---- wild: roaming --------------------------------------------------------------------------------------------

	private void roam(ServerLevel level) {
		if (this.home == null) {
			this.home = this.position();
		}
		this.wildTicks++;
		switch (this.wild) {
			case PERCH_GO -> {
				if (this.perch == null) {
					circle();
					return;
				}
				Vec3 top = Vec3.atBottomCenterOf(this.perch.above());
				double flat = top.subtract(this.position()).horizontalDistance();
				setFlying(true);
				flyToward(level, top.add(0.0, flat > 6.0 ? 3.0 : 0.0, 0.0), flat > 6.0 ? cruiseSpeed() : 0.25, flat < 6.0);
				if (flat < 1.5 && this.getY() - top.y < 1.5 || this.onGround() && flat < 4.0 || this.wildTicks > 600) {
					land();
					this.wild = Wild.PERCHED;
					this.wildTicks = 0;
					this.wildFor = Mth.nextInt(this.random, PERCH_MIN, PERCH_MAX);
					level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.55F);
				}
			}
			case PERCHED -> {
				this.getNavigation().stop();
				perchedTick(level);
				if (this.wildTicks > this.wildFor) {
					takeOff();
					this.home = this.position();
					circle();
				}
			}
			case FLEE -> {
				Player near = level.getNearestPlayer(this, LOSE_RANGE);
				Vec3 away = near == null ? this.getLookAngle() : this.position().subtract(near.position()).multiply(1.0, 0.0, 1.0).normalize();
				setFlying(true);
				flyToward(level, this.position().add(away.scale(20.0)).add(0.0, 8.0, 0.0), cruiseSpeed() * 1.4, false);
				if (this.wildTicks > FLEE_TICKS) {
					this.home = this.position();
					circle();
				}
			}
			default -> {
				if (this.wild != Wild.CIRCLE) {
					circle();
				}
				if (this.onGround() && !this.flying) {
					takeOff();
				}
				setFlying(true);
				this.circleAngle += cruiseSpeed() / CIRCLE_RADIUS;
				double floor = this.homeFloor(level);
				double height = level.dimension() == Level.NETHER ? NETHER_CRUISE_HEIGHT : CRUISE_HEIGHT;
				Vec3 point = new Vec3(this.home.x + Math.cos(this.circleAngle) * CIRCLE_RADIUS, floor + height,
					this.home.z + Math.sin(this.circleAngle) * CIRCLE_RADIUS);
				flyToward(level, point, cruiseSpeed(), false);
				if (this.wildTicks > this.wildFor) {
					this.perch = findPerch(level);
					this.wild = this.perch == null ? Wild.CIRCLE : Wild.PERCH_GO;
					this.wildTicks = 0;
				}
			}
		}
	}

	private void circle() {
		this.wild = Wild.CIRCLE;
		this.wildTicks = 0;
		this.wildFor = Mth.nextInt(this.random, CIRCLE_MIN, CIRCLE_MAX);
		this.circleAngle = Math.atan2(this.getZ() - (this.home == null ? this.getZ() : this.home.z), this.getX() - (this.home == null ? this.getX() : this.home.x));
	}

	/** The ground under its home (the first solid block below it; works under the Nether's roof too). */
	private double homeFloor(ServerLevel level) {
		if (Double.isNaN(this.floorY) || this.tickCount % 40 == 0) {
			Vec3 at = this.home == null ? this.position() : this.home;
			this.floorY = floorBelow(level, BlockPos.containing(at.x, Math.max(this.getY(), at.y), at.z));
		}
		return this.floorY;
	}

	protected static double floorBelow(ServerLevel level, BlockPos from) {
		BlockPos.MutableBlockPos at = from.mutable();
		for (int i = 0; i < 64 && at.getY() > level.getMinY(); i++) {
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getFluidState(at).isEmpty()) {
				return at.getY() + 1.0;
			}
			at.move(0, -1, 0);
		}
		return from.getY() - 64.0;
	}

	/** The tallest standable spot round its home ({@link #perchable}): a spire's top with room for it. */
	protected @Nullable BlockPos findPerch(ServerLevel level) {
		Vec3 centre = this.home == null ? this.position() : this.home;
		BlockPos best = null;
		boolean nether = level.dimension() == Level.NETHER;
		for (int i = 0; i < PERCH_TRIES; i++) {
			int x = Mth.floor(centre.x) + this.random.nextInt(PERCH_SEARCH * 2 + 1) - PERCH_SEARCH;
			int z = Mth.floor(centre.z) + this.random.nextInt(PERCH_SEARCH * 2 + 1) - PERCH_SEARCH;
			BlockPos top;
			if (!level.isLoaded(new BlockPos(x, 0, z))) {
				continue;
			}
			if (nether) {
				double y = floorBelow(level, BlockPos.containing(x, this.getY(), z));
				top = BlockPos.containing(x, y - 1.0, z);
			} else {
				top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
			}
			if (!perchable(level, top)) {
				continue;
			}
			if (best == null || top.getY() > best.getY()) {
				best = top;
			}
		}
		return best;
	}

	// ---- wild: fighting -------------------------------------------------------------------------------------------

	/** The player it's after: the one an encounter set it on, or anyone (not creative) who comes near. */
	private @Nullable LivingEntity wildFoe(ServerLevel level) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return null;
		}
		LivingEntity target = this.foe != null ? this.foe : this.getTarget();
		if (!valid(target)) {
			target = null;
			Player near = level.getNearestPlayer(this, AGGRO_RANGE);
			if (valid(near)) {
				target = near;
				level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.7F);
			}
		}
		if (target == null && this.foe != null) {
			// it lost them: back to circling
			if (this.wild != Wild.FLEE) {
				this.wild = Wild.CIRCLE;
			}
		}
		this.foe = target;
		return target;
	}

	private boolean valid(@Nullable LivingEntity e) {
		return e != null && e.isAlive() && e.level() == this.level() && e.distanceToSqr(this) < LOSE_RANGE * LOSE_RANGE
			&& !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	private void fight(ServerLevel level, LivingEntity target) {
		this.wildTicks++;
		this.home = target.position();
		switch (this.wild) {
			case SWOOP_RISE -> {
				setFlying(true);
				if (this.swoopFrom == null) {
					Vec3 off = this.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
					off = off.lengthSqr() < 1.0 ? new Vec3(1.0, 0.0, 0.0) : off.normalize();
					this.swoopFrom = target.position().add(off.scale(16.0)).add(0.0, 14.0, 0.0);
				}
				flyToward(level, this.swoopFrom, cruiseSpeed() * 1.3, false);
				if (this.position().distanceTo(this.swoopFrom) < 4.0 || this.wildTicks > 160) {
					this.wild = Wild.SWOOP_DIVE;
					this.wildTicks = 0;
					if (roarsBeforeSwoop()) {
						level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.8F);
					}
				}
			}
			case SWOOP_DIVE -> {
				setFlying(true);
				Vec3 at = target.getBoundingBox().getCenter();
				this.setDeltaMovement(this.getDeltaMovement().lerp(at.subtract(this.position()).normalize().scale(swoopSpeed()), 0.25));
				face(this.getDeltaMovement());
				if (this.getBoundingBox().inflate(1.0).intersects(target.getBoundingBox())) {
					claw(level, target);
					this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.9, 0.0));
					next(level, target);
				} else if (this.wildTicks > 80 || this.horizontalCollision) {
					next(level, target);
				}
			}
			case HOVER -> {
				setFlying(true);
				Vec3 off = this.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
				off = off.lengthSqr() < 1.0 ? new Vec3(0.0, 0.0, 1.0) : off.normalize();
				Vec3 spot = target.position().add(off.scale(HOVER_OFF)).add(0.0, HOVER_UP, 0.0);
				if (this.position().distanceTo(spot) > 3.0 && this.breathTicks == 0) {
					flyToward(level, spot, cruiseSpeed(), false);
				} else {
					this.setDeltaMovement(this.getDeltaMovement().scale(0.7).add(0.0, Mth.sin(this.tickCount * 0.15F) * 0.02, 0.0));
					if (this.breathTicks == 0 && this.wildTicks < 200) {
						this.breathTicks = BREATH_TICKS;
					}
				}
				lookAt(target);
				if (this.wildTicks > 220 || this.breathTicks == 1) {
					next(level, target);
				}
			}
			case GROUND -> {
				if (!this.onGround() && this.flying) {
					// coming down beside them
					flyToward(level, target.position().add(this.position().subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize()
						.scale(5.0)), cruiseSpeed(), true);
					if (this.wildTicks > 200) {
						next(level, target);
					}
					return;
				}
				if (this.flying) {
					land();
					this.wildFor = Mth.nextInt(this.random, GROUND_FIGHT_MIN, GROUND_FIGHT_MAX);
					this.wildTicks = 0;
				}
				groundFight(level, target, true);
				if (this.wildTicks > this.wildFor) {
					next(level, target);
				}
			}
			default -> next(level, target);
		}
	}

	/** Picks its next move against {@code target}. */
	private void next(ServerLevel level, LivingEntity target) {
		this.wildTicks = 0;
		this.swoopFrom = null;
		float roll = this.random.nextFloat();
		if (this.onGround() && this.wild != Wild.GROUND) {
			takeOff();
		}
		this.wild = roll < SWOOP_ODDS ? Wild.SWOOP_RISE : roll < SWOOP_ODDS + HOVER_ODDS ? Wild.HOVER : Wild.GROUND;
		this.wildFor = Mth.nextInt(this.random, GROUND_FIGHT_MIN, GROUND_FIGHT_MAX);
		if (this.wild != Wild.GROUND && this.onGround()) {
			takeOff();
		}
	}

	/** On its feet: bite whoever's in reach, sweep the tail round when it's crowded or they're beside it, breathe at range. */
	private void groundFight(ServerLevel level, LivingEntity target, boolean breathe) {
		lookAt(target);
		double reach = BITE_REACH + this.getBbWidth() * 0.5 + target.getBbWidth() * 0.5;
		double distance = this.distanceTo(target);
		if (distance > reach - 0.5) {
			this.getNavigation().moveTo(target, 1.0);
		} else {
			this.getNavigation().stop();
		}
		this.biteIn--;
		this.sweepIn--;
		this.breathIn--;
		if (distance <= reach && this.biteIn <= 0) {
			this.biteIn = BITE_EVERY;
			target.hurtServer(level, this.damageSources().mobAttack(this), (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE));
			level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.5F, 0.6F);
		}
		List<LivingEntity> close = level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(SWEEP_RADIUS),
			e -> e != this && e.isAlive() && !(e instanceof Player p && (p.isCreative() || p.isSpectator())) && !DragonBreath.spared(this, e)
				&& !(e instanceof WindDragon));
		Vec3 facing = Vec3.directionFromRotation(0.0F, this.getYRot());
		Vec3 toTarget = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize();
		boolean beside = facing.dot(toTarget) < 0.3;
		if (this.sweepIn <= 0 && distance < SWEEP_RADIUS + 1.0 && (close.size() >= 2 || beside)) {
			this.sweepIn = SWEEP_EVERY;
			sweep(level, close);
		}
		if (breathe && this.breathIn <= 0 && distance > reach + 1.0 && distance < breathRange() && this.breathTicks == 0) {
			this.breathIn = GROUND_BREATH_EVERY;
			this.breathTicks = BREATH_TICKS / 2;
		}
	}

	private void sweep(ServerLevel level, List<LivingEntity> hit) {
		level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0F, 0.5F);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY() + 1.0, this.getZ(), 6, 2.5, 0.3, 2.5, 0.0);
		for (LivingEntity e : hit) {
			if (e.hurtServer(level, this.damageSources().mobAttack(this), SWEEP)) {
				Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0).normalize().scale(SWEEP_THROW);
				e.push(away.x, 0.45, away.z);
				e.syncVelocity = true;
			}
		}
	}

	private void claw(ServerLevel level, LivingEntity target) {
		if (target.hurtServer(level, this.damageSources().mobAttack(this), CLAW)) {
			Vec3 v = this.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize().scale(SWOOP_THROW);
			target.push(v.x, 0.6, v.z);
			target.syncVelocity = true;
		}
		level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, flapVolume() * 1.5F, 0.7F);
	}

	private Vec3 breathAim() {
		LivingEntity target = this.foe;
		if (target != null && target.isAlive()) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(mouth());
			if (to.lengthSqr() > 0.01) {
				return to.normalize();
			}
		}
		return Vec3.directionFromRotation(this.getXRot(), this.getYHeadRot());
	}

	protected void lookAt(LivingEntity target) {
		Vec3 to = target.getEyePosition().subtract(this.position());
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float yawNow = approach(this.getYRot(), yaw, 8.0F);
		this.setYRot(yawNow);
		this.yBodyRot = yawNow;
		this.setYHeadRot(approach(this.getYHeadRot(), yaw, 15.0F));
		float pitch = (float)(-Mth.atan2(to.y - MOUTH_UP, to.horizontalDistance()) * Mth.RAD_TO_DEG);
		this.setXRot(Mth.clamp(pitch, -40.0F, 60.0F));
	}

	// ---- flight -------------------------------------------------------------------------------------------------------

	protected void setFlying(boolean on) {
		this.flying = on;
		this.setNoGravity(on);
	}

	protected void takeOff() {
		setFlying(true);
		this.getNavigation().stop();
		this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.7, 0.0));
		this.playSound(SoundEvents.ENDER_DRAGON_FLAP, flapVolume() * 1.5F, 0.6F);
	}

	protected void land() {
		setFlying(false);
		this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 1.0, 0.3));
		this.playSound(SoundEvents.RAVAGER_STEP, 2.0F, 0.5F);
	}

	/** Eases its velocity toward {@code point} at {@code speed}, keeping clear of the ground unless {@code landing}. */
	protected void flyToward(ServerLevel level, Vec3 point, double speed, boolean landing) {
		Vec3 to = point.subtract(this.position());
		double distance = to.length();
		Vec3 want = distance < 0.01 ? Vec3.ZERO : to.scale(Math.min(speed, distance * 0.25) / distance);
		if (!landing) {
			double floor = floorBelow(level, this.blockPosition());
			if (this.getY() < floor + MIN_CLEARANCE) {
				want = want.add(0.0, 0.25, 0.0);
			}
		}
		if (this.horizontalCollision) {
			want = want.add(0.0, 0.5, 0.0);
		}
		this.setDeltaMovement(this.getDeltaMovement().lerp(want, TURN_EASE));
		if (this.tickCount % 18 == 0 && want.y > -0.1) {
			this.playSound(SoundEvents.ENDER_DRAGON_FLAP, flapVolume(), 0.7F + this.random.nextFloat() * 0.1F);
		}
		face(this.getDeltaMovement());
	}

	private void face(Vec3 v) {
		if (v.horizontalDistanceSqr() < 0.0025) {
			return;
		}
		float yaw = (float)(Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
		float now = approach(this.getYRot(), yaw, 6.0F);
		this.setYRot(now);
		this.yBodyRot = now;
		this.setYHeadRot(now);
		this.setXRot((float)Mth.clamp(-v.y * 40.0, -30.0, 30.0));
	}

	private static float approach(float from, float to, float step) {
		return from + Mth.clamp(Mth.wrapDegrees(to - from), -step, step);
	}

	// ---- the crash and the taming --------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (!hurt || this.isTamed() || !this.isAlive()) {
			return hurt;
		}
		LivingEntity attacker = source.getEntity() instanceof LivingEntity l ? l : null;
		if (grounded()) {
			if (attacker != null) {
				distrust(level, attacker, HURT_DRAIN);
			}
		} else if (this.getHealth() < this.getMaxHealth() * CRASH_AT) {
			crash(level);
		} else if (attacker != null && valid(attacker) && this.foe == null) {
			this.foe = attacker;
		}
		return hurt;
	}

	/** Worn down: it falls out of the sky, and stays down a while. */
	private void crash(ServerLevel level) {
		setFlying(false);
		setDragonFlag(GROUNDED, true);
		this.groundedLeft = GROUNDED_TICKS;
		this.breathTicks = 0;
		this.foe = null;
		this.setTarget(null);
		this.entityData.set(DATA_TRUST, 0.0F);
		this.entityData.set(DATA_TAMER, -1);
		this.tamer = null;
		this.wild = Wild.CIRCLE;
		level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 4.0F, 0.5F);
		tell(level, "message.jaymoskingdoms.dragon.crashed", ChatFormatting.GOLD);
	}

	private void groundedAi(ServerLevel level) {
		this.setNoGravity(false);
		if (--this.groundedLeft <= 0) {
			bolt(level);
			return;
		}
		LivingEntity lash = this.foe;
		if (lash != null && this.tickCount < this.lashUntil && valid(lash)) {
			groundFight(level, lash, false);
		} else {
			this.foe = null;
			this.getNavigation().stop();
			Player near = level.getNearestPlayer(this, TAME_RANGE * 2.0);
			if (near != null) {
				// warily watching the nearest
				Vec3 to = near.position().subtract(this.position());
				this.setYHeadRot(approach(this.getYHeadRot(), (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F, 6.0F));
			}
		}
		if (this.tickCount % 5 == 0) {
			taming(level);
		}
		if (this.tickCount % 30 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 1.5, this.getZ(), 3, 0.8, 0.3, 0.8, 0.01);
		}
	}

	/** Every quarter second: who's near, and what they're doing to its trust. */
	private void taming(ServerLevel level) {
		float step = 5.0F / 20.0F;
		boolean calmed = false;
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(SPRINT_RANGE), p -> p.isAlive() && !p.isSpectator())) {
			double distance = player.distanceTo(this);
			if (armed(player) && distance < TAME_RANGE + 2.0) {
				distrust(level, player, WEAPON_DRAIN * step);
			} else if (player.isSprinting() && distance < SPRINT_RANGE && player.getDeltaMovement().horizontalDistanceSqr() > 0.0
				&& player.getDeltaMovement().dot(this.position().subtract(player.position())) > 0.0) {
				distrust(level, player, SPRINT_DRAIN * step);
			} else if (player.isShiftKeyDown() && distance < TAME_RANGE && openHanded(player)) {
				calmed = true;
				gainTrust(level, player, CALM_TRUST * step);
			}
		}
		if (!calmed && trust() > 0.0F) {
			this.entityData.set(DATA_TRUST, Math.max(0.0F, trust() - DECAY * step));
		}
	}

	private static boolean armed(Player player) {
		for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
			if (stack.is(ItemTags.WEAPON_ENCHANTABLE) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT)) {
				return true;
			}
		}
		return false;
	}

	/** Empty hands, or holding out its favourite food. */
	private boolean openHanded(Player player) {
		ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
		return (main.isEmpty() || favourite(main)) && (off.isEmpty() || favourite(off));
	}

	private void gainTrust(ServerLevel level, Player player, float amount) {
		amount *= bondFactor(player);
		float now = Math.min(TRUST_FULL, trust() + amount);
		this.entityData.set(DATA_TRUST, now);
		this.entityData.set(DATA_TAMER, player.getId());
		this.tamer = player.getUUID();
		if (now >= TRUST_FULL) {
			tame(level, player);
		}
	}

	/** {@code who} upset it: trust drains, it lashes out at them, and if the meter empties it bolts. */
	private void distrust(ServerLevel level, LivingEntity who, float amount) {
		float was = trust();
		float now = Math.max(0.0F, was - amount);
		this.entityData.set(DATA_TRUST, now);
		if (this.tickCount >= this.lashUntil || this.foe != who) {
			level.playSound(null, this.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.7F);
		}
		this.foe = who;
		this.lashUntil = this.tickCount + LASH_TICKS;
		if (was > 0.0F && now <= 0.0F) {
			bolt(level);
		}
	}

	/** It's had enough: it heals, takes to the air and flies off. */
	private void bolt(ServerLevel level) {
		setDragonFlag(GROUNDED, false);
		this.setHealth(Math.max(this.getHealth(), this.getMaxHealth() * BOLT_HEAL));
		this.entityData.set(DATA_TRUST, 0.0F);
		this.entityData.set(DATA_TAMER, -1);
		this.tamer = null;
		this.foe = null;
		this.setTarget(null);
		takeOff();
		this.wild = Wild.FLEE;
		this.wildTicks = 0;
		tell(level, "message.jaymoskingdoms.dragon.bolted", ChatFormatting.GOLD);
	}

	private void tame(ServerLevel level, Player player) {
		this.tameWithName(player);
		this.setPersistenceRequired();
		setDragonFlag(GROUNDED, false);
		this.entityData.set(DATA_TAMER, -1);
		setStamina(STAMINA);
		this.foe = null;
		this.setTarget(null);
		this.breathTicks = 0;
		this.heal(this.getMaxHealth() * 0.25F);
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
		level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 2.5, this.getZ(), 10, 1.0, 0.5, 1.0, 0.0);
		level.playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.NEUTRAL, 1.5F, 1.2F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.dragon.tamed", this.getDisplayName()).withStyle(ChatFormatting.GOLD));
	}

	private void tell(ServerLevel level, String key, ChatFormatting style) {
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(this) < 48.0 * 48.0) {
				player.sendOverlayMessage(Component.translatable(key, this.getDisplayName()).withStyle(style));
			}
		}
	}

	private void boss(ServerLevel level) {
		if (this.bar == null) {
			this.bar = new ServerBossEvent(this.getUUID(), this.getDisplayName(), barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		com.jaymoskingdoms.BossBars.show(level, this, this.bar);
	}

	// ---- tamed ----------------------------------------------------------------------------------------------------------

	private void tamedAi(ServerLevel level) {
		this.setNoGravity(false);
		if (sitting()) {
			this.getNavigation().stop();
			return;
		}
		LivingEntity owner = this.getOwner();
		if (owner == null || owner.level() != this.level()) {
			return;
		}
		// it takes on whoever hurts its owner
		LivingEntity threat = owner.getLastHurtByMob();
		if (threat != null && threat != this && threat.isAlive() && threat.distanceToSqr(this) < 16.0 * 16.0 && !DragonBreath.spared(this, threat)) {
			groundFight(level, threat, false);
			return;
		}
		double distance = this.distanceTo(owner);
		if (distance > FOLLOW_TELEPORT) {
			BlockPos at = owner.blockPosition().offset(this.random.nextInt(7) - 3, 0, this.random.nextInt(7) - 3);
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
			if (Math.abs(ground.getY() - owner.getBlockY()) < 6) {
				this.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
				this.getNavigation().stop();
			}
		} else if (distance > FOLLOW_START) {
			this.getNavigation().moveTo(owner, 1.2);
		} else if (distance < FOLLOW_STOP) {
			this.getNavigation().stop();
		}
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean server = !this.level().isClientSide();
		if (!this.isTamed()) {
			if (grounded() && favourite(stack)) {
				if (server && this.level() instanceof ServerLevel level && this.tickCount - this.offeredAt >= OFFER_EVERY) {
					this.offeredAt = this.tickCount;
					stack.consume(1, player);
					this.playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 0.6F);
					level.sendParticles(ParticleTypes.SMOKE, this.mouth().x, this.mouth().y, this.mouth().z, 4, 0.2, 0.2, 0.2, 0.01);
					gainTrust(level, player, OFFER_TRUST);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (stack.is(ItemTags.MEAT) && (stamina() < STAMINA - 5.0F || this.getHealth() < this.getMaxHealth())) {
			if (server) {
				setStamina(stamina() + (favourite(stack) ? FAVOURITE_STAMINA : MEAT_STAMINA));
				this.heal(MEAL_HEAL);
				stack.consume(1, player);
				this.playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 0.6F);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.SADDLE) && !saddled() && !this.isVehicle()) {
			if (server) {
				this.setItemSlot(EquipmentSlot.SADDLE, stack.split(1));
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (stack.isEmpty()) {
				if (server && owner(player)) {
					setDragonFlag(SITTING, !sitting());
					player.sendOverlayMessage(Component.translatable(sitting() ? "message.jaymoskingdoms.dragon.sits" : "message.jaymoskingdoms.dragon.follows",
						this.getDisplayName()));
				}
			} else {
				this.openCustomInventoryScreen(player);
			}
			return InteractionResult.SUCCESS;
		}
		if (!server) {
			return InteractionResult.SUCCESS;
		}
		if (player.isPassenger() || !saddled()) {
			return InteractionResult.PASS;
		}
		if (!this.isVehicle()) {
			if (!owner(player)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.dragon.not_yours", this.getDisplayName()));
				return InteractionResult.SUCCESS;
			}
			setDragonFlag(SITTING, false);
			player.setYRot(this.getYRot());
			player.setXRot(this.getXRot());
			player.startRiding(this);
		} else if (this.getPassengers().size() < 2 && this.getControllingPassenger() instanceof Player rider && owner(rider)) {
			// a piggyback rider behind the owner
			player.startRiding(this);
		}
		return InteractionResult.SUCCESS;
	}

	protected boolean owner(Player player) {
		var owner = this.getOwnerReference();
		return owner != null && owner.getUUID().equals(player.getUUID());
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < 2;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int index = Math.max(this.getPassengers().indexOf(passenger), 0);
		double sink = flag(SITTING) || grounded() ? 0.5 : 0.0;
		Vec3 seat = seat(index);
		return new Vec3(0.0, (seat.y - sink) * scale, seat.z * scale).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
	}

	// ---- the client's animation -----------------------------------------------------------------------------------------

	private void animate() {
		this.flyAnimO = this.flyAnim;
		this.breathAnimO = this.breathAnim;
		this.landAnimO = this.landAnim;
		this.sitAnimO = this.sitAnim;
		this.crashAnimO = this.crashAnim;
		this.flapPhaseO = this.flapPhase;
		this.glideAnimO = this.glideAnim;
		this.pitchAnimO = this.pitchAnim;
		this.swimAnimO = this.swimAnim;
		boolean air = this.airborne();
		if (this.flyAnim > 0.5F && !air && this.onGround()) {
			this.landAnim = 1.0F;
		}
		this.flyAnim = Mth.approach(this.flyAnim, air ? 1.0F : 0.0F, 0.1F);
		this.breathAnim = Mth.approach(this.breathAnim, flag(BREATHING) ? 1.0F : 0.0F, 0.2F);
		this.landAnim = Math.max(0.0F, this.landAnim - 0.06F);
		this.sitAnim = Mth.approach(this.sitAnim, sitting() && !this.isVehicle() ? 1.0F : 0.0F, 0.08F);
		this.crashAnim = Mth.approach(this.crashAnim, grounded() ? 1.0F : 0.0F, 0.08F);
		Vec3 v = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
		// fast and level or falling: it glides; climbing, hovering or slow: it beats hard
		boolean gliding = v.horizontalDistance() > 0.45 && v.y < 0.02 && !flag(BREATHING);
		this.glideAnim = Mth.approach(this.glideAnim, gliding ? 1.0F : 0.0F, 0.05F);
		float rate = air ? (v.y > 0.05 ? 0.42F : flag(BREATHING) ? 0.3F : 0.32F) * (1.0F - this.glideAnim * 0.7F) : 0.08F;
		this.flapPhase += rate;
		float pitch = air ? (float)Mth.clamp(-v.y * 1.2, -0.6, 0.6) : 0.0F;
		this.pitchAnim += (pitch - this.pitchAnim) * 0.2F;
		this.swimAnim = Mth.approach(this.swimAnim, this.isInWater() && !air ? 1.0F : 0.0F, 0.1F);
	}

	// ---- the rest -------------------------------------------------------------------------------------------------------

	@Override
	public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return !this.isTamed() && !this.hasCustomName();
	}

	/** Only its owner steers it: a piggyback rider left alone in the saddle can't fly or breathe, so it lands. */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		LivingEntity rider = super.getControllingPassenger();
		return rider instanceof Player player && owner(player) ? rider : null;
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		if (this.bar != null) {
			this.bar.removePlayer(player);
		}
	}

	@Override
	public boolean canMate(Animal partner) {
		return false;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public void die(DamageSource source) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
		}
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (this.bar != null) {
			this.bar.removeAllPlayers();
		}
		super.remove(reason);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("dragon_look", this.entityData.get(DATA_LOOK));
		output.putFloat("dragon_stamina", stamina());
		output.putInt("dragon_flags", this.entityData.get(DATA_FLAGS) & (GROUNDED | SITTING));
		output.putInt("dragon_grounded", this.groundedLeft);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		int look = input.getIntOr("dragon_look", -1);
		this.entityData.set(DATA_LOOK, look < 0 ? DragonLook.roll(this.random).encode() : look);
		setStamina(input.getFloatOr("dragon_stamina", STAMINA));
		this.entityData.set(DATA_FLAGS, (byte)input.getIntOr("dragon_flags", 0));
		this.groundedLeft = input.getIntOr("dragon_grounded", 0);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENDER_DRAGON_GROWL;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENDER_DRAGON_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.RAVAGER_DEATH;
	}

	@Override
	protected @Nullable SoundEvent getAngrySound() {
		return SoundEvents.RAVAGER_ROAR;
	}

	@Override
	protected @Nullable SoundEvent getEatingSound() {
		return SoundEvents.GENERIC_EAT.value();
	}

	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * 0.55F;
	}

	@Override
	protected float getSoundVolume() {
		return 1.6F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		if (!state.liquid()) {
			this.playSound(SoundEvents.RAVAGER_STEP, 0.6F, 0.5F);
		}
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}
}
