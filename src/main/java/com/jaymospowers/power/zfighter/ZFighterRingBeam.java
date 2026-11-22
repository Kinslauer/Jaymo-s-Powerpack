package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Soundscape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ring Beam: a spinning ring of ki that cuts along a line.
 * Charged technique on V ({@link ZFighterTraining#RING_BEAM}).
 * Fires a ring-shaped beam that cuts blocks and entities in its path.
 */
public final class ZFighterRingBeam {
	/** Ki to start, max at full charge. */
	static final float MIN_KI = 30.0F;
	static final float MAX_KI = 65.0F;
	/** Damage at min and max charge. */
	static final float DAMAGE_MIN = 10.0F;
	static final float DAMAGE_MAX = 22.0F;
	/** Ticks to full charge. */
	public static final int FULL_TICKS = 65;
	/** Cooldown after firing. */
	static final int COOLDOWN = 220;
	/** Ring radius (blocks). */
	static final double RING_RADIUS = 1.5;
	/** Ring thickness (blocks). */
	static final double RING_THICKNESS = 0.35;
	/** Range (blocks). */
	static final double RANGE = 50.0;
	/** Speed (blocks/tick). */
	static final double SPEED = 2.2;

	private ZFighterRingBeam() {
	}

	static void init() {
	}

	static void clear() {
	}

	static void start(ServerPlayer player, ZFighterPower.Live live) {
		// not used for charged
	}

	/** V held: gathering the ring between the hands. */
	static void gather(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = LockOn.aim(player);
		int color = ZFighterArts.color(live);
		Vec3 hands = ZFighterKi.waveHands(player, look);
		double size = 0.3 + 0.8 * charge;
		// ring forming
		ZFighterKi.orb(level, -(player.getId() * 4 + 7), hands, size, color);
		if (charge >= 1.0F && level.getGameTime() % 3 == 0) {
			level.sendParticles(MagicFx.CRACKLE, true, false, hands.x, hands.y, hands.z, 3, 0.3, 0.3, 0.3, 0.2);
		}
		if (Soundscape.due(player, "ki.wave", 8, 13)) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS, ZFighterKi.KI_HUM.pitched(0.7F + charge), ZFighterKi.KI_SURGE);
		}
	}

	/** V released: fire the ring beam. */
	static void fire(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = LockOn.aim(player);
		float kiPower = ZFighterPower.kiPower(player, live);
		int color = ZFighterArts.color(live);
		float damage = Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX) * kiPower;

		Vec3 from = ZFighterKi.waveHands(player, look).add(look.scale(0.5));
		Vec3 dir = look.normalize();

		// Fire the ring as a custom beam
		ZFighterWaveBeam.fire(player, from, look, new ZFighterWaveBeam.Spec(RANGE, SPEED, RING_RADIUS, damage,
				1.0F, 0, true, 0.0F, net.minecraft.world.level.Level.ExplosionInteraction.NONE, false,
				color, ZFighterKi.WAVE, ZFighterWaveBeam.strength(charge), false, 1.3F));

		// Add ring-specific visual: spinning particles along the path
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.1F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.6F);
		ZFighterHands.thrust(player, ZFighterHands.BOTH, ZFighterHands.PUSH);
		ZFighterHands.shout(player);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(1.5F * (1.0F + charge));
		}
	}

	static String blocker(ZFighterPower.Live live) {
		return null;
	}

	static float min(ServerPlayer player) {
		return MIN_KI;
	}

	static float max(ServerPlayer player) {
		return MAX_KI;
	}

	static int cooldown(ServerPlayer player) {
		return COOLDOWN;
	}
}