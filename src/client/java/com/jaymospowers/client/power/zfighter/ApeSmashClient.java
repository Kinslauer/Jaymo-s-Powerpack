package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ApeSmash;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * (0.16.6) The Great Ape's punch ({@link ApeSmash}): left mouse held draws the fist back, let go throws it. Vanilla's
 * swing and digging stand aside while you're the ape (ZFighterAttackMixin). Everyone sees the fist drawn back and
 * thrown ({@link #POSE}, in {@link ZFighterApe.Model}).
 */
public final class ApeSmashClient {
	/** On an ape's render state: how far the fist's drawn back (0-1) and how far through the blow (0-1, or -1). */
	public static final RenderStateDataKey<float[]> POSE = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ape_smash");
	/** The drawn-back fist and the thrown one (radians on the right arm), and the body's twist into it. */
	static final float DRAW_X = 1.1F, DRAW_Z = 0.5F, THROW_X = -1.9F, TWIST = 0.45F;

	private static boolean wasDown;

	private ApeSmashClient() {
	}

	/** Whether the Great Ape has the attack key just now (no swing, no digging). */
	public static boolean holdsAttack(Minecraft minecraft) {
		return minecraft.player != null && ZFighterLineage.ape(minecraft.player);
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		boolean down = ZFighterLineage.ape(player) && minecraft.gui.screen() == null && !player.isSpectator()
			&& minecraft.options.keyAttack.isDown();
		if (down != wasDown) {
			wasDown = down;
			ClientPlayNetworking.send(new ApeSmash.Key(down));
		}
	}

	static void reset() {
		if (wasDown) {
			wasDown = false;
			ClientPlayNetworking.send(new ApeSmash.Key(false));
		}
	}

	/** From the ape's extract: the punch's pose, or none. */
	static float[] pose(Player player) {
		long now = player.level().getGameTime();
		Long charge = player.getAttached(ApeSmash.CHARGE);
		Long swung = player.getAttached(ApeSmash.SWUNG);
		float drawn = charge == null ? 0.0F : Mth.clamp((now - charge) / (float)ApeSmash.FULL_TICKS, 0.0F, 1.0F);
		float swing = swung == null || now - swung >= ApeSmash.SWING_TICKS ? -1.0F : (now - swung) / (float)ApeSmash.SWING_TICKS;
		return charge == null && swing < 0.0F ? null : new float[] {drawn, swing};
	}
}
