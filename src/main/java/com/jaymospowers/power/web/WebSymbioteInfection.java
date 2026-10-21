package com.jaymospowers.power.web;

import com.jaymospowers.core.Powers;
import java.util.Comparator;
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
 * blob that won't take that host again.</li>
 * </ul>
 * A web slinger's own bond is untouched by all of this.
 */
public final class WebSymbioteInfection {
	/** Three minutes. */
	public static final int ACCLIMATE_TICKS = 3600;
	/** Acclimated, the bond sits here (Tendril Grab, Living Armor, Wall Crawl, Consume and Shroud; not Spikes). */
	public static final int HOLD_BOND = 60;
	/** A web slinger this close (same world), and the black or red one leaves for them; checked every {@code SENSE_EVERY}. */
	public static final double WEB_SENSE_RANGE = 500.0;
	public static final int SENSE_EVERY = 40;
	/** Satiety under this and the black or red one leaves (where in a web slinger it would start hunting). */
	public static final int LEAVE_HUNGRY_BELOW = WebSymbiote.HUNGRY_HUNT_BELOW;
	/** Lines it has on leaving, of each kind. */
	public static final int LEAVE_LINES = 3;

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

	private static void tick(ServerPlayer player) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (!state.bonded() || !player.isAlive() || player.isSpectator()) {
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
			WebSymbiote.leave(player, "infection.hungry." + (1 + player.getRandom().nextInt(LEAVE_LINES)));
			return;
		}
		if (player.tickCount % SENSE_EVERY == 0) {
			ServerPlayer slinger = slinger(player);
			if (slinger != null) {
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
