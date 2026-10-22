package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollNet;
import com.jaymoskitchen.ragdoll.RagdollRig;
import com.jaymoskitchen.ragdoll.RagdollSnapshot;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Iterator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * (0.5.1) Every ragdoll this client knows, by entity id: the last two sets of points it was sent (or simulated itself,
 * for its own limp player), so a frame can be drawn between them. Bodies not heard from in a tick hold still; bodies
 * whose entity is gone are dropped.
 */
public final class ClientRagdolls {
	/** A body unheard of this long (ticks) while its entity is gone is forgotten. */
	private static final int FORGET_TICKS = 40;
	/** A body unheard of longer than this (ticks) jumps to its next points instead of gliding there. */
	private static final int GAP_TICKS = 2;
	/**
	 * (0.5.16) A four-legged body moving slower than this (blocks a tick) is eased onto its side, this much a tick (see
	 * RagdollPose#of): its legs prop the physics belly down, sitting up like a dog, but a dead animal lies on its side.
	 */
	private static final double SIDE_SPEED = 0.06;
	private static final float SIDE_EASE = 0.12F;

	/** One body: its rig, and its points a tick ago and now (x, y, z each). */
	public static final class Track {
		public final RagdollRig rig;
		final double[] before, now;
		boolean fresh, asleep;
		int unseen;
		/** (0.5.16) How far onto its side it's drawn (0 as the physics has it, 1 on its side), and which side is up. */
		float side, sideBefore;
		int sideUp;

		Track(RagdollRig rig) {
			this.rig = rig;
			this.before = new double[rig.count * 3];
			this.now = new double[rig.count * 3];
		}

		/** Point {@code i} between the last two ticks, into {@code out} at {@code o}. */
		public void point(int i, float partialTicks, double[] out, int o) {
			for (int k = 0; k < 3; k++) {
				out[o + k] = before[i * 3 + k] + (now[i * 3 + k] - before[i * 3 + k]) * partialTicks;
			}
		}

		/** Every point between the last two ticks: x, y, z each. */
		public double[] points(float partialTicks) {
			double[] out = new double[now.length];
			for (int i = 0; i < rig.count; i++) {
				point(i, partialTicks, out, i * 3);
			}
			return out;
		}

		public boolean asleep() {
			return asleep;
		}

		/** (0.5.16) How far onto its side a four-legged body is drawn, between the last two ticks. */
		public float side(float partialTicks) {
			return sideBefore + (side - sideBefore) * partialTicks;
		}

		/** (0.5.16) Which side is up as it lies (1 its right, -1 its left). */
		public int sideUp() {
			return sideUp == 0 ? 1 : sideUp;
		}

		/** Already lying still when first seen (come into view): drawn on its side from the start. */
		void onSide() {
			if (rig.shape == RagdollRig.Shape.QUADRUPED) {
				pickSide();
				side = sideBefore = 1.0F;
			}
		}

		/** It keeps lying on whichever side is already the higher. */
		private void pickSide() {
			float[] frame = new float[9];
			float[] x = new float[rig.count], y = new float[rig.count], z = new float[rig.count];
			for (int i = 0; i < rig.count; i++) {
				x[i] = (float) now[i * 3];
				y[i] = (float) now[i * 3 + 1];
				z[i] = (float) now[i * 3 + 2];
			}
			RagdollRig.frame(x, y, z, frame);
			sideUp = frame[1] >= 0.0F ? 1 : -1;
		}

		/** Eases a four-legged body toward its side while it's (nearly) still, back to the physics while it moves. */
		void settle() {
			sideBefore = side;
			if (rig.shape != RagdollRig.Shape.QUADRUPED) {
				return;
			}
			double fastest = 0;
			for (int i = 0; i < rig.count; i++) {
				double dx = now[i * 3] - before[i * 3], dy = now[i * 3 + 1] - before[i * 3 + 1], dz = now[i * 3 + 2] - before[i * 3 + 2];
				fastest = Math.max(fastest, dx * dx + dy * dy + dz * dz);
			}
			boolean still = Math.sqrt(fastest) < SIDE_SPEED;
			if (still && sideUp == 0) {
				pickSide();
			}
			side = Math.max(0.0F, Math.min(1.0F, side + (still ? SIDE_EASE : -SIDE_EASE)));
			if (side == 0.0F && !still) {
				sideUp = 0;
			}
		}
	}

