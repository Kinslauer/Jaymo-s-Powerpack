package com.jaymospowers.aether.dungeon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a sentry golem throws: a lobbed demolition charge that bursts where it lands (bigger against a player) and breaks
 * no blocks. It never hurts another monster directly.
 */
public class DemolitionBomb extends ThrowableItemProjectile {
	// ---- tuning ----
	/** Burst radius against the ground and a player, ticks it may fly, and how it falls (blocks per tick squared). */
	public static final float BLAST = 1.5F, BLAST_ON_PLAYER = 2.0F;
	public static final int LIFETIME = 160;
	public static final double GRAVITY = 0.05;

	public DemolitionBomb(EntityType<? extends DemolitionBomb> type, Level level) {
		super(type, level);
	}

	/** Lobs a bomb from {@code owner} at {@code at}. */
	public static void lob(LivingEntity owner, Vec3 at, float speed) {
		DemolitionBomb bomb = new DemolitionBomb(DungeonMobs.DEMOLITION_BOMB, owner.level());
		bomb.setOwner(owner);
		Vec3 from = owner.getEyePosition().add(0.0, -0.2, 0.0);
		bomb.setPos(from.x, from.y, from.z);
		Vec3 aim = at.subtract(from);
		// aim a little high for the fall
		double flat = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
		bomb.shoot(aim.x, aim.y + flat * 0.12, aim.z, speed, 1.0F);
		owner.level().addFreshEntity(bomb);
	}

	@Override
	protected Item getDefaultItem() {
		return DungeonMobs.DEMOLITION_BOMB_ITEM;
	}

	@Override
	protected double getDefaultGravity() {
		return GRAVITY;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && !(entity instanceof Enemy && this.getOwner() instanceof Enemy);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
		} else if (this.tickCount > LIFETIME) {
			this.discard();
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			boolean onPlayer = hit instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() instanceof Player;
			level.explode(this.getOwner(), this.getX(), this.getY(), this.getZ(), onPlayer ? BLAST_ON_PLAYER : BLAST, Level.ExplosionInteraction.NONE);
			this.discard();
		}
	}
}
