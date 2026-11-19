package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebSymbioteWings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The symbiote's wings, for the local player (the movement is client-authoritative, as the web wings' is): out and in the
 * air they glide like an elytra (pitch down to gain speed, up to trade it for height, level to float), and a Jump press
 * in the air is a strong flap that climbs ({@link WebSymbioteWings#FLAPS} of them, one coming back every
 * {@link WebSymbioteWings#RECHARGE_TICKS}, all of them on the ground; the server charges the satiety). Sneak or Back
 * flares the wings to slow the fall.
 */
final class WebSymbioteWingsClient {
	/** The glide's gravity (vanilla's 0.08) and speed cap, as the web wings' are. */
	static final double GRAVITY = 0.08, MAX_SPEED = 1.8;
	/** A flap: straight up this much (b/t), forward this much, and the climb it can't beat. */
	static final double FLAP_LIFT = 0.62, FLAP_FORWARD = 0.22, FLAP_MAX_RISE = 1.1;
	/** Flared (Sneak or Back): the horizontal speed kept each tick, and the sink the wings catch it to. */
	static final double FLARE_DRAG = 0.9, FLARE_SINK = 0.12;

	private static int used;
	private static int rechargeIn;
	private static boolean jumpHeld;

	private WebSymbioteWingsClient() {
	}

	/** The wings are spread on this player. */
	static boolean out(LocalPlayer player) {
		return WebSymbioteWings.out(player) && player.isAlive() && !player.isSpectator() && !player.getAbilities().flying;
	}

	/** Spread and in the air: the wings fly (the caller leaves everything else of the web movement alone). */
	static boolean flying(LocalPlayer player) {
		return out(player) && !player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable() && !player.isFallFlying();
	}

	static void reset() {
		used = 0;
		rechargeIn = 0;
		jumpHeld = false;
	}

	/** Every tick the wings are out and no web holds the player in the air. */
	static void tick(LocalPlayer player) {
		boolean jump = player.input.keyPresses.jump();
		boolean pressed = jump && !jumpHeld;
		jumpHeld = jump;
		if (player.onGround()) {
			used = 0;
			rechargeIn = 0;
			return;
		}
		if (used > 0 && ++rechargeIn >= WebSymbioteWings.RECHARGE_TICKS) {
			used--;
			rechargeIn = 0;
		}
		Vec3 m = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
		Vec3 look = player.getLookAngle();
		float lean = player.getXRot() * Mth.DEG_TO_RAD;
		double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		double moveHorizontal = m.horizontalDistance();
		double lift = Mth.square(Math.cos(lean));
		if (pressed && used < WebSymbioteWings.FLAPS) {
			used++;
			rechargeIn = 0;
			ClientPlayNetworking.send(new WebSymbioteWings.Flap());
			double forward = lookHorizontal > 0.0 ? FLAP_FORWARD / lookHorizontal : 0.0;
			m = new Vec3(m.x + look.x * forward, Math.min(FLAP_MAX_RISE, Math.max(m.y, 0.0) + FLAP_LIFT), m.z + look.z * forward);
			player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.4F, 0.6F, false);
			player.setDeltaMovement(m);
			return;
		}
		boolean flare = player.input.keyPresses.backward() || player.input.keyPresses.shift();
		if (flare) {
			// flared: it catches the air, bleeding the speed off and slowing the fall
			m = new Vec3(m.x * FLARE_DRAG, Math.max(m.y * 0.98 - GRAVITY * 0.4, -FLARE_SINK), m.z * FLARE_DRAG);
		} else {
			m = m.add(0.0, GRAVITY * (-1.0 + lift * 0.75), 0.0);
			if (m.y < 0.0 && lookHorizontal > 0.0) {
				// falling turns into forward speed
				double convert = m.y * -0.1 * lift;
				m = m.add(look.x * convert / lookHorizontal, convert, look.z * convert / lookHorizontal);
			}
			if (lean < 0.0F && lookHorizontal > 0.0) {
				// pulling up turns speed into height
				double convert = moveHorizontal * -Mth.sin(lean) * 0.04;
				m = m.add(-look.x * convert / lookHorizontal, convert * 3.2, -look.z * convert / lookHorizontal);
			}
			if (lookHorizontal > 0.0) {
				m = m.add((look.x / lookHorizontal * moveHorizontal - m.x) * 0.1, 0.0, (look.z / lookHorizontal * moveHorizontal - m.z) * 0.1);
			}
			m = m.multiply(0.99, 0.98, 0.99);
		}
		if (m.length() > MAX_SPEED) {
			m = m.normalize().scale(MAX_SPEED);
		}
		player.setDeltaMovement(m);
		if (player.tickCount % 4 == 0) {
			player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.08F, 1.2F, false);
		}
	}
}
