package com.jaymoskingdoms.client.nightforms;

import com.jaymoskingdoms.NightHorrors;
import com.jaymoskingdoms.nightforms.NightForms;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * A vampire player in a shape of the night ({@link NightForms}: bat, mist or the true shape) on the screen. They are not drawn, nor is their hand
 * in first person: the puppet the server made is all that is seen of them (client mixins {@code FormHidesPlayerMixin},
 * {@code FormHidesHandMixin}). Seeing the living through walls is blood vision's ({@code BloodVision}), unchanged. The true
 * shape's puppet is drawn by {@link NightHorrorRenderer}, and its one jump in the air is asked for here, off a fresh press of
 * the jump key while the feet are off the ground.
 */
public final class NightFormsClient {
	private static boolean jumpHeld;

	private NightFormsClient() {
	}

	public static void register() {
		EntityRenderers.register(NightHorrors.TYPE, NightHorrorRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			Player player = minecraft.player;
			boolean down = minecraft.options.keyJump.isDown();
			if (player != null && down && !jumpHeld && !player.onGround() && !player.isInWater() && !player.getAbilities().flying
				&& NightForms.HORROR.equals(NightForms.form(player))) {
				ClientPlayNetworking.send(new NightHorrors.AirJump());
			}
			jumpHeld = down;
		});
	}

	/** Whether {@code entity} is a player in a shape (the puppet is drawn in their place). */
	public static boolean shaped(Entity entity) {
		return entity instanceof Player player && !NightForms.form(player).isEmpty();
	}
}
