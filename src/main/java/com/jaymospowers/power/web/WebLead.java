package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Yank lead (B held), the parts that aren't the pull itself ({@code WebPower.tickTether}): what Yank catches, which
 * of a catch's own doings break the lead, and how a creature or player on it gets up terrain.
 * <ul>
 * <li><b>Aim:</b> Yank catches what's right under the crosshair, or else, like a swing web, the creature nearest the
 * crosshair's line within {@link #AIM_MAGNET} blocks (less its own size), in sight, in reach. A moving enemy no longer
 * has to be hit dead centre, and a near miss no longer strings a webline behind it.</li>
 * <li><b>Enemies stay on:</b> a mob's own attacks and arm swings don't snap the lead (a caught <i>player</i> still
 * breaks free by fighting it, as before). An enemy burning in daylight doesn't burn it through (fire and lava along the
 * line, or anything else alight, still do). A mob knocked past the break length only snaps it if it stays there for
 * {@link #MOB_SNAP_TICKS} ticks.</li>
 * <li><b>Up terrain:</b> while held, its step height is raised by {@link #STEP_BOOST} (so a full block steps up like a
 * slab, for a mob's walk and a player's own client alike); pulled at a taller face (up to {@link #CLIMB_HEIGHT}) with
 * room above it, it's hauled up the face at {@link #CLIMB_SPEED} and pushed over the top.</li>
 * </ul>
 */
public final class WebLead {
	// ===== the knobs ================================================================================================
	/** Yank's aim: how close to the crosshair's line (blocks, less half the creature's size) still catches it. */
	static final double AIM_MAGNET = 1.5;
	/** A mob knocked past the lead's break length snaps it only after this many ticks there in a row. */
	static final int MOB_SNAP_TICKS = 8;
	/** Held on the lead: added step height (a player's and a mob's own is 0.6, so 1.25 in all: a full block). */
	static final double STEP_BOOST = 0.65;
	/**
	 * Pulled at a face taller than its step (up to {@code CLIMB_HEIGHT} blocks) with room above it, the lead hauls it up
	 * at {@code CLIMB_SPEED} blocks/tick, pressed {@code CLIMB_HUG} blocks/tick into the face so it keeps hold, and
	 * shoved {@code CLIMB_OVER} over the lip as it clears. Only when the pull's level share is at least
	 * {@code CLIMB_MIN_FLAT} (a pull straight up already lifts it). The face is looked for {@code PROBE} blocks ahead.
	 */
	static final double CLIMB_HEIGHT = 3.0, CLIMB_SPEED = 0.3, CLIMB_HUG = 0.08, CLIMB_OVER = 0.22, CLIMB_MIN_FLAT = 0.25;
	static final double PROBE = 0.4;
	/** The face's height is measured in steps this fine (blocks). */
	static final double CLIMB_STEP = 0.25;
	// ================================================================================================================

	private static final Identifier STEP_ID = JaymosPowers.id("web_lead_step");
	/** Who has the lead's step height on, and the game time it was last renewed (taken off a tick after it isn't). */
	private static final Map<LivingEntity, Long> STEPPING = new WeakHashMap<>();

	private WebLead() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (STEPPING.isEmpty()) {
				return;
			}
			for (LivingEntity living : new ArrayList<>(STEPPING.keySet())) {
				Long renewed = STEPPING.get(living);
				// (a lead let go of, snapped, or its slinger gone: the step height comes off)
				if (renewed == null || living.isRemoved() || living.level().getGameTime() - renewed > 1L) {
					STEPPING.remove(living);
					step(living, false);
				}
			}
		});
	}

	// ---- aim ---------------------------------------------------------------------------------------------------------

	/**
	 * What Yank catches: the entity right under the crosshair, else the creature, player or corpse nearest the
	 * crosshair's line within {@link #AIM_MAGNET} (not behind a wall), out to {@code range}. Null if none.
	 */
	static @Nullable Entity aim(ServerPlayer player, double range) {
		Entity exact = WebPower.entityInSight(player, range);
		if (exact != null) {
			return exact;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		BlockHitResult wall = WebAnchors.clip(player, eye, eye.add(look.scale(range)));
		double reach = wall.getType() == HitResult.Type.MISS ? range : Math.min(range, wall.getLocation().distanceTo(eye) + AIM_MAGNET);
		AABB search = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(AIM_MAGNET + 1.0);
		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity e : player.level().getEntities(player, search, e -> WebAnchors.webbable(player, e) && !(e instanceof ItemEntity))) {
			Vec3 middle = e.getBoundingBox().getCenter();
			double along = middle.subtract(eye).dot(look);
			if (along < 0.5 || along > reach) {
				continue;
			}
			double off = middle.distanceTo(eye.add(look.scale(along))) - Math.min(e.getBbWidth(), e.getBbHeight()) * 0.5;
			if (off > AIM_MAGNET) {
				continue;
			}
			// nearest the line first, then nearest you
			double score = Math.max(0.0, off) + along * 0.02;
			if (score < bestScore && WebAnchors.clip(player, eye, middle).getType() == HitResult.Type.MISS) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	// ---- what breaks it ----------------------------------------------------------------------------------------------

	/** Whether the catch's own attacks and swings break the lead: only a player's (a mob's never do). */
	static boolean fightsFree(Entity target) {
		return target instanceof Player;
	}

	/**
	 * What the lead's burn check treats as the burning catch: an enemy alight on dry land (the undead in daylight, as
	 * often as not) doesn't burn the lead through; anything else alight does, and so does fire or lava along the line.
	 */
	static @Nullable Entity burnTarget(Entity target) {
		return target instanceof Enemy && !target.isInLava() ? null : target;
	}

	// ---- up terrain --------------------------------------------------------------------------------------------------

	/**
	 * Every tick something is on the lead: its step height stays raised, and if {@code pull} (unit, from it toward the
	 * slinger's hand) is pulling it at a face too tall to step, it's hauled up it. {@code pulling}: the lead is past its
	 * length (it's being pulled, not hanging slack).
	 */
	static void hold(Entity target, Vec3 pull, boolean pulling) {
		if (target instanceof LivingEntity living) {
			STEPPING.put(living, living.level().getGameTime());
			step(living, true);
		}
		if (pulling) {
			climb(target, pull);
		}
	}

	private static void step(LivingEntity living, boolean on) {
		AttributeInstance attribute = living.getAttribute(Attributes.STEP_HEIGHT);
		if (attribute == null || attribute.hasModifier(STEP_ID) == on) {
			return;
		}
		if (on) {
			// transient: never saved, so a crash or an unload can't leave it behind
			attribute.addTransientModifier(new AttributeModifier(STEP_ID, STEP_BOOST, AttributeModifier.Operation.ADD_VALUE));
		} else {
			attribute.removeModifier(STEP_ID);
		}
	}

	/** Pulled at a face taller than it can step, with room to go up it: hauled up, and over the lip. */
	private static void climb(Entity target, Vec3 pull) {
		if (target.isPassenger() || target.isInWater() || target.isInLava()) {
			return;
		}
		Vec3 flat = new Vec3(pull.x, 0.0, pull.z);
		double level = flat.length();
		if (level < CLIMB_MIN_FLAT) {
			return;
		}
		flat = flat.scale(1.0 / level);
		Level world = target.level();
		AABB box = target.getBoundingBox();
		AABB ahead = box.move(flat.scale(PROBE));
		if (world.noCollision(target, ahead)) {
			// nothing in the way
			return;
		}
		// how tall the face is: the least lift that clears the way ahead, with the room above it to rise into
		double step = target instanceof LivingEntity living && living.getAttributes().hasAttribute(Attributes.STEP_HEIGHT)
			? living.getAttributeValue(Attributes.STEP_HEIGHT) : 0.6;
		double lift = -1.0;
		for (double up = CLIMB_STEP; up <= CLIMB_HEIGHT + 1.0E-3; up += CLIMB_STEP) {
			if (!world.noCollision(target, box.move(0.0, up, 0.0))) {
				// a ceiling over it: no way up here
				return;
			}
			if (world.noCollision(target, ahead.move(0.0, up, 0.0))) {
				lift = up;
				break;
			}
		}
		if (lift < 0.0 || target.onGround() && lift <= step) {
			// too tall to climb, or (on its feet) low enough to step up on its own; partway up a face it's hauled on
			return;
		}
		Vec3 v = target.getDeltaMovement();
		// nearly over the lip: shoved over it; else hauled up the face, pressed into it
		double over = lift <= CLIMB_STEP * 2.0 ? CLIMB_OVER : CLIMB_HUG;
		Vec3 want = new Vec3(v.x + flat.x * over, Math.max(v.y, CLIMB_SPEED), v.z + flat.z * over);
		target.setDeltaMovement(want);
		target.needsSync = true;
		target.syncVelocity = true;
		target.resetFallDistance();
	}
}
