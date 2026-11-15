package com.jaymoskingdoms.oldage;

import com.jaymoskingdoms.JaymosKingdoms;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
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
 * (medieval picks, cv-ruins) The cities of the old age (Lore: the old age's peoples): one structure type,
 * {@code jaymoskingdoms:old_ruins}, laid out four ways by its {@code people} ({@link People}): the Sunstep folk's in the
 * sands (a stepped pyramid over the tomb), the Pillared folk's on the plains (a columned hall), the Mound-kin's in the
 * north (a great grass barrow, and long halls for houses) and the Walled Court's in Blossom (a walled palace round a hall).
 * <ul>
 * <li>A city is a square of {@link #CELLS} by {@link #CELLS} lots, each {@link #CELL} blocks, with worn streets between
 * them. The middle four lots hold its monument; one lot beside it the temple, whose altar ({@link OldAltarBlock}) is
 * raised to the people's patron; most of the rest hold fallen houses (a chest in some, emeralds in it: the old age's
 * money); {@link #HUTS} lots near the edge hold the whole, swept huts where a few descendants still live
 * ({@link RuinFolk}).</li>
 * <li>Under the monument a stair goes down {@link #TOMB_DEPTH} blocks to a passage shut by tomb seals
 * ({@link OldAge#TOMB_SEAL}); behind them lies the ruler's chamber: the ruler asleep by the sarcophagus
 * ({@link OldRuler}), the guard asleep round the walls ({@link TombGuard}), two tomb chests.</li>
 * </ul>
 * Every building stands at the ground under its own middle (worked out with the rest of the layout, so a building split
 * between chunks agrees with itself): footings fill down to the ground, and what grew over it is cleared. The ruin's
 * wear is a hash of each block's place, so it doesn't matter which chunk draws it first.
 */
public final class OldRuins extends Structure {
	// ---- tuning ----
	/** The city's lots: how many each way, and how wide each is (blocks). */
	static final int CELLS = 6, CELL = 12;
	static final int HALF = CELLS * CELL / 2;
	/** The most the ground may rise across the city (blocks), or it isn't raised there. */
	static final int STEEPEST = 14;
	/** Of the lots left over, the share that hold a fallen house; how many hold a descendant's hut. */
	static final float HOUSE_SHARE = 0.7F;
	static final int HUTS = 3;
	/** Of a ruin's blocks, how many have fallen (more the higher they stand) and how many of the rest are worn. */
	static final float DECAY = 0.22F, DECAY_PER_BLOCK = 0.06F, WORN = 0.3F;
	/** Of the streets' blocks, how many are still paved, and how many lie in rubble; scattered fallen stones over the rest. */
	static final float PAVED = 0.55F, RUBBLE = 0.25F, STONES = 0.015F;
	/** How far under the monument's floor the tomb's floor lies (blocks). */
	static final int TOMB_DEPTH = 12;
	/** The tomb chamber's half width and depth (blocks) and its height. */
	static final int TOMB_HALF = 5, TOMB_LONG = 11, TOMB_HIGH = 5;
	/** The ruler's guard: how many sleep in the tomb. */
	static final int GUARDS_MIN = 2, GUARDS_MAX = 4;
	/** A house's chest: the share of houses that have one. */
	static final float HOUSE_CHEST = 0.45F;

	static final int STREETS = 0, MONUMENT = 1, TEMPLE = 2, HOUSE = 3, HUT = 4;

	public static final MapCodec<OldRuins> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i),
			Codec.STRING.fieldOf("people").forGetter((OldRuins r) -> r.people.id))
		.apply(i, (settings, people) -> new OldRuins(settings, People.byName(people))));
	public static StructureType<OldRuins> TYPE;
	public static StructurePieceType PIECE;
	/** All the old cities ({@code #jaymoskingdoms:old_ruins}). */
	public static final TagKey<Structure> RUINS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("old_ruins"));

	final People people;

	public OldRuins(StructureSettings settings, People people) {
		super(settings);
		this.people = people;
	}

	static void register() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, JaymosKingdoms.id("old_ruins"), () -> CODEC);
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, JaymosKingdoms.id("old_ruins"), (StructurePieceType.ContextlessType) Piece::new);
	}

	static ResourceKey<LootTable> loot(People p, String what) {
		return ResourceKey.create(Registries.LOOT_TABLE, JaymosKingdoms.id("chests/old_age/" + p.id + "_" + what));
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		int cx = context.chunkPos().getMiddleBlockX(), cz = context.chunkPos().getMiddleBlockZ();
		int ground = ground(context, cx, cz);
		int sea = context.chunkGenerator().getSeaLevel();
		if (ground <= sea || context.chunkGenerator().getFirstFreeHeight(cx, cz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
				context.randomState()) < ground) {
			return Optional.empty();
		}
		int low = ground, high = ground;
		for (int dx = -HALF; dx <= HALF; dx += HALF) {
			for (int dz = -HALF; dz <= HALF; dz += HALF) {
				int h = ground(context, cx + dx, cz + dz);
				low = Math.min(low, h);
				high = Math.max(high, h);
			}
		}
		if (high - low > STEEPEST || low <= sea - 2 || ground - TOMB_DEPTH - 4 < context.heightAccessor().getMinY() + 4) {
			return Optional.empty();
		}
		RandomSource random = context.random();
		long seed = random.nextLong();
		int turn = random.nextInt(4);
		People people = this.people;
		List<Piece> pieces = new ArrayList<>();
		pieces.add(new Piece(STREETS, people, new BlockPos(cx, ground, cz), 0, seed,
			new BoundingBox(cx - HALF, low - 3, cz - HALF, cx + HALF, high + 3, cz + HALF)));
		// the monument on the middle four lots, its tomb under it
		int[] tomb = tombBox(turn);
		pieces.add(new Piece(MONUMENT, people, new BlockPos(cx, ground, cz), turn, seed ^ 0x6D6F6EL, new BoundingBox(cx - CELL, ground - TOMB_DEPTH - 3,
			cz - CELL, cx + CELL, ground + 16, cz + CELL).encapsulate(new BoundingBox(cx + tomb[0], ground - TOMB_DEPTH - 3, cz + tomb[1],
				cx + tomb[2], ground + 2, cz + tomb[3]))));
		// the temple on a lot beside it, facing the monument
		int[][] besides = {{2, 1, 2}, {3, 1, 2}, {4, 2, 3}, {4, 3, 3}, {2, 4, 0}, {3, 4, 0}, {1, 2, 1}, {1, 3, 1}};
		int[] temple = besides[random.nextInt(besides.length)];
		boolean[][] taken = new boolean[CELLS][CELLS];
		for (int gx = 2; gx <= 3; gx++) {
			for (int gz = 2; gz <= 3; gz++) {
				taken[gx][gz] = true;
			}
		}
		// the tomb runs out under the lots its stair heads to: nothing is built over it
		for (int gx = 0; gx < CELLS; gx++) {
			for (int gz = 0; gz < CELLS; gz++) {
				int x0 = cx - HALF + gx * CELL, z0 = cz - HALF + gz * CELL;
				if (x0 + CELL > cx + tomb[0] && x0 < cx + tomb[2] + 1 && z0 + CELL > cz + tomb[1] && z0 < cz + tomb[3] + 1) {
					taken[gx][gz] = true;
				}
			}
		}
		if (taken[temple[0]][temple[1]]) {
			temple = new int[] {5 - temple[0], 5 - temple[1], (temple[2] + 2) & 3};
		}
		taken[temple[0]][temple[1]] = true;
		pieces.add(this.lot(context, TEMPLE, temple[0], temple[1], temple[2], seed ^ 0x74656DL, cx, cz));
		// the descendants' huts, near the edge
		List<int[]> edge = new ArrayList<>();
		for (int gx = 0; gx < CELLS; gx++) {
			for (int gz = 0; gz < CELLS; gz++) {
				if (!taken[gx][gz] && (gx == 0 || gz == 0 || gx == CELLS - 1 || gz == CELLS - 1)) {
					edge.add(new int[] {gx, gz});
				}
			}
		}
		for (int i = 0; i < HUTS && !edge.isEmpty(); i++) {
			int[] at = edge.remove(random.nextInt(edge.size()));
			taken[at[0]][at[1]] = true;
			pieces.add(this.lot(context, HUT, at[0], at[1], facing(at[0], at[1]), seed ^ (0x687574L + i), cx, cz));
		}
		// the fallen houses on the rest
		for (int gx = 0; gx < CELLS; gx++) {
			for (int gz = 0; gz < CELLS; gz++) {
				if (!taken[gx][gz] && random.nextFloat() < HOUSE_SHARE) {
					pieces.add(this.lot(context, HOUSE, gx, gz, random.nextInt(4), seed ^ (gx * 31L + gz * 977L), cx, cz));
				}
			}
		}
		return Optional.of(new GenerationStub(new BlockPos(cx, ground, cz), builder -> pieces.forEach(builder::addPiece)));
	}

	/** A lot's building ({@code kind}) on lot ({@code gx}, {@code gz}), its door facing {@code turn}, at the ground under its middle. */
	private Piece lot(GenerationContext context, int kind, int gx, int gz, int turn, long seed, int cx, int cz) {
		int x = cx - HALF + gx * CELL + CELL / 2, z = cz - HALF + gz * CELL + CELL / 2;
		int y = ground(context, x, z);
		int half = CELL / 2 - 1;
		return new Piece(kind, this.people, new BlockPos(x, y, z), turn, seed, new BoundingBox(x - half, y - 8, z - half, x + half, y + 12, z + half));
	}

	/** Which way a hut on the edge lot ({@code gx}, {@code gz}) faces: in toward the city's middle. */
	private static int facing(int gx, int gz) {
		if (gz == 0) {
			return 2;
		}
		if (gz == CELLS - 1) {
			return 0;
		}
		return gx == 0 ? 1 : 3;
	}

	/** The tomb's reach (min x, min z, max x, max z) round the monument's middle, for its turn. */
	static int[] tombBox(int turn) {
		int far = TOMB_DEPTH + 5 + TOMB_LONG;
		BlockPos a = turn(-TOMB_HALF - 1, 0, turn), b = turn(TOMB_HALF + 1, far, turn);
		return new int[] {Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ())};
	}

	/** Local ({@code lx}, {@code lz}) turned a quarter {@code turn} times (as {@link Rotation} turns a block). */
	static BlockPos turn(int lx, int lz, int turn) {
		return switch (turn & 3) {
			case 1 -> new BlockPos(-lz, 0, lx);
			case 2 -> new BlockPos(-lx, 0, -lz);
			case 3 -> new BlockPos(lz, 0, -lx);
			default -> new BlockPos(lx, 0, lz);
		};
	}

	private static int ground(GenerationContext context, int x, int z) {
		return context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}

	/** A hash of a block's place and a salt, as a number in [0, 1): the same whichever chunk asks. */
	static float noise(long seed, int x, int y, int z, int salt) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L) ^ (salt * 0x27D4EB2F165667C5L);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 40) / (float) (1L << 24);
	}

	// ---- the pieces ----

	public static final class Piece extends StructurePiece {
		final int kind, turn;
		final People people;
		/** The middle of its floor (the ground it stands on is a block under). */
		final BlockPos at;
		final long seed;

		Piece(int kind, People people, BlockPos at, int turn, long seed, BoundingBox box) {
			super(PIECE, 0, box);
			this.kind = kind;
			this.people = people;
			this.at = at;
			this.turn = turn;
			this.seed = seed;
		}

		Piece(CompoundTag tag) {
			super(PIECE, tag);
			this.kind = tag.getIntOr("kind", HOUSE);
			this.people = People.byId(tag.getIntOr("people", 0));
			this.at = BlockPos.of(tag.getLongOr("at", 0L));
			this.turn = tag.getIntOr("turn", 0);
			this.seed = tag.getLongOr("seed", 0L);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putInt("kind", this.kind);
			tag.putInt("people", this.people.ordinal());
			tag.putLong("at", this.at.asLong());
			tag.putInt("turn", this.turn);
			tag.putLong("seed", this.seed);
		}

		void chest(WorldGenLevel level, BoundingBox box, RandomSource random, BlockPos at, ResourceKey<LootTable> loot, BlockState state) {
			this.createChest(level, box, random, at, loot, state);
		}

		/** Whether this is a descendant's hut (where {@link RuinFolk} come to live). */
		public boolean hut() {
			return this.kind == HUT;
		}

		/** The middle of its floor. */
		public BlockPos at() {
			return this.at;
		}

		public People people() {
			return this.people;
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource chunkRandom, BoundingBox box,
				ChunkPos chunk, BlockPos reference) {
			Build b = new Build(level, box, this, chunkRandom);
			switch (this.kind) {
				case STREETS -> b.streets();
				case MONUMENT -> {
					switch (this.people) {
						case SUNSTEP -> b.pyramid();
						case PILLARED -> b.columnedHall();
						case MOUND -> b.barrow();
						case COURT -> b.palace();
					}
					b.tomb();
				}
				case TEMPLE -> b.temple();
				case HUT -> b.hut();
				default -> b.house();
			}
		}
	}

	/** One piece's building in one chunk: every block placed in the piece's own frame (turned, round its middle). */
	static final class Build {
		final WorldGenLevel level;
		final BoundingBox box;
		final Piece piece;
		final People p;
		final RandomSource random;
		final int x0, y0, z0;
		final Rotation rotation;

		Build(WorldGenLevel level, BoundingBox box, Piece piece, RandomSource random) {
			this.level = level;
			this.box = box;
			this.piece = piece;
			this.p = piece.people;
			this.random = random;
			this.x0 = piece.at.getX();
			this.y0 = piece.at.getY();
			this.z0 = piece.at.getZ();
			this.rotation = Rotation.values()[piece.turn & 3];
		}

		BlockPos pos(int lx, int y, int lz) {
			BlockPos t = turn(lx, lz, this.piece.turn);
			return new BlockPos(this.x0 + t.getX(), this.y0 + y, this.z0 + t.getZ());
		}

		float noise(int lx, int y, int lz, int salt) {
			return OldRuins.noise(this.piece.seed, lx, y, lz, salt);
		}

		/** {@code state} at local ({@code lx}, {@code y} over the floor, {@code lz}), turned with the piece. */
		void set(int lx, int y, int lz, BlockState state) {
			BlockPos at = this.pos(lx, y, lz);
			if (this.box.isInside(at) && !this.level.getBlockState(at).is(Blocks.BEDROCK)) {
				this.level.setBlock(at, state.rotate(this.rotation), 2);
			}
		}

		/** Ruined: fallen (left out) more often the higher it stands, worn now and then. */
		void ruin(int lx, int y, int lz, BlockState state) {
			if (y > 0 && this.noise(lx, y, lz, 1) < DECAY + DECAY_PER_BLOCK * y) {
				return;
			}
			this.set(lx, y, lz, this.noise(lx, y, lz, 2) < WORN && state == this.p.wall() ? this.p.worn() : state);
		}

		void air(int lx, int y, int lz) {
			BlockPos at = this.pos(lx, y, lz);
			if (this.box.isInside(at)) {
				BlockState s = this.level.getBlockState(at);
				if (!s.is(Blocks.BEDROCK) && !s.isAir()) {
					this.level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}

		void clear(int ax, int bx, int ya, int yb, int az, int bz) {
			for (int lx = ax; lx <= bx; lx++) {
				for (int lz = az; lz <= bz; lz++) {
					for (int y = ya; y <= yb; y++) {
						this.air(lx, y, lz);
					}
				}
			}
		}

		/** The floor over a footprint, footings down to the ground under it, and what grows over it cleared to {@code height}. */
		void ground(int ax, int bx, int az, int bz, int height, BlockState floor) {
			for (int lx = ax; lx <= bx; lx++) {
				for (int lz = az; lz <= bz; lz++) {
					for (int y = -1; y >= -8; y--) {
						BlockPos at = this.pos(lx, y, lz);
						if (!this.box.isInside(at)) {
							break;
						}
						BlockState s = this.level.getBlockState(at);
						if (s.isSolid() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES)) {
							break;
						}
						this.level.setBlock(at, this.p.rubble().is(Blocks.SAND) ? Blocks.SANDSTONE.defaultBlockState() : this.p.wall(), 2);
					}
					this.set(lx, 0, lz, this.noise(lx, 0, lz, 3) < 0.12F ? this.p.rubble() : floor);
					for (int y = 1; y <= height; y++) {
						this.air(lx, y, lz);
					}
				}
			}
		}

		/** A wall's ring round a footprint, {@code height} high, with a door {@code door} wide in its front (-z) side. */
		void walls(int ax, int bx, int az, int bz, int height, int door, BlockState corner) {
			for (int lx = ax; lx <= bx; lx++) {
				for (int lz = az; lz <= bz; lz++) {
					boolean edge = lx == ax || lx == bx || lz == az || lz == bz;
					if (!edge) {
						continue;
					}
					boolean cornerPost = (lx == ax || lx == bx) && (lz == az || lz == bz);
					for (int y = 1; y <= height; y++) {
						if (lz == az && Math.abs(lx - (ax + bx) / 2) <= door / 2 && y <= 2 + door / 3) {
							continue;
						}
						this.ruin(lx, y, lz, cornerPost ? corner : this.p.wall());
					}
				}
			}
		}

		BlockState top(BlockState slab) {
			return slab.hasProperty(SlabBlock.TYPE) ? slab.setValue(SlabBlock.TYPE, SlabType.TOP) : slab;
		}

		void chest(int lx, int y, int lz, ResourceKey<LootTable> loot, Direction facing) {
			BlockPos at = this.pos(lx, y, lz);
			if (this.box.isInside(at)) {
				this.piece.chest(this.level, this.box, this.random, at, loot,
					Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, this.rotation.rotate(facing)));
			}
		}

		// ---- the streets ----

		void streets() {
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			BoundingBox city = this.piece.getBoundingBox();
			for (int x = Math.max(this.box.minX(), city.minX()); x <= Math.min(this.box.maxX(), city.maxX()); x++) {
				for (int z = Math.max(this.box.minZ(), city.minZ()); z <= Math.min(this.box.maxZ(), city.maxZ()); z++) {
					int lx = x - this.x0 + HALF, lz = z - this.z0 + HALF;
					int top = this.level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
					at.set(x, top, z);
					BlockState s = this.level.getBlockState(at);
					if (!natural(s) || !this.level.getFluidState(at.above()).isEmpty()) {
						continue;
					}
					boolean street = Math.floorMod(lx, CELL) <= 1 || Math.floorMod(lx, CELL) == CELL - 1
						|| Math.floorMod(lz, CELL) <= 1 || Math.floorMod(lz, CELL) == CELL - 1;
					float n = OldRuins.noise(this.piece.seed, x, 0, z, 7);
					if (street) {
						if (n < PAVED) {
							this.level.setBlock(at, this.p.floor(), 2);
						} else if (n < PAVED + RUBBLE) {
							this.level.setBlock(at, this.p.rubble().is(Blocks.SAND) ? Blocks.SANDSTONE.defaultBlockState() : this.p.rubble(), 2);
						}
						BlockPos up = at.above();
						if (this.level.getBlockState(up).canBeReplaced() && this.level.getFluidState(up).isEmpty()) {
							this.level.setBlock(up, Blocks.AIR.defaultBlockState(), 2);
						}
					} else if (n < STONES) {
						BlockPos up = at.above();
						if (this.level.getBlockState(up).canBeReplaced() && this.level.getFluidState(up).isEmpty()) {
							this.level.setBlock(up, this.p.worn(), 2);
						}
					}
				}
			}
		}

		static boolean natural(BlockState s) {
			return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(BlockTags.BASE_STONE_OVERWORLD)
				|| s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(BlockTags.TERRACOTTA);
		}

		// ---- the monuments ----

		/** The Sunstep folk's stepped pyramid: five steps of sandstone, a shrine on top, a passage in to the stair down. */
		void pyramid() {
			int r = 10;
			this.ground(-r, r, -r, r, 13, this.p.floor());
			for (int step = 0; step < 5; step++) {
				int w = r - 2 * step;
				for (int lx = -w; lx <= w; lx++) {
					for (int lz = -w; lz <= w; lz++) {
						boolean outer = Math.abs(lx) == w || Math.abs(lz) == w;
						for (int dy = 1; dy <= 2; dy++) {
							int y = 2 * step + dy;
							BlockState s = outer && dy == 2 ? this.p.trim() : this.p.wall();
							if (outer) {
								this.ruin(lx, y, lz, s);
							} else {
								this.set(lx, y, lz, s);
							}
						}
					}
				}
			}
			// the shrine on top: four posts and a roof
			for (int lx = -1; lx <= 1; lx += 2) {
				for (int lz = -1; lz <= 1; lz += 2) {
					this.ruin(lx, 11, lz, this.p.column());
					this.ruin(lx, 12, lz, this.p.column());
				}
			}
			for (int lx = -1; lx <= 1; lx++) {
				for (int lz = -1; lz <= 1; lz++) {
					this.ruin(lx, 13, lz, this.p.roof());
				}
			}
			// the chamber inside and the passage in from the front
			this.clear(-2, 2, 1, 4, -2, 4);
			this.clear(-1, 1, 1, 3, -r, -3);
			for (int lz = -r; lz <= -3; lz += 3) {
				this.set(-2, 2, lz, this.p.trim());
				this.set(2, 2, lz, this.p.trim());
			}
		}

		/** The Pillared folk's columned hall: a ring of columns round a walled inner hall, its roof mostly fallen. */
		void columnedHall() {
			int rx = 10, rz = 8;
			this.ground(-rx - 1, rx + 1, -rz - 1, rz + 1, 10, this.p.floor());
			for (int lx = -rx - 1; lx <= rx + 1; lx++) {
				this.set(lx, 0, -rz - 1, this.p.trim());
			}
			for (int lx = -rx; lx <= rx; lx++) {
				for (int lz = -rz; lz <= rz; lz++) {
					boolean ring = Math.abs(lx) == rx || Math.abs(lz) == rz;
					if (ring && Math.floorMod(lx + lz, 2) == 0) {
						int high = 3 + (int) (this.noise(lx, 0, lz, 4) * 4.0F);
						for (int y = 1; y <= Math.min(6, high); y++) {
							this.set(lx, y, lz, this.p.column());
						}
						if (high >= 6) {
							this.set(lx, 7, lz, this.p.trim());
						}
					}
				}
			}
			// the architrave over the ring, and the roof, where they still stand
			for (int lx = -rx; lx <= rx; lx++) {
				for (int lz = -rz; lz <= rz; lz++) {
					boolean ring = Math.abs(lx) == rx || Math.abs(lz) == rz;
					if (ring && this.noise(lx, 7, lz, 5) < 0.55F) {
						this.set(lx, 7, lz, this.p.trim());
					}
					if (this.noise(lx, 8, lz, 6) < 0.35F) {
						this.set(lx, 8, lz, this.p.roof());
					}
				}
			}
			// the inner hall
			this.walls(-5, 5, -3, 7, 5, 3, this.p.column());
		}

		/** The Mound-kin's great barrow: a grass hill over a stone chamber, a passage in between standing stones. */
		void barrow() {
			int r = 11;
			for (int lx = -r; lx <= r; lx++) {
				for (int lz = -r; lz <= r; lz++) {
					double d = Math.sqrt(lx * lx + lz * lz);
					if (d > r) {
						continue;
					}
					int high = (int) Math.round(7.0 * Math.sqrt(1.0 - d * d / (r * r)));
					for (int y = -1; y >= -6; y--) {
						BlockPos at = this.pos(lx, y, lz);
						if (!this.box.isInside(at) || this.level.getBlockState(at).isSolid()) {
							break;
						}
						this.level.setBlock(at, Blocks.DIRT.defaultBlockState(), 2);
					}
					this.set(lx, 0, lz, Blocks.DIRT.defaultBlockState());
					for (int y = 1; y <= high; y++) {
						this.set(lx, y, lz, y == high ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState());
					}
					for (int y = high + 1; y <= 10; y++) {
						this.air(lx, y, lz);
					}
				}
			}
			// the chamber, lined in stone
			for (int lx = -3; lx <= 3; lx++) {
				for (int lz = -3; lz <= 5; lz++) {
					boolean edge = Math.abs(lx) == 3 || lz == -3 || lz == 5;
					this.set(lx, 0, lz, this.p.floor());
					for (int y = 1; y <= 4; y++) {
						if (edge) {
							this.set(lx, y, lz, this.noise(lx, y, lz, 2) < WORN ? this.p.worn() : this.p.wall());
						} else {
							this.air(lx, y, lz);
						}
					}
					this.set(lx, 5, lz, this.p.wall());
				}
			}
			// the passage in, timbered
			for (int lz = -r; lz <= -3; lz++) {
				for (int lx = -2; lx <= 2; lx++) {
					this.set(lx, 0, lz, this.p.floor());
					for (int y = 1; y <= 3; y++) {
						if (Math.abs(lx) == 2) {
							this.set(lx, y, lz, Math.floorMod(lz, 3) == 0 ? this.p.column() : this.p.wall());
						} else {
							this.air(lx, y, lz);
						}
					}
					this.set(lx, 4, lz, Math.floorMod(lz, 3) == 0 ? this.p.trim() : this.p.wall());
				}
			}
			// standing stones at the mouth
			for (int side = -1; side <= 1; side += 2) {
				for (int y = 1; y <= 3; y++) {
					this.set(3 * side, y, -r - 1, Blocks.STONE.defaultBlockState());
				}
			}
		}

		/** The Walled Court's palace: a wall with corner towers and a gate, round a courtyard and a hall under a tiled roof. */
		void palace() {
			int r = 11;
			this.ground(-r, r, -r, r, 12, this.p.floor());
			for (int lx = -r; lx <= r; lx++) {
				for (int lz = -r; lz <= r; lz++) {
					boolean ring = Math.abs(lx) == r || Math.abs(lz) == r;
					boolean tower = Math.abs(lx) >= r - 2 && Math.abs(lz) >= r - 2;
					if (tower) {
						for (int y = 1; y <= 7; y++) {
							this.ruin(lx, y, lz, (Math.abs(lx) == r - 2 || Math.abs(lx) == r) && (Math.abs(lz) == r - 2 || Math.abs(lz) == r)
								? this.p.column() : this.p.wall());
						}
						this.ruin(lx, 8, lz, this.p.roof());
					} else if (ring) {
						boolean gate = lz == -r && Math.abs(lx) <= 1;
						for (int y = 1; y <= 5; y++) {
							if (gate && y <= 3) {
								continue;
							}
							this.ruin(lx, y, lz, y == 5 ? this.p.trim() : this.p.wall());
						}
						if (Math.floorMod(lx + lz, 2) == 0) {
							this.ruin(lx, 6, lz, this.p.wall());
						}
					}
				}
			}
			// the hall
			int ax = -6, bx = 6, az = -2, bz = 8;
			for (int lx = ax; lx <= bx; lx++) {
				for (int lz = az; lz <= bz; lz++) {
					boolean edge = lx == ax || lx == bx || lz == az || lz == bz;
					if (!edge) {
						continue;
					}
					boolean post = Math.floorMod(lx - ax, 3) == 0 && (lz == az || lz == bz) || Math.floorMod(lz - az, 3) == 0 && (lx == ax || lx == bx);
					for (int y = 1; y <= 5; y++) {
						if (lz == az && Math.abs(lx) <= 1 && y <= 3) {
							continue;
						}
						this.ruin(lx, y, lz, post ? this.p.column() : y == 5 ? this.p.trim() : this.p.wall());
					}
				}
			}
			// its roof, in three tiers
			for (int tier = 0; tier < 3; tier++) {
				int w = 7 - 2 * tier;
				for (int lx = -w; lx <= w; lx++) {
					for (int lz = az - 1 + 2 * tier; lz <= bz + 1 - 2 * tier; lz++) {
						if (this.noise(lx, 6 + tier, lz, 6) < 0.8F) {
							this.set(lx, 6 + tier, lz, this.p.roof());
						}
					}
				}
			}
		}

		// ---- the tomb ----

		/** The stair down from the monument, the passage, the seals, and the ruler's chamber with its sleepers and chests. */
		void tomb() {
			int bottom = -TOMB_DEPTH;
			BlockState stair = this.p.stairs().setValue(StairBlock.FACING, Direction.NORTH);
			// the stair, three wide, down toward +z
			for (int i = 0; i < TOMB_DEPTH; i++) {
				int y = -1 - i, lz = i;
				for (int lx = -2; lx <= 2; lx++) {
					if (Math.abs(lx) == 2) {
						for (int dy = 0; dy <= 4; dy++) {
							this.wallIn(lx, y + dy, lz);
						}
					} else {
						this.set(lx, y - 1, lz, this.p.wall());
						this.set(lx, y, lz, stair);
						for (int dy = 1; dy <= 4; dy++) {
							this.air(lx, y + dy, lz);
						}
						this.wallIn(lx, y + 5, lz);
					}
				}
			}
			// the passage to the seals
			int pa = TOMB_DEPTH, pb = TOMB_DEPTH + 3, seal = TOMB_DEPTH + 4;
			for (int lz = pa; lz <= seal; lz++) {
				for (int lx = -2; lx <= 2; lx++) {
					this.set(lx, bottom - 1, lz, this.p.floor());
					for (int dy = 0; dy <= 3; dy++) {
						if (Math.abs(lx) == 2 || dy == 3) {
							this.set(lx, bottom + dy, lz, this.p.wall());
						} else if (lz == seal) {
							this.set(lx, bottom + dy, lz, OldAge.TOMB_SEAL.defaultBlockState());
						} else if (lz <= pb) {
							this.air(lx, bottom + dy, lz);
						}
					}
				}
			}
			// the chamber's front wall either side of the seals
			for (int lx = -TOMB_HALF - 1; lx <= TOMB_HALF + 1; lx++) {
				if (Math.abs(lx) > 2) {
					for (int dy = -1; dy <= TOMB_HIGH; dy++) {
						this.set(lx, bottom + dy, seal, this.p.wall());
					}
				}
			}
			// the chamber
			int ca = seal + 1, cb = seal + TOMB_LONG;
			for (int lx = -TOMB_HALF - 1; lx <= TOMB_HALF + 1; lx++) {
				for (int lz = ca; lz <= cb + 1; lz++) {
					boolean edge = Math.abs(lx) == TOMB_HALF + 1 || lz == cb + 1;
					this.set(lx, bottom - 1, lz, this.p.floor());
					for (int dy = 0; dy <= TOMB_HIGH; dy++) {
						if (dy == TOMB_HIGH) {
							this.set(lx, bottom + dy, lz, this.p.wall());
						} else if (edge) {
							this.set(lx, bottom + dy, lz, dy == 2 ? this.p.trim() : this.p.wall());
						} else {
							this.air(lx, bottom + dy, lz);
						}
					}
				}
			}
			int mid = (ca + cb) / 2;
			for (int side = -1; side <= 1; side += 2) {
				for (int lz = ca + 2; lz <= cb - 1; lz += 4) {
					for (int dy = 0; dy < TOMB_HIGH; dy++) {
						this.set(3 * side, bottom + dy, lz, this.p.column());
					}
				}
			}
			// the sarcophagus
			for (int lz = mid; lz <= mid + 2; lz++) {
				this.set(0, bottom, lz, this.p.trim());
				this.set(0, bottom + 1, lz, this.p.roof());
			}
			this.chest(-TOMB_HALF, bottom, cb, OldRuins.loot(this.p, "tomb"), Direction.NORTH);
			this.chest(TOMB_HALF, bottom, cb, OldRuins.loot(this.p, "tomb"), Direction.NORTH);
			// the sleepers: the ruler at the sarcophagus's foot, the guard by the columns
			this.sleeper(true, 0, bottom, mid - 1, 0);
			int guards = GUARDS_MIN + (int) (this.noise(0, 0, 0, 9) * (GUARDS_MAX - GUARDS_MIN + 1));
			int[][] spots = {{-4, ca + 1}, {4, ca + 1}, {-4, cb - 2}, {4, cb - 2}};
			for (int i = 0; i < Math.min(guards, spots.length); i++) {
				this.sleeper(false, spots[i][0], bottom, spots[i][1], i + 1);
			}
		}

		/** A wall block where the stair's side or roof meets the ground, so nothing falls in. */
		void wallIn(int lx, int y, int lz) {
			BlockPos at = this.pos(lx, y, lz);
			if (this.box.isInside(at)) {
				BlockState s = this.level.getBlockState(at);
				if (!s.is(Blocks.BEDROCK) && (!s.isSolid() || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || !s.getFluidState().isEmpty())) {
					this.level.setBlock(at, this.p.wall(), 2);
				}
			}
		}

		/** The ruler or one of the guard, asleep at local ({@code lx}, {@code y}, {@code lz}), facing the seals. */
		void sleeper(boolean ruler, int lx, int y, int lz, int n) {
			BlockPos at = this.pos(lx, y, lz);
			if (!this.box.isInside(at)) {
				return;
			}
			float yaw = this.rotation.rotate(Direction.NORTH).toYRot();
			if (ruler) {
				OldRuler r = OldAge.RULER.create(this.level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (r != null) {
					r.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
					r.settle(this.p, at, true);
					this.level.addFreshEntityWithPassengers(r);
				}
			} else {
				TombGuard g = OldAge.GUARD.create(this.level.getLevel(), EntitySpawnReason.STRUCTURE);
				if (g != null) {
					g.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
					g.settle(this.p, at, true, n % 2 == 0);
					this.level.addFreshEntityWithPassengers(g);
				}
			}
		}

		// ---- the temple, the houses, the huts ----

		/** The temple: a platform, columns at its front, walls behind, and the patron's altar at the back. */
		void temple() {
			this.ground(-5, 5, -4, 4, 8, this.p.floor());
			for (int lx = -5; lx <= 5; lx++) {
				this.set(lx, 0, -4, this.p.trim());
			}
			for (int lx = -5; lx <= 5; lx += 2) {
				if (Math.abs(lx) == 1) {
					continue;
				}
				int high = 3 + (int) (this.noise(lx, 0, -4, 4) * 3.0F);
				for (int y = 1; y <= Math.min(5, high); y++) {
					this.set(lx, y, -4, this.p.column());
				}
			}
			for (int lx = -5; lx <= 5; lx++) {
				for (int lz = -2; lz <= 4; lz++) {
					if (Math.abs(lx) == 5 || lz == 4) {
						for (int y = 1; y <= 5; y++) {
							this.ruin(lx, y, lz, y == 5 ? this.p.trim() : this.p.wall());
						}
					}
					if (this.noise(lx, 6, lz, 6) < 0.45F) {
						this.set(lx, 6, lz, this.p.roof());
					}
				}
			}
			this.set(0, 0, 3, this.p.trim());
			this.set(0, 1, 3, OldAge.ALTAR.defaultBlockState().setValue(OldAltarBlock.PATRON, this.p.patron.ordinal()).setValue(OldAltarBlock.LIT, true));
			this.set(-2, 1, 3, Blocks.DECORATED_POT.defaultBlockState());
			this.set(2, 1, 3, Blocks.DECORATED_POT.defaultBlockState());
		}

		/** A fallen house (the Mound-kin's: a long timber hall): low walls, a scrap of roof, now and then its chest. */
		void house() {
			int w, d;
			boolean hall = this.p == People.MOUND;
			if (hall) {
				w = 4;
				d = 2;
			} else {
				w = 2 + (int) (this.noise(0, 0, 0, 11) * 2.0F);
				d = 2 + (int) (this.noise(0, 0, 0, 12) * 2.0F);
			}
			this.ground(-w, w, -d, d, 6, this.p.floor());
			BlockState side = hall ? this.p.planks() : this.p.wall();
			for (int lx = -w; lx <= w; lx++) {
				for (int lz = -d; lz <= d; lz++) {
					boolean edge = Math.abs(lx) == w || Math.abs(lz) == d;
					if (!edge) {
						continue;
					}
					boolean post = Math.abs(lx) == w && Math.abs(lz) == d || hall && Math.floorMod(lx, 2) == 0 && Math.abs(lz) == d;
					int high = 1 + (int) (this.noise(lx, 0, lz, 4) * 4.0F);
					for (int y = 1; y <= high; y++) {
						if (lz == -d && lx == 0 && y <= 2) {
							continue;
						}
						this.set(lx, y, lz, post ? this.p.column() : this.noise(lx, y, lz, 2) < WORN && !hall ? this.p.worn() : side);
					}
				}
			}
			for (int lx = -w; lx <= w; lx++) {
				for (int lz = -d; lz <= d; lz++) {
					if (this.noise(lx, 5, lz, 6) < 0.2F) {
						this.set(lx, 5, lz, this.p.roof());
					} else if (Math.abs(lx) < w && Math.abs(lz) < d && this.noise(lx, 1, lz, 8) < 0.08F) {
						this.set(lx, 1, lz, this.p.rubble().is(Blocks.SAND) ? Blocks.SAND.defaultBlockState() : this.p.worn());
					}
				}
			}
			if (this.noise(0, 0, 0, 13) < HOUSE_CHEST) {
				this.chest(w - 1, 1, d - 1, OldRuins.loot(this.p, "house"), Direction.NORTH);
			}
		}

		/** A descendant's hut: whole and swept, its own people's stone, a lantern, a barrel and a bench. */
		void hut() {
			this.ground(-2, 2, -2, 2, 6, this.p.planks());
			for (int lx = -2; lx <= 2; lx++) {
				for (int lz = -2; lz <= 2; lz++) {
					boolean edge = Math.abs(lx) == 2 || Math.abs(lz) == 2, corner = Math.abs(lx) == 2 && Math.abs(lz) == 2;
					for (int y = 1; y <= 3; y++) {
						if (!edge || lz == -2 && lx == 0 && y <= 2) {
							this.air(lx, y, lz);
						} else {
							this.set(lx, y, lz, corner ? this.p.column() : y == 2 && (lx == 0 || lz == 0) ? this.p.trim() : this.p.wall());
						}
					}
				}
			}
			for (int lx = -3; lx <= 3; lx++) {
				for (int lz = -3; lz <= 3; lz++) {
					boolean rim = Math.abs(lx) == 3 || Math.abs(lz) == 3;
					this.set(lx, 4, lz, rim ? this.top(this.p.roof()) : this.p.planks());
				}
			}
			this.set(1, 1, 1, Blocks.BARREL.defaultBlockState());
			this.set(-1, 1, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
			this.set(1, 1, -1, this.p.roof());
			this.set(0, 3, 0, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		}
	}
}
