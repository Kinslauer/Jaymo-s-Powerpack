package com.jaymoskingdoms;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Seat compounds ({@code jaymoskingdoms:seat_compound}): a kingdom's seat the world generates whole, in every kind of
 * country, not only where an Epic town stands. Andy (of Hedmere's chateau): "make bits and pieces (castle, garden,
 * square, barracks, homes) generate together and allow builders to expand". In the middle, one of the seat castles the
 * pack's structure mods ship ({@link GrandSeats#CHOICES}, picked by the land and read from its own mod by id, as the
 * grand seats are); round it the town's first pieces, drawn in the land's materials by the same designs the builders
 * use ({@link Buildings}): a paved square with the bell, a walled garden with a fountain, the barracks and three to five
 * homes, each door turned toward the castle. The ground under all of it is levelled as a village's is (beard_box).
 * <p>
 * The castle comes as a lived-in seat, not a dungeon: its spawners and loot are taken out as it generates. When a
 * player first comes near the bell, the compound is founded ({@link #found}): a kingdom of its own when no realm stands
 * within {@link KingdomEvents#CAPITAL_SPACING}, its throne in the castle ({@link KingdomEvents#foundIn}); near a
 * kingdom, that kingdom's fiefdom ({@link GrimSites#foundFief}); else a village allied to the nearest. Every piece is
 * kept from the builders' tearing down ({@link Holds}), and the barracks and homes count as built, so the builders grow
 * the town outward from there as any other ({@link Planner}).
 */
public final class SeatCompounds {
	/** The structure's id. */
	static final Identifier ID = JaymosKingdoms.id("seat_compound");
	/** Chunks kept from the world's middle, where players start. */
	static final int SPAWN_CLEARANCE = 24;
	/** Homes round the castle: at least, at most. */
	static final int HOMES_MIN = 3, HOMES_MAX = 5;
	/** Blocks kept clear between two pieces; how many times a crowded piece is pushed out, and how far each time. */
	static final int GAP = 4, PUSHES = 3, PUSH_STEP = 6;
	/** Angles tried round the castle for each piece (each side of its own, in turn), and the step between them (degrees). */
	static final int ANGLE_TRIES = 8, ANGLE_STEP = 25;
	/** How far the ground under a piece may rise or fall across it. */
	static final int MAX_SLOPE = 6;
	/** The furthest a piece may reach from the compound's chunk (the game only looks 8 chunks round for structures). */
	static final int REACH = 128;
	/** Blocks cleared above a piece's roof (trees, hillside); its footing filled this deep at most. */
	static final int HEADROOM = 3, FOOTING = 10;
	/** Trees standing on a piece's ground are cleared this high over its floor (the town is laid after the trees grow). */
	static final int TREE_CLEAR = 40;
	/** Fiefdoms a seat compound may become: a kingdom this near (blocks) takes it. */
	static final double FIEF_RANGE = GrimSites.RANGE;

	public static StructureType<Compound> TYPE;
	public static StructurePieceType PIECE, CLEAN;

	/** What a ring piece is. */
	enum Kind {
		SQUARE("meeting_point_square"), GARDEN("decor_garden"), BARRACKS("barracks"), HOUSE("house");

		/** Its piece's name ({@link Seats#name}): a square and a garden are no building to seat a lord in. */
		final String name;

		Kind(String name) {
			this.name = name;
		}
	}

	/** The lands by name, for a piece kept in a saved chunk. */
	private static final Map<String, Palette> LANDS = new LinkedHashMap<>();

	static {
		LANDS.put("plains", Palette.PLAINS);
		LANDS.put("taiga", Palette.TAIGA);
		LANDS.put("snowy", Palette.SNOWY);
		LANDS.put("savanna", Palette.SAVANNA);
		LANDS.put("desert", Palette.DESERT);
		LANDS.put("badlands", Palette.BADLANDS);
		LANDS.put("jungle", Palette.JUNGLE);
		LANDS.put("swamp", Palette.SWAMP);
		LANDS.put("cherry", Palette.CHERRY);
	}

	private SeatCompounds() {
	}

	static void register() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, ID, () -> Compound.CODEC);
		PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, JaymosKingdoms.id("seat_piece"), (StructurePieceType.ContextlessType) Piece::new);
		CLEAN = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, JaymosKingdoms.id("seat_clean"), (StructurePieceType.ContextlessType) Clean::new);
	}

	/** A seat compound, by its holder. */
	static boolean is(Holder<Structure> holder) {
		return holder.unwrapKey().map(k -> k.identifier().equals(ID)).orElse(false);
	}

	/** A seat compound, by its id. */
	static boolean is(String id) {
		return ID.toString().equals(id);
	}

	/** A place a settlement may rise in: a village ({@link Seats#village}) or a seat compound. */
	static boolean settled(Holder<Structure> holder) {
		return Seats.village(holder) || is(holder);
	}

	private static String landName(Palette land) {
		for (Map.Entry<String, Palette> e : LANDS.entrySet()) {
			if (e.getValue() == land) {
				return e.getKey();
			}
		}
		return "plains";
	}

	/** A ring piece's design: the same kind, land and seed always draw the same building. */
	static Blueprint draw(Kind kind, Palette land, long seed) {
		Buildings.Kit k = Buildings.Kit.wild(land, seed);
		return switch (kind) {
			case SQUARE -> square(k);
			case GARDEN -> garden(k);
			case BARRACKS -> Buildings.barracks(k);
			case HOUSE -> Buildings.house(k);
		};
	}

	// ---- founding ---------------------------------------------------------------------------------------------------

	/**
	 * The compound round {@code bell} founded: its castle emptied of what world generation left in it, then a kingdom
	 * then a fiefdom of the kingdom near, or a village allied to the nearest (never a kingdom: {@link EpicTowns#seek}). Every piece is held, and its
	 * barracks and homes count as built. Null if nothing could be founded.
	 */
	static @Nullable Faction found(ServerLevel level, BlockPos bell, StructureStart start) {
		MinecraftServer server = level.getServer();
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof Clean) {
				GrandSeats.empty(level, piece.getBoundingBox());
			}
		}
		// a lone castle is never a kingdom: a fiefdom of the kingdom near, else a village; with none near to swear to, a kingdom
		// is looked for in an Epic town further out meanwhile
		Faction lord = nearestKingdom(server, bell);
		Faction founded = lord != null && GrimSites.count(server, lord, true) < GrimSites.FIEFS_PER_KINGDOM
			? GrimSites.foundFief(level, start, "seat_compound", lord, "message.jaymoskingdoms.seat.fiefdom") : null;
		if (founded == null) {
			founded = Villages.found(level, bell);
			Villages.ally(server, founded);
		}
		if (Factions.realmNear(server, bell, KingdomEvents.CAPITAL_SPACING) == null) {
			EpicTowns.seek(level, bell);
		}
		if (founded != null) {
			settle(server, founded, start);
			JaymosKingdoms.LOGGER.info("The seat compound by {} is {}'s ({})", bell, founded.name(), founded.type());
		}
		return founded;
	}

	/** The kingdom whose seat is nearest {@code at}, within {@link #FIEF_RANGE}. */
	private static @Nullable Faction nearestKingdom(MinecraftServer server, BlockPos at) {
		Faction best = null;
		double bestD = FIEF_RANGE * FIEF_RANGE;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() != FactionType.KINGDOM) {
				continue;
			}
			double d = f.seat().distSqr(at);
			if (d <= bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	/** Every piece held from the builders; the barracks and homes entered as built (never rebuilt to another design). */
	private static void settle(MinecraftServer server, Faction f, StructureStart start) {
		List<Works.Built> built = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			Holds.guard(server, f.id(), piece.getBoundingBox());
			if (piece instanceof Piece p && (p.kind == Kind.BARRACKS || p.kind == Kind.HOUSE)) {
				BoundingBox b = p.getBoundingBox();
				built.add(new Works.Built(p.kind.name, new BlockPos(b.minX(), b.minY(), b.minZ()), new BlockPos(b.maxX(), b.maxY(), b.maxZ()),
					Optional.empty(), 0L, 0L));
			}
		}
		if (!built.isEmpty()) {
			Works.update(server, f.id(), w -> {
				List<Works.Built> all = new ArrayList<>(w.built());
				all.addAll(built);
				return w.withBuilt(all);
			});
		}
	}

	// ---- the structure ----------------------------------------------------------------------------------------------

	public static final class Compound extends Structure {
		public static final MapCodec<Compound> CODEC = simpleCodec(Compound::new);

		public Compound(StructureSettings settings) {
			super(settings);
		}

		@Override
		protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
			ChunkPos middle = context.chunkPos();
			if ((long)middle.x() * middle.x() + (long)middle.z() * middle.z() < (long)SPAWN_CLEARANCE * SPAWN_CLEARANCE) {
				return Optional.empty();
			}
			ChunkGenerator generator = context.chunkGenerator();
			int mx = middle.getMiddleBlockX(), mz = middle.getMiddleBlockZ();
			int my = generator.getFirstOccupiedHeight(mx, mz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			Holder<Biome> biome = context.biomeResolver().getNoiseBiome(QuartPos.fromBlock(mx), QuartPos.fromBlock(my), QuartPos.fromBlock(mz));
			if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)
				|| peak(biome) || wet(context, mx, mz, my)) {
				return Optional.empty();
			}
			Palette land = Palette.of(biome);
			RandomSource random = context.random();
			StructurePiecesBuilder all = new StructurePiecesBuilder();
			List<BoundingBox> placed = new ArrayList<>();
			// the castle in the middle
			BoundingBox core = null;
			BlockPos first = null;
			List<Holder<Structure>> castles = castles(context.registryAccess(), land);
			java.util.Collections.shuffle(castles, new java.util.Random(random.nextLong()));
			for (Holder<Structure> castle : castles) {
				GenerationContext there = new GenerationContext(context.registryAccess(), generator, context.biomeSource(), context.climateSampler(),
					context.randomState(), context.structureTemplateManager(), context.seed(), middle, context.heightAccessor(), b -> true);
				Optional<GenerationStub> stub = castle.value().findValidGenerationPoint(there);
				if (stub.isEmpty() || stub.get().getPiecesBuilder().isEmpty()) {
					continue;
				}
				List<StructurePiece> pieces = new ArrayList<>();
				for (StructurePiece piece : stub.get().getPiecesBuilder().build().pieces()) {
					if (pieces.isEmpty() || !GrandSeats.outlying(Seats.name(piece))) {
						pieces.add(piece);
					}
				}
				List<StructurePiece> built = pieces.stream().filter(p -> !GrandSeats.terrain(Seats.name(p))).toList();
				BoundingBox box = StructurePiece.createBoundingBox(built.stream());
				if (box.getXSpan() > GrandSeats.MAX_SIDE || box.getZSpan() > GrandSeats.MAX_SIDE) {
					continue;
				}
				for (StructurePiece piece : pieces) {
					all.addPiece(piece);
				}
				core = box;
				first = stub.get().position();
				placed.add(box);
				break;
			}
			if (core == null) {
				return Optional.empty();
			}
			// the town's first pieces round it
			List<Kind> plan = new ArrayList<>(List.of(Kind.SQUARE, Kind.BARRACKS, Kind.GARDEN));
			int homes = HOMES_MIN + random.nextInt(HOMES_MAX - HOMES_MIN + 1);
			for (int i = 0; i < homes; i++) {
				plan.add(Kind.HOUSE);
			}
			double base = random.nextDouble() * Math.PI * 2.0;
			boolean squared = false;
			for (int i = 0; i < plan.size(); i++) {
				Kind kind = plan.get(i);
				// (the square at the front, the barracks beside it, the garden behind, the homes spread round)
				double want = switch (kind) {
					case SQUARE -> base;
					case BARRACKS -> base + Math.PI / 3.0;
					case GARDEN -> base + Math.PI;
					case HOUSE -> base - Math.PI / 3.0 - (i - 3) * (Math.PI * 4.0 / 3.0) / Math.max(1, homes);
				};
				Piece piece = this.ring(context, kind, land, random.nextLong(), want, core, placed, middle);
				if (piece != null) {
					all.addPiece(piece);
					placed.add(piece.getBoundingBox());
					squared |= kind == Kind.SQUARE;
				}
			}
			if (!squared) {
				// (no bell, nothing to found)
				return Optional.empty();
			}
			// last, the castle cleared of its spawners and loot as each chunk of it is made
			all.addPiece(new Clean(core.inflatedBy(2)));
			return Optional.of(new GenerationStub(first, Either.right(all)));
		}

		/** A ring piece set round the castle near the angle it wants, on ground level and dry enough; null if none fits. */
		private @Nullable Piece ring(GenerationContext context, Kind kind, Palette land, long seed, double want, BoundingBox core,
			List<BoundingBox> placed, ChunkPos middle) {
			Blueprint plain = draw(kind, land, seed);
			BlockPos centre = core.getCenter();
			int coreHalf = Math.max(core.getXSpan(), core.getZSpan()) / 2;
			int pieceHalf = Math.max(plain.width(), plain.depth()) / 2 + 1;
			int x0 = middle.getMinBlockX() - REACH, z0 = middle.getMinBlockZ() - REACH, x1 = middle.getMaxBlockX() + REACH,
				z1 = middle.getMaxBlockZ() + REACH;
			for (int push = 0; push <= PUSHES; push++) {
				int dist = coreHalf + pieceHalf + GAP + push * PUSH_STEP;
				for (int t = 0; t < ANGLE_TRIES; t++) {
					// its own angle, then a step either side, then two...
					int side = (t + 1) / 2 * (t % 2 == 0 ? 1 : -1);
					double angle = want + Math.toRadians(side * ANGLE_STEP);
					int cx = centre.getX() + (int)Math.round(Math.cos(angle) * dist), cz = centre.getZ() + (int)Math.round(Math.sin(angle) * dist);
					// the door toward the castle
					int dx = centre.getX() - cx, dz = centre.getZ() - cz;
					Direction door = Math.abs(dx) > Math.abs(dz) ? dx > 0 ? Direction.EAST : Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
					Rotation rotation = Planner.facing(door);
					int[] size = Planner.turned(plain, rotation);
					int ox = cx - size[0] / 2, oz = cz - size[1] / 2;
					if (ox < x0 || oz < z0 || ox + size[0] - 1 > x1 || oz + size[1] - 1 > z1) {
						continue;
					}
					BoundingBox foot = new BoundingBox(ox, 0, oz, ox + size[0] - 1, 0, oz + size[1] - 1);
					if (crowded(foot, placed)) {
						continue;
					}
					int floor = ground(context, foot);
					if (floor == Integer.MIN_VALUE) {
						continue;
					}
					return new Piece(kind, landName(land), seed, new BlockPos(ox, floor, oz), rotation, size[0], size[1], plain.height());
				}
			}
			return null;
		}

		@Override
		public StructureType<?> type() {
			return TYPE;
		}
	}

	/** The seat castles that suit {@code land} in this world (the plains' if it has none of its own). */
	private static List<Holder<Structure>> castles(RegistryAccess access, Palette land) {
		Registry<Structure> registry = access.lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> out = new ArrayList<>();
		for (GrandSeats.Choice c : GrandSeats.CHOICES) {
			if (c.suits(land)) {
				registry.get(Identifier.parse(c.id())).ifPresent(out::add);
			}
		}
		return out.isEmpty() && land != Palette.PLAINS ? castles(access, Palette.PLAINS) : out;
	}

	/** Bare peaks and steep slopes, no place for a town (a meadow or a grove under them is). */
	private static boolean peak(Holder<Biome> biome) {
		String name = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		return name.contains("peaks") || name.contains("slopes");
	}

	/** Water (or lava) on top at {@code (x, z)}, whose top block is at {@code top}. */
	private static boolean wet(Structure.GenerationContext context, int x, int z, int top) {
		return !context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState()).getBlock(top).getFluidState().isEmpty();
	}

	/** Its floor (the ground's top under its middle) if the ground under {@code foot} is level enough and dry; else MIN_VALUE. */
	private static int ground(Structure.GenerationContext context, BoundingBox foot) {
		ChunkGenerator generator = context.chunkGenerator();
		int[][] at = {{foot.minX(), foot.minZ()}, {foot.maxX(), foot.minZ()}, {foot.minX(), foot.maxZ()}, {foot.maxX(), foot.maxZ()},
			{foot.getCenter().getX(), foot.getCenter().getZ()}};
		int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE, mid = 0;
		for (int i = 0; i < at.length; i++) {
			int h = generator.getFirstOccupiedHeight(at[i][0], at[i][1], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			low = Math.min(low, h);
			high = Math.max(high, h);
			mid = h;
		}
		if (high - low > MAX_SLOPE || wet(context, at[4][0], at[4][1], mid)) {
			return Integer.MIN_VALUE;
		}
		return mid;
	}

	private static boolean crowded(BoundingBox foot, List<BoundingBox> placed) {
		BoundingBox wide = foot.inflatedBy(GAP, 0, GAP);
		for (BoundingBox other : placed) {
			if (wide.intersects(other.minX(), other.minZ(), other.maxX(), other.maxZ())) {
				return true;
			}
		}
		return false;
	}

	// ---- the pieces -------------------------------------------------------------------------------------------------

	/** A ring piece: one of the town's first buildings, drawn as the builders would draw it, laid on the levelled ground. */
	public static final class Piece extends StructurePiece {
		final Kind kind;
		private final String land;
		private final long seed;
		private final BlockPos origin;
		private final Rotation rotation;

		Piece(Kind kind, String land, long seed, BlockPos origin, Rotation rotation, int w, int d, int h) {
			super(PIECE, 0, new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + w - 1, origin.getY() + h - 1 + HEADROOM,
				origin.getZ() + d - 1));
			this.kind = kind;
			this.land = land;
			this.seed = seed;
			this.origin = origin;
			this.rotation = rotation;
		}

		Piece(CompoundTag tag) {
			super(PIECE, tag);
			this.kind = Kind.valueOf(tag.getStringOr("kind", "HOUSE"));
			this.land = tag.getStringOr("land", "plains");
			this.seed = tag.getLongOr("seed", 0L);
			this.origin = BlockPos.of(tag.getLongOr("origin", 0L));
			this.rotation = Rotation.values()[Math.floorMod(tag.getIntOr("rotation", 0), Rotation.values().length)];
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			tag.putString("kind", this.kind.name());
			tag.putString("land", this.land);
			tag.putLong("seed", this.seed);
			tag.putLong("origin", this.origin.asLong());
			tag.putInt("rotation", this.rotation.ordinal());
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
			ChunkPos chunk, BlockPos reference) {
			Blueprint bp = draw(this.kind, LANDS.getOrDefault(this.land, Palette.PLAINS), this.seed);
			BoundingBox box = this.getBoundingBox();
			Set<BlockPos> laid = new HashSet<>();
			for (Blueprint.Placement pl : bp.blocks()) {
				laid.add(this.origin.offset(Planner.turn(bp, pl.offset(), this.rotation)));
			}
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			BlockState air = Blocks.AIR.defaultBlockState();
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
						continue;
					}
					// clear what stands over it (trees, a hillside's top)
					for (int y = box.minY() + 1; y <= box.maxY(); y++) {
						at.set(x, y, z);
						if (!laid.contains(at) && !level.getBlockState(at).isAir()) {
							level.setBlock(at, air, Block.UPDATE_CLIENTS);
						}
					}
					// and the rest of any tree over it, so no crown hangs over the roofs
					for (int y = box.maxY() + 1; y <= box.minY() + TREE_CLEAR; y++) {
						at.set(x, y, z);
						BlockState there = level.getBlockState(at);
						if (there.is(BlockTags.LOGS) || there.is(BlockTags.LEAVES) || there.is(Blocks.VINE) || there.is(Blocks.BEE_NEST)) {
							level.setBlock(at, air, Block.UPDATE_CLIENTS);
						}
					}
					// a footing down to the ground
					for (int d = 1; d <= FOOTING; d++) {
						at.set(x, box.minY() - d, z);
						BlockState there = level.getBlockState(at);
						if (there.isSolid() && there.getFluidState().isEmpty()) {
							break;
						}
						level.setBlock(at, d == 1 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			for (Blueprint.Placement pl : bp.blocks()) {
				BlockPos p = this.origin.offset(Planner.turn(bp, pl.offset(), this.rotation));
				if (chunkBox.isInside(p)) {
					level.setBlock(p, pl.state().rotate(this.rotation), Block.UPDATE_CLIENTS);
				}
			}
		}

		@Override
		public String toString() {
			return "jaymoskingdoms:seat/" + this.kind.name;
		}
	}

	/** Takes the castle's spawners out and its chests' loot as each chunk of it is made (it's someone's home, not a dungeon). */
	public static final class Clean extends StructurePiece {
		Clean(BoundingBox box) {
			super(CLEAN, 0, box);
		}

		Clean(CompoundTag tag) {
			super(CLEAN, tag);
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
			ChunkPos chunk, BlockPos reference) {
			BoundingBox box = this.getBoundingBox();
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int x = Math.max(box.minX(), chunkBox.minX()); x <= Math.min(box.maxX(), chunkBox.maxX()); x++) {
				for (int z = Math.max(box.minZ(), chunkBox.minZ()); z <= Math.min(box.maxZ(), chunkBox.maxZ()); z++) {
					for (int y = box.minY(); y <= box.maxY(); y++) {
						at.set(x, y, z);
						BlockState state = level.getBlockState(at);
						if (state.is(Blocks.SPAWNER)) {
							level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (state.hasBlockEntity()) {
							BlockEntity be = level.getBlockEntity(at);
							if (be instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null) {
								chest.setLootTable(null);
							}
						}
					}
				}
			}
		}

		@Override
		public String toString() {
			return "jaymoskingdoms:seat/terrain_clean";
		}
	}

	// ---- the square and the garden ----------------------------------------------------------------------------------

	/**
	 * The town square (15 x 15): paved in the land's stone, a ring of trim round a dais in the middle with the bell on it,
	 * benches facing it on four sides, a lamp post at each corner and planted beds between them. Its open side is north
	 * (turned toward the castle).
	 */
	static Blueprint square(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("seat_square", 15, 15, 6);
		Palette p = k.p;
		for (int x = 0; x < 15; x++) {
			for (int z = 0; z < 15; z++) {
				int r = Math.max(Math.abs(x - 7), Math.abs(z - 7));
				s.set(x, 0, z, r == 3 ? p.trim() : r <= 1 ? p.wall() : p.path());
			}
		}
		// the dais and the bell
		s.set(7, 1, 7, p.wall());
		s.set(7, 2, 7, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, Direction.NORTH));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			s.set(7 + d.getStepX(), 1, 7 + d.getStepZ(), Buildings.Sketch.stair(p.stairs(), d.getOpposite()));
			// a bench on each side, its back away from the bell
			int bx = 7 + d.getStepX() * 5, bz = 7 + d.getStepZ() * 5;
			Direction across = d.getClockWise();
			for (int a = -1; a <= 1; a++) {
				s.set(bx + across.getStepX() * a, 1, bz + across.getStepZ() * a, Buildings.Sketch.stair(k.woodStairs, d));
			}
		}
		// lamp posts at the corners, beds of flowers between
		for (int[] c : new int[][] {{1, 1}, {13, 1}, {1, 13}, {13, 13}}) {
			s.set(c[0], 1, c[1], p.fence());
			s.set(c[0], 2, c[1], p.fence());
			s.set(c[0], 3, c[1], Blocks.LANTERN.defaultBlockState());
		}
		for (int[] c : new int[][] {{3, 1}, {11, 1}, {1, 3}, {13, 3}, {1, 11}, {13, 11}, {3, 13}, {11, 13}}) {
			s.set(c[0], 0, c[1], Blocks.GRASS_BLOCK.defaultBlockState());
			s.set(c[0], 1, c[1], flower(k));
		}
		return s.done();
	}

	/**
	 * The castle garden (15 x 13): a clipped hedge round it with a way in at the north, gravel walks crossing at a fountain
	 * (a basin round a lamp-topped pillar), beds of flowers and flowering bushes in the four quarters, benches by the walks.
	 */
	static Blueprint garden(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch("seat_garden", 15, 13, 6);
		Palette p = k.p;
		BlockState hedge = leaves(p);
		for (int x = 0; x < 15; x++) {
			for (int z = 0; z < 13; z++) {
				boolean edge = x == 0 || x == 14 || z == 0 || z == 12;
				boolean walk = x >= 6 && x <= 8 || z >= 5 && z <= 7;
				s.set(x, 0, z, walk && !edge ? Blocks.GRAVEL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
				if (edge) {
					boolean gate = z == 0 && x >= 6 && x <= 8;
					if (!gate) {
						s.set(x, 1, z, hedge);
						s.set(x, 2, z, hedge);
					}
				} else if (!walk) {
					// the quarters: flowers, a bush at the inner corner of each
					boolean bush = (x == 5 || x == 9) && (z == 4 || z == 8);
					s.set(x, 1, z, bush ? Blocks.FLOWERING_AZALEA.defaultBlockState() : (x + z) % 2 == 0 ? flower(k) : Blocks.SHORT_GRASS.defaultBlockState());
				}
			}
		}
		// the gate's lamps
		s.set(5, 3, 0, Blocks.LANTERN.defaultBlockState());
		s.set(9, 3, 0, Blocks.LANTERN.defaultBlockState());
		// the fountain: a basin of water round a pillar with a lamp on top
		for (int x = 5; x <= 9; x++) {
			for (int z = 4; z <= 8; z++) {
				boolean rim = x == 5 || x == 9 || z == 4 || z == 8;
				s.set(x, 0, z, p.wall());
				s.set(x, 1, z, rim ? p.slab() : Blocks.WATER.defaultBlockState());
			}
		}
		s.set(7, 1, 6, p.wall());
		s.set(7, 2, 6, p.wall());
		s.set(7, 3, 6, Blocks.LANTERN.defaultBlockState());
		// benches along the long walk, facing it
		for (int z : new int[] {2, 10}) {
			s.set(5, 1, z, Buildings.Sketch.stair(k.woodStairs, Direction.WEST));
			s.set(9, 1, z, Buildings.Sketch.stair(k.woodStairs, Direction.EAST));
		}
		return s.done();
	}

	private static final String[] FLOWERS = {"poppy", "dandelion", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
		"white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley"};

	private static BlockState flower(Buildings.Kit k) {
		String name = FLOWERS[k.nextInt(FLOWERS.length)];
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
		return (b == Blocks.AIR ? Blocks.POPPY : b).defaultBlockState();
	}

	/** Clipped hedge: the land's own leaves (oak where its wood has none), kept from decaying. */
	private static BlockState leaves(Palette p) {
		String wood = BuiltInRegistries.BLOCK.getKey(p.planks().getBlock()).getPath().replace("_planks", "");
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(wood + "_leaves"));
		BlockState state = (b == Blocks.AIR || !(b instanceof LeavesBlock) ? Blocks.OAK_LEAVES : b).defaultBlockState();
		return state.hasProperty(LeavesBlock.PERSISTENT) ? state.setValue(LeavesBlock.PERSISTENT, true) : state;
	}

}
