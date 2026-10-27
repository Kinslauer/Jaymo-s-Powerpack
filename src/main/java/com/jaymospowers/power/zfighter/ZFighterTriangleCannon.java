package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.Soundscape;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.46, Andy's NOEA picks) Triangle Cannon, a charged technique (sneak+V, hold and let go): both hands make a triangle
 * and a crushing blast leaves it angled down, steeper than you look, to hit the ground a few blocks ahead and leave a
 * crater (the mob griefing rule decides whether the ground is taken). It costs health as well as ki: {@link #HEALTH_FULL}
 * at a full charge (two hearts), never taking him below {@link #HEALTH_FLOOR} (a heart); an Earthling pays half the health.
 * Fired as a broad {@link ZFighterWaveBeam} (so it hurts what it passes, pushes and can clash), only angled down.
 */
final class ZFighterTriangleCannon {
	static final float MIN_KI = 35.0F;
	static final float MAX_KI = 65.0F;
	static final int FULL_TICKS = 60;
	/** Damage to what the beam passes, charge 0 to full (times Ki Power). */
	static final float DAMAGE_MIN = 9.0F;
	static final float DAMAGE_MAX = 20.0F;
	static final double RANGE = 28.0, SPEED = 3.0;
	static final double RADIUS_MIN = 1.0, RADIUS_MAX = 1.8;
	/** The blast where it lands (power; a Super adds 30%): it takes the ground where mob griefing is on. */
	static final float BLAST_MIN = 3.0F;
	static final float BLAST_MAX = 6.0F;
	/** How steep it goes at the least: the beam's vertical share (negative: down) of a unit heading. */
	static final double MIN_DOWN = -0.55;
	/** Health it costs at a full charge (2 hearts), the least health it ever leaves, and an Earthling's share of the cost. */
	static final float HEALTH_FULL = 4.0F;
	static final float HEALTH_FLOOR = 2.0F;
	static final float EARTHLING_SHARE = 0.5F;
	static final double PUSH = 1.4;
	static final float EXHAUSTION = 2.5F;

	private ZFighterTriangleCannon() {
	}

	/** The hands make a triangle (three points of light and a glow in its middle) that grows with the charge. */
	static void gather(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 centre = player.getEyePosition().add(look.scale(0.8)).add(0.0, -0.4, 0.0);
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
		Vec3 up = right.cross(look).normalize();
		double span = 0.18 + 0.12 * charge;
		int color = ZFighterArts.color(live);
		int id = -(player.getId() * 4 + 3);
		// two thumbs below, two fingertips meeting above
		Vec3[] points = {centre.add(right.scale(-span)).add(up.scale(-span * 0.6)), centre.add(right.scale(span)).add(up.scale(-span * 0.6)),
			centre.add(up.scale(span * 1.1))};
		for (int i = 0; i < points.length; i++) {
			ZFighterKi.orb(level, id - 1000 * (i + 1), points[i], 0.1 + 0.1 * charge, color);
		}
		ZFighterKi.orb(level, id, centre, 0.15 + 0.5 * charge, color);
		DustParticleOptions dust = new DustParticleOptions(color, 0.8F + charge);
		for (int i = 0; i < points.length; i++) {
			// the edges of the triangle, one mote along each
			Vec3 a = points[i];
			Vec3 b = points[(i + 1) % points.length];
			Vec3 at = a.lerp(b, level.getRandom().nextDouble());
			level.sendParticles(dust, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (charge >= 1.0F && level.getGameTime() % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, centre.x, centre.y, centre.z, 2, 0.2, 0.2, 0.2, 0.2);
		}
		if (Soundscape.due(player, "ki.triangle", 8, 13)) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS, ZFighterKi.KI_HUM.louder(0.9F).pitched(0.7F + charge * 0.6F), ZFighterKi.KI_SURGE);
		}
	}

	/** Let go: the blast, angled down; the cost in health is paid. */
	static void fire(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 aim = LockOn.aim(player);
		// at least MIN_DOWN steep: the horizontal part is kept, the rest is turned downward
		double flat = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
		Vec3 dir;
		if (aim.y <= MIN_DOWN) {
			dir = aim;
		} else if (flat < 1.0E-4) {
			dir = new Vec3(0.0, -1.0, 0.0);
		} else {
			double horizontal = Math.sqrt(1.0 - MIN_DOWN * MIN_DOWN);
			dir = new Vec3(aim.x / flat * horizontal, MIN_DOWN, aim.z / flat * horizontal);
		}
		Vec3 from = ZFighterKi.waveHands(player, aim);
		float damage = Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX) * ZFighterPower.kiPower(player, live);
		boolean super_ = live.superOn();
		float power = Mth.lerp(charge, BLAST_MIN, BLAST_MAX) * (super_ ? 1.3F : 1.0F);
		int color = ZFighterArts.color(live);
		ZFighterWaveBeam.fire(player, from, dir, new ZFighterWaveBeam.Spec(RANGE, SPEED, Mth.lerp(charge, (float)RADIUS_MIN, (float)RADIUS_MAX), damage, PUSH, 0,
			false, power, Level.ExplosionInteraction.MOB, true, color, ZFighterKi.WAVE, ZFighterWaveBeam.strength(charge), false, 0.6F));
		payHealth(player, charge);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 0.6F);
		Shakes.send(player, 0.8F + 1.0F * charge, from);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION * (1.0F + charge));
		}
	}

	/** The blast takes health too: up to {@link #HEALTH_FULL}, half for an Earthling, never below {@link #HEALTH_FLOOR}. */
	private static void payHealth(ServerPlayer player, float charge) {
		if (player.isCreative()) {
			return;
		}
		float cost = HEALTH_FULL * charge * (ZFighterLineage.of(player) == ZFighterLineage.EARTHLING ? EARTHLING_SHARE : 1.0F);
		float taken = Math.min(cost, player.getHealth() - HEALTH_FLOOR);
		if (taken <= 0.0F) {
			return;
		}
		player.setHealth(player.getHealth() - taken);
		player.level().sendParticles(ParticleTypes.DAMAGE_INDICATOR, true, false, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), 4, 0.2,
			0.3, 0.2, 0.1);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5F, 0.9F);
	}
}
