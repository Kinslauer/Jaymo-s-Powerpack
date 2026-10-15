package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.18) A ship's gun's round shot: an iron ball that arcs out over the water and holes whatever ship it meets (the
 * planks round where it strikes are smashed out, sails it passes through are torn: {@link Ship#struck}), and hurts
 * everyone near where it lands. It splashes into the sea, and smashes a hole in whatever it hits on land ({@link #breach}).
 * Never saved.
 */
public final class ShipShot extends Projectile implements ItemSupplier {
	/** How fast it leaves the barrel and how fast it falls (blocks per tick, per tick), and how long it flies at most. */
	static final double SPEED = 1.6, GRAVITY = 0.015;
	static final int MAX_LIFE = 160;
	/** Its blow on whoever it hits, falling off to {@link #BURST_SHARE} at {@link #BURST_RADIUS}. */
	static final float DAMAGE = 16.0F, BURST_SHARE = 0.3F;
	static final double BURST_RADIUS = 2.0, KNOCKBACK = 0.8;
	/** (0.7.18) How far round where it strikes land it smashes blocks out, what holds against it, and the share left as items. */
	static final double BREACH_RADIUS = 1.6;
	static final float TOUGHEST = 1200.0F, RUBBLE = 0.3F;
	/** How finely its flight is tested against ships (blocks). */
	static final double STEP = 0.25;

	public static EntityType<ShipShot> TYPE;

	private int life;
	/** The ship that fired it (-1: none), which it never hits. */
	private int fromShip = -1;

	public ShipShot(EntityType<? extends ShipShot> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("ship_shot"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<ShipShot>of(ShipShot::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.35F, 0.35F)
			.clientTrackingRange(10)
			.updateInterval(1)
			.build(key));
	}

	static void fire(ServerLevel level, @Nullable Entity gunner, @Nullable Ship ship, Vec3 muzzle, Vec3 dir) {
		ShipShot shot = new ShipShot(TYPE, level);
		shot.setOwner(gunner);
		shot.fromShip = ship != null ? ship.getId() : -1;
		shot.setPos(muzzle);
		shot.setDeltaMovement(dir.scale(SPEED));
		level.addFreshEntity(shot);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(Sailing.ROUND_SHOT);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (++this.life > MAX_LIFE) {
			this.discard();
			return;
		}
		Vec3 pos = this.position(), move = this.getDeltaMovement(), next = pos.add(move);
		Entity gunner = this.getOwner();
		// the ships in its way, a little at a time along its flight
		int steps = Math.max(1, (int)Math.ceil(move.length() / STEP));
		for (int k = 1; k <= steps; k++) {
			Vec3 p = pos.add(move.scale((double)k / steps));
			for (Ship ship : Ships.in(level)) {
				if (ship.getId() == this.fromShip || !ship.getBoundingBox().inflate(0.5).contains(p)) {
					continue;
				}
				if (ship.struck(p, gunner)) {
					this.burst(level, gunner, p, null);
					return;
				}
			}
		}
		HitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (hit.getType() != HitResult.Type.MISS) {
			next = hit.getLocation();
		}
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, this, pos, next, new AABB(pos, next).inflate(0.6),
			e -> e != gunner && e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !(gunner != null && e == gunner.getVehicle())
				// (0.7.42) nor her own crew, firing out past them
				&& !(e.getVehicle() != null && e.getVehicle().getId() == this.fromShip), 0.3F);
		if (entityHit != null) {
			this.burst(level, gunner, entityHit.getLocation(), entityHit.getEntity());
			return;
		}
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			// it smashes into what it hits: no mercy
			this.breach(level, gunner, blockHit.getBlockPos(), hit.getLocation());
			this.burst(level, gunner, hit.getLocation(), null);
			return;
		}
		if (level.getFluidState(BlockPos.containing(next)).is(FluidTags.WATER)) {
			level.sendParticles(ParticleTypes.SPLASH, next.x, next.y + 0.5, next.z, 40, 0.4, 0.3, 0.4, 0.3);
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, next.x, next.y, next.z, 12, 0.3, 0.2, 0.3, 0.1);
			level.playSound(null, next.x, next.y, next.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.BLOCKS, 1.6F, 0.6F);
			this.discard();
			return;
		}
		this.setPos(next);
		this.setDeltaMovement(move.add(0.0, -GRAVITY, 0.0));
		level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0.03, 0.03, 0.03, 0.0);
	}

	/**
	 * (0.7.18) Smashes the blocks round where it struck land: everything within {@link #BREACH_RADIUS} that can be broken
	 * at all and is softer than {@link #TOUGHEST} (obsidian and its like hold), some of it left as rubble to pick up.
	 */
	private void breach(ServerLevel level, @Nullable Entity gunner, BlockPos struck, Vec3 at) {
		double r2 = BREACH_RADIUS * BREACH_RADIUS;
		int r = (int)Math.ceil(BREACH_RADIUS);
		for (BlockPos pos : BlockPos.betweenClosed(struck.offset(-r, -r, -r), struck.offset(r, r, r))) {
			if (!pos.equals(struck) && Vec3.atCenterOf(pos).distanceToSqr(at) > r2) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, pos) < 0.0F || state.getBlock().getExplosionResistance() >= TOUGHEST) {
				continue;
			}
			level.destroyBlock(pos, this.random.nextFloat() < RUBBLE, gunner != null ? gunner : this);
		}
	}

	private void burst(ServerLevel level, @Nullable Entity gunner, Vec3 at, @Nullable Entity direct) {
		DamageSource source = this.damageSources().explosion(this, gunner);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS),
			e -> e != gunner && e.isAlive() && !e.isSpectator())) {
			double d = victim == direct ? 0.0 : victim.getBoundingBox().getCenter().distanceTo(at);
			if (d > BURST_RADIUS) {
				continue;
			}
			float share = (float)(1.0 - d / BURST_RADIUS);
			victim.hurtServer(level, source, DAMAGE * (BURST_SHARE + (1.0F - BURST_SHARE) * share));
			double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 away = victim.position().subtract(at);
			if (resist > 0.0 && away.lengthSqr() > 1.0E-4) {
				Vec3 push = away.normalize().scale(KNOCKBACK * share * resist);
				victim.push(push.x, 0.3 * share * resist, push.z);
				victim.syncVelocity = true;
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.6F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 1.2F);
		this.discard();
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
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160.0 * 160.0;
	}
}
