package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Worker;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.18) A ship's gun's shot: an iron ball that arcs out over the water and holes whatever ship it meets (the planks
 * round where it strikes are smashed out, sails it passes through are torn: {@link Ship#struck}), and hurts everyone near
 * where it lands. It splashes into the sea. (0.7.490) Of five kinds ({@link ShotKind}): round, heavy, explosive shell,
 * chain (rigging only) and grape (a short spray for crews). Where it lands ashore it blasts a crater that strips the
 * ground to the stone, worked off over the next few seconds ({@link Bombardment}). Never saved.
 */
public final class ShipShot extends Projectile implements ItemSupplier {
	/** How fast it leaves the barrel and how fast it falls (blocks per tick, per tick), before its {@link ShotKind} scales them. */
	static final double SPEED = 1.6, GRAVITY = 0.015;
	/** Its blow falls off to this share of itself at its burst's edge. */
	static final float BURST_SHARE = 0.3F;
	/** How finely its flight is tested against ships (blocks). */
	static final double STEP = 0.25;
	/** A shell's second blast: how far out round where it strikes a ship it tears her planking (blocks). */
	static final double SPLASH = 1.2;
	/** How far under where it bursts (blocks) the ground it blasts may lie. */
	static final int LAND_REACH = 3;

	public static EntityType<ShipShot> TYPE;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ShipShot.class, EntityDataSerializers.INT);

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

	static void fire(ServerLevel level, @Nullable Entity gunner, @Nullable Ship ship, Vec3 muzzle, Vec3 dir, ShotKind kind) {
		ShipShot shot = new ShipShot(TYPE, level);
		shot.setOwner(gunner);
		shot.fromShip = ship != null ? ship.getId() : -1;
		shot.entityData.set(KIND, kind.ordinal());
		shot.setPos(muzzle);
		shot.setDeltaMovement(dir.scale(SPEED * kind.speed));
		level.addFreshEntity(shot);
	}

	/** What it is (round shot until it's told). */
	public ShotKind kind() {
		ShotKind[] kinds = ShotKind.values();
		int i = this.entityData.get(KIND);
		return kinds[Math.max(0, Math.min(kinds.length - 1, i))];
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(this.kind().item());
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		ShotKind kind = this.kind();
		Entity gunner = this.getOwner();
		if (++this.life > kind.life) {
			if (kind == ShotKind.GRAPE) {
				// grape has flown as far as it's going: it spreads
				this.burst(level, gunner, this.position(), null, true);
				return;
			}
			this.discard();
			return;
		}
		Vec3 pos = this.position(), move = this.getDeltaMovement(), next = pos.add(move);
		// the ships in its way, a little at a time along its flight
		int steps = Math.max(1, (int)Math.ceil(move.length() / STEP));
		for (int k = 1; k <= steps; k++) {
			Vec3 p = pos.add(move.scale((double)k / steps));
			for (Ship ship : Ships.in(level)) {
				if (ship.getId() == this.fromShip || !ship.getBoundingBox().inflate(0.5).contains(p)) {
					continue;
				}
				if (this.hitShip(level, ship, p, move, gunner, kind)) {
					this.burst(level, gunner, p, null, false);
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
				&& !(e.getVehicle() != null && e.getVehicle().getId() == this.fromShip)
				// (0.7.490) nor her own landing party ashore
				&& !own(gunner, e), 0.3F);
		if (entityHit != null) {
			this.burst(level, gunner, entityHit.getLocation(), entityHit.getEntity(), true);
			return;
		}
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			// it smashes into what it hits: no mercy
			// (0.7.490) the crater is centred on the block it struck: a hit on an east or south face lies on the
			// block's edge, and containing() would find the air in front of it
			this.burst(level, gunner, hit.getLocation(), null, true, blockHit.getBlockPos());
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
		this.setDeltaMovement(move.add(0.0, -GRAVITY * kind.gravity, 0.0));
		level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0.03, 0.03, 0.03, 0.0);
		if (kind == ShotKind.SHELL) {
			// a shell's fuse sputters
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.1, this.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** It's reached {@code ship} at {@code p}: whether it stops there (and bursts). Chain tears rigging, the rest holes her. */
	private boolean hitShip(ServerLevel level, Ship ship, Vec3 p, Vec3 move, @Nullable Entity gunner, ShotKind kind) {
		if (kind == ShotKind.CHAIN) {
			// chain flies through her sails, tearing them, and stops at her hull
			return ship.rigged(p, gunner) == 2;
		}
		if (kind == ShotKind.GRAPE) {
			return ship.struck(p, gunner);
		}
		if (!ship.struck(p, gunner)) {
			return false;
		}
		Vec3 along = move.normalize();
		// heavy shot drives on through her planking
		for (int j = 1; j <= kind.pierce; j++) {
			ship.struck(p.add(along.scale(0.9 * j)), gunner);
		}
		if (kind.splash) {
			// a shell bursts in her: the planking all round goes too
			for (Vec3 d : new Vec3[] {new Vec3(SPLASH, 0, 0), new Vec3(-SPLASH, 0, 0), new Vec3(0, 0, SPLASH), new Vec3(0, 0, -SPLASH),
				new Vec3(0, SPLASH, 0), new Vec3(0, -SPLASH, 0)}) {
				ship.struck(p.add(d), gunner);
			}
		}
		return true;
	}

	/** The solid ground at or just under {@code at} (within {@link #LAND_REACH} blocks), or null: over open water or air, nothing to crater. */
	private @Nullable BlockPos land(ServerLevel level, Vec3 at) {
		BlockPos from = BlockPos.containing(at);
		for (int d = 0; d <= LAND_REACH; d++) {
			BlockPos pos = from.below(d);
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (!state.getFluidState().isEmpty()) {
				return null;
			}
			if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
				return pos;
			}
		}
		return null;
	}

	/** (0.7.490) Whether {@code e} is one of the gunner's own folk (a pirate gun's landing party ashore): her shot spares them. */
	private static boolean own(@Nullable Entity gunner, Entity e) {
		return gunner instanceof Worker g && g.factionId() >= 0 && e instanceof Worker w && w.factionId() == g.factionId();
	}

	/**
	 * (0.7.490) Where it bursts: everyone near is hurt, and if it's ashore, the ground is blasted (queued, {@link Bombardment}).
	 */
	private void burst(ServerLevel level, @Nullable Entity gunner, Vec3 at, @Nullable Entity direct, boolean ground) {
		this.burst(level, gunner, at, direct, ground, null);
	}

	/** (0.7.490) As above; {@code struck}, when it hit a block, is that block, and the crater is centred on it. */
	private void burst(ServerLevel level, @Nullable Entity gunner, Vec3 at, @Nullable Entity direct, boolean ground, @Nullable BlockPos struck) {
		ShotKind kind = this.kind();
		double radius = kind.burst;
		DamageSource source = this.damageSources().explosion(this, gunner);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
			e -> e != gunner && e.isAlive() && !e.isSpectator() && !own(gunner, e))) {
			double d = victim == direct ? 0.0 : victim.getBoundingBox().getCenter().distanceTo(at);
			if (d > radius) {
				continue;
			}
			float share = (float)(1.0 - d / radius);
			victim.hurtServer(level, source, kind.damage * (BURST_SHARE + (1.0F - BURST_SHARE) * share));
			double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 away = victim.position().subtract(at);
			if (resist > 0.0 && away.lengthSqr() > 1.0E-4) {
				Vec3 push = away.normalize().scale(kind.knockback * share * resist);
				victim.push(push.x, 0.3 * share * resist, push.z);
				victim.syncVelocity = true;
			}
		}
		BlockPos land = !ground ? null : struck != null ? struck : this.land(level, at);
		if (land != null) {
			// (0.7.490) it struck land: the crater, centred on the block it hit
			Bombardment.crater(level, Vec3.atCenterOf(land), kind.crater, gunner != null ? gunner : this);
		}
		switch (kind) {
			case SHELL -> {
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 24, 1.0, 0.8, 1.0, 0.06);
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 16, 0.8, 0.5, 0.8, 0.08);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3.0F, 0.8F);
			}
			case GRAPE -> {
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 1.6, 0.8, 1.6, 0.4);
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 14, 1.2, 0.6, 1.2, 0.05);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.2F, 1.5F);
			}
			case CHAIN -> {
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0.2);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.5F, 0.7F);
			}
			default -> {
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.03);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.6F, 0.7F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, kind == ShotKind.HEAVY ? 0.8F : 1.2F);
			}
		}
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
