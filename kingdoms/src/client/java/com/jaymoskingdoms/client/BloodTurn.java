package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BloodTemptation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.332) Turn your head: when the blood says so ({@link BloodTemptation.Turn}), a vampire's view is drawn slowly round to
 * a mortal player in sight over about a second, eased in so it starts gently and finishes firmly. It lets go the moment
 * something else has the wheel ({@link SeizureClient}) or a screen opens.
 */
public final class BloodTurn {
	/** The share of the way it closes each tick at the start, and at the end of the turn. */
	static final float START = 0.04F, END = 0.35F;

	private static int target = -1, ticks, total;

	private BloodTurn() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BloodTemptation.Turn.TYPE, (payload, context) -> {
			target = payload.target();
			ticks = total = Math.max(1, payload.ticks());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(BloodTurn::tick);
	}

	private static void clear() {
		target = -1;
		ticks = total = 0;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (ticks <= 0) {
			return;
		}
		if (player == null || minecraft.level == null || SeizureClient.locked() || minecraft.gui.screen() != null || !player.isAlive()) {
			clear();
			return;
		}
		Entity prey = minecraft.level.getEntity(target);
		if (prey == null || prey == player) {
			clear();
			return;
		}
		float progress = 1.0F - (float) ticks / total;
		float share = Mth.lerp(progress, START, END);
		ticks--;
		Vec3 to = prey.getEyePosition().subtract(player.getEyePosition());
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float)(-Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
		player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * share);
		player.setXRot(Mth.clamp(player.getXRot() + (pitch - player.getXRot()) * share, -90.0F, 90.0F));
	}
}
