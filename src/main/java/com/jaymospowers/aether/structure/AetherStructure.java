package com.jaymospowers.aether.structure;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * The Aether II structures other than the Sentry Ruins ({@code jaymospowers:aether_structure}, one structure type; each structure's
 * JSON names a {@link Plan} and optionally a variant). Pieces are built in code ({@link AetherPiece}); this finds a spot that
 * suits the plan (flat ground, or rock all round for the buried ones) and lays the pieces out. Data (biome tags, spacing) is in
 * {@code worldgen/structure} and {@code worldgen/structure_set}, written by {@code tools/gen_aether_structures.py}.
 */
public final class AetherStructure extends Structure {
	public enum Plan implements StringRepresentable {
		OUTPOST, CAMP, TOWER, DEN, RUINS, HENGE, LIBRARY, AQUEDUCT, BREXALLEN, MINESHAFT, IRRADIATED, GUARDIAN;

		public static final Codec<Plan> CODEC = StringRepresentable.fromEnum(Plan::values);

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	// ---- sizes: footprint (across, along), how high the building stands, and how uneven the ground may be ----
	public static final int OUTPOST_SIZE = 22, OUTPOST_H = 9, OUTPOST_TALL_H = 18;
	public static final int CAMP_SIZE = 31, CAMP_H = 8;
	public static final int TOWER_W = 9, TOWER_L = 10;
	/** The three towers' heights up to the platform (the roof stands above). */
	public static final int[] TOWER_HEIGHTS = {16, 20, 24};
	public static final int DEN_SIZE = 9, DEN_H = 7;
	public static final int RUINS_SIZE = 27, RUINS_H = 10;
	public static final int HENGE_SIZE = 27, HENGE_H = 9;
	/** The library: 25 across and 36 along (the entrance building, the stairs down, the hall) and this deep below the ground. */
	public static final int LIBRARY_W = 25, LIBRARY_L = 36, LIBRARY_DEPTH = 11, LIBRARY_H = 10;
	public static final int AQUEDUCT_W = 9, AQUEDUCT_L = 49, AQUEDUCT_H = 14;
	public static final int BREXALLEN_SIZE = 21, BREXALLEN_H = 8;
	public static final int IRRADIATED_SIZE = 23, IRRADIATED_H = 10, BUNKER_DEPTH = 4;
	public static final int GUARDIAN_SIZE = 33, GUARDIAN_H = 80;
	/** How uneven (blocks, highest column to lowest of nine) the ground under a surface structure may be. */
	public static final int TOLERANCE = 3, WIDE_TOLERANCE = 5, AQUEDUCT_TOLERANCE = 12;
	/** The mineshaft: corridors from the hub, branches and the farthest a piece may reach, and how deep (below ground) it lies. */
	public static final int MINE_CORRIDORS = 14, MINE_DEPTH = 18, MINE_REACH = 72;

