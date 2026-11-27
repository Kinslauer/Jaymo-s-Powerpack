package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Springing off the spot a Zip carries you to. Andy's pick from Legends: Superheroes, "hold Jump while a Zip carries you
 * in, or press it as you land, and you kick off the spot at once with the zip's speed; look up or down at a wall to
 * shoot along it instead". A zip used to stop at a wall (or mantle a ledge, {@link WebLedge}); now Jump chains it
 * straight into the next move without a pause. Holding Jump through the zip carries it all the way in (no early let go)
 * and kicks off the instant it arrives; a press of Jump up to {@link #WINDOW} ticks after arriving does the same. On a
 * wall the kick is away from it, along where you look, or, looking steeply up or down ({@link #ALONG_PITCH}), up or down
 * the wall's own face; on a floor, a ceiling or a creature it goes where you look.
 */
final class WebSpring {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** A press of Jump this many ticks after the zip arrives still springs. */
	static final int WINDOW = 8;
	/** The kick's speed (blocks/tick): the zip's own speed, at least this and at most that. */
	static final double MIN_SPEED = 0.9, MAX_SPEED = 2.2;
	/** Looking this many degrees up or down at a wall shoots along it. */
	static final double ALONG_PITCH = 30.0;
	/** Off a wall: how much of the kick goes straight out from it, and how much of it lifts. */
	static final double OUT_SHARE = 0.7, LIFT = 0.35;
	/** Along a wall: how much of the kick leans out from the face, so it doesn't scrape. */
	static final double ALONG_OUT = 0.25;
	// ================================================================================================================

	private int window;
	private double speed;
	private WebNet.@Nullable Pull pull;
	private boolean jumpWas;

	void reset() {
		window = 0;
		pull = null;
	}

	/**
	 * A zip has just arrived. With Jump held it springs now: the velocity to kick off with. Otherwise a press of Jump in
	 * the next {@link #WINDOW} ticks will, and this returns null.
	 */
	@Nullable Vec3 arrive(LocalPlayer player, WebNet.Pull arrived, double zipSpeed, boolean jumpHeld) {
		this.speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, zipSpeed));
		this.pull = arrived;
		jumpWas = jumpHeld;
		if (jumpHeld) {
			window = 0;
			return launch(player, arrived, speed);
		}
		window = WINDOW;
		return null;
	}

	/** Every tick off a zip: a fresh press of Jump inside the window springs (the velocity to kick off with), else null. */
	@Nullable Vec3 tick(LocalPlayer player) {
		Input input = player.input.keyPresses;
		boolean pressed = input.jump() && !jumpWas;
		jumpWas = input.jump();
		if (window <= 0 || pull == null) {
			return null;
		}
		window--;
		if (!pressed) {
			return null;
		}
		window = 0;
		return launch(player, pull, speed);
	}

	/**
	 * The kick off the end of {@code arrived}: away from a wall along the look (up, over it), up or down its face when
	 * looking steeply that way, or along the look from a floor, a ceiling or a creature.
	 */
	static Vec3 launch(LocalPlayer player, WebNet.Pull arrived, double speed) {
		Vec3 look = player.getLookAngle();
		Direction face = arrived.face() >= 0 && arrived.face() < Direction.values().length && arrived.entityId() < 0
			? Direction.values()[arrived.face()] : null;
		Vec3 dir;
		if (face != null && face.getAxis().isHorizontal()) {
			Vec3 out = new Vec3(face.getStepX(), 0.0, face.getStepZ());
			float pitch = player.getXRot();
			if (Math.abs(pitch) >= ALONG_PITCH) {
				// looking steeply up (negative pitch) or down: shoot along the wall's face
				dir = new Vec3(out.x * ALONG_OUT, pitch < 0.0F ? 1.0 : -1.0, out.z * ALONG_OUT).normalize();
			} else {
				// off the wall: along the look, but never into it, and with some lift
				Vec3 flat = new Vec3(look.x, 0.0, look.z);
				double into = flat.dot(out);
				if (into < OUT_SHARE) {
					flat = flat.add(out.scale(OUT_SHARE - into));
				}
				dir = new Vec3(flat.x, Math.max(look.y, 0.0) + LIFT, flat.z).normalize();
			}
		} else {
			dir = look;
			if (face == Direction.UP && dir.y < 0.2) {
				dir = new Vec3(dir.x, 0.2, dir.z).normalize();
			}
		}
		return dir.scale(speed);
	}
}
