package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Loaded;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The cyclops's hall (after Mythika's "Cyclops' Cave"; our own build). Where a cyclops comes into the world by nature
 * near a mountainside, a hall is cut into the slope for it, its door-stone rolled aside: a mouth {@link #MOUTH_HALF}*2+1
 * wide, then a hall {@link #HALF_WIDTH}*2+1 wide, {@link #HEIGHT} high and {@link #DEPTH} deep, its floor beaten earth and
 * gravel. In the middle burns its bonfire on a bale of hay, under a smoke hole cut up through the rock to the sky, so
 * the smoke stands up over the mountain and can be seen from far off (that is how you find one). In a back corner is a
 * fold of {@link #SHEEP} sheep (its flock: {@link Cyclops#FLOCK_TAG}), and along the other wall a shelf of the bones of
 * whatever it has eaten.
 * <p>
 * Only into natural rock and earth with enough of the mountain over it; never through water, anything built or anything
 * holding a block entity. If no slope near will take a hall, the cyclops roams without one. The work is done on the next
 * tick, on the loaded world.
 */
public final class CyclopsCaves {
	/** How far out (blocks) a slope is looked for, how high it must rise over the cyclops's ground, how far the hall's floor may sit from it. */
	static final int SEARCH = 18, RISE = 8, FLOOR_SLACK = 3;
	/** The hall: half its width, its depth (mouth included), its height; the mouth's half width, height and depth. */
	static final int HALF_WIDTH = 4, DEPTH = 12, HEIGHT = 6, MOUTH_HALF = 2, MOUTH_HEIGHT = 4, MOUTH_DEPTH = 2;
	/** Rock left over the hall's roof (blocks), and the share of the hall's room that must be rock or earth already. */
	static final int COVER = 2;
	static final float SOLID_SHARE = 0.7F;
	/** How many sheep in its fold. */
	static final int SHEEP = 3;

	private record Job(ServerLevel level, Cyclops cyclops) {
	}

	private static final List<Job> QUEUE = new ArrayList<>();

	private CyclopsCaves() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (QUEUE.isEmpty()) {
				return;
			}
			List<Job> jobs = List.copyOf(QUEUE);
			QUEUE.clear();
			for (Job job : jobs) {
				if (job.cyclops().isAlive() && job.cyclops().home() == null) {
					make(job.level(), job.cyclops());
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> QUEUE.clear());
	}

	/** A cyclops came into the world: by nature, it gets a hall if a slope near will take one. */
	static void cyclops(ServerLevelAccessor level, Cyclops cyclops, EntitySpawnReason reason) {
		if (reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION) {
			QUEUE.add(new Job(level.getLevel(), cyclops));
		}
	}

	/** Cuts a hall for {@code cyclops} into the first slope near it that will take one; true if one was made. */
	public static boolean make(ServerLevel level, Cyclops cyclops) {
		BlockPos at = cyclops.blockPosition();
		List<Direction> ways = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
		Collections.shuffle(ways, new java.util.Random(level.getRandom().nextLong()));
		for (Direction way : ways) {
			BlockPos mouth = slope(level, at, way);
			if (mouth != null && fits(level, mouth, way)) {
				build(level, cyclops, mouth, way);
				return true;
			}
		}
		return false;
	}

	/** Out along {@code way}: the first column the ground rises {@link #RISE} over {@code at}, at the floor of the one before it. */
	private static BlockPos slope(ServerLevel level, BlockPos at, Direction way) {
		int lastTop = Integer.MIN_VALUE;
		for (int s = 2; s <= SEARCH; s++) {
			BlockPos column = at.relative(way, s);
			if (!Loaded.made(level, column.getX(), column.getZ())) {
				return null;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			if (top - at.getY() >= RISE && lastTop != Integer.MIN_VALUE && Math.abs(lastTop - at.getY()) <= FLOOR_SLACK) {
				return new BlockPos(column.getX(), lastTop, column.getZ());
			}
			lastTop = top;
		}
		return null;
	}

	/** A block of the hall: {@code k} in from the mouth, {@code w} to the right, {@code h} up from the floor. */
	private static BlockPos cell(BlockPos mouth, Direction way, int k, int w, int h) {
		return mouth.relative(way, k).relative(way.getClockWise(), w).above(h);
	}

	/** Whether the hall's cell ({@code k}, {@code w}, {@code h}) is cut out (the mouth narrow and arched, the hall's top corners rounded). */
	private static boolean open(int k, int w, int h) {
		if (k < MOUTH_DEPTH) {
			return Math.abs(w) <= MOUTH_HALF && h < MOUTH_HEIGHT && !(Math.abs(w) == MOUTH_HALF && h == MOUTH_HEIGHT - 1);
		}
		if (Math.abs(w) > HALF_WIDTH || h >= HEIGHT) {
			return false;
		}
		boolean edge = Math.abs(w) == HALF_WIDTH, end = k == MOUTH_DEPTH || k == DEPTH - 1;
		return !(edge && h >= HEIGHT - 2) && !(edge && end) && !(end && h == HEIGHT - 1 && Math.abs(w) >= HALF_WIDTH - 1);
	}

	private static boolean natural(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
			|| state.is(BlockTags.SNOW) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.CALCITE) || state.is(Blocks.TUFF)
			|| state.is(Blocks.PACKED_ICE) || state.is(BlockTags.ORES) || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.MOSS_BLOCK);
	}

	/** The hall's room is natural rock and earth (mostly), dry, unbuilt, with the mountain over it. */
	private static boolean fits(ServerLevel level, BlockPos mouth, Direction way) {
		BlockPos a = cell(mouth, way, -2, -HALF_WIDTH - 4, 0), b = cell(mouth, way, DEPTH + 1, HALF_WIDTH + 4, HEIGHT);
		if (!level.hasChunksAt(new BlockPos(Math.min(a.getX(), b.getX()), 0, Math.min(a.getZ(), b.getZ())),
			new BlockPos(Math.max(a.getX(), b.getX()), 0, Math.max(a.getZ(), b.getZ())))) {
			return false;
		}
		int cells = 0, solid = 0;
		for (int k = 0; k < DEPTH; k++) {
			for (int w = -HALF_WIDTH - 1; w <= HALF_WIDTH + 1; w++) {
				for (int h = -1; h <= HEIGHT; h++) {
					BlockPos pos = cell(mouth, way, k, w, h);
					BlockState state = level.getBlockState(pos);
					if (!state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) {
						return false;
					}
					if (!open(k, w, h)) {
						// the walls, floor and roof round it: rock or earth (or open air at the mouth's face)
						if (k >= MOUTH_DEPTH && !state.isAir() && !natural(state)) {
							return false;
						}
						continue;
					}
					cells++;
					if (natural(state)) {
						solid++;
					} else if (!state.isAir() && !state.canBeReplaced()) {
						return false;
					}
				}
			}
			if (k >= MOUTH_DEPTH) {
				for (int w = -HALF_WIDTH; w <= HALF_WIDTH; w++) {
					BlockPos column = cell(mouth, way, k, w, 0);
					if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) < column.getY() + HEIGHT + COVER) {
						return false;
					}
				}
			}
		}
		return cells > 0 && solid >= cells * SOLID_SHARE;
	}

	private static void build(ServerLevel level, Cyclops cyclops, BlockPos mouth, Direction way) {
		RandomSource random = level.getRandom();
		BlockState air = Blocks.CAVE_AIR.defaultBlockState();
		for (int k = 0; k < DEPTH; k++) {
			for (int w = -HALF_WIDTH; w <= HALF_WIDTH; w++) {
				for (int h = 0; h < HEIGHT; h++) {
					if (open(k, w, h)) {
						level.setBlock(cell(mouth, way, k, w, h), air, Block.UPDATE_CLIENTS);
					}
				}
				if (open(k, w, 0)) {
					BlockPos floor = cell(mouth, way, k, w, -1);
					if (natural(level.getBlockState(floor))) {
						level.setBlock(floor, (random.nextInt(3) == 0 ? Blocks.GRAVEL : Blocks.COARSE_DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		// the bonfire, on hay so its smoke stands tall, and the smoke hole cut up over it to the sky
		int mid = MOUTH_DEPTH + (DEPTH - MOUTH_DEPTH) / 2;
		BlockPos fire = cell(mouth, way, mid, 0, 0);
		level.setBlock(fire.below(), Blocks.HAY_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, true),
			Block.UPDATE_ALL);
		for (BlockPos up = fire.above(HEIGHT); up.getY() < level.getMaxY(); up = up.above()) {
			BlockState state = level.getBlockState(up);
			if (state.isAir()) {
				break;
			}
			if (!natural(state) && !state.is(BlockTags.LEAVES) && !state.canBeReplaced()) {
				break;
			}
			level.setBlock(up, air, Block.UPDATE_CLIENTS);
		}
		fold(level, cyclops, mouth, way);
		shelf(level, mouth, way, random);
		doorStone(level, mouth, way, random);
		cyclops.setHome(fire);
		return;
	}

	/** The sheepfold in the back right corner: a fence round three sides (the hall's own walls the rest), a gate, hay, its flock. */
	private static void fold(ServerLevel level, Cyclops cyclops, BlockPos mouth, Direction way) {
		int front = DEPTH - 4, inner = 1;
		BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
		for (int w = inner; w < HALF_WIDTH; w++) {
			BlockPos pos = cell(mouth, way, front, w, 0);
			BlockState state = w == inner + 2 ? Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, way) : fence;
			level.setBlock(pos, state, Block.UPDATE_ALL);
		}
		for (int k = front + 1; k < DEPTH - 1; k++) {
			level.setBlock(cell(mouth, way, k, inner, 0), fence, Block.UPDATE_ALL);
		}
		level.setBlock(cell(mouth, way, DEPTH - 2, HALF_WIDTH - 1, 0), Blocks.HAY_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		String mine = Cyclops.FLOCK_TAG + ":" + cyclops.getUUID();
		for (int i = 0; i < SHEEP; i++) {
			BlockPos pen = cell(mouth, way, front + 1 + i % 2, inner + 1 + i / 2, 0);
			Sheep sheep = net.minecraft.world.entity.EntityTypes.SHEEP.create(level, EntitySpawnReason.STRUCTURE);
			if (sheep == null) {
				continue;
			}
			sheep.snapTo(pen.getX() + 0.5, pen.getY(), pen.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			sheep.addTag(mine);
			sheep.setPersistenceRequired();
			level.addFreshEntity(sheep);
		}
	}

	/** Along the left wall, a shelf (spruce slabs) of the bones of what it has eaten: skulls and bones. */
	private static void shelf(ServerLevel level, BlockPos mouth, Direction way, RandomSource random) {
		Direction facing = way.getClockWise();
		for (int k = MOUTH_DEPTH + 1; k < MOUTH_DEPTH + 6; k++) {
			BlockPos slab = cell(mouth, way, k, -HALF_WIDTH, 2);
			level.setBlock(slab, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL);
			BlockState on = switch (random.nextInt(4)) {
				case 0 -> Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(facing));
				case 1 -> Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, way.getAxis());
				case 2 -> Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION,
					RotationSegment.convertToSegment(facing) + random.nextInt(3) - 1 & 15);
				default -> null;
			};
			if (on != null) {
				level.setBlock(slab.above(), on, Block.UPDATE_ALL);
			}
		}
	}

	/** The door-stone rolled aside: a rough boulder by the mouth, on the ground outside. */
	private static void doorStone(ServerLevel level, BlockPos mouth, Direction way, RandomSource random) {
		BlockPos c = cell(mouth, way, -1, MOUTH_HALF + 3, 0);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
		if (Math.abs(ground - mouth.getY()) > FLOOR_SLACK) {
			return;
		}
		BlockPos centre = new BlockPos(c.getX(), ground + 1, c.getZ());
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, -1, -2), centre.offset(2, 2, 2))) {
			double d = pos.distSqr(centre);
			if (d > 3.2 + random.nextDouble() * 1.2) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.canBeReplaced() || natural(state)) {
				BlockState stone = switch (random.nextInt(4)) {
					case 0 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
					case 1 -> Blocks.COBBLESTONE.defaultBlockState();
					default -> Blocks.STONE.defaultBlockState();
				};
				level.setBlock(pos, stone, Block.UPDATE_CLIENTS);
			}
		}
	}
}
