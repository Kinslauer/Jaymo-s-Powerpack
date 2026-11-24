package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * The maze under a great necromancer's crypt ({@link Necropolis}). Once a crypt stands and its land is loaded, a level of
 * stone passages is cut {@link #DROP} blocks under its hall: a perfect maze ({@link MazeGen}) of {@link #CELLS} by
 * {@link #CELLS} cells, three-high walls of the crypt's own stone, reached by a spiral stair in the hall's north-west corner
 * (round the brazier that stands there, clear of the hatch shaft on the east wall and of the soul urn, which lies tens of
 * blocks off), with a heart room at its middle: a chest ({@code chests/crypt_maze}: bones, candles, a little gold, now and
 * then an enchanted book) among soul lanterns.
 * <p>
 * The dead who were builders still lay stones without thinking, and here they never stopped: at night, while no player
 * stands in the maze, every {@link #SHIFT_TICKS} the walls are taken down and laid again from a new seed (the crypt's id and
 * the turn), and it is always a perfect maze from the stair to the heart. Players near but outside hear the stone grind. No
 * wall is laid where something stands, and nothing is touched in land that isn't loaded. When the necromancer is destroyed
 * ({@link Necropolis.Crypt#done}) the walls stay as they are.
 * <p>
 * Each crypt's maze is written down ({@link #MAZES}: where its box is, how many turns it has taken and when the last was),
 * so it is the same maze after a restart. A crypt whose ground won't take a maze (caves, water or other works in the way)
 * is written down with turn -1 and is never tried again.
 */
public final class CryptMaze {
	// ---- tuning ----
	/** The maze is this many cells a side (19 blocks); its heart room starts at this cell and is this wide; the stair's room is this wide, in the corner. */
	static final int CELLS = 9, HEART_AT = 3, HEART_SIZE = 3, STAIR_SIZE = 2;
	/** Blocks along a side of the box. */
	static final int SIZE = 2 * CELLS + 1;
	/** How far under the hall's floor the maze's floor lies, and how high its walls stand. */
	static final int DROP = 8, WALL_HEIGHT = 3;
	/** The first cell of the walk (the cell the stair's door opens on), and that door's block, in box coordinates. */
	static final int ROOT_X = 2, ROOT_Z = 1, DOOR_X = 4, DOOR_Z = 3;
	/** The heart room's blocks, in box coordinates (its first and last). */
	static final int HEART_LO = 2 * HEART_AT + 1, HEART_HI = 2 * (HEART_AT + HEART_SIZE - 1) + 1;
	/** How often the crypts are looked at (ticks), and how long between two turns of the walls. */
	static final int CHECK = 40;
	static final long SHIFT_TICKS = 3000L;
	/** How loud the grinding is (a sound is heard about 16 blocks per unit of volume). */
	static final float GRIND_VOLUME = 4.0F;
	/** The share of the box that may be air already (small caves are fine), and the world's lowest floor allowed (above the bottom). */
	static final float AIR_ALLOWED = 0.25F;
	static final int MIN_ABOVE_BOTTOM = 3;
	/** Blocks kept between the box and the soul urn's room. */
	static final int VESSEL_CLEAR = 3;

	static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("chests/crypt_maze"));

	/** The spiral's ring, in the stair room's own blocks, from the hall floor down: each step a block lower, the first over the open throat. */
	private static final int[][] RING = {{2, 2}, {1, 2}, {0, 2}, {0, 1}, {0, 0}, {1, 0}, {2, 0}, {2, 1}};
	/** Which way each step rises (toward the one before it, so you go down the other way). */
	private static final Direction[] RISES = {Direction.EAST, Direction.EAST, Direction.EAST, Direction.SOUTH, Direction.SOUTH, Direction.WEST,
		Direction.WEST, Direction.NORTH};

	/** One crypt's maze: its box's lowest corner (the floor level), the turns taken (-1: none can be cut here) and the game time of the last. */
	public record Maze(UUID id, BlockPos origin, int turn, long last) {
		static final Codec<Maze> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(Maze::id),
				BlockPos.CODEC.fieldOf("origin").forGetter(Maze::origin),
				Codec.INT.fieldOf("turn").forGetter(Maze::turn),
				Codec.LONG.fieldOf("last").forGetter(Maze::last))
			.apply(i, Maze::new));
	}

	static final AttachmentType<List<Maze>> MAZES = AttachmentRegistry.create(JaymosKingdoms.id("crypt_mazes"), b -> b.persistent(Maze.CODEC.listOf()));

	private CryptMaze() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CryptMaze::tick);
	}

	// ---- the tick ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		List<Necropolis.Crypt> crypts = level.getAttachedOrElse(Necropolis.CRYPTS, List.<Necropolis.Crypt>of());
		if (crypts.isEmpty()) {
			return;
		}
		List<Maze> mazes = new ArrayList<>(level.getAttachedOrElse(MAZES, List.<Maze>of()));
		boolean changed = false;
		for (Necropolis.Crypt crypt : crypts) {
			if (crypt.done()) {
				// the necromancer is gone: the walls stop where they are
				continue;
			}
			int at = -1;
			for (int i = 0; i < mazes.size(); i++) {
				if (mazes.get(i).id().equals(crypt.id())) {
					at = i;
				}
			}
			if (at < 0) {
				Maze made = cut(level, crypt);
				if (made != null) {
					mazes.add(made);
					changed = true;
				}
			} else if (mazes.get(at).turn() >= 0) {
				Maze turned = turn(level, mazes.get(at));
				if (turned != null) {
					mazes.set(at, turned);
					changed = true;
				}
			}
		}
		if (changed) {
			level.setAttached(MAZES, List.copyOf(mazes));
		}
	}

	/** A crypt's maze cut for the first time; its record (turn -1 if the ground won't take one), or null if its land isn't loaded yet. */
	private static Maze cut(ServerLevel level, Necropolis.Crypt crypt) {
		BlockPos hall = crypt.hall();
		BlockPos origin = new BlockPos(hall.getX() - Necropolis.HALF - 1, hall.getY() - DROP, hall.getZ() - Necropolis.HALF - 1);
		if (!Loaded.made(level, origin.getX(), origin.getZ(), origin.getX() + SIZE - 1, origin.getZ() + SIZE - 1)) {
			return null;
		}
		long now = level.getGameTime();
		if (!room(level, origin, crypt)) {
			return new Maze(crypt.id(), origin, -1, now);
		}
		lay(level, origin, seed(crypt.id(), 0), true);
		furnish(level, origin);
		return new Maze(crypt.id(), origin, 0, now);
	}

	/** The walls laid again, if it is night, the last turn was long enough ago and nobody is inside; the new record, or null if nothing was done. */
	private static Maze turn(ServerLevel level, Maze maze) {
		if (level.getGameTime() - maze.last() < SHIFT_TICKS || !level.isDarkOutside()) {
			return null;
		}
		BlockPos o = maze.origin();
		if (!Loaded.made(level, o.getX(), o.getZ(), o.getX() + SIZE - 1, o.getZ() + SIZE - 1)) {
			return null;
		}
		AABB box = box(o);
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.getBoundingBox().intersects(box.inflate(1.0))) {
				return null;
			}
		}
		int turn = maze.turn() + 1;
		lay(level, o, seed(maze.id(), turn), false);
		level.playSound(null, box.getCenter().x, box.getCenter().y, box.getCenter().z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, GRIND_VOLUME, 0.5F);
		level.playSound(null, box.getCenter().x, box.getCenter().y, box.getCenter().z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, GRIND_VOLUME, 0.6F);
		return new Maze(maze.id(), o, turn, level.getGameTime());
	}

	private static long seed(UUID id, int turn) {
		return id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 21) ^ turn * 0x9E3779B97F4A7C15L;
	}

	/** The whole box, floor to ceiling. */
	private static AABB box(BlockPos o) {
		return new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + SIZE, o.getY() + WALL_HEIGHT + 2, o.getZ() + SIZE);
	}

	/**
	 * Whether the ground under the hall will take the maze: above the world's bottom, clear of the soul urn's room, and
	 * natural, dry and mostly solid all through the box and the stair's well above it.
	 */
	private static boolean room(ServerLevel level, BlockPos o, Necropolis.Crypt crypt) {
		if (o.getY() - 1 <= level.getMinY() + MIN_ABOVE_BOTTOM) {
			return false;
		}
		BlockPos v = crypt.vessel();
		if (v.getX() + 1 + VESSEL_CLEAR >= o.getX() && v.getX() - 1 - VESSEL_CLEAR <= o.getX() + SIZE - 1
			&& v.getZ() + 1 + VESSEL_CLEAR >= o.getZ() && v.getZ() - 1 - VESSEL_CLEAR <= o.getZ() + SIZE - 1
			&& v.getY() + 1 + VESSEL_CLEAR >= o.getY() - 1 && v.getY() - 1 - VESSEL_CLEAR <= o.getY() + DROP) {
			return false;
		}
		int air = 0, all = 0;
		for (BlockPos p : BlockPos.betweenClosed(o.offset(0, -1, 0), o.offset(SIZE - 1, DROP - 1, SIZE - 1))) {
			BlockState s = level.getBlockState(p);
			all++;
			if (!s.getFluidState().isEmpty()) {
				return false;
			}
			if (s.isAir()) {
				air++;
			} else if (!Lairs.natural(s)) {
				return false;
			}
		}
		return air <= all * AIR_ALLOWED;
	}

	// ---- laying the maze --------------------------------------------------------------------------------------------

	/** Part of the box that never changes: the stair's room (the corner) and the heart room. */
	private static boolean fixed(int x, int z) {
		return x <= 2 * STAIR_SIZE - 1 && z <= 2 * STAIR_SIZE - 1 || x >= HEART_LO && x <= HEART_HI && z >= HEART_LO && z <= HEART_HI;
	}

	/** The crypt's own stone, mossy and cracked here and there (the same block at the same spot every time). */
	private static BlockState stone(int x, int y, int z) {
		int r = Math.floorMod(x * 31 + z * 17 + y * 7, 11);
		return (r == 0 ? Blocks.MOSSY_STONE_BRICKS : r < 3 ? Blocks.CRACKED_STONE_BRICKS : r < 5 ? Blocks.DEEPSLATE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
	}

	private static boolean isWall(BlockState s) {
		return s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS) || s.is(Blocks.CRACKED_STONE_BRICKS) || s.is(Blocks.DEEPSLATE_BRICKS);
	}

	/**
	 * The maze's passages and walls from {@code seed}. First time ({@code first}): the floor, the ceiling, the outer wall and
	 * everything laid whole. After that only the walls inside move: a wall is taken down where the new maze has a passage and
	 * laid where it has a wall, but never on a spot where something stands, and never over a block that is not the crypt's own
	 * stone or air (a torch someone left, a chest).
	 */
	private static void lay(ServerLevel level, BlockPos o, long seed, boolean first) {
		boolean[][] open = MazeGen.layout(CELLS, new MazeGen.Room(HEART_AT, HEART_AT, HEART_SIZE), new MazeGen.Room(0, 0, STAIR_SIZE), seed, ROOT_X, ROOT_Z);
		open[DOOR_X][DOOR_Z] = true;
		List<AABB> bodies = new ArrayList<>();
		if (!first) {
			for (Entity e : level.getEntities((Entity)null, box(o))) {
				bodies.add(e.getBoundingBox());
			}
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				boolean outer = x == 0 || z == 0 || x == SIZE - 1 || z == SIZE - 1;
				if (first) {
					level.setBlock(at.set(o.getX() + x, o.getY(), o.getZ() + z), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
					level.setBlock(at.set(o.getX() + x, o.getY() + WALL_HEIGHT + 1, o.getZ() + z), stone(x, WALL_HEIGHT + 1, z), Block.UPDATE_CLIENTS);
				} else if (outer || fixed(x, z)) {
					continue;
				}
				boolean wall = !open[x][z];
				AABB column = wall && !first ? new AABB(o.getX() + x, o.getY() + 1, o.getZ() + z, o.getX() + x + 1, o.getY() + WALL_HEIGHT + 1, o.getZ() + z + 1) : null;
				if (column != null && bodies.stream().anyMatch(b -> b.intersects(column))) {
					continue;
				}
				for (int y = 1; y <= WALL_HEIGHT; y++) {
					at.set(o.getX() + x, o.getY() + y, o.getZ() + z);
					BlockState now = level.getBlockState(at);
					if (wall && (first || now.isAir())) {
						level.setBlock(at, stone(x, y, z), Block.UPDATE_CLIENTS);
					} else if (!wall && (first || isWall(now))) {
						level.setBlock(at, air, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/** The spiral stair, the heart room with its chest and lanterns (laid once, over the maze's first walls). */
	private static void furnish(ServerLevel level, BlockPos o) {
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		// the stair: the room's corner blocks 1..3, its ring of eight steps down from the hall's floor round a pillar, each solid
		// underneath, the first over an open throat down to the maze's floor; the hall's floor is opened over the rest
		int hallY = o.getY() + DROP;
		for (int k = 0; k < RING.length; k++) {
			int bx = o.getX() + 1 + RING[k][0], bz = o.getZ() + 1 + RING[k][1], step = hallY - k;
			for (int y = o.getY() + 1; y <= hallY + 1; y++) {
				BlockState s = y == step ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, RISES[k])
					: y < step && k > 0 ? brick : air;
				level.setBlock(at.set(bx, y, bz), s, Block.UPDATE_CLIENTS);
			}
		}
		for (int y = o.getY() + 1; y < hallY; y++) {
			level.setBlock(at.set(o.getX() + 2, y, o.getZ() + 2), brick, Block.UPDATE_CLIENTS);
		}
		// the heart: a dais, the chest on it, a soul lantern in each corner, candles along the walls
		int c = (HEART_LO + HEART_HI) / 2;
		int floor = o.getY();
		for (int x = HEART_LO; x <= HEART_HI; x++) {
			for (int z = HEART_LO; z <= HEART_HI; z++) {
				boolean dais = Math.abs(x - c) <= 1 && Math.abs(z - c) <= 1;
				level.setBlock(at.set(o.getX() + x, floor, o.getZ() + z), (dais ? Blocks.CRACKED_STONE_BRICKS : Blocks.DEEPSLATE_BRICKS).defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		BlockPos chest = new BlockPos(o.getX() + c, floor + 1, o.getZ() + c);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(chest) instanceof RandomizableContainerBlockEntity box) {
			box.setLootTable(LOOT, level.getRandom().nextLong());
		}
		for (int[] d : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			level.setBlock(at.set(o.getX() + c + d[0], floor + 1, o.getZ() + c + d[1]), Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		for (int[] d : new int[][] {{-1, -2}, {1, 2}, {-2, 1}, {2, -1}}) {
			level.setBlock(at.set(o.getX() + c + d[0], floor + 1, o.getZ() + c + d[1]), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2), Block.UPDATE_CLIENTS);
		}
	}
}
