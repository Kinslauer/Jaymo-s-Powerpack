package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoGears;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Momentum for the local speedster (Overclock and Velocity), the client's to decide like the wall run:
 * <ul>
 * <li><b>Building:</b> a run starts at {@link #BUILD_FLOOR} of its speed and comes up to all of it over
 * {@link #BUILD_TICKS} ticks of running straight; the speed holds while you keep to a line.</li>
 * <li><b>Drift turns:</b> turning your view sharply ({@link #TURN_DEGREES} in a tick) sheds speed, more in a higher gear
 * ({@link ChronoGears#TURN_SHED}), and leans the body into the turn (drawn by {@code ChronoRunPose}) with a puff of the
 * ground's dust.</li>
 * <li><b>Skids:</b> let go of every key from a fast run and you slide on ({@link #SKID_DECAY} of the speed kept a tick)
 * to a stop in a spray of the ground's dust. Any key, a jump or leaving the ground ends the slide.</li>
 * </ul>
 */
public final class ChronoMomentumClient {
	// ---- tuning ----
	/** Share of full speed at the first stride, and ticks of straight running to reach all of it. */
	static final float BUILD_FLOOR = 0.6F;
	static final int BUILD_TICKS = 30;
	/** A view turn this sharp in one tick (degrees) sheds speed; at {@link #TURN_FULL} it sheds the gear's whole share. */
	static final float TURN_DEGREES = 14.0F, TURN_FULL = 70.0F;
	/** How far the body leans into a turn at most (degrees), and how fast the lean comes and goes (share a tick). */
	public static final float LEAN_DEGREES = 22.0F;
	static final float LEAN_RISE = 0.5F, LEAN_FALL = 0.12F;
	/** Skid: the speed (blocks a tick) a run must have had to slide, the share kept a tick, and the speed it stops at. */
	static final double SKID_MIN = 0.3, SKID_DECAY = 0.88, SKID_STOP = 0.05;
	/** Dust a tick while skidding, per unit of speed. */
	static final int SKID_DUST = 6;

	private static float momentum;
	private static float lean;
	private static float leanShown;
	private static boolean skidding;
	private static Vec3 skidDir = Vec3.ZERO;
	private static double skidSpeed;
	private static double lastSpeed;
	private static boolean wasRunning;
	private static int entityId = -1;

	private ChronoMomentumClient() {
	}

	public static void reset() {
		momentum = 0.0F;
		lean = 0.0F;
		leanShown = 0.0F;
		skidding = false;
		wasRunning = false;
		lastSpeed = 0.0;
	}

	/** The lean (degrees, positive to the right) of the local player's body, or 0 for anyone else. */
	public static float lean(int id) {
		return id == entityId ? leanShown : 0.0F;
	}

	/** Each client tick: {@code on} is whether the local player is a speedster who is free to run (not on a wall). */
	public static void tick(LocalPlayer player, boolean on) {
		entityId = player.getId();
		boolean free = on && !player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying() && !player.isSwimming()
			&& !player.isSpectator() && !player.onClimbable() && player.isAlive() && !player.isInWater() && !player.input.keyPresses.shift();
		if (!free) {
			reset();
			return;
		}
		var keys = player.input.keyPresses;
		double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
		double speed = Math.sqrt(dx * dx + dz * dz);
		boolean anyKey = keys.forward() || keys.backward() || keys.left() || keys.right() || keys.jump();
		if (skidding) {
			skid(player, anyKey);
			leanShown = Mth.lerp(LEAN_FALL, leanShown, 0.0F);
			return;
		}
		boolean running = keys.forward() && player.isSprinting() && player.onGround();
		if (running) {
			momentum = Math.min(1.0F, momentum + 1.0F / BUILD_TICKS);
			float turn = Mth.wrapDegrees(player.getYRot() - player.yRotO);
			if (Math.abs(turn) > TURN_DEGREES) {
				float shed = ChronoGears.TURN_SHED[ChronoGears.gear(player) - 1] * Math.min(1.0F, Math.abs(turn) / TURN_FULL);
				momentum *= 1.0F - shed;
				scale(player, 1.0F - shed);
				lean = Mth.clamp(turn / TURN_FULL, -1.0F, 1.0F);
				dust(player, Math.max(0.1, speed) * 0.5, 2, null);
			} else {
				lean *= 1.0F - LEAN_FALL;
			}
			float build = BUILD_FLOOR + (1.0F - BUILD_FLOOR) * momentum;
			if (build < 1.0F) {
				scale(player, build);
			}
		} else if (wasRunning && !anyKey && lastSpeed > SKID_MIN && player.onGround()) {
			// every key let go from a fast run: slide on
			skidding = true;
			skidDir = new Vec3(dx, 0.0, dz).normalize();
			skidSpeed = Math.max(speed, lastSpeed * 0.9);
			momentum = 0.0F;
		} else {
			momentum = Math.max(0.0F, momentum - 0.05F);
			lean *= 1.0F - LEAN_FALL;
		}
		leanShown = Mth.lerp(Math.abs(lean * LEAN_DEGREES) > Math.abs(leanShown) ? LEAN_RISE : LEAN_FALL, leanShown, lean * LEAN_DEGREES);
		wasRunning = running;
		lastSpeed = speed;
	}

	private static void scale(LocalPlayer player, float by) {
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * by, v.y, v.z * by);
	}

	private static void skid(LocalPlayer player, boolean anyKey) {
		if (anyKey || !player.onGround() || skidSpeed < SKID_STOP || player.horizontalCollision) {
			skidding = false;
			wasRunning = false;
			lastSpeed = 0.0;
			return;
		}
		skidSpeed *= SKID_DECAY;
		player.setDeltaMovement(skidDir.x * skidSpeed, player.getDeltaMovement().y, skidDir.z * skidSpeed);
		dust(player, skidSpeed, Math.max(1, (int)(skidSpeed * SKID_DUST)), skidDir);
	}

	/** A spray of the ground's own dust off the heels, thrown back and out. */
	private static void dust(LocalPlayer player, double speed, int count, Vec3 along) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
		BlockState ground = minecraft.level.getBlockState(below);
		if (ground.isAir()) {
			return;
		}
		Vec3 back = along != null ? along.scale(-1.0) : player.getDeltaMovement().multiply(1.0, 0.0, 1.0).normalize().scale(-1.0);
		var random = player.getRandom();
		for (int i = 0; i < count; i++) {
			double side = (random.nextDouble() - 0.5) * 0.6;
			minecraft.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
				back.x * speed * 0.6 + -back.z * side, 0.1 + random.nextDouble() * 0.15, back.z * speed * 0.6 + back.x * side);
		}
	}
}
