package com.jaymospowers.rig;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Rig's picks, five devices woken from the Core, each an entry in the Rig menu (Sneak + J) and
 * so a thing J can be set to:
 * <ul>
 * <li>{@link #DRONES} drones ({@link RigDrones}): launched if they are home, a missile if they are out.</li>
 * <li>{@link #MARK} a patrol waypoint where you look ({@link RigDrones#mark}).</li>
 * <li>{@link #TABLE} the wrist table ({@link RigTable}): the map, the studied kinds and the orders (formations, shell, fetch, mend, patrol, decoy kind, Bank).</li>
 * <li>{@link #DECOY} the decoy ({@link RigDecoy}).</li>
 * <li>{@link #FIELD} the storing field ({@link RigField}).</li>
 * <li>{@link #BANK} bank shot ({@link RigBank}).</li>
 * <li>{@link #PULLUP} pull-up warning ({@link RigActives}).</li>
 * <li>{@link #HYPER} hyper thrust ({@link RigActives}).</li>
 * <li>{@link #BANK_TURN} banking turns ({@link RigActives}).</li>
 * <li>{@link #METEOR} meteor dive ({@link RigDive}).</li>
 * <li>{@link #SMOOTH} smooth other flyers ({@link RigActives}).</li>
 * </ul>
 * These actions share {@link RigNet.Use} with the Rig's own, numbered from {@link #FIRST}.
 */
public final class RigPicks {
	public static final int FIRST = 20;
	public static final int DRONES = 20, MARK = 21, TABLE = 22, DECOY = 23, FIELD = 24, BANK = 25;
	/** The thruster dash and the artillery barrage ({@link RigStrikes}). */
	public static final int DASH = 26, BARRAGE = 27;
	/** The arm forms ({@link RigForms}). */
	public static final int AF_BLADE = 29, AF_MACE = 30, AF_PILE = 31, AF_DRILL = 32, AF_LANCE = 33, AF_LASH = 34,
		AF_MIMIC = 35;
	/** The failsafe's switch ({@link RigCall}): works with the suit parked too. */
	public static final int FAILSAFE = 28;
	/** (0.12.x) Flight actives: pull-up, hyper thrust, banking, meteor dive, smooth others. */
	public static final int PULLUP = 36, HYPER = 37, BANK_TURN = 38, METEOR = 39, SMOOTH = 40;

	private RigPicks() {
	}

	/** Called from {@code Rig.init}. */
	public static void init() {
		RigTable.init();
		RigStudy.init();
		RigDrones.init();
		RigDecoy.init();
		RigField.init();
		RigBank.init();
		RigStrikes.init();
		RigDive.init();
		RigForms.init();
		RigBack.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				RigDrones.tick(player);
				RigField.tick(player);
				RigStrikes.tick(player);
				RigBack.tick(player);
			}
		});
	}

	/** Whether {@code action} is one of these. */
	public static boolean is(int action) {
		return action >= FIRST && action <= SMOOTH || RigBack.is(action);
	}

	/** A Rig menu entry from the client. */
	public static void use(ServerPlayer player, int action) {
		if (player.isSpectator()) {
			return;
		}
		if (action == FAILSAFE) {
			RigCall.toggleFailsafe(player);
			return;
		}
		if (!Rig.wearsAny(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.needs_rig").withStyle(ChatFormatting.GRAY));
			return;
		}
		switch (action) {
			case DRONES -> RigDrones.use(player);
			case MARK -> RigDrones.mark(player);
			case TABLE -> RigTable.show(player, true);
			case DECOY -> RigDecoy.use(player);
			case FIELD -> RigField.use(player);
			case BANK -> RigBank.toggle(player);
			case DASH -> RigStrikes.dash(player);
			case BARRAGE -> RigStrikes.barrage(player);
			case AF_BLADE, AF_MACE, AF_PILE, AF_DRILL, AF_LANCE, AF_LASH, AF_MIMIC -> RigForms.use(player, action);
			case PULLUP -> RigActives.pullUp(player);
			case HYPER -> RigActives.hyperThrust(player);
			case BANK_TURN -> RigActives.toggleBanking(player);
			case METEOR -> RigDive.meteorDive(player);
			case SMOOTH -> RigActives.toggleSmoothOthers(player);
			case RigBack.WINGSLASH, RigBack.SPEARS, RigBack.PINCER, RigBack.GRAPPLE, RigBack.HORNS -> RigBack.use(player, action);
			default -> {
			}
		}
	}
}
