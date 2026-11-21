package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Absorption;
import com.jaymoskingdoms.SorcererJump;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (pb-jump) A fire, lightning or wind Sorcerer's second jump ({@link SorcererJump}): a fresh press of Jump a few ticks off
 * the ground kicks once, until they stand on something, swim, climb or ride again. The fall is counted from the top of the
 * jump, as vanilla's is. With a power of Jaymo's Powers (its own second jump) or a cloud in a bottle (its jump goes first),
 * those answer the press and this waits, so no press makes two.
 */
final class SorcererJumpClient {
	/** Upward speed of the jump (blocks a tick; a plain jump is 0.42), and the push the way they steer. */
	static final double LIFT = 0.5, PUSH = 0.2;
	/** Ticks off the ground before a press counts (a late jump just off a ledge is a plain jump's). */
	static final int AIR_TICKS = 5;

	private static boolean spent, wasJump;
	private static int air;
	/** Jaymo's Powers' answers, looked up once: does the player have a power's jump, does the cloud bottle take this press. */
	private static @Nullable MethodHandle powerJump, cloudJump;
	private static boolean looked;

	private SorcererJumpClient() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SorcererJumpClient::tick);
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		try {
			powerJump = MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.client.jump.PowerJumpClient"), "has",
				MethodType.methodType(boolean.class, LocalPlayer.class));
			cloudJump = MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.client.baubles.ArtifactBaublesClient"), "cloudJump",
				MethodType.methodType(boolean.class, LocalPlayer.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			powerJump = null;
			cloudJump = null;
		}
	}

	private static boolean asked(@Nullable MethodHandle handle, LocalPlayer player) {
		if (handle == null) {
			return false;
		}
		try {
			return (boolean)handle.invoke(player);
		} catch (Throwable e) {
			return false;
		}
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			spent = false;
			wasJump = false;
			air = 0;
			return;
		}
		boolean jump = minecraft.options.keyJump.isDown() && minecraft.gui.screen() == null;
		boolean pressed = jump && !wasJump;
		wasJump = jump;
		if (player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying || player.isPassenger()
			|| player.onClimbable()) {
			spent = false;
			air = 0;
			return;
		}
		air++;
		if (!pressed || spent || air < AIR_TICKS || player.isSpectator() || player.isFallFlying() || !SorcererJump.has(player)
			|| !ClientPlayNetworking.canSend(SorcererJump.Jump.TYPE)) {
			return;
		}
		look();
		if (asked(powerJump, player) || asked(cloudJump, player)) {
			return;
		}
		spent = true;
		double yaw = Math.toRadians(player.getYRot());
		double dx = -Math.sin(yaw) * player.zza + Math.cos(yaw) * player.xxa;
		double dz = Math.cos(yaw) * player.zza + Math.sin(yaw) * player.xxa;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length > 1.0) {
			dx /= length;
			dz /= length;
		}
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x + dx * PUSH, LIFT, v.z + dz * PUSH);
		player.resetFallDistance();
		ClientPlayNetworking.send(SorcererJump.Jump.INSTANCE);
	}
}
