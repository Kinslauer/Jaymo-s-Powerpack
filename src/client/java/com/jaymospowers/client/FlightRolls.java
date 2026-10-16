package com.jaymospowers.client;

import com.jaymospowers.client.power.smoke.SmokeFlightClient;
import com.jaymospowers.client.power.wind.WindPowerClient;
import com.jaymospowers.client.power.zfighter.ZFighterFlightClient;
import com.jaymospowers.core.FlightRoll;
import com.jaymospowers.power.xenoform.XenoformState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The barrel roll ({@link FlightRoll}), on the client: flying with Wind, a Xenoform alien, a Z Fighter's ki, Smoke's
 * Vapour Form or the Rig's thrusters, double-tap Left or Right (A / D) and you corkscrew a full turn that way and slide a few blocks aside. The
 * slide is added on top of whatever the flight set this tick, so the flight keeps its own momentum. Everyone sees the
 * body turn over (rolled about its own length: upright while hovering, laid out along the flight at speed), and your
 * own first-person view turns with it.
 */
public final class FlightRolls {
	/** The most ticks between two Left (or two Right) presses that count as a double tap. */
	private static final int TAP_TICKS = 7;
	/** How much of the full turn the first-person view follows (1: all the way round). */
	private static final float VIEW_ROLL = 1.0F;

	/** The roll's signed angle (degrees, positive to the right) on a player's render state; absent when not rolling. */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "jaymospowers:flight_roll");

	private static boolean leftWasDown;
	private static boolean rightWasDown;
	private static int sinceTap = Integer.MAX_VALUE;
	private static boolean lastTapRight;
	/** Client game time the local roll started (Long.MIN_VALUE: none), which way, and the last tick it was ready again. */
	private static long rollStart = Long.MIN_VALUE;
	private static boolean rollRight;

	private FlightRolls() {
	}

	/** Every client tick, right after the power's own tick (so the slide goes on top of the flight's velocity). */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		long now = player.level().getGameTime();
		boolean left = minecraft.options.keyLeft.isDown();
		boolean right = minecraft.options.keyRight.isDown();
		if (sinceTap < Integer.MAX_VALUE) {
			sinceTap++;
		}
		boolean flying = flying(player);
		int into = rollStart == Long.MIN_VALUE ? Integer.MAX_VALUE : (int)(now - rollStart);
		if (into < FlightRoll.ROLL_TICKS) {
			if (flying) {
				slide(player, into);
			}
		} else if (flying && minecraft.gui.screen() == null && into >= FlightRoll.ROLL_TICKS + FlightRoll.COOLDOWN) {
			boolean pressedLeft = left && !leftWasDown;
			boolean pressedRight = right && !rightWasDown;
			if (pressedLeft != pressedRight) {
				if (sinceTap <= TAP_TICKS && lastTapRight == pressedRight) {
					start(player, pressedRight, now);
					sinceTap = Integer.MAX_VALUE;
				} else {
					sinceTap = 0;
					lastTapRight = pressedRight;
				}
			}
		} else if (!flying) {
			sinceTap = Integer.MAX_VALUE;
		}
		leftWasDown = left;
		rightWasDown = right;
	}

	/** Whether the local player is flying in one of the four flights that roll. */
	private static boolean flying(LocalPlayer player) {
		if (player.onGround() || player.isPassenger() || player.isSpectator()) {
			return false;
		}
		if (WindPowerClient.isActive(player)) {
			return WindPowerClient.INSTANCE.flight().state().flying();
		}
		return ZFighterFlightClient.pose() > 0 || SmokeFlightClient.flying() || XenoformState.flight(player) != XenoformState.FLY_NONE
			// (0.11.87) the Rig's thrusters, hovering or in full flight
			|| com.jaymospowers.client.rig.RigClient.flying(player);
	}

	private static void start(LocalPlayer player, boolean right, long now) {
		rollStart = now;
		rollRight = right;
		ClientPlayNetworking.send(new FlightRoll.Roll(right));
		player.level().playLocalSound(player, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.5F);
		slide(player, 0);
	}

	/** This tick's share of the slide: fastest through the middle of the turn. */
	private static void slide(LocalPlayer player, int into) {
		double speed = FlightRoll.SIDE_SPEED * Math.sin(Math.PI * (into + 0.5) / FlightRoll.ROLL_TICKS) * (rollRight ? 1.0 : -1.0);
		double yaw = Math.toRadians(player.getYRot());
		player.setDeltaMovement(player.getDeltaMovement().add(-Math.cos(yaw) * speed, 0.0, -Math.sin(yaw) * speed));
	}

	/** How far round the roll is (degrees, positive to the right) for this player, or null when they aren't rolling. */
	private static @Nullable Float angle(Entity entity, float partialTicks) {
		if (!(entity instanceof Player player)) {
			return null;
		}
		long now = player.level().getGameTime();
		Float progress;
		if (player == Minecraft.getInstance().player && rollStart != Long.MIN_VALUE) {
			// our own roll draws at once, before the server's copy comes back
			float t = (now - rollStart + partialTicks) / FlightRoll.ROLL_TICKS;
			progress = t < 0.0F || t >= 1.0F ? null : rollRight ? t : -t;
		} else {
			progress = FlightRoll.progress(player, now, partialTicks);
		}
		if (progress == null) {
			return null;
		}
		float t = Math.abs(progress);
		float eased = t * t * (3.0F - 2.0F * t);
		return Math.signum(progress) * eased * 360.0F;
	}

	/** From the avatar renderer's extractRenderState. */
	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		state.setData(KEY, angle(entity, partialTicks));
	}

	/** The roll's angle on this render state (0 when not rolling). */
	public static float angle(LivingEntityRenderState state) {
		Float angle = state.getData(KEY);
		return angle == null ? 0.0F : angle;
	}

	/**
	 * An upright (hovering) body rolls about the way it faces, round its middle; {@code weight} is how upright it is
	 * (1 - the cruise pose's amount; the laid-out body's roll is about its length, in WindPose's tilt).
	 */
	public static void rollUpright(LivingEntityRenderState state, PoseStack poseStack, float entityScale, float weight) {
		float angle = angle(state);
		if (angle == 0.0F || weight <= 0.0F) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, middle, 0.0F);
		// right (+X in the body's frame) is where the top goes for a roll to the right
		poseStack.rotateDegrees(Axis.ZP, -angle * weight);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** The first-person view turns over with the roll. */
	public static void applyCamera(PoseStack poseStack, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		Float angle = angle(minecraft.player, partialTicks);
		if (angle != null) {
			poseStack.rotateDegrees(Axis.ZP, Mth.wrapDegrees(angle * VIEW_ROLL));
		}
	}
}
