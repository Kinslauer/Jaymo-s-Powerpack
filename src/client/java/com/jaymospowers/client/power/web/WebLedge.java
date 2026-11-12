package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Ledge launch: a Zip aimed at a roof edge or ledge (a block face with open air above its top, within a block or so of
 * where the web stuck) does not stop at the wall. It hauls you all the way up and throws you over the lip, high enough
 * that the web wings can open straight away. Only the local player's own movement; everyone else just sees the jump.
 */
final class WebLedge {
	/** How far above the aim point (blocks) the ledge's top may be, and how far below it still counts as the ledge. */
	static final double REACH_UP = 2.0, REACH_DOWN = 0.6;
	/** Height (blocks) the throw carries you above the ledge's top, so the wings have room to open. */
	static final double CLEARANCE = 3.2;
	/** Upward speed (blocks/tick): the least and the most the throw gives. */
	static final double MIN_UP = 0.62, MAX_UP = 1.3;
	/** Gravity the throw is figured against (vanilla, per tick). */
	static final double GRAVITY = 0.08;
	/** Push (blocks/tick) over the lip, into the roof. */
	static final double OVER = 0.2;

	private WebLedge() {
	}

	/** The velocity to throw {@code player} with at the end of {@code pull}, or null when it is not aimed at a ledge. */
	static @Nullable Vec3 launch(LocalPlayer player, WebNet.Pull pull) {
		if (pull.entityId() >= 0 || pull.face() < 0 || pull.face() >= Direction.values().length) {
			return null;
		}
		Direction face = Direction.values()[pull.face()];
		Level level = player.level();
		Vec3 over;
		double top;
		if (face.getAxis().isHorizontal()) {
			BlockPos wall = BlockPos.containing(pull.anchor().subtract(face.getUnitVec3().scale(0.05)));
			top = Double.NaN;
			for (int k = -1; k <= 2; k++) {
				BlockPos at = wall.above(k);
				if (solid(level, at) && open(level, at.above()) && open(level, at.above(2))) {
					top = at.getY() + 1.0;
					break;
				}
			}
			if (Double.isNaN(top)) {
				return null;
			}
			over = face.getUnitVec3().scale(-1.0);
		} else if (face == Direction.UP) {
			BlockPos under = BlockPos.containing(pull.anchor().subtract(0.0, 0.05, 0.0));
			if (!open(level, under.above()) || !open(level, under.above(2))) {
				return null;
			}
			// an edge: at least one side of it drops away
			boolean edge = false;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (open(level, under.relative(d)) && open(level, under.relative(d).below())) {
					edge = true;
				}
			}
			if (!edge) {
				return null;
			}
			top = under.getY() + 1.0;
			over = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			if (over.lengthSqr() < 1.0E-4) {
				return null;
			}
			over = over.normalize();
		} else {
			return null;
		}
		double rise = top - pull.anchor().y;
		if (rise > REACH_UP || rise < -REACH_DOWN) {
			return null;
		}
		double height = Math.max(0.0, top - player.getY()) + CLEARANCE;
		double up = Math.max(MIN_UP, Math.min(MAX_UP, Math.sqrt(2.0 * GRAVITY * height) + 0.08));
		return new Vec3(over.x * OVER, up, over.z * OVER);
	}

	private static boolean solid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static boolean open(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
