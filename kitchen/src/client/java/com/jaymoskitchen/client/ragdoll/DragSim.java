package com.jaymoskitchen.client.ragdoll;

import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.ragdoll.LevelRagdollWorld;
import com.jaymoskitchen.ragdoll.RagdollBody;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.45) Andy: the dragged body "should move from that single point instead of jumping every movement". The server's
 * points come a round trip behind the hand you see (your own hand most of all), so a body being dragged is worked out
 * here instead, every tick after the players have moved: its one held point ({@link CorpseEntity#gripPoint}) is put in
 * the hand where this client draws it, and the rest hangs and drags from it over this client's blocks. It starts from
 * the points the server last sent; the server's own points for it are passed over while it's held, and once it's let go
 * they take over again.
 */
public final class DragSim {
	private static final Int2ObjectMap<RagdollBody> BODIES = new Int2ObjectOpenHashMap<>();

	private DragSim() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DragSim::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BODIES.clear());
	}

	/** Whether this client is working out body {@code id} itself (being dragged): the server's points for it wait. */
	static boolean active(int id) {
		return BODIES.containsKey(id);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			BODIES.clear();
			return;
		}
		IntSet held = new IntOpenHashSet();
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof CorpseEntity corpse) || !(corpse.dragger() instanceof Player player)) {
				continue;
			}
			int id = corpse.getId();
			RagdollBody body = BODIES.get(id);
			if (body == null) {
				ClientRagdolls.Track track = ClientRagdolls.get(id);
				if (track == null) {
					// (not heard of yet: the server's points first)
					continue;
				}
				body = new RagdollBody(track.rig);
				double[] points = track.points(1.0F);
				for (int i = 0; i < body.rig.count; i++) {
					body.x[i] = body.px[i] = points[i * 3];
					body.y[i] = body.py[i] = points[i * 3 + 1];
					body.z[i] = body.pz[i] = points[i * 3 + 2];
				}
				BODIES.put(id, body);
			}
			int point = CorpseEntity.gripPoint(body.rig);
			Vec3 hand = CorpseEntity.dragHand(player, corpse.dragHands(), new Vec3(body.x[point], body.y[point], body.z[point]));
			body.grip(0, point, hand.x, hand.y, hand.z);
			body.step(new LevelRagdollWorld(level, corpse), corpse.isNoGravity() ? 0.0F : RagdollBody.GRAVITY);
			if (!body.sane()) {
				// (blew up: back to the server's)
				BODIES.remove(id);
				continue;
			}
			ClientRagdolls.put(id, body);
			held.add(id);
		}
		BODIES.int2ObjectEntrySet().removeIf(entry -> !held.contains(entry.getIntKey()));
	}
}
