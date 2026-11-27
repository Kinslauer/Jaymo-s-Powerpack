package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Moves that flow (Night's Blood, patch 1). Andy, on what felt wrong in play: "it doesnt feel as coherent or fluid as any of
 * the other powers". A vampire's moves all sit on the skill bar, and these chains join them the way the Powers' combos join
 * theirs: one move opens a short window, and the right next move inside it is cheaper ({@link #COST_SHARE}) and reaches
 * further than it would cold. Each chain has its own window constant.
 * <ul>
 * <li><b>Dash into claws</b> ({@link #DASH_WINDOW}): a Blood Dash cancelled into Pounce or Rake. The claw move is cheaper, the
 * pounce finds prey further off ({@link #DASH_POUNCE_REACH}) and the rake starts from a longer reach ({@link #DASH_RAKE_REACH}).</li>
 * <li><b>Leap into feed</b> ({@link #LEAP_FEED_WINDOW}): a locked-on right-click leap that comes down on its prey goes straight
 * into Feed ({@link BloodLeap}): if it runs out of air a step short, the window still carries it in.</li>
 * <li><b>Groundpound into rend</b> ({@link #POUND_WINDOW}): those the pound threw back can be torn at without closing in first
 * ({@code ClawArts}: Rend lunges at the nearest of them within {@link #POUND_REND_REACH}).</li>
 * <li><b>Mist into leap</b> ({@link #MIST_WINDOW}): mist ending, by its own end or by a Blood Dash or a right-click leap on it,
 * is thrown out in a stronger leap ({@link #MIST_LEAP_BOOST}), in the air too.</li>
 * </ul>
 */
public final class NightFlow {
	// ---- tuning ----
	/** How long each chain's window stays open after the move that opens it (ticks). */
	public static final int DASH_WINDOW = 14, LEAP_FEED_WINDOW = 12, POUND_WINDOW = 40, MIST_WINDOW = 30;
	/** What a chained move costs, as a share of its cold cost. */
	public static final float COST_SHARE = 0.5F;
	/** After a dash: how far Pounce reaches and how far Rake starts from (blocks). */
	public static final double DASH_POUNCE_REACH = 20.0, DASH_RAKE_REACH = 6.0;
	/** After a pound: how far Rend finds a foe it threw (blocks). */
	public static final double POUND_REND_REACH = 10.0;
	/** Out of mist: how much stronger the leap or dash is. */
	public static final double MIST_LEAP_BOOST = 1.3;

	/** The chains' windows. */
	public enum Link {
		DASH_CLAW, POUND_REND, MIST_LEAP
	}

	private static final Map<UUID, Map<Link, Long>> OPEN = new HashMap<>();
	/** The foes a groundpound threw (entity ids), by who pounded. */
	private static final Map<UUID, List<Integer>> POUNDED = new HashMap<>();

	private NightFlow() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			OPEN.remove(handler.player.getUUID());
			POUNDED.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OPEN.clear();
			POUNDED.clear();
		});
	}

	/** Opens {@code link}'s window for {@code ticks}. */
	public static void open(ServerPlayer player, Link link, int ticks) {
		OPEN.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(Link.class)).put(link, player.level().getGameTime() + ticks);
	}

	/** Whether {@code link}'s window is open for {@code player} now. */
	public static boolean within(ServerPlayer player, Link link) {
		Map<Link, Long> mine = OPEN.get(player.getUUID());
		Long until = mine == null ? null : mine.get(link);
		return until != null && player.level().getGameTime() <= until;
	}

	/** Closes {@code link}'s window (the chain was taken). */
	public static void close(ServerPlayer player, Link link) {
		Map<Link, Long> mine = OPEN.get(player.getUUID());
		if (mine != null) {
			mine.remove(link);
		}
	}

	/** {@code mb}, at {@link #COST_SHARE} of it when {@code chained}. */
	public static int cost(int mb, boolean chained) {
		return chained ? Math.round(mb * COST_SHARE) : mb;
	}

	/** Remembers the foes {@code player}'s groundpound threw and opens its Rend window. */
	static void pounded(ServerPlayer player, List<LivingEntity> thrown) {
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : thrown) {
			ids.add(e.getId());
		}
		POUNDED.put(player.getUUID(), ids);
		open(player, Link.POUND_REND, POUND_WINDOW);
	}

	/** The nearest living foe the last groundpound threw, within {@link #POUND_REND_REACH}, in sight, while its window is open; else null. */
	static @Nullable LivingEntity poundedFoe(ServerPlayer player) {
		if (!within(player, Link.POUND_REND)) {
			return null;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (int id : POUNDED.getOrDefault(player.getUUID(), List.of())) {
			if (player.level().getEntity(id) instanceof LivingEntity e && e.isAlive() && player.hasLineOfSight(e)) {
				double d = e.distanceToSqr(player);
				if (d <= POUND_REND_REACH * POUND_REND_REACH && d < bestD) {
					best = e;
					bestD = d;
				}
			}
		}
		return best;
	}
}
