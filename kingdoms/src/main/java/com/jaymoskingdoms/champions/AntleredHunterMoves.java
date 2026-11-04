package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.PatronChampion;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The Antlered Hunter's moves: Pinning Spear (a green line, step off it), Into the Green (unseen, behind the farthest
 * foe), Stampede (a charge in a line; a wall dazes him); in a group, his hounds.
 */
final class AntleredHunterMoves implements Moves {
	static final int SPEAR_WINDUP = 15, SPEAR_COOLDOWN = 120, SPEAR_SLOW = 30;
	static final double SPEAR_MIN = 6.0, SPEAR_MAX = 28.0, SPEAR_WIDTH = 1.2;
	static final float SPEAR_SHARE = 0.8F;
	static final int GREEN_COOLDOWN = 300, GREEN_INVIS = 80, GREEN_BLOW_TICKS = 100;
	static final double GREEN_RANGE = 24.0;
	static final float GREEN_BLOW = 1.5F;
	static final int STAMPEDE_WINDUP = 20, STAMPEDE_COOLDOWN = 220, STAMPEDE_TICKS = 14, STAMPEDE_DAZE = 30;
	static final double STAMPEDE_MIN = 5.0, STAMPEDE_MAX = 16.0, STAMPEDE_SPEED = 1.1, STAMPEDE_REACH = 1.5, STAMPEDE_PUSH = 1.4;
	static final float STAMPEDE_SHARE = 1.1F, STAMPEDE_POSTURE = 0.25F;

	private Vec3 spearFrom = Vec3.ZERO, spearTo = Vec3.ZERO, charge = Vec3.ZERO;
	private int running = -1;
	private final Set<LivingEntity> trampled = new HashSet<>();
	private final List<Move> moves = List.of(
		new Move("pinning_spear", SPEAR_WINDUP, SPEAR_COOLDOWN, SPEAR_MIN, SPEAR_MAX, this::spearTell, this::spearLand),
		new Move("into_the_green", 0, GREEN_COOLDOWN, 0.0, 1000.0, (l, c, t, left) -> { }, this::green),
		new Move("stampede", STAMPEDE_WINDUP, STAMPEDE_COOLDOWN, STAMPEDE_MIN, STAMPEDE_MAX, this::stampedeTell, this::stampede));

	@Override
	public List<Move> moves() {
		return this.moves;
	}

	private void spearTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == SPEAR_WINDUP && target != null) {
			this.spearFrom = c.position().add(0.0, 1.3, 0.0);
			Vec3 dir = target.position().add(0.0, 1.0, 0.0).subtract(this.spearFrom).normalize();
			this.spearTo = this.spearFrom.add(dir.scale(SPEAR_MAX));
		}
		DustParticleOptions dust = new DustParticleOptions(c.patron().colour & 0xFFFFFF, 1.0F);
		for (double d = 0.0; d <= SPEAR_MAX; d += 1.0) {
			Vec3 p = this.spearFrom.add(this.spearTo.subtract(this.spearFrom).scale(d / SPEAR_MAX));
			level.sendParticles(dust, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private void spearLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (LivingEntity e : c.foes(SPEAR_MAX + 2.0)) {
			if (MoveKit.nearLine(this.spearFrom, this.spearTo, e, SPEAR_WIDTH)) {
				c.hurtFoe(level, e, MoveKit.share(c, SPEAR_SHARE), false);
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPEAR_SLOW, 3));
			}
		}
		level.sendParticles(ParticleTypes.CRIT, this.spearTo.x, this.spearTo.y, this.spearTo.z, 10, 0.3, 0.3, 0.3, 0.1);
		MoveKit.sound(level, c.position(), SoundEvents.TRIDENT_THROW, 2.0F, 0.8F);
	}

	private void green(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		List<LivingEntity> foes = c.foes(GREEN_RANGE);
		if (foes.isEmpty()) {
			return;
		}
		LivingEntity far = foes.get(foes.size() - 1);
		level.sendParticles(ParticleTypes.CHERRY_LEAVES, c.getX(), c.getY() + 1.2, c.getZ(), 30, 0.5, 0.9, 0.5, 0.05);
		c.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, GREEN_INVIS, 0));
		Vec3 behind = far.position().subtract(far.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(2.5));
		Vec3 spot = MoveKit.standable(level, behind.x, far.getY(), behind.z);
		if (spot == null) {
			spot = MoveKit.blinkSpot(level, far, 2.0, 3.0);
		}
		if (spot != null) {
			c.blinkTo(spot);
			c.setTarget(far);
		}
		c.nextBlow(GREEN_BLOW, GREEN_BLOW_TICKS);
	}

	private void stampedeTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == STAMPEDE_WINDUP && target != null) {
			Vec3 d = target.position().subtract(c.position());
			d = new Vec3(d.x, 0.0, d.z);
			this.charge = d.lengthSqr() < 1.0E-4 ? c.getLookAngle() : d.normalize();
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.getX(), c.getY() + 0.1, c.getZ(), 2, 0.4, 0.0, 0.4, 0.01);
		DustParticleOptions dust = new DustParticleOptions(c.patron().colour & 0xFFFFFF, 1.0F);
		for (double d = 1.0; d <= STAMPEDE_MAX; d += 1.0) {
			level.sendParticles(dust, c.getX() + this.charge.x * d, c.getY() + 0.2, c.getZ() + this.charge.z * d, 1, 0.2, 0.0, 0.2, 0.0);
		}
	}

	private void stampede(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		this.running = STAMPEDE_TICKS;
		this.trampled.clear();
		c.getNavigation().stop();
		MoveKit.sound(level, c.position(), SoundEvents.RAVAGER_ROAR, 2.0F, 0.8F);
	}

	@Override
	public void tick(ServerLevel level, PatronChampion c) {
		if (this.running < 0) {
			return;
		}
		this.running--;
		c.getNavigation().stop();
		c.setDeltaMovement(this.charge.x * STAMPEDE_SPEED, c.getDeltaMovement().y, this.charge.z * STAMPEDE_SPEED);
		c.needsSync = true;
		level.sendParticles(ParticleTypes.CLOUD, c.getX(), c.getY() + 0.2, c.getZ(), 3, 0.4, 0.1, 0.4, 0.02);
		for (LivingEntity e : c.foes(STAMPEDE_REACH + 1.0)) {
			if (e.distanceTo(c) <= STAMPEDE_REACH && this.trampled.add(e)) {
				c.hurtFoe(level, e, MoveKit.share(c, STAMPEDE_SHARE), false);
				MoveKit.push(e, c.position(), STAMPEDE_PUSH, 0.3);
			}
		}
		if (c.horizontalCollision && this.running < STAMPEDE_TICKS - 3) {
			this.running = -1;
			c.wearPostureShare(STAMPEDE_POSTURE);
			c.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAMPEDE_DAZE, 9));
			level.sendParticles(ParticleTypes.CRIT, c.getX(), c.getY() + 1.0, c.getZ(), 20, 0.5, 0.5, 0.5, 0.2);
			MoveKit.sound(level, c.position(), SoundEvents.ANVIL_LAND, 1.0F, 1.4F);
		}
	}

	@Override
	public void clear(PatronChampion c) {
		this.running = -1;
	}

	@Override
	public void hand(ServerLevel level, PatronChampion c, int group) {
		for (int i = 0; i < MoveKit.adds(group); i++) {
			Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.EVENT);
			if (wolf == null) {
				continue;
			}
			MoveKit.name(wolf, "hound");
			c.spawnAdd(level, wolf);
		}
	}
}
