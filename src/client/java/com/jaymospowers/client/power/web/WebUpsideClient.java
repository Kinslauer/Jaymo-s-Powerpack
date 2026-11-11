package com.jaymospowers.client.power.web;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import com.jaymospowers.power.web.WebNet;

/**
 * The local player turning head down: holding still on a swing line anchored well above (a ceiling, branch or
 * overhang) for {@link #HOLD_TICKS}, with no movement key held, hangs you upside down; the server is told so everyone
 * sees it ({@code WebPower#UPSIDE}). Moving, landing, or letting the line go puts you upright again. Jump and Sneak
 * keep reeling you up and down on the line as they do for any hang.
 */
final class WebUpsideClient {
	/** Still this many ticks to turn over. */
	static final int HOLD_TICKS = 16;
	/** Slower than this (blocks/tick) counts as still. */
	static final double STILL = 0.25;
	/** The line's anchor must be at least this far above your body, and no further out sideways than this share of that. */
	static final double ABOVE = 1.8, SIDEWAYS = 0.8;
	private static final double ROPE_HEIGHT = 1.2;

	private int still;
	private boolean on;

	boolean on() {
		return on;
	}

	void tick(LocalPlayer player, WebSwing.Strand[] strands) {
		Input in = player.input.keyPresses;
		boolean above = false;
		Vec3 body = player.position().add(0.0, ROPE_HEIGHT, 0.0);
		for (WebSwing.Strand strand : strands) {
			if (!strand.attached || strand.travel > 0 || strand.entityId >= 0) {
				continue;
			}
			double up = strand.anchor.y - body.y;
			double side = Math.hypot(strand.anchor.x - body.x, strand.anchor.z - body.z);
			if (up >= ABOVE && side <= up * SIDEWAYS) {
				above = true;
			}
		}
		boolean keys = in.forward() || in.backward() || in.left() || in.right();
		boolean ok = above && !player.onGround() && !keys && !player.isInWater();
		if (on) {
			if (!ok) {
				set(false);
			}
			return;
		}
		if (ok && player.getDeltaMovement().length() < STILL) {
			if (++still >= HOLD_TICKS) {
				set(true);
			}
		} else {
			still = 0;
		}
	}

	void reset() {
		still = 0;
		if (on) {
			set(false);
		}
	}

	private void set(boolean value) {
		on = value;
		still = 0;
		ClientPlayNetworking.send(new WebNet.Upside(value));
	}
}
