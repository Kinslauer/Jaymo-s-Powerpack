package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebBridges;
import com.jaymospowers.power.web.WebNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Perch: landing on a fence post, a wall top, a ledge's edge, a webline plank or any similar narrow top settles the
 * slinger into a balanced crouch, arms out, with no sliding off: knocks and the slope of a stray push are cancelled
 * until they move, jump, or step off. Everyone sees the crouch ({@code WebPower#PERCH}); Spider-Sense reaches further
 * from a perch (server side). Only the local player's own motion is touched here.
 */
final class WebPerchClient {
	/** A top this narrow or narrower (blocks, either way across) counts as a post, wall or rail. */
	static final double NARROW = 0.75;
	/** A corner of the feet over air this deep (blocks) counts the spot as a ledge's edge. */
	static final int DROP = 2;

	private boolean wasGround = true;
	private boolean on;

	boolean on() {
		return on;
	}

	void tick(LocalPlayer player, boolean swinging) {
		Input in = player.input.keyPresses;
		boolean ground = player.onGround();
		boolean landed = ground && !wasGround;
		wasGround = ground;
		boolean moving = in.forward() || in.backward() || in.left() || in.right() || in.jump() || in.sprint();
		if (on) {
			if (!ground || moving || swinging || player.isInWater() || player.isPassenger()) {
				set(false);
				return;
			}
			// held fast: nothing slides it off
			Vec3 v = player.getDeltaMovement();
			if (v.x != 0.0 || v.z != 0.0) {
				player.setDeltaMovement(0.0, v.y, 0.0);
			}
			return;
		}
		if (landed && !moving && !swinging && !player.isShiftKeyDown() && !player.isInWater() && !player.isPassenger() && narrow(player)) {
			set(true);
		}
	}

	void reset() {
		wasGround = true;
		if (on) {
			set(false);
		}
	}

	private void set(boolean value) {
		on = value;
		ClientPlayNetworking.send(new WebNet.Perch(value));
	}

	/** Whether the feet are on something narrow, a webline, or the edge of a drop. */
	private static boolean narrow(LocalPlayer player) {
		Level level = player.level();
		BlockPos under = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
		BlockState state = level.getBlockState(under);
		if (state.is(WebBridges.BRIDGE)) {
			return true;
		}
		VoxelShape shape = state.getCollisionShape(level, under);
		if (!shape.isEmpty()) {
			AABB b = shape.bounds();
			if (b.getXsize() <= NARROW || b.getZsize() <= NARROW) {
				return true;
			}
		}
		// a ledge's edge: a corner of the feet over open air, a good drop below
		AABB box = player.getBoundingBox();
		double y = box.minY - 0.05;
		double[][] corners = {{box.minX, box.minZ}, {box.minX, box.maxZ}, {box.maxX, box.minZ}, {box.maxX, box.maxZ}};
		for (double[] c : corners) {
			BlockPos at = BlockPos.containing(c[0], y, c[1]);
			boolean open = true;
			for (int i = 0; i <= DROP; i++) {
				BlockPos q = at.below(i);
				if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
					open = false;
					break;
				}
			}
			if (open) {
				return true;
			}
		}
		return false;
	}
}
