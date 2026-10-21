package com.jaymospowers.power.wind;

import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.web.KitchenCompat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * (0.15.54) How a Wind user meets the ground. Andy (2026-10-21): "fall damage as wind, i have a clip where the user
 * slowly descended and still took the damage. It shouldnt work this way past mastery 2, and even then the damage should
 * scale lower related to the speed." From {@link #SAFE_MASTERY} unlocked abilities on, falls never hurt; before that a
 * fall's damage is scaled by how fast they were really coming down when they landed (the server's own measure, the
 * fastest sink of the last few ticks, easing off as they slow): a drift or a slow hover sink is nothing, a plain drop
 * hurts in full. Server side; the scaling is applied in {@code WindFallMixin}.
 * <p>
 * And the ragdoll (Jaymo's Kitchen's living ragdolls): taking to the air holds a Wind user up, so catching a fall with
 * the flight never leaves them limp, nor does a landing just after ({@link #RAGDOLL_GRACE}). Lying limp, R only gets them
 * back into the air from {@link #ESCAPE_MASTERY} on; below that the wind won't answer until they're up.
 */
public final class WindFall {
	/** From this many unlocked abilities a Wind user never takes fall damage. */
	static final int SAFE_MASTERY = 2;
	/** Coming down slower than this (blocks a tick) does no harm; this fast or more, a fall's full damage. */
	static final float SINK_SAFE = 0.45F, SINK_FULL = 1.5F;
	/** How fast the remembered sink eases off once they slow (blocks a tick, per tick). */
	static final float SINK_EASE = 0.25F;
	/** For this long (ticks) after flying, a Wind user can't be knocked limp (a late catch still lands). */
	static final int RAGDOLL_GRACE = 20;
	/** From this many unlocked abilities, R takes a limp Wind user back into the air (and so out of the ragdoll). */
	public static final int ESCAPE_MASTERY = 2;

	/** The fastest each Wind user has been coming down lately (blocks a tick). */
	private static final Map<UUID, Float> SINK = new HashMap<>();

	private WindFall() {
	}

	/** Every tick, per Wind user: how fast they're coming down. */
	static void tick(ServerPlayer player) {
		float sink = (float)Math.max(0.0, player.yo - player.getY());
		float last = SINK.getOrDefault(player.getUUID(), 0.0F);
		SINK.put(player.getUUID(), Math.max(sink, last - SINK_EASE));
	}

	static void forget(ServerPlayer player) {
		SINK.remove(player.getUUID());
	}

	/** A Wind user's fall damage (whole points), after the mastery and the speed have their say. */
	public static int damage(ServerPlayer player, int damage) {
		if (damage <= 0 || !Powers.has(player, WindPower.INSTANCE)) {
			return damage;
		}
		if (Mastery.unlocked(player) >= SAFE_MASTERY) {
			return 0;
		}
		float sink = SINK.getOrDefault(player.getUUID(), SINK_FULL);
		float share = Mth.clamp((sink - SINK_SAFE) / (SINK_FULL - SINK_SAFE), 0.0F, 1.0F);
		return Math.round(damage * share);
	}

	/** Whether the wind has hold of {@code player}, so they mustn't go limp (and get up if they are). */
	public static boolean holdsUp(Player player) {
		if (!Powers.has(player, WindPower.INSTANCE)) {
			return false;
		}
		Integer state = player.getAttached(WindPower.FLIGHT);
		WindFlight flight = state == null ? null : WindFlight.byId(state);
		if (flight != null && flight.flying()) {
			return true;
		}
		// (the server's own record; a client asks only the synced state)
		return player instanceof ServerPlayer serverPlayer && WindPower.INSTANCE.flewRecently(serverPlayer, RAGDOLL_GRACE);
	}

	/** Whether {@code player} may take off now: anyone not lying limp, and the limp only once they've mastered enough. */
	public static boolean canTakeOff(Player player) {
		return Mastery.unlocked(player) >= ESCAPE_MASTERY || !KitchenCompat.isLimp(player);
	}
}
