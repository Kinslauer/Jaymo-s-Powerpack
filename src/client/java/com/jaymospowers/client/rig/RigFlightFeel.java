package com.jaymospowers.client.rig;

import com.jaymospowers.client.GiantCamera;
import com.jaymospowers.client.ScreenShake;
import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import com.jaymospowers.client.sound.PowerLoop;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigForgeBlock;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How the Rig's flight feels to the one flying it, on their own screen (the flight itself is {@link RigClient}):
 * <ul>
 * <li>the <b>view widens</b> with speed (scaled by the FOV-effects slider, like sprinting) and closes on the brakes;</li>
 * <li>the <b>thrusters sound</b>: a hum that rises with the throttle while hovering, a roar over it in full flight and
 * the wind rushing past at speed ({@link PowerLoop}, so none of it ticks like a clock);</li>
 * <li>seen from behind, the <b>camera</b> settles lower and further back as the suit lies flat, so it fills the frame;</li>
 * <li>at sprint speed <b>streaks</b> pass the visor and the view trembles (the server cracks the air for everyone as the
 * suit breaks into it, {@code RigActives});</li>
 * <li>and the Rig menu's <b>take-off</b> switch: lifting off the ground goes straight on into full flight.</li>
 * </ul>
 */
public final class RigFlightFeel {
	/** Extra field of view at full sprint flight (0.14 = +14%), before the FOV-effects slider; how fast it follows the speed. */
	static final float FOV_BOOST = 0.14F, FOV_EASE = 0.12F;
	/** The hum: volume and pitch hovering still, and at full throttle. */
	static final float HUM_VOLUME = 0.22F, HUM_VOLUME_FAST = 0.4F, HUM_PITCH = 0.7F, HUM_PITCH_FAST = 1.5F;
	/** The roar and the wind in full flight: volume at full speed, and the pitch range. */
	static final float ROAR_VOLUME = 0.55F, ROAR_PITCH = 0.55F, ROAR_PITCH_FAST = 1.05F, WIND_VOLUME = 0.6F, WIND_PITCH = 0.9F, WIND_PITCH_FAST = 1.35F;
	/** The share of top speed the wind starts at. */
	static final float WIND_FROM = 0.35F;
	/** Third person: how far the camera drops and falls back at cruise (blocks), and how fast it settles. */
	static final double CAMERA_DROP = 0.7, CAMERA_BACK = 1.6;
	static final float CAMERA_EASE = 0.08F;
	/** Sprint flight: the share of top speed the streaks and the tremble start at, the tremble (degrees), streaks a tick. */
	static final float SONIC_FROM = 0.88F, SONIC_TREMBLE = 0.35F;
	static final int STREAKS = 3;
	/** Where the streaks start: this far ahead, within this far of the line of flight. */
	static final double STREAK_AHEAD = 7.0, STREAK_SPREAD = 2.6;
	/** The Rig menu's take-off switch, kept in the client's settings. */
	static final String KEY_JET_START = "rig.jet_start";

	private static float fov, camera, lastCamera, speedShare;
	private static boolean thrusting, flying;

	private RigFlightFeel() {
	}

	/** Whether a take-off from the ground goes straight on into full flight. */
	static boolean jetStart() {
		return "true".equals(ZFighterClientConfig.get(KEY_JET_START, "false"));
	}

	static void toggleJetStart() {
		ZFighterClientConfig.set(KEY_JET_START, Boolean.toString(!jetStart()));
	}

	/** Every client tick, after the flight has moved the player. */
	static void tick(Minecraft minecraft, LocalPlayer player) {
		thrusting = RigClient.status.hovering() && Rig.fullSet(player) && !player.onGround();
		flying = thrusting && RigClient.status.flying();
		double top = RigClient.FLIGHT_SPEED * RigClient.FLIGHT_SPRINT * Rig.frame(player).airSpeed;
		speedShare = thrusting ? (float)Mth.clamp(player.getDeltaMovement().length() / top, 0.0, 1.0) : 0.0F;
		fov += ((flying ? speedShare : 0.0F) - fov) * FOV_EASE;
		lastCamera = camera;
		camera += ((flying && RigPose.cruising(player) ? 1.0F : 0.0F) - camera) * CAMERA_EASE;
		if (!thrusting) {
			return;
		}
		PowerLoop.keep("rig.hum", SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, player, () -> thrusting, new PowerLoop.Mix() {
			@Override
			public float volume() {
				return Mth.lerp(speedShare, HUM_VOLUME, HUM_VOLUME_FAST);
			}

			@Override
			public float pitch() {
				return Mth.lerp(speedShare, HUM_PITCH, HUM_PITCH_FAST);
			}
		});
		PowerLoop.keep("rig.roar", SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, player, () -> flying, new PowerLoop.Mix() {
			@Override
			public float volume() {
				return ROAR_VOLUME * (0.35F + 0.65F * speedShare);
			}

			@Override
			public float pitch() {
				return Mth.lerp(speedShare, ROAR_PITCH, ROAR_PITCH_FAST);
			}
		});
		PowerLoop.keep("rig.wind", SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, player, () -> flying, new PowerLoop.Mix() {
			@Override
			public float volume() {
				return WIND_VOLUME * Mth.clamp((speedShare - WIND_FROM) / (1.0F - WIND_FROM), 0.0F, 1.0F);
			}

			@Override
			public float pitch() {
				return Mth.lerp(speedShare, WIND_PITCH, WIND_PITCH_FAST);
			}
		});
		if (flying && speedShare >= SONIC_FROM && !minecraft.isPaused()) {
			ScreenShake.add(SONIC_TREMBLE, 3, null);
			streaks(player);
		}
	}

	/** Thin streaks of light coming at the visor along the line of flight. */
	private static void streaks(LocalPlayer player) {
		Vec3 motion = player.getDeltaMovement();
		Vec3 along = motion.normalize();
		Vec3 eye = player.getEyePosition();
		for (int i = 0; i < STREAKS; i++) {
			Vec3 at = eye.add(along.scale(STREAK_AHEAD)).add((player.getRandom().nextDouble() - 0.5) * 2.0 * STREAK_SPREAD,
				(player.getRandom().nextDouble() - 0.5) * 2.0 * STREAK_SPREAD, (player.getRandom().nextDouble() - 0.5) * 2.0 * STREAK_SPREAD);
			player.level().addParticle(MagicFx.spark(RigForgeBlock.FX_COLOR, 0.5F), at.x, at.y, at.z, -motion.x, -motion.y, -motion.z);
		}
	}

	/** The field of view's multiplier (1: none), before the FOV-effects slider. */
	public static float fovMultiplier() {
		return 1.0F + fov * FOV_BOOST;
	}

	/** Seen from behind: where the camera goes from {@code position} this frame, or null to leave it. */
	public static @Nullable Vec3 camera(Minecraft minecraft, Vec3 position, float partialTicks) {
		LocalPlayer player = minecraft.player;
		float amount = Mth.lerp(partialTicks, lastCamera, camera);
		if (player == null || amount < 0.01F || minecraft.getCameraEntity() != player || minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK
			|| GiantCamera.active(minecraft)) {
			return null;
		}
		Vec3 want = position.subtract(player.getViewVector(partialTicks).scale(CAMERA_BACK * amount)).add(0.0, -CAMERA_DROP * amount, 0.0);
		return GiantCamera.clipped(player, player.getEyePosition(partialTicks), want);
	}
}
