package com.jaymospowers.power.resonance;

import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.core.gifted.Gifted;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.12) Resonance for a gifted villager ({@link Gifted}; Andy: "They should also be able to use resonance"): a pulse of
 * sound at the foe that knocks them back and breaks their guard, a sonic boom that goes through armour, a ring of sound
 * that throws back everyone too close, and a Hush, the dome of silence, round them when a player with a power comes at
 * them (Andy: "It's okay to silence the players": their power keys do nothing inside it, {@link ResonanceHush}).
 * Shrieker-taken (corrupted), they shriek: darkness on all who hear. A ruler's ultimate is the Collapse, a great boom out
 * all round them and a silence after. Out of a fight they hum, and glass and amethyst near them sing back.
 */
final class ResonanceGift implements GiftKit {
	/** The pulse: reach, damage, knockback (across, up) and cooldown. */
	static final double PULSE_RANGE = 8.0, PULSE_KNOCKBACK = 1.2, PULSE_UP = 0.3;
	static final float PULSE_DAMAGE = 3.5F;
	static final int PULSE_COOLDOWN = 50;
	/** The boom: reach, damage (through armour), knockback and cooldown. */
	static final double BOOM_RANGE = 16.0, BOOM_KNOCKBACK = 1.6;
	static final float BOOM_DAMAGE = 7.0F;
	static final int BOOM_COOLDOWN = 260;
	/** The ring: radius, damage, throw and cooldown. */
	static final double RING_RADIUS = 4.0, RING_THROW = 1.3;
	static final float RING_DAMAGE = 2.5F;
	static final int RING_COOLDOWN = 110;
	/** The hush: how near a powered player must be, how long it lasts (ticks), and its cooldown. */
	static final double HUSH_REACH = ResonanceHush.RADIUS - 1.0;
	static final int HUSH_TICKS = 80, HUSH_COOLDOWN = 500;
	/** Shrieker-taken: the shriek's radius, its darkness (ticks) and cooldown. */
	static final double SHRIEK_RADIUS = 12.0;
	static final int SHRIEK_TICKS = 120, SHRIEK_COOLDOWN = 400;
	/** A ruler's Collapse: radius, damage (through armour), throw, and the silence after (ticks). */
	static final double COLLAPSE_RADIUS = 8.0, COLLAPSE_THROW = 1.8;
	static final float COLLAPSE_DAMAGE = 7.0F;
	static final int COLLAPSE_HUSH = 100, COLLAPSE_FILM = 40;

	static void init() {
		Gifted.register(new ResonanceGift());
		// (0.17.103) a Rubber-folk who swallowed one: the Pulse itself
		com.jaymospowers.core.SwallowedPower.register(ResonancePower.ID, ResonancePower.PULSE_COOLDOWN, ResonancePulse::fire);
	}

	@Override
	public Identifier power() {
		return ResonancePower.ID;
	}

	@Override
	public List<Move> moves() {
		return List.of(
			Move.corrupt("shriek", Kind.CLOSE, SHRIEK_COOLDOWN, 0.0, SHRIEK_RADIUS, ResonanceGift::shriek),
			Move.of("boom", Kind.STRIKE, BOOM_COOLDOWN, 3.0, BOOM_RANGE, ResonanceGift::boom),
			Move.of("pulse", Kind.STRIKE, PULSE_COOLDOWN, 1.5, PULSE_RANGE, ResonanceGift::pulse),
			Move.of("hush", Kind.SUPPORT, HUSH_COOLDOWN, 0.0, HUSH_REACH, ResonanceGift::hush),
			Move.of("ring", Kind.CLOSE, RING_COOLDOWN, 0.0, RING_RADIUS, ResonanceGift::ring));
	}