	private static final Int2ObjectMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

	private ClientRagdolls() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(RagdollNet.Pose.TYPE, (payload, ctx) -> put(payload.entity(), payload.snapshot()));
		ClientTickEvents.START_CLIENT_TICK.register(ClientRagdolls::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TRACKS.clear());
	}

	/** The body drawn for entity {@code id}, or null if it has none (not a ragdoll, or not heard of yet). */
	public static Track get(int id) {
		return TRACKS.get(id);
	}

	public static void forget(int id) {
		TRACKS.remove(id);
	}

	/** A body's points as sent. A body seen for the first time (or changed shape) starts there, not from nothing. */
	public static void put(int id, RagdollSnapshot snapshot) {
		Track track = TRACKS.get(id);
		if (track == null || !snapshot.fits(track.rig)) {
			track = new Track(snapshot.rig());
			if (track.rig.count != snapshot.count()) {
				return;
			}
			TRACKS.put(id, track);
			fill(track.now, snapshot);
			System.arraycopy(track.now, 0, track.before, 0, track.now.length);
			if (snapshot.asleep()) {
				track.onSide();
			}
		} else if (track.unseen > GAP_TICKS) {
			// silent a while (carried, asleep): it starts where it is now rather than sliding from where it was
			fill(track.now, snapshot);
			System.arraycopy(track.now, 0, track.before, 0, track.now.length);
		} else {
			System.arraycopy(track.now, 0, track.before, 0, track.now.length);
			fill(track.now, snapshot);
		}
		track.fresh = true;
		track.unseen = 0;
		track.asleep = snapshot.asleep();
	}

	/** A body this client simulates itself (its own player, limp): its points as they are now. */
	public static void put(int id, RagdollBody body) {
		Track track = TRACKS.get(id);
		if (track == null || track.rig.shape != body.rig.shape || track.rig.count != body.rig.count) {
			track = new Track(body.rig);
			TRACKS.put(id, track);
			copy(track.now, body);
			System.arraycopy(track.now, 0, track.before, 0, track.now.length);
		} else {
			System.arraycopy(track.now, 0, track.before, 0, track.now.length);
			copy(track.now, body);
		}
		track.fresh = true;
		track.unseen = 0;
		track.asleep = body.asleep();
	}

	private static void fill(double[] into, RagdollSnapshot snapshot) {
		for (int i = 0; i < snapshot.count(); i++) {
			into[i * 3] = snapshot.pointX(i);
			into[i * 3 + 1] = snapshot.pointY(i);
			into[i * 3 + 2] = snapshot.pointZ(i);
		}
	}

	private static void copy(double[] into, RagdollBody body) {
		for (int i = 0; i < body.rig.count; i++) {
			into[i * 3] = body.x[i];
			into[i * 3 + 1] = body.y[i];
			into[i * 3 + 2] = body.z[i];
		}
	}

	/** Start of each client tick: bodies not heard from last tick stand still; gone ones are forgotten. */
	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			TRACKS.clear();
			return;
		}
		Iterator<Int2ObjectMap.Entry<Track>> it = TRACKS.int2ObjectEntrySet().iterator();
		while (it.hasNext()) {
			Int2ObjectMap.Entry<Track> entry = it.next();
			Track track = entry.getValue();
			track.settle();
			if (!track.fresh) {
				System.arraycopy(track.now, 0, track.before, 0, track.now.length);
				if (++track.unseen > FORGET_TICKS && minecraft.level.getEntity(entry.getIntKey()) == null) {
					it.remove();
				}
			}
			track.fresh = false;
		}
	}
}
