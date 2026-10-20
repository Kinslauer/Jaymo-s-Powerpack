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
 * (0.5.0) Every ragdoll this client knows, by entity id: the last two sets of points it was sent (or simulated itself,
 * for its own limp player), so a frame can be drawn between them. Bodies not heard from in a tick hold still; bodies
 * whose entity is gone are dropped.
 */
public final class ClientRagdolls {
	/** A body unheard of this long (ticks) while its entity is gone is forgotten. */
	private static final int FORGET_TICKS = 40;

	/** One body: its rig, and its points a tick ago and now (x, y, z each). */
	public static final class Track {
		public final RagdollRig rig;
		final double[] before, now;
		boolean fresh, asleep;
		int unseen;

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
