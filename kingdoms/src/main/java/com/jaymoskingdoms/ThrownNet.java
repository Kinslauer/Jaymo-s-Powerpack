package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A thrown net ({@link Nets}): a weighted net that spreads as it flies and falls over the first living thing it meets
 * (never its thrower), holding it ({@link Nets#catchIn}). Whether it catches or misses it is spent: it is the item that was
 * thrown. One thrown by a guard or slaver flies at its mark and catches only that one.
 */
public final class ThrownNet extends Projectile implements ItemSupplier {
	/** How fast it falls (blocks per tick, per tick) and the most ticks it flies. */
	static final double GRAVITY = 0.035;
	static final int FLIGHT_TICKS = 60;
	/** A hand throw's speed (blocks per tick) and loft. */
	static final float THROW_SPEED = 1.2F, THROW_LOFT = 0.1F;
	/** How wide it is, for catching (blocks). */
	static final double SPREAD = 0.6;

	public static EntityType<ThrownNet> TYPE;

	private int flight;
	/** The one a guard's or slaver's net is meant for. */
	private @Nullable UUID mark;

	public ThrownNet(EntityType<? extends ThrownNet> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("thrown_net"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<ThrownNet>of(ThrownNet::new, MobCategory.MISC)
			.noLootTable().sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1).build(key));
	}

	/** A net thrown by {@code thrower} along {@code velocity}. */
	static ThrownNet throwFrom(ServerLevel level, LivingEntity thrower, Vec3 velocity) {
		ThrownNet net = new ThrownNet(TYPE, level);
		net.setOwner(thrower);
		net.setPos(thrower.getX(), thrower.getEyeY() - 0.2, thrower.getZ());
		net.setDeltaMovement(velocity);
		level.addFreshEntity(net);
		level.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8F, 0.5F);
		return net;
	}

	/** A net thrown by {@code thrower} at {@code target}, aimed to arc onto it. */
	static void throwAt(ServerLevel level, LivingEntity thrower, LivingEntity target, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		double speed = THROW_SPEED;
		double ticks = Math.max(1.0, flat / speed);
		// aim high enough that gravity brings it down on the mark
		double rise = d.y / ticks + GRAVITY * ticks * 0.5;
		Vec3 v = new Vec3(d.x / ticks, rise, d.z / ticks);
		ThrownNet net = throwFrom(level, thrower, v);
		net.mark = target.getUUID();
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(Nets.NET);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 pos = this.position();
		if (++this.flight > FLIGHT_TICKS || !level.getFluidState(BlockPos.containing(pos)).isEmpty()) {
			this.discard();
			return;
		}
		Vec3 move = this.getDeltaMovement().add(0.0, -GRAVITY, 0.0);
		Vec3 next = pos.add(move);
		Entity owner = this.getOwner();
		EntityHitResult struck = ProjectileUtil.getEntityHitResult(level, this, pos, next, new AABB(pos, next).inflate(SPREAD),
			e -> e != owner && e instanceof LivingEntity l && l.isAlive() && !l.isSpectator() && (this.mark == null || e.getUUID().equals(this.mark)), 0.3F);
		HitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (struck != null && struck.getEntity() instanceof LivingEntity foe && (hit.getType() != HitResult.Type.BLOCK
			|| pos.distanceToSqr(struck.getLocation()) <= pos.distanceToSqr(hit.getLocation()))) {
			Nets.catchIn(foe, owner instanceof LivingEntity thrower ? thrower : null);
			this.discard();
			return;
		}
		if (hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK) {
			level.playSound(null, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 0.5F, 1.1F);
			this.discard();
			return;
		}
		this.setPos(next);
		this.setDeltaMovement(move);
		level.sendParticles(ParticleTypes.CRIT, next.x, next.y, next.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0 * 96.0;
	}
}
