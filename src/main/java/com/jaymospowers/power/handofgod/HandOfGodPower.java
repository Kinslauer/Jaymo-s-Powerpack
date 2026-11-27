package com.jaymospowers.power.handofgod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Powers;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

/**
 * The Hand of God: a patron god's favour, a fragment of divine authority in a mortal's hands. The power is a
 * school: each ability is a god's domain, usable by the chosen. Granted by a patron god's quest or ritual.
 *
 * <p>Abilities (on the standard ability keys):
 * <ul>
 * <li>Q, Celestial Hold: freeze the Sun and the Moon in the sky for a few seconds. The world's clock stands
 *     still — the light holds, the shadows stop — while everything else goes on. A global effect (the sky is
 *     one per level).</li>
 * <li>E, Celestial Turn: the Sun and the Moon jump to where you look. The sky snaps to your gaze and the
 *     world's clock follows. Also global.</li>
 * </ul>
 *
 * <p>Both abilities act on the Overworld's clock (the other dimensions have no sky). The freeze is a
 * {@link CelestialCommand} tick handler that holds the overworld's day time; the turn sets it to the angle
 * of the player's look.
 */
public final class HandOfGodPower implements Power {
	public static final HandOfGodPower INSTANCE = new HandOfGodPower();
	public static final Identifier ID = JaymosPowers.id("handofgod");

	/** Q: freeze the celestial bodies. */
	public static final int CELESTIAL_HOLD = 0;
	/** E: turn the sky to where you look. */
	public static final int CELESTIAL_TURN = 1;

	private HandOfGodPower() {
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return 0xFFD700;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.NETHER_STAR);
	}

	@Override
	public void onGained(ServerPlayer player) {
		CelestialCommand.init(player);
	}

	@Override
	public void onLost(ServerPlayer player) {
		CelestialCommand.clear(player);
	}

	@Override
	public void tick(ServerPlayer player) {
		CelestialCommand.tick(player);
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		if (key == PowerKey.ABILITY_1) {
			CelestialCommand.hold(player);
		} else if (key == PowerKey.ABILITY_2) {
			CelestialCommand.turn(player);
		}
	}

	@Override
	public boolean fated() {
		// not a random awakening: it comes only through a patron god's calling
		return false;
	}

	public static void init() {
		Powers.register(INSTANCE);
		CelestialCommand.register();
	}
}
