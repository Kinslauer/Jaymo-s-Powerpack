package com.jaymospowers.client.rig;

import com.jaymospowers.rig.RigMoves;
import com.jaymospowers.rig.RigUnibeam;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.player.Player;

/**
 * The weight of the Rig's biggest shots, on this screen:
 * <ul>
 * <li><b>The sound dips:</b> as a Unibeam or a barrage goes off within {@link #DUCK_RANGE} blocks, everything else
 * (the world, mobs, weather, music) drops to {@link #DUCK_SHARE} for a moment and swells back, so the shot is all there
 * is to hear. The players' own channel, voices and the menus are left alone.</li>
 * <li><b>A slow beat:</b> the wearer's own Unibeam leaving the chest slows their game to {@link #SLOW_RATE} ticks a
 * second for {@link #SLOW_MS} ms. Only on their own screen: the server and everyone else run on as ever. The rate goes
 * back to what the server had it at, and the Rig menu can switch the beat off.</li>
 * </ul>
 */
public final class RigImpact {
	/** The dip: how near the shot has to be (blocks), the share other sounds drop to, how long it holds and how long the swell back takes (ms). */
	static final double DUCK_RANGE = 24.0;
	static final float DUCK_SHARE = 0.25F;
	static final long DUCK_HOLD_MS = 500L, DUCK_SWELL_MS = 900L;
	/** The sources that dip. */
	static final SoundSource[] DUCKED = {SoundSource.MUSIC, SoundSource.RECORDS, SoundSource.WEATHER, SoundSource.BLOCKS, SoundSource.HOSTILE,
		SoundSource.NEUTRAL, SoundSource.AMBIENT};
	/** The slow beat: the tick rate it drops to, and how long it lasts (ms, on the wall clock). */
	static final float SLOW_RATE = 8.0F;
	static final long SLOW_MS = 450L;
	/** The Rig menu's switch for the slow beat, kept in the client's settings (on unless switched off). */
	static final String KEY_SLOW = "rig.slow_beat";

	private static long duckFrom, slowUntil;
	private static boolean ducked, slowed;
	private static float rateBefore;
	/** Players whose big shot has already been answered (entity ids), until their move ends. */
	private static final Set<Integer> ANSWERED = new HashSet<>();

	private RigImpact() {
	}

	static boolean slowBeat() {
		return !"false".equals(com.jaymospowers.client.power.zfighter.ZFighterClientConfig.get(KEY_SLOW, "true"));
	}

	static void toggleSlowBeat() {
		com.jaymospowers.client.power.zfighter.ZFighterClientConfig.set(KEY_SLOW, Boolean.toString(!slowBeat()));
	}

	/** Every client tick. */
	static void tick(Minecraft minecraft, LocalPlayer local) {
		long time = local.level().getGameTime();
		for (Player player : local.level().players()) {
			RigMoves.Move move = RigMoves.get(player);
			boolean big = move != null && (move.kind() == RigMoves.UNIBEAM && time - move.start() >= RigUnibeam.CHARGE_TICKS
				|| move.kind() == RigMoves.BARRAGE && time - move.start() >= RigMoves.BARRAGE_FIRE);
			if (!big) {
				ANSWERED.remove(player.getId());
			} else if (ANSWERED.add(player.getId()) && player.distanceToSqr(local) <= DUCK_RANGE * DUCK_RANGE) {
				duckFrom = Util.getMillis();
				if (player == local && move.kind() == RigMoves.UNIBEAM && slowBeat()) {
					slow(minecraft);
				}
			}
		}
		duck(minecraft);
		if (slowed && Util.getMillis() >= slowUntil) {
			restore(minecraft);
		}
	}

	private static void duck(Minecraft minecraft) {
		long since = Util.getMillis() - duckFrom;
		boolean on = duckFrom != 0L && since < DUCK_HOLD_MS + DUCK_SWELL_MS;
		if (!on && !ducked) {
			return;
		}
		float share = !on ? 1.0F : since < DUCK_HOLD_MS ? DUCK_SHARE : Mth.lerp((since - DUCK_HOLD_MS) / (float)DUCK_SWELL_MS, DUCK_SHARE, 1.0F);
		for (SoundSource source : DUCKED) {
			minecraft.getSoundManager().updateCategoryVolume(source, minecraft.options.getSoundSourceVolume(source) * share);
		}
		ducked = on;
	}

	private static void slow(Minecraft minecraft) {
		if (minecraft.level == null) {
			return;
		}
		TickRateManager rate = minecraft.level.tickRateManager();
		if (!slowed) {
			rateBefore = rate.tickrate();
		}
		if (rateBefore <= SLOW_RATE) {
			return;
		}
		rate.setTickRate(SLOW_RATE);
		slowed = true;
		slowUntil = Util.getMillis() + SLOW_MS;
	}

	private static void restore(Minecraft minecraft) {
		slowed = false;
		if (minecraft.level != null && minecraft.level.tickRateManager().tickrate() == SLOW_RATE) {
			minecraft.level.tickRateManager().setTickRate(rateBefore);
		}
	}

	/** Leaving the world: nothing stays dipped or slowed. */
	static void clear(Minecraft minecraft) {
		if (ducked) {
			duckFrom = 0L;
			duck(minecraft);
		}
		slowed = false;
		ANSWERED.clear();
	}
}
