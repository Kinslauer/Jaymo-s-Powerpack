package com.jaymospowers.client.rig;

import com.jaymospowers.client.UltimateCinematic;
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
 * The wrap suit-up's camera (Andy wanted the Rig's suit-ups like the Iron Man 2 one, which is filmed from the front): on
 * your own screen the view swings round in front of you as the arms go out, close on the chest while the gauntlets and
 * chest plates lock on, drifts a little round you, comes in on the face as the helm folds over and the eyes light, then
 * pulls back to show the whole suit and goes back behind you. In first person it goes to the third-person view for it
 * and back after. Getting hurt ends it at once. The suit-down (parking it) is filmed the same way in its own order: on the
 * face as the helm comes off, back to the chest as the legs, chest and gauntlets go, then the wide shot.
 */
public final class RigSuitUpCamera {
	/** How far out the view sits (blocks): on the chest, on the face, the last wide shot; its margin off walls. */
	static final double CHEST = 2.3, FACE = 1.5, WIDE = 4.2, WALL_MARGIN = 0.25;
	/** How far under the eyes it looks at (blocks): the chest, the face. */
	static final double CHEST_DOWN = 0.6, FACE_DOWN = 0.05;
	/** How far round the front it drifts over the build (degrees, from a little to one side to a little to the other), and how far it looks down. */
	static final float DRIFT_FROM = 28.0F, DRIFT_TO = -18.0F, PITCH = 6.0F;
	/** Beats (ticks into the move): swung round by, on the face from and until, wide from, and back behind you by the end. */
	static final float IN = 8.0F, FACE_FROM = 54.0F, FACE_AT = 60.0F, FACE_UNTIL = 70.0F, WIDE_AT = 78.0F;
	/** The suit-down's beats: on the face until, back on the chest by, wide from and by. */
	static final float DOWN_FACE_UNTIL = 18.0F, DOWN_CHEST_AT = 26.0F, DOWN_WIDE_FROM = 54.0F, DOWN_WIDE_AT = 64.0F;
	/** The vanilla third-person distance (blocks), where the view starts and ends for someone already in it. */
	static final double THIRD_PERSON = 4.0;

	/** The suit-up being filmed (its start), -1 for none; the view to go back to, or null. */
	private static long start = -1L;
	private static @Nullable CameraType viewBefore;
	/** Whether it's the suit-down being filmed. */
	private static boolean down;

	private RigSuitUpCamera() {
	}

	/** Every client tick (RigClient). */
	static void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;
		RigMoves.Move move = self == null ? null : RigMoves.get(self);
		boolean on = self != null && move != null && filmed(move.kind()) && !UltimateCinematic.filming() && self.hurtTime == 0
			&& minecraft.level != null && minecraft.level.getGameTime() - move.start() < RigMoves.length(move.kind()) && start != -2L - move.start();
		if (on && start != move.start()) {
			start = move.start();
			down = move.kind() == RigMoves.SUITDOWN;
			if (viewBefore == null && minecraft.options.getCameraType().isFirstPerson()) {
				viewBefore = minecraft.options.getCameraType();
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else if (!on && start >= 0L) {
			// (hurt part way: this suit-up is not filmed again)
			start = move != null && filmed(move.kind()) ? -2L - move.start() : -1L;
			if (viewBefore != null) {
				if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
					minecraft.options.setCameraType(viewBefore);
				}
				viewBefore = null;
			}
		}
	}

	private static boolean filmed(int kind) {
		return kind == RigMoves.SUITUP_WRAP || kind == RigMoves.SUITDOWN;
	}

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null with no wrap being filmed. */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		LocalPlayer self = minecraft.player;
		if (start < 0L || self == null || minecraft.level == null || minecraft.getCameraEntity() != self) {
			return null;
		}
		float t = minecraft.level.getGameTime() - start + partialTicks;
		float end = down ? RigMoves.SUITDOWN_TICKS : RigMoves.SUITUP_WRAP_TICKS;
		// how far round in front (0 your own view, 1 the film's), how close on the face, how wide the last shot
		float round = RigBeats.at(t, 0.0F, 0.0F, RigBeats.LINEAR, IN, 1.0F, RigBeats.OUT, end - 8.0F, 1.0F, RigBeats.LINEAR, end, 0.0F, RigBeats.SINE);
		float face, wide, drift;
		if (down) {
			// on the face from the start (the helm comes off 10 ticks in), the chest for the rest, wide once it's all gone
			face = RigBeats.at(t, 0.0F, 1.0F, RigBeats.LINEAR, DOWN_FACE_UNTIL, 1.0F, RigBeats.LINEAR, DOWN_CHEST_AT, 0.0F, RigBeats.SINE);
			wide = RigBeats.at(t, DOWN_WIDE_FROM, 0.0F, RigBeats.LINEAR, DOWN_WIDE_AT, 1.0F, RigBeats.SINE);
			drift = Mth.lerp(Mth.clamp(t / DOWN_WIDE_FROM, 0.0F, 1.0F), DRIFT_TO, DRIFT_FROM);
		} else {
			face = RigBeats.at(t, FACE_FROM, 0.0F, RigBeats.LINEAR, FACE_AT, 1.0F, RigBeats.OUT, FACE_UNTIL, 1.0F, RigBeats.LINEAR, WIDE_AT, 0.0F, RigBeats.SINE);
			wide = RigBeats.at(t, FACE_UNTIL, 0.0F, RigBeats.LINEAR, WIDE_AT, 1.0F, RigBeats.SINE);
			drift = Mth.lerp(Mth.clamp(t / FACE_FROM, 0.0F, 1.0F), DRIFT_FROM, DRIFT_TO);
		}
		double distance = Mth.lerp(wide, Mth.lerp(face, CHEST, FACE), WIDE);
		double down = Mth.lerp(face, CHEST_DOWN, FACE_DOWN) * (1.0F - wide);
		Vec3 eyes = self.getEyePosition(partialTicks);
		Vec3 pivot = eyes.add(0.0, -down, 0.0);
		float body = Mth.rotLerp(partialTicks, self.yBodyRotO, self.yBodyRot);
		// the film's shot: out in front (the body's facing, drifted round), looking back at the pivot
		float shotYaw = body + drift;
		Vec3 film = clear(self, pivot, pivot.add(Vec3.directionFromRotation(-PITCH, shotYaw).scale(distance)));
		float filmYaw = shotYaw + 180.0F, filmPitch = PITCH;
		// your own view: the eyes in first person, behind you in third
		float yaw = self.getViewYRot(partialTicks), pitch = self.getViewXRot(partialTicks);
		Vec3 own = viewBefore != null ? eyes : clear(self, eyes, eyes.subtract(Vec3.directionFromRotation(pitch, yaw).scale(THIRD_PERSON)));
		Vec3 pos = own.lerp(film, round);
		return new double[] {pos.x, pos.y, pos.z, Mth.rotLerp(round, yaw, filmYaw), Mth.lerp(round, pitch, filmPitch)};
	}

	/** {@code want}, or short of it by the margin where a wall stands between it and {@code pivot}. */
	private static Vec3 clear(LocalPlayer self, Vec3 pivot, Vec3 want) {
		HitResult hit = self.level().clip(new ClipContext(pivot, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self));
		if (hit.getType() == HitResult.Type.MISS) {
			return want;
		}
		Vec3 back = hit.getLocation().subtract(pivot);
		double length = back.length();
		return length > WALL_MARGIN ? pivot.add(back.scale((length - WALL_MARGIN) / length)) : pivot;
	}
}
