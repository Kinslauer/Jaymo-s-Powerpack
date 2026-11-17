package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.compat.RagdollifiedCompat;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.ragdoll.RagdollNet;
import com.jaymoskitchen.ragdoll.RagdollSnapshot;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Ragdollified (see {@link RagdollifiedCompat}) plays a dying body's fall on the client. The corpse that waits for it
 * ({@link CorpseEntity#origin}) isn't drawn while its ragdoll is; when the ragdoll reports it came to rest, this client
 * tells the server where and along which way, the corpse lies down there, and the ragdoll is taken away the moment the
 * corpse is drawn again. Called by reflection only.
 */
public final class RagdollifiedBridge {
	/** Ticks a fresh corpse stays undrawn whatever Ragdollified says: its ragdoll is made a few ticks after the death. */
	private static final int SPAWN_GRACE = 6;
	/** Ticks after telling the server that the ragdoll is taken away anyway (the server never answered). */
	private static final int ANSWER_TICKS = 100;

	/** Ragdolls (by dying entity id) that came to rest, reported on whatever thread Ragdollified calls from. */
	private static final ConcurrentLinkedQueue<Integer> SETTLED = new ConcurrentLinkedQueue<>();
	/** Ragdolls this client reported: when (client tick). */
	private static final Map<Integer, Integer> SENT = new HashMap<>();
	private static int now;

	private static Class<?> apiClass = Object.class;
	private static @Nullable Method hasRagdoll, getState, remove, torsoPosition, partPositions;

	private RagdollifiedBridge() {
	}

	public static void register() {
		if (!RagdollifiedCompat.loaded()) {
			return;
		}
		try {
			Class<?> api = Class.forName("com.raiiiden.ragdollified.api.RagdollifiedApi");
			Class<?> listener = Class.forName("com.raiiiden.ragdollified.api.RagdollSettleListener");
			Class<?> state = Class.forName("com.raiiiden.ragdollified.api.RagdollState");
			Class<?> event = Class.forName("com.raiiiden.ragdollified.api.RagdollSettleEvent");
			apiClass = api;
			hasRagdoll = api.getMethod("hasRagdoll", int.class);
			getState = api.getMethod("getState", int.class);
			remove = api.getMethod("remove", int.class);
			torsoPosition = state.getMethod("torsoPosition");
			partPositions = state.getMethod("partPositions");
			Method eventId = event.getMethod("ragdollEntityId");
			Object proxy = Proxy.newProxyInstance(RagdollifiedBridge.class.getClassLoader(), new Class<?>[] {listener},
				(self, method, args) -> {
					switch (method.getName()) {
						case "onRagdollSettled" -> {
							SETTLED.add((Integer) eventId.invoke(args[0]));
							return null;
						}
						case "hashCode" -> {
							return System.identityHashCode(self);
						}
						case "equals" -> {
							return self == args[0];
						}
						default -> {
							return self.getClass().getName();
						}
					}
				});
			api.getMethod("addSettleListener", listener).invoke(null, proxy);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			JaymosKitchen.LOGGER.warn("Ragdollified is loaded but its ragdolls can't be followed: {}", e.toString());
			return;
		}
		CorpseEntity.hiddenByRagdoll = corpse -> corpse.tickCount < SPAWN_GRACE || has(corpse.origin());
		ClientTickEvents.END_CLIENT_TICK.register(RagdollifiedBridge::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SETTLED.clear();
			SENT.clear();
			CorpseEntity.WAITING.clear();
		});
	}

	private static boolean has(int id) {
		try {
			return (Boolean) hasRagdoll.invoke(null, id);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static void tick(Minecraft minecraft) {
		now++;
		Integer id;
		while ((id = SETTLED.poll()) != null) {
			if (!SENT.containsKey(id)) {
				report(id);
			}
		}
		// the corpse lies down (or never will): the ragdoll goes the moment the corpse is drawn
		Iterator<Map.Entry<Integer, Integer>> it = SENT.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Integer> e = it.next();
			if (waiting(e.getKey()) == null || now - e.getValue() > ANSWER_TICKS) {
				take(e.getKey());
				it.remove();
			}
		}
		CorpseEntity.WAITING.removeIf(corpse -> corpse.isRemoved() || corpse.origin() < 0);
	}

	/** The corpse still waiting for the ragdoll of dying entity {@code id}, or null. */
	private static @Nullable CorpseEntity waiting(int id) {
		for (CorpseEntity corpse : new ArrayList<>(CorpseEntity.WAITING)) {
			if (corpse.origin() == id && !corpse.isRemoved()) {
				return corpse;
			}
		}
		return null;
	}

	private static void take(int id) {
		try {
			remove.invoke(null, id);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// already gone
		}
	}

	/** Tells the server where the ragdoll of dying entity {@code id} lies: its middle, the ground under it, its head's way. */
	private static void report(int id) {
		CorpseEntity corpse = waiting(id);
		if (corpse == null) {
			return;
		}
		try {
			Object state = ((Optional<?>) getState.invoke(null, id)).orElse(null);
			if (state == null) {
				return;
			}
			Vec3 torso = (Vec3) torsoPosition.invoke(state);
			double ground = torso.y;
			Vec3 head = null;
			for (Map.Entry<?, ?> part : ((Map<?, ?>) partPositions.invoke(state)).entrySet()) {
				Vec3 at = (Vec3) part.getValue();
				ground = Math.min(ground, at.y);
				if ("HEAD".equals(((Enum<?>) part.getKey()).name())) {
					head = at;
				}
			}
			float yaw = corpse.getYRot();
			if (head != null) {
				double dx = head.x - torso.x, dz = head.z - torso.z;
				if (dx * dx + dz * dz > 0.04) {
					yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				}
			}
			// the pose it lies in, on our rig (humanoids only; null otherwise, and the corpse lies in our fallen pose)
			ClientRagdolls.Track track = ClientRagdolls.get(corpse.getId());
			RagdollSnapshot pose = track == null ? null : RagdollifiedPose.build(apiClass, id, track.rig);
			ClientPlayNetworking.send(new RagdollNet.Settled(corpse.getId(), torso.x, ground, torso.z, yaw, pose));
			SENT.put(id, now);
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosKitchen.LOGGER.debug("Ragdollified's ragdoll {} couldn't be read: {}", id, e.toString());
		}
	}
}
