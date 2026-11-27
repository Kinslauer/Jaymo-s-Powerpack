package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The taker's cellar: a maze dug under a vampire taker's house ({@link Takers}), instead of a dimension.
 * <p><b>Where and why.</b> It is dug the moment a village's taker is chosen, under the bed's room: a hatch of iron (a floor
 * trapdoor beside the bed that never opens by hand: the cellar "always locked") over a ladder shaft down to a {@value #SIZE}-block
 * square maze of stone brick, three blocks high, its corridors one wide, centred under the hatch. A taker keeps its victims where
 * no neighbour would dig: under its own floor, below the village's cellars.
 * <p><b>What's in it.</b> The farthest dead end holds a chest: a silver sword and two draughts of the purifying kind (else
 * golden apples). The next {@link #ROOMS} dead ends are side rooms behind a spruce door; one starts with a captive, the others
 * wait for whoever the taker drags down ({@link #drag}): a villager is chained there ({@link Captives#hold}), a player shackled
 * ({@link StoryHard#shackle}, no camp). Both are freed as any captive is.
 */
final class TakersCellar {
	/** The maze is this many cells a side, each cell a block, with a wall block between: (2 n + 1) blocks across. */
	static final int CELLS = 7, SIZE = 2 * CELLS + 1;
	/** Floor to ceiling inside (blocks), how deep under the hatch the floor lies, and the lowest the floor may go above the world's bottom. */
	static final int HEIGHT = 2, DEPTH = 8, ABOVE_BOTTOM = 5;
	/** Side rooms behind a door, and how many of them start with a captive. */
	static final int ROOMS = 3, PREFILLED = 1;
	/** A dragged victim's health is brought up to at least this. */
	static final float DRAGGED_HEALTH = 8.0F;
	/** Stone brick wall variants: the chance of a mossy and of a cracked block. */
	static final float MOSSY = 0.12F, CRACKED = 0.12F;
	static final Identifier DRAUGHT = Identifier.fromNamespaceAndPath("jaymospowers", "purifying_draught");
	/** What shackled players in a cellar are held by: no camp. */
	static final int NO_CAMP = -2;

	private TakersCellar() {
	}

	/** Digs the cellar under {@code home} and returns the taker's record (no hatch when it can't be dug: not loaded, no floor). */
	static Takers.Taker dig(ServerLevel level, Faction village, PathfinderMob who, BlockPos home) {
		Takers.Taker none = new Takers.Taker(true, 0L, List.of(), village.id());
		BlockPos spot = hatchSpot(level, home);
		if (spot == null) {
			return none;
		}
		int floorY = spot.getY() - 1;
		int y0 = Math.max(level.getMinY() + ABOVE_BOTTOM, floorY - DEPTH);
		int ox = spot.getX() - CELLS, oz = spot.getZ() - CELLS;
		for (int dx : new int[] {0, SIZE - 1}) {
			for (int dz : new int[] {0, SIZE - 1}) {
				if (!level.isLoaded(new BlockPos(ox + dx, y0, oz + dz))) {
					return none;
				}
			}
		}
		RandomSource random = level.getRandom();
		boolean[][] air = carve(random);
		int c = CELLS;
		// (the shaft's ladder needs the centre cell's east wall)
		air[c + 1][c] = false;
		// the whole block of stone brick, then the corridors cut out of it
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				for (int y = 0; y <= HEIGHT + 1; y++) {
					boolean open = air[x][z] && y >= 1 && y <= HEIGHT;
					level.setBlock(new BlockPos(ox + x, y0 + y, oz + z), open ? Blocks.AIR.defaultBlockState() : brick(random), 2);
				}
			}
		}
		// the shaft: air up from the ceiling to the hatch, a wall behind it and a ladder up it
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
		for (int y = y0 + HEIGHT + 1; y < floorY; y++) {
			level.setBlock(new BlockPos(spot.getX(), y, spot.getZ()), ladder, 2);
			level.setBlock(new BlockPos(spot.getX() + 1, y, spot.getZ()), Blocks.STONE_BRICKS.defaultBlockState(), 2);
		}
		level.setBlock(new BlockPos(spot.getX(), y0 + HEIGHT + 1, spot.getZ()), ladder, 2);
		level.setBlock(new BlockPos(spot.getX(), floorY, spot.getZ()), Blocks.IRON_TRAPDOOR.defaultBlockState(), 3);
		// dead ends, farthest first
		List<int[]> ends = deadEnds(air, c, c);
		List<Long> rooms = new ArrayList<>();
		for (int i = 0; i < ends.size(); i++) {
			int[] e = ends.get(i);
			BlockPos at = new BlockPos(ox + e[0], y0 + 1, oz + e[1]);
			if (i == 0) {
				cache(level, at);
			} else if (i <= ROOMS) {
				door(level, ox, y0, oz, air, e);
				if (i <= PREFILLED) {
					Captives.take(level, at, village, false);
				} else {
					rooms.add(at.asLong());
				}
			}
		}
		return new Takers.Taker(true, spot.asLong(), rooms, village.id());
	}

	/** A free floor spot beside the bed (or the bed's own) with air above it and a firm floor under. */
	private static @Nullable BlockPos hatchSpot(ServerLevel level, BlockPos home) {
		List<BlockPos> tries = new ArrayList<>();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				tries.add(home.offset(dx, 0, dz));
			}
		}
		tries.sort(java.util.Comparator.comparingInt(p -> p.distManhattan(home)));
		for (BlockPos p : tries) {
			BlockState floor = level.getBlockState(p.below());
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && floor.isSolid() && floor.getBlock() != Blocks.BEDROCK
				&& !(floor.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
				return p;
			}
		}
		return null;
	}

	private static BlockState brick(RandomSource random) {
		float r = random.nextFloat();
		return (r < MOSSY ? Blocks.MOSSY_STONE_BRICKS : r < MOSSY + CRACKED ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
	}

	/** A perfect maze by depth-first carving: the open blocks of a {@link #SIZE} square (cells at odd coordinates). */
	private static boolean[][] carve(RandomSource random) {
		boolean[][] air = new boolean[SIZE][SIZE];
		boolean[][] seen = new boolean[CELLS][CELLS];
		ArrayDeque<int[]> stack = new ArrayDeque<>();
		int[] start = {CELLS / 2, CELLS / 2};
		seen[start[0]][start[1]] = true;
		air[1 + 2 * start[0]][1 + 2 * start[1]] = true;
		stack.push(start);
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!stack.isEmpty()) {
			int[] cell = stack.peek();
			List<int[]> next = new ArrayList<>();
			for (int[] d : dirs) {
				int nx = cell[0] + d[0], nz = cell[1] + d[1];
				if (nx >= 0 && nz >= 0 && nx < CELLS && nz < CELLS && !seen[nx][nz]) {
					next.add(new int[] {nx, nz, d[0], d[1]});
				}
			}
			if (next.isEmpty()) {
				stack.pop();
				continue;
			}
			int[] n = next.get(random.nextInt(next.size()));
			seen[n[0]][n[1]] = true;
			air[1 + 2 * n[0]][1 + 2 * n[1]] = true;
			air[1 + 2 * cell[0] + n[2]][1 + 2 * cell[1] + n[3]] = true;
			stack.push(new int[] {n[0], n[1]});
		}
		return air;
	}

	/** The dead-end cells (block coordinates in the maze), farthest from the entry first. {@code entryX}, {@code entryZ}: the entry cell's block. */
	private static List<int[]> deadEnds(boolean[][] air, int entryX, int entryZ) {
		int[][] dist = new int[SIZE][SIZE];
		for (int[] row : dist) {
			java.util.Arrays.fill(row, -1);
		}
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {entryX, entryZ});
		dist[entryX][entryZ] = 0;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		List<int[]> ends = new ArrayList<>();
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			int open = 0;
			for (int[] d : dirs) {
				int x = p[0] + d[0], z = p[1] + d[1];
				if (x >= 0 && z >= 0 && x < SIZE && z < SIZE && air[x][z]) {
					open++;
					if (dist[x][z] < 0) {
						dist[x][z] = dist[p[0]][p[1]] + 1;
						queue.add(new int[] {x, z});
					}
				}
			}
			if (open == 1 && p[0] % 2 == 1 && p[1] % 2 == 1 && !(p[0] == entryX && p[1] == entryZ)) {
				ends.add(new int[] {p[0], p[1], dist[p[0]][p[1]]});
			}
		}
		ends.sort((a, b) -> Integer.compare(b[2], a[2]));
		return ends;
	}

	/** The cache: a chest with a silver blade and two draughts. */
	private static void cache(ServerLevel level, BlockPos at) {
		level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
		if (level.getBlockEntity(at) instanceof ChestBlockEntity chest) {
			ItemStack blade = com.jaymoskingdoms.monsters.Monsters.SILVER_SWORD == null ? new ItemStack(Items.IRON_SWORD)
				: new ItemStack(com.jaymoskingdoms.monsters.Monsters.SILVER_SWORD);
			chest.setItem(0, blade);
			ItemStack draught = BuiltInRegistries.ITEM.getOptional(DRAUGHT).map(item -> new ItemStack(item, 2)).orElseGet(() -> new ItemStack(Items.GOLDEN_APPLE, 2));
			chest.setItem(1, draught);
		}
	}

	/** A spruce door in the passage that leads into dead end {@code e}. */
	private static void door(ServerLevel level, int ox, int y0, int oz, boolean[][] air, int[] e) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int gx = e[0] + d.getStepX(), gz = e[1] + d.getStepZ();
			if (gx >= 0 && gz >= 0 && gx < SIZE && gz < SIZE && air[gx][gz]) {
				BlockPos low = new BlockPos(ox + gx, y0 + 1, oz + gz);
				BlockState base = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, d);
				level.setBlock(low, base.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
				level.setBlock(low.above(), base.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
				return;
			}
		}
	}

	/** Whether {@code mob}'s cellar has a side room left to hold someone. */
	static boolean hasRoom(PathfinderMob mob) {
		Takers.Taker t = mob.getAttached(Takers.TAKER);
		return t != null && t.vampire() && t.hatch() != 0L && !t.rooms().isEmpty();
	}

	/**
	 * {@code mob}, a vampire taker, drags {@code prey} (downed, bitten) down to a side room of its cellar and holds them there.
	 * False when there's no room to put them (they're left as any vampire's prey).
	 */
	static boolean drag(ServerLevel level, PathfinderMob mob, LivingEntity prey) {
		Takers.Taker t = mob.getAttached(Takers.TAKER);
		if (t == null || !hasRoom(mob)) {
			return false;
		}
		List<Long> rooms = new ArrayList<>(t.rooms());
		BlockPos room = BlockPos.of(rooms.removeFirst());
		mob.setAttached(Takers.TAKER, new Takers.Taker(true, t.hatch(), rooms, t.village()));
		Faction village = Factions.get(level.getServer(), t.village());
		Takers.taken(level, village, prey);
		Vec3 at = Vec3.atBottomCenterOf(room);
		PowersCompat.getUp(level, prey);
		prey.setHealth(Math.max(prey.getHealth(), Math.min(DRAGGED_HEALTH, prey.getMaxHealth())));
		if (prey instanceof ServerPlayer player) {
			player.stopSleeping();
			player.teleportTo(level, at.x, at.y, at.z, Set.<net.minecraft.world.entity.Relative>of(), player.getYRot(), 0.0F, false);
			StoryHard.shackle(player, room, NO_CAMP);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.taker.dragged").withStyle(net.minecraft.ChatFormatting.GRAY));
		} else if (prey instanceof Villager v) {
			v.stopSleeping();
			v.teleportTo(at.x, at.y, at.z);
			if (village != null) {
				Captives.hold(level, v, village);
			}
		} else {
			return false;
		}
		Takers.stained(mob);
		return true;
	}
}
