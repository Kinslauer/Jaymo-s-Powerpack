package com.jaymoskingdoms.nether;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Hornkin hurler's magma bomb ({@link Hornkin.Kind#HURLER}): a glob of magma lobbed in an arc. It bursts where it
 * lands, hurting and setting alight everyone close but the Hornkin themselves, and leaves the blocks whole.
 */
public class MagmaBomb extends ThrowableItemProjectile {
	/** Its burst: the blow at its heart (falling off to a quarter at the edge), its reach, how long it sets them burning (seconds). */
	static final float DAMAGE = 5.0F, BURN_SECONDS = 4.0F;
	static final double RADIUS = 2.5;

	public MagmaBomb(EntityType<? extends MagmaBomb> type, Level level) {
		super(type, level);
	}

	MagmaBomb(Level level, LivingEntity thrower) {
		super(Nether.MAGMA_BOMB, thrower, level, new ItemStack(Items.MAGMA_CREAM));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.MAGMA_CREAM;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && this.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			this.burst(level);
			this.discard();
		}
	}

	private void burst(ServerLevel level) {
		Vec3 at = this.position();
		Entity owner = this.getOwner();
		DamageSource source = level.damageSources().thrown(this, owner);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(RADIUS),
			e -> e.isAlive() && !(e instanceof Hornkin) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			double d = e.position().distanceTo(at);
			if (d > RADIUS + 0.5) {
				continue;
			}
			float share = (float) Math.max(0.25, 1.0 - d / (RADIUS + 0.5));
			if (e.hurtServer(level, source, DAMAGE * share)) {
				e.igniteForSeconds(BURN_SECONDS);
			}
		}
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 8, 0.4, 0.2, 0.4, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 20, 0.6, 0.3, 0.6, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MAGMA_CUBE_SQUISH, SoundSource.HOSTILE, 1.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.8F, 1.2F);
	}
}
