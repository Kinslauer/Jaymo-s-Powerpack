package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.627) A settlement's open-pit mine, Andy (2026-11-06): "the dungeons and taverns mine here is exactly what i was
 * imagining when i wanted our builders to mine. the current one should be for poverty nations only as it is too simple."
 * Our own build in that style (nothing of theirs copied): its builders dig a stepped pit {@link #HALF}*2+1 blocks across,
 * a layer at a time, each terrace a step in from the one above, down to a floor {@link #DEPTH} deep. Then they fence its
 * rim (lanterns on the corner posts), lay a minecart track from the floor up the terraces to a cobblestone shed with a
 * plank roof behind it (a cart waiting inside, a barrel, a stonecutter), and drive the old tunnel ({@link Mines}) on into
 * the rock from the pit floor, its mouth framed in logs. A poor settlement ({@link #poor}) still digs the plain tunnel or
 * shaft. The pit is a {@link Mines.Mine} with {@code pit} set; {@code layer} counts the pit's layers dug.
 */
final class MinePit {
	/** The pit's half-width at the rim (it's this*2+1 across) and how many layers deep; every layer is a step in from the one above. */
	static final int HALF = 6, DEPTH = 5;
	/** The tunnel from the floor runs level this many steps (into the wall, under the rim) before it turns down; its mouth frame at this step. */
	static final int FLAT = 5, MOUTH = 3;
	/** The shed behind the pit: this far each side of its track, this long. */
	static final int SHED_HALF = 2, SHED_LONG = 5;
	/** A settlement worth less than this ({@link Wealth#worth}: its coin, its people, its fiefdoms) digs the plain mine. A fiefdom goes by its overlord's worth. */
	static final long POOR_WORTH = 80L;
	/** Sites looked at this far apart. */
	static final int SITE_STEP = 4;

	private MinePit() {
	}

	/** Too poor for a pit: it digs the plain tunnel or shaft. */
	static boolean poor(MinecraftServer server, Faction f) {
		Faction judge = f;
		if (f.type() == FactionType.FIEFDOM && f.overlord() >= 0) {
			Faction lord = Factions.get(server, f.overlord());
			if (lord != null && !lord.fallen()) {
				judge = lord;
			}
		}
		return Wealth.worth(server, judge) < POOR_WORTH;
	}

	/** The floor's half-width. */
	static int floorHalf() {
		return HALF - DEPTH + 1;
	}

	/** A pit centred on {@code at} (the air over the ground), its tunnel to go {@code facing} from the floor. */
	static Mines.Mine plan(BlockPos at, Direction facing) {
		BlockPos entrance = at.relative(facing, floorHalf() + 1).below(DEPTH);
		return new Mines.Mine(entrance, facing, FLAT, 0, Mines.DIGGING, HALF, 0, BlockPos.ZERO, 0);
	}

	/** The pit's middle, in the air over the ground it was dug from. */
	static BlockPos center(Mines.Mine m) {
		if (m.pit() < 0) {
			return DntMine.center(m);
		}
		return m.entrance().relative(m.facing(), -(floorHalf() + 1)).above(DEPTH);
	}

	/** The block {@code d} along {@code way} and {@code s} to its side ({@link Direction#getClockWise}) of {@code from}, {@code h} up. */
	private static BlockPos at(BlockPos from, Direction way, int d, int s, int h) {
		return from.relative(way, d).relative(way.getClockWise(), s).above(h);
	}

	/** Layer {@code k}'s blocks: the first also clears what stands on the ground out to the fence. */
	static List<BlockPos> layer(Mines.Mine m, int k) {
		BlockPos c = center(m);
		List<BlockPos> out = new ArrayList<>();
		if (k == 0) {
			for (int h = 1; h >= 0; h--) {
				for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
					for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
						out.add(c.offset(dx, h, dz));
					}
				}
			}
		}
		int half = HALF - k;
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				out.add(c.offset(dx, -1 - k, dz));
			}
		}
		return out;
	}

	/** What's left of the layer being dug (nothing unbreakable). */
	static List<BlockPos> left(ServerLevel level, Mines.Mine m) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : m.pit() < 0 ? DntMine.layer(level.getServer(), m, m.layer()) : layer(m, m.layer())) {
			if (!Mines.clear(level, p) && level.getBlockState(p).getDestroySpeed(level, p) >= 0) {
				out.add(p);
			}
		}
		return out;
	}

	/** The block of the layer nearest {@code near}, or null when the layer is open. */
	static @Nullable BlockPos next(ServerLevel level, Mines.Mine m, BlockPos near) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : left(level, m)) {
			double d = p.distSqr(near);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** Water or lava beside a block about to be dug is stopped with cobblestone first (the pit stays dry). */
	static void plug(ServerLevel level, BlockPos at) {
		for (Direction d : Direction.values()) {
			BlockPos n = at.relative(d);
			if (!level.getFluidState(n).isEmpty() && d != Direction.UP) {
				level.setBlock(n, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	/** Where one stands in the air standing over the ground at Chebyshev distance {@code r} from the middle. */
	private static int standY(int y0, int r) {
		return r > HALF ? y0 : y0 - 1 - Math.min(HALF - r, DEPTH - 1);
	}

	/**
	 * A layer is dug: the ground under each new terrace (and the floor) made good, then the next; after the last, the
	 * rim, track and shed, and on to the tunnel.
	 */
	static Mines.Mine advance(ServerLevel level, Faction f, Mines.Mine m, boolean quiet) {
		if (m.pit() < 0) {
			return DntMine.advance(level, f, m);
		}
		int k = m.layer();
		BlockPos c = center(m);
		int y0 = c.getY();
		int half = HALF - k;
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				int r = Math.max(Math.abs(dx), Math.abs(dz));
				if (r != half && k != DEPTH - 1) {
					continue;
				}
				BlockPos under = c.offset(dx, -2 - k, dz);
				if (standY(y0, r) - 1 == under.getY() && (Mines.clear(level, under) || !level.getFluidState(under).isEmpty())) {
					level.setBlock(under, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		Mines.Mine after = m.withLayer(k + 1);
		if (k + 1 >= DEPTH) {
			furnish(level, after, Palette.at(level, f.seat()));
			JaymosKingdoms.LOGGER.info("{}'s mine pit is dug; on to the tunnel", f.name());
		}
		Mines.put(level.getServer(), f.id(), after);
		return after;
	}

	// ---- the site ---------------------------------------------------------------------------------------------------

	/** The rim, the shed and its overhang, as two corners (for the buildings list and clashes). */
	static BlockPos[] footprint(Mines.Mine m) {
		if (m.pit() < 0) {
			return DntMine.footprint(m);
		}
		BlockPos c = center(m);
		Direction back = m.facing().getOpposite();
		BlockPos a = c.offset(-HALF - 2, -DEPTH - 1, -HALF - 2);
		BlockPos b = c.offset(HALF + 2, 5, HALF + 2);
		BlockPos s0 = at(c, back, HALF + SHED_LONG + 2, -SHED_HALF - 1, 0);
		BlockPos s1 = at(c, back, HALF + SHED_LONG + 2, SHED_HALF + 1, 0);
		return new BlockPos[] {
			new BlockPos(min(a.getX(), s0.getX(), s1.getX()), a.getY(), min(a.getZ(), s0.getZ(), s1.getZ())),
			new BlockPos(max(b.getX(), s0.getX(), s1.getX()), b.getY(), max(b.getZ(), s0.getZ(), s1.getZ()))};
	}

	private static int min(int a, int b, int c) {
		return Math.min(a, Math.min(b, c));
	}

	private static int max(int a, int b, int c) {
		return Math.max(a, Math.max(b, c));
	}

	/** Natural ground: soil, rock, sand or gravel (not a road, a floor or a roof). */
	static boolean natural(BlockState ground) {
		return ground.is(BlockTags.SUBSTRATE_OVERWORLD) || ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(BlockTags.SAND)
			|| ground.is(Blocks.GRAVEL) || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.CLAY);
	}

	/** The column at {@code x, z} is natural, dry ground within a block of {@code y0}. */
	static boolean level(ServerLevel level, int x, int z, int y0) {
		BlockPos column = new BlockPos(x, y0, z);
		if (!level.isLoaded(column)) {
			return false;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		return Math.abs(top.getY() - y0) <= 1 && level.getFluidState(top).isEmpty() && level.getFluidState(top.below()).isEmpty()
			&& natural(level.getBlockState(top.below()));
	}

	/**
	 * The nearest open, level, dry ground round the seat with room for the pit and its shed (and nothing built there),
	 * or null. {@code least}: no nearer the seat than this.
	 */
	static Mines.@Nullable Mine site(ServerLevel level, Faction f, List<int[]> taken, int least) {
		BlockPos seat = f.seat();
		Mines.Mine best = null;
		double bestD = Double.MAX_VALUE;
		int reach = Mines.SITE_MAX;
		for (int dx = -reach; dx <= reach; dx += SITE_STEP) {
			for (int dz = -reach; dz <= reach; dz += SITE_STEP) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < least + HALF || d > reach || d >= bestD) {
					continue;
				}
				BlockPos column = seat.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (!level(level, at.getX(), at.getZ(), at.getY()) || !square(level, at) || wet(level, at)) {
					continue;
				}
				for (Direction way : Direction.Plane.HORIZONTAL) {
					Mines.Mine m = plan(at, way);
					if (!shedGround(level, m) || Mines.clash(level.getServer(), footprint(m), taken)) {
						continue;
					}
					best = m;
					bestD = d;
					break;
				}
			}
		}
		return best;
	}

	/** The pit and its fence out to a block past the rim: level, dry, natural ground. */
	private static boolean square(ServerLevel level, BlockPos at) {
		for (int dx = -HALF - 2; dx <= HALF + 2; dx++) {
			for (int dz = -HALF - 2; dz <= HALF + 2; dz++) {
				if (!level(level, at.getX() + dx, at.getZ() + dz, at.getY())) {
					return false;
				}
			}
		}
		return true;
	}

	/** Water or lava where the pit would go (or just under it): an underground lake or lava sea. */
	private static boolean wet(ServerLevel level, BlockPos at) {
		for (int k = 0; k <= DEPTH; k++) {
			int half = Math.max(1, HALF - k) + 1;
			for (int dx = -half; dx <= half; dx += 1) {
				for (int dz = -half; dz <= half; dz += 1) {
					if (!level.getFluidState(at.offset(dx, -1 - k, dz)).isEmpty()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The shed's ground behind the pit: level, dry, natural. */
	private static boolean shedGround(ServerLevel level, Mines.Mine m) {
		BlockPos c = center(m);
		Direction back = m.facing().getOpposite();
		for (int d = HALF + 2; d <= HALF + SHED_LONG + 2; d++) {
			for (int s = -SHED_HALF - 1; s <= SHED_HALF + 1; s++) {
				BlockPos q = at(c, back, d, s, 0);
				if (!level(level, q.getX(), q.getZ(), c.getY())) {
					return false;
				}
			}
		}
		return true;
	}

	// ---- the furnishings --------------------------------------------------------------------------------------------

	private static void set(ServerLevel level, BlockPos at, BlockState state) {
		level.setBlock(at, state, Block.UPDATE_ALL);
	}

	/** The pit's dug: the fence round its rim, the track, the shed. */
	static void furnish(ServerLevel level, Mines.Mine m, Palette p) {
		BlockPos c = center(m);
		int y0 = c.getY();
		Direction back = m.facing().getOpposite();
		shed(level, c, back, p);
		fence(level, c, back, p);
		track(level, c, back, y0);
	}

	/** Fence posts round the rim, a gap where the track leaves and another where the path comes in opposite; lanterns on the corners. */
	private static void fence(ServerLevel level, BlockPos c, Direction back, Palette p) {
		int r = HALF + 1;
		List<BlockPos> posts = new ArrayList<>();
		for (int d = -r; d <= r; d++) {
			for (int s = -r; s <= r; s++) {
				if (Math.max(Math.abs(d), Math.abs(s)) != r || (s == 0 && Math.abs(d) == r)) {
					continue;
				}
				BlockPos q = at(c, back, d, s, 0);
				if (!Mines.clear(level, q)) {
					continue;
				}
				if (Mines.clear(level, q.below())) {
					set(level, q.below(), Blocks.COBBLESTONE.defaultBlockState());
				}
				set(level, q, p.fence());
				posts.add(q);
				if (Math.abs(d) == r && Math.abs(s) == r && Mines.clear(level, q.above())) {
					set(level, q.above(), Castle.byName("lantern", Blocks.TORCH).trySetValue(LanternBlock.HANGING, false));
				}
			}
		}
		// (each post joined to its neighbours)
		for (BlockPos q : posts) {
			level.setBlock(q, Block.updateFromNeighbourShapes(level.getBlockState(q), level, q), Block.UPDATE_CLIENTS);
		}
	}

	/** Rails from the middle of the floor up the terraces at the back, out through the fence and into the shed, a cart at the end. */
	private static void track(ServerLevel level, BlockPos c, Direction back, int y0) {
		int last = HALF + 1 + SHED_LONG - 2;
		RailShape flat = back.getAxis() == Direction.Axis.Z ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST;
		RailShape up = switch (back) {
			case NORTH -> RailShape.ASCENDING_NORTH;
			case SOUTH -> RailShape.ASCENDING_SOUTH;
			case EAST -> RailShape.ASCENDING_EAST;
			default -> RailShape.ASCENDING_WEST;
		};
		BlockPos end = null;
		for (int d = 0; d <= last; d++) {
			int y = standY(y0, d);
			BlockPos q = at(c, back, d, 0, 0).atY(y);
			if (!Mines.clear(level, q)) {
				continue;
			}
			if (Mines.clear(level, q.below())) {
				set(level, q.below(), Blocks.COBBLESTONE.defaultBlockState());
			}
			boolean rises = d < last && standY(y0, d + 1) == y + 1;
			set(level, q, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, rises ? up : flat));
			end = q;
		}
		if (end != null && level.getEntitiesOfClass(AbstractMinecart.class, new AABB(end).inflate(3.0)).isEmpty()) {
			EntityTypes.MINECART.spawn(level, end, EntitySpawnReason.STRUCTURE);
		}
	}

	/** A cobblestone shed with log corners and a plank roof behind the pit, open to the track; a barrel, a stonecutter and a lantern inside. */
	private static void shed(ServerLevel level, BlockPos c, Direction back, Palette p) {
		int d0 = HALF + 2, d1 = HALF + 1 + SHED_LONG;
		// cleared out, roof and overhang included
		for (int d = d0 - 1; d <= d1 + 1; d++) {
			for (int s = -SHED_HALF - 1; s <= SHED_HALF + 1; s++) {
				boolean inside = d >= d0 && d <= d1 && Math.abs(s) <= SHED_HALF;
				for (int h = inside ? 0 : 3; h <= 5; h++) {
					BlockPos q = at(c, back, d, s, h);
					if (!level.getBlockState(q).isAir() && !level.getBlockState(q).is(BlockTags.FENCES)) {
						set(level, q, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		for (int d = d0; d <= d1; d++) {
			for (int s = -SHED_HALF; s <= SHED_HALF; s++) {
				boolean wall = d == d0 || d == d1 || Math.abs(s) == SHED_HALF;
				BlockPos q = at(c, back, d, s, 0);
				// its floor: planks inside, the walls on cobblestone
				set(level, q.below(), wall ? Blocks.COBBLESTONE.defaultBlockState() : p.planks());
				if (!wall) {
					continue;
				}
				boolean corner = (d == d0 || d == d1) && Math.abs(s) == SHED_HALF;
				for (int h = 0; h < 3; h++) {
					boolean door = d == d0 && s == 0 && h < 2;
					boolean window = Math.abs(s) == SHED_HALF && d == (d0 + d1) / 2 && h == 1;
					if (door || window) {
						continue;
					}
					BlockPos w = q.above(h);
					set(level, w, corner ? p.log() : Math.floorMod(w.hashCode(), 5) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
						: Blocks.COBBLESTONE.defaultBlockState());
				}
			}
		}
		// the roof: planks with a slab eave round it, a slab ridge along the middle
		for (int d = d0 - 1; d <= d1 + 1; d++) {
			for (int s = -SHED_HALF - 1; s <= SHED_HALF + 1; s++) {
				set(level, at(c, back, d, s, 3), Math.abs(s) == SHED_HALF + 1 ? p.woodSlab() : p.planks());
				if (Math.abs(s) <= 1) {
					set(level, at(c, back, d, s, 4), p.woodSlab());
				}
			}
		}
		set(level, at(c, back, d1 - 1, -1, 0), Blocks.BARREL.defaultBlockState());
		set(level, at(c, back, d1 - 1, 1, 0), Blocks.STONECUTTER.defaultBlockState());
		set(level, at(c, back, (d0 + d1) / 2, -1, 2), Castle.byName("lantern", Blocks.AIR).trySetValue(LanternBlock.HANGING, true));
	}

	/** The tunnel's mouth in the pit wall: log posts each side and a log lintel across. */
	static void mouth(ServerLevel level, Mines.Mine m, int i, Palette p) {
		BlockPos c = m.cell(i);
		Direction side = m.side();
		int half = Mines.WIDE / 2;
		for (int s = -half; s <= half; s++) {
			BlockPos q = c.relative(side, s);
			if (Math.abs(s) == half) {
				for (int h = 0; h < 2; h++) {
					if (Mines.clear(level, q.above(h))) {
						set(level, q.above(h), p.log());
					}
				}
			}
			if (Mines.clear(level, q.above(2))) {
				set(level, q.above(2), p.log().trySetValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, side.getAxis()));
			}
		}
	}
}
