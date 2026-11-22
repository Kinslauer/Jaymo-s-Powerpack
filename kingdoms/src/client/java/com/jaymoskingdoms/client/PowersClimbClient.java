package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * What a mount does while someone climbs onto it, from Jaymo's Powers' client ({@code MountClimbPose.mountPose}), looked up
 * once by reflection so Kingdoms never needs Powers. Without Powers nobody climbs and this answers nothing. The war elephants
 * and the wind dragons read it to curl a trunk, lower a neck or a wing, in step with the rider.
 */
public final class PowersClimbClient {
	/** What the mount is asked to do ({@code MountClimb.POSE_*} in Powers). */
	public static final int KNEEL = 1, TRUNK = 2, CROUCH = 3, WING = 4, NECK = 5;

	/** What a mount does for a climb on it: {@code style} (the numbers above), which {@code side} (-1 left, 1 right, 0 neither), how far {@code amount} down (0 to 1) and how far {@code share} through the climb. */
	public record Pose(int style, int side, float amount, float share) {
	}

	private static java.lang.invoke.@Nullable MethodHandle pose;
	private static boolean looked;

	private PowersClimbClient() {
	}

	/** What the mount with this entity id is doing for a climb on it this frame, or null: nobody is climbing, or no Powers. */
	public static @Nullable Pose on(int mountId, float partialTicks) {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					pose = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.client.MountClimbPose"), "mountPose",
						java.lang.invoke.MethodType.methodType(float[].class, int.class, float.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Powers' mount climb pose isn't there; mounts won't bend for riders", e);
				}
			}
		}
		if (pose == null) {
			return null;
		}
		try {
			float[] p = (float[])pose.invoke(mountId, partialTicks);
			return p == null ? null : new Pose((int)p[0], (int)p[1], p[2], p[3]);
		} catch (Throwable e) {
			return null;
		}
	}
}
