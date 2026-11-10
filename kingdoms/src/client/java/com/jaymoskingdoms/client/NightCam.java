package com.jaymoskingdoms.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * The view while the night runs by ({@link com.jaymoskingdoms.Timelapse}): once everyone needed is asleep, the camera
 * leaves the pillow and drifts up and out over the bed, then circles it slowly so you watch the world hurry on until
 * the sun is up. Dragging the mouse swings it round and up or down, the scroll wheel brings it nearer or further. The
 * pull-out and the circling run on real time, not ticks, since the world's ticks are sped up. Leaving bed, or the
 * morning, puts the view straight back in your eyes.
 */
public final class NightCam {
	/** How far from the bed the camera settles (blocks) and the nearest and furthest the wheel takes it. */
	static final float DISTANCE = 18.0F, DISTANCE_MIN = 6.0F, DISTANCE_MAX = 48.0F, ZOOM_STEP = 2.0F;
	/** How far above level it looks down on the bed (degrees), and the most a drag tilts that either way. */
	static final float ELEVATION = 30.0F, ELEVATION_MIN = 5.0F, ELEVATION_MAX = 80.0F;
	/** Milliseconds the pull-out takes, and degrees a second the camera circles the bed once out. */
	static final long PULL_MS = 3500L;
	static final float ORBIT_DEG_PER_SEC = 3.0F;
	/** Blocks the camera may climb to get out of solid ground or walls. */
	static final int MOST_LIFT = 24;

	private static boolean active;
	private static long startMs, lastMs;
	private static float startYaw, orbit, distance = DISTANCE;

	private NightCam() {
	}

	/** Whether the free view is on now: asleep (not resting) while the night runs by. */
	public static boolean wanted(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && player.isSleeping() && !RestClient.resting && RestClient.timelapse(minecraft.level);
	}

	public static boolean active() {
		return active;
	}

	/** The wheel brings the camera nearer (up) or further (down). */
	public static void zoom(double scroll) {
		distance = Mth.clamp(distance - (float) scroll * ZOOM_STEP, DISTANCE_MIN, DISTANCE_MAX);
	}

	/**
	 * Where the camera goes this frame, from the pillow view it has now ({@code eye}, {@code eyeYaw}, {@code eyePitch}),
	 * or null when the free view is off. Returns {x, y, z, yaw, pitch}.
	 */
	public static double[] frame(Minecraft minecraft, Vec3 eye, float eyeYaw, float eyePitch, float partialTicks) {
		if (!wanted(minecraft)) {
			if (active) {
				active = false;
				distance = DISTANCE;
			}
			return null;
		}
		long now = Util.getMillis();
		if (!active) {
			active = true;
			startMs = now;
			lastMs = now;
			// (the pillow view looks along the bed, so starting there the camera backs away over the headboard)
			startYaw = eyeYaw - RestClient.lookYaw();
			orbit = 0.0F;
		}
		float pull = Mth.clamp((now - startMs) / (float) PULL_MS, 0.0F, 1.0F);
		if (pull >= 1.0F) {
			orbit += ORBIT_DEG_PER_SEC * (now - lastMs) / 1000.0F;
		}
		lastMs = now;
		LocalPlayer player = minecraft.player;
		Vec3 bed = new Vec3(Mth.lerp(partialTicks, player.xo, player.getX()), Mth.lerp(partialTicks, player.yo, player.getY()) + 0.4,
			Mth.lerp(partialTicks, player.zo, player.getZ()));
		float yaw = startYaw + orbit + RestClient.lookYaw();
		float elevation = Mth.clamp(ELEVATION - RestClient.lookPitch(), ELEVATION_MIN, ELEVATION_MAX);
		double rad = yaw * Mth.DEG_TO_RAD, up = elevation * Mth.DEG_TO_RAD;
		double flat = Math.cos(up) * distance;
		// (behind where it looks: a camera at yaw looks along -sin, +cos)
		Vec3 out = new Vec3(bed.x + Math.sin(rad) * flat, bed.y + Math.sin(up) * distance, bed.z - Math.cos(rad) * flat);
		for (int lift = 0; lift < MOST_LIFT && minecraft.level.getBlockState(BlockPos.containing(out)).isSolidRender(); lift++) {
			out = out.add(0.0, 1.0, 0.0);
		}
		Vec3 to = bed.subtract(out);
		float lookYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float lookPitch = (float) (-(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG));
		float p = Mth.smoothstep(pull);
		Vec3 at = eye.lerp(out, p);
		return new double[] {at.x, at.y, at.z, Mth.rotLerp(p, eyeYaw, lookYaw), Mth.lerp(p, eyePitch, lookPitch)};
	}
}