	public static final MapCodec<AetherStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i),
		Plan.CODEC.fieldOf("plan").forGetter(s -> s.plan),
		Codec.INT.optionalFieldOf("variant", -1).forGetter(s -> s.variant)).apply(i, AetherStructure::new));
	public static StructureType<AetherStructure> TYPE;

	private final Plan plan;
	private final int variant;

	public AetherStructure(StructureSettings settings, Plan plan, int variant) {
		super(settings);
		this.plan = plan;
		this.variant = variant;
	}

	static void init() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, JaymosPowers.id("aether_structure"), () -> CODEC);
		AetherPiece.TYPE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, JaymosPowers.id("aether_piece"),
			(StructurePieceType.ContextlessType)AetherPiece::new);
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}

	private static BoundingBox centered(int cx, int cz, int across, int along, Direction facing, int minY, int height) {
		boolean swap = facing.getAxis() == Direction.Axis.X;
		int w = swap ? along : across, l = swap ? across : along;
		int x0 = cx - w / 2, z0 = cz - l / 2;
		return new BoundingBox(x0, minY, z0, x0 + w - 1, minY + height - 1, z0 + l - 1);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int cx = chunk.getMiddleBlockX(), cz = chunk.getMiddleBlockZ();
		WorldgenRandom random = context.random();
		Ground ground = new Ground(context);
		Direction facing = Direction.from2DDataValue(random.nextInt(4));
		long seed = random.nextLong();
		int variant = this.variant;
		return switch (this.plan) {
			case OUTPOST -> {
				variant = variant < 0 ? random.nextInt(2) : variant;
				yield surface(ground, AetherPiece.Kind.OUTPOST, cx, cz, OUTPOST_SIZE, OUTPOST_SIZE, variant == 0 ? OUTPOST_H : OUTPOST_TALL_H, TOLERANCE, facing, seed, variant, 0);
			}
			case CAMP -> surface(ground, AetherPiece.Kind.CAMP, cx, cz, CAMP_SIZE, CAMP_SIZE, CAMP_H, WIDE_TOLERANCE, facing, seed, Math.max(variant, 0), 0);
			case TOWER -> {
				variant = variant < 0 ? random.nextInt(TOWER_HEIGHTS.length) : variant;
				yield surface(ground, AetherPiece.Kind.TOWER, cx, cz, TOWER_W, TOWER_L, TOWER_HEIGHTS[variant] + 10, TOLERANCE, facing, seed, variant, 0);
			}
			case DEN -> {
				variant = variant < 0 ? random.nextInt(3) : variant;
				yield surface(ground, AetherPiece.Kind.DEN, cx, cz, DEN_SIZE, DEN_SIZE, DEN_H, TOLERANCE, facing, seed, variant, 0);
			}
			case RUINS -> surface(ground, AetherPiece.Kind.RUINS, cx, cz, RUINS_SIZE, RUINS_SIZE, RUINS_H, WIDE_TOLERANCE, facing, seed, Math.max(variant, 0), 0);
			case HENGE -> surface(ground, AetherPiece.Kind.HENGE, cx, cz, HENGE_SIZE, HENGE_SIZE, HENGE_H, WIDE_TOLERANCE, facing, seed, 0, 0);
			case IRRADIATED -> {
				variant = Math.max(variant, 0);
				// the bunker is sunk into the ground
				yield surface(ground, AetherPiece.Kind.IRRADIATED, cx, cz, IRRADIATED_SIZE, IRRADIATED_SIZE, IRRADIATED_H + BUNKER_DEPTH, WIDE_TOLERANCE, facing, seed, variant,
					variant == 0 ? BUNKER_DEPTH : 0);
			}
			case GUARDIAN -> surface(ground, AetherPiece.Kind.GUARDIAN, cx, cz, GUARDIAN_SIZE, GUARDIAN_SIZE, GUARDIAN_H, WIDE_TOLERANCE, facing, seed, 0, 0);
			case LIBRARY -> library(ground, cx, cz, facing, seed, Math.max(variant, 0));
			case AQUEDUCT -> aqueduct(ground, cx, cz, facing, seed);
			case BREXALLEN -> brexallen(ground, random, cx, cz, facing, seed);
			case MINESHAFT -> mineshaft(ground, random, cx, cz);
		};
	}

	/** A single surface piece on flat ground (sunk {@code sink} blocks, for a bunker). */
	private static Optional<GenerationStub> surface(Ground ground, AetherPiece.Kind kind, int cx, int cz, int across, int along, int height, int tolerance,
		Direction facing, long seed, int variant, int sink) {
		int y = ground.flat(cx, cz, across / 2, along / 2, tolerance);
		if (y == Ground.NONE) {
			return Optional.empty();
		}
		BoundingBox box = centered(cx, cz, across, along, facing, y - sink, height);
		AetherPiece piece = new AetherPiece(kind, box, facing, seed, variant);
		return Optional.of(new GenerationStub(new BlockPos(cx, y, cz), builder -> builder.addPiece(piece)));
	}

	private static Optional<GenerationStub> library(Ground ground, int cx, int cz, Direction facing, long seed, int variant) {
		int y = ground.flat(cx, cz, LIBRARY_W / 2, LIBRARY_L / 2, TOLERANCE);
		if (y == Ground.NONE) {
			return Optional.empty();
		}
		// rock under the hall: the nine sample columns must be solid right under its floor
		int floor = y - LIBRARY_DEPTH;
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				if (!ground.solid(cx + i * LIBRARY_W / 2, floor - 2, cz + j * LIBRARY_L / 2)) {
					return Optional.empty();
				}
			}
		}
		BoundingBox box = centered(cx, cz, LIBRARY_W, LIBRARY_L, facing, floor, LIBRARY_DEPTH + LIBRARY_H);
		AetherPiece piece = new AetherPiece(AetherPiece.Kind.LIBRARY, box, facing, seed, variant);
		return Optional.of(new GenerationStub(new BlockPos(cx, y, cz), builder -> builder.addPiece(piece)));
	}

	private static Optional<GenerationStub> aqueduct(Ground ground, int cx, int cz, Direction facing, long seed) {
		int along = AQUEDUCT_L / 2;
		boolean x = facing.getAxis() == Direction.Axis.X;
		int y = ground.flat(cx, cz, x ? along : AQUEDUCT_W / 2, x ? AQUEDUCT_W / 2 : along, AQUEDUCT_TOLERANCE, true);
		if (y == Ground.NONE) {
			return Optional.empty();
		}
		BoundingBox box = centered(cx, cz, AQUEDUCT_W, AQUEDUCT_L, facing, y, AQUEDUCT_H);
		AetherPiece piece = new AetherPiece(AetherPiece.Kind.AQUEDUCT, box, facing, seed, 0);
		return Optional.of(new GenerationStub(new BlockPos(cx, y, cz), builder -> builder.addPiece(piece)));
	}

	private static Optional<GenerationStub> brexallen(Ground ground, WorldgenRandom random, int cx, int cz, Direction facing, long seed) {
		int top = ground.top(cx, cz);
		int floor = top - 14 - random.nextInt(14);
		if (top == Ground.NONE || floor - 1 < -24) {
			return Optional.empty();
		}
		BoundingBox box = centered(cx, cz, BREXALLEN_SIZE, BREXALLEN_SIZE, facing, floor - 1, BREXALLEN_H);
		if (!ground.buried(box)) {
			return Optional.empty();
		}
		AetherPiece piece = new AetherPiece(AetherPiece.Kind.BREXALLEN, box, facing, seed, random.nextInt(3));
		return Optional.of(new GenerationStub(new BlockPos(cx, floor, cz), builder -> builder.addPiece(piece)));
	}

	private static BoundingBox beyond(int ox, int oz, Direction d, int along, int across, int minY, int height) {
		// a box starting at (ox, oz), running `along` in direction d, `across` wide, centred on that line
		int half = across / 2;
		int x0, x1, z0, z1;
		switch (d) {
			case SOUTH -> {
				x0 = ox - half;
				x1 = x0 + across - 1;
				z0 = oz;
				z1 = oz + along - 1;
			}
			case NORTH -> {
				x0 = ox - half;
				x1 = x0 + across - 1;
				z1 = oz;
				z0 = oz - along + 1;
			}
			case EAST -> {
				z0 = oz - half;
				z1 = z0 + across - 1;
				x0 = ox;
				x1 = ox + along - 1;
			}
			default -> {
				z0 = oz - half;
				z1 = z0 + across - 1;
				x1 = ox;
				x0 = ox - along + 1;
			}
		}
		return new BoundingBox(x0, minY, z0, x1, minY + height - 1, z1);
	}

	/** The buried mineshaft: a hub, corridors out of it that branch and end in storage rooms, all kept inside the rock. */
	private static Optional<GenerationStub> mineshaft(Ground ground, WorldgenRandom random, int cx, int cz) {
		int top = ground.top(cx, cz);
		int floor = top - MINE_DEPTH - random.nextInt(8);
		if (top == Ground.NONE || floor - 1 < -24) {
			return Optional.empty();
		}
		int minY = floor - 1;
		int hub = 9;
		BoundingBox hubBox = new BoundingBox(cx - hub / 2, minY, cz - hub / 2, cx + hub / 2, minY + 6, cz + hub / 2);
		if (!ground.buried(hubBox)) {
			return Optional.empty();
		}
		List<AetherPiece> pieces = new ArrayList<>();
		List<BoundingBox> boxes = new ArrayList<>();
		pieces.add(new AetherPiece(AetherPiece.Kind.MINE_HUB, hubBox, Direction.NORTH, random.nextLong(), 0));
		boxes.add(hubBox);
		// open ends: (x, z, direction leaving it, steps from the hub)
		List<int[]> ends = new ArrayList<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			ends.add(new int[] {cx + d.getStepX() * (hub / 2 + 1), cz + d.getStepZ() * (hub / 2 + 1), d.get2DDataValue(), 0});
		}
		int made = 0;
		while (!ends.isEmpty() && made < MINE_CORRIDORS) {
			int[] end = ends.remove(random.nextInt(ends.size()));
			Direction d = Direction.from2DDataValue(end[2]);
			int len = 10 + random.nextInt(11);
			BoundingBox tunnel = beyond(end[0], end[1], d, len, 5, minY, 6);
			if (Math.abs(tunnel.minX() - cx) > MINE_REACH || Math.abs(tunnel.maxX() - cx) > MINE_REACH || Math.abs(tunnel.minZ() - cz) > MINE_REACH
				|| Math.abs(tunnel.maxZ() - cz) > MINE_REACH || !ground.buried(tunnel)) {
				continue;
			}
			made++;
			pieces.add(new AetherPiece(AetherPiece.Kind.MINE_TUNNEL, tunnel, d, random.nextLong(), 0));
			int ex = end[0] + d.getStepX() * len, ez = end[1] + d.getStepZ() * len;
			boolean room = end[3] >= 1 && random.nextInt(3) > 0;
			if (room) {
				// a storage room beyond the end of the corridor
				BoundingBox box = beyond(ex, ez, d, 9, 9, minY, 6);
				if (ground.buried(box)) {
					pieces.add(new AetherPiece(AetherPiece.Kind.MINE_ROOM, box, d, random.nextLong(), 0));
				}
			} else if (end[3] < 3) {
				// a junction: onward, and to each side
				ends.add(new int[] {ex, ez, d.get2DDataValue(), end[3] + 1});
				ends.add(new int[] {ex, ez, d.getClockWise().get2DDataValue(), end[3] + 1});
				ends.add(new int[] {ex, ez, d.getCounterClockWise().get2DDataValue(), end[3] + 1});
			}
		}
		if (pieces.size() < 3) {
			return Optional.empty();
		}
		return Optional.of(new GenerationStub(new BlockPos(cx, floor, cz), builder -> pieces.forEach(builder::addPiece)));
	}
}
