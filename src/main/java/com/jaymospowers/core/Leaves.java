package com.jaymospowers.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;

/**
 * Realistic leaves, for everyone (our own take on The Realistic Leaves Mod, which only exists for 1.7.10 on PML;
 * nothing of it is copied): leaves are soft. Players, mobs, animals, items and vehicles push into and through them
 * instead of standing on them, and they drag on you while you're inside, so you sink slowly through a canopy, wade
 * through hedges, and a fall into a tree is cushioned. Hold Jump inside leaves to climb up through them. Arrows and
 * other projectiles still hit leaves, and falling sand and gravel still land on them.
 *
 * <p>Both sides run the same rules (the collision shape and the drag), so movement stays in step; the rustle and the
 * shaken-loose leaves are drawn on each client ({@code client/LeafRustle}).
 */
public final class Leaves {
	/** Inside leaves, sideways speed is kept at this share each tick: like wading. */
	public static final double SIDEWAYS_DRAG = 0.72;
	/** Falling speed kept each tick inside leaves: you settle down through a canopy at about two blocks a second. */
	public static final double FALL_DRAG = 0.55;
	/** Rising speed kept each tick (a jump inside leaves doesn't go far). */
	public static final double RISE_DRAG = 0.8;
	/** Holding Jump inside leaves climbs at this speed (blocks a tick). */
	public static final double CLIMB_SPEED = 0.13;
	/** Share of the fall so far kept each tick inside leaves, so a drop through branches hurts much less. */
	public static final double CUSHION = 0.5;

	/** Something an entity remembers: the last tick leaves dragged on it (one drag a tick, however many it touches). */
	public interface Dragged {
		int jaymospowers$leafTick();

		void jaymospowers$leafTick(int tick);
	}

	/** Lets us read whether a living thing is holding Jump. */
	public interface Jumper {
		boolean jaymospowers$jumping();
	}

	private Leaves() {
	}

	/** Any leaves, modded ones too (checked on every collision, so a class check rather than a tag lookup). */
	public static boolean is(BlockState state) {
		return state.getBlock() instanceof LeavesBlock;
	}

	/** Whether this collision is an entity that goes through leaves (not a projectile, falling block, or no entity at all). */
	public static boolean passesThrough(CollisionContext context) {
		if (!(context instanceof EntityCollisionContext entityContext)) {
			return false;
		}
		Entity entity = entityContext.getEntity();
		return entity != null && passesThrough(entity);
	}

	public static boolean passesThrough(Entity entity) {
		return !(entity instanceof Projectile) && !(entity instanceof FallingBlockEntity) && !entity.noPhysics;
	}

	/** An entity is inside a leaves block this tick: drag on it once, cushion its fall, and let it climb if it jumps. */
	public static void inside(Entity entity) {
		if (!passesThrough(entity) || entity.isSpectator() || entity instanceof Player player && player.getAbilities().flying) {
			return;
		}
		Dragged dragged = (Dragged)entity;
		if (dragged.jaymospowers$leafTick() == entity.tickCount) {
			return;
		}
		dragged.jaymospowers$leafTick(entity.tickCount);
		Vec3 v = entity.getDeltaMovement();
		double y = v.y * (v.y < 0.0 ? FALL_DRAG : RISE_DRAG);
		if (entity instanceof LivingEntity && ((Jumper)entity).jaymospowers$jumping()) {
			y = Math.max(y, CLIMB_SPEED);
		}
		entity.setDeltaMovement(v.x * SIDEWAYS_DRAG, y, v.z * SIDEWAYS_DRAG);
		entity.fallDistance *= CUSHION;
	}
}
