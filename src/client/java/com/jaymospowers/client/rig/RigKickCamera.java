package com.jaymospowers.client.rig;

import com.jaymospowers.client.UltimateCinematic;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Prosthetics;
import com.jaymospowers.rig.RigMoves;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The combo roundhouse's camera (Andy: "spins the camera around as you do a roundhouse kick"): on your own screen the
 * view pulls out behind you, swings all the way round with the body's turn ({@link RigPose#roundhouseSpin}) and comes
 * back in where it started. In first person it goes to the third-person view for the spin and back after it.
 */
public final class RigKickCamera {
	/** How far out the view swings (blocks), how far it looks down on you (degrees), how far under the eyes it turns about (blocks), its margin off walls. */
	static final double ORBIT_OUT = 3.6, PIVOT_DOWN = 0.3, WALL_MARGIN = 0.25;
	static final float ORBIT_PITCH = 18.0F;
	/** The vanilla third-person distance (blocks), where the view starts and ends for someone already in it. */
	static final double THIRD_PERSON = 4.0;
	/** Ticks to pull out and when it starts back in; the spin ends this many ticks before the move does. */
	static final float OUT_TICKS = 2.0F, IN_AT = 11.0F, END_EARLY = 2.0F;

	/** The roundhouse being filmed (its start), -1 for none; the view to go back to, or null; which way it turns. */
	private static long start = -1L;
	private static @Nullable CameraType viewBefore;
	private static float side = 1.0F;

	private RigKickCamera() {
	}

	/** Every client tick (RigClient). */
	static void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		RigMoves.Move move = self == null ? null : RigMoves.get(self);
		boolean on = self != null && move != null && move.kind() == RigMoves.ROUNDHOUSE && !UltimateCinematic.filming()
			&& minecraft.level != null && minecraft.level.getGameTime() - move.start() < RigMoves.ROUNDHOUSE_TICKS - END_EARLY;
		if (on && start != move.start()) {
			start = move.start();
			side = Prosthetics.hasRig(self, Dismember.RIGHT_LEG) || !Prosthetics.hasRig(self, Dismember.LEFT_LEG) ? 1.0F : -1.0F;
			if (viewBefore == null && minecraft.options.getCameraType().isFirstPerson()) {
				viewBefore = minecraft.options.getCameraType();
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else if (!on && start >= 0L) {
			start = -1L;
			if (viewBefore != null) {
				if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
					minecraft.options.setCameraType(viewBefore);
				}
				viewBefore = null;
			}
		}
	}

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null with no spin under way. */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		LocalPlayer self = minecraft.player;
		if (start < 0L || self == null || minecraft.level == null || minecraft.getCameraEntity() != self) {
			return null;
		}
		float t = minecraft.level.getGameTime() - start + partialTicks;
		float out = RigBeats.at(t, 0.0F, 0.0F, RigBeats.LINEAR, OUT_TICKS, 1.0F, RigBeats.OUT, IN_AT, 1.0F, RigBeats.LINEAR,
			RigMoves.ROUNDHOUSE_TICKS - END_EARLY, 0.0F, RigBeats.SINE);
		double base = viewBefore != null ? 0.0 : THIRD_PERSON;
		double distance = Mth.lerp(out, base, ORBIT_OUT);
		float yaw = self.getViewYRot(partialTicks) - side * 360.0F * RigPose.roundhouseSpin(t);
		float pitch = Mth.lerp(out, self.getViewXRot(partialTicks), ORBIT_PITCH);
		Vec3 pivot = self.getEyePosition(partialTicks).add(0.0, -PIVOT_DOWN * out, 0.0);
		Vec3 want = pivot.subtract(Vec3.directionFromRotation(pitch, yaw).scale(distance));
		HitResult hit = self.level().clip(new ClipContext(pivot, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self));
		Vec3 pos = want;
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 back = hit.getLocation().subtract(pivot);
			double length = back.length();
			pos = length > WALL_MARGIN ? pivot.add(back.scale((length - WALL_MARGIN) / length)) : pivot;
		}
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch};
	}
}
