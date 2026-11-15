package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * How much of a sound gets through the blocks between where it was made and a listener's ears ({@link Hearing}).
 * <p>
 * The way of it is borrowed from Mob Senses (Shamanalle, MIT; its {@code VoxelRay}, {@code RayBundle},
 * {@code BlockAcoustics}, {@code AcousticMaterial} and {@code NoiseModel.occlusionFactor}), adapted: a grid walk (a DDA)
 * along the line, every block it crosses adding its material's thickness (in stone blocks: {@link #weight}) for as much of
 * the block as the line runs through, five parallel lines about a block wide averaged so a door frame or a corner doesn't
 * flip it between clear and blocked, and what gets through falling off as {@code e^(-DECAY * thickness)}. Stone lets
 * {@code ~0.64} through a block, wool {@code ~0.53}, wood {@code ~0.73}, glass {@code ~0.84}; an open door, trapdoor or gate
 * hardly counts. The material is read from the block's sound and kind rather than its name.
 */
public final class SoundRay {
	// ---- tuning ----
	/** What gets through falls off as e^(-DECAY * thickness): one block of stone lets 0.64 through. */
	static final double DECAY = 0.446;
	/** Past this thickness (stone blocks) on one line nothing gets through; a line walks at most this many blocks. */
	static final double MAX_THICKNESS = 8.0;
	static final int MAX_BLOCKS = 96;
	/** How far the side lines are set off the middle one (blocks); a line this far inside a block counts it fully. */
	static final double SPREAD = 0.4, FULL_CHORD = 0.4;
	/** Thickness (stone blocks) of one block of each material. */
	static final double STONE = 1.0, METAL = 1.3, EARTH = 0.9, WOOD = 0.7, WOOL = 1.4, SOFT = 1.2, GLASS = 0.4, DOOR = 0.6, LEAVES = 0.15,
		THIN = 0.2, WATER = 0.35;
	/** An open door, trapdoor or gate keeps this share of a closed one's: the sound goes round the panel. */
	static final double OPEN_SHARE = 0.1;

	private SoundRay() {
	}

	/** The share (0 to 1) of a sound made at {@code from} that reaches {@code to} through the blocks between. */
	static double through(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double length = d.length();
		if (length < 0.5) {
			return 1.0;
		}
		Vec3 dir = d.scale(1.0 / length);
		// side = dir x up (the X axis looking straight up or down); up' = side x dir
		Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
		side = side.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 up = side.cross(dir);
		double total = line(level, from, to);
		total += offset(level, from, to, side);
		total += offset(level, from, to, side.scale(-1.0));
		total += offset(level, from, to, up);
		total += offset(level, from, to, up.scale(-1.0));
		double thickness = total / 5.0;
		return thickness >= MAX_THICKNESS ? 0.0 : Math.exp(-DECAY * thickness);
	}

	/** A side line, shifted as far as the air allows (so in a two-block corridor it doesn't run inside the ceiling). */
	private static double offset(ServerLevel level, Vec3 from, Vec3 to, Vec3 by) {
		double spread = SPREAD;
		while (spread > 0.0 && !(open(level, from.add(by.scale(spread))) && open(level, to.add(by.scale(spread))))) {
			spread = spread > SPREAD * 0.3 ? spread / 2.0 : 0.0;
		}
		return line(level, from.add(by.scale(spread)), to.add(by.scale(spread)));
	}

	private static boolean open(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		BlockState state = level.getBlockState(pos);
		return state.isAir() || isOpen(state) || state.getCollisionShape(level, pos).isEmpty();
	}

