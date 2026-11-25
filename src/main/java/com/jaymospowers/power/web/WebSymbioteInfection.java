package com.jaymospowers.power.web;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.52) Andy: "Every player can now be infected with any symbiote. the only one they can keep long term is anti,
 * the other two variants will leave either when they get within 500 blocks of a web user, then seek them out, or they
 * leave after getting hungry. they stay at 60 bond once acclimated. it takes 3m to acclimate."
 * <ul>
 * <li><b>Anyone:</b> the goo and a roaming blob take anyone without a symbiote now, whatever their power (or none);
 * the suit, the radial menu (X) and its abilities answer them as they do a web slinger, less what rides a web
 * slinger's zips (Tendril Grab's pull to a block or a big creature, a takeover's zips).</li>
 * <li><b>Acclimating:</b> in someone who isn't a web slinger it lies dormant {@link #ACCLIMATE_TICKS} every time it
 * takes them (the dormancy of {@link WebSymbioteBond}, whispers and all, only shorter); then its bond goes to
 * {@link #HOLD_BOND} and stays there ({@link #held}): it neither warms nor cools.</li>
 * <li><b>Leaving:</b> only the anti one (white) stays for good. The black and the red leave such a host when a web
 * slinger comes within {@link #WEB_SENSE_RANGE} (and make for them: {@link WebSymbioteBlob#pursue}), or when they get
 * hungry (under {@link #LEAVE_HUNGRY_BELOW}, before they'd ever hunt with that body). Either way it pours out alive as a
 * blob that won't take that host again. (Andy, 2026-11-25) On meeting a web slinger it fights them first, driving its
 * host at them ({@link WebSymbiote#fightSlinger}), and only when its host is close to losing
 * ({@link #LOSING_HEALTH}) or the slinger is downed does it leave for them (and bind, not just chase).</li>
 * <li><b>Clinging:</b> without the Web nothing comes off by the host's choice: every try at the suit is a fight, and
 * winning gets the mask off at most ({@link WebSymbiote#clings}).</li>
 * <li><b>Warning:</b> Andy, of a clip where it left him minutes after settling in and wouldn't come back:
 * "why doesnt the symbiote want to rebind here?" It had been feeding his hunger bar out of its own satiety (its
 * share with the hunger bar, every two seconds) straight down past the leave line. In a host it can leave, it now keeps
 * {@link #WARN_HUNGRY_AT} back from that share and says once that it's hungry when it gets there (raw meat feeds it),
 * so it leaves only when the host has let it go hungry, not because it fed them.</li>
 * </ul>
 * A web slinger's own bond is untouched by all of this.
 */
public final class WebSymbioteInfection {
	/** Three minutes. */
	public static final int ACCLIMATE_TICKS = 3600;
	/**
	 * Acclimated, the bond sits here. Andy (2026-11-25): "the blob should give you all the abilities that it has", and the
	 * big body at this bond too ({@link WebSymbiote#unlockBond}, {@link WebSymbiote#bulkBond}).
	 */
	public static final int HOLD_BOND = 60;
	/** A web slinger this close (same world), and the black or red one leaves for them; checked every {@code SENSE_EVERY}. */
	public static final double WEB_SENSE_RANGE = 500.0;
	public static final int SENSE_EVERY = 40;
	/** Satiety under this and the black or red one leaves (where in a web slinger it would start hunting). */
	public static final int LEAVE_HUNGRY_BELOW = WebSymbiote.HUNGRY_HUNT_BELOW;
	/** Lines it has on leaving, of each kind. */
	public static final int LEAVE_LINES = 3;
	/**
	 * In a host it can leave, it stops feeding their hunger bar at this satiety, and tells them it's hungry
	 * ({@code WARN_LINES} lines, once each time it sinks here): about three minutes of its own hunger before it goes.
	 */
	public static final int WARN_HUNGRY_AT = LEAVE_HUNGRY_BELOW + 15;
	public static final int WARN_LINES = 3;
	/**
	 * (Andy, 2026-11-25) "close to losing": the host at or under this share of their most health (or downed), and it
	 * gives up the fight and goes into the slinger. The share the symbiote defends its host at ({@link WebSymbiote#DEFEND_HEALTH}).
	 */
	public static final float LOSING_HEALTH = WebSymbiote.DEFEND_HEALTH;
	/** Within this many blocks of the slinger it leaps straight in; farther, it pours out and makes for them ({@link WebSymbioteBlob#pursue}). */
	public static final double BIND_REACH = 6.0;
	/** Lines it has as it starts the fight, and as it binds to the slinger. */
	public static final int FIGHT_LINES = 3, BIND_LINES = 3;
	/** Hosts it has warned this hunger (not saved: a reload warns again). */
	private static final Set<UUID> WARNED = new HashSet<>();
	/** Host to the web slinger it is fighting; not saved. */
	private static final Map<UUID, UUID> FIGHTS = new HashMap<>();

	private WebSymbioteInfection() {
	}

	static void register() {
		// a web slinger's symbiote ticks with the power (WebPower.tick); anyone else's here
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (Powers.has(player, WebPower.INSTANCE) || !WebSymbiote.ticking(player)) {
					continue;
				}
				tick(player);
				WebSymbiote.tick(player);
				WebTendrils.tick(player);
			}
		});
	}

	/** A host who isn't a web slinger, with it acclimated: the bond is held at {@link #HOLD_BOND}. */
	static boolean held(ServerPlayer player) {
		return WebSymbiote.state(player).bonded() && !Powers.has(player, WebPower.INSTANCE) && !WebSymbioteBond.dormant(player);
	}

	/**
	 * How low the share with {@code player}'s hunger bar may take it ({@code floor}, the usual floor): in a host
	 * it will leave when hungry, never into the warning, so feeding them can't starve it out of them.
	 */
	static int shareFloor(ServerPlayer player, int floor) {
		return leavesWhenHungry(player) ? Math.max(floor, WARN_HUNGRY_AT) : floor;
	}

	/** Bonded to someone who isn't a web slinger, and not the anti one (white): it leaves them when it's hungry. */
	private static boolean leavesWhenHungry(ServerPlayer player) {
		WebSymbioteState state = WebSymbiote.state(player);
		return state.bonded() && state.kind() != WebSymbioteStrain.WHITE && !Powers.has(player, WebPower.INSTANCE);
	}

	private static void tick(ServerPlayer player) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (!state.bonded() || !player.isAlive() || player.isSpectator()) {
			WARNED.remove(player.getUUID());
			FIGHTS.remove(player.getUUID());
			return;
		}
		boolean acclimating = WebSymbioteBond.dormant(player);
		if (!acclimating && state.bond() != HOLD_BOND) {
			// settled in: the bond goes where it'll stay (each ability it opens is announced)
			WebSymbiote.addBond(player, HOLD_BOND - state.bond());
		}
		if (state.kind() == WebSymbioteStrain.WHITE) {
			// the anti one is yours to keep
			return;
		}
		if (!acclimating && state.satiety() < LEAVE_HUNGRY_BELOW) {
			WARNED.remove(player.getUUID());
			FIGHTS.remove(player.getUUID());
			WebSymbiote.endSlingerFight(player);
			WebSymbiote.leave(player, "infection.hungry." + (1 + player.getRandom().nextInt(LEAVE_LINES)));
			return;
		}
		if (state.satiety() > WARN_HUNGRY_AT) {
			WARNED.remove(player.getUUID());
		} else if (!acclimating && WARNED.add(player.getUUID())) {
			// a chance to feed it before it goes
			WebSymbiote.whisper(player, "infection.warn." + (1 + player.getRandom().nextInt(WARN_LINES)));
		}
		UUID id = player.getUUID();
		if (FIGHTS.containsKey(id)) {
			tickFight(player, id);
			return;
		}
		if (player.tickCount % SENSE_EVERY == 0) {
			ServerPlayer slinger = slinger(player);
			if (slinger != null) {
				if (acclimating || slinger.isCreative() || player.isCreative()) {
					// (dormant, it doesn't take the wheel; a creative slinger can't be beaten, nor a creative host driven: it leaves for them as it always has)
					WebSymbiote.leave(player, "infection.sense." + (1 + player.getRandom().nextInt(LEAVE_LINES))).pursue(slinger);
				} else if (closeToLosing(player) || Downed.isDowned(slinger)) {
					// deliberate: a host already close to losing, or a slinger already downed, leaves nothing to fight for
					bind(player, slinger);
				} else if (WebSymbiote.fightSlinger(player, slinger)) {
					// (Andy, 2026-11-25) it fights the slinger first, driving its host at them
					FIGHTS.put(id, slinger.getUUID());
				}
			}
		}
	}

	/** The host is downed, or at or under {@link #LOSING_HEALTH} of their most health. */
	private static boolean closeToLosing(ServerPlayer host) {
		return Downed.isDowned(host) || host.getHealth() <= host.getMaxHealth() * LOSING_HEALTH;
	}

	/** It pours out of its host and goes into the slinger (leaping straight in if they're near, else making for them). */
	private static void bind(ServerPlayer player, ServerPlayer slinger) {
		WebSymbioteBlob blob = WebSymbiote.leave(player, "infection.bind." + (1 + player.getRandom().nextInt(BIND_LINES)));
		if (slinger.level() != player.level() || slinger.distanceTo(player) > BIND_REACH || !blob.leapInto(slinger)) {
			blob.pursue(slinger);
		}
	}

	/**
	 * The fight with a web slinger goes on, or ends: the host close to losing, or the slinger downed, and it leaves them
	 * for the slinger (Andy: "if it gets close to losing it will bind to the slinger, it will also detach and bind if
	 * the slinger is downed"). Over some other way (the host broke free, it timed out, PvP off, the slinger out of
	 * range) while the slinger is still about, it falls back to leaving the host and pursuing the slinger as it always
	 * has, so it never seizes its host again and again. Over because the slinger is gone (dead, left, another world),
	 * nobody is left to pursue and {@link #slinger} no longer finds them.
	 */
	private static void tickFight(ServerPlayer player, UUID id) {
		ServerPlayer slinger = player.level().getServer().getPlayerList().getPlayer(FIGHTS.get(id));
		boolean about = slinger != null && slinger.isAlive() && !slinger.isSpectator() && slinger.level() == player.level();
		if (about && (closeToLosing(player) || Downed.isDowned(slinger))) {
			FIGHTS.remove(id);
			WebSymbiote.endSlingerFight(player);
			bind(player, slinger);
			return;
		}
		if (!WebSymbiote.fightingSlinger(player) || !about) {
			FIGHTS.remove(id);
			WebSymbiote.endSlingerFight(player);
			if (about) {
				WebSymbiote.leave(player, "infection.sense." + (1 + player.getRandom().nextInt(LEAVE_LINES))).pursue(slinger);
			}
		}
	}

	/** The nearest web slinger within {@link #WEB_SENSE_RANGE} of {@code host} (one without a symbiote first), or null. */
	private static @Nullable ServerPlayer slinger(ServerPlayer host) {
		double range = WEB_SENSE_RANGE * WEB_SENSE_RANGE;
		return host.level().players().stream()
			.filter(p -> p != host && p.isAlive() && !p.isSpectator() && Powers.has(p, WebPower.INSTANCE) && p.distanceToSqr(host) <= range)
			.min(Comparator.comparingDouble(p -> p.distanceToSqr(host) + (WebSymbiote.state(p).bonded() ? range : 0.0)))
			.orElse(null);
	}
}
