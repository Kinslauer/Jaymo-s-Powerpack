package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Raising a Spirit Bomb, the view pulls way back and up behind him, further as the sphere grows, so he sees the whole of
 * it over his head; let go, it eases back in. Walls stop it short.
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
		Vec3 want = eyes.subtract(look.scale(pulled)).add(0.0, pulled * RISE, 0.0);
		HitResult hit = player.level().clip(new ClipContext(eyes, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 back = hit.getLocation().subtract(eyes);
			double length = back.length();
			want = length < 0.6 ? eyes : eyes.add(back.scale((length - 0.5) / length));
		}
		return want;
	}
}
