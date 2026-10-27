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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.46, Andy's NOEA picks) Nova Sphere, a charged technique (sneak+V, hold and let go): a huge ball of ki raised over your
 * head, bigger than a Ki Bomb and slower, thrown where you look. It bursts in a big blast that takes the ground where mob
 * griefing is on. It can't be used in base form unless you are Frost-born (who also pay half the TP for it, {@link
 * ZFighterTraining#techniqueCost}). The ball itself is a Ki Bomb's flight ({@link ZFighterKi#throwBall}), so it is drawn
 * and heard like one, just larger.
 */
final class ZFighterNovaSphere {
	static final float MIN_KI = 55.0F;
	static final float MAX_KI = 95.0F;
	static final int FULL_TICKS = 100;
	/** The ball's size (the Ki Bomb's is 0.6-1.6), its blast power and burst reach, charge 0 to full. */
	static final double SIZE_MIN = 1.4, SIZE_MAX = 2.8;
	static final float BLAST_MIN = 4.5F, BLAST_MAX = 8.0F;
	/** Burst reach (blocks) = this + size times this. */
	static final double REACH_BASE = 3.0, REACH_PER_SIZE = 2.2;
	/** Damage at the burst's centre, charge 0 to full (times Ki Power). */
	static final float DAMAGE_MIN = 18.0F;
	static final float DAMAGE_MAX = 36.0F;
	/** Blocks a tick (a Ki Bomb's is 0.6), and how long it flies before it bursts where it is. */
	static final double SPEED = 0.35;
	static final int LIFE = 160;
	/** How far above his head the ball hangs, past its own radius. */
	static final double LIFT = 0.8;
	/** The aim point it is thrown at (blocks along the look). */
	static final double AIM_DISTANCE = 30.0;
	static final float EXHAUSTION = 3.0F;

	private ZFighterNovaSphere() {
	}

	/** Why it can't be used now (a lang key), or null: base form only for the Frost-born. */
	static @Nullable String blocker(ZFighterPower.Live live) {
		return live.form == 0 && live.lineage != ZFighterLineage.FROST ? "message.jaymospowers.zfighter_nova_base" : null;
	}

	private static Vec3 overhead(ServerPlayer player, double size) {
		return player.position().add(0.0, player.getBbHeight() + LIFT + size, 0.0);
	}

	/** The ball swells over his head, ki drawn into it from all round. */
	static void gather(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		double size = Mth.lerp(charge, SIZE_MIN, SIZE_MAX);
		Vec3 at = overhead(player, size);
		int color = ZFighterArts.color(live);
		ZFighterKi.orb(level, -(player.getId() * 4 + 3), at, size * 2.0, color);
		if (level.getRandom().nextInt(2) == 0) {
			double ox = (level.getRandom().nextDouble() - 0.5) * 2.0 * size;
			double oy = (level.getRandom().nextDouble() - 0.5) * 2.0 * size;
			double oz = (level.getRandom().nextDouble() - 0.5) * 2.0 * size;
			level.sendParticles(new DustParticleOptions(color, 1.6F), true, false, at.x + ox * 1.5, at.y + oy * 1.5, at.z + oz * 1.5, 0, -ox, -oy, -oz, 0.1);
		}
		if (charge >= 1.0F && level.getGameTime() % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, at.x, at.y, at.z, 4, size * 0.5, size * 0.5, size * 0.5, 0.3);
		}
		if (Soundscape.due(player, "ki.nova", 8, 12)) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS, ZFighterKi.KI_HUM.louder(1.3F).pitched(0.5F + charge * 0.5F), ZFighterKi.KI_SURGE);
		}
	}

	/** Let go: thrown from over his head at where he looks. */
	static void fire(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		double size = Mth.lerp(charge, SIZE_MIN, SIZE_MAX);
		Vec3 from = overhead(player, size);
		Vec3 aimPoint = player.getEyePosition().add(LockOn.aim(player).scale(AIM_DISTANCE));
		Vec3 toward = aimPoint.subtract(from);
		Vec3 heading = toward.lengthSqr() < 1.0E-4 ? player.getViewVector(1.0F) : toward.normalize();
		float damage = Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX) * ZFighterPower.kiPower(player, live);
		float burst = Mth.lerp(charge, BLAST_MIN, BLAST_MAX) * (live.superOn() ? 1.3F : 1.0F);
		ZFighterKi.throwBall(player, from, heading.scale(SPEED), damage, ZFighterArts.color(live), size, REACH_BASE + size * REACH_PER_SIZE, burst, true, LIFE);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 0.6F);
		Shakes.send(player, 0.6F + 0.8F * charge, from);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION * (1.0F + charge));
		}
	}
}
