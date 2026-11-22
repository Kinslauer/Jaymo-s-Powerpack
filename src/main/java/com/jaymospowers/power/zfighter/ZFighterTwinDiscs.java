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
import net.minecraft.world.phys.Vec3;

/**
 * Twin Discs: two discs at once, a step up from Ki Disc.
 * Charged technique on V ({@link ZFighterTraining#TWIN_DISCS}).
 * Throws two discs in a spread that both cut through everything.
 */
public final class ZFighterTwinDiscs {
	/** Ki to start, max at full charge. */
	static final float MIN_KI = 25.0F;
	static final float MAX_KI = 55.0F;
	/** Damage at min and max charge. */
	static final float DAMAGE_MIN = 8.0F;
	static final float DAMAGE_MAX = 15.0F;
	/** Ticks to full charge. */
	public static final int FULL_TICKS = 55;
	/** Cooldown after firing. */
	static final int COOLDOWN = 180;
	/** Spread angle between the two discs (radians). */
	static final double SPREAD = 0.25;

	private ZFighterTwinDiscs() {
	}

	static void init() {
	}

	static void clear() {
	}

	static void start(ServerPlayer player, ZFighterPower.Live live) {
		// not used for charged
	}

	/** V held: gathering the twin discs between the hands. */
	static void gather(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = LockOn.aim(player);
		int color = ZFighterArts.color(live);
		Vec3 hands = ZFighterKi.waveHands(player, look);
		// two orbs forming, one in each palm
		ZFighterKi.discOrb(level, -(player.getId() * 4 + 5), ZFighterHands.palm(player, look, true), 0.2 + 0.5 * charge, color);
		ZFighterKi.discOrb(level, -(player.getId() * 4 + 6), ZFighterHands.palm(player, look, false), 0.2 + 0.5 * charge, color);
		if (charge >= 1.0F && level.getGameTime() % 3 == 0) {
			level.sendParticles(MagicFx.CRACKLE, true, false, hands.x, hands.y, hands.z, 2, 0.2, 0.2, 0.2, 0.2);
		}
		if (Soundscape.due(player, "ki.wave", 8, 13)) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS, ZFighterKi.KI_HUM.pitched(0.8F + charge), ZFighterKi.KI_SURGE);
		}
	}

	/** V released: throw both discs. */
	static void fire(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = LockOn.aim(player);
		float kiPower = ZFighterPower.kiPower(player, live);
		int color = ZFighterArts.color(live);
		float damage = Mth.lerp(charge, DAMAGE_MIN, DAMAGE_MAX) * kiPower;

		// left disc
		Vec3 leftDir = look.yRot((float)-SPREAD).normalize();
		ZFighterKi.throwDisc(player, live, ZFighterHands.palm(player, leftDir, true), leftDir, damage);
		// right disc
		Vec3 rightDir = look.yRot((float)SPREAD).normalize();
		ZFighterKi.throwDisc(player, live, ZFighterHands.palm(player, rightDir, false), rightDir, damage);

		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.8F);
		ZFighterHands.thrust(player, ZFighterHands.BOTH, ZFighterHands.THROW);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(0.8F * (1.0F + charge));
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