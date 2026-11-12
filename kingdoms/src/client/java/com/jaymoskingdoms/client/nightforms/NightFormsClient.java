package com.jaymoskingdoms.client.nightforms;

import com.jaymoskingdoms.nightforms.NightForms;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * A vampire player in a shape of the night ({@link NightForms}: bat or mist) on the screen. They are not drawn, nor is their hand
 * in first person: the puppet the server made is all that is seen of them (client mixins {@code FormHidesPlayerMixin},
 * {@code FormHidesHandMixin}). Seeing the living through walls is blood vision's ({@code BloodVision}), unchanged.
 */
public final class NightFormsClient {
	private NightFormsClient() {
	}

	/** Whether {@code entity} is a player in a shape (the puppet is drawn in their place). */
	public static boolean shaped(Entity entity) {
		return entity instanceof Player player && !NightForms.form(player).isEmpty();
	}
}
