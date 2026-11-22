package com.jaymospowers.core;

import com.jaymospowers.power.web.KitchenCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Andy: "you should also use both arms when lifting anything other than small animals and mobs. so you shouldnt be able
 * to use abilities that require both hands, like the web glide". Whether a player's arms are both full with what they
 * lift: our carry of anyone or anything but a small animal (a severed part or a drag is one hand), a Kitchen corpse not
 * held by the neck in one hand, or a block in Carry On's arms. Both sides (every part of it is synced).
 * Checked wherever a move needs both arms: the web wings, the aercloud glider, the paraglider.
 */
public final class ArmsFull {
	/** A non-person this small or smaller (wide, tall; Kitchen's one-handed size) is held in one hand. */
	static final float SMALL_WIDTH = 0.6F, SMALL_HEIGHT = 0.8F;

	private ArmsFull() {
	}

	public static boolean both(Player player) {
		Carry.Carrying carrying = player.getAttached(Carry.CARRYING);
		if (carrying != null && carrying.hold() != Carry.PART && carrying.hold() != Carry.DRAG) {
			if (carrying.hold() == Carry.BLOCK || carrying.hold() == Carry.OVERHEAD) {
				return true;
			}
			Entity held = player.level().getEntity(carrying.target());
			if (held == null || Carry.person(held) || held.getBbWidth() > SMALL_WIDTH || held.getBbHeight() > SMALL_HEIGHT) {
				return true;
			}
		}
		return KitchenCompat.corpseInBothArms(player) || CarryOnCompat.isCarrying(player);
	}

	/** Tells the player why a two-handed move won't go. */
	public static void refuse(Player player) {
		player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.arms_full"));
	}
}
