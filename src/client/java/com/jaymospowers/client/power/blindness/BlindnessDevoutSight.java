package com.jaymospowers.client.power.blindness;

import com.jaymospowers.client.GiantCamera;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.blindness.Blind;
import com.jaymospowers.power.blindness.BlindnessDevotion;
import com.jaymospowers.power.resonance.ResonancePower;
import com.jaymospowers.power.resonance.ResonanceSight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Resonance holder's sight in the dark ({@code Blind}, {@link BlindnessDevotion}), on their own screen: living things
 * near them burn through it (pale gold, a dull red for the hostile), and in Sky Sight ({@link ResonanceSight}) the view
 * rises high over them and looks almost straight down, eased in and out. Ceilings stop it short.
 */
public final class BlindnessDevoutSight {
	static final int PASSIVE = 0xE8D9A0, HOSTILE = 0xA03A2A;
	/** How high over the eyes and how far behind them Sky Sight sits (blocks), and how fast it eases (a share a frame). */
	static final double HEIGHT = 16.0, BACK = 4.0, EASE = 0.05;

	private static double raised;

	private BlindnessDevoutSight() {
	}

	/** Whether this client's player holds Resonance and is in the dark: both eyes gone, or a blindfold on. */
	public static boolean devout(@Nullable LocalPlayer player) {
		return player != null && Powers.has(player, ResonancePower.INSTANCE) && Blind.sightless(player);
	}

	/** Whether this client's player is in Sky Sight now. */
	public static boolean skySight(@Nullable LocalPlayer player) {
		if (!devout(player)) {
			return false;
		}
		return player.hasAttached(ResonanceSight.ACTIVE);
	}

	/** How far up the view is, 0 to 1. */
	public static double raised() {
		return Mth.clamp(raised, 0.0, 1.0);
	}

	/** The outline colour {@code entity} gets on this screen, or 0 for none. */
	public static int color(Entity entity) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (entity == player || !(entity instanceof LivingEntity) || !entity.isAlive() || !devout(player)) {
			return 0;
		}
		double range = skySight(player) ? BlindnessDevotion.SKY_SIGHT_RANGE : BlindnessDevotion.SIGHT_RANGE;
		if (entity.distanceToSqr(player) > range * range) {
			return 0;
		}
		return entity instanceof Enemy ? HOSTILE : PASSIVE;
	}

	/** Where Sky Sight puts the view from {@code eyes} this frame, as x, y, z, yaw, pitch; or null when it's not up. */
	public static double @Nullable [] view(Minecraft minecraft, Vec3 eyes, float partialTicks) {
		LocalPlayer player = minecraft.player;
		raised += ((skySight(player) ? 1.0 : 0.0) - raised) * EASE;
		if (raised < 0.02 || player == null) {
			return null;
		}
		float yaw = player.getViewYRot(partialTicks);
		Vec3 flat = Vec3.directionFromRotation(0.0F, yaw);
		double r = raised();
		Vec3 want = eyes.add(0.0, HEIGHT * r, 0.0).subtract(flat.scale(BACK * r));
		Vec3 pos = GiantCamera.clipped(player, eyes, want);
		Vec3 down = eyes.subtract(pos);
		double flatLength = Math.sqrt(down.x * down.x + down.z * down.z);
		float pitch = down.lengthSqr() < 0.01 ? player.getViewXRot(partialTicks)
			: (float)Math.toDegrees(Math.atan2(-down.y, Math.max(flatLength, 1.0E-4)));
		// from a low ceiling it still leans toward the player's own look
		pitch = (float)Mth.lerp(r, player.getViewXRot(partialTicks), pitch);
		return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
	}
}
