package com.jaymoskitchen.corpse;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.18) A loose leg of a crushed spider ({@link SpiderCrush}): it flies out, tumbles, comes to rest, lies there for
 * {@link #LIFE_MIN}-{@link #LIFE_MAX} ticks and sinks away over its last {@link #SINK_TICKS}. Nothing to pick up or
 * grab. It keeps the spider's look, so the client draws it in that spider's own skin (a cave spider's, Kingdoms' kinds').
 */
public class SpiderLeg extends Entity {
	/** How long a leg lies about (ticks; 30 to 60 seconds), and how long of that it spends sinking away at the end. */
	public static final int LIFE_MIN = 600, LIFE_MAX = 1200, SINK_TICKS = 40;
	/** A cave spider's legs are drawn this much smaller (its renderer's own scale). */
	public static final float CAVE_SPIDER_SIZE = 0.7F;
	/** Falling: gravity and air drag a tick; on the ground, the speed kept a tick, and how much of a hard landing bounces. */
	static final double GRAVITY = 0.04, AIR_KEEP = 0.98, GROUND_KEEP = 0.6, BOUNCE = 0.3;
	/** Client: tumbling in the air, this many degrees a tick (at most; each leg its own). */
	static final float SPIN = 35.0F;

	private static final EntityDataAccessor<String> LOOK = SynchedEntityData.defineId(SpiderLeg.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(SpiderLeg.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(SpiderLeg.class, EntityDataSerializers.INT);

	/** Client: its tumble (degrees), last tick's and now. */
	public float spin, spinO;
	/** Client: the spider it came off, rebuilt from its look, for its skin (never added to the world). */
	private @Nullable Entity body;
	private boolean bodyTried;

	public SpiderLeg(EntityType<? extends SpiderLeg> type, Level level) {
		super(type, level);
	}

	/** Its spider's look ({@link Corpses#look}), size (1 = a spider's), and how long it lasts (ticks). */
	void setUp(String look, float size, int life) {
		this.entityData.set(LOOK, look);
		this.entityData.set(SIZE, size);
		this.entityData.set(LIFE, life);
	}

	public float size() {
		return this.entityData.get(SIZE);
	}

	/** How far it has sunk away, 0 (not yet) to 1 (gone), at {@code partialTicks}. */
	public float sunk(float partialTicks) {
		int life = this.entityData.get(LIFE);
		return Mth.clamp((this.tickCount + partialTicks - (life - SINK_TICKS)) / SINK_TICKS, 0.0F, 1.0F);
	}

	/** Client: the spider it came off, for drawing its skin; null if it can't be rebuilt. */
	public @Nullable Entity clientBody() {
		if (!this.bodyTried) {
			this.bodyTried = true;
			this.body = rebuild(level(), this.entityData.get(LOOK), -2000 - getId());
		}
		return this.body;
	}

	/** (0.5.21) The creature a {@code look} ({@link Corpses#look}) was taken from, rebuilt but never added to the world. */
	static @Nullable Entity rebuild(Level level, String look, int id) {
		CompoundTag tag = CorpseEntity.parse(look);
		Optional<EntityType<?>> type = CorpseEntity.typeOf(tag);
		if (type.isEmpty()) {
			return null;
		}
		Entity entity = type.get().create(level, EntitySpawnReason.LOAD);
		if (entity == null) {
			return null;
		}
		ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
		entity.load(input);
		if (entity instanceof LivingEntity living) {
			input.read("attributes", AttributeInstance.Packed.LIST_CODEC).ifPresent(living.getAttributes()::apply);
		}
		entity.setId(id);
		return entity;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && this.tickCount >= this.entityData.get(LIFE)) {
			discard();
			return;
		}
		applyGravity();
		Vec3 before = getDeltaMovement();
		move(MoverType.SELF, before);
		Vec3 v = getDeltaMovement();
		if (onGround()) {
			// a hard landing bounces a little; then it slides to a stop
			double up = before.y < -0.15 ? -before.y * BOUNCE : 0.0;
			setDeltaMovement(v.x * GROUND_KEEP, up, v.z * GROUND_KEEP);
		} else {
			setDeltaMovement(v.scale(AIR_KEEP));
		}
		if (level().isClientSide()) {
			this.spinO = this.spin;
			if (!onGround()) {
				// each leg tumbles its own way
				this.spin += SPIN * (0.5F + (Math.floorMod(getId() * 31, 100) / 100.0F) * 0.5F);
			} else {
				// it comes to rest lying flat
				float rest = Math.round(this.spin / 180.0F) * 180.0F;
				this.spin += (rest - this.spin) * 0.5F;
			}
		}
	}

	@Override
	protected double getDefaultGravity() {
		return GRAVITY;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LOOK, "");
		builder.define(SIZE, 1.0F);
		builder.define(LIFE, LIFE_MIN);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(LOOK, input.getStringOr("Look", ""));
		this.entityData.set(SIZE, input.getFloatOr("Size", 1.0F));
		this.entityData.set(LIFE, input.getIntOr("Life", LIFE_MIN));
		this.tickCount = input.getIntOr("Age", 0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("Look", this.entityData.get(LOOK));
		output.putFloat("Size", this.entityData.get(SIZE));
		output.putInt("Life", this.entityData.get(LIFE));
		output.putInt("Age", this.tickCount);
	}
}
