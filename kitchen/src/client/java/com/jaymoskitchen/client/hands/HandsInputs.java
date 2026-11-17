package com.jaymoskitchen.client.hands;

import com.jaymoskitchen.cooking.hands.CookTools;
import com.jaymoskitchen.cooking.hands.Geometry;
import com.jaymoskitchen.cooking.hands.HandsOn;
import com.jaymoskitchen.cooking.hands.Pick;
import com.jaymoskitchen.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** What the player's own hand does at a running station that a click can't carry: sent to the server each tick. */
final class HandsInputs {
	/** Ticks between repeats of "still holding use" (the server forgets a hold after {@link HandsOn#HOLD_STALE}). */
	private static final int KEEPALIVE = 5;

	private static boolean holding;
	private static int holdSent;
	private static boolean pointing;
	private static float lastPitch = Float.NaN;
	private static BlockPos last;

	private HandsInputs() {
	}

	private static void send(BlockPos pos, int what, float value) {
		ClientPlayNetworking.send(new Payloads.HandsInput(pos, what, value));
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		HandsClient.View view = HandsClient.current(minecraft);
		boolean running = view != null && view.sync().phase() == Payloads.HandsSync.PHASE_RUNNING;
		Pick.Kind kind = running ? Pick.Kind.byIndex(view.sync().kind()) : null;
		BlockPos pos = running ? view.sync().pos() : null;
		boolean wantsHold = false;
		boolean aimed = false;
		if (kind != null && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getLookAngle();
			int phase = view.sync().ints().length > 0 ? view.sync().ints()[0] : 0;
			switch (kind) {
				case STIR -> {
					if (CookTools.Tool.SPOON.holds(player.getMainHandItem())) {
						float angle = Geometry.pointedAngle(eye, look, potCenter(minecraft, pos));
						aimed = !Float.isNaN(angle);
						if (aimed || pointing) {
							send(pos, HandsOn.IN_ANGLE, angle);
						}
					}
				}
				case DOUGH -> wantsHold = phase < 2 && minecraft.options.keyUse.isDown();
				case JOINT -> {
					wantsHold = minecraft.options.keyUse.isDown();
					if (phase == 0) {
						float angle = Geometry.pointedAngle(eye, look, topCenter(minecraft, pos));
						aimed = !Float.isNaN(angle);
						if (wantsHold && (aimed || pointing)) {
							send(pos, HandsOn.IN_ANGLE, angle);
						}
					} else if (phase == 1) {
						send(pos, HandsOn.IN_ACROSS, Geometry.pointedAcross(eye, look, topCenter(minecraft, pos)));
					} else if (phase == 2) {
						float pitch = player.getXRot();
						if (wantsHold && !Float.isNaN(lastPitch) && pitch != lastPitch) {
							send(pos, HandsOn.IN_ROLL, Math.abs(pitch - lastPitch));
						}
						lastPitch = pitch;
					}
				}
				default -> {
				}
			}
		}
		if (phaseNotRoll(kind, view)) {
			lastPitch = Float.NaN;
		}
		pointing = aimed;
		// the held use key, with its keepalive; let go (or walk off) says so once
		if (wantsHold) {
			if (!holding || pos == null || !pos.equals(last) || ++holdSent >= KEEPALIVE) {
				send(pos, HandsOn.IN_HOLD, 1.0F);
				holdSent = 0;
			}
			holding = true;
			last = pos;
		} else if (holding) {
			if (last != null) {
				send(last, HandsOn.IN_HOLD, 0.0F);
			}
			holding = false;
			last = null;
		}
	}

	private static boolean phaseNotRoll(Pick.Kind kind, HandsClient.View view) {
		return kind != Pick.Kind.JOINT || view.sync().ints().length == 0 || view.sync().ints()[0] != 2;
	}

	private static Vec3 potCenter(Minecraft minecraft, BlockPos pos) {
		return Geometry.potCenter(minecraft.level, pos);
	}

	private static Vec3 topCenter(Minecraft minecraft, BlockPos pos) {
		return Geometry.topCenter(minecraft.level, pos);
	}
}
