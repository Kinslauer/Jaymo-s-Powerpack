package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.96, Xeno-Band patch 4) The stalker: near a monolith, at night, something stands at the edge of sight and
 * watches whoever wears a Xeno-Band. It's only ever there for one wearer ({@link #watcher}). Catch it with your eyes
 * (look straight at it) and it's gone; do it {@link #SIGHTINGS_TO_SHED} times (counted on the player,
 * {@link XenoformState#SIGHTINGS}) and the last time it leaves its skin behind instead: a still, empty husk
 * ({@link #shed}) the band can scan for the hidden alien Lurk. Nothing hurts it, it never fights, and it's never saved.
 */
public class XenoStalker extends PathfinderMob {
	// ---- tuning ----
	/** How often (ticks) each wearer is checked for a visit, and the chance of one then. */
	public static final int VISIT_EVERY = 200;
	public static final float VISIT_CHANCE = 0.2F;
	/** It only comes within this many blocks of a monolith's foot (Kingdoms'). */
	public static final double MONOLITH_RANGE = 160.0;
	/** How far off it stands (blocks), and how long it waits before leaving on its own (ticks). */
	public static final double STAND_MIN = 22.0, STAND_MAX = 34.0, LOST_PAST = 64.0;
	public static final int STAY_TICKS = 2400;
	/** How squarely you must look at it to catch it (the cosine), and how near. */
	public static final double CAUGHT_LOOK = 0.985, CAUGHT_RANGE = 56.0;
	public static final int SIGHTINGS_TO_SHED = 3;
	/** How long the shed skin lies there (ticks). */
	public static final int SHED_TICKS = 6000;

	public static EntityType<XenoStalker> TYPE;

	private static final EntityDataAccessor<Boolean> DATA_SHED = SynchedEntityData.defineId(XenoStalker.class, EntityDataSerializers.BOOLEAN);
	/** Each wearer's stalker now (entity id). */
	private static final Map<UUID, Integer> VISITING = new HashMap<>();
	private static @Nullable Method nearestMonolith;
	private static boolean lookedForMonoliths;

	private @Nullable UUID watcher;
	private int age;

	public XenoStalker(EntityType<? extends XenoStalker> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setSilent(true);
	}

	static void init() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("xeno_stalker"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<XenoStalker>of(XenoStalker::new, MobCategory.MISC)
				.sized(0.6F, 1.9F)
				.eyeHeight(1.7F)
				.clientTrackingRange(10)
				.noLootTable()
				.build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
		ServerTickEvents.END_LEVEL_TICK.register(XenoStalker::visits);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> VISITING.clear());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 40.0)
			.add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.SCALE, 1.05);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHED, false);
	}

	/** Whether this is the empty skin it left behind (scannable), not the stalker itself. */
	public boolean shed() {
		return this.entityData.get(DATA_SHED);
	}

	// ---- the visits ----------------------------------------------------------------------------------------------------

	/** Every so often at night, near a monolith, a wearer who hasn't found Lurk yet may be visited. */
	private static void visits(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD || level.getGameTime() % VISIT_EVERY != 0 || !level.isDarkOutside()) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !XenoformPower.has(player) || XenoformState.band(player).has(XenoForms.LURK.id)
				|| level.getEntity(VISITING.getOrDefault(player.getUUID(), -1)) instanceof XenoStalker
				|| player.getRandom().nextFloat() >= VISIT_CHANCE) {
				continue;
			}
			BlockPos monolith = nearestMonolith(level, player.blockPosition());
			if (monolith == null || monolith.distSqr(player.blockPosition()) > MONOLITH_RANGE * MONOLITH_RANGE) {
				continue;
			}
			visit(level, player);
		}
	}

	/** Stands it somewhere at the edge of {@code player}'s sight: behind them more often than not. */
	public static @Nullable XenoStalker visit(ServerLevel level, ServerPlayer player) {
		for (int tries = 0; tries < 8; tries++) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD + Mth.PI * (0.5F + player.getRandom().nextFloat());
			double d = STAND_MIN + player.getRandom().nextDouble() * (STAND_MAX - STAND_MIN);
			int x = Mth.floor(player.getX() - Mth.sin(yaw) * d);
			int z = Mth.floor(player.getZ() + Mth.cos(yaw) * d);
			if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, y, z);
			if (!level.getBlockState(at.below()).isSolid() || !level.getFluidState(at.below()).isEmpty() || Math.abs(y - player.getY()) > 16.0) {
				continue;
			}
			XenoStalker stalker = TYPE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
			if (stalker == null) {
				return null;
			}
			stalker.watcher = player.getUUID();
			stalker.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
			stalker.face(player);
			if (!level.addFreshEntity(stalker)) {
				return null;
			}
			VISITING.put(player.getUUID(), stalker.getId());
			return stalker;
		}
		return null;
	}

	private static @Nullable BlockPos nearestMonolith(ServerLevel level, BlockPos from) {
		if (!lookedForMonoliths) {
			lookedForMonoliths = true;
			try {
				nearestMonolith = Class.forName("com.jaymoskingdoms.remnants.Monoliths").getMethod("nearestFoot", ServerLevel.class, BlockPos.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				nearestMonolith = null;
			}
		}
		if (nearestMonolith == null) {
			return null;
		}
		try {
			return (BlockPos)nearestMonolith.invoke(null, level, from);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	// ---- its life ------------------------------------------------------------------------------------------------------

	@Override
	protected void registerGoals() {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.age++;
		ServerPlayer player = this.watcher == null ? null : level.getServer().getPlayerList().getPlayer(this.watcher);
		if (this.shed()) {
			// the husk lies there until it's scanned (or long after), then crumbles
			if (this.age > SHED_TICKS || player != null && XenoformState.band(player).has(XenoForms.LURK.id)) {
				crumble(level);
			}
			return;
		}
		if (player == null || player.level() != level || player.distanceTo(this) > LOST_PAST || this.age > STAY_TICKS || !level.isDarkOutside()) {
			leave(level, false);
			return;
		}
		face(player);
		if (caught(player)) {
			int seen = player.getAttachedOrElse(XenoformState.SIGHTINGS, 0) + 1;
			if (seen >= SIGHTINGS_TO_SHED) {
				player.removeAttached(XenoformState.SIGHTINGS);
				shedSkin(level);
			} else {
				player.setAttached(XenoformState.SIGHTINGS, seen);
				leave(level, true);
			}
		}
	}

	/** Whether {@code player} is looking straight at it, with nothing between. */
	private boolean caught(ServerPlayer player) {
		Vec3 to = this.getEyePosition().subtract(player.getEyePosition());
		double d = to.length();
		return d < CAUGHT_RANGE && d > 0.1 && to.scale(1.0 / d).dot(player.getViewVector(1.0F)) > CAUGHT_LOOK && player.hasLineOfSight(this);
	}

	private void face(ServerPlayer player) {
		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
		this.yBodyRot = yaw;
		double dy = player.getEyeY() - this.getEyeY();
		this.setXRot((float)(-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
	}

	/** It's gone: a blink of smoke if it was seen, nothing at all if not. */
	private void leave(ServerLevel level, boolean seen) {
		if (seen) {
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.3, 0.8, 0.3, 0.02);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6F, 0.5F);
		}
		if (this.watcher != null) {
			VISITING.remove(this.watcher);
		}
		this.discard();
	}

	/** The third time: it slips out of its skin and is gone, the husk left kneeling where it stood. */
	private void shedSkin(ServerLevel level) {
		this.entityData.set(DATA_SHED, true);
		this.age = 0;
		this.setPose(net.minecraft.world.entity.Pose.CROUCHING);
		level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.5F, 1.4F);
		if (this.watcher != null) {
			VISITING.remove(this.watcher);
		}
	}

	private void crumble(ServerLevel level) {
		level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 1.0, this.getZ(), 40, 0.3, 0.6, 0.3, 0.02);
		this.discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	/** Never written to disk: a reload ends a visit, and a husk. */
	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}
}
