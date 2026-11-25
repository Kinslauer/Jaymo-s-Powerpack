package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A cluster of sharp roots that bursts out of the ground, like evoker fangs: it waits {@code warmup} ticks (the
 * ground cracks and spores seep up where it will break through), erupts, hits whatever stands on it at tick 6
 * (never its owner or fungal creatures) and sinks back by tick 22. Harmless spikes are only for show.
 */
public class RootSpikeEntity extends Entity implements TraceableEntity {
	public static final int HIT_TICK = 6;
	public static final int LIFETIME = 22;
	private static final byte EVENT_ERUPT = 4;

	public final AnimationState eruptState = new AnimationState();
	private int warmup;
	private int life;
	private boolean erupted;
	private boolean clientErupted;
	private float damage = 8.0F;
	private int rootedTicks = 40;
	private boolean harmless;
	@Nullable
	private UUID ownerId;
	@Nullable
	private LivingEntity owner;

	public RootSpikeEntity(EntityType<? extends RootSpikeEntity> type, Level level) {
		super(type, level);
	}

	public RootSpikeEntity(Level level, double x, double y, double z, float yRot, int warmup, @Nullable LivingEntity owner,
						   float damage, int rootedTicks, boolean harmless) {
		this(GlowCreatures.ROOT_SPIKE, level);
		this.warmup = warmup;
		this.damage = damage;
		this.rootedTicks = rootedTicks;
		this.harmless = harmless;
		this.setOwner(owner);
		this.setYRot(yRot);
		this.setPos(x, y, z);
	}

	public void setOwner(@Nullable LivingEntity owner) {
		this.owner = owner;
		this.ownerId = owner == null ? null : owner.getUUID();
	}

	@Nullable
	@Override
	public LivingEntity getOwner() {
		if (this.owner == null && this.ownerId != null && this.level() instanceof ServerLevel server
				&& server.getEntity(this.ownerId) instanceof LivingEntity living) {
			this.owner = living;
		}
		return this.owner;
	}

	/** Client: true once the spike has broken through (the renderer draws nothing before). */
	public boolean hasErupted() {
		return this.clientErupted;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void readAdditionalSaveData(ValueInput tag) {
		this.warmup = tag.getIntOr("Warmup", 0);
		this.life = tag.getIntOr("Life", 0);
		this.erupted = tag.getBooleanOr("Erupted", false);
		this.damage = tag.getFloatOr("Damage", 0.0F);
		this.rootedTicks = tag.getIntOr("Rooted", 0);
		this.harmless = tag.getBooleanOr("Harmless", false);
		this.ownerId = tag.read("Owner", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	public void addAdditionalSaveData(ValueOutput tag) {
		tag.putInt("Warmup", this.warmup);
		tag.putInt("Life", this.life);
		tag.putBoolean("Erupted", this.erupted);
		tag.putFloat("Damage", this.damage);
		tag.putInt("Rooted", this.rootedTicks);
		tag.putBoolean("Harmless", this.harmless);
		tag.storeNullable("Owner", UUIDUtil.CODEC, this.ownerId);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel server)) {
			return;
		}
		if (!this.erupted) {
			if (--this.warmup > 0) {
				if (this.warmup % 2 == 0) {
					// The telegraph: the ground cracks and spores seep up where the root will break through.
					server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground()), this.getX(),
							this.getY() + 0.05, this.getZ(), 3, 0.25, 0.02, 0.25, 0.05);
					server.sendParticles(GlowFx.spore(), this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.2, 0.0,
							0.2, 0.01);
				}
				return;
			}
			this.erupted = true;
			this.level().broadcastEntityEvent(this, EVENT_ERUPT);
			if (!this.harmless || this.random.nextInt(3) == 0) {
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(), GlowSounds.ROOT_SPIKE_ERUPT,
						SoundSource.HOSTILE, 0.9F, 0.85F + this.random.nextFloat() * 0.3F);
			}
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground()), this.getX(), this.getY() + 0.2,
					this.getZ(), 14, 0.35, 0.2, 0.35, 0.15);
		}
		this.life++;
		if (this.life == HIT_TICK && !this.harmless) {
			hit(server);
		}
		if (this.life >= LIFETIME) {
			this.discard();
		}
	}

	private BlockState ground() {
		BlockState below = this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.5, this.getZ()));
		return below.isAir() ? Blocks.ROOTED_DIRT.defaultBlockState() : below;
	}

	private void hit(ServerLevel level) {
		LivingEntity source = this.getOwner();
		for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.2, 0.0, 0.2))) {
			if (!target.isAlive() || target.isInvulnerable() || target == source || target.is(GlowCreatures.FUNGAL)
					|| (source != null && source.isAlliedTo(target))) {
				continue;
			}
			boolean hurt = target.hurtServer(level, source != null ? this.damageSources().mobAttack(source) : this.damageSources().magic(),
					this.damage);
			if (hurt) {
				target.addEffect(new MobEffectInstance(GlowCreatures.ROOTED, this.rootedTicks), source);
				target.push(0.0, 0.35, 0.0);
				target.syncVelocity = true;
			}
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_ERUPT) {
			this.clientErupted = true;
			this.eruptState.start(this.tickCount);
			for (int i = 0; i < 6; i++) {
				this.level().addParticle(GlowFx.glowSpore(), this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
						this.getY() + 0.3 + this.random.nextDouble(), this.getZ() + (this.random.nextDouble() - 0.5) * 0.8,
						0.0, 0.03, 0.0);
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** Spikes cannot be hurt. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	/** The spike's owner is never hit by it (also checked by the boss). */
	public boolean isOwnedBy(Entity entity) {
		return this.ownerId != null && this.ownerId.equals(entity.getUUID());
	}
}
