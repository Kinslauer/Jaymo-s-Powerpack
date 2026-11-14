package com.jaymospowers.client.freerun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Climbing a wall in first person (Andy: "the climbing and wall run animation need work"): your own climb showed
 * nothing but the held item. Now both bare hands are on the wall in front of you, going hand over hand on the
 * third-person climb's beat ({@code WebParkourPose}'s cycle, keyed to how far you've climbed): a hand grips at the top
 * of the view and sinks down through it as you rise past it, drops out of sight, then comes back up from below to its
 * next hold at the top, the two hands half a cycle apart. Hanging still, they stay where they are; slipping down once
 * the grip is gone, both hands are up at the top clawing at the wall. Drawn from {@code LedgeFirstPersonMixin}, placed
 * straight in the view as the ledge's hands are ({@code LedgePose#firstPersonArm}). Render thread only.
 */
public final class ClimbFirstPerson {
	// ---- tuning: view space (blocks, x toward the arm's side) and degrees ---------------------------------------------
	/** Blocks climbed per full cycle (as WebParkourPose.CLIMB_CYCLE) and the share of it a hand stays gripped. */
	static final float CYCLE = 1.2F, HOLD = 0.55F;
	/** The shoulder at a hand's top hold {x, y, z} and how far it sinks (blocks) by the time the hand lets go (out of view). */
	static final float[] TOP = {0.05F, -0.55F, -0.45F};
	static final float SINK = 0.95F;
	/** Coming up to the next hold: how far nearer you the hand comes off the wall (blocks). */
	static final float OFF_WALL = 0.12F;
	/** How far the arm tips away from you and out to its side (degrees), gripping and reaching. */
	static final float TIP_GRIP = 14.0F, TIP_REACH = 4.0F, OUT = 10.0F;
	/** Slipping: falling faster than this (blocks/tick) is the slide; the hands' claw at the top (blocks, radians a tick). */
	static final float SLIP_FALL = 0.03F, CLAW = 0.04F, CLAW_RATE = 0.9F;
	// ----------------------------------------------------------------------------------------------------------------------

	private static double lastX, lastY, lastZ, travel;
	private static boolean tracking;

	private ClimbFirstPerson() {
	}

	/** Each frame on the wall, before the hands: how far the body has climbed (up, down and across). */
	public static void track(LocalPlayer player, float partialTicks) {
		double x = Mth.lerp(partialTicks, player.xo, player.getX()), y = Mth.lerp(partialTicks, player.yo, player.getY());
		double z = Mth.lerp(partialTicks, player.zo, player.getZ());
		if (tracking) {
			double step = Math.sqrt(Mth.square(x - lastX) + Mth.square(y - lastY) + Mth.square(z - lastZ));
			if (step < 0.5 && y - lastY > -SLIP_FALL) {
				travel = (travel + step) % 4096.0;
			}
		}
		lastX = x;
		lastY = y;
		lastZ = z;
		tracking = true;
	}

	/** Off the wall: the next climb starts afresh. */
	public static void stop() {
		tracking = false;
	}

	/** Place one first-person arm for the climb (the caller then draws the bare arm). */
	public static void firstPersonArm(PoseStack poseStack, LocalPlayer player, float time, boolean right, float equip) {
		float side = right ? 1.0F : -1.0F;
		float x = TOP[0], y, z = TOP[2], tip;
		if (player.getDeltaMovement().y < -SLIP_FALL) {
			// the grip gone: both hands up at the top, clawing
			y = TOP[1] - Math.abs(Mth.sin(time * CLAW_RATE + (right ? 0.0F : Mth.PI))) * CLAW * 3.0F;
			tip = TIP_GRIP;
		} else {
			float v = (float) (travel / CYCLE) + (right ? 0.0F : 0.5F);
			v -= Mth.floor(v);
			if (v < HOLD) {
				// gripped: the hand keeps its hold while you rise past it, so it sinks through the view
				y = TOP[1] - SINK * v / HOLD;
				tip = TIP_GRIP;
			} else {
				// reaching: up from below to the next hold, off the wall a little on the way
				float t = (v - HOLD) / (1.0F - HOLD), s = t * t * (3.0F - 2.0F * t);
				y = TOP[1] - SINK + SINK * s;
				z += OFF_WALL * Mth.sin(Mth.PI * t);
				tip = Mth.lerp(Mth.sin(Mth.PI * t), TIP_GRIP, TIP_REACH);
			}
		}
		poseStack.translate(side * x, y, z);
		poseStack.rotateDegrees(Axis.XP, -tip);
		// (model space faces the other way: the arm's own side is -x there)
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.rotateDegrees(Axis.ZP, -side * OUT);
		com.jaymospowers.client.LedgePose.undoVanillaArm(poseStack, side, equip);
	}
}
