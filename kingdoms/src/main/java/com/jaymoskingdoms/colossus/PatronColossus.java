package com.jaymoskingdoms.colossus;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-23): "I want that figure to appear, larger than any giant". A patron's colossus: a vast faceless figure,
 * pale as cloud, with its patron's marks (the Truth's bare), {@link #HEIGHT} blocks tall. It is never solid, never hit,
 * never saved; {@link Colossi} makes it and moves it each tick (so it walks even out where nothing else ticks).
 * <ul>
 * <li>{@link #STRIKE}: called by a Paladin, Cleric or Warlock; it rises over the spot, lifts its foot and stamps once.
 * Everyone sees it.</li>
 * <li>{@link #OMEN}: it stands far off and watches the one who broke its commandments, then fades.</li>
 * <li>{@link #HUNT}: it walks at them, very slowly; if it reaches them it stamps, and they die.</li>
 * </ul>
 * An omen or a hunt is sent only to the pious (anyone sworn to a patron); nobody else's client ever hears of it.
 */
public final class PatronColossus extends Entity {
	/** How tall it stands (blocks): the Aether's Giants are 30.6, the biggest of them twice that. */
	public static final float HEIGHT = 80.0F;
	/** What it is doing. */
	public static final int STRIKE = 0, OMEN = 1, HUNT = 2;
	/** The stamp: ticks the foot takes to rise, and to come down. */
	public static final int LIFT_TICKS = 30, FALL_TICKS = 6;
	/** Ticks it takes to come and to go. */
	public static final int FADE_IN = 30, FADE_OUT = 40;

	public static EntityType<PatronColossus> TYPE;

	private static final EntityDataAccessor<Integer> PATRON = SynchedEntityData.defineId(PatronColossus.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(PatronColossus.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> WALKING = SynchedEntityData.defineId(PatronColossus.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> STAMPING = SynchedEntityData.defineId(PatronColossus.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> LEAVING = SynchedEntityData.defineId(PatronColossus.class, EntityDataSerializers.BOOLEAN);

	/** Server: the one it came for (an omen or a hunt) or who called it (a strike). */
	@Nullable UUID who;
	/** Server: ticks since it came, since it began to stamp (-1 not), since it began to go (-1 not). */
	int age, stamp = -1, gone = -1;
	/** Server: where a strike lands. */
	Vec3 spot = Vec3.ZERO;
	/** Client: the level time it was first seen, began to stamp, began to go. */
	public long seenAt = -1L, stampAt = -1L, goneAt = -1L;

	public PatronColossus(EntityType<? extends PatronColossus> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("patron_colossus"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<PatronColossus>of(PatronColossus::new, MobCategory.MISC)
			.noLootTable()
			.noSave()
			.noSummon()
			.fireImmune()
			.sized(2.0F, 4.0F)
			.clientTrackingRange(16)
			.updateInterval(2)
			.build(key));
	}

	static PatronColossus make(ServerLevel level, Patron patron, int mode, UUID who, Vec3 at, float yaw) {
		PatronColossus c = new PatronColossus(TYPE, level);
		c.entityData.set(PATRON, patron.ordinal());
		c.entityData.set(MODE, mode);
		c.who = who;
		c.setPos(at.x, at.y, at.z);
		c.setYRot(yaw);
		c.setOldPosAndRot();
		level.addFreshEntity(c);
		return c;
	}

	public Patron patron() {
		Patron[] all = Patron.values();
		return all[Mth.clamp(this.entityData.get(PATRON), 0, all.length - 1)];
	}

	public int mode() {
		return this.entityData.get(MODE);
	}

	public boolean walking() {
		return this.entityData.get(WALKING);
	}

	void walking(boolean on) {
		this.entityData.set(WALKING, on);
	}

	/** Begins the stamp. */
	void stamp() {
		if (this.stamp < 0) {
			this.stamp = 0;
			this.entityData.set(WALKING, false);
			this.entityData.set(STAMPING, true);
		}
	}

	boolean stamping() {
		return this.stamp >= 0;
	}

	/** Begins to go; it fades and is gone {@link #FADE_OUT} ticks later. */
	void leave() {
		if (this.gone < 0) {
			this.gone = 0;
			this.entityData.set(WALKING, false);
			this.entityData.set(LEAVING, true);
		}
	}

	boolean leaving() {
		return this.gone >= 0;
	}

	/** Turns to face {@code at}. */
	void face(Vec3 at) {
		double dx = at.x - this.getX(), dz = at.z - this.getZ();
		if (dx * dx + dz * dz > 1.0E-4) {
			this.setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
		}
	}

	/** Moves it to stand on the ground at {@code x, z} (keeping its height where the ground isn't loaded). */
	void standAt(ServerLevel level, double x, double z) {
		BlockPos column = BlockPos.containing(x, this.getY(), z);
		double y = level.isLoaded(column) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) : this.getY();
		this.setPos(x, y, z);
	}

	// ---- the client's clocks ----

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (!this.level().isClientSide()) {
			return;
		}
		long now = this.level().getGameTime();
		if (STAMPING.equals(accessor) && this.entityData.get(STAMPING) && this.stampAt < 0) {
			this.stampAt = now;
		}
		if (LEAVING.equals(accessor) && this.entityData.get(LEAVING) && this.goneAt < 0) {
			this.goneAt = now;
		}
	}

	// ---- who sees it ----

	@Override
	public boolean broadcastToPlayer(ServerPlayer player) {
		// a strike is seen by all; a punishment only by the pious
		return this.mode() == STRIKE || Patrons.of(player) != null;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return true;
	}

	// ---- nothing touches it ----

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PATRON, 0);
		builder.define(MODE, STRIKE);
		builder.define(WALKING, false);
		builder.define(STAMPING, false);
		builder.define(LEAVING, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
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
	public boolean canBeCollidedWith(@Nullable Entity entity) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}
}
