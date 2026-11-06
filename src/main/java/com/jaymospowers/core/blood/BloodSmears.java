package com.jaymospowers.core.blood;

import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.web.KitchenCompat;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.19.55) Andy liked Death Engine's blood trail (ideas only: All Rights Reserved, Forge only): a body dragged over the
 * ground smears its blood along it. Anyone downed who crawls or is dragged ({@link #downed}, from {@link Downed}), and
 * Jaymo's Kitchen's carcasses dragged behind a player ({@link #tick}), leave a streak of {@link BloodTrail#smear}s behind
 * them, one every {@link #STEP} blocks. A living one's smears are its own stains, so hunting mobs ({@link BloodTracking})
 * and Kingdoms' hunters follow a crawler's streak like any blood trail. A downed body smears less as it weakens (less
 * left to bleed) and a dead one less than the living.
 */
public final class BloodSmears {
	/** A smear is laid every this many blocks the body is dragged; moved further in one go (a teleport, a throw), it starts afresh. */
	static final double STEP = 0.5, JUMP = 3.0;
	/** How far off the ground a body can be and still smear it (blocks). */
	static final double OFF_GROUND = 0.6;
	/** How wide a smear is (stain scale, a man's), from a downed body at full strength down to spent, and from a carcass. */
	static final float DOWNED_SMEAR = 0.42F, SPENT_SMEAR = 0.24F, CARCASS_SMEAR = 0.3F;

	/**
	 * (0.21.26) Andy: "dragging and walking through puddles should smear more than the regular trails". A body dragged
	 * through a pool soaks up to this much of it each step (stain size) and smears it out after, this much at most a
	 * step, so its streak runs wide and dark out of the pool and thins as it goes.
	 */
	static final float DRAG_SOAK = 0.35F, DRAG_SPILL = 0.3F;
	/** (0.21.26) A corpse dragged through water: how much of its blood washes off each step. */
	static final int DRAG_WASH = 12;

	/** Where each body last smeared. */
	private static final Map<Entity, Vec3> LAST = new WeakHashMap<>();
	/** (0.21.26) The blood each body has picked up off pools and not yet smeared out, and its colour. */
	private static final class Wet {
		float amount;
		int color;
	}

	private static final Map<Entity, Wet> WET = new WeakHashMap<>();

	private BloodSmears() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(BloodSmears::tick);
	}

	/** A downed body this tick (crawling, dragged, or lying): smears behind it as it moves. */
	public static void downed(ServerLevel level, LivingEntity body) {
		if (!BladeBlood.bleeds(body)) {
			return;
		}
		if (com.jaymospowers.core.Carry.isCarried(body) && !com.jaymospowers.core.Carry.isDragged(body)) {
			// up in someone's arms: off the ground (and a fresh streak once they're set down)
			LAST.remove(body);
			return;
		}
		float scale = Mth.lerp(Downed.strength(body), SPENT_SMEAR, DOWNED_SMEAR) * size(body.getBbWidth());
		drag(level, body, BladeBlood.color(body), scale);
	}

	/** Every tick: each carcass a player drags behind them. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Entity corpse = KitchenCompat.draggedCorpse(player);
			if (corpse == null || !(corpse.level() instanceof ServerLevel level)) {
				continue;
			}
			EntityType<?> type = KitchenCompat.bodyType(corpse);
			if (type != null && BladeBlood.bleeds(type)) {
				drag(level, corpse, BladeBlood.color(type), CARCASS_SMEAR * size(type.getWidth()));
			}
		}
		if (server.getTickCount() % 200 == 0) {
			LAST.keySet().removeIf(Entity::isRemoved);
			WET.keySet().removeIf(Entity::isRemoved);
		}
	}

	/** Lays a smear behind {@code body} once it's been dragged {@link #STEP} along the ground since the last. */
	private static void drag(ServerLevel level, Entity body, int color, float scale) {
		Vec3 now = body.position();
		Vec3 last = LAST.get(body);
		if (last != null && body.level() == level && (body.isInWater() || body.isInLava())
			&& Math.hypot(now.x - last.x, now.z - last.z) >= STEP) {
			// (0.21.26) dragged through water: washed, a step at a time
			LAST.put(body, now);
			WET.remove(body);
			if (body.isInWater()) {
				if (body instanceof LivingEntity living) {
					com.jaymospowers.core.wet.ArmourGrime.dragged(level, living);
				} else {
					Bloodied.washCorpse(body, DRAG_WASH);
				}
			}
			return;
		}
		if (last == null || body.level() != level) {
			LAST.put(body, now);
			return;
		}
		double dx = now.x - last.x, dz = now.z - last.z;
		double moved = Math.sqrt(dx * dx + dz * dz);
		if (moved > JUMP || Math.abs(now.y - last.y) > JUMP) {
			LAST.put(body, now);
			return;
		}
		if (moved < STEP) {
			return;
		}
		LAST.put(body, now);
		if (!nearGround(level, body)) {
			return;
		}
		// (0.21.26) through a pool: soak it up, and smear it on wider
		int[] poolColor = {color};
		float soaked = BloodTrail.soak(level, now, body.getBbWidth() * 0.5 + 0.3, DRAG_SOAK, poolColor);
		Wet wet = WET.get(body);
		if (soaked > 0.0F) {
			if (wet == null) {
				wet = new Wet();
				WET.put(body, wet);
			}
			wet.amount += soaked;
			wet.color = poolColor[0];
		}
		if (wet != null) {
			float spill = Math.min(wet.amount, DRAG_SPILL);
			wet.amount -= spill;
			if (spill > scale) {
				// mostly the pool's blood now (someone else's, maybe)
				color = wet.color;
			}
			scale += spill;
			if (wet.amount <= 0.01F) {
				WET.remove(body);
			}
		}
		BloodTrail.smear(level, body, color, last, now, scale);
		// (0.21.26) and what it wears takes it: the pool's blood soaked up, the ground's dirt ground in
		if (soaked > 0.0F) {
			Bloodied.dragged(body, soaked, poolColor[0]);
		}
		if (body instanceof LivingEntity living) {
			com.jaymospowers.core.wet.ArmourGrime.dragged(level, living);
		}
	}

	/** Lying on (or just over) the ground, not in water. */
	private static boolean nearGround(ServerLevel level, Entity body) {
		if (body.isInWater() || body.isInLava()) {
			return false;
		}
		if (body.onGround()) {
			return true;
		}
		return !level.noCollision(body, body.getBoundingBox().move(0.0, -OFF_GROUND, 0.0));
	}

	/** A body's smear size by its width (a man's is 1). */
	private static float size(float width) {
		return (width + 2.0F) / 3.0F;
	}
}
