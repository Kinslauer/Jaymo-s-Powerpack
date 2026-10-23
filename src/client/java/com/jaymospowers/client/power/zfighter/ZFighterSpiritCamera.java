package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.GiantCamera;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Raising a Spirit Bomb, the view pulls way back and up behind him, further as the sphere grows, so he sees the whole of
 * it over his head; let go, it eases back in. Walls stop it short. (0.15.141) The Great Ape's view from behind is now
 * every giant's, {@link GiantCamera}.
 */
public final class ZFighterSpiritCamera {
	/** How far back the view sits with a fresh bomb, and how much further a full one takes it (blocks). */
	static final double NEAR = 12.0, FAR = 30.0;
	/** How high it rises, as a share of the distance back. */
	static final double RISE = 0.45;
	/** How fast it eases out and back in (a share of the way a frame). */
	static final double EASE = 0.06;

	private static double pulled;

	private ZFighterSpiritCamera() {
	}

	/** Where the view goes from {@code eyes} this frame, or null when it sits where it always does. */
	public static @Nullable Vec3 position(Minecraft minecraft, Vec3 eyes, float partialTicks) {
		LocalPlayer player = minecraft.player;
		double target = 0.0;
		if (player != null && Powers.has(player, ZFighterPower.INSTANCE)) {
			int bomb = ZFighterPower.state(player).bomb();
			if (bomb >= 0) {
				target = NEAR + (FAR - NEAR) * Math.sqrt(Mth.clamp(bomb / 100.0, 0.0, 1.0));
			}
		}
		pulled += (target - pulled) * EASE;
		if (pulled < 0.3 || player == null) {
			return null;
		}
		Vec3 look = player.getViewVector(partialTicks);
		return GiantCamera.clipped(player, eyes, eyes.subtract(look.scale(pulled)).add(0.0, pulled * RISE, 0.0));
	}

	/** Whether a Spirit Bomb has the view pulled out now (the giant's view gives way to it). */
	public static boolean pulling() {
		return pulled >= 0.3;
	}
}
