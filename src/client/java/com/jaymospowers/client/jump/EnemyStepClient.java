package com.jaymospowers.client.jump;

import com.jaymospowers.mortal.AirCombo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The air combo's enemy step ({@link AirCombo}), the jump being the client's as all movement is: a fresh press of Jump in
 * mid-air, by anyone with a second jump of any kind, within {@link AirCombo#STEP_REACH} of a foe in a combo that can be
 * stepped off ({@link AirCombo#STEPPABLE}), kicks off it ({@link AirCombo#STEP_LIFT} up, a little the way you steer)
 * and tells the server, which starts that combo again. That press is the step's: the second jumps (a power's, a cloud in
 * a bottle's, the gravitite set's, a Sorcerer's) leave it alone ({@link #takes}) and are still there for the next one.
 */
public final class EnemyStepClient {
	/** Ticks off the ground before a press counts (a jump just off the ground is a plain jump's). */
	static final int AIR_TICKS = 3;

	private static boolean wasJump, decided;
	private static int air, decidedTick = Integer.MIN_VALUE;

	private EnemyStepClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.player == null) {
				wasJump = false;
				air = 0;
				decidedTick = Integer.MIN_VALUE;
			} else {
				takes(minecraft.player);
			}
		});
	}

	/**
	 * Whether this tick's press of Jump is an enemy step: worked out (and made) the first time anything asks in a tick,
	 * the same answer after, so it doesn't matter whose tick runs first.
	 */
	public static boolean takes(LocalPlayer player) {
		if (decidedTick == player.tickCount) {
			return decided;
		}
		decidedTick = player.tickCount;
		decided = false;
		Minecraft minecraft = Minecraft.getInstance();
		boolean jump = minecraft.options.keyJump.isDown() && minecraft.gui.screen() == null;
		boolean pressed = jump && !wasJump;
		wasJump = jump;
		if (player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying || player.isPassenger()
			|| player.onClimbable()) {
			air = 0;
			return false;
		}
		air++;
		if (!pressed || air < AIR_TICKS || PowerJumpClient.busy(player) || !ClientPlayNetworking.canSend(AirCombo.Step.TYPE)
			|| !AirCombo.hasAirJump(player)) {
			return false;
		}
		LivingEntity foe = foe(player);
		if (foe == null) {
			return false;
		}
		double yaw = Math.toRadians(player.getYRot());
		double dx = -Math.sin(yaw) * player.zza + Math.cos(yaw) * player.xxa;
		double dz = Math.cos(yaw) * player.zza + Math.sin(yaw) * player.xxa;
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length > 1.0) {
			dx /= length;
			dz /= length;
		}
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x + dx * AirCombo.STEP_PUSH, AirCombo.STEP_LIFT, v.z + dz * AirCombo.STEP_PUSH);
		player.resetFallDistance();
		ClientPlayNetworking.send(new AirCombo.Step(foe.getId()));
		decided = true;
		return true;
	}

	/** The nearest foe within reach that can be stepped off, or null. */
	private static @Nullable LivingEntity foe(LocalPlayer player) {
		LivingEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(AirCombo.STEP_REACH),
			e -> e != player && e.isAlive() && Boolean.TRUE.equals(e.getAttached(AirCombo.STEPPABLE)))) {
			double d = e.distanceToSqr(player);
			if (d < best) {
				best = d;
				nearest = e;
			}
		}
		return nearest;
	}
}
