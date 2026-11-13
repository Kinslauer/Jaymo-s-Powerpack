package com.jaymospowers.aether;

import com.jaymospowers.compat.Stacked;
import com.jaymospowers.power.wind.aether.WindAether;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Andy: "you constantly see villagers and entities falling out of the aether". In a One Dimension world the Aether's
 * islands sit right over the Overworld's sky, so a villager or an animal that steps off an island's edge falls the whole
 * way down into the Overworld (and in an Aether of its own, out of the world). Nothing pushed them on purpose: they cut a
 * corner of their path, got jostled in a crowd, slid on quicksoil, were carried by a stream running off the edge or hopped
 * the wrong way. So a mob in the Aether backs off from an edge the way a sneaking player does ({@code mixin/AetherEdgeMixin},
 * on {@code Entity.maybeBackOffFromEdge}): its own move never carries it from standing ground out over a drop of
 * {@link #DROP} blocks or more. Ordinary slopes and steps are left alone (a drop is only checked once there's nothing
 * within {@link #STEP} blocks under the next step).
 * <p>
 * Real falls stay: a blow that knocks it off ({@code hurtTime}), a fling faster than it could walk ({@link #FLUNG}), one
 * already falling, one a player rides or leads on a lead, a flier, and players themselves (they have their own sneak).
 */
public final class EdgeGuard {
	/** How far (blocks) below the next step must be empty for it to count as the island's edge. */
	static final double DROP = 24.0;
	/** Ground within this far (blocks) under the next step is a slope or a step down, not an edge. */
	static final double STEP = 1.5;
	/** Ground within this far (blocks) under the mob now: it's standing (or mid-hop, or swimming) on an island. */
	static final double STANDING = 2.5;
	/** A move faster than this (blocks a tick, across) is a fling, not the mob's own going. */
	static final double FLUNG = 0.9;
	/** Already falling this far (blocks): too late, and not ours to stop. */
	static final double FALLING = 1.0;

	private EdgeGuard() {
	}

	/** {@code delta}, cut back so {@code entity}'s own move doesn't take it off an Aether island's edge. */
	public static Vec3 guard(Entity entity, Vec3 delta, MoverType type) {
		if (!(entity instanceof Mob mob) || entity.level().isClientSide() || type != MoverType.SELF
			|| delta.x == 0.0 && delta.z == 0.0 || mob.hurtTime > 0 || mob.fallDistance >= FALLING
			|| delta.horizontalDistanceSqr() > FLUNG * FLUNG || mob.isNoGravity() || mob.isPassenger()
			|| mob.getControllingPassenger() instanceof Player || mob instanceof Leashable l && l.isLeashed()
			|| mob.getNavigation() instanceof FlyingPathNavigation) {
			return delta;
		}
		if (!emptyBelow(mob, delta.x, delta.z, STEP) || !Stacked.in(mob, WindAether.AETHER) || emptyBelow(mob, 0.0, 0.0, STANDING)) {
			// ground under the next step; or not in the Aether; or not standing on anything to begin with
			return delta;
		}
		double dx = delta.x, dz = delta.z;
		if (dx != 0.0 && edge(mob, dx, 0.0)) {
			dx = 0.0;
		}
		if (dz != 0.0 && edge(mob, 0.0, dz)) {
			dz = 0.0;
		}
		if (dx != 0.0 && dz != 0.0 && edge(mob, dx, dz)) {
			// (only the corner itself hangs over the drop)
			if (Math.abs(dx) > Math.abs(dz)) {
				dz = 0.0;
			} else {
				dx = 0.0;
			}
			if (edge(mob, dx, dz)) {
				dx = 0.0;
				dz = 0.0;
			}
		}
		return dx == delta.x && dz == delta.z ? delta : new Vec3(dx, delta.y, dz);
	}

	/** Moving by (dx, dz) would leave {@code mob} over the edge: nothing under it within {@link #DROP}. */
	private static boolean edge(Mob mob, double dx, double dz) {
		return (dx != 0.0 || dz != 0.0) && emptyBelow(mob, dx, dz, STEP) && emptyBelow(mob, dx, dz, DROP);
	}

	/** Nothing to stand on within {@code depth} blocks under {@code mob}'s feet, moved by (dx, dz) (as vanilla's sneak checks it). */
	private static boolean emptyBelow(Mob mob, double dx, double dz, double depth) {
		AABB box = mob.getBoundingBox();
		return mob.level().noCollision(mob, new AABB(box.minX + 1.0E-7 + dx, box.minY - depth - 1.0E-7, box.minZ + 1.0E-7 + dz,
			box.maxX - 1.0E-7 + dx, box.minY, box.maxZ - 1.0E-7 + dz));
	}
}
