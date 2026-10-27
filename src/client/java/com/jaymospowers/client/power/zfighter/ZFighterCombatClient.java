package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterGrab;
import com.jaymospowers.power.zfighter.ZFighterTraining;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * (0.17.45, Andy's NOEA picks) The client side of the combat feel that isn't a drawing of its own: it wires lock-on
 * ({@link ZFighterLockClient}), takes the left click for the throw while he holds something
 * ({@link ZFighterGrab}), and gives the new G techniques their icons.
 */
public final class ZFighterCombatClient {
	private ZFighterCombatClient() {
	}

	public static void init() {
		ZFighterLockClient.init();
		ClientTickEvents.START_CLIENT_TICK.register(ZFighterCombatClient::grab);
	}

	/** Holding something, a left click throws it (and is not an attack). */
	private static void grab(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !ZFighterGrab.holding(player) || minecraft.gui.screen() != null) {
			return;
		}
		boolean thrown = false;
		while (minecraft.options.keyAttack.consumeClick()) {
			thrown = true;
		}
		if (thrown) {
			ClientPlayNetworking.send(new ZFighterGrab.Throw());
		}
	}

	/** The icon on G for a technique of this round. */
	public static Item icon(int technique) {
		return switch (technique) {
			case ZFighterTraining.DRAGON_RUSH -> Items.DRAGON_BREATH;
			case ZFighterTraining.PACK_FANG -> Items.BONE;
			case ZFighterTraining.HAMMERLOCK -> Items.IRON_BLOCK;
			case ZFighterTraining.SKYWARD_CHASE -> Items.ELYTRA;
			case ZFighterTraining.KI_WEAPON -> Items.AMETHYST_SHARD;
			default -> Items.FIRE_CHARGE;
		};
	}
}
