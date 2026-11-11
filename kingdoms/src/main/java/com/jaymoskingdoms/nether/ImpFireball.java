package com.jaymoskingdoms.nether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An {@link Imp}'s great fireball. Where it lands it bursts: it burns whoever is near (never imps), breaks nothing, and
 * sets the trees and everything around it ablaze ({@link Imps#blaze}). Like a ghast's, a blow can knock it back.
 */
public class ImpFireball extends Fireball {
	/** The burst: how far it reaches (blocks), what it does at its middle, how long those it catches burn (seconds). */
	static final double RADIUS = 3.0;
	static final float DAMAGE = 7.0F, BURN_SECONDS = 6.0F;
	/** How far round the landing fires are lit (blocks), and how many. */
	static final int BLAZE_RADIUS = 4, FIRES = 16;

	public ImpFireball(EntityType<? extends ImpFireball> type, Level level) {
		super(type, level);
	}

	ImpFireball(Level level, LivingEntity thrower, Vec3 direction) {
		super(Imps.IMP_FIREBALL, thrower, direction, level);
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
		DamageSource source = level.damageSources().fireball(this, owner);
		// thrown back at them, it burns imps too
		boolean returned = !(owner instanceof Imp);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(RADIUS),
			e -> e.isAlive() && (returned || !(e instanceof Imp)) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			double d = e.position().distanceTo(at);
			if (d <= RADIUS + 0.5 && e.hurtServer(level, source, DAMAGE * (float) Math.max(0.3, 1.0 - d / (RADIUS + 0.5)))) {
				e.igniteForSeconds(BURN_SECONDS);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 60, 0.9, 0.6, 0.9, 0.1);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 14, 0.6, 0.3, 0.6, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2F, 1.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 0.7F);
		Imps.blaze(level, BlockPos.containing(at), BLAZE_RADIUS, FIRES);
	}
}
