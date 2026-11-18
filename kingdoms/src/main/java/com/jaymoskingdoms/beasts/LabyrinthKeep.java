package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Loaded;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the labyrinth-builders left in a labyrinth, set out once for it when a player first comes near
 * ({@link Labyrinths}, as its minotaur is; the dungeon itself is not changed):
 * <ul>
 * <li><b>Braziers</b> in every hall, some of them living ({@link BrazierMimic}, {@link #MIMIC_SHARE}).</li>
 * <li>The minotaur's hall shut behind <b>rune gates</b> ({@link RuneGateBlock}) across its doorways, the three <b>rune
 * tablets</b> that open them each in a chest in the other halls, and the <b>Afterblow</b> on a pedestal in the hall
 * ({@link Afterblow}). Not done if a player is already in the hall, if the hall has no doorways that can be shut, or if
 * the other halls have no room for the tablets: the minotaur then keeps an open hall.</li>
 * </ul>
 */
final class LabyrinthKeep {
	/** Braziers tried for in each hall, and the share of them that are living. */
	static final int BRAZIERS = 2;
	static final float MIMIC_SHARE = 0.35F;
	/** The most halls furnished with braziers, and the tries to find a clear place in one. */
	static final int MOST_HALLS = 10, SPOT_TRIES = 40;
	/** The largest doorway a gate shuts (blocks across, and high), and the most blocks of gate in a hall. */
	static final int DOOR_WIDE = 7, DOOR_HIGH = 7, MOST_GATE = 300;
	/** How much of a wall must be solid for it to be the wall (the rest being doorways). */
	static final double WALL_SOLID = 0.4;
	/** How near the minotaur or each other the tablets' chests and the pedestal keep (blocks, squared). */
	static final double APART_SQR = 16.0;

	private LabyrinthKeep() {
	}

	/** Sets out the braziers in {@code halls} and the gates, tablets and the Afterblow for {@code lair} (the minotaur's hall, stood at {@code minotaur}). */
	static void furnish(ServerLevel level, List<BoundingBox> halls, BoundingBox lair, BlockPos minotaur) {
		RandomSource random = level.getRandom();
		List<BlockPos> taken = new ArrayList<>();
		taken.add(minotaur);
		int furnished = 0;
		for (BoundingBox hall : halls) {
			if (furnished++ >= MOST_HALLS || !Loaded.made(level, hall.minX(), hall.minZ(), hall.maxX(), hall.maxZ())) {
				continue;
			}
			for (int i = 0; i < BRAZIERS; i++) {
				BlockPos spot = spot(level, hall, random, 3, taken);
				if (spot != null) {
					taken.add(spot);
					brazier(level, spot, random.nextFloat() < MIMIC_SHARE);
				}
			}
		}
		gate(level, halls, lair, random, taken);
	}

	/** A brazier: a pillar of brick with a fire lit on it, or the same fire living. */
	private static void brazier(ServerLevel level, BlockPos spot, boolean living) {
		if (living) {
			BrazierMimic mimic = LabyrinthCraft.BRAZIER_MIMIC.create(level, EntitySpawnReason.STRUCTURE);
			if (mimic != null) {
				mimic.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
				mimic.setPersistenceRequired();
				level.addFreshEntity(mimic);
			}
			return;
		}
		level.setBlock(spot, Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
		level.setBlock(spot.above(), Blocks.CAMPFIRE.defaultBlockState(), 2);
	}

	private static void gate(ServerLevel level, List<BoundingBox> halls, BoundingBox lair, RandomSource random, List<BlockPos> taken) {
		if (!Loaded.made(level, lair.minX() - 1, lair.minZ() - 1, lair.maxX() + 1, lair.maxZ() + 1)
			|| level.players().stream().anyMatch(player -> lair.isInside(player.blockPosition()))) {
			return;
		}
		List<BoundingBox> others = new ArrayList<>();
		for (BoundingBox hall : halls) {
			if (!hall.intersects(lair) && Loaded.made(level, hall.minX(), hall.minZ(), hall.maxX(), hall.maxZ())) {
				others.add(hall);
			}
		}
		List<BlockPos> doorways = doorways(level, lair);
		BlockPos pedestal = spot(level, lair, random, 3, taken);
		if (others.isEmpty() || doorways.isEmpty() || pedestal == null) {
			return;
		}
		taken.add(pedestal);
		List<BlockPos> chests = new ArrayList<>();
		for (int i = 0; i < LabyrinthCraft.TABLETS.length; i++) {
			BlockPos spot = null;
			for (int tries = 0; tries < others.size() && spot == null; tries++) {
				spot = spot(level, others.get((i + tries) % others.size()), random, 2, taken);
			}
			if (spot == null) {
				return;
			}
			taken.add(spot);
			chests.add(spot);
		}
		for (int i = 0; i < chests.size(); i++) {
			BlockPos at = chests.get(i);
			BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.from2DDataValue(random.nextInt(4)));
			level.setBlock(at, chest, 2);
			if (level.getBlockEntity(at) instanceof ChestBlockEntity entity) {
				entity.setItem(0, new ItemStack(LabyrinthCraft.TABLETS[i]));
			}
		}
		level.setBlock(pedestal, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
		ItemEntity prize = new ItemEntity(level, pedestal.getX() + 0.5, pedestal.getY() + 1.2, pedestal.getZ() + 0.5, new ItemStack(LabyrinthCraft.AFTERBLOW));
		prize.setNoGravity(true);
		prize.setDeltaMovement(Vec3.ZERO);
		prize.setUnlimitedLifetime();
		prize.setGlowingTag(true);
		level.addFreshEntity(prize);
		for (BlockPos at : doorways) {
			level.setBlock(at, LabyrinthCraft.RUNE_GATE.defaultBlockState(), 2);
		}
	}

	/** A clear floor in {@code hall} with {@code height} blocks of air above and no sky, away from {@code taken}, or null. */
	private static @Nullable BlockPos spot(ServerLevel level, BoundingBox hall, RandomSource random, int height, List<BlockPos> taken) {
		if (hall.getXSpan() < 3 || hall.getZSpan() < 3) {
			return null;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i < SPOT_TRIES; i++) {
			int x = hall.minX() + 1 + random.nextInt(hall.getXSpan() - 2), z = hall.minZ() + 1 + random.nextInt(hall.getZSpan() - 2);
			for (int y = hall.minY(); y <= hall.maxY() - height + 1; y++) {
				if (!clear(level, at.set(x, y, z), height)) {
					continue;
				}
				BlockPos found = new BlockPos(x, y, z);
				if (taken.stream().noneMatch(other -> other.distSqr(found) < APART_SQR)) {
					return found;
				}
				break;
			}
		}
		return null;
	}

	private static boolean clear(ServerLevel level, BlockPos.MutableBlockPos at, int height) {
		int x = at.getX(), y = at.getY(), z = at.getZ();
		if (!level.getBlockState(at.set(x, y - 1, z)).isFaceSturdy(level, at, Direction.UP) || level.canSeeSky(at.set(x, y, z))) {
			return false;
		}
		for (int h = 0; h < height; h++) {
			BlockState state = level.getBlockState(at.set(x, y + h, z));
			if (!state.isAir()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The cells of {@code hall}'s doorways: on each side, the wall (the box's face or the layer just outside it, whichever is
	 * more solid) is read as a grid, and each hole in it no bigger than a gate's {@link #DOOR_WIDE} by {@link #DOOR_HIGH}
	 * that opens through to both sides is a doorway. Empty if the hall has none, or too many cells.
	 */
	private static List<BlockPos> doorways(ServerLevel level, BoundingBox hall) {
		List<BlockPos> cells = new ArrayList<>();
		for (Direction side : Direction.Plane.HORIZONTAL) {
			boolean alongX = side.getAxis() == Direction.Axis.Z;
			int from = alongX ? hall.minX() : hall.minZ(), across = (alongX ? hall.getXSpan() : hall.getZSpan()), high = hall.getYSpan();
			boolean[][] best = null;
			int bestLayer = 0;
			double bestSolid = 0.0;
			for (int out = 0; out <= 1; out++) {
				int layer = (switch (side) {
					case NORTH -> hall.minZ();
					case SOUTH -> hall.maxZ();
					case WEST -> hall.minX();
					default -> hall.maxX();
				}) + out * side.getStepX() + out * side.getStepZ();
				boolean[][] open = new boolean[across][high];
				int solid = 0;
				for (int t = 0; t < across; t++) {
					for (int y = 0; y < high; y++) {
						open[t][y] = passable(level, cell(alongX, from + t, hall.minY() + y, layer));
						solid += open[t][y] ? 0 : 1;
					}
				}
				double share = solid / (double) (across * high);
				if (share > bestSolid) {
					bestSolid = share;
					best = open;
					bestLayer = layer;
				}
			}
			if (best == null || bestSolid < WALL_SOLID) {
				continue;
			}
			boolean[][] seen = new boolean[across][high];
			for (int t = 0; t < across; t++) {
				for (int y = 0; y < high; y++) {
					if (!best[t][y] || seen[t][y]) {
						continue;
					}
					List<int[]> hole = new ArrayList<>();
					ArrayDeque<int[]> queue = new ArrayDeque<>();
					queue.add(new int[] {t, y});
					seen[t][y] = true;
					while (!queue.isEmpty()) {
						int[] c = queue.poll();
						hole.add(c);
						for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							int nt = c[0] + d[0], ny = c[1] + d[1];
							if (nt >= 0 && nt < across && ny >= 0 && ny < high && best[nt][ny] && !seen[nt][ny]) {
								seen[nt][ny] = true;
								queue.add(new int[] {nt, ny});
							}
						}
					}
					if (door(level, hall, hole, side, alongX, from, bestLayer)) {
						for (int[] c : hole) {
							cells.add(cell(alongX, from + c[0], hall.minY() + c[1], bestLayer));
						}
					}
				}
			}
		}
		return cells.size() > MOST_GATE ? List.of() : cells;
	}

	/** Whether {@code hole} (cells of a wall, as across and up) is a doorway: small enough, and open through the wall on some cell. */
	private static boolean door(ServerLevel level, BoundingBox hall, List<int[]> hole, Direction side, boolean alongX, int from, int layer) {
		int minT = Integer.MAX_VALUE, maxT = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		boolean through = false;
		for (int[] c : hole) {
			minT = Math.min(minT, c[0]);
			maxT = Math.max(maxT, c[0]);
			minY = Math.min(minY, c[1]);
			maxY = Math.max(maxY, c[1]);
			BlockPos at = cell(alongX, from + c[0], hall.minY() + c[1], layer);
			through |= passable(level, at.relative(side)) && passable(level, at.relative(side.getOpposite()));
		}
		return through && maxT - minT < DOOR_WIDE && maxY - minY < DOOR_HIGH;
	}

	private static BlockPos cell(boolean alongX, int t, int y, int layer) {
		return alongX ? new BlockPos(t, y, layer) : new BlockPos(layer, y, t);
	}

	private static boolean passable(ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		return state.getCollisionShape(level, at).isEmpty() && state.getFluidState().isEmpty();
	}
}
