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
 * </ul>
 * These actions share {@link RigNet.Use} with the Rig's own, numbered from {@link #FIRST}.
 */
public final class RigPicks {
	public static final int FIRST = 20;
	public static final int DRONES = 20, MARK = 21, TABLE = 22, DECOY = 23, FIELD = 24, BANK = 25;

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
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				RigDrones.tick(player);
				RigField.tick(player);
			}
		});
	}

	/** Whether {@code action} is one of these. */
	public static boolean is(int action) {
		return action >= FIRST && action <= BANK;
	}

	/** A Rig menu entry from the client. */
	public static void use(ServerPlayer player, int action) {
		if (player.isSpectator()) {
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
			default -> {
			}
		}
	}
}
