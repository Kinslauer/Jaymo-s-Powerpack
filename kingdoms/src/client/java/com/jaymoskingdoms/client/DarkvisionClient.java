package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Darkvision;
import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.Nullable;

/**
 * Seeing in the dark by blood, on the player's own screen ({@link Darkvision}): the {@code darkvision} post effect lifts
 * whatever is dim within sixteen blocks to grey shapes with no colour in them. Only the player's own view wears it, and
 * only through their own eyes: not while they watch through another's, nor with Night Vision on (that sees in colour).
 */
public final class DarkvisionClient {
	/** The post effect ({@code assets/jaymoskingdoms/post_effect/darkvision.json}). */
	public static final Identifier EFFECT = JaymosKingdoms.id("darkvision");

	private DarkvisionClient() {
	}

	/** The post effect the screen wears this frame, or null. */
	public static @Nullable Identifier effect() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.getCameraEntity() != player || player.isSpectator()
			|| player.hasEffect(MobEffects.NIGHT_VISION) || !Darkvision.has(player)) {
			return null;
		}
		return EFFECT;
	}
}
