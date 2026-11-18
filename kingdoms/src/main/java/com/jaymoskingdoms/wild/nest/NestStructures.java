package com.jaymoskingdoms.wild.nest;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * (medieval picks, de-nests, de-broodqueen) The spider nests' two places:
 * <ul>
 * <li><b>The brood queen's chamber</b> ({@code jaymoskingdoms:brood_lair}): at the deepest of a nest (the lowest point of
 * the silk deeps under its start), a great round chamber walled in spider stone and roofed in nest silk, with tunnels
 * running off into the caves, egg sacs round its floor and roof, skins and cobwebs; in its middle a silk mound with her egg
 * on it ({@link BroodEggBlock}, which lays her asleep beside it).</li>
 * <li><b>The watcher's hut</b> ({@code jaymoskingdoms:nest_watch_hut}): a small spruce hut on dry ground over a nest, long
 * left and hung with webs; its chest ({@code chests/nest_watch_hut}) keeps a map down to the nearest queen's chamber
 * (vanilla's exploration map, to {@code #jaymoskingdoms:on_nest_maps}).</li>
 * </ul>
 * Lore: the silk deeps (Lore.md): above, a web-hung hut where someone once watched the nest keeps a map down.
 */
public final class NestStructures {
	// ---- tuning ----
	/** The chamber is looked for between these heights; its floor is this far above the lowest point of the nest found. */
	static final int LAIR_LOWEST = -52, LAIR_HIGHEST = 40, LAIR_LIFT = 3;
	/** It must lie at least this far under the ground's top (blocks). */
	static final int LAIR_COVER = 24;
	/** The chamber's half width and height (blocks); its tunnels: how many, how long, how wide. */
	static final int CHAMBER_R = 10, CHAMBER_H = 7, TUNNELS = 3, TUNNEL_LEAST = 14, TUNNEL_MOST = 22;
	static final double TUNNEL_R = 2.2;
	/** Egg sacs on its floor and roof, skins, cobwebs. */
	static final int LAIR_EGGS = 10, LAIR_HUNG = 6, LAIR_SKINS = 14, LAIR_WEBS = 24;
	/** The hut is looked for over a nest this far down under it (blocks, at most), testing every so many. */
	static final int HUT_NEST_DEPTH = 90, HUT_NEST_STEP = 8;
	/** Of the air round the hut's walls and roof, the share webbed over. */
	static final float HUT_WEBS = 0.35F;

	public static StructureType<BroodLair> LAIR;
	public static StructureType<WatchHut> HUT;
	public static StructurePieceType LAIR_PIECE, HUT_PIECE;
	static final ResourceKey<LootTable> HUT_CHEST = ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("chests/nest_watch_hut"));

	private NestStructures() {
	}

	static void register() {
		LAIR = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, JaymosKingdoms.id("brood_lair"), () -> BroodLair.CODEC);
		HUT = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, JaymosKingdoms.id("nest_watch_hut"), () -> WatchHut.CODEC);
		LAIR_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, JaymosKingdoms.id("brood_lair"),
			(StructurePieceType.ContextlessType) LairPiece::new);
		HUT_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, JaymosKingdoms.id("nest_watch_hut"),
			(StructurePieceType.ContextlessType) HutPiece::new);
	}

	/** Whether the biome at {@code (x, y, z)} is the silk deeps, as the structure's own biome lookup sees it. */
	static boolean nestAt(Structure.GenerationContext context, int x, int y, int z) {
		return context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))
			.is(SilkDeeps.SILK_DEEPS);
	}

	// ---- the queen's chamber ----

	public static final class BroodLair extends Structure {
		public static final MapCodec<BroodLair> CODEC = simpleCodec(BroodLair::new);

		public BroodLair(StructureSettings settings) {
			super(settings);
		}

		@Override
		protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
			int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
			int ground = context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			int lowest = Math.max(LAIR_LOWEST, context.heightAccessor().getMinY() + CHAMBER_R);
			for (int y = lowest; y <= Math.min(LAIR_HIGHEST, ground - LAIR_COVER); y += 4) {
				if (nestAt(context, x, y, z)) {
					BlockPos floor = new BlockPos(x, y + LAIR_LIFT, z);
					long seed = context.random().nextLong();
					return Optional.of(new GenerationStub(floor, builder -> builder.addPiece(new LairPiece(floor, seed))));
				}
			}
			return Optional.empty();
		}

		@Override
		public StructureType<?> type() {
			return LAIR;
		}
	}

	public static final class LairPiece extends StructurePiece {
		private final BlockPos floor;
		private final long seed;

		LairPiece(BlockPos floor, long seed) {
			super(LAIR_PIECE, 0, new BoundingBox(floor.getX() - CHAMBER_R - TUNNEL_MOST - 3, floor.getY() - 8, floor.getZ() - CHAMBER_R - TUNNEL_MOST - 3,
				floor.getX() + CHAMBER_R + TUNNEL_MOST + 3, floor.getY() + CHAMBER_H + 10, floor.getZ() + CHAMBER_R + TUNNEL_MOST + 3));
			this.floor = floor;
			this.seed = seed;
		}

		LairPiece(CompoundTag tag) {
			super(LAIR_PIECE, tag);
			this.floor = BlockPos.of(tag.getLongOr("floor", 0L));
			this.seed = tag.getLongOr("seed", 0L);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putLong("floor", this.floor.asLong());
			tag.putLong("seed", this.seed);
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource chunkRandom, BoundingBox box,
				ChunkPos chunk, BlockPos reference) {
			RandomSource random = RandomSource.create(this.seed);
			int cx = this.floor.getX(), cy = this.floor.getY(), cz = this.floor.getZ();
			double mid = cy + CHAMBER_H * 0.45;
			// the chamber: a squat dome, open floor at cy
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int x = cx - CHAMBER_R - 2; x <= cx + CHAMBER_R + 2; x++) {
				for (int z = cz - CHAMBER_R - 2; z <= cz + CHAMBER_R + 2; z++) {
					for (int y = cy - 2; y <= cy + CHAMBER_H + 2; y++) {
						p.set(x, y, z);
						if (!box.isInside(p)) {
							continue;
						}
						double dx = (x - cx) / (double) CHAMBER_R, dz = (z - cz) / (double) CHAMBER_R;
						double dy = (y - mid) / (CHAMBER_H * 0.6);
						double r = Math.sqrt(dx * dx + dz * dz + dy * dy);
						if (y >= cy && r <= 1.0) {
							this.dig(level, p);
						} else if (r <= 1.0 + 1.6 / CHAMBER_R || y == cy - 1 && dx * dx + dz * dz <= 1.0) {
							this.wall(level, p, y > mid);
						}
					}
				}
			}
			// tunnels off into the caves
			for (int t = 0; t < TUNNELS; t++) {
				double yaw = random.nextDouble() * Math.PI * 2.0, pitch = (random.nextDouble() - 0.5) * 0.5;
				int length = CHAMBER_R + TUNNEL_LEAST + random.nextInt(TUNNEL_MOST - TUNNEL_LEAST + 1);
				for (int s = CHAMBER_R - 2; s <= length; s++) {
					double tx = cx + Math.cos(yaw) * s, tz = cz + Math.sin(yaw) * s, ty = cy + 1.5 + Math.sin(pitch) * (s - CHAMBER_R);
					this.bore(level, box, tx, ty, tz);
				}
			}
			// her mound and her egg
			for (Direction d : Direction.Plane.HORIZONTAL) {
				this.put(level, box, this.floor.relative(d), SpiderNests.NEST_SILK.defaultBlockState());
			}
			this.put(level, box, this.floor, SpiderNests.NEST_SILK.defaultBlockState());
			this.put(level, box, this.floor.above(), SpiderNests.NEST_SILK.defaultBlockState());
			this.put(level, box, this.floor.above(2), SpiderNests.BROOD_EGG.defaultBlockState());
			// her brood round about
			for (int i = 0; i < LAIR_EGGS; i++) {
				double a = random.nextDouble() * Math.PI * 2.0, r = 4.0 + random.nextDouble() * (CHAMBER_R - 5.0);
				BlockPos at = BlockPos.containing(cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r);
				if (box.isInside(at) && level.getBlockState(at).isAir()) {
					EggSacBlock.lay(level, at, false, 60 + random.nextInt(60));
				}
			}
			for (int i = 0; i < LAIR_HUNG; i++) {
				BlockPos at = this.roofOver(level, box, cx + random.nextInt(CHAMBER_R * 2 - 3) - CHAMBER_R + 2, cz + random.nextInt(CHAMBER_R * 2 - 3) - CHAMBER_R + 2);
				if (at != null && box.isInside(at)) {
					EggSacBlock.lay(level, at, true, 60 + random.nextInt(60));
				}
			}
			for (int i = 0; i < LAIR_SKINS; i++) {
				BlockPos at = new BlockPos(cx + random.nextInt(CHAMBER_R * 2 - 1) - CHAMBER_R + 1, cy, cz + random.nextInt(CHAMBER_R * 2 - 1) - CHAMBER_R + 1);
				if (box.isInside(at) && level.getBlockState(at).isAir() && at.distManhattan(this.floor) > 3) {
					level.setBlock(at, SpiderNests.SHED_SKIN.defaultBlockState(), 2);
				}
			}
			for (int i = 0; i < LAIR_WEBS; i++) {
				BlockPos at = this.roofOver(level, box, cx + random.nextInt(CHAMBER_R * 2 - 1) - CHAMBER_R + 1, cz + random.nextInt(CHAMBER_R * 2 - 1) - CHAMBER_R + 1);
				if (at != null && box.isInside(at) && at.getY() > cy + 3) {
					level.setBlock(at, Blocks.COBWEB.defaultBlockState(), 2);
				}
			}
		}

		/** The open spot just under the chamber's roof over column (x, z), or null (also when the column is outside box, the chunk being built). */
		private BlockPos roofOver(WorldGenLevel level, BoundingBox box, int x, int z) {
			if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) {
				return null;
			}
			for (int y = this.floor.getY() + CHAMBER_H + 1; y > this.floor.getY() + 1; y--) {
				BlockPos at = new BlockPos(x, y, z);
				if (level.getBlockState(at).isAir() && !level.getBlockState(at.above()).isAir()) {
					return at;
				}
			}
			return null;
		}

		private void dig(WorldGenLevel level, BlockPos p) {
			BlockState s = level.getBlockState(p);
			if (!s.is(Blocks.BEDROCK) && !s.hasBlockEntity()) {
				level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), 2);
			}
		}

		/** The chamber's shell: rock (and any water or lava against it) to spider stone, the roof to nest silk. */
		private void wall(WorldGenLevel level, BlockPos p, boolean roof) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.BEDROCK) || s.hasBlockEntity() || s.isAir() && level.getFluidState(p).isEmpty()) {
				return;
			}
			level.setBlock(p, (roof && level.getFluidState(p).isEmpty() ? SpiderNests.NEST_SILK : SpiderNests.SPIDER_STONE).defaultBlockState(), 2);
		}

		private void bore(WorldGenLevel level, BoundingBox box, double tx, double ty, double tz) {
			int r = (int) Math.ceil(TUNNEL_R) + 1;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int x = (int) Math.floor(tx) - r; x <= tx + r; x++) {
				for (int y = (int) Math.floor(ty) - r; y <= ty + r; y++) {
					for (int z = (int) Math.floor(tz) - r; z <= tz + r; z++) {
						p.set(x, y, z);
						if (!box.isInside(p)) {
							continue;
						}
						double d = Math.sqrt((x + 0.5 - tx) * (x + 0.5 - tx) + (y + 0.5 - ty) * (y + 0.5 - ty) * 1.4 + (z + 0.5 - tz) * (z + 0.5 - tz));
						if (d <= TUNNEL_R) {
							this.dig(level, p);
						} else if (d <= TUNNEL_R + 1.0 && !level.getFluidState(p).isEmpty()) {
							level.setBlock(p, SpiderNests.SPIDER_STONE.defaultBlockState(), 2);
						}
					}
				}
			}
		}

		private void put(WorldGenLevel level, BoundingBox box, BlockPos p, BlockState state) {
			if (box.isInside(p)) {
				level.setBlock(p, state, 2);
			}
		}
	}

	// ---- the watcher's hut ----

	public static final class WatchHut extends Structure {
		public static final MapCodec<WatchHut> CODEC = simpleCodec(WatchHut::new);

		public WatchHut(StructureSettings settings) {
			super(settings);
		}

		@Override
		protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
			int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
			int ground = context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			int floor = context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
			if (floor < ground || ground <= context.chunkGenerator().getSeaLevel()) {
				// under water: no hut
				return Optional.empty();
			}
			// only over a nest
			boolean over = false;
			for (int y = ground - 30; y >= ground - HUT_NEST_DEPTH && !over; y -= HUT_NEST_STEP) {
				over = nestAt(context, x, y, z);
			}
			if (!over) {
				return Optional.empty();
			}
			BlockPos at = new BlockPos(x, ground, z);
			int turn = context.random().nextInt(4);
			return Optional.of(new GenerationStub(at, builder -> builder.addPiece(new HutPiece(at, turn))));
		}

		@Override
		public StructureType<?> type() {
			return HUT;
		}
	}

	public static final class HutPiece extends StructurePiece {
		private final BlockPos at;
		private final int turn;

		HutPiece(BlockPos at, int turn) {
			super(HUT_PIECE, 0, new BoundingBox(at.getX() - 3, at.getY() - 5, at.getZ() - 3, at.getX() + 3, at.getY() + 6, at.getZ() + 3));
			this.at = at;
			this.turn = turn;
		}

		HutPiece(CompoundTag tag) {
			super(HUT_PIECE, tag);
			this.at = BlockPos.of(tag.getLongOr("at", 0L));
			this.turn = tag.getIntOr("turn", 0);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putLong("at", this.at.asLong());
			tag.putInt("turn", this.turn);
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource chunkRandom, BoundingBox box,
				ChunkPos chunk, BlockPos reference) {
			RandomSource random = RandomSource.create(this.at.asLong() * 31L + 7L);
			Direction door = Direction.from2DDataValue(this.turn);
			int x0 = this.at.getX(), y0 = this.at.getY(), z0 = this.at.getZ();
			BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState(), log = Blocks.SPRUCE_LOG.defaultBlockState();
			List<BlockPos> shell = new ArrayList<>();
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					// footings down to the ground
					for (int y = y0 - 1; y >= y0 - 5; y--) {
						BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
						if (!box.isInside(p) || level.getBlockState(p).isSolid()) {
							break;
						}
						level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
					}
					boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
					this.put(level, box, x0 + dx, y0, z0 + dz, planks);
					for (int y = 1; y <= 3; y++) {
						BlockPos p = new BlockPos(x0 + dx, y0 + y, z0 + dz);
						BlockState s = Blocks.AIR.defaultBlockState();
						if (corner) {
							s = log;
						} else if (edge) {
							boolean doorway = dx == door.getStepX() * 2 && dz == door.getStepZ() * 2 && y <= 2
								|| door.getStepX() == 0 && dz == door.getStepZ() * 2 && dx == 0 && y <= 2
								|| door.getStepZ() == 0 && dx == door.getStepX() * 2 && dz == 0 && y <= 2;
							boolean window = y == 2 && (dx == 0 || dz == 0) && !doorway;
							s = doorway || window ? Blocks.AIR.defaultBlockState() : planks;
							if (!doorway && !window) {
								shell.add(p);
							}
						}
						this.put(level, box, p.getX(), p.getY(), p.getZ(), s);
					}
				}
			}
			// the roof, a little wider than the walls
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
					BlockState roof = rim ? Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP) : planks;
					this.put(level, box, x0 + dx, y0 + 4, z0 + dz, roof);
					if (!rim) {
						shell.add(new BlockPos(x0 + dx, y0 + 4, z0 + dz));
					}
				}
			}
			// inside: the watcher's chest against the back wall, a lantern, a stool, and the webs that came in
			Direction back = door.getOpposite();
			BlockPos chest = new BlockPos(x0 + back.getStepX(), y0 + 1, z0 + back.getStepZ()).relative(back.getClockWise());
			this.createChest(level, box, random, chest, HUT_CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, door));
			this.put(level, box, x0 + back.getCounterClockWise().getStepX(), y0 + 1, z0 + back.getCounterClockWise().getStepZ(),
				Blocks.LANTERN.defaultBlockState());
			this.put(level, box, x0 + back.getStepX() + back.getCounterClockWise().getStepX(), y0 + 1,
				z0 + back.getStepZ() + back.getCounterClockWise().getStepZ(), Blocks.SPRUCE_SLAB.defaultBlockState());
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (random.nextFloat() < 0.4F) {
						this.web(level, box, new BlockPos(x0 + dx, y0 + 3, z0 + dz));
					}
				}
			}
			// and the webs over it
			for (BlockPos p : shell) {
				for (Direction d : Direction.values()) {
					if (random.nextFloat() < HUT_WEBS) {
						this.web(level, box, p.relative(d));
					}
				}
				if (p.getY() == y0 + 4 && random.nextFloat() < 0.15F) {
					this.put(level, box, p.getX(), p.getY(), p.getZ(), SpiderNests.NEST_SILK.defaultBlockState());
				}
			}
		}

		/** A cobweb at {@code p} if it's open air outside the hut's walls, or under its roof. */
		private void web(WorldGenLevel level, BoundingBox box, BlockPos p) {
			if (box.isInside(p) && level.getBlockState(p).isAir() && level.getBlockState(p.below()).getBlock() != Blocks.CHEST) {
				level.setBlock(p, Blocks.COBWEB.defaultBlockState(), 2);
			}
		}

		private void put(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
			BlockPos p = new BlockPos(x, y, z);
			if (box.isInside(p)) {
				level.setBlock(p, state, 2);
			}
		}
	}
}
