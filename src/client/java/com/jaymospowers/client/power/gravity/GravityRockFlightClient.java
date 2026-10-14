package com.jaymospowers.client.power.gravity;

import com.jaymospowers.power.gravity.GravityPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Flying the rock slab (see GravityRockFlight), the rider's side: W drives it the way you look (building speed),
 * S is the brake, A and D slide it sideways; Jump lifts it, but never more than {@code MAX_ALTITUDE} off the ground,
 * and with nothing pressed it's always sinking a little, like debris. Close to the ground it skims rather than lands.
 * In the last {@code ROCK_STRUGGLE_BAND} under the top it struggles: the climb weakens, it shudders and sags, and the
 * view shakes with it.
 */
final class GravityRockFlightClient {
	/** Top speed, how fast it gets there, the brake, the sideways slide (blocks/tick). */
	private static final double MAX_SPEED = 0.95, ACCEL = 0.06, BRAKE = 0.82, STRAFE = 0.25;
	/** Climb with Jump, the steady sink, and the skim over the ground. */
	private static final double CLIMB = 0.18, SINK = 0.04, SKIM = 0.6;
	/** Straining at the top: how hard it shudders (blocks/tick) and sags, and the view's shake. */
	private static final double SHUDDER = 0.16, SAG = 0.035;
	private static final float STRAIN_SHAKE = 0.3F;

	private static double speed;

	private GravityRockFlightClient() {
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		if (player.getAttached(GravityPower.ROCK_FLIGHT) == null) {
			speed = 0.0;
			return;
		}
		Input input = player.input.keyPresses;
		Vec3 look = player.getLookAngle().horizontal();
		look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		Vec3 side = new Vec3(-look.z, 0.0, look.x);
		if (input.forward()) {
			speed = Math.min(MAX_SPEED, speed + ACCEL);
		} else if (input.backward()) {
			// the brake (no dust off you: only the slab's underside sheds it)
			speed *= BRAKE;
		} else {
			speed *= 0.98;
		}
		double slide = (input.left() ? 1.0 : 0.0) - (input.right() ? 1.0 : 0.0);
		Vec3 horizontal = look.scale(speed).add(side.scale(-slide * STRAFE));
		// how high over the ground
		Vec3 feet = player.position();
		HitResult ground = player.level().clip(new ClipContext(feet, feet.subtract(0.0, GravityPower.ROCK_MAX_ALTITUDE + 3.0, 0.0),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		double height = ground.getType() == HitResult.Type.MISS ? GravityPower.ROCK_MAX_ALTITUDE + 3.0 : feet.y - ground.getLocation().y;
		double top = GravityPower.ROCK_MAX_ALTITUDE, band = GravityPower.ROCK_STRUGGLE_BAND;
		double vy = -SINK;
		if (input.jump() && height < top) {
			vy = CLIMB * Mth.clamp((top - height) / band, 0.15, 1.0);
		} else if (height > top) {
			vy = -SINK * 4.0;
		} else if (height < SKIM) {
			vy = 0.06;
		}
		if (height > top - band) {
			// fighting to stay up there: it shudders and keeps sagging back
			double strain = Mth.clamp((height - (top - band)) / band, 0.0, 1.0);
			vy += (player.getRandom().nextDouble() - 0.5) * SHUDDER * strain - SAG * strain;
			horizontal = horizontal.scale(1.0 - 0.25 * strain);
			if (player.tickCount % 4 == 0) {
				com.jaymospowers.client.ScreenShake.add(STRAIN_SHAKE * (float)strain);
			}
		}
		player.setDeltaMovement(horizontal.x, vy, horizontal.z);
		player.resetFallDistance();
	}
}
