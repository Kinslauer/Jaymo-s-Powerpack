package com.jaymospowers.power.web;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.150) Andy: "A takeover that digs you down deep on purpose". When it asks for time deep underground and is ignored
 * ({@link WebSymbioteMind}), it takes the wheel and tunnels you down a spiral stair, then lets go once you're deep.
 * <p>
 * The stair winds round a pillar one block thick: the 3x3 ring of cells round {@code centre} (eight cells, each a step
 * down from the last, so a full turn drops eight blocks), three blocks high so you walk it upright. Only the ring is
 * cut, the pillar stays, so the shaft is three blocks across however deep. Starting from where you stand (the ring's
 * first cell), it keeps {@code LEAD} steps cut ahead of your feet, one step every {@code CARVE_EVERY} ticks, and keeps a
 * marker at the newest step, which is what the takeover's path code ({@link WebTakeoverPath}) walks you to. It floors a
 * cell that has no floor, takes out gravel and sand that would fall in behind the cut, and never touches a block it
 * couldn't rip out for a takeover anyway (block entities, unbreakable, harder than {@code MAX_HARDNESS}, or one you
 * may not break). Water or lava on the way, or anything it can't cut, and it stops and lets go.
 * <p>
 * The takeover itself (the marker, the hold, the path) is set up in {@code WebSymbiote.mindDig}; this class does the cutting.
 */
final class WebSymbioteDig {
	/** The takeover's hold, in ticks (the stair takes a minute or so at most). */
	static final int LIMIT_TICKS = 1200;
	/** It lets go once your feet are at or under this height. */
	static final int GOAL_Y = 0;
	/** And never cuts within this many blocks of the bottom of the world. */
	static final int FLOOR_MARGIN = 6;
	/** Steps kept cut ahead of your feet, and ticks between cuts. */
	static final int LEAD = 5, CARVE_EVERY = 3;
	/** Nothing harder than this is cut (as the takeover path's ripping). */
	static final float MAX_HARDNESS = 20.0F;
	/** Falling blocks stacked over a cut that it clears. */
	static final int CLEAR_ABOVE = 4;
	/** The ring, as (forward, right) offsets from the pillar; the first is the cell you start in. */
	private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

	static final int CONTINUE = 0, DONE = 1, BLOCKED = 2;

	private static final class Dig {
		BlockPos centre;
		Direction forward;
		Direction right;
		int startY;
		int carved;
		long nextAt;
		boolean placed;
	}

	private static final Map<UUID, Dig> DIGS = new HashMap<>();

	private WebSymbioteDig() {
	}

	static boolean active(ServerPlayer player) {
		return DIGS.containsKey(player.getUUID());
	}

	static void forget(ServerPlayer player) {
		DIGS.remove(player.getUUID());
	}

	/** Sets a dig going from where {@code player} stands, winding away in the direction they face. */
	static void start(ServerPlayer player) {
		Dig dig = new Dig();
		dig.forward = player.getDirection();
		dig.right = dig.forward.getClockWise();
		BlockPos feet = player.blockPosition();
		dig.startY = feet.getY();
		// you stand in the ring's first cell, (-1, -1): the pillar is one step forward and one to the right
		dig.centre = feet.relative(dig.forward).relative(dig.right).atY(feet.getY());
		dig.carved = 0;
		dig.nextAt = 0L;
		DIGS.put(player.getUUID(), dig);
	}

	/** The foot cell of step {@code k} (0 is where it started). */
	private static BlockPos cell(Dig dig, int k) {
		int[] off = RING[Math.floorMod(k, RING.length)];
		return dig.centre.relative(dig.forward, off[0]).relative(dig.right, off[1]).atY(dig.startY - k);
	}

	/** The lowest foot height it will cut to in this level. */
	private static int bottom(ServerLevel level) {
		return Math.max(GOAL_Y, level.getMinY() + FLOOR_MARGIN);
	}

	/**
	 * One tick of the dig: cut the next step if it's time and there are fewer than {@code LEAD} ahead of your feet, move
	 * the marker (the path's goal) to the newest step. {@code DONE} once your feet are deep enough, {@code BLOCKED} if it
	 * can't go on.
	 */
	static int tick(ServerPlayer player, Entity marker, long now) {
		Dig dig = DIGS.get(player.getUUID());
		if (dig == null) {
			return BLOCKED;
		}
		ServerLevel level = player.level();
		int goal = bottom(level);
		int feetY = player.blockPosition().getY();
		if (feetY <= goal) {
			return DONE;
		}
		int descended = Math.max(0, dig.startY - feetY);
		int last = dig.startY - goal;
		if (dig.carved < last && dig.carved < descended + LEAD && now >= dig.nextAt) {
			if (!carve(player, level, dig, dig.carved + 1)) {
				return BLOCKED;
			}
			dig.carved++;
			dig.nextAt = now + CARVE_EVERY;
			dig.placed = false;
		}
		if (!dig.placed || marker.getY() != cell(dig, dig.carved).getY()) {
			BlockPos at = cell(dig, dig.carved);
			marker.setPos(Vec3.atBottomCenterOf(at));
			dig.placed = true;
		}
		return CONTINUE;
	}

	/** Cuts step {@code k}: its three-block column, a floor if it has none. False if something is in the way. */
	private static boolean carve(ServerPlayer player, ServerLevel level, Dig dig, int k) {
		BlockPos foot = cell(dig, k);
		if (!level.hasChunkAt(foot)) {
			return false;
		}
		// no water or lava in it, under it, round it or over it
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					if (!level.getFluidState(foot.offset(dx, dy, dz)).isEmpty()) {
						return false;
					}
				}
			}
		}
		for (int dy = 0; dy < 3; dy++) {
			BlockPos at = foot.above(dy);
			BlockState state = level.getBlockState(at);
			if (state.isAir()) {
				continue;
			}
			if (!breakable(player, level, at, state)) {
				return false;
			}
		}
		for (int dy = 0; dy < 3; dy++) {
			BlockPos at = foot.above(dy);
			BlockState state = level.getBlockState(at);
			if (state.isAir()) {
				continue;
			}
			if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, at, state, level.getBlockEntity(at))) {
				// someone's protecting it
				return false;
			}
			level.destroyBlock(at, false, player, 512);
			PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, at, state, null);
		}
		// sand and gravel overhead would pour in behind
		for (int dy = 3; dy < 3 + CLEAR_ABOVE; dy++) {
			BlockPos at = foot.above(dy);
			if (!(level.getBlockState(at).getBlock() instanceof FallingBlock)) {
				break;
			}
			level.destroyBlock(at, false, player, 512);
		}
		// a floor to stand on
		BlockPos floor = foot.below();
		BlockState under = level.getBlockState(floor);
		if (under.isAir() || under.getCollisionShape(level, floor).isEmpty()) {
			level.setBlock(floor, (foot.getY() < 0 ? Blocks.COBBLED_DEEPSLATE : Blocks.COBBLESTONE).defaultBlockState(), 3);
		}
		level.playSound(null, foot, level.getBlockState(floor).getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8F, 0.6F);
		return true;
	}

	/** Solid, not too hard, no block entity or fluid, and you'd be allowed to break it. */
	private static boolean breakable(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || hardness > MAX_HARDNESS) {
			return false;
		}
		return player.mayInteract(level, pos) && !player.blockActionRestricted(level, pos, player.gameMode());
	}
}
