package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalRide;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blade riding (see {@link ArsenalRide}), the rider's side: Jump tapped twice steps on (and off), and while the blade
 * is out it moves its rider. W drives it the way they look, up and down too, building speed; S is the brake; A and D
 * slide it sideways; Jump lifts it; with nothing pressed it hangs where it is. It never climbs past
 * {@link ArsenalRide#MAX_ALTITUDE} over the ground and skims rather than lands. As it is about to go home it shudders.
 */
final class ArsenalRideClient {
	/** The most ticks between two presses of Jump that count as a double tap. */
	private static final int DOUBLE_TAP_TICKS = 7;
	/** Top speed, how fast it gets there, the brake, the sideways slide (blocks/tick). */
	private static final double MAX_SPEED = 0.85, ACCEL = 0.05, BRAKE = 0.8, STRAFE = 0.22;
	/** Climb with Jump, the sink over the top, and the skim over the ground. */
	private static final double CLIMB = 0.22, SINK = 0.15, SKIM = 0.5;
	/** About to go home: how hard it shudders (blocks/tick), and the view's shake. */
	private static final double SHUDDER = 0.12;
	private static final float SHAKE = 0.3F;

	private static double speed;
	private static boolean jumpWasDown;
	private static int sinceTap = DOUBLE_TAP_TICKS + 1;

	private ArsenalRideClient() {
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		boolean jump = minecraft.options.keyJump.isDown() && minecraft.gui.screen() == null;
		sinceTap = Math.min(sinceTap + 1, DOUBLE_TAP_TICKS + 1);
		if (jump && !jumpWasDown) {
			if (sinceTap <= DOUBLE_TAP_TICKS && !player.isSpectator() && !player.getAbilities().mayfly && ArsenalRide.unlocked(player)
				&& ClientPlayNetworking.canSend(ArsenalRide.Step.TYPE)) {
				ClientPlayNetworking.send(ArsenalRide.Step.INSTANCE);
				sinceTap = DOUBLE_TAP_TICKS + 1;
			} else {
				sinceTap = 0;
			}
		}
		jumpWasDown = jump;

		ArsenalRide.Ride ride = player.getAttached(ArsenalRide.RIDE);
		if (ride == null) {
			speed = 0.0;
			return;
		}
		Input input = player.input.keyPresses;
		Vec3 look = player.getLookAngle();
		Vec3 level = look.horizontal();
		level = level.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : level.normalize();
		Vec3 side = new Vec3(-level.z, 0.0, level.x);
		if (input.forward()) {
			speed = Math.min(MAX_SPEED, speed + ACCEL);
		} else if (input.backward()) {
			speed *= BRAKE;
		} else {
			speed *= 0.9;
		}
		double slide = (input.left() ? 1.0 : 0.0) - (input.right() ? 1.0 : 0.0);
		Vec3 motion = look.scale(speed).add(side.scale(-slide * STRAFE));
		double vy = motion.y + (input.jump() ? CLIMB : 0.0);
		// how high over the ground
		Vec3 feet = player.position();
		double reach = ArsenalRide.MAX_ALTITUDE + 3.0;
		HitResult ground = player.level().clip(new ClipContext(feet, feet.subtract(0.0, reach, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		double height = ground.getType() == HitResult.Type.MISS ? reach : feet.y - ground.getLocation().y;
		if (height > ArsenalRide.MAX_ALTITUDE) {
			vy = Math.min(vy, -SINK);
		} else if (height < SKIM && vy <= 0.0) {
			vy = 0.06;
		}
		double x = motion.x, z = motion.z;
		float shake = ride.shake(player.level().getGameTime());
		if (shake > 0.0F) {
			x += (player.getRandom().nextDouble() - 0.5) * SHUDDER * shake;
			vy += (player.getRandom().nextDouble() - 0.5) * SHUDDER * shake;
			z += (player.getRandom().nextDouble() - 0.5) * SHUDDER * shake;
			if (player.tickCount % 4 == 0) {
				com.jaymospowers.client.ScreenShake.add(SHAKE * shake);
			}
		}
		player.setDeltaMovement(x, vy, z);
		player.resetFallDistance();
	}
}
