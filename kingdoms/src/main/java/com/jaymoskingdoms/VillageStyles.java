package com.jaymoskingdoms;

import com.jaymoskingdoms.StyleBuildings.House;
import com.jaymoskingdoms.StyleBuildings.Look;
import com.jaymoskingdoms.StyleBuildings.Roof;
import com.jaymoskingdoms.beasts.Gnomes;
import com.jaymoskingdoms.blossomland.BlossomBlocks;
import com.jaymoskingdoms.seas.Mohknee;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Village styles (Reference Mods patch 9, V1 to V13; ideas from Integrated Villages and Luki's Grand Capitals, both All
 * Rights Reserved, so nothing of theirs is used). Each style is a village of its own people's way of building, drawn in
 * code ({@link StyleBuildings}) the way {@link BlossomVillages} and {@link SteamTowns} draw theirs, and rising the same
 * way: by itself, out of sight of a player passing, where its land and its reason are right. Once up it is an ordinary
 * {@link FactionType#VILLAGE} (an elder, a shed, a builder crew, its own growth), or, for a capital, an ordinary kingdom.
 * <p>
 * Every style says where it rises and why, in its registration below: the {@code where} text is also written to the log
 * when one rises. A style is data (its ground, its layout of pieces, its streets, its names) plus up to four small
 * hooks: where to look ({@link Siter}), what the land must be ({@link Fits}), which way its gate faces ({@link Facer}),
 * and who else lives there ({@link Extra}).
 * <p>
 * Styles outside this class: the pirate coves' shacks, careening beach and fence's market ({@link CoveExtras}, V7) and the
 * illager outposts on the roads ({@link RoadOutposts}, V14).
 */
public final class VillageStyles {
	// ---- tuning ----
	/** How often (ticks) a style may rise near a player, and the chance each time. */
	static final int RISE_TICKS = 20 * 90;
	static final float RISE_CHANCE = 0.3F;
	/** How far from a player a site is looked for (at least, plus up to), and how near no player may be. */
	static final int SITE_MIN = 72, SITE_SPREAD = 60;
	static final double HIDE_RANGE = 56.0;
	/** No settlement's seat this near (blocks), no bell this near. */
	static final int SPACING = 320, BELL_CLEARANCE = 96;
	/** Tries for a site each time. */
	static final int TRIES = 6;
	/** Who moves in: villagers in each home, in each hall. */
	static final int PER_HOME = 2, PER_HALL = 1;
	/** A footing under a building on uneven ground goes down at most this far. */
	static final int FOOTING = 14;
	/** A capital: no realm's seat within this (blocks), its ground's radius, the most it may rise and fall, how high it is cleared. */
	static final int CAPITAL_REALM_SPACING = 1100, CAPITAL_RADIUS = 50, CAPITAL_SPREAD = 8, CAPITAL_CLEAR = 30, CAPITAL_HALF = 34;
	/** Two realms at peace this far apart (blocks, at least and at most) have a market town between them. */
	static final int BORDER_MIN = 380, BORDER_MAX = 1500;
	/** A crossroads' two roads cross this near (blocks) and this far from any settlement's seat. */
	static final int CROSS_NEAR = 6, CROSS_CLEAR = 90;
	/** Hill-holes keep this far from every road (blocks). */
	static final int HAMLET_ROAD_CLEAR = 150;

	enum Ground {
		/** Levelled flat to the bell's floor. */
		LEVEL,
		/** Each building on the ground it stands on, down a slope or into a hillside. */
		SLOPE,
		/** On legs over shallow water, joined by boardwalks at the water's own height. */
		WATER
	}

	enum Role {
		HOME, HALL, DECOR
	}

	/** What a piece is drawn from: the level, the village, a random source, and (lazily) its settlement's own kit. */
	record Ctx(ServerLevel level, Faction village, RandomSource random) {
		Buildings.Kit kit() {
			long v = this.random.nextLong();
			return Buildings.Kit.of(this.level, this.village, v == 0L ? 1L : v, v == 0L ? 1L : v);
		}
	}

	/** One building: its place in the bell's frame (its front's middle; the gate side is -z), turns clockwise from the gate, and how it is drawn. */
	record Piece(Role role, int x, int z, int turns, Function<Ctx, @Nullable Blueprint> make) {
	}

	@FunctionalInterface
	interface Fits {
		boolean ok(ServerLevel level, BlockPos at);
	}

	@FunctionalInterface
	interface Siter {
		@Nullable BlockPos pick(ServerLevel level, ServerPlayer player, RandomSource random);
	}

	@FunctionalInterface
	interface Facer {
		Direction face(ServerLevel level, BlockPos at, Direction towardPlayer);
	}

	@FunctionalInterface
	interface Extra {
		void run(Ctx ctx, BlockPos bell, Direction gate, List<BlockPos> homes);
	}

	static final class Style {
		final String id, where;
		Ground ground = Ground.LEVEL;
		int radius = 22, spread = 6, clear = 14, sink = 0, apart = 900;
		boolean mohknee, capital;
		Fits fits = (l, p) -> true;
		@Nullable Siter siter;
		@Nullable Facer facer;
		@Nullable Extra extra;
		String haven = "";
		BlockState road = Blocks.GRAVEL.defaultBlockState(), road2 = Blocks.COBBLESTONE.defaultBlockState(), foot = Blocks.DIRT.defaultBlockState();
		int[][] streets = {};
		String[] first = {"Old"}, second = {"ford"};
		final List<Piece> pieces = new ArrayList<>();

		Style(String id, String where) {
			this.id = id;
			this.where = where;
		}

		Style ground(Ground v, int radius, int spread, int clear) {
			this.ground = v;
			this.radius = radius;
			this.spread = spread;
			this.clear = clear;
			return this;
		}

		Style names(String[] first, String[] second) {
			this.first = first;
			this.second = second;
			return this;
		}

		Style roads(BlockState a, BlockState b, BlockState foot, int[]... streets) {
			this.road = a;
			this.road2 = b;
			this.foot = foot;
			this.streets = streets;
			return this;
		}

		Style fits(Fits v) {
			this.fits = v;
			return this;
		}

		Style piece(Role role, int x, int z, int turns, Function<Ctx, @Nullable Blueprint> make) {
			this.pieces.add(new Piece(role, x, z, turns, make));
			return this;
		}
	}

	/** Settlement id to the style it was raised in (on the Overworld), kept for spacing and for whoever asks. */
	static final AttachmentType<Map<String, String>> STYLE_OF = AttachmentRegistry.create(JaymosKingdoms.id("village_styles"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	/** The realms either side of a border market's site (x, z packed) while it is being looked at: their ids. */
	private static final Map<Long, int[]> BORDERS = new HashMap<>();

	static final List<Style> STYLES = new ArrayList<>();

	private VillageStyles() {
	}

	static void register() {
		VillageStyleSet.fill(STYLES);
		ServerTickEvents.END_SERVER_TICK.register(VillageStyles::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BORDERS.clear());
	}

	/** The style {@code f} was raised in, or null. */
	static @Nullable String styleOf(MinecraftServer server, @Nullable Faction f) {
		return f == null ? null : Factions.level(server).getAttachedOrElse(STYLE_OF, Map.of()).get(Integer.toString(f.id()));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % RISE_TICKS == 0 && level.getRandom().nextFloat() < RISE_CHANCE) {
			tryRise(level);
		}
	}

	// ---- rising --------------------------------------------------------------------------------------------------------

	private static void tryRise(ServerLevel level) {
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator() && Stacked.at(level, p.blockPosition(), Level.OVERWORLD)).toList();
		if (players.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		List<Style> order = new ArrayList<>(STYLES);
		Collections.shuffle(order, new java.util.Random(random.nextLong()));
		int tries = 0;
		for (Style style : order) {
			if (tries++ >= TRIES) {
				return;
			}
			ServerPlayer player = players.get(random.nextInt(players.size()));
			BlockPos at = style.siter != null ? style.siter.pick(level, player, random) : ring(player, random);
			if (at == null || !style.fits.ok(level, at) || !open(level, style, at)) {
				continue;
			}
			int floor = floorFor(level, style, at);
			if (floor == Integer.MIN_VALUE) {
				continue;
			}
			Direction toward = Direction.getApproximateNearest(player.getX() - at.getX(), 0, player.getZ() - at.getZ());
			Direction gate = style.facer != null ? style.facer.face(level, at, toward) : toward;
			if (style.capital) {
				if (foundCapital(level, style, at, floor, gate) != null) {
					return;
				}
			} else {
				found(level, style, at, floor, gate);
				return;
			}
		}
	}

	static BlockPos ring(ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int r = SITE_MIN + random.nextInt(SITE_SPREAD);
		return player.blockPosition().offset((int)Math.round(Math.cos(angle) * r), 0, (int)Math.round(Math.sin(angle) * r));
	}

	/** {@code at} is free for {@code style}: loaded, its land nobody's, no settlement or bell near, none of its own kind near, out of sight. */
	private static boolean open(ServerLevel level, Style style, BlockPos at) {
		MinecraftServer server = level.getServer();
		if (!level.isPositionEntityTicking(at) || Mohknee.on(level, at) != style.mohknee) {
			return false;
		}
		int r = style.radius;
		for (BlockPos corner : new BlockPos[] {at, at.offset(-r, 0, -r), at.offset(r, 0, -r), at.offset(-r, 0, r), at.offset(r, 0, r)}) {
			if (!Loaded.made(level, corner) || Factions.at(server, level, corner) != null) {
				return false;
			}
		}
		Map<String, String> styles = Factions.level(server).getAttachedOrElse(STYLE_OF, Map.of());
		for (Faction f : Factions.all(server)) {
			double apart = styles.containsKey(Integer.toString(f.id())) && styles.get(Integer.toString(f.id())).equals(style.id) ? style.apart : SPACING;
			if (Raids.horizontal(f.seat(), at) < apart * apart) {
				return false;
			}
		}
		if (Raids.playerNear(level, at, HIDE_RANGE)) {
			return false;
		}
		return level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), at, BELL_CLEARANCE, PoiManager.Occupancy.ANY).isEmpty();
	}

	/** The bell's floor: the ground's median (levelled and sloping styles), or the water's surface (a bog town); MIN_VALUE if the ground won't do. */
	private static int floorFor(ServerLevel level, Style style, BlockPos at) {
		if (style.ground != Ground.WATER) {
			return IllagerBuildings.floor(level, at, style.spread, style.radius);
		}
		int wet = 0, all = 0, low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
		for (int dx = -style.radius; dx <= style.radius; dx += 4) {
			for (int dz = -style.radius; dz <= style.radius; dz += 4) {
				BlockPos column = at.offset(dx, 0, dz);
				if (!Loaded.made(level, column)) {
					return Integer.MIN_VALUE;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, column);
				all++;
				if (!level.getFluidState(top.below()).isEmpty()) {
					wet++;
				}
				low = Math.min(low, top.getY());
				high = Math.max(high, top.getY());
			}
		}
		return wet * 5 >= all * 2 && high - low <= style.spread ? level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, at).getY() : Integer.MIN_VALUE;
	}

	// ---- founding ------------------------------------------------------------------------------------------------------

	/** The y a building's layer 0 is laid at, at the frame's column (x, z). */
	private static int anchorY(ServerLevel level, Style style, Role role, int x, int z, int floor) {
		return switch (style.ground) {
			case LEVEL -> floor - 1;
			case WATER -> floor - 1 - (role == Role.DECOR ? 0 : style.sink);
			case SLOPE -> groundY(level, x, z);
		};
	}

	/** The top of the ground at (x, z): down through trunks, leaves and plants. */
	static int groundY(ServerLevel level, int x, int z) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		int y = top.getY() - 1;
		while (y > level.getMinY()) {
			BlockState s = level.getBlockState(new BlockPos(x, y, z));
			if (s.isAir() || s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.canBeReplaced()) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	/** Sets {@code state} at {@code pos}, and (on uneven ground and over water) a footing of {@code foot} under it. */
	private static void lay(ServerLevel level, Style style, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (style.ground != Ground.LEVEL) {
			for (int i = 1; i <= FOOTING; i++) {
				BlockPos under = pos.below(i);
				BlockState there = level.getBlockState(under);
				if (!(there.isAir() || there.canBeReplaced() || !level.getFluidState(under).isEmpty() || there.is(net.minecraft.tags.BlockTags.LEAVES))) {
					break;
				}
				level.setBlock(under, style.foot, Block.UPDATE_CLIENTS);
			}
		}
	}

	static Direction turned(Direction gate, int turns) {
		Direction facing = gate;
		for (int i = 0; i < turns; i++) {
			facing = facing.getClockWise();
		}
		return facing;
	}

	/**
	 * Places {@code bp} with its front's middle at the frame's (x, z), turned, on {@code anchorY}; first clears what stands in
	 * its way on uneven ground, then lays it, then its footing. Returns the middle of its floor.
	 */
	private static BlockPos place(ServerLevel level, Style style, Blueprint bp, Piece spot, BlockPos bell, Direction gate, int floor, @Nullable List<Works.Built> built) {
		CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
		BlockPos column = frame.at(spot.x(), 0, spot.z());
		int y = anchorY(level, style, spot.role(), column.getX(), column.getZ(), floor);
		CampBuildings.Placer placer = new CampBuildings.Placer(new BlockPos(column.getX(), y, column.getZ()), turned(gate, spot.turns()), bp.width());
		if (style.ground != Ground.LEVEL) {
			Set<Long> columns = new HashSet<>();
			for (Blueprint.Placement p : bp.blocks()) {
				BlockPos at = placer.at(p.offset());
				if (columns.add(BlockPos.asLong(at.getX(), 0, at.getZ()))) {
					for (int dy = 1; dy <= bp.height() + 1; dy++) {
						BlockPos above = at.atY(y + dy);
						BlockState s = level.getBlockState(above);
						if (!s.isAir() && (s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES) || Castle.natural(s))) {
							level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
			}
		}
		for (Blueprint.Placement p : bp.blocks()) {
			BlockPos at = placer.at(p.offset());
			if (p.offset().getY() == 0 && style.ground != Ground.LEVEL && !p.state().isAir()) {
				lay(level, style, at, placer.turn(p.state()));
			} else {
				level.setBlock(at, placer.turn(p.state()), Block.UPDATE_ALL);
			}
		}
		if (built != null) {
			BlockPos a = placer.at(0, 0, 0), b = placer.at(bp.width() - 1, bp.height() - 1, bp.depth() - 1);
			built.add(new Works.Built(bp.id(), new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ())),
				new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()))));
		}
		return placer.at(bp.width() / 2, 1, bp.depth() / 2);
	}

	/** A new village of {@code style} round {@code center} on {@code floor}, its gate toward {@code gate}: built, named, peopled. */
	static Faction found(ServerLevel level, Style style, BlockPos center, int floor, Direction gate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos bell = new BlockPos(center.getX(), floor, center.getZ());
		if (style.ground == Ground.LEVEL) {
			CampBuildings.level(level, bell, style.radius, style.clear);
		}
		CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
		for (int[] street : style.streets) {
			for (int x = street[0]; x <= street[2]; x++) {
				for (int z = street[1]; z <= street[3]; z++) {
					BlockPos col = frame.at(x, 0, z);
					int y = style.ground == Ground.SLOPE ? groundY(level, col.getX(), col.getZ()) : floor - 1;
					BlockState road = Math.floorMod(x * 7 + z * 13, 5) == 0 ? style.road2 : style.road;
					// (a boardwalk stands on posts every fourth block; a slope's path lies on the ground)
					if (style.ground == Ground.WATER && Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0) {
						lay(level, style, col.atY(y), road);
					} else {
						level.setBlock(col.atY(y), road, Block.UPDATE_ALL);
					}
				}
			}
		}
		lay(level, style, bell.below(), Blocks.STONE_BRICKS.defaultBlockState());
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
		Faction village = Faction.create(Factions.nextId(server), FactionType.VILLAGE, name(server, style, random), DyeColor.values()[random.nextInt(DyeColor.values().length)],
			bell, KingdomEvents.claim(server, bell, Villages.CLAIM_RADIUS));
		Factions.put(server, village);
		int id = village.id();
		Ctx ctx = new Ctx(level, village, random);
		List<Works.Built> built = new ArrayList<>();
		List<BlockPos> homes = new ArrayList<>(), halls = new ArrayList<>();
		for (Piece piece : style.pieces) {
			Blueprint bp = piece.make().apply(ctx);
			if (bp == null) {
				continue;
			}
			BlockPos middle = place(level, style, bp, piece, bell, gate, floor, piece.role() == Role.DECOR ? null : built);
			if (piece.role() == Role.HOME) {
				homes.add(middle);
			} else if (piece.role() == Role.HALL) {
				halls.add(middle);
			}
		}
		Works.update(server, id, w -> {
			List<Works.Built> all = new ArrayList<>(w.built());
			all.addAll(built);
			return w.withBuilt(all);
		});
		Storehouse.build(level, Factions.get(server, id));
		for (BlockPos home : homes) {
			for (int i = 0; i < PER_HOME; i++) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, home, 4), EntitySpawnReason.STRUCTURE);
			}
		}
		for (BlockPos hall : halls) {
			for (int i = 0; i < PER_HALL; i++) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, hall, 4), EntitySpawnReason.STRUCTURE);
			}
		}
		EntityTypes.IRON_GOLEM.spawn(level, Construction.standable(level, frame.at(0, 0, -6), 4), EntitySpawnReason.STRUCTURE);
		if (style.extra != null) {
			style.extra.run(ctx, bell, gate, homes);
		}
		Faction stored = Factions.get(server, id);
		if (stored != null) {
			if (!style.haven.isEmpty()) {
				Havens.make(level, stored, style.haven);
			}
			Villages.chooseElder(level, stored);
		}
		WalledTowns.front(server, id, gate);
		remember(server, id, style);
		JaymosKingdoms.LOGGER.info("A {} village, {}, rose at {}: {}", style.id, village.name(), bell, style.where);
		Faction out = Factions.get(server, id);
		return out != null ? out : village;
	}

	static void remember(MinecraftServer server, int id, Style style) {
		ServerLevel store = Factions.level(server);
		Map<String, String> next = new HashMap<>(store.getAttachedOrElse(STYLE_OF, Map.of()));
		next.put(Integer.toString(id), style.id);
		store.setAttached(STYLE_OF, Map.copyOf(next));
	}

	private static String name(MinecraftServer server, Style style, RandomSource random) {
		String name = "";
		for (int tries = 0; tries < 20; tries++) {
			name = style.first[random.nextInt(style.first.length)] + style.second[random.nextInt(style.second.length)];
			if (Factions.byName(server, name) == null) {
				return name;
			}
		}
		return name + Factions.nextId(server);
	}

	/**
	 * Stamps {@code bp} (its front toward {@code facing}, its front's middle at {@code anchor}, y = 0 on the anchor's y): what stands in
	 * its way cleared, a footing of {@code foot} under its lowest layer. For the pirate coves and the road outposts.
	 */
	static void stamp(ServerLevel level, Blueprint bp, BlockPos anchor, Direction facing, BlockState foot) {
		CampBuildings.Placer placer = new CampBuildings.Placer(anchor, facing, bp.width());
		Style plain = new Style("stamp", "");
		plain.ground = Ground.SLOPE;
		plain.foot = foot;
		Set<Long> columns = new HashSet<>();
		for (Blueprint.Placement p : bp.blocks()) {
			BlockPos at = placer.at(p.offset());
			if (columns.add(BlockPos.asLong(at.getX(), 0, at.getZ()))) {
				for (int dy = 1; dy <= bp.height() + 1; dy++) {
					BlockPos above = at.atY(anchor.getY() + dy);
					BlockState s = level.getBlockState(above);
					if (!s.isAir() && (s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LOGS) || s.is(net.minecraft.tags.BlockTags.LEAVES) || Castle.natural(s))) {
						level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		for (Blueprint.Placement p : bp.blocks()) {
			BlockPos at = placer.at(p.offset());
			if (p.offset().getY() == 0 && !p.state().isAir()) {
				lay(level, plain, at, placer.turn(p.state()));
			} else {
				level.setBlock(at, placer.turn(p.state()), Block.UPDATE_ALL);
			}
		}
	}

	// ---- capitals ----------------------------------------------------------------------------------------------------

	/**
	 * A capital (V13): a kingdom raised whole inside walls, in its land's own materials ({@link Palette#at}: so a desert
	 * capital is sandstone and a taiga one cobble and spruce): a gatehouse and a main street from it, houses either side, a
	 * market quarter round the bell (four markets, a well, an inn), a temple on each side behind it, barracks, a smithy and a
	 * mason in the flanking quarters, and the keep at the back (the seat: a throne in its hall, a panic room under it).
	 * Null if the kingdom could not be raised.
	 */
	private static @Nullable Faction foundCapital(ServerLevel level, Style style, BlockPos center, int floor, Direction gate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos bell = new BlockPos(center.getX(), floor, center.getZ());
		CampBuildings.level(level, bell, CAPITAL_RADIUS, CAPITAL_CLEAR);
		CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
		Faction kingdom = KingdomEvents.create(level, bell);
		int id = kingdom.id();
		Buildings.Kit kit = Buildings.Kit.of(level, kingdom);
		Look look = Look.of(kit);
		// the wall, with a gap for the gate, and the streets
		int h = CAPITAL_HALF;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) != h || (z == -h && Math.abs(x) <= 4)) {
					continue;
				}
				for (int y = 0; y <= 5; y++) {
					level.setBlock(frame.at(x, y, z), y == 5 && (x + z) % 2 != 0 ? Blocks.AIR.defaultBlockState() : kit.p.wall(), Block.UPDATE_ALL);
				}
			}
		}
		int[][] streets = {{-2, -h - 2, 2, h - 3}, {-h + 1, -2, h - 1, 2}};
		for (int[] s : streets) {
			for (int x = s[0]; x <= s[2]; x++) {
				for (int z = s[1]; z <= s[3]; z++) {
					level.setBlock(frame.at(x, -1, z), kit.road, Block.UPDATE_ALL);
				}
			}
		}
		for (int z = -h + 2; z < h - 3; z += 6) {
			for (int x : new int[] {-3, 3}) {
				BlockPos post = frame.at(x, 0, z);
				level.setBlock(post, kit.p.fence(), Block.UPDATE_ALL);
				level.setBlock(post.above(), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
		Style flat = new Style("capital", style.where);
		List<Works.Built> built = new ArrayList<>();
		List<BlockPos> homes = new ArrayList<>(), halls = new ArrayList<>();
		Ctx ctx = new Ctx(level, kingdom, random);
		// the keep first: it is the seat
		List<Works.Built> keepBuilt = new ArrayList<>();
		place(level, flat, StyleBuildings.keep(look), new Piece(Role.HALL, 0, 14, 0, c -> null), bell, gate, floor, keepBuilt);
		Works.Built keepAt = keepBuilt.getFirst();
		BoundingBox keepBox = BoundingBox.fromCorners(keepAt.min(), keepAt.max());
		for (int[] c : new int[][] {{-h, -h}, {h, -h}, {-h, h}, {h, h}, {-h, 0}, {h, 0}, {0, h}}) {
			place(level, flat, Buildings.tower(kit), new Piece(Role.DECOR, c[0], c[1] - 3, 0, k -> null), bell, gate, floor, null);
		}
		place(level, flat, Buildings.gatehouse(kit), new Piece(Role.DECOR, 0, -h - 2, 0, k -> null), bell, gate, floor, null);
		// the market quarter
		for (int[] m : new int[][] {{-5, -9}, {5, -9}, {-5, 1}, {5, 1}}) {
			place(level, flat, Buildings.market(kit), new Piece(Role.DECOR, m[0], m[1], 0, k -> null), bell, gate, floor, null);
		}
		place(level, flat, Buildings.well(kit), new Piece(Role.DECOR, 0, 12, 0, k -> null), bell, gate, floor, null);
		homes.add(place(level, flat, Buildings.tavernInn(kit), new Piece(Role.HALL, 0, -26, 0, k -> null), bell, gate, floor, built));
		// the houses along the main street, the quarters and the temples
		int[][] houses = {{-8, -22}, {8, -22}, {-8, -13}, {8, -13}, {-8, 10}, {8, 10}, {-8, 20}, {8, 20}};
		for (int[] s : houses) {
			homes.add(place(level, flat, Buildings.house(Buildings.Kit.of(level, kingdom, random.nextLong() | 1L, random.nextLong() | 1L)),
				new Piece(Role.HOME, s[0], s[1], s[0] < 0 ? 1 : 3, k -> null), bell, gate, floor, built));
		}
		halls.add(place(level, flat, Buildings.barracks(kit), new Piece(Role.HALL, -22, -8, 1, k -> null), bell, gate, floor, built));
		halls.add(place(level, flat, Buildings.smithy(kit), new Piece(Role.HALL, 22, -8, 3, k -> null), bell, gate, floor, built));
		halls.add(place(level, flat, Buildings.mason(kit), new Piece(Role.HALL, 22, 6, 3, k -> null), bell, gate, floor, built));
		place(level, flat, StyleBuildings.temple(look), new Piece(Role.DECOR, -22, 12, 0, k -> null), bell, gate, floor, null);
		place(level, flat, StyleBuildings.temple(look), new Piece(Role.DECOR, 22, 12, 0, k -> null), bell, gate, floor, null);
		Works.update(server, id, w -> {
			List<Works.Built> all = new ArrayList<>(w.built());
			all.addAll(built);
			return w.withBuilt(all);
		});
		Seats.Room room = Seats.room(level, new Seats.Building(keepBox, "keep"), bell);
		Castle.Keep keep;
		if (room != null) {
			keep = Seats.keep(level, kingdom, room, bell, true, true);
			Holds.guard(server, id, keepBox);
		} else {
			keep = Castle.build(level, kingdom, bell, Castle.Kind.KEEP);
		}
		Factions.putKeep(server, id, keep);
		KingdomEvents.man(level, Factions.get(server, id), keep);
		Storehouse.build(level, Factions.get(server, id));
		KingdomEvents.settle(level, Factions.get(server, id), null, bell);
		for (BlockPos home : homes) {
			for (int i = 0; i < PER_HOME; i++) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, home, 4), EntitySpawnReason.STRUCTURE);
			}
		}
		for (BlockPos hall : halls) {
			EntityTypes.VILLAGER.spawn(level, Construction.standable(level, hall, 4), EntitySpawnReason.STRUCTURE);
		}
		WalledTowns.front(server, id, gate);
		remember(server, id, style);
		JaymosKingdoms.LOGGER.info("A capital, {}, rose at {}: {}", kingdom.name(), bell, style.where);
		return Factions.get(server, id);
	}

	// ---- looking for sites ---------------------------------------------------------------------------------------------

	static String biome(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
	}

	static boolean has(String name, String... words) {
		for (String w : words) {
			if (name.contains(w)) {
				return true;
			}
		}
		return false;
	}

	/** A surface of water at the column (x, z) (loaded ground only). */
	static boolean waterAt(ServerLevel level, int x, int z) {
		BlockPos column = new BlockPos(x, 0, z);
		return Loaded.made(level, column) && !level.getFluidState(level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, column).below()).isEmpty();
	}

	/** Water at a column between {@code min} and {@code max} blocks from {@code at}, in one of 16 directions: its position, or null. */
	static @Nullable BlockPos waterRing(ServerLevel level, BlockPos at, int min, int max) {
		for (int r = min; r <= max; r += 6) {
			for (int i = 0; i < 16; i++) {
				double a = Math.PI * 2.0 * i / 16.0;
				int x = at.getX() + (int)Math.round(Math.cos(a) * r), z = at.getZ() + (int)Math.round(Math.sin(a) * r);
				if (waterAt(level, x, z)) {
					return new BlockPos(x, at.getY(), z);
				}
			}
		}
		return null;
	}

	/** Water (a river, a lake, the sea) found round a point near the player: the site is {@code back} blocks off it, inland. */
	static @Nullable BlockPos besideWater(ServerLevel level, ServerPlayer player, RandomSource random, int back) {
		BlockPos around = ring(player, random);
		BlockPos water = waterRing(level, around, 4, 56);
		if (water == null) {
			return null;
		}
		double dx = around.getX() - water.getX(), dz = around.getZ() - water.getZ(), len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		return new BlockPos(water.getX() + (int)Math.round(dx / len * back), around.getY(), water.getZ() + (int)Math.round(dz / len * back));
	}

	/** A king's road (any road) passes within {@code reach} of {@code at}. */
	static boolean roadNear(ServerLevel level, BlockPos at, double reach) {
		for (Roads.Road road : Roads.network(level.getServer()).roads()) {
			if (road.points().size() >= 2) {
				Roads.Line line = Roads.line(road);
				if (line.near(at.getX(), at.getZ(), reach) && line.project(at.getX(), at.getZ())[1] <= reach) {
					return true;
				}
			}
		}
		return false;
	}

	/** Where two roads cross, far from any seat, within reach of the player: a random such crossing, or null. */
	static @Nullable BlockPos crossing(ServerLevel level, ServerPlayer player, RandomSource random) {
		List<Roads.Road> roads = Roads.network(level.getServer()).roads().stream().filter(r -> r.points().size() >= 2).toList();
		List<BlockPos> found = new ArrayList<>();
		double px = player.getX(), pz = player.getZ();
		for (int i = 0; i < roads.size(); i++) {
			Roads.Line a = Roads.line(roads.get(i));
			if (!a.near(px, pz, SITE_MIN + SITE_SPREAD)) {
				continue;
			}
			for (int j = i + 1; j < roads.size(); j++) {
				Roads.Line b = Roads.line(roads.get(j));
				for (double s = 0; s <= a.length(); s += 10.0) {
					Vec3 v = a.at(s);
					if (Math.hypot(v.x - px, v.z - pz) > SITE_MIN + SITE_SPREAD || b.project(v.x, v.z)[1] > CROSS_NEAR) {
						continue;
					}
					BlockPos pos = BlockPos.containing(v.x, v.y, v.z);
					boolean clear = true;
					for (Faction f : Factions.all(level.getServer())) {
						clear &= Raids.horizontal(f.seat(), pos) >= (double)CROSS_CLEAR * CROSS_CLEAR;
					}
					if (clear) {
						found.add(pos);
					}
				}
			}
		}
		return found.isEmpty() ? null : found.get(random.nextInt(found.size()));
	}

	/** The midpoint of two realms at peace that lie {@link #BORDER_MIN} to {@link #BORDER_MAX} apart, near the player; null if none. */
	static @Nullable BlockPos between(ServerLevel level, ServerPlayer player, RandomSource random) {
		MinecraftServer server = level.getServer();
		List<Faction> realms = Factions.all(server).stream().filter(f -> f.type().isRealm() && !f.fallen()).toList();
		List<int[]> pairs = new ArrayList<>();
		List<BlockPos> mids = new ArrayList<>();
		for (int i = 0; i < realms.size(); i++) {
			for (int j = i + 1; j < realms.size(); j++) {
				Faction a = realms.get(i), b = realms.get(j);
				double d = Math.sqrt(Raids.horizontal(a.seat(), b.seat()));
				if (d < BORDER_MIN || d > BORDER_MAX || Relations.between(a, b) == Relation.WAR || Relations.between(a, b) == Relation.RIVAL
					|| Relations.isVassal(a, b) || Relations.isVassal(b, a)) {
					continue;
				}
				BlockPos mid = new BlockPos((a.seat().getX() + b.seat().getX()) / 2, player.getBlockY(), (a.seat().getZ() + b.seat().getZ()) / 2);
				double near = Math.hypot(mid.getX() - player.getX(), mid.getZ() - player.getZ());
				if (near >= SITE_MIN && near <= SITE_MIN + SITE_SPREAD) {
					pairs.add(new int[] {a.id(), b.id()});
					mids.add(mid);
				}
			}
		}
		if (mids.isEmpty()) {
			return null;
		}
		int k = random.nextInt(mids.size());
		BORDERS.put(BlockPos.asLong(mids.get(k).getX(), 0, mids.get(k).getZ()), pairs.get(k));
		return mids.get(k);
	}

	/** The two realms a border market stands between (null: not one). */
	static int @Nullable [] bordersAt(BlockPos bell) {
		return BORDERS.get(BlockPos.asLong(bell.getX(), 0, bell.getZ()));
	}

	/** Some iron, copper or coal ore in the chunks round {@code at} (two either way): ore mountains. */
	static boolean oreNear(ServerLevel level, BlockPos at) {
		int cx = at.getX() >> 4, cz = at.getZ() >> 4;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk != null && IronVeins.vein(chunk)) {
					return true;
				}
			}
		}
		return false;
	}

	static boolean realmWithin(ServerLevel level, BlockPos at, double reach) {
		for (Faction f : Factions.all(level.getServer())) {
			if (f.type().isRealm() && !f.fallen() && Raids.horizontal(f.seat(), at) < reach * reach) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code pos} is Mohknee's dry land (any biome but sea, river and beach), as the Blossom villages read it. */
	static boolean dryMohknee(ServerLevel level, BlockPos pos) {
		var biome = level.getBiome(pos);
		return Mohknee.on(level, pos) && !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH)
			&& !biome.is(com.jaymoskingdoms.lands.Lands.ASHLANDS);
	}

	/** Whether {@code blossom} blocks stand (the Blossom land's blocks are registered). */
	static boolean blossomBlocks() {
		return BlossomBlocks.SHOJI != null;
	}
}
