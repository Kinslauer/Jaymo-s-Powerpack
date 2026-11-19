package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * The armour a plated costume gives while it is worn (Andy: "could add armor values to the system as a whole"). Each plate
 * ({@link CostumePlates}) adds its slot's points times its metal's multiplier, as one armour modifier kept up to date every
 * {@link #EVERY} ticks. Two rules keep it from stacking past sense: a plate counts only in a slot where the player wears no
 * real armour piece (the costume stands in for the armour there, as it hides it), and the whole costume gives at most
 * {@link #MAX_POINTS}, chainmail's 12 (a full suit of iron gives 7, of steel 10.5, of netherite 11.9).
 */
public final class CostumePlating {
	static final Identifier MODIFIER = JaymosPowers.id("costume_plates");
	/** The most armour points a costume's plates give. */
	public static final double MAX_POINTS = 12.0;
	/** How often (ticks) a player's plating is checked. */
	static final int EVERY = 10;

	private CostumePlating() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					update(player);
				}
			}
		});
	}

	/** The armour points {@code design}'s plates give {@code player} just now. */
	public static double points(Player player, CostumeDesign design) {
		double total = 0.0;
		for (CostumePlates.Slot slot : CostumePlates.Slot.values()) {
			CostumePlates.Metal metal = design.kit().plates().at(slot);
			if (metal != CostumePlates.Metal.NONE && player.getItemBySlot(slot.equipment).isEmpty()) {
				total += slot.armour * metal.armour;
			}
		}
		return Math.min(MAX_POINTS, Math.round(total * 2.0) / 2.0);
	}

	private static void update(ServerPlayer player) {
		AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
		if (armour == null) {
			return;
		}
		CostumeDesign worn = Costume.shown(player);
		double points = worn == null ? 0.0 : points(player, worn);
		AttributeModifier now = armour.getModifier(MODIFIER);
		if (points <= 0.0) {
			if (now != null) {
				armour.removeModifier(MODIFIER);
			}
		} else if (now == null || now.amount() != points) {
			armour.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, points, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
