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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.19.55) Andy liked Death Engine's blood trail (ideas only: All Rights Reserved, Forge only): a body dragged over the
 * ground smears its blood along it. Anyone downed who crawls or is dragged ({@link #downed}, from {@link Downed}), and
 * Jaymo's Kitchen's carcasses dragged behind a player ({@link #tick}), leave a trail of {@link BloodTrail#drag} pieces behind
 * them, as wide as the body ({@link #TRAIL_WIDTH}). A living one's smears are its own stains, so hunting mobs ({@link BloodTracking})
 * and Kingdoms' hunters follow a crawler's streak like any blood trail. A downed body smears less as it weakens (less
 * left to bleed) and a dead one less than the living.
 */
public final class BloodSmears {
	/** Moved further than this in one go (a teleport, a throw), a body's trail starts afresh (blocks). */
	static final double JUMP = 3.0;
	/**
	 * Andy: "a large body will drag and a single tiny trail of blood will be there, it should be more like the rest of the
	 * splashes". A trail is as wide as the body dragging it: this many times its hitbox width (a man's broad, a cow's wide, a
	 * chicken's thin), times this share for a downed body at full strength down to spent, or for a carcass; never wider
	 * than this (blocks).
	 */
	static final float TRAIL_WIDTH = 1.1F, DOWNED_SHARE = 1.0F, SPENT_SHARE = 0.55F, CARCASS_SHARE = 0.85F, MAX_WIDTH = 2.5F;
	/** A trail piece is laid every this share of its width dragged, between these steps (blocks), so the pieces overlap into one smear. */
	static final double STEP_PER_WIDTH = 0.8, MIN_STEP = 0.2, MAX_STEP = 0.6;
	/** Each piece's width wanders this share either way, and it lies up to this share of its width to one side: a blotchy edge. */
	static final float WIDTH_JITTER = 0.18F, SIDE_JITTER = 0.12F;
	/** Each step, the chance a drop falls beside the trail, and its size (stain scale) for a man-wide trail. */
	static final float DROP_CHANCE = 0.4F, DROP_SCALE = 0.16F;
	/** Turned more than this (degrees) between steps: a denser blot where it turned, this big next to its width. */
	static final float TURN_DEGREES = 40.0F, TURN_BLOT = 0.9F;
	/** Stopped this many ticks: a blot where it lies, this big next to its width (once a stop). */
	static final int STOP_TICKS = 30;
	static final float STOP_BLOT = 1.1F;
	/** How far off the ground a body can be and still smear it (blocks). */
	static final double OFF_GROUND = 0.6;

	/**
	 * (0.21.28) Andy: "dragging and walking through puddles should smear more than the regular trails". A body dragged
	 * through a pool soaks up to this much of it each step (stain size) and smears it out after, this much at most a
	 * step, so its streak runs wide and dark out of the pool and thins as it goes.
	 */
	static final float DRAG_SOAK = 0.35F, DRAG_SPILL = 0.3F;
	/** (0.21.28) A corpse dragged through water: how much of its blood washes off each step. */
	static final int DRAG_WASH = 12;
	/**
	 * (0.21.28) Andy: "i'd like players to pull dirty beaten armor off enemies". A dragged corpse's armour (kept on the body,
	 * Kitchen) takes the blood, dirt and washing as a living body's does, gathered up and laid on every this many steps
	 * (rewriting what it wears is costly, and every player near is sent the body's look again).
	 */
	static final int CORPSE_GEAR_STEPS = 4;

	/** (0.21.28) What a dragged corpse's armour has been through since it was last laid on. */
	private static final class Gear {
		int steps;
		float blood;
		int color;
		int dirtSteps;
		com.jaymospowers.core.wet.ArmourGrime.@org.jspecify.annotations.Nullable Dirt dirt;
		int washSteps;
	}

	private static final Map<Entity, Gear> GEAR = new WeakHashMap<>();

	/** Each body's trail so far: where it last smeared, the way it was going, and how long it has lain still. */
	private static final class Path {
		Vec3 last;
		/** Where it was last tick. */
		Vec3 at;
		double dirX, dirZ;
		int still;
		boolean blotted;

		Path(Vec3 at) {
			this.last = at;
			this.at = at;
		}
	}

	private static final Map<Entity, Path> LAST = new WeakHashMap<>();
	/** (0.21.28) The blood each body has picked up off pools and not yet smeared out, and its colour. */
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
		drag(level, body, BladeBlood.color(body), width(body.getBbWidth(), Mth.lerp(Downed.strength(body), SPENT_SHARE, DOWNED_SHARE)));
	}

	/** Every tick: each carcass a player drags behind them. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Entity corpse = KitchenCompat.draggedCorpse(player);
			if (corpse == null || !(corpse.level() instanceof ServerLevel level)) {
				continue;
			}
			EntityType<?> type = KitchenCompat.bodyType(corpse);
			if (type != null && BladeBlood.bleeds(corpse, type)) {
				drag(level, corpse, BladeBlood.color(corpse, type), width(type.getWidth(), CARCASS_SHARE));
			}
		}
		if (server.getTickCount() % 200 == 0) {
			LAST.keySet().removeIf(Entity::isRemoved);
			WET.keySet().removeIf(Entity::isRemoved);
			GEAR.keySet().removeIf(Entity::isRemoved);
		}
	}

	/** Lays {@code body}'s trail, {@code width} blocks across, behind it once it's been dragged a step along the ground since the last. */
	private static void drag(ServerLevel level, Entity body, int color, float width) {
		Vec3 now = body.position();
		Path path = LAST.get(body);
		Vec3 last = path == null ? null : path.last;
		double step = Mth.clamp(width * STEP_PER_WIDTH, MIN_STEP, MAX_STEP);
		if (last != null && body.level() == level && (body.isInWater() || body.isInLava())
			&& Math.hypot(now.x - last.x, now.z - last.z) >= step) {
			// (0.21.28) dragged through water: washed, a step at a time
			path.last = now;
			path.dirX = path.dirZ = 0.0;
			WET.remove(body);
			if (body.isInWater()) {
				if (body instanceof LivingEntity living) {
					com.jaymospowers.core.wet.ArmourGrime.dragged(level, living);
				} else {
					Bloodied.washCorpse(body, DRAG_WASH);
					corpseGear(level, body).washSteps++;
					flushGear(level, body, false);
				}
			}
			return;
		}
		if (last == null || body.level() != level) {
			LAST.put(body, new Path(now));
			return;
		}
		double dx = now.x - last.x, dz = now.z - last.z;
		double moved = Math.sqrt(dx * dx + dz * dz);
		if (moved > JUMP || Math.abs(now.y - last.y) > JUMP) {
			LAST.put(body, new Path(now));
			return;
		}
		RandomSource random = level.getRandom();
		boolean stopped = now.distanceToSqr(path.at) < 1.0E-4;
		path.at = now;
		path.still = stopped ? path.still + 1 : 0;
		if (moved < step) {
			// dragged, then lain still a while: blood gathers where it stopped
			boolean dragged = path.dirX != 0.0 || path.dirZ != 0.0;
			if (dragged && path.still >= STOP_TICKS && !path.blotted && nearGround(level, body)) {
				path.blotted = true;
				BloodTrail.blot(level, body, color, now.x, now.y + 0.5, now.z, blotScale(width, STOP_BLOT));
			}
			return;
		}
		double ux = dx / moved, uz = dz / moved;
		boolean turned = (path.dirX != 0.0 || path.dirZ != 0.0)
			&& ux * path.dirX + uz * path.dirZ < Mth.cos(TURN_DEGREES * Mth.DEG_TO_RAD);
		path.last = now;
		path.dirX = ux;
		path.dirZ = uz;
		path.blotted = false;
		if (!nearGround(level, body)) {
			return;
		}
		// (0.21.28) through a pool: soak it up, and smear it on wider
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
			if (spill * BloodParticles.Puddle.BLOCKS_PER_SCALE > width) {
				// mostly the pool's blood now (someone else's, maybe)
				color = wet.color;
			}
			width += spill * BloodParticles.Puddle.BLOCKS_PER_SCALE * 0.5F;
			if (wet.amount <= 0.01F) {
				WET.remove(body);
			}
		}
		width = Math.min(width, MAX_WIDTH);
		if (turned) {
			// a denser blot where it turned
			BloodTrail.blot(level, body, color, last.x, Math.max(last.y, now.y) + 0.5, last.z, blotScale(width, TURN_BLOT));
		}
		// overlapping pieces, as wide as the body, each a little wider or narrower and off to one side: one blotchy smear
		boolean alongX = Math.abs(dx) >= Math.abs(dz);
		int pieces = Math.min(6, Mth.ceil(moved / step));
		for (int i = 0; i < pieces; i++) {
			double t = (i + 0.5) / pieces;
			float w = width * (1.0F + (random.nextFloat() * 2.0F - 1.0F) * WIDTH_JITTER);
			double side = (random.nextFloat() * 2.0F - 1.0F) * SIDE_JITTER * width;
			BloodTrail.drag(level, body, color, last.x + dx * t - uz * side, Math.max(last.y, now.y) + 0.5, last.z + dz * t + ux * side, alongX, w);
		}
		if (random.nextFloat() < DROP_CHANCE) {
			// a drop shaken off beside it
			double side = (random.nextBoolean() ? 1.0 : -1.0) * width * (0.5 + random.nextDouble() * 0.4);
			double along = random.nextDouble();
			BloodTrail.blot(level, body, color, last.x + dx * along - uz * side, Math.max(last.y, now.y) + 0.5, last.z + dz * along + ux * side,
				DROP_SCALE * Mth.clamp(width / (TRAIL_WIDTH * 0.6F), 0.5F, 1.6F) * (0.6F + random.nextFloat() * 0.8F));
		}
		// (0.21.28) and what it wears takes it: the pool's blood soaked up, the ground's dirt ground in
		if (soaked > 0.0F) {
			Bloodied.dragged(body, soaked, poolColor[0]);
		}
		if (body instanceof LivingEntity living) {
			com.jaymospowers.core.wet.ArmourGrime.dragged(level, living);
		} else {
			Gear gear = corpseGear(level, body);
			if (soaked > 0.0F) {
				gear.blood += soaked;
				gear.color = poolColor[0];
			}
			com.jaymospowers.core.wet.ArmourGrime.Dirt dirt = com.jaymospowers.core.wet.ArmourGrime.dragDirt(level, body);
			if (dirt != null) {
				gear.dirtSteps++;
				gear.dirt = dirt;
			}
			flushGear(level, body, false);
		}
	}

	private static Gear corpseGear(ServerLevel level, Entity corpse) {
		Gear gear = GEAR.computeIfAbsent(corpse, c -> new Gear());
		gear.steps++;
		return gear;
	}

	/** (0.21.28) Lays what a corpse's armour has been through on it, every {@link #CORPSE_GEAR_STEPS} steps (or {@code now}). */
	private static void flushGear(ServerLevel level, Entity corpse, boolean now) {
		Gear gear = GEAR.get(corpse);
		if (gear == null || !now && gear.steps < CORPSE_GEAR_STEPS) {
			return;
		}
		GEAR.remove(corpse);
		if (gear.blood <= 0.0F && gear.dirtSteps <= 0 && gear.washSteps <= 0) {
			return;
		}
		long time = level.getGameTime();
		KitchenCompat.reworkArmour(corpse, (stack, index) -> {
			if (gear.washSteps > 0) {
				com.jaymospowers.core.wet.ArmourGrime.washArmour(stack, gear.washSteps);
			}
			if (gear.dirt != null && gear.dirtSteps > 0) {
				com.jaymospowers.core.wet.ArmourGrime.dragArmour(stack, index, gear.dirt, gear.dirtSteps, level.getRandom());
			}
			if (gear.blood > 0.0F) {
				Bloodied.dragArmour(stack, index, gear.blood, gear.color, time, level.getRandom());
			}
		});
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

	/** A trail's width (blocks) behind a body this wide (its hitbox), at this share. */
	private static float width(float bodyWidth, float share) {
		return Math.min(MAX_WIDTH, bodyWidth * TRAIL_WIDTH * share);
	}

	/** A round blot's stain scale for a trail this wide, at this size next to it. */
	private static float blotScale(float width, float size) {
		return width * size / BloodParticles.Puddle.BLOCKS_PER_SCALE;
	}
}
