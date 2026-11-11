package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.core.gifted.Gifted;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.3) Z Fighter for a gifted villager ({@link Gifted}): a ki blast, a flurry of blows, a palm that throws the foe back,
 * and a flash step away. No Great Ape, no lineage and no fusion; just the fighter. A ruler's ultimate is the Wave Beam
 * ({@link ZFighterWaveBeam}'s look, in a straight line that breaks no block); out of a fight they shadowbox.
 */
final class ZFighterGift implements GiftKit {
	/** Ki colours: the blast's gold, the wave's blue (as {@code ZFighterKi}'s). */
	static final int BLAST_COLOR = 0xFFC85A, WAVE_COLOR = 0x8FD3FF;
	/** The ki blast: its reach, damage and cooldown (ticks). */
	static final double BLAST_RANGE = 18.0;
	static final float BLAST_DAMAGE = 4.0F;
	static final int BLAST_COOLDOWN = 50;
	/** The flurry: its reach, how many blows, damage a blow and cooldown. */
	static final double FLURRY_RANGE = 3.5;
	static final int FLURRY_BLOWS = 5, FLURRY_COOLDOWN = 120;
	static final float FLURRY_DAMAGE = 1.5F;
	/** The palm: its reach, damage, throw (horizontal, up) and cooldown. */
	static final double PALM_RANGE = 3.0, PALM_THROW = 1.4, PALM_LIFT = 0.3;
	static final float PALM_DAMAGE = 3.0F;
	static final int PALM_COOLDOWN = 100;
	/** The flash step: how hard and high they go, and its cooldown. */
	static final double STEP_SPEED = 1.6, STEP_LIFT = 0.2;
	static final int STEP_COOLDOWN = 110;
	/** A ruler's Wave Beam: its length, half width, throw, damage, slow (ticks) and film (ticks). */
	static final double WAVE_LENGTH = 28.0, WAVE_WIDTH = 1.6, WAVE_THROW = 1.2;
	static final float WAVE_DAMAGE = 9.0F;
	static final int WAVE_SLOW_TICKS = 60, WAVE_FILM = 50;
	/** (0.19.60) How long it gathers in their hands before it fires (ticks). */
	static final int WAVE_GATHER = 16;
	/** Everyday: their shadowboxing, how many punches at a time and how far from them. */
	static final int SHADOWBOX_PUNCHES = 3;
	static final double SHADOWBOX_REACH = 1.2;

	static void init() {
		Gifted.register(new ZFighterGift());
	}

	@Override
	public Identifier power() {
		return ZFighterPower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.of("palm", Kind.CLOSE, PALM_COOLDOWN, 0.0, PALM_RANGE, ZFighterGift::palm),
			Move.of("flurry", Kind.CLOSE, FLURRY_COOLDOWN, 0.0, FLURRY_RANGE, ZFighterGift::flurry),
			Move.of("blast", Kind.STRIKE, BLAST_COOLDOWN, 2.0, BLAST_RANGE, ZFighterGift::blast),
			Move.of("flash step", Kind.ESCAPE, STEP_COOLDOWN, 0.0, 5.0, ZFighterGift::step));
	}

	private static void push(LivingEntity e, Vec3 horizontal, double lift) {
		Vec3 v = e.getDeltaMovement().add(horizontal);
		e.setDeltaMovement(v.x, Math.max(v.y, 0.0) + lift, v.z);
		e.syncVelocity = true;
	}

	/** A line of ki motes from {@code from} to {@code to}. */
	private static void line(ServerLevel level, Vec3 from, Vec3 to, int color, float size, double perBlock) {
		MagicFx.line(level, from, to, MagicFx.wisp(color, size), Math.max(1, (int)(to.distanceTo(from) * perBlock)));
	}

	private static boolean blast(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		// (0.19.60) out of the palm, thrown with a thrust of that arm
		Vec3 to = foe.getBoundingBox().getCenter(), aim = to.subtract(caster.getEyePosition());
		Vec3 from = ZFighterHands.mainPalm(caster, aim.lengthSqr() < 1.0E-6 ? caster.getLookAngle() : aim.normalize());
		ZFighterHands.thrust(caster, ZFighterHands.hand(caster, true), ZFighterHands.JAB);
		line(level, from, to, BLAST_COLOR, 1.2F, 2.0);
		level.sendParticles(MagicFx.wisp(BLAST_COLOR, 1.8F), to.x, to.y, to.z, 16, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(MagicFx.CRACKLE, to.x, to.y, to.z, 6, 0.2, 0.2, 0.2, 0.2);
		level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.7F, 1.8F);
		level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.NEUTRAL, 0.7F, 1.5F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.35F, 1.9F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), BLAST_DAMAGE);
		return true;
	}

	private static boolean flurry(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = foe.getBoundingBox().getCenter();
		for (int i = 0; i < FLURRY_BLOWS; i++) {
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 3, 0.35, 0.35, 0.35, 0.2);
			level.sendParticles(MagicFx.CRACKLE, c.x, c.y, c.z, 2, 0.35, 0.35, 0.35, 0.2);
		}
		level.sendParticles(MagicFx.wisp(BLAST_COLOR, 1.2F), caster.getX(), caster.getY(0.6), caster.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.8F, 1.8F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.8F, 1.2F);
		// separate blows would be soaked up by the invulnerable frames, so they land together
		foe.hurtServer(level, level.damageSources().mobAttack(caster), FLURRY_DAMAGE * FLURRY_BLOWS);
		return true;
	}

	private static boolean palm(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 away = foe.position().subtract(caster.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal() : away;
		Vec3 c = foe.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(MagicFx.wisp(WAVE_COLOR, 1.5F), c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.1);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 1.0F, 0.8F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.4F, 1.8F);
		foe.hurtServer(level, level.damageSources().mobAttack(caster), PALM_DAMAGE);
		push(foe, away.normalize().scale(PALM_THROW), PALM_LIFT);
		return true;
	}

	private static boolean step(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!caster.onGround()) {
			return false;
		}
		Vec3 away = caster.position().subtract(foe.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? caster.getLookAngle().horizontal().reverse() : away;
		// the afterimage: a column of fading motes where they stood
		ParticleOptions image = MagicFx.wisp(BLAST_COLOR, 1.0F);
		for (double h = 0.1; h < caster.getBbHeight(); h += 0.3) {
			level.sendParticles(image, caster.getX(), caster.getY() + h, caster.getZ(), 3, 0.15, 0.05, 0.15, 0.0);
		}
		level.sendParticles(ParticleTypes.POOF, caster.getX(), caster.getY(0.5), caster.getZ(), 6, 0.2, 0.4, 0.2, 0.02);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.NEUTRAL, 0.7F, 1.8F);
		push(caster, away.normalize().scale(STEP_SPEED), STEP_LIFT);
		caster.fallDistance = 0.0;
		return true;
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// shadowboxing: punches in the air in front of them, sparks off their fists
		Vec3 look = mob.getLookAngle().horizontal();
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : look.normalize();
		ParticleOptions ki = MagicFx.wisp(BLAST_COLOR, 0.8F);
		for (int i = 0; i < SHADOWBOX_PUNCHES; i++) {
			Vec3 fist = mob.position().add(look.scale(SHADOWBOX_REACH)).add(0.0, mob.getBbHeight() * (0.55 + 0.1 * (i % 2)), 0.0);
			level.sendParticles(MagicFx.CRACKLE, fist.x, fist.y, fist.z, 2, 0.1, 0.1, 0.1, 0.1);
			level.sendParticles(ki, fist.x, fist.y, fist.z, 2, 0.1, 0.1, 0.1, 0.0);
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.5F, 1.6F);
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.PROFILE, WAVE_FILM, false, ZFighterGift::wave);
	}

	/** (0.19.60) The wave gathers between their cupped hands at the hip first ({@link #WAVE_GATHER} ticks), then fires. */
	private static boolean wave(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (foe.getBoundingBox().getCenter().distanceToSqr(caster.getEyePosition()) < 1.0E-4) {
			return false;
		}
		ZFighterHands.pose(caster, ZFighterHands.CUP);
		for (int i = 0; i < WAVE_GATHER; i++) {
			double size = 0.25 + 0.75 * i / (double)WAVE_GATHER;
			ZFighterHands.later(caster, i, () -> {
				caster.getLookControl().setLookAt(foe, 30.0F, 30.0F);
				ZFighterKi.orb(level, -(caster.getId() * 4 + 3), ZFighterHands.cupped(caster), size, WAVE_COLOR);
			});
		}
		ZFighterHands.later(caster, WAVE_GATHER, () -> {
			ZFighterHands.pose(caster, ZFighterHands.NONE);
			if (foe.isAlive() && foe.level() == level) {
				fireWave(level, caster, foe);
			}
		});
		return true;
	}

	private static boolean fireWave(ServerLevel level, PathfinderMob caster, LivingEntity foe) {
		Vec3 aim = foe.getBoundingBox().getCenter().subtract(caster.getEyePosition());
		if (aim.lengthSqr() < 1.0E-4) {
			return false;
		}
		// (0.19.60) from both hands pushed out together, with a shout
		Vec3 dir = aim.normalize();
		Vec3 from = ZFighterHands.palm(caster, dir, false).lerp(ZFighterHands.palm(caster, dir, true), 0.5);
		Vec3 end = from.add(dir.scale(WAVE_LENGTH));
		ZFighterHands.thrust(caster, ZFighterHands.BOTH, ZFighterHands.PUSH);
		ZFighterHands.shout(caster);
		// the beam: a thick blue coil from the hands to its end, bursting where it stops (no block is touched)
		line(level, from, end, WAVE_COLOR, 3.0F, 2.0);
		MagicFx.burst(level, end.x, end.y, end.z, WAVE_COLOR, 1.5F, 24, 0.4, 0.4, 0.4);
		level.sendParticles(MagicFx.LIGHT, end.x, end.y, end.z, 10, 0.2, 0.2, 0.2, 0.15);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.NEUTRAL, 1.5F, 1.2F);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.2F, 1.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(WAVE_WIDTH),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy))) {
			Vec3 rel = e.getBoundingBox().getCenter().subtract(from);
			double along = rel.dot(dir);
			if (along < 0.0 || along > WAVE_LENGTH || rel.subtract(dir.scale(along)).length() > WAVE_WIDTH) {
				continue;
			}
			e.hurtServer(level, level.damageSources().mobAttack(caster), WAVE_DAMAGE);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WAVE_SLOW_TICKS, 1), caster);
			push(e, dir.horizontal().scale(WAVE_THROW), 0.2);
		}
		return true;
	}
}
