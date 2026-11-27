package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Living Armor moves you (patch 4 of the 1.1.3 picks): while the armour is on ({@link WebSymbiote#livingArmor}, the thin
 * armour or the big body) and the bond is held, you run {@link #SPEED} faster and step up {@link #STEP} more (a full
 * block, over the 0.6 a player steps), as the suit's own legs carry you. Transient attribute modifiers, put on and taken
 * off every second, like the black strain's night ({@link WebSymbioteBody}) and the physique ({@link WebStrength}).
 */
public final class WebSymbioteStride {
	/** The share of your speed it adds (multiplied) and the height it adds to a step (blocks, on top of 0.6, so a block is climbed). */
	public static final double SPEED = 0.06, STEP = 0.5;
	static final int EVERY = 20;

	private static final Identifier SPEED_ID = JaymosPowers.id("web_symbiote_armor_speed");
	private static final Identifier STEP_ID = JaymosPowers.id("web_symbiote_armor_step");

	private WebSymbioteStride() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.tickCount % EVERY == 0) {
					tick(player);
				}
			}
		});
	}

	private static void tick(ServerPlayer player) {
		boolean on = WebSymbiote.isBonded(player) && WebSymbiote.livingArmor(player) && player.isAlive();
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);
		if (speed != null) {
			if (on) {
				if (speed.getModifier(SPEED_ID) == null) {
					speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
			} else {
				speed.removeModifier(SPEED_ID);
			}
		}
		if (step != null) {
			if (on) {
				if (step.getModifier(STEP_ID) == null) {
					step.addOrUpdateTransientModifier(new AttributeModifier(STEP_ID, STEP, AttributeModifier.Operation.ADD_VALUE));
				}
			} else {
				step.removeModifier(STEP_ID);
			}
		}
	}
}
