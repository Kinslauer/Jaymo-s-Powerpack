package com.jaymospowers.client.power.chrono;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.chrono.ChronoFlicker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Blur steps on the runner's own side ({@link ChronoFlicker}): the dodge key (Left Alt) with left, right, back or jump held,
 * for a Chrono overclocked in the low gears, is a short burst of speed that way ({@link #TICKS} ticks at {@link #SPEED} blocks a
 * tick, easing off) instead of the dash. It is movement, not a teleport: the runner crosses every block of the path in a blur
 * (the streak's afterimages follow the speed), stopped by walls as any run is. {@code DodgeClient} offers the key press here
 * first; a step that isn't wanted (no direction held, a higher gear, no Overclock) is left to the dash as ever.
 */
public final class ChronoFlickerClient {
	/** How long a step lasts (ticks), the speed it starts at sideways and back, and how fast it rises (blocks a tick). */
	static final int TICKS = 5;
	static final double SPEED = 0.95, RISE = 0.78;
	static final int COLOR = 0xBFE8FF;

	private static int left, cooldown, direction;
	private static Vec3 way = Vec3.ZERO;

	private ChronoFlickerClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ChronoFlickerClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			left = 0;
			cooldown = 0;
		});
	}

	/** The dodge key was pressed: a blur step if the keys and the gear want one (true), else the dash goes ahead (false). */
	public static boolean step(LocalPlayer player) {
		if (cooldown > 0 || !ChronoFlicker.able(player) || !ClientPlayNetworking.canSend(ChronoFlicker.Step.TYPE)) {
			return false;
		}
		Input input = player.input.keyPresses;
		int go;
		if (input.jump()) {
			go = ChronoFlicker.UP;
		} else if (input.left() != input.right()) {
			go = input.left() ? ChronoFlicker.LEFT : ChronoFlicker.RIGHT;
		} else if (input.backward() && !input.forward()) {
			go = ChronoFlicker.BACK;
		} else {
			return false;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 along = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw)), across = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
		way = switch (go) {
			case ChronoFlicker.LEFT -> across;
			case ChronoFlicker.RIGHT -> across.reverse();
			case ChronoFlicker.BACK -> along.reverse();
			default -> new Vec3(0.0, 1.0, 0.0);
		};
		direction = go;
		left = TICKS;
		cooldown = ChronoFlicker.GAP;
		ClientPlayNetworking.send(new ChronoFlicker.Step(go));
		return true;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (cooldown > 0) {
			cooldown--;
		}
		if (left <= 0 || player == null) {
			left = 0;
			return;
		}
		if (!ChronoFlicker.able(player)) {
			left = 0;
			return;
		}
		float ease = 0.55F + 0.45F * left / TICKS;
		Vec3 now = player.getDeltaMovement();
		if (direction == ChronoFlicker.UP) {
			player.setDeltaMovement(now.x, RISE * ease, now.z);
		} else {
			player.setDeltaMovement(way.x * SPEED * ease, now.y, way.z * SPEED * ease);
		}
		player.level().addParticle(MagicFx.wisp(COLOR, 0.8F), player.getX(), player.getY() + 0.9, player.getZ(), 0.0, 0.0, 0.0);
		left--;
	}
}
