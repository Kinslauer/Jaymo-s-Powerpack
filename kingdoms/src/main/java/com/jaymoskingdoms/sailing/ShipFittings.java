package com.jaymoskingdoms.sailing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.285) What every ship a crew sails is fitted with as she's laid ({@link Fleet#lay}), whoever drew her:
 * <ul>
 * <li><b>A boarding ladder</b> down each side amidships, from her rail to below her waterline (Andy: "the ship should have
 * a ladder somewhere along the side so you can get back in if you fall over"): a ladder on a batten of her own planking,
 * the rail above it opened for a gangway. Her ladders are climbed as she sails ({@link Ships#climbing}).</li>
 * <li><b>Sweeps</b> for a ship drawn with no oars (Andy: "the captain keeps getting stuck, can there be oars? it needs a
 * way to back up and manuever better"): {@link #SWEEPS} a side in her rail, so her captain can row her astern and round
 * in close water ({@code Corsairs}).</li>
 * </ul>
 * Her frame here is the one she's drawn in: x across (+x east), y up from her keel, her bow +z.
 */
final class ShipFittings {
	/** Sweeps a side, for a ship drawn without oars; how far from her middle (a share of her length) they're set. */
	static final int SWEEPS = 2;
	static final double SWEEP_SPREAD = 0.25;
	/** How far (blocks, fore and aft of her middle) a ladder's place is looked for. */
	static final int LADDER_SEARCH = 8;

	private ShipFittings() {
	}

	/** {@code parts} with her ladders and (if she has none) her sweeps added. */
	static List<Fleet.Part> fit(List<Fleet.Part> parts) {
		if (parts.isEmpty()) {
			return parts;
		}
		Map<BlockPos, Integer> at = new HashMap<>();
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, deck = Integer.MIN_VALUE, top = Integer.MIN_VALUE;
		boolean oars = false;
		for (int i = 0; i < parts.size(); i++) {
			Fleet.Part p = parts.get(i);
			at.put(p.at(), i);
			minZ = Math.min(minZ, p.at().getZ());
			maxZ = Math.max(maxZ, p.at().getZ());
			top = Math.max(top, p.at().getY());
			oars |= p.state().getBlock() instanceof OarBlock;
			if (p.state().getBlock() instanceof HelmBlock) {
				deck = Math.max(deck, p.at().getY());
			}
		}
		if (deck == Integer.MIN_VALUE) {
			deck = top;
		}
		int water = waterline(parts);
		List<Fleet.Part> out = new ArrayList<>(parts);
		// (parts taken out leave a gap in the list, filled in at the end)
		boolean[] gone = new boolean[parts.size()];
		int mid = (minZ + maxZ) / 2, length = maxZ - minZ + 1;
		List<Integer> ladderZ = new ArrayList<>();
		for (int side : new int[] {1, -1}) {
			for (int dz = 0; dz <= LADDER_SEARCH; dz++) {
				Integer z = null;
				for (int z1 : dz == 0 ? new int[] {mid} : new int[] {mid + dz, mid - dz}) {
					if (ladder(parts, at, gone, out, side, z1, deck, water)) {
						z = z1;
						break;
					}
				}
				if (z != null) {
					ladderZ.add(z);
					break;
				}
			}
		}
		if (!oars) {
			for (int side : new int[] {1, -1}) {
				for (int k = 0; k < SWEEPS; k++) {
					int want = mid + (int)Math.round((k == 0 ? 1 : -1) * length * SWEEP_SPREAD);
					for (int dz = 0; dz <= 4; dz++) {
						if (sweep(parts, at, gone, out, side, want + dz, deck, ladderZ) || dz > 0 && sweep(parts, at, gone, out, side, want - dz, deck, ladderZ)) {
							break;
						}
					}
				}
			}
		}
		List<Fleet.Part> kept = new ArrayList<>(out.size());
		for (int i = 0; i < out.size(); i++) {
			if (i >= gone.length || !gone[i]) {
				kept.add(out.get(i));
			}
		}
		return kept;
	}

	/** How high (in her frame) the water comes up her side, as her hull works it out. */
	private static int waterline(List<Fleet.Part> parts) {
		List<Ship.Piece> pieces = new ArrayList<>(parts.size());
		for (Fleet.Part p : parts) {
			pieces.add(new Ship.Piece(Block.getId(p.state()), p.at().getX(), p.at().getY(), p.at().getZ()));
		}
		Hull h = new Hull(pieces);
		return Mth.floor(h.minY + h.draft);
	}

	/** Her outermost hull at {@code z} on {@code side} (x), no higher than {@code deck} + 1; null: none. */
	private static @Nullable Integer edge(List<Fleet.Part> parts, Map<BlockPos, Integer> at, boolean[] gone, int side, int z, int deck) {
		Integer edge = null;
		for (int i = 0; i < parts.size(); i++) {
			Fleet.Part p = parts.get(i);
			if (gone[i] || p.at().getZ() != z || p.at().getY() > deck + 1 || p.state().is(BlockTags.WOOL) || p.state().getBlock() instanceof SailBlock) {
				continue;
			}
			if (edge == null || p.at().getX() * side > edge * side) {
				edge = p.at().getX();
			}
		}
		return edge;
	}

	private static boolean full(BlockState state) {
		return Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
	}

	/** A rail piece, which may give way to a gangway or a sweep: a fence, a wall, a pane, a trapdoor. */
	private static boolean rail(BlockState state) {
		return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(Blocks.IRON_BARS) || state.getBlock() instanceof TrapDoorBlock
			|| state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock;
	}

	/** Her rail at (edge, z): the sill (the full block it stands on) and the rail pieces above it (null: none such). */
	private static int @Nullable [] sill(List<Fleet.Part> parts, Map<BlockPos, Integer> at, boolean[] gone, int edge, int z, int deck) {
		int y = deck + 1;
		while (y >= 0 && find(at, gone, edge, y, z) < 0) {
			y--;
		}
		int railTop = y;
		while (y >= 0) {
			int i = find(at, gone, edge, y, z);
			if (i < 0) {
				return null;
			}
			BlockState state = parts.get(i).state();
			if (full(state)) {
				return new int[] {y, railTop};
			}
			if (!rail(state)) {
				return null;
			}
			y--;
		}
		return null;
	}

	private static int find(Map<BlockPos, Integer> at, boolean[] gone, int x, int y, int z) {
		Integer i = at.get(new BlockPos(x, y, z));
		return i == null || i < gone.length && gone[i] ? -1 : i;
	}

	/** A ladder down her {@code side} at {@code z}, if there's room for one there; whether it went. */
	private static boolean ladder(List<Fleet.Part> parts, Map<BlockPos, Integer> at, boolean[] gone, List<Fleet.Part> out, int side, int z, int deck,
		int water) {
		Integer edge = edge(parts, at, gone, side, z, deck);
		if (edge == null) {
			return false;
		}
		int[] sill = sill(parts, at, gone, edge, z, deck);
		if (sill == null) {
			return false;
		}
		int low = Math.max(0, water - 1), x = edge + side;
		if (low > sill[0]) {
			return false;
		}
		// nothing of her outboard of it, and room to climb out over the sill
		for (int y = low; y <= sill[1] + 2; y++) {
			if (find(at, gone, x, y, z) >= 0) {
				return false;
			}
		}
		BlockState batten = parts.get(find(at, gone, edge, sill[0], z)).state();
		Direction facing = side > 0 ? Direction.EAST : Direction.WEST;
		for (int y = low; y <= sill[0]; y++) {
			if (find(at, gone, edge, y, z) < 0) {
				// her planking let out to carry it where her side falls away
				out.add(new Fleet.Part(new BlockPos(edge, y, z), batten));
				at.put(new BlockPos(edge, y, z), out.size() - 1);
			}
			out.add(new Fleet.Part(new BlockPos(x, y, z), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing)));
			at.put(new BlockPos(x, y, z), out.size() - 1);
		}
		// the gangway: her rail opened over it
		for (int y = sill[0] + 1; y <= sill[1]; y++) {
			int i = find(at, gone, edge, y, z);
			if (i >= 0 && i < gone.length) {
				gone[i] = true;
			}
		}
		return true;
	}

	/** A sweep in her rail on {@code side} at {@code z}, if her rail there will take one (not by a ladder); whether it went. */
	private static boolean sweep(List<Fleet.Part> parts, Map<BlockPos, Integer> at, boolean[] gone, List<Fleet.Part> out, int side, int z, int deck,
		List<Integer> ladders) {
		for (int l : ladders) {
			if (Math.abs(l - z) <= 1) {
				return false;
			}
		}
		Integer edge = edge(parts, at, gone, side, z, deck);
		if (edge == null) {
			return false;
		}
		int[] sill = sill(parts, at, gone, edge, z, deck);
		if (sill == null || sill[1] <= sill[0]) {
			return false;
		}
		int i = find(at, gone, edge, sill[0] + 1, z);
		if (i < 0 || i >= gone.length) {
			return false;
		}
		Direction facing = side > 0 ? Direction.EAST : Direction.WEST;
		out.set(i, new Fleet.Part(new BlockPos(edge, sill[0] + 1, z), Sailing.OAR.defaultBlockState().setValue(OarBlock.FACING, facing)));
		// (nothing of the rail left over it)
		for (int y = sill[0] + 2; y <= sill[1]; y++) {
			int j = find(at, gone, edge, y, z);
			if (j >= 0 && j < gone.length) {
				gone[j] = true;
			}
		}
		return true;
	}
}
