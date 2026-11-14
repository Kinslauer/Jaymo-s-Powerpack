package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A severed head or limb (see {@link Dismember}): it flies off the body, tumbles, bounces and lies where it lands,
 * drawn as that part of whoever lost it (from {@link #look()}, a player's from their skin). A player's fallen head is
 * what they see through until they respawn. Gone after {@link #LIFETIME}.
 * <p>
 * Anyone can pick one up ({@link Carry}, hold {@link Carry#PART}): it dangles from their right hand (held by the hair, or
 * by the end of the limb) until it's dropped or thrown. A player's head, carried, is carried with their view.
 */
public class SeveredPart extends Entity {
	public static final int LIFETIME = 6000;
	/** How hard it flies off (blocks a tick), and up. */
	static final double FLING = 0.32, FLING_UP = 0.3;
	static final double GRAVITY = 0.05, BOUNCE = 0.35;
	/**
	 * Carried: where the hand is from the carrier's feet ({right, up, ahead}, blocks), how far below it the part's middle
	 * hangs (a head, a limb; for a body 1.8 tall), and how far it swings (degrees) as it's carried along.
	 */
	static final double HAND_RIGHT = 0.36, HAND_UP = 0.78, HAND_AHEAD = 0.3, HEAD_HANG = 0.23, LIMB_HANG = 0.33;
	static final float CARRY_SWAY = 9.0F;
	/**
	 * (0.10.47) Rolling and settling: rolling along the ground it turns this many degrees per block moved (about a
	 * hand's width across), until it's slower than ROLL_MIN; then it rocks onto its side on a damped spring.
	 */
	static final float ROLL_RATE = 380.0F, ROLL_MIN = 0.02F, SETTLE_PULL = 0.25F, SETTLE_DAMPING = 0.6F;
	/** (0.10.47) Walked into, it's kicked along this much faster than the walker goes, and up; at most this often. */
	static final double KICK = 1.3, KICK_UP = 0.14, KICK_MIN = 0.05;
	static final int KICK_EVERY = 8;
	/** The part's middle sits this high above the entity (as the renderer draws it). */
	static final double MIDDLE = 0.17;
	/** Saved data that has nothing to do with how the body looks (and gear, which the part doesn't carry). */
	private static final Set<String> DROPPED_KEYS = Set.of("Brain", "brain", "Offers", "offers", "Gossips", "gossips", "attributes",
		"Attributes", "active_effects", "ActiveEffects", "Passengers", "Leash", "leash", "equipment", "HandItems", "ArmorItems", "Inventory",
		"drop_chances", "HandDropChances", "ArmorDropChances", "Tags");
	private static final int MAX_LOOK = 30000;

	public static EntityType<SeveredPart> TYPE;

	private static final EntityDataAccessor<String> LOOK = SynchedEntityData.defineId(SeveredPart.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(SeveredPart.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> PART = SynchedEntityData.defineId(SeveredPart.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(SeveredPart.class, EntityDataSerializers.FLOAT);
	/** (0.15.148) Nailed to a wall by the arrow that took it off (Jaymo's Kingdoms' Archer): it hangs there until picked up. */
	private static final EntityDataAccessor<Boolean> PINNED = SynchedEntityData.defineId(SeveredPart.class, EntityDataSerializers.BOOLEAN);
	/** (0.15.148) Where the next head to come off goes instead of flying: nailed at this spot, facing this way (see {@link Dismember#beheadPinned}). */
	static @org.jspecify.annotations.Nullable Vec3 pinNextAt;
	static float pinNextYaw;

	private int age;
	/** (0.19.62) A head taken off a carcass on Jaymo's Kitchen's meat hook: kept, it never goes. */
	private boolean trophy;
	/** Client side: how far it has turned over (degrees), this tick and last, and about which way it spins. */
	public float spin, spinO, spinAxis;
	/** Client side: how fast it's turning over (degrees a tick). */
	private float spinVel;
	private int kickCooldown;
	/** (0.21.50) The blood of whoever lost it (RGB), or {@code BladeBlood.NO_BLOOD}: a skeleton's head never drips. */
	private int blood = 0x7A0000;
	/** Bites taken out of it so far, and what it came off (looked up from {@link #look()} once). */
	private int bitten;
	private @org.jspecify.annotations.Nullable EntityType<?> was;
	private boolean wasLooked;

	/** Bites until a limb, or a head, is eaten; the chance a bone is left of it, and how hard each bite tugs it. */
	static final int BITES_LIMB = 3, BITES_HEAD = 4;
	static final float BONE_LEFT = 0.5F;
	static final double TUG = 0.12;

	public SeveredPart(EntityType<? extends SeveredPart> type, Level level) {
		super(type, level);
		this.spinAxis = this.random.nextFloat() * 360.0F;
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("severed_part"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<SeveredPart>of(SeveredPart::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.35F, 0.35F)
			.eyeHeight(0.18F)
			.clientTrackingRange(8)
			.updateInterval(2)
			.build(key));
	}

	/** {@code part} comes off {@code body}, flung away from {@code from}. */
	static void spawn(ServerLevel level, LivingEntity body, int part, Vec3 from) {
		SeveredPart severed = new SeveredPart(TYPE, level);
		Vec3 at = Dismember.stump(body, part);
		severed.setPos(at.x, at.y - 0.15, at.z);
		Vec3 away = at.subtract(from).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = Vec3.directionFromRotation(0.0F, body.getRandom().nextFloat() * 360.0F);
		}
		away = away.normalize().scale(FLING * (0.7 + body.getRandom().nextDouble() * 0.6));
		severed.setDeltaMovement(away.x, FLING_UP * (0.7 + body.getRandom().nextDouble() * 0.6), away.z);
		severed.setYRot(body.yBodyRot);
		severed.entityData.set(PART, part);
		severed.entityData.set(HEIGHT, body.getBbHeight());
		severed.blood = BladeBlood.bleeds(body) ? BladeBlood.color(body) : BladeBlood.NO_BLOOD;
		if (part == Dismember.HEAD && pinNextAt != null) {
			// (0.15.148) the arrow carries it into the wall behind and nails it there
			severed.setPos(pinNextAt.x, pinNextAt.y - MIDDLE, pinNextAt.z);
			severed.setDeltaMovement(Vec3.ZERO);
			severed.setYRot(pinNextYaw);
			severed.entityData.set(PINNED, true);
			severed.setNoGravity(true);
			pinNextAt = null;
		}
		if (body instanceof ServerPlayer player) {
			severed.entityData.set(OWNER, player.getUUID().toString());
			severed.entityData.set(LOOK, "{id:\"minecraft:player\",Name:\"" + player.getGameProfile().name() + "\"}");
		} else {
			severed.entityData.set(LOOK, look(body));
		}
		level.addFreshEntity(severed);
	}

	/**
	 * (0.19.62) A head off a butchered carcass (Jaymo's Kitchen's meat hook, {@code Hanging}): drawn from the corpse's
	 * {@code look}, falling from {@code at}; kept for good (a trophy), not gone after {@link #LIFETIME}.
	 */
	public static void dropTrophyHead(ServerLevel level, String look, float height, float yaw, Vec3 at) {
		SeveredPart severed = new SeveredPart(TYPE, level);
		severed.setPos(at.x, at.y - 0.15, at.z);
		severed.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.1, 0.05, (level.getRandom().nextDouble() - 0.5) * 0.1);
		severed.setYRot(yaw);
		severed.entityData.set(PART, Dismember.HEAD);
		severed.entityData.set(HEIGHT, height);
		severed.entityData.set(LOOK, look.length() > MAX_LOOK ? "{}" : look);
		severed.trophy = true;
		level.addFreshEntity(severed);
	}

	/**
	 * A part come off a body already lying dead (Jaymo's Kitchen: a skeleton falling to pieces, a rotten limb torn away,
	 * a corpse struck where it lies): drawn from the corpse's {@code look}, at {@code at}, flung with {@code velocity};
	 * {@code blood} is the body's blood colour, or {@code BladeBlood.NO_BLOOD}.
	 */
	public static SeveredPart drop(ServerLevel level, String look, int part, float height, float yaw, Vec3 at, Vec3 velocity, int blood) {
		SeveredPart severed = new SeveredPart(TYPE, level);
		severed.setPos(at.x, at.y, at.z);
		severed.setDeltaMovement(velocity);
		severed.setYRot(yaw);
		severed.entityData.set(PART, part);
		severed.entityData.set(HEIGHT, height);
		severed.entityData.set(LOOK, look.length() > MAX_LOOK ? "{}" : look);
		severed.blood = blood;
		level.addFreshEntity(severed);
		return severed;
	}

	/** How {@code body} looks, as saved data (without its gear and the things that don't show). */
	static String look(LivingEntity body) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, body.registryAccess());
		body.saveWithoutId(output);
		CompoundTag tag = output.buildResult();
		for (String key : DROPPED_KEYS) {
			tag.remove(key);
		}
		tag.putString("id", EntityType.getKey(body.getType()).toString());
		String look = tag.toString();
		if (look.length() > MAX_LOOK) {
			CompoundTag minimal = new CompoundTag();
			minimal.putString("id", EntityType.getKey(body.getType()).toString());
			look = minimal.toString();
		}
		return look;
	}

	public String look() {
		return this.entityData.get(LOOK);
	}

	/**
	 * (0.19.62) Andy: "wall trophy heads". A carried head set down while looking at a wall goes up on it (see
	 * {@link Carry}): nailed there facing out, at {@code at} on the face, and kept for good like a hook's trophy head.
	 */
	void mountOn(Vec3 at, float yaw) {
		this.setPos(at.x, at.y - MIDDLE, at.z);
		this.setDeltaMovement(Vec3.ZERO);
		this.setYRot(yaw);
		this.entityData.set(PINNED, true);
		this.setNoGravity(true);
		this.trophy = true;
		this.age = 200;
	}

	/** (0.15.148) Whether it's nailed to a wall. */
	public boolean pinned() {
		return this.entityData.get(PINNED);
	}

	public int part() {
		return this.entityData.get(PART);
	}

	/** The height of the body it came off (for drawing it at the right size). */
	public float bodyHeight() {
		return this.entityData.get(HEIGHT);
	}

	/** The player it came off, if a player. */
	public Optional<UUID> owner() {
		String id = this.entityData.get(OWNER);
		if (id.isEmpty()) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/** A cut-off part lying on the ground leaks for this long (ticks), at first every {@link #LEAK_FIRST} ticks, at the end every {@link #LEAK_LAST}. */
	static final int LEAK_TICKS = 600, LEAK_FIRST = 20, LEAK_LAST = 90;
	static final float LEAK_STAIN = 0.15F;

	/** How often it leaks now: more seldom as it crusts over. */
	private int leakEvery() {
		float t = Mth.clamp(this.age / (float) LEAK_TICKS, 0.0F, 1.0F);
		return Math.max(1, Math.round(Mth.lerp(t, LEAK_FIRST, LEAK_LAST)));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getVehicle() != null) {
			// in a carrion bird's talons (LimbEaters): it hangs under the bird, swaying as it flies
			this.setDeltaMovement(Vec3.ZERO);
			this.resetFallDistance();
			if (this.level().isClientSide()) {
				this.spinO = this.spin;
				this.spin = Mth.sin(this.tickCount * 0.25F) * CARRY_SWAY;
			}
			return;
		}
		Player carrier = Carry.carrierOf(this);
		if (carrier != null) {
			if (pinned()) {
				// pulled off the wall
				this.entityData.set(PINNED, false);
				this.setNoGravity(false);
			}
			carried(carrier);
			return;
		}
		if (pinned()) {
			// (0.15.148) nailed to the wall: it stays put, dripping a while, until someone takes it down
			this.setDeltaMovement(Vec3.ZERO);
			if (this.level() instanceof ServerLevel level) {
				if (++this.age > LIFETIME && !this.trophy) {
					this.discard();
					return;
				}
				if (this.age < 200 && this.age % 8 == 0 && this.blood != BladeBlood.NO_BLOOD) {
					level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(this.blood, 1.0F), this.getX(), this.getY(), this.getZ(), 1, 0.03, 0.0, 0.03, 0.0);
				}
			} else {
				this.spinO = this.spin;
				this.spinVel = 0.0F;
			}
			return;
		}
		this.setDeltaMovement(this.getDeltaMovement().add(0.0, -GRAVITY, 0.0));
		Vec3 before = this.getDeltaMovement();
		this.move(MoverType.SELF, before);
		if (this.verticalCollision && before.y < -0.15) {
			// a thud and a little bounce
			this.setDeltaMovement(this.getDeltaMovement().x, -before.y * BOUNCE, this.getDeltaMovement().z);
		}
		if (this.horizontalCollision) {
			this.setDeltaMovement(-before.x * BOUNCE, this.getDeltaMovement().y, -before.z * BOUNCE);
		}
		this.setDeltaMovement(this.getDeltaMovement().scale(this.onGround() ? 0.55 : 0.98));
		if (this.level() instanceof ServerLevel level) {
			if (++this.age > LIFETIME && !this.trophy) {
				this.discard();
				return;
			}
			if (this.onGround() && --this.kickCooldown <= 0) {
				kickedBy(level);
			}
			if (this.age < 40 && this.age % 3 == 0 && this.blood != BladeBlood.NO_BLOOD) {
				// a trail of blood off the cut
				level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(this.blood, 1.0F), this.getX(), this.getY() + 0.1, this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
			} else if (this.age < LEAK_TICKS && this.onGround() && this.blood != BladeBlood.NO_BLOOD && this.age % leakEvery() == 0) {
				// Andy's Cruor pick: lying still, it goes on leaking from the cut for a while, slower and slower, into a
				// small pool that lasts (BloodTrail), then crusts over
				level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(this.blood, 0.8F), this.getX(), this.getY() + 0.05, this.getZ(), 1, 0.04, 0.0, 0.04, 0.0);
				com.jaymospowers.core.blood.BloodTrail.bleed(level, this, this.blood, 1, 0.1, LEAK_STAIN, 4);
			}
		} else {
			this.spinO = this.spin;
			Vec3 motion = this.getDeltaMovement();
			float rolling = (float) motion.horizontalDistance();
			if (!this.onGround()) {
				this.spinVel = (float) Math.min(30.0, motion.length() * 45.0);
			} else if (rolling > ROLL_MIN) {
				// rolling along the ground, as far round as it goes
				this.spinVel = rolling * ROLL_RATE;
			} else {
				// rocks to a stop, a limb lying along the ground, a head on any face (not on end, for a limb)
				float rest = this.part() == Dismember.HEAD ? Math.round(this.spin / 90.0F) * 90.0F
					: Math.round((this.spin - 90.0F) / 180.0F) * 180.0F + 90.0F;
				this.spinVel = (this.spinVel + Mth.wrapDegrees(rest - this.spin) * SETTLE_PULL) * SETTLE_DAMPING;
			}
			this.spin += this.spinVel;
		}
	}

	/** What it came off (the body's kind, from its look), or null if that can't be told. */
	public EntityType<?> bodyType() {
		if (!this.wasLooked) {
			this.wasLooked = true;
			try {
				CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(look());
				net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(tag.getStringOr("id", ""));
				this.was = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
			} catch (Exception e) {
				this.was = null;
			}
		}
		return this.was;
	}

	/**
	 * Whether something may eat it (Andy: "everything should be able to be eaten"): lying loose (not carried, not nailed
	 * up, not a kept trophy, not burning), with flesh on it, rotten or not (not a skeleton's bones, not a machine's), and
	 * not a dead player's head they're still looking out of (once they're back, it's food like any other).
	 */
	public boolean edible() {
		if (this.isRemoved() || pinned() || this.trophy || this.isOnFire() || this.isPassenger() || Carry.carrierOf(this) != null
			|| this.blood == BladeBlood.NO_BLOOD) {
			return false;
		}
		if (part() == Dismember.HEAD && this.level().getServer() != null) {
			Optional<UUID> owner = owner();
			if (owner.isPresent() && this.level().getServer().getPlayerList().getPlayer(owner.get()) instanceof ServerPlayer player && !player.isAlive()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * {@code eater} bites into it: it's tugged toward the jaws, blood flies, and it's chewed. True when that was the last
	 * of it: it's gone, and sometimes a bone is left on the ground.
	 */
	public boolean bite(ServerLevel level, Entity eater) {
		Vec3 toEater = eater.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
		if (toEater.lengthSqr() > 1.0E-4) {
			Vec3 tug = toEater.normalize().scale(TUG);
			this.setDeltaMovement(this.getDeltaMovement().add(tug.x, 0.08, tug.z));
			this.needsSync = true;
		}
		this.age = Math.max(this.age, LEAK_TICKS);
		float pitch = 0.7F + this.random.nextFloat() * 0.3F;
		if (this.blood != BladeBlood.NO_BLOOD) {
			level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(this.blood, 1.0F), this.getX(), this.getY() + 0.15, this.getZ(), 6, 0.12, 0.08, 0.12, 0.05);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(),
			net.minecraft.sounds.SoundSource.NEUTRAL, 0.9F, pitch);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.SLIME_SQUISH_SMALL,
			net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, pitch);
		if (++this.bitten < (part() == Dismember.HEAD ? BITES_HEAD : BITES_LIMB)) {
			return false;
		}
		// the last of it, bone and all: a crack, and sometimes the bone's left
		level.playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.BONE_BLOCK_BREAK,
			net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.2F);
		if (this.blood != BladeBlood.NO_BLOOD) {
			level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(this.blood, 1.0F), this.getX(), this.getY() + 0.1, this.getZ(), 12, 0.2, 0.08, 0.2, 0.08);
			com.jaymospowers.core.blood.BloodTrail.bleed(level, this, this.blood, 1, 0.15, LEAK_STAIN, 4);
		}
		if (this.random.nextFloat() < BONE_LEFT) {
			net.minecraft.world.entity.item.ItemEntity bone = new net.minecraft.world.entity.item.ItemEntity(level, this.getX(), this.getY() + 0.1, this.getZ(),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE));
			bone.setDefaultPickUpDelay();
			level.addFreshEntity(bone);
		}
		this.discard();
		return true;
	}

	/** (0.10.47) Someone walking into it on the ground kicks it along ahead of them, rolling and bouncing. */
	private void kickedBy(ServerLevel level) {
		for (Entity walker : level.getEntities(this, this.getBoundingBox().inflate(0.1),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
			double mx = walker.getX() - walker.xo, mz = walker.getZ() - walker.zo;
			double speed = Math.sqrt(mx * mx + mz * mz);
			if (speed > KICK_MIN) {
				this.setDeltaMovement(mx * KICK, KICK_UP + speed * 0.3, mz * KICK);
				this.needsSync = true;
				this.kickCooldown = KICK_EVERY;
				return;
			}
		}
	}

	/** Carried: it hangs from the carrier's right hand, swinging a little as they go (both sides, so it stays put). */
	private void carried(Player carrier) {
		float yaw = carrier.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		double hang = (part() == Dismember.HEAD ? HEAD_HANG : LIMB_HANG) * bodyHeight() / 1.8 + MIDDLE;
		Vec3 at = carrier.position().add(right.scale(HAND_RIGHT)).add(ahead.scale(HAND_AHEAD)).add(0.0, HAND_UP - hang, 0.0);
		this.setPos(at.x, at.y, at.z);
		this.setDeltaMovement(Vec3.ZERO);
		this.setYRot(carrier.yBodyRot);
		this.setOnGround(false);
		this.resetFallDistance();
		if (this.level().isClientSide()) {
			// upright as it was on the body, swinging with the carrier's stride
			float stride = carrier.walkAnimation.position();
			this.spinO = this.spin;
			this.spin = Mth.sin(stride * 0.6662F) * CARRY_SWAY * Math.min(1.0F, carrier.walkAnimation.speed() * 2.0F + 0.15F);
		}
	}

	/** Carried off in a bird's talons: it hangs this far below the bird's feet (blocks). */
	static final double TALON_HANG = 0.25;

	@Override
	public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
		// the bird's riding point is on its back: hang below its feet instead
		return new Vec3(0.0, vehicle.getPassengerRidingPosition(this).y - vehicle.getY() + TALON_HANG + MIDDLE, 0.0);
	}

	/** Grabbed up by {@code bird} to be carried off and eaten somewhere quiet. */
	public boolean snatchedBy(Entity bird) {
		if (!edible() || this.isPassenger()) {
			return false;
		}
		this.setNoGravity(false);
		return this.startRiding(bird, true, true);
	}

	@Override
	public boolean isPickable() {
		// it can be clicked on (to pick it up), though nothing hurts it
		return !this.isRemoved();
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LOOK, "");
		builder.define(OWNER, "");
		builder.define(PART, Dismember.HEAD);
		builder.define(HEIGHT, 1.8F);
		builder.define(PINNED, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(LOOK, input.getStringOr("Look", ""));
		this.entityData.set(OWNER, input.getStringOr("Owner", ""));
		this.entityData.set(PART, input.getIntOr("Part", Dismember.HEAD));
		this.entityData.set(HEIGHT, input.getFloatOr("BodyHeight", 1.8F));
		this.age = input.getIntOr("Age", 0);
		this.trophy = input.getBooleanOr("Trophy", false);
		this.blood = input.getIntOr("Blood", 0x7A0000);
		this.entityData.set(PINNED, input.getBooleanOr("Pinned", false));
		this.setNoGravity(pinned());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("Look", look());
		output.putString("Owner", this.entityData.get(OWNER));
		output.putInt("Part", part());
		output.putFloat("BodyHeight", bodyHeight());
		output.putInt("Age", this.age);
		output.putInt("Blood", this.blood);
		if (this.trophy) {
			output.putBoolean("Trophy", true);
		}
		output.putBoolean("Pinned", pinned());
	}
}
