package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Sailing;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sky pirates (the medieval picks' vp-skypirates; Lore: "Sky pirates"): when the steam towns first put balloon ships in the
 * air some were taken, and their takers stayed up there. A sky cove is a sixth kind of camp ({@link FactionType#BARBARIAN},
 * {@link Camps.CampData#kind} {@link #KIND}) on a high peak near a kingdom, rising only once a steam town stands somewhere:
 * a tarred shack over their hoard of kegs, powder and shot, a fire, a mast with a black flag, and moored in the open sky
 * beside it their airship ({@link SkyShips}), crewed by {@link Corsairs} and flown by her captain here, the cove's leader.
 * <p>She roams the sky round her cove. What her lookout spots she marks, and circles a while out of reach (Valkyrien
 * Pirates' "circle" pattern), then comes down on it: a ship or another airship she circles close and fires broadsides into;
 * a caravan, a player or a town on the ground she makes bombing runs over, letting kegs fall through her hatch. Spent
 * (no kegs, no powder), or too far from home, she goes back to her cove and takes on stores from its hoard. Kill her captain
 * and the cove is cleared ({@link Camps#chiefSlain}); she drifts and sinks with nobody at her wheel ({@link Ship}). A cove
 * whose airship was lost without him builds another.
 */
final class SkyPirates {
	// ---- tuning ----
	/** {@link Camps.CampData#kind} of a sky cove. */
	static final String KIND = "skycove";
	/** At most this many near a kingdom; the chance one rises each spawn check (with a steam town standing). */
	static final int COVES_PER_KINGDOM = 1;
	static final float COVE_CHANCE = 0.05F;
	/** A peak: its top at least this far over the sea; looked for over a grid this many blocks a step round each guess. */
	static final int PEAK_RISE = 48, PEAK_STEP = 8, PEAK_GRID = 2;
	/** Its ground: levelled this far round its fire, rising and falling at most this much, cleared this high. */
	static final int RADIUS = 5, MAX_SPREAD = 6, CLEAR_HEIGHT = 10;
	/** Its mast's height. */
	static final int MAST = 7;
	/** Where she moors: this far out from its fire and this far above it. */
	static final int BERTH_OUT = RADIUS + SkyShips.WIDTH / 2 + 5, BERTH_UP = 6;
	/** A new cove's hands ashore, and the most it keeps. */
	static final int START_HANDS = 2, HANDS_MOST = 6, DAYS_PER_HAND = 2, HAND_RATION = 2;
	/** What a new cove starts with, and takes in each day. */
	static final int START_KEGS = 12, START_POWDER = 24, START_SHOT = 24, START_FISH = 12, START_GOLD = 6;
	static final int DAY_KEGS = 2, DAY_POWDER = 3, DAY_SHOT = 3, DAY_FISH = 3;
	/** What she takes aboard from its hoard (at most). */
	static final int SHIP_KEGS = 8, SHIP_POWDER = 16, SHIP_SHOT = 16;
	/** Her crew besides her two gunners. */
	static final int HANDS = 2, BOYS = 0;
	/** Her airship lost, another is built after this long (ticks: two days); she's missing after this long unseen where she was. */
	static final long REBUILD_TICKS = 48000L, MISSING_TICKS = 20 * 180;
	/** She's never put out with a player this near her berth. */
	static final double LAUNCH_HIDE = 40.0;
	/** Her captain thinks every this many ticks. */
	static final int THINK = 20;
	/** Her lookout's reach; how far from home she roams, and how far she'll chase before she turns back. */
	static final double SPOT = 96.0, ROAM = 160.0, LEASH = 320.0;
	/** She circles a mark this wide, this high over it, this long, before she comes down on it. */
	static final double CIRCLE_RADIUS = 30.0, CIRCLE_UP = 26.0;
	static final long CIRCLE_TICKS = 20 * 15;
	/** Against a ship: she circles this close, this high over her. */
	static final double ENGAGE_RADIUS = 18.0, ENGAGE_UP = 8.0;
	/** A bombing run: she comes over the mark this high, and lets a keg fall within this of it, a keg at most every this many ticks. */
	static final double RUN_UP = 14.0, KEG_REACH = 4.0;
	static final long KEG_GAP = 30L;
	/** She gives up on a mark after this long at it, and leaves it alone this long after. */
	static final long ATTACK_TICKS = 20 * 60, SPARE_TICKS = 20 * 600;
	/** A mark is only taken with a player this near it (to see it). */
	static final double SEEN = 96.0;
	/** Her cloth: roaming, fighting, on a bombing run. */
	static final double CRUISE = 0.55, FIGHT = 0.7, RUN = 0.8;
	/** Roaming: her height over her cove's fire; a waypoint's reached this near. */
	static final double CRUISE_UP = 18.0, WAYPOINT = 20.0, HOME_REACH = 10.0;

	private static final String[] NAME_FIRST = {"Crow's", "Gale", "Cloudbreak", "Eagle's", "Thunder", "Highwind", "Stormcrag", "Kite", "Rook's",
		"Skyhook"};
	private static final String[] NAME_SECOND = {"Eyrie", "Roost", "Crag", "Perch", "Nest", "Spire", "Peak", "Aerie"};

	/** What's known of a sky cove beyond its camp data: her berth and how she lies there, her airship, where she was last seen, when she was lost. */
	record Cove(BlockPos berth, Direction bow, Optional<UUID> ship, Optional<BlockPos> seen, long lostAt) {
		static final Codec<Cove> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("berth").forGetter(Cove::berth),
				Direction.CODEC.fieldOf("bow").forGetter(Cove::bow),
				UUIDUtil.CODEC.optionalFieldOf("ship").forGetter(Cove::ship),
				BlockPos.CODEC.optionalFieldOf("seen").forGetter(Cove::seen),
				Codec.LONG.optionalFieldOf("lost_at", -1L).forGetter(Cove::lostAt))
			.apply(i, Cove::new));

		Cove withShip(@Nullable UUID ship, @Nullable BlockPos seen, long lostAt) {
			return new Cove(this.berth, this.bow, Optional.ofNullable(ship), Optional.ofNullable(seen), lostAt);
		}
	}

	/** Every sky cove (camp id → it), kept on the Overworld. */
	static final AttachmentType<Map<String, Cove>> COVES = AttachmentRegistry.create(JaymosKingdoms.id("sky_coves"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Cove.CODEC)));

	enum Phase {
		ROAM, CIRCLE, ATTACK, HOME
	}

	/** What her captain is about. */
	private static final class Hunt {
		Phase phase = Phase.ROAM;
		long since;
		/** Her mark: an entity (its id), or a town's seat; where it was last; whether it's a ship. */
		int markId = -1;
		@Nullable String markKey;
		@Nullable Vec3 markAt;
		boolean onShip;
		long nextKeg;
		@Nullable Vec3 waypoint;
		/** Marks let be a while (their key → until). */
		final Map<String, Long> spared = new HashMap<>();
	}

	/** Airship → her captain's hunt. */
	private static final Map<UUID, Hunt> HUNTS = new HashMap<>();
	/** Camp id → ticks its airship has been missing where she should be. */
	private static final Map<Integer, Long> MISSING = new HashMap<>();

	private SkyPirates() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("sky cove attachment: {}", COVES);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Ship ship) {
				HUNTS.remove(ship.getUUID());
			}
		});
	}

	static @Nullable Cove cove(MinecraftServer server, int camp) {
		return Factions.level(server).getAttachedOrElse(COVES, Map.of()).get(Integer.toString(camp));
	}

	static void putCove(MinecraftServer server, int camp, @Nullable Cove cove) {
		ServerLevel level = Factions.level(server);
		Map<String, Cove> all = new HashMap<>(level.getAttachedOrElse(COVES, Map.of()));
		if (cove == null) {
			all.remove(Integer.toString(camp));
		} else {
			all.put(Integer.toString(camp), cove);
		}
		level.setAttached(COVES, Map.copyOf(all));
	}

	// ---- where one rises -------------------------------------------------------------------------------------------

	/** A sky cove near {@code realm} (whose players are {@code players}): on the highest ground round a guess out of their sight. */
	static boolean trySite(MinecraftServer server, ServerLevel level, Faction realm, List<ServerPlayer> players) {
		if (SteamTowns.all(level).isEmpty()) {
			// (no steam town has put an airship up yet: there is nothing to have been taken)
			return false;
		}
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < Camps.SITE_TRIES; tries++) {
			ServerPlayer player = players.get(random.nextInt(players.size()));
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = Camps.HIDE_RANGE + random.nextInt(Camps.SITE_SPREAD);
			BlockPos guess = player.blockPosition().offset((int)Math.round(Math.cos(angle) * r), 0, (int)Math.round(Math.sin(angle) * r));
			BlockPos peak = null;
			for (int dx = -PEAK_GRID; dx <= PEAK_GRID; dx++) {
				for (int dz = -PEAK_GRID; dz <= PEAK_GRID; dz++) {
					BlockPos column = guess.offset(dx * PEAK_STEP, 0, dz * PEAK_STEP);
					if (!Loaded.made(level, column)) {
						continue;
					}
					BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
					if (peak == null || top.getY() > peak.getY()) {
						peak = top;
					}
				}
			}
			if (peak == null || peak.getY() < level.getSeaLevel() + PEAK_RISE) {
				continue;
			}
			double d = Math.sqrt(Raids.horizontal(peak, realm.seat()));
			if (d < Camps.MIN_DISTANCE || d > Camps.MAX_DISTANCE || !Camps.open(server, level, peak)) {
				continue;
			}
			int floor = IllagerBuildings.floor(level, peak, MAX_SPREAD, RADIUS);
			if (floor != Integer.MIN_VALUE && found(level, peak, floor) != null) {
				return true;
			}
		}
		return false;
	}

	// ---- founding --------------------------------------------------------------------------------------------------

	/** A new sky cove at {@code center} on {@code floor}: built, its hoard stocked, its hands ashore, its airship put out. */
	static @Nullable Faction found(ServerLevel level, BlockPos center, int floor) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		// her berth: off the side where the mountain falls away most
		Direction out = null;
		int lowest = Integer.MAX_VALUE;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos column = fire.relative(d, BERTH_OUT);
			if (!Loaded.made(level, column)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			if (y < lowest) {
				lowest = y;
				out = d;
			}
		}
		if (out == null) {
			return null;
		}
		BlockPos berth = fire.relative(out, BERTH_OUT).above(Math.max(BERTH_UP, lowest - floor + SkyShips.POSTS + 2));
		Faction camp = Faction.create(Factions.nextId(server), FactionType.BARBARIAN, name(server, random), DyeColor.BLACK, fire,
			KingdomEvents.claim(server, fire, Camps.CLAIM_RADIUS));
		Factions.put(server, camp);
		int id = camp.id();
		long now = level.getGameTime();
		Camps.putData(server, id, new Camps.CampData(fire, out, now, now, Production.day(server), -1L, List.of(), 0L, Map.of(), KIND, 0, 0));
		putCove(server, id, new Cove(berth, out.getClockWise(), Optional.empty(), Optional.empty(), -1L));
		List<BlockPos> storage = new ArrayList<>();
		BlockPos depot = build(level, fire, out, storage);
		Storage.register(server, id, storage, depot);
		Faction stocked = Factions.get(server, id);
		Storage.put(level, stocked, Items.TNT, START_KEGS);
		Storage.put(level, stocked, Items.GUNPOWDER, START_POWDER);
		Storage.put(level, stocked, Sailing.ROUND_SHOT, START_SHOT);
		Storage.put(level, stocked, Items.COOKED_COD, START_FISH);
		Storage.put(level, stocked, Items.GOLD_INGOT, START_GOLD);
		Camps.takeSides(server, id, KIND);
		for (int i = 0; i < START_HANDS; i++) {
			PirateCoves.spawn(level, id, Pirates.DECKHAND, spot(level, fire, random), fire, RADIUS);
		}
		if (launch(level, Factions.get(server, id)) == null) {
			// (no room to put her out yet: her captain waits ashore, and she's built when there's room)
			ashore(level, Factions.get(server, id), depot);
		}
		JaymosKingdoms.LOGGER.info("A sky cove, {}, rose at {} (berth {})", camp.name(), fire, berth);
		return Factions.get(server, id);
	}

	private static String name(MinecraftServer server, RandomSource random) {
		String name = "";
		for (int tries = 0; tries < 20; tries++) {
			name = NAME_FIRST[random.nextInt(NAME_FIRST.length)] + " " + NAME_SECOND[random.nextInt(NAME_SECOND.length)];
			if (Factions.byName(server, name) == null) {
				return name;
			}
		}
		return name + " " + Factions.nextId(server);
	}

	/** Its captain ashore by the shack (his airship not yet built), the cove's leader. */
	private static void ashore(ServerLevel level, @Nullable Faction camp, BlockPos at) {
		if (camp == null) {
			return;
		}
		Folk captain = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
		if (captain == null) {
			return;
		}
		captain.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		captain.setup(Pirates.CAPTAIN, camp, at, 4);
		level.addFreshEntity(captain);
		Factions.update(level.getServer(), camp.id(), f -> f.withLeader(Optional.of(captain.getUUID())));
	}

	/** Open ground near the fire for a hand to stand on. */
	private static BlockPos spot(ServerLevel level, BlockPos fire, RandomSource random) {
		for (int tries = 0; tries < 12; tries++) {
			BlockPos column = fire.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
			if (column.distManhattan(fire) < 2) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (Math.abs(at.getY() - fire.getY()) <= 1 && level.getBlockState(at).isAir()) {
				return at;
			}
		}
		return fire.offset(2, 0, 1);
	}

	/**
	 * The cove round {@code fire}, its berth off toward {@code out}: the shack (its hoard inside) behind the fire, the mast and
	 * its black flag at the edge toward the berth. Its hoard's chests go into {@code storage}; where its loot is set down.
	 */
	private static BlockPos build(ServerLevel level, BlockPos fire, Direction out, List<BlockPos> storage) {
		CampBuildings.level(level, fire, RADIUS, CLEAR_HEIGHT);
		Direction back = out.getOpposite(), side = out.getClockWise();
		BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState(), log = Blocks.SPRUCE_LOG.defaultBlockState();
		set(level, fire, Blocks.CAMPFIRE.defaultBlockState());
		// the shack: 5 across, 4 deep, its door toward the fire
		BlockPos shack = fire.relative(back, 3);
		for (int a = -2; a <= 2; a++) {
			for (int d = -1; d <= 2; d++) {
				BlockPos base = shack.relative(side, a).relative(back, d);
				set(level, base.below(), planks);
				boolean wall = Math.abs(a) == 2 || d == -1 || d == 2, corner = Math.abs(a) == 2 && (d == -1 || d == 2);
				for (int y = 0; y < 3; y++) {
					boolean door = d == -1 && a == 0 && y < 2;
					set(level, base.above(y), wall && !door ? (corner ? log : planks) : Blocks.AIR.defaultBlockState());
				}
				set(level, base.above(3), Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			}
		}
		for (int a : new int[] {-1, 1}) {
			BlockPos chest = shack.relative(side, a).relative(back, 1);
			set(level, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, out));
			storage.add(chest.immutable());
		}
		set(level, shack.relative(back, 1), Blocks.BARREL.defaultBlockState());
		set(level, shack.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		// the mast at the edge toward the berth, its black flag streaming out over the drop
		BlockPos mast = fire.relative(out, RADIUS - 1).relative(side, 2);
		for (int y = 0; y < MAST; y++) {
			set(level, mast.above(y), Blocks.DARK_OAK_FENCE.defaultBlockState());
		}
		for (int w = 1; w <= 3; w++) {
			for (int h = 0; h < 2; h++) {
				Block cloth = w == 2 && h == 1 ? Blocks.WOOL.pick(DyeColor.WHITE) : Blocks.WOOL.pick(DyeColor.BLACK);
				set(level, mast.above(MAST - 1 - h).relative(side, w), cloth.defaultBlockState());
			}
		}
		set(level, fire.relative(side, -3), Blocks.BARREL.defaultBlockState());
		return fire.relative(back, 1).immutable();
	}

	private static void set(ServerLevel level, BlockPos at, BlockState state) {
		level.setBlock(at, state, Block.UPDATE_ALL);
	}

	// ---- her airship ---------------------------------------------------------------------------------------------------

	/** The cove's airship put out at its berth, crewed and stored from its hoard; her captain made its leader. Null if there was no room. */
	static @Nullable Ship launch(ServerLevel level, @Nullable Faction camp) {
		if (camp == null) {
			return null;
		}
		MinecraftServer server = level.getServer();
		Cove cove = cove(server, camp.id());
		if (cove == null || !level.isPositionEntityTicking(cove.berth()) || !Storage.loaded(level, camp)) {
			return null;
		}
		RandomSource random = level.getRandom();
		Crew crew = new Crew(Crew.Side.PIRATE, camp.id(), Pirates.pirateShip(random), cove.berth());
		crew.flag = SkyShips.PIRATE;
		List<ItemStack> stores = new ArrayList<>();
		for (ItemStack s : new ItemStack[] {Storage.take(level, camp, Items.TNT, SHIP_KEGS), Storage.take(level, camp, Items.GUNPOWDER, SHIP_POWDER),
			Storage.take(level, camp, Sailing.ROUND_SHOT, SHIP_SHOT)}) {
			if (!s.isEmpty()) {
				stores.add(s);
			}
		}
		List<SkyShips.Placed> design = SkyShips.design(Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.DARK_OAK_FENCE.defaultBlockState(),
			Blocks.WOOL.pick(DyeColor.BLACK).defaultBlockState(), Blocks.WOOL.pick(DyeColor.RED).defaultBlockState(), 2);
		Ship ship = null;
		for (int tries = 0; tries < 3 && ship == null; tries++) {
			ship = SkyShips.launch(level, design, cove.berth().above(tries * 4), cove.bow(), crew, camp,
				new Corsairs.Complement(Pirates.CAPTAIN, Pirates.GUNNER, Pirates.DECKHAND, HANDS, BOYS), stores);
		}
		if (ship == null) {
			for (ItemStack s : stores) {
				Storage.put(level, camp, s);
			}
			return null;
		}
		UUID captain = crew.captain;
		UUID old = camp.leader().orElse(null);
		if (old != null && !old.equals(captain) && level.getEntity(old) instanceof Worker ashore && !(ashore.mob().getVehicle() instanceof Ship)) {
			// (he waited ashore for her: now he's aboard her, and the one ashore goes)
			McaCompat.vanish(ashore.mob());
		}
		Factions.update(server, camp.id(), f -> f.withLeader(Optional.ofNullable(captain)));
		putCove(server, camp.id(), cove.withShip(ship.getUUID(), ship.blockPosition(), -1L));
		JaymosKingdoms.LOGGER.info("{} put out the airship {}", camp.name(), crew.name);
		return ship;
	}

	/** Every {@link Camps#CHECK_TICKS} while a standing sky cove is loaded: its airship kept track of, and built again when she's lost. */
	static void tick(ServerLevel level, Faction camp, Camps.CampData data, boolean force) {
		MinecraftServer server = level.getServer();
		Cove cove = cove(server, camp.id());
		if (cove == null) {
			return;
		}
		long now = level.getGameTime();
		if (cove.ship().isPresent()) {
			if (level.getEntity(cove.ship().get()) instanceof Ship s && !s.isRemoved()) {
				MISSING.remove(camp.id());
				if (!s.blockPosition().equals(cove.seen().orElse(null))) {
					putCove(server, camp.id(), cove.withShip(s.getUUID(), s.blockPosition(), cove.lostAt()));
				}
				return;
			}
			BlockPos seen = cove.seen().orElse(cove.berth());
			if (!level.isPositionEntityTicking(seen) || MISSING.merge(camp.id(), (long)Camps.CHECK_TICKS, Long::sum) < MISSING_TICKS) {
				return;
			}
			MISSING.remove(camp.id());
			putCove(server, camp.id(), cove = cove.withShip(null, null, now));
		}
		hands(level, camp, data);
		if (!force && (cove.lostAt() >= 0 && now - cove.lostAt() < REBUILD_TICKS || Raids.playerNear(level, cove.berth(), LAUNCH_HIDE))) {
			return;
		}
		launch(level, camp);
	}

	/** Its hands ashore, one more every few days up to {@link #HANDS_MOST}, each new one fed out of its stores. */
	private static void hands(ServerLevel level, Faction camp, Camps.CampData data) {
		int cap = Math.min(HANDS_MOST, START_HANDS + data.grown() / DAYS_PER_HAND);
		int hands = camp.holdings().count(Pirates.DECKHAND) + camp.holdings().vacancies().getOrDefault(Folk.roleName(Pirates.DECKHAND), 0);
		if (hands < cap && Storage.loaded(level, camp) && !Storage.take(level, camp, Items.COOKED_COD, HAND_RATION).isEmpty()) {
			PirateCoves.spawn(level, camp.id(), Pirates.DECKHAND, spot(level, data.center(), level.getRandom()), data.center(), RADIUS);
		}
	}

	/** {@code days}' growth: what the cove takes in (kegs and powder bought off the steam towns' smugglers, shot, fish). */
	static void grow(ServerLevel level, Faction camp, int days) {
		if (Storage.loaded(level, camp)) {
			Storage.put(level, camp, Items.TNT, DAY_KEGS * days);
			Storage.put(level, camp, Items.GUNPOWDER, DAY_POWDER * days);
			Storage.put(level, camp, Sailing.ROUND_SHOT, DAY_SHOT * days);
			Storage.put(level, camp, Items.COOKED_COD, DAY_FISH * days);
		}
		Camps.updateData(level.getServer(), camp.id(), d -> d.withGrown(d.grown() + days));
	}

	/** {@code crew}'s airship went down: her cove builds another in a while, if it still stands. */
	static void shipLost(ServerLevel level, Crew crew, @Nullable Ship ship) {
		if (ship != null) {
			HUNTS.remove(ship.getUUID());
		}
		Cove cove = cove(level.getServer(), crew.faction);
		if (cove != null) {
			putCove(level.getServer(), crew.faction, cove.withShip(null, null, level.getGameTime()));
		}
	}

	/** She's home at her berth: her hold topped up from the cove's hoard. */
	private static void restock(ServerLevel level, Ship ship, Crew crew) {
		Faction camp = Factions.get(level.getServer(), crew.faction);
		if (camp == null || camp.fallen() || !Storage.loaded(level, camp)) {
			return;
		}
		top(level, ship, camp, Items.TNT, SHIP_KEGS);
		top(level, ship, camp, Items.GUNPOWDER, SHIP_POWDER);
		top(level, ship, camp, Sailing.ROUND_SHOT, SHIP_SHOT);
	}

	private static void top(ServerLevel level, Ship ship, Faction camp, Item item, int most) {
		int want = most - ship.inHold(s -> s.is(item));
		if (want <= 0) {
			return;
		}
		ItemStack taken = Storage.take(level, camp, item, want);
		if (!taken.isEmpty()) {
			ItemStack left = ship.intoHold(taken);
			if (!left.isEmpty()) {
				Storage.put(level, camp, left);
			}
		}
	}

	// ---- her captain -----------------------------------------------------------------------------------------------

	/** Her helm this tick, from {@link Corsairs#helm} for a sky pirate's airship in the air with her captain aboard. */
	static Input helm(ServerLevel level, Ship ship, Crew crew) {
		long now = level.getGameTime();
		Hunt h = HUNTS.computeIfAbsent(ship.getUUID(), k -> new Hunt());
		if ((now + ship.getId()) % THINK == 0) {
			think(level, ship, crew, h, now);
		}
		Cove cove = cove(level.getServer(), crew.faction);
		BlockPos berth = cove != null ? cove.berth() : crew.home;
		Vec3 home = Vec3.atCenterOf(berth);
		switch (h.phase) {
			case CIRCLE -> {
				return SkyShips.fly(level, ship, SkyShips.circling(ship, h.markAt, CIRCLE_RADIUS), CRUISE, h.markAt.y + CIRCLE_UP);
			}
			case ATTACK -> {
				if (h.onShip) {
					return SkyShips.fly(level, ship, SkyShips.circling(ship, h.markAt, ENGAGE_RADIUS), FIGHT, h.markAt.y + ENGAGE_UP);
				}
				bomb(level, ship, h, now);
				return SkyShips.fly(level, ship, com.jaymoskingdoms.sailing.Fleet.bearing(ship, h.markAt.x, h.markAt.z), RUN, h.markAt.y + RUN_UP);
			}
			case HOME -> {
				double d = SkyShips.flat(ship, home);
				return SkyShips.fly(level, ship, com.jaymoskingdoms.sailing.Fleet.bearing(ship, home.x, home.z), d < HOME_REACH ? 0.0 : CRUISE, berth.getY());
			}
			default -> {
				if (h.waypoint == null || SkyShips.flat(ship, h.waypoint) < WAYPOINT) {
					RandomSource random = level.getRandom();
					double a = random.nextDouble() * Math.PI * 2.0, r = ROAM * (0.35 + 0.65 * random.nextDouble());
					h.waypoint = new Vec3(home.x + Math.cos(a) * r, berth.getY() + CRUISE_UP, home.z + Math.sin(a) * r);
				}
				return SkyShips.fly(level, ship, com.jaymoskingdoms.sailing.Fleet.bearing(ship, h.waypoint.x, h.waypoint.z), CRUISE, h.waypoint.y);
			}
		}
	}

	/** A bombing run: over the mark, a keg let fall through her hatch. */
	private static void bomb(ServerLevel level, Ship ship, Hunt h, long now) {
		if (now < h.nextKeg || h.markAt == null) {
			return;
		}
		Vec3 hatch = SkyShips.hatchOf(ship);
		if (hatch == null) {
			return;
		}
		Vec3 under = ship.toWorld(hatch.x, hatch.y, hatch.z);
		double dx = under.x - h.markAt.x, dz = under.z - h.markAt.z;
		if (dx * dx + dz * dz > KEG_REACH * KEG_REACH) {
			return;
		}
		h.nextKeg = now + KEG_GAP;
		LivingEntity by = ship.crewAboard().isEmpty() ? null : ship.crewAboard().getFirst();
		SkyShips.dropKeg(level, ship, hatch, by);
	}

	/** Her captain looks about him: his mark, and what he does about it. */
	private static void think(ServerLevel level, Ship ship, Crew crew, Hunt h, long now) {
		Cove cove = cove(level.getServer(), crew.faction);
		Vec3 home = Vec3.atCenterOf(cove != null ? cove.berth() : crew.home);
		h.spared.values().removeIf(until -> until < now);
		boolean spent = ship.inHold(SkyShips::keg) <= 0 && ship.inHold(s -> s.is(Items.GUNPOWDER)) <= 0;
		if (h.phase != Phase.ROAM && h.phase != Phase.HOME) {
			// her mark: where it is now, or lost
			Vec3 at = markAt(level, h);
			boolean over = at == null || SkyShips.flat(ship, at) > SPOT * 1.5 || SkyShips.flat(ship, home) > LEASH
				|| h.phase == Phase.ATTACK && (now - h.since > ATTACK_TICKS || spent);
			if (over) {
				if (h.markKey != null) {
					h.spared.put(h.markKey, now + SPARE_TICKS);
				}
				drop(h);
				h.phase = spent || SkyShips.flat(ship, home) > LEASH ? Phase.HOME : Phase.ROAM;
				h.since = now;
			} else {
				h.markAt = at;
				if (h.phase == Phase.CIRCLE && now - h.since > CIRCLE_TICKS) {
					h.phase = Phase.ATTACK;
					h.since = now;
				}
			}
		} else if (h.phase == Phase.HOME) {
			if (SkyShips.flat(ship, home) < HOME_REACH) {
				restock(level, ship, crew);
				h.phase = Phase.ROAM;
				h.since = now;
			}
		} else if (spent || SkyShips.flat(ship, home) > LEASH) {
			h.phase = Phase.HOME;
			h.since = now;
		} else if (lookout(level, ship, crew, h)) {
			h.phase = Phase.CIRCLE;
			h.since = now;
		}
		// her gunners' marks: the ship she's fighting, or the ground she's coming down on
		boolean attacking = h.phase == Phase.ATTACK && h.markAt != null;
		crew.target = attacking && h.onShip ? h.markId : -1;
		crew.shore = attacking && !h.onShip ? h.markAt : null;
		crew.shoreLiving = crew.shore != null;
		crew.shoreSet = now;
		crew.mode = h.phase.name().toLowerCase(java.util.Locale.ROOT);
	}

	private static void drop(Hunt h) {
		h.markId = -1;
		h.markKey = null;
		h.markAt = null;
		h.onShip = false;
	}

	/** Where her mark is now (null: it's gone). */
	private static @Nullable Vec3 markAt(ServerLevel level, Hunt h) {
		if (h.markId >= 0) {
			Entity e = level.getEntity(h.markId);
			if (e == null || e.isRemoved() || e instanceof Ship s && s.foundering() || e instanceof LivingEntity l && !l.isAlive()) {
				return null;
			}
			return e.position();
		}
		return h.markAt;
	}

	/**
	 * What her lookout spots within {@link #SPOT}, with a player near to see it: a ship or airship not a pirate's, else a
	 * caravan's cart, else a player on the ground, else a town. The nearest of the first kind there is; true if she marked one.
	 */
	private static boolean lookout(ServerLevel level, Ship ship, Crew crew, Hunt h) {
		MinecraftServer server = level.getServer();
		Entity best = null;
		double bestD = SPOT;
		for (Ship s : Ships.in(level)) {
			if (s == ship || s.isRemoved() || s.foundering() || s.crew != null && s.crew.side == Crew.Side.PIRATE
				|| s.crew == null && s.pilot() == null && Corsairs.playersAboard(level, s).isEmpty() && !s.hasAttached(SteamAirships.TRIP)) {
				continue;
			}
			double d = SkyShips.flat(ship, s.position());
			if (d < bestD && !h.spared.containsKey(key(s)) && Raids.playerNear(level, s.blockPosition(), SEEN)) {
				best = s;
				bestD = d;
			}
		}
		if (best == null) {
			AABB around = ship.getBoundingBox().inflate(SPOT, 160.0, SPOT);
			for (Wagon w : level.getEntitiesOfClass(Wagon.class, around, w -> !w.isRemoved())) {
				double d = SkyShips.flat(ship, w.position());
				if (d < bestD && !h.spared.containsKey(key(w)) && Raids.playerNear(level, w.blockPosition(), SEEN)) {
					best = w;
					bestD = d;
				}
			}
			if (best == null) {
				for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, around, p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
					&& Ships.aboard(p) == null && p.onGround())) {
					double d = SkyShips.flat(ship, p.position());
					if (d < bestD && !h.spared.containsKey(key(p))) {
						best = p;
						bestD = d;
					}
				}
			}
		}
		if (best != null) {
			h.markId = best.getId();
			h.markKey = key(best);
			h.markAt = best.position();
			h.onShip = best instanceof Ship;
			return true;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || f.id() == crew.faction) {
				continue;
			}
			double d = SkyShips.flat(ship, Vec3.atCenterOf(f.seat()));
			String k = "town" + f.id();
			if (d < bestD && !h.spared.containsKey(k) && Raids.playerNear(level, f.seat(), SEEN)) {
				// (over the town: a little off its seat, where its people are)
				RandomSource random = level.getRandom();
				int x = f.seat().getX() + Mth.nextInt(random, -10, 10), z = f.seat().getZ() + Mth.nextInt(random, -10, 10);
				h.markId = -1;
				h.markKey = k;
				h.markAt = new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
				h.onShip = false;
				bestD = d;
			}
		}
		return h.markKey != null;
	}

	private static String key(Entity e) {
		return e.getStringUUID();
	}

	static void clear() {
		HUNTS.clear();
		MISSING.clear();
	}
}
