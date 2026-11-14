package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Darkvision;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.astronomy.SunMoon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Seeing in the dark by blood, on the player's own screen ({@link Darkvision}): the {@code darkvision} post effect lifts
 * whatever is dim within sixteen blocks to grey shapes with no colour in them. Only the player's own view wears it, and
 * only through their own eyes: not while they watch through another's, nor with Night Vision on (that sees in colour).
 * And only where it is dark: standing in daylight or good lamplight the effect is off, since it can only tell "dim" by
 * how dark a pixel is, and would grey sunlit grass and dark stone.
 */
public final class DarkvisionClient {
	/** The post effect ({@code assets/jaymoskingdoms/post_effect/darkvision.json}). */
	public static final Identifier EFFECT = JaymosKingdoms.id("darkvision");

	/** The light at the eyes (0-15: lamps, or the sky's by how high the Sun stands) from which the sight is off. */
	static final int LIT = 12;
	/** The Sun's height (the sine of its altitude) from which the sky's light counts in full. */
	static final float SUN_FULL = 0.25F;

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
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		BlockPos at = BlockPos.containing(eye);
		float day = Mth.clamp((float)SunMoon.sun(minecraft.level, eye).y / SUN_FULL, 0.0F, 1.0F);
		float light = Math.max(minecraft.level.getBrightness(LightLayer.BLOCK, at), minecraft.level.getBrightness(LightLayer.SKY, at) * day);
		return light >= LIT ? null : EFFECT;
	}
}