	/** Sound rippling out along the line from {@code from} to {@code to}. */
	private static void line(ServerLevel level, Vec3 from, Vec3 to, double every) {
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)(step.length() / every));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.NOTE, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.6);
		}
	}

	private static boolean pulse(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 from = caster.getEyePosition(), to = foe.getBoundingBox().getCenter();
		line(level, from, to, 1.0);
		level.playSound(null, caster.getX(), caster.getEyeY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.4F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.NEUTRAL, 0.6F, 1.6F);
		boolean deep = ResonanceDamage.sensitive(foe);
		foe.hurtServer(level, ResonanceDamage.source(caster, deep ? ResonanceDamage.PULSE_DEEP : ResonanceDamage.PULSE), PULSE_DAMAGE);
		ResonanceDamage.push(foe, to.subtract(from), PULSE_KNOCKBACK, PULSE_UP);
		ResonancePulse.interrupt(foe);
		return true;
	}

	private static boolean boom(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 from = caster.getEyePosition(), to = foe.getBoundingBox().getCenter();
		Vec3 step = to.subtract(from);
		int steps = Math.max(1, (int)step.length());
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.add(step.scale((double)i / steps));
			level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.NEUTRAL, 2.0F, 1.1F);
		foe.hurtServer(level, ResonanceDamage.source(caster, ResonanceDamage.SONIC_BOOM), BOOM_DAMAGE);
		ResonanceDamage.push(foe, step, BOOM_KNOCKBACK, 0.4);
		return true;
	}

	private static boolean ring(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = caster.position().add(0.0, 0.2, 0.0);
		ResonanceEcho.ripple(level, c, RING_RADIUS);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.2F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.NEUTRAL, 1.0F, 0.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(RING_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy))) {
			e.hurtServer(level, ResonanceDamage.source(caster, ResonanceDamage.PULSE), RING_DAMAGE);
			ResonanceDamage.push(e, e.position().subtract(c), RING_THROW, 0.35);
			ResonancePulse.interrupt(e);
		}
		return true;
	}

	/** A dome of silence round them, when a player with a power comes at them (only they feel it: their keys go dead). */
	private static boolean hush(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		if (!(foe instanceof ServerPlayer player) || com.jaymospowers.core.Powers.of(player) == null) {
			return false;
		}
		return ResonanceHush.cast(level, caster, gift.corrupted(), HUSH_TICKS);
	}

	private static boolean shriek(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(SHRIEK_RADIUS),
			e -> e != caster && e.isAlive())) {
			e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SHRIEK_TICKS, 0, false, false, true), caster);
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.NEUTRAL, 2.0F, 0.9F);
		for (int i = 0; i < 6; i++) {
			level.sendParticles(new ShriekParticleOption(i * 5), caster.getX(), caster.getEyeY() + 0.2, caster.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
		return true;
	}

	@Override
	public Ultimate ultimate() {
		return new Ultimate(UltimateFilm.ORBIT, COLLAPSE_FILM, false, ResonanceGift::collapse);
	}

	/** The Collapse: a great boom out all round them, and the silence after it. */
	private static boolean collapse(ServerLevel level, PathfinderMob caster, LivingEntity foe, Gifted.Gift gift) {
		Vec3 c = caster.position();
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 12.0;
			level.sendParticles(ParticleTypes.SONIC_BOOM, c.x + Math.cos(a) * COLLAPSE_RADIUS * 0.6, c.y + 1.0, c.z + Math.sin(a) * COLLAPSE_RADIUS * 0.6,
				1, 0.0, 0.0, 0.0, 0.0);
		}
		ResonanceEcho.ripple(level, c.add(0.0, 0.1, 0.0), COLLAPSE_RADIUS);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.NEUTRAL, 3.0F, 0.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(COLLAPSE_RADIUS),
			e -> e != caster && e.isAlive() && (e == foe || e instanceof Enemy || e.getLastHurtByMob() == caster || caster.getLastHurtByMob() == e))) {
			e.hurtServer(level, ResonanceDamage.source(caster, ResonanceDamage.SONIC_BOOM), COLLAPSE_DAMAGE);
			ResonanceDamage.push(e, e.position().subtract(c), COLLAPSE_THROW, 0.5);
		}
		ResonanceHush.cast(level, caster, gift.corrupted(), COLLAPSE_HUSH);
		return true;
	}

	@Override
	public void everyday(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		// a hum, and the air round them singing back
		level.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 3, 0.4, 0.2, 0.4, 0.8);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F,
			0.6F + mob.getRandom().nextFloat() * 0.8F);
	}

	@Override
	public void corrupted(ServerLevel level, PathfinderMob mob) {
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.NEUTRAL, 1.5F, 0.7F);
	}
}
