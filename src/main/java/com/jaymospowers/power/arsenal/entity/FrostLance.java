package com.jaymospowers.power.arsenal.entity;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Hrunting's Frost Lance: a spear of ice thrown down the sight line. It runs through the first thing it meets (and on
 * through up to {@link #PIERCE} more), freezing and slowing each, stops at a wall or at the end of its reach, then flies
 * back to the thrower and shatters in their hand. Server-driven; never saved.
 */
public class FrostLance extends Projectile {
	/** Speed out and back (blocks a tick), how far it goes, how long it may live at most. */
	public static final double SPEED = 1.7, RETURN_SPEED = 1.4, RANGE = 24.0;
	public static final int MAX_LIFE = 100;
	/** On a hit: damage, frost added, slowness, and how many it can run through after the first. */
	public static final float DAMAGE = 7.0F;
	public static final int FROST_TICKS = 160, FROST_OVER = 80;
	public static final int SLOW_TICKS = 60, SLOW_LEVEL = 2;
	public static final int PIERCE = 1;
	/** Close enough to the thrower to be caught. */
	public static final double CATCH_DISTANCE = 1.2;

	private Vec3 origin = Vec3.ZERO;
	private Vec3 dir = Vec3.ZERO;
	private boolean returning;
	private int life;
	private final Set<Integer> hit = new HashSet<>();

	public FrostLance(EntityType<? extends FrostLance> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	public static FrostLance create(ServerLevel level, ServerPlayer owner) {
		FrostLance lance = new FrostLance(ArsenalEntities.FROST_LANCE, level);
		lance.setOwner(owner);
		Vec3 eye = owner.getEyePosition().add(0.0, -0.15, 0.0);
		lance.origin = eye;
		lance.dir = owner.getLookAngle().normalize();
		lance.setPos(eye.add(lance.dir.scale(0.6)));
		lance.face(lance.dir);
		return lance;
	}

	private void face(Vec3 dir) {
		float yRot = (float)(Mth.atan2(dir.x, dir.z) * Mth.RAD_TO_DEG);
		float xRot = (float)(Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG);
		this.setYRot(yRot);
		this.setXRot(xRot);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Entity owner = this.getOwner();
		if (!(owner instanceof ServerPlayer player) || !player.isAlive() || player.level() != level || ++this.life > MAX_LIFE) {
			this.shatter(level);
			return;
		}
		Vec3 pos = this.position();
		Vec3 move;
		if (!this.returning) {
			move = this.dir.scale(SPEED);
			Vec3 next = pos.add(move);
			BlockHitResult block = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (block.getType() != HitResult.Type.MISS) {
				next = block.getLocation();
				move = next.subtract(pos);
				this.turnBack(level, next);
			}
			this.strike(level, player, pos, next);
			if (next.distanceTo(this.origin) >= RANGE) {
				this.turnBack(level, next);
			}
		} else {
			Vec3 to = player.getEyePosition().add(0.0, -0.4, 0.0).subtract(pos);
			double dist = to.length();
			if (dist <= CATCH_DISTANCE) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.6F);
				level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 12, 0.2, 0.2, 0.2, 0.05);
				this.discard();
				return;
			}
			move = to.scale(Math.min(RETURN_SPEED, dist) / dist);
		}
		this.setDeltaMovement(move);
		this.face(move);
		this.setPos(pos.add(move));
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
		if (this.life % 2 == 0) {
			level.sendParticles(ParticleTypes.ITEM_SNOWBALL, this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	private void turnBack(ServerLevel level, Vec3 at) {
		if (this.returning) {
			return;
		}
		this.returning = true;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.05);
	}

	/** Everything on the way from {@code from} to {@code to} it hasn't already struck. */
	private void strike(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(0.6),
			e -> e != player && e.isAlive() && !e.isSpectator() && !this.hit.contains(e.getId()))) {
			if (target.getBoundingBox().inflate(0.3).clip(from, to).isEmpty() && !target.getBoundingBox().inflate(0.3).contains(from)) {
				continue;
			}
			this.hit.add(target.getId());
			DamageSource source = this.damageSources().trident(this, player);
			target.hurtServer(level, source, DAMAGE);
			if (target.canFreeze()) {
				target.setTicksFrozen(Math.min(target.getTicksFrozen() + FROST_TICKS, target.getTicksRequiredToFreeze() + FROST_OVER));
			}
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_LEVEL), player);
			level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 16, 0.3, 0.4, 0.3, 0.05);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 1.0F);
			if (this.hit.size() > PIERCE) {
				this.turnBack(level, target.position());
				return;
			}
		}
	}

	private void shatter(ServerLevel level) {
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6F, 1.6F);
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
		return distance < 64.0 * 64.0;
	}
}
