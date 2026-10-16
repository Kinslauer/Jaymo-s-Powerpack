package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterPower;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Raising a Spirit Bomb, the view pulls way back and up behind him, further as the sphere grows, so he sees the whole of
 * it over his head; let go, it eases back in. Walls stop it short. (0.13.5) Any Great Ape, wild or not, seen from
 * behind (third person) has the view over his shoulder, as far out as he's grown, so he sees himself; first person
 * stays in his eyes.
 */
public final class ZFighterSpiritCamera {
	/** How far back the view sits with a fresh bomb, and how much further a full one takes it (blocks). */
	static final double NEAR = 12.0, FAR = 30.0;
	/** How high it rises, as a share of the distance back. */
	static final double RISE = 0.45;
	/** How fast it eases out and back in (a share of the way a frame). */
	static final double EASE = 0.06;
	/** A Great Ape's shoulder view: how far back and up it sits (per block of his height) and out to the side (per block of his width). */
	static final double APE_BACK = 0.75, APE_UP = 0.12, APE_SIDE = 0.65;

	private static double pulled;

	private ZFighterSpiritCamera() {
	}

	/** Where the view goes from {@code eyes} this frame, or null when it sits where it always does. */
	public static @Nullable Vec3 position(Minecraft minecraft, Vec3 eyes, float partialTicks) {
		LocalPlayer player = minecraft.player;
		if (player != null && ZFighterLineage.ape(player) && minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
			// over his shoulder (the right), grown with him
			pulled = 0.0;
			float yaw = player.getViewYRot(partialTicks) * Mth.DEG_TO_RAD;
			Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
			double height = player.getBbHeight();
			Vec3 want = eyes.subtract(player.getViewVector(partialTicks).scale(height * APE_BACK)).add(0.0, height * APE_UP, 0.0)
				.add(right.scale(player.getBbWidth() * APE_SIDE));
			return clipped(player, eyes, want);
		}
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
		return clipped(player, eyes, eyes.subtract(look.scale(pulled)).add(0.0, pulled * RISE, 0.0));
	}

	/** {@code want}, or short of it where a wall's in the way. */
	private static Vec3 clipped(LocalPlayer player, Vec3 eyes, Vec3 want) {
		HitResult hit = player.level().clip(new ClipContext(eyes, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 back = hit.getLocation().subtract(eyes);
			double length = back.length();
			want = length < 0.6 ? eyes : eyes.add(back.scale((length - 0.5) / length));
		}
		return want;
	}
}
