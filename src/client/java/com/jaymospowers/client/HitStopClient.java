package com.jaymospowers.client;

import com.jaymospowers.core.HitStop;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Hit-stop on this side ({@link HitStop}): for the few ticks of a hold, the attacker's and the struck one's bodies keep
 * the pose they had at the blow (every model drawn for them: the body, armour, the suit over it), the local attacker's
 * own slide stops, and the screen takes a short kick from the blow. The hold is timed on the wall clock, so it is the
 * same length whatever the frame rate.
 */
public final class HitStopClient {
	/** The kick on the screen of whoever struck or was struck, in degrees for each tick held. */
	static final float SHAKE_PER_TICK = 0.22F;
	/** A tick, in milliseconds. */
	static final long TICK_MS = 50L;

	/** Entity id to the time (ms) its hold ends. */
	private static final Map<Integer, Long> UNTIL = new HashMap<>();
	/** The time (ms) until which this player, having struck, stands planted. */
	private static long plantUntil;
	/** Entity id to each of its models' kept poses (by the model itself). */
	private static final Map<Integer, Map<Model<?>, PartPose[]>> KEPT = new HashMap<>();

	private HitStopClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(HitStop.Stop.TYPE, (payload, context) -> stop(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(HitStopClient::tick);
	}

	private static void stop(Minecraft minecraft, HitStop.Stop stop) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		long until = Util.getMillis() + stop.ticks() * TICK_MS;
		for (int id : new int[] {stop.attacker(), stop.target()}) {
			if (id >= 0) {
				UNTIL.merge(id, until, Math::max);
			}
		}
		if (player.getId() == stop.attacker()) {
			plantUntil = Math.max(plantUntil, until);
		}
		if (player.getId() == stop.attacker() || player.getId() == stop.target()) {
			Entity other = player.level().getEntity(player.getId() == stop.attacker() ? stop.target() : stop.attacker());
			Vec3 from = other == null ? null : other.getBoundingBox().getCenter();
			ScreenShake.add(SHAKE_PER_TICK * stop.ticks(), stop.ticks() + 3, from);
		}
	}

	/** Whether this entity is held now. */
	public static boolean held(int id) {
		Long until = UNTIL.get(id);
		return until != null && Util.getMillis() < until;
	}

	private static void tick(Minecraft minecraft) {
		long now = Util.getMillis();
		for (Iterator<Map.Entry<Integer, Long>> it = UNTIL.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> entry = it.next();
			if (now >= entry.getValue()) {
				KEPT.remove(entry.getKey());
				it.remove();
			}
		}
		LocalPlayer player = minecraft.player;
		if (player != null && now < plantUntil && player.onGround()) {
			// the one who struck plants their feet for it
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(0.0, motion.y, 0.0);
		}
	}

	/** Right after a model animates: a held body's model keeps the pose it had when the hold began. */
	public static void applyModel(Model<?> model, Object state) {
		if (UNTIL.isEmpty() || !(state instanceof LivingEntityRenderState living)) {
			return;
		}
		Integer id = living instanceof AvatarRenderState avatar ? Integer.valueOf(avatar.id) : living.getData(PoseTakeover.MOB_ID);
		if (id == null || !held(id)) {
			return;
		}
		List<ModelPart> parts = model.allParts();
		Map<Model<?>, PartPose[]> models = KEPT.computeIfAbsent(id, i -> new HashMap<>());
		PartPose[] kept = models.get(model);
		if (kept == null || kept.length != parts.size()) {
			kept = new PartPose[parts.size()];
			for (int i = 0; i < kept.length; i++) {
				kept[i] = parts.get(i).storePose();
			}
			models.put(model, kept);
			return;
		}
		for (int i = 0; i < kept.length; i++) {
			parts.get(i).loadPose(kept[i]);
		}
	}
}