	/** The thickness (stone blocks) of what one straight line from {@code from} to {@code to} passes through. */
	private static double line(ServerLevel level, Vec3 from, Vec3 to) {
		int x = floor(from.x), y = floor(from.y), z = floor(from.z);
		int sx0 = x, sy0 = y, sz0 = z;
		int ex = floor(to.x), ey = floor(to.y), ez = floor(to.z);
		double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
		int stepX = Double.compare(dx, 0.0), stepY = Double.compare(dy, 0.0), stepZ = Double.compare(dz, 0.0);
		double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
		double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
		double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
		double maxX = stepX == 0 ? Double.MAX_VALUE : deltaX * (stepX > 0 ? x + 1 - from.x : from.x - x);
		double maxY = stepY == 0 ? Double.MAX_VALUE : deltaY * (stepY > 0 ? y + 1 - from.y : from.y - y);
		double maxZ = stepZ == 0 ? Double.MAX_VALUE : deltaZ * (stepZ > 0 ? z + 1 - from.z : from.z - z);
		double total = 0.0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int visited = 0; visited < MAX_BLOCKS; visited++) {
			// (the block the sound is made in and the one the ears are in don't count)
			boolean ends = x == sx0 && y == sy0 && z == sz0 || x == ex && y == ey && z == ez;
			if (!ends) {
				total += block(level, pos.set(x, y, z), from, to);
				if (total >= MAX_THICKNESS) {
					return MAX_THICKNESS;
				}
			}
			if (x == ex && y == ey && z == ez) {
				break;
			}
			if (maxX < maxY && maxX < maxZ) {
				if (maxX > 1.0) {
					break;
				}
				x += stepX;
				maxX += deltaX;
			} else if (maxY < maxZ) {
				if (maxY > 1.0) {
					break;
				}
				y += stepY;
				maxY += deltaY;
			} else {
				if (maxZ > 1.0) {
					break;
				}
				z += stepZ;
				maxZ += deltaZ;
			}
		}
		return total;
	}

	/** What one block on the line adds: its material's thickness, for as much of it as the line runs through. */
	private static double block(ServerLevel level, BlockPos pos, Vec3 from, Vec3 to) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return 0.0;
		}
		if (state.hasProperty(BlockStateProperties.OPEN)) {
			// a door, trapdoor or gate: thin by nature, so a closed one counts fully however the line crosses it
			return DOOR * (state.getValue(BlockStateProperties.OPEN) ? OPEN_SHARE : 1.0);
		}
		if (!state.getFluidState().isEmpty() && state.getFluidState().isSource() && state.getCollisionShape(level, pos).isEmpty()) {
			return WATER;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		if (shape.isEmpty()) {
			return 0.0;
		}
		AABB box = shape.bounds().move(pos);
		double chord = chord(from, to, box);
		return chord <= 0.0 ? 0.0 : weight(state) * Math.min(1.0, chord / FULL_CHORD);
	}

	/** One full block's thickness, in stone blocks, by what it's made of. */
	static double weight(BlockState state) {
		if (state.getBlock() instanceof LeavesBlock) {
			return LEAVES;
		}
		SoundType s = state.getSoundType();
		if (s == SoundType.WOOL || s == SoundType.SPONGE || s == SoundType.WET_SPONGE || s == SoundType.SCULK || s == SoundType.MOSS_CARPET) {
			return WOOL;
		}
		if (s == SoundType.GLASS) {
			return GLASS;
		}
		if (s == SoundType.WOOD || s == SoundType.NETHER_WOOD || s == SoundType.CHERRY_WOOD || s == SoundType.BAMBOO_WOOD || s == SoundType.BAMBOO
			|| s == SoundType.STEM || s == SoundType.LADDER || s == SoundType.SCAFFOLDING || s == SoundType.CHISELED_BOOKSHELF) {
			return WOOD;
		}
		if (s == SoundType.METAL || s == SoundType.IRON || s == SoundType.COPPER || s == SoundType.COPPER_GRATE || s == SoundType.CHAIN
			|| s == SoundType.ANVIL || s == SoundType.LANTERN || s == SoundType.NETHERITE_BLOCK || s == SoundType.LODESTONE) {
			return METAL;
		}
		if (s == SoundType.SNOW || s == SoundType.POWDER_SNOW || s == SoundType.SLIME_BLOCK || s == SoundType.HONEY_BLOCK || s == SoundType.MOSS
			|| s == SoundType.WET_GRASS || s == SoundType.STRAW_BED) {
			return SOFT;
		}
		if (s == SoundType.GRAVEL || s == SoundType.SAND || s == SoundType.GRASS || s == SoundType.SOUL_SAND || s == SoundType.SOUL_SOIL
			|| s == SoundType.ROOTED_DIRT || s == SoundType.MUD || s == SoundType.SUSPICIOUS_SAND || s == SoundType.SUSPICIOUS_GRAVEL) {
			return EARTH;
		}
		if (s == SoundType.CROP || s == SoundType.VINE || s == SoundType.WEEPING_VINES || s == SoundType.TWISTING_VINES || s == SoundType.COBWEB) {
			return THIN;
		}
		return STONE;
	}

	/** An open door, trapdoor or gate. */
	private static boolean isOpen(BlockState state) {
		return state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
	}

	/** How far (blocks) the line from {@code a} to {@code b} runs inside {@code box} (0 if it misses). */
	private static double chord(Vec3 a, Vec3 b, AABB box) {
		double[] range = {0.0, 1.0};
		if (!clip(a.x, b.x - a.x, box.minX, box.maxX, range) || !clip(a.y, b.y - a.y, box.minY, box.maxY, range)
			|| !clip(a.z, b.z - a.z, box.minZ, box.maxZ, range)) {
			return 0.0;
		}
		return (range[1] - range[0]) * a.distanceTo(b);
	}

	private static boolean clip(double start, double delta, double min, double max, double[] range) {
		if (Math.abs(delta) < 1.0E-9) {
			return start >= min && start <= max;
		}
		double t0 = (min - start) / delta, t1 = (max - start) / delta;
		range[0] = Math.max(range[0], Math.min(t0, t1));
		range[1] = Math.min(range[1], Math.max(t0, t1));
		return range[0] <= range[1];
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}
}
