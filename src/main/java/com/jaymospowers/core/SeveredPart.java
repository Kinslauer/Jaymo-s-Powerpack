package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A severed head or limb (see {@link Dismember}): it flies off the body, tumbles, bounces and lies where it lands,
 * drawn as that part of whoever lost it (from {@link #look()}, a player's from their skin). A player's fallen head is
 * what they see through until they respawn. Gone after {@link #LIFETIME}.
 */
public class SeveredPart extends Entity {
	public static final int LIFETIME = 6000;
	/** How hard it flies off (blocks a tick), and up. */
	static final double FLING = 0.32, FLING_UP = 0.3;
	static final double GRAVITY = 0.05, BOUNCE = 0.35;
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

	private int age;
	/** Client side: how far it has turned over (degrees), this tick and last, and about which way it spins. */
	public float spin, spinO, spinAxis;

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
		if (body instanceof ServerPlayer player) {
			severed.entityData.set(OWNER, player.getUUID().toString());
			severed.entityData.set(LOOK, "{id:\"minecraft:player\",Name:\"" + player.getGameProfile().name() + "\"}");
		} else {
			severed.entityData.set(LOOK, look(body));
		}
		level.addFreshEntity(severed);
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

	@Override
	public void tick() {
		super.tick();
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
			if (++this.age > LIFETIME) {
				this.discard();
				return;
			}
			if (this.age < 40 && this.age % 3 == 0) {
				// a trail of blood off the cut
				level.sendParticles(new DustParticleOptions(0x7A0000, 1.0F), this.getX(), this.getY() + 0.1, this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
			}
		} else {
			this.spinO = this.spin;
			if (!this.onGround()) {
				this.spin += (float) Math.min(30.0, this.getDeltaMovement().length() * 45.0);
			} else {
				// settles on its side
				float rest = Math.round(this.spin / 90.0F) * 90.0F + 90.0F * (this.part() == Dismember.HEAD ? 1 : 0);
				this.spin = Mth.approachDegrees(this.spin, rest, 12.0F);
			}
		}
	}

	@Override
	public boolean isPickable() {
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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(LOOK, input.getStringOr("Look", ""));
		this.entityData.set(OWNER, input.getStringOr("Owner", ""));
		this.entityData.set(PART, input.getIntOr("Part", Dismember.HEAD));
		this.entityData.set(HEIGHT, input.getFloatOr("BodyHeight", 1.8F));
		this.age = input.getIntOr("Age", 0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("Look", look());
		output.putString("Owner", this.entityData.get(OWNER));
		output.putInt("Part", part());
		output.putFloat("BodyHeight", bodyHeight());
		output.putInt("Age", this.age);
	}
}
