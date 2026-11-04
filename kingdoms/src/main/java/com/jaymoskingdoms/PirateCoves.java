package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Ship;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
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
import org.jspecify.annotations.Nullable;

/**
 * (0.7.42) Sailing, stage 3: pirate coves, a fifth kind of camp ({@link FactionType#BARBARIAN} factions whose
 * {@link Camps.CampData#kind} is {@link #KIND}). One may rise on a coast near a coastal kingdom: a tarred shack over the
 * crew's hoard, a jetty out into the water, a black flag on a pole and a fire with deckhands round it, and offshore
 * their ship ({@link Fleet}, sailed by {@link Corsairs}), whose captain is the cove's leader. His ship patrols the coast,
 * runs down every ship that isn't a pirate's and fires broadsides, and his landing parties raid the coastal settlements
 * near it ({@link Raids}). Kill the captain (or sink her with him aboard) and the cove is cleared like any camp
 * ({@link Camps#chiefSlain}); a cove whose ship was lost without him builds another.
 */
final class PirateCoves {
	/** {@link Camps.CampData#kind} of a pirate cove. */
	static final String KIND = "pirate";
	/** At most this many standing coves near a kingdom; the chance one rises each spawn check (only near a coastal kingdom). */
	static final int COVES_PER_KINGDOM = 1;
	static final float COVE_CHANCE = 0.1F;
	/** A cove's ground: how far round its fire it's levelled, and how much it may rise and fall. */
	static final int RADIUS = 8, MAX_SPREAD = 4, CLEAR_HEIGHT = 10;
	/** Open sea a ship can lie in must be this near the cove; a kingdom whose seat has it this near is coastal. */
	static final int SEA_REACH = 48, COAST_REACH = 120;
	/** The jetty: as long as this at most, two wide. */
	static final int JETTY_MOST = 24;
	/** The flagpole's height. */
	static final int POLE = 7;
	/** A new cove: deckhands ashore, and the hoard it starts with (the caps follow). */
	static final int START_HANDS = 3, DAYS_PER_HAND = 2;
	/** (0.7.490) Its own caps: the most deckhands ashore, the most bombardiers, the most growth stages (its ground runs out), the most a ship's hold takes in a haul (stacks). */
	static final int HANDS_MOST = 16, BOMBARDIERS_MOST = 4, MAX_STAGE = 36, HAUL_MOST = 96;
	/** (0.7.490) A new hand eats this much cooked fish out of the stores; a stage waits this long (ticks) after the last, and is given up after this many tries at a place. */
	static final int HAND_RATION = 2;
	static final long STAGE_GAP = 6000L;
	static final int STAGE_TRIES = 3;
	/** (0.7.490) A stage costs this many dark oak planks and gold nuggets to begin with, more for each stage so far (at most), and powder every fourth. */
	static final int COST_PLANKS = 12, COST_PLANKS_PER = 2, COST_PLANKS_MOST = 64, COST_NUGGETS = 9, COST_NUGGETS_PER = 1, COST_NUGGETS_MOST = 45, COST_POWDER = 6;
	/** (0.7.490) Stages ring the cove on its landward side: the first this far from its fire, a ring further every few, no further than the most; and its tower's height. */
	static final int RING_START = RADIUS + 5, RING_STEP = 4, STAGES_PER_RING = 4, RING_MOST = RADIUS + 30, TOWER_HEIGHT = 8;
	/** (0.7.490) Nothing is built with a player this near. */
	static final double STAGE_HIDE = 24.0;
	/** (0.7.490) A rover that sinks a ship takes: her coin (at most this many emeralds), and a share of her generated hold; the haul is unloaded when her ship is back this near the mooring. */
	static final int PRIZE_COIN = 48;
	static final double UNLOAD_REACH = 36.0;
	/** (0.7.490) What a new cove starts with, and takes in each day, in planks. */
	static final int START_PLANKS = 32, DAY_PLANKS = 6;
	/** (0.7.490) Each stage raises a bigger prize: a ship is a galleon this much likelier for each stage, at most; and carries one more hand for every this many stages. */
	static final float GALLEON_PER_STAGE = 0.04F, GALLEON_MOST = 0.9F;
	static final int STAGES_PER_CREW = 4, CREW_MOST = 4;
	/** (0.7.107) Its bombardiers ashore (off its roster): one, a second once it has grown this many days; one lost comes back with the next day's growth. */
	static final int BOMBARDIERS = 1, MAX_BOMBARDIERS = 2, DAYS_TO_SECOND_BOMBARDIER = 4;
	static final int START_GOLD = 8, START_EMERALDS = 6, START_GUNPOWDER = 12, START_SHOT = 12, START_RUM = 4;
	/** What it takes in each day (smuggling, wrecking). */
	static final int DAY_GOLD = 2, DAY_EMERALDS = 1, DAY_GUNPOWDER = 2, DAY_FISH = 4;
	/** Its ship: lost, another is built after this long (ticks: two days); her captain ashore after this long missing. */
	static final long REBUILD_TICKS = 48000L, MISSING_TICKS = 20 * 180;
	/** She's never launched with a player this near her mooring. */
	static final double LAUNCH_HIDE = 28.0;
	/** Her crew: deckhands besides her gunners, powder monkeys; on a brigantine ((0.7.112) the galleon). */
	static final int SLOOP_HANDS = 3, BRIG_HANDS = 5, BOYS = 1;
	/** (0.7.112) A small ship's chance of being a drakkar rather than a sloop (Jaki's; the big one is always Jaki's galleon). */
	static final float DRAKKAR_CHANCE = 0.4F;
	/** How long a cached coast check holds (ticks). */
	static final long COAST_MEMORY = 20 * 300;

	private static final String[] NAME_FIRST = {"Gallows", "Blackwater", "Skull", "Wreckers'", "Smugglers'", "Drowned Man's", "Rum", "Cutthroat",
		"Hangman's", "Siren", "Saltbone", "Grey Gull"};
	private static final String[] NAME_SECOND = {"Cove", "Bay", "Inlet", "Haven", "Hole", "Reach", "Landing", "Creek"};

	/** What's known of a cove beyond its camp data: its mooring, which way the sea is, its ship, where she was last seen, when she was lost. */
	record Cove(BlockPos mooring, Direction seaward, Optional<UUID> ship, Optional<BlockPos> seen, long lostAt, List<ItemStack> haul) {
		static final Codec<Cove> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("mooring").forGetter(Cove::mooring),
				Direction.CODEC.fieldOf("seaward").forGetter(Cove::seaward),
				UUIDUtil.CODEC.optionalFieldOf("ship").forGetter(Cove::ship),
				BlockPos.CODEC.optionalFieldOf("seen").forGetter(Cove::seen),
				Codec.LONG.optionalFieldOf("lost_at", -1L).forGetter(Cove::lostAt),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("haul", List.of()).forGetter(Cove::haul))
			.apply(i, Cove::new));

		Cove withShip(@Nullable UUID ship, @Nullable BlockPos seen, long lostAt) {
			return new Cove(this.mooring, this.seaward, Optional.ofNullable(ship), Optional.ofNullable(seen), lostAt, this.haul);
		}

		/** (0.7.490) With the prizes her ship carries home. */
		Cove withHaul(List<ItemStack> haul) {
			return new Cove(this.mooring, this.seaward, this.ship, this.seen, this.lostAt, List.copyOf(haul));
		}
	}

	/** Every cove (camp id → it), kept on the Overworld. */
	static final AttachmentType<Map<String, Cove>> COVES = AttachmentRegistry.create(JaymosKingdoms.id("pirate_coves"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Cove.CODEC)));

	/** Camp id → ticks its ship (or her captain) has been missing where she should be. */
	private static final Map<Integer, Long> MISSING = new HashMap<>();
	/** Place → (when it was looked at, the open sea found near it or null). */
	private static final Map<Long, Object[]> COAST = new HashMap<>();

	private PirateCoves() {
	}

	static boolean is(MinecraftServer server, int camp) {
		Camps.CampData data = Camps.data(server, camp);
		return data != null && KIND.equals(data.kind());
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

	// ---- the coast -------------------------------------------------------------------------------------------------

	/** Open sea a ship can lie in within {@code reach} of {@code at} (nearest first): its top water block, or null. Remembered a while. */
	static @Nullable BlockPos sea(ServerLevel level, BlockPos at, int reach) {
		long key = at.asLong() * 31L + reach;
		long now = level.getGameTime();
		Object[] known = COAST.get(key);
		if (known != null && now - (long)known[0] < COAST_MEMORY) {
			return (BlockPos)known[1];
		}
		BlockPos found = null;
		boolean looked = false;
		search:
		for (int r = 16; r <= reach; r += 8) {
			for (int a = 0; a < 16; a++) {
				double angle = a * Math.PI / 8.0;
				BlockPos column = at.offset(Mth.floor(Math.cos(angle) * r), 0, Mth.floor(Math.sin(angle) * r));
				if (!level.isPositionEntityTicking(column)) {
					continue;
				}
				looked = true;
				BlockPos top = Fleet.surface(level, column);
				if (top != null && Fleet.roomy(level, top, 10)) {
					found = top;
					break search;
				}
			}
		}
		if (looked) {
			COAST.put(key, new Object[] {now, found});
		}
		return found;
	}

	/** {@code realm} lies on a coast: open sea within {@link #COAST_REACH} of its seat. */
	static boolean coastal(ServerLevel level, Faction realm) {
		return sea(level, realm.seat(), COAST_REACH) != null;
	}

	/** Pirate land: dry ground (a beach, a coast, an island) with open sea a short row off. */
	static boolean land(ServerLevel level, BlockPos at) {
		if (level.getBiome(at).is(BiomeTags.IS_OCEAN) || level.getBiome(at).is(BiomeTags.IS_RIVER)) {
			return false;
		}
		return sea(level, at, SEA_REACH) != null;
	}

	// ---- founding --------------------------------------------------------------------------------------------------

	/** A new cove at {@code center} on {@code floor}: built, its hoard stocked, its hands ashore, its ship launched (and her captain its leader). */
	static @Nullable Faction found(ServerLevel level, BlockPos center, int floor) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		BlockPos water = sea(level, fire, SEA_REACH);
		if (water == null) {
			return null;
		}
		Direction seaward = Direction.getApproximateNearest(water.getX() - fire.getX(), 0, water.getZ() - fire.getZ());
		Faction camp = Faction.create(Factions.nextId(server), FactionType.BARBARIAN, name(server, random), DyeColor.BLACK, fire,
			KingdomEvents.claim(server, fire, Camps.CLAIM_RADIUS));
		Factions.put(server, camp);
		int id = camp.id();
		long now = level.getGameTime();
		Camps.putData(server, id, new Camps.CampData(fire, seaward, now, now, Production.day(server), -1L, List.of(), 0L, Map.of(), KIND, 0, 0));
		putCove(server, id, new Cove(water, seaward, Optional.empty(), Optional.empty(), -1L, List.of()));
		Layout layout = build(level, fire, seaward);
		Storage.register(server, id, layout.storage(), layout.depot());
		Faction stocked = Factions.get(server, id);
		Storage.put(level, stocked, Items.GOLD_INGOT, START_GOLD);
		// (0.7.490) pirates take anything gold: what they have taken off ships is dinars, not emeralds
		Storage.put(level, stocked, Currencies.GOLD_DINAR, START_EMERALDS);
		Storage.put(level, stocked, Items.GUNPOWDER, START_GUNPOWDER);
		Storage.put(level, stocked, com.jaymoskingdoms.sailing.Sailing.ROUND_SHOT, START_SHOT);
		Storage.put(level, stocked, Items.HONEY_BOTTLE, START_RUM);
		Storage.put(level, stocked, Items.DARK_OAK_PLANKS, START_PLANKS);
		Camps.takeSides(server, id, KIND);
		for (int i = 0; i < START_HANDS; i++) {
			spawn(level, id, Pirates.DECKHAND, spot(level, fire, random), fire, RADIUS);
		}
		for (int i = 0; i < BOMBARDIERS; i++) {
			bombardier(level, Factions.get(server, id), spot(level, fire, random), fire);
		}
		if (launch(level, Factions.get(server, id)) == null) {
			// (no room to put her out yet: her captain waits ashore, and she's built when there's room)
			ashore(level, Factions.get(server, id), layout.depot());
		}
		JaymosKingdoms.LOGGER.info("A pirate cove, {}, rose at {} (mooring {})", camp.name(), fire, water);
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

	/** A cove's hands, ashore: one of {@code role} at {@code at}, counted on its roster. */
	static @Nullable Folk spawn(ServerLevel level, int id, int role, BlockPos at, BlockPos home, int radius) {
		Faction camp = Factions.get(level.getServer(), id);
		if (camp == null) {
			return null;
		}
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
		if (folk == null) {
			return null;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		folk.setup(role, camp, home, radius);
		level.addFreshEntity(folk);
		Factions.update(level.getServer(), id, f -> f.withHoldings(f.holdings().adjust(role, 1)));
		return folk;
	}

	/** Its captain ashore by the shack (his ship not yet built), the cove's leader. */
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
			BlockPos column = fire.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
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

	// ---- the cove's buildings --------------------------------------------------------------------------------------

	record Layout(List<BlockPos> storage, BlockPos depot, BlockPos jettyEnd) {
	}

	/** The cove round {@code fire}: the shack (its hoard inside) behind the fire, the flagpole, the jetty out to {@code seaward}. */
	static Layout build(ServerLevel level, BlockPos fire, Direction seaward) {
		CampBuildings.level(level, fire, RADIUS, CLEAR_HEIGHT);
		Direction back = seaward.getOpposite(), side = seaward.getClockWise();
		BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState(), log = Blocks.SPRUCE_LOG.defaultBlockState();
		set(level, fire, Blocks.CAMPFIRE.defaultBlockState());
		// the shack: 7 across, 5 deep, its door toward the fire
		BlockPos shack = fire.relative(back, 5);
		List<BlockPos> storage = new ArrayList<>();
		for (int a = -3; a <= 3; a++) {
			for (int d = -2; d <= 2; d++) {
				BlockPos base = shack.relative(side, a).relative(seaward, d);
				set(level, base.below(), planks);
				boolean wall = Math.abs(a) == 3 || Math.abs(d) == 2;
				boolean corner = Math.abs(a) == 3 && Math.abs(d) == 2;
				for (int y = 0; y < 3; y++) {
					BlockPos at = base.above(y);
					boolean door = d == 2 && a == 0 && y < 2;
					boolean window = y == 1 && !corner && (Math.abs(a) == 3 && d == 0 || d == -2 && Math.abs(a) == 1);
					set(level, at, wall && !door ? (corner ? log : window ? Blocks.SPRUCE_FENCE.defaultBlockState() : planks) : Blocks.AIR.defaultBlockState());
				}
				set(level, base.above(3), Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			}
		}
		// the hoard against the back wall, a barrel of powder, a lantern
		for (int a : new int[] {-1, 1}) {
			BlockPos chest = shack.relative(side, a).relative(back, 1);
			set(level, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, seaward));
			storage.add(chest.immutable());
		}
		set(level, shack.relative(side, -2).relative(back, 1), Blocks.BARREL.defaultBlockState());
		set(level, shack.relative(side, 2), Blocks.BARREL.defaultBlockState());
		set(level, shack.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		BlockPos depot = shack.relative(seaward, 3);
		// the flagpole, its black flag (a white skull's worth in the middle)
		BlockPos pole = fire.relative(side, 3);
		for (int y = 0; y < POLE; y++) {
			set(level, pole.above(y), Blocks.DARK_OAK_FENCE.defaultBlockState());
		}
		for (int w = 1; w <= 3; w++) {
			for (int h = 0; h < 2; h++) {
				Block cloth = w == 2 && h == 1 ? Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE) : Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BLACK);
				set(level, pole.above(POLE - 1 - h).relative(side, w), cloth.defaultBlockState());
			}
		}
		// crates and rum by the fire
		set(level, fire.relative(side, -3), Blocks.BARREL.defaultBlockState());
		set(level, fire.relative(side, -3).relative(back), Blocks.BARREL.defaultBlockState());
		// the jetty: from the cove's edge out over the water
		BlockPos end = jetty(level, fire.relative(seaward, RADIUS - 1), seaward, side);
		return new Layout(storage, depot, end);
	}

	/** Lays a two-wide jetty from {@code start} toward {@code way}: over land on the ground, then on posts over the water. Its far end. */
	static BlockPos jetty(ServerLevel level, BlockPos start, Direction way, Direction side) {
		int sea = level.getSeaLevel();
		BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState(), post = Blocks.SPRUCE_FENCE.defaultBlockState();
		BlockPos at = start;
		int overWater = 0;
		for (int i = 0; i < JETTY_MOST; i++) {
			at = start.relative(way, i);
			if (!level.isLoaded(at)) {
				break;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
			boolean water = level.getFluidState(top.below()).is(FluidTags.WATER);
			int y = water ? sea : Math.max(top.getY() - 1, sea);
			if (!water && top.getY() - 1 > start.getY() + 1) {
				// (a rise in the way: it ends there)
				break;
			}
			boolean blocked = false;
			for (int s = 0; s <= 1; s++) {
				BlockPos plank = new BlockPos(at.getX(), y, at.getZ()).relative(side, s);
				if (!free(level, plank) || !free(level, plank.above())) {
					blocked = true;
				}
			}
			if (blocked) {
				// (something built there: the jetty stops short of it)
				break;
			}
			for (int s = 0; s <= 1; s++) {
				BlockPos plank = new BlockPos(at.getX(), y, at.getZ()).relative(side, s);
				set(level, plank, deck);
				set(level, plank.above(), Blocks.AIR.defaultBlockState());
				if (water && i % 3 == 0) {
					for (int d = 1; d <= 6 && level.getFluidState(plank.below(d)).is(FluidTags.WATER); d++) {
						set(level, plank.below(d), post.setValue(net.minecraft.world.level.block.FenceBlock.WATERLOGGED, true));
					}
				}
			}
			if (water) {
				overWater++;
				if (overWater >= 5 && Fleet.deep(level, new BlockPos(at.getX(), sea - 1, at.getZ()), 3)) {
					break;
				}
			}
		}
		BlockPos end = new BlockPos(at.getX(), sea + 1, at.getZ());
		if (overWater > 0 && free(level, end) && free(level, end.above())) {
			set(level, end, post);
			set(level, end.above(), Blocks.LANTERN.defaultBlockState());
		}
		return end;
	}

	/** Nothing built at {@code at}: air, water, or plants and snow. */
	static boolean free(ServerLevel level, BlockPos at) {
		BlockState there = level.getBlockState(at);
		return there.isAir() || there.canBeReplaced() || there.is(Blocks.WATER);
	}

	private static void set(ServerLevel level, BlockPos at, BlockState state) {
		level.setBlock(at, state, Block.UPDATE_ALL);
	}

	/** What rots once a cove is cleared: its camp's cloth and fences, and its barrels. */
	static boolean perishable(BlockState state) {
		return CampBuildings.perishable(state) || state.is(Blocks.BARREL);
	}

	// ---- her ship --------------------------------------------------------------------------------------------------

	/** The cove's ship put out at its mooring, crewed; her captain made its leader. Null if there was no room for her. */
	static @Nullable Ship launch(ServerLevel level, @Nullable Faction camp) {
		if (camp == null) {
			return null;
		}
		MinecraftServer server = level.getServer();
		Cove cove = cove(server, camp.id());
		if (cove == null || !level.isPositionEntityTicking(cove.mooring())) {
			return null;
		}
		RandomSource random = level.getRandom();
		// ((0.7.490) a cove that has grown puts out bigger ships with bigger crews)
		Camps.CampData grown = Camps.data(server, camp.id());
		int stages = grown == null ? 0 : grown.built();
		int extra = Math.min(CREW_MOST, stages / STAGES_PER_CREW);
		boolean brig = random.nextFloat() < Math.min(GALLEON_MOST, 0.45F + GALLEON_PER_STAGE * stages);
		Crew crew = new Crew(Crew.Side.PIRATE, camp.id(), Pirates.pirateShip(random), cove.mooring());
		Fleet.Look look = new Fleet.Look(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_LOG, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.BLACK), Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE));
		Direction bow = cove.seaward().getClockWise();
		// (0.7.112) the big ship's Jaki's galleon (was our brigantine); the small one our sloop or Jaki's drakkar
		Fleet.Rig rig = brig ? Fleet.Rig.GALLEON : random.nextFloat() < DRAKKAR_CHANCE ? Fleet.Rig.DRAKKAR : Fleet.Rig.SLOOP;
		Ship ship = null;
		for (int tries = 0; tries < 4 && ship == null; tries++) {
			BlockPos water = tries == 0 ? cove.mooring() : Fleet.openSea(level, cove.mooring(), 24, 4, random);
			if (water != null) {
				ship = Corsairs.launch(level, rig, look, water, bow, crew, camp,
					new Corsairs.Complement(Pirates.CAPTAIN, Pirates.GUNNER, Pirates.DECKHAND, (brig ? BRIG_HANDS : SLOOP_HANDS) + extra, BOYS));
			}
		}
		if (ship == null) {
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
		JaymosKingdoms.LOGGER.info("{} put out {} ({})", camp.name(), crew.name, rig.id());
		return ship;
	}

	/** Every {@link Camps#CHECK_TICKS} while a standing cove is loaded: its ship kept track of, and built again when she's lost. */
	static void tick(ServerLevel level, Faction camp, Camps.CampData data, boolean force) {
		MinecraftServer server = level.getServer();
		Cove cove = cove(server, camp.id());
		if (cove == null) {
			return;
		}
		long now = level.getGameTime();
		// ((0.7.490) the cove grows as its stores allow, and unloads the prizes her ship brings home)
		stage(level, camp, data, force);
		cove = unload(level, camp, cove);
		if (cove.ship().isPresent()) {
			Entity ship = level.getEntity(cove.ship().get());
			if (ship instanceof Ship s && !s.isRemoved()) {
				MISSING.remove(camp.id());
				if (!s.blockPosition().equals(cove.seen().orElse(null))) {
					putCove(server, camp.id(), cove.withShip(s.getUUID(), s.blockPosition(), cove.lostAt()));
				}
				return;
			}
			// (not here: she may only be out of reach; she's lost once where she was last seen is loaded and she isn't there)
			BlockPos seen = cove.seen().orElse(cove.mooring());
			if (!level.isPositionEntityTicking(seen) || MISSING.merge(camp.id(), (long)Camps.CHECK_TICKS, Long::sum) < MISSING_TICKS) {
				return;
			}
			MISSING.remove(camp.id());
			putCove(server, camp.id(), cove = cove.withShip(null, null, now));
		}
		if (!force && (cove.lostAt() >= 0 && now - cove.lostAt() < REBUILD_TICKS || Raids.playerNear(level, cove.mooring(), LAUNCH_HIDE))) {
			grownHands(level, camp, data);
			return;
		}
		launch(level, camp);
	}

	/**
	 * Its hands ashore, one more every few days and every stage it has grown, up to its own most ({@link #HANDS_MOST}); each
	 * new one eats a ration out of the stores ((0.7.490) so a cove that is plundered, or has lost its fish, stops taking men on).
	 */
	private static void grownHands(ServerLevel level, Faction camp, Camps.CampData data) {
		int cap = (int)Math.min(HANDS_MOST, START_HANDS + data.grown() / DAYS_PER_HAND + data.built());
		int hands = camp.holdings().count(Pirates.DECKHAND) + camp.holdings().vacancies().getOrDefault(Folk.roleName(Pirates.DECKHAND), 0);
		if (hands < cap && Storage.loaded(level, camp) && !Storage.take(level, camp, Items.COOKED_COD, HAND_RATION).isEmpty()) {
			spawn(level, camp.id(), Pirates.DECKHAND, spot(level, data.center(), level.getRandom()), data.center(), RADIUS);
		}
		int bombers = Math.min(BOMBARDIERS_MOST, (data.grown() >= DAYS_TO_SECOND_BOMBARDIER ? MAX_BOMBARDIERS : BOMBARDIERS) + data.built() / 4);
		BlockPos center = data.center();
		// ((0.7.296) folk or villagers: they were counted only as folk, so a cove whose bombardiers had become villagers kept sending more)
		int around = Workers.near(level, new net.minecraft.world.phys.AABB(center).inflate(RADIUS * 4),
			w -> w.mob().isAlive() && w.factionId() == camp.id() && w.role() == Pirates.BOMBARDIER).size();
		if (around < bombers) {
			bombardier(level, camp, spot(level, center, level.getRandom()), center);
		}
	}

	/** (0.7.107) A bombardier ashore at the cove: off its roster, as its gunners are (so no vacancy is kept for him). */
	private static void bombardier(ServerLevel level, @Nullable Faction camp, BlockPos at, BlockPos home) {
		if (camp == null) {
			return;
		}
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
		if (folk == null) {
			return;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		folk.setup(Pirates.BOMBARDIER, camp, home, RADIUS);
		level.addFreshEntity(folk);
	}

	/** {@code days}' growth: what the cove takes in (smuggling, wrecking, fishing). */
	static void grow(ServerLevel level, Faction camp, int days) {
		if (Storage.loaded(level, camp)) {
			Storage.put(level, camp, Items.GOLD_NUGGET, DAY_GOLD * 9 * days);
			Storage.put(level, camp, Currencies.GOLD_DINAR, DAY_EMERALDS * days);
			Storage.put(level, camp, Items.GUNPOWDER, DAY_GUNPOWDER * days);
			Storage.put(level, camp, Items.COOKED_COD, DAY_FISH * days);
			Storage.put(level, camp, Items.DARK_OAK_PLANKS, DAY_PLANKS * days);
		}
		Camps.updateData(level.getServer(), camp.id(), d -> d.withGrown(d.grown() + days));
		Camps.CampData data = Camps.data(level.getServer(), camp.id());
		Faction now = Factions.get(level.getServer(), camp.id());
		if (data != null && now != null && level.isPositionEntityTicking(data.center())) {
			grownHands(level, now, data);
		}
	}

	/** Its deckhands ashore (and on their way back), its war band for a raid. */
	static int fighters(Faction camp) {
		return camp.holdings().count(Pirates.DECKHAND) + camp.holdings().vacancies().getOrDefault(Folk.roleName(Pirates.DECKHAND), 0);
	}

	// ---- (0.7.490) growing by its stores ---------------------------------------------------------------------------

	/** Camp id → game time its last stage was tried; and how many times in a row the stage now due found no place. */
	private static final Map<Integer, Long> STAGED = new HashMap<>();
	private static final Map<Integer, Integer> TRIES = new HashMap<>();

	/** What stage {@code n} costs: planks and gold nuggets, more with each stage, and powder for every fourth. */
	static Map<Item, Integer> stageCost(int n) {
		Map<Item, Integer> cost = new java.util.LinkedHashMap<>();
		cost.put(Items.DARK_OAK_PLANKS, Math.min(COST_PLANKS_MOST, COST_PLANKS + COST_PLANKS_PER * n));
		cost.put(Items.GOLD_NUGGET, Math.min(COST_NUGGETS_MOST, COST_NUGGETS + COST_NUGGETS_PER * n));
		if (n % 4 == 2) {
			cost.put(Items.GUNPOWDER, COST_POWDER);
		}
		return cost;
	}

	/**
	 * The cove's next stage, when its stores can pay for it and no player is by: in turn a bunkhouse, a stores shed, a
	 * watchtower and a side pier, round its landward side (the stage count is kept in its camp data, {@code built}). More
	 * stages mean more deckhands, more bombardiers, bigger ships and crews ({@link #launch}), up to {@link #MAX_STAGE}.
	 */
	private static void stage(ServerLevel level, Faction camp, Camps.CampData data, boolean force) {
		MinecraftServer server = level.getServer();
		int n = data.built();
		long now = level.getGameTime();
		if (n >= MAX_STAGE || !Storage.loaded(level, camp) || !level.isPositionEntityTicking(data.center())
			|| !force && now - STAGED.getOrDefault(camp.id(), 0L) < STAGE_GAP) {
			return;
		}
		Map<Item, Integer> cost = stageCost(n);
		Cove cove = cove(server, camp.id());
		if (cove == null || !Storage.has(level, camp, cost)) {
			return;
		}
		int kind = n % 4;
		int half = kind == 0 ? 2 : 1, height = kind == 0 ? 6 : kind == 1 ? 4 : TOWER_HEIGHT + 2;
		double back = Math.atan2(cove.seaward().getOpposite().getStepZ(), cove.seaward().getOpposite().getStepX());
		double angle = back + (n * Outbuild.GOLDEN) % 2.4 - 1.2;
		int ring = Outbuild.ring(n, RING_START, RING_STEP, STAGES_PER_RING, RING_MOST);
		BlockPos floor = kind == 3 ? null : Outbuild.find(level, data.center(), angle, 6, ring, half, height, data.center().getY(), 2, 4, camp.id());
		if (!force && Raids.playerNear(level, floor != null ? floor : data.center(), STAGE_HIDE)) {
			return;
		}
		STAGED.put(camp.id(), now);
		boolean built;
		if (kind == 3) {
			Direction side = cove.seaward().getClockWise();
			int offset = (n / 4 % 2 == 0 ? 1 : -1) * (4 + 3 * (n / 8));
			BlockPos start = data.center().relative(cove.seaward(), RADIUS - 1).relative(side, offset);
			built = level.isLoaded(start);
			if (built) {
				jetty(level, start, cove.seaward(), side);
			}
		} else if (floor == null) {
			built = false;
		} else {
			built = switch (kind) {
				case 0 -> bunkhouse(level, floor, data.center());
				case 1 -> shed(level, camp, floor);
				default -> tower(level, floor);
			};
		}
		if (!built) {
			// (no good ground this time: a few tries, then that stage is let go, unpaid)
			if (TRIES.merge(camp.id(), 1, Integer::sum) >= STAGE_TRIES) {
				TRIES.remove(camp.id());
				Camps.updateData(server, camp.id(), d -> d.withBuilt(d.built() + 1));
			}
			return;
		}
		TRIES.remove(camp.id());
		for (var e : cost.entrySet()) {
			Storage.take(level, camp, e.getKey(), e.getValue());
		}
		Camps.updateData(server, camp.id(), d -> d.withBuilt(d.built() + 1));
		JaymosKingdoms.LOGGER.debug("{} grew to stage {}", camp.name(), n + 1);
	}

	/** A bunkhouse of planks and logs, its door toward the fire, two barrels and a lantern inside. */
	private static boolean bunkhouse(ServerLevel level, BlockPos floor, BlockPos fire) {
		double dx = fire.getX() - floor.getX(), dz = fire.getZ() - floor.getZ();
		Direction door = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState(), log = Blocks.SPRUCE_LOG.defaultBlockState();
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				Outbuild.set(level, floor.offset(x, -1, z), planks);
				boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2, corner = Math.abs(x) == 2 && Math.abs(z) == 2;
				int across = x * door.getStepX() + z * door.getStepZ(), along = x * door.getStepZ() - z * door.getStepX();
				boolean doorway = across == 2 && along == 0;
				for (int y = 0; y < 3; y++) {
					boolean open = !wall || doorway && y < 2;
					Outbuild.set(level, floor.offset(x, y, z), open ? Blocks.AIR.defaultBlockState() : corner ? log : planks);
				}
				Outbuild.set(level, floor.offset(x, 3, z), Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			}
		}
		Outbuild.set(level, floor.relative(door.getOpposite(), 1).relative(door.getClockWise(), 1), Blocks.BARREL.defaultBlockState());
		Outbuild.set(level, floor.relative(door.getOpposite(), 1).relative(door.getCounterClockWise(), 1), Blocks.BARREL.defaultBlockState());
		Outbuild.set(level, floor.above(2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		return true;
	}

	/** An open shed on four posts with a barrel under it, which joins the cove's stores. */
	private static boolean shed(ServerLevel level, Faction camp, BlockPos floor) {
		BlockState post = Blocks.DARK_OAK_FENCE.defaultBlockState();
		for (int x = -1; x <= 1; x += 2) {
			for (int z = -1; z <= 1; z += 2) {
				for (int y = 0; y < 3; y++) {
					Outbuild.set(level, floor.offset(x, y, z), post);
				}
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				Outbuild.set(level, floor.offset(x, 3, z), Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
			}
		}
		Outbuild.set(level, floor, Blocks.BARREL.defaultBlockState());
		Storage.add(level.getServer(), camp.id(), List.of(floor.immutable()));
		return true;
	}

	/** A watchtower of spruce on a hollow 3 by 3 footing, a railed deck and a lantern at the top. */
	private static boolean tower(ServerLevel level, BlockPos floor) {
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState(), planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		for (int y = 0; y < TOWER_HEIGHT; y++) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					boolean edge = Math.abs(x) == 1 || Math.abs(z) == 1;
					boolean corner = Math.abs(x) == 1 && Math.abs(z) == 1;
					Outbuild.set(level, floor.offset(x, y, z), !edge ? Blocks.AIR.defaultBlockState() : corner ? log : y % 3 == 1 ? Blocks.SPRUCE_FENCE.defaultBlockState() : planks);
				}
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				Outbuild.set(level, floor.offset(x, TOWER_HEIGHT, z), planks);
				if (Math.abs(x) == 1 || Math.abs(z) == 1) {
					Outbuild.set(level, floor.offset(x, TOWER_HEIGHT + 1, z), Blocks.DARK_OAK_FENCE.defaultBlockState());
				}
			}
		}
		Outbuild.set(level, floor.offset(0, TOWER_HEIGHT + 1, 0), Blocks.LANTERN.defaultBlockState());
		return true;
	}

	// ---- (0.7.490) prizes --------------------------------------------------------------------------------------------

	/**
	 * {@code ship} (of {@code crew}, not a pirate's) was sunk by a gunner of a cove's: the cove's ship takes what she
	 * carried (everything in her chests and barrels, a share of her hold, and a little of her realm's coin) and carries it
	 * home. It is kept in the cove ({@link Cove#haul}) until she is back at the mooring, and spilled if she is lost first.
	 */
	static void captured(ServerLevel level, Ship ship, Crew crew, @Nullable Entity by) {
		MinecraftServer server = level.getServer();
		if (!(by instanceof Worker gunner) || !is(server, gunner.factionId()) || gunner.factionId() == crew.faction) {
			return;
		}
		Cove cove = cove(server, gunner.factionId());
		Faction camp = Factions.get(server, gunner.factionId());
		if (cove == null || camp == null || camp.fallen()) {
			return;
		}
		RandomSource random = level.getRandom();
		List<ItemStack> prize = new ArrayList<>(ship.takeCargo(HAUL_MOST));
		List<ItemStack> share = new ArrayList<>(java.util.Collections.nCopies(Corsairs.SHARE, ItemStack.EMPTY));
		Corsairs.stock(level, crew, share, random);
		for (ItemStack stack : share) {
			if (!stack.isEmpty()) {
				prize.add(stack);
			}
		}
		Faction owner = Factions.get(server, crew.faction);
		if (owner != null && owner.type().isRealm() && owner.treasury() > 0L) {
			long coin = Math.min(owner.treasury(), PRIZE_COIN);
			Factions.update(server, owner.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - coin)));
			// (0.7.498) the prize is struck in the owner realm's coin, not emeralds
			prize.addAll(com.jaymoskingdoms.money.Money.stacks(server, owner, com.jaymoskingdoms.money.Money.worth(coin)));
		}
		List<ItemStack> haul = new ArrayList<>();
		for (ItemStack stack : cove.haul()) {
			addTo(haul, stack.copy());
		}
		for (ItemStack stack : prize) {
			addTo(haul, stack);
		}
		putCove(server, camp.id(), cove.withHaul(haul));
		Raids.tell(level, ship.blockPosition(), net.minecraft.network.chat.Component.translatable("message.jaymoskingdoms.pirate.prize", camp.name(), crew.name)
			.withStyle(net.minecraft.ChatFormatting.GOLD));
		JaymosKingdoms.LOGGER.info("{} took the cargo of {} ({} stacks)", camp.name(), crew.name, prize.size());
	}

	/** Adds {@code stack} to {@code haul}: topping up like stacks, else a new one if the hold has room. */
	private static void addTo(List<ItemStack> haul, ItemStack stack) {
		for (ItemStack there : haul) {
			if (ItemStack.isSameItemSameComponents(there, stack)) {
				int n = Math.min(there.getMaxStackSize() - there.getCount(), stack.getCount());
				if (n > 0) {
					there.grow(n);
					stack.shrink(n);
				}
			}
		}
		if (!stack.isEmpty() && haul.size() < HAUL_MOST) {
			haul.add(stack);
		}
	}

	/** Her ship is home at the mooring with a haul: it goes into the cove's stores. Returns the cove as it now stands. */
	private static Cove unload(ServerLevel level, Faction camp, Cove cove) {
		if (cove.haul().isEmpty() || cove.ship().isEmpty() || !(level.getEntity(cove.ship().get()) instanceof Ship ship) || ship.isRemoved()
			|| ship.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(cove.mooring())) > UNLOAD_REACH * UNLOAD_REACH
			|| !Storage.loaded(level, camp) || Raids.raiding(camp.id())) {
			return cove;
		}
		Plunder.depositAll(level, camp, cove.haul());
		Cove emptied = cove.withHaul(List.of());
		putCove(level.getServer(), camp.id(), emptied);
		return emptied;
	}

	// ---- raids and losses ------------------------------------------------------------------------------------------

	/** {@code crew}'s cove has a landing party ashore (she stands off the raid while it's on). */
	static boolean raiding(ServerLevel level, Crew crew) {
		return Raids.raiding(crew.faction);
	}

	/** {@code camp}'s raid on {@code target} is under way: her ship sails to stand off the shore nearest its landing. */
	static void raidStarted(ServerLevel level, Faction camp, BlockPos landing) {
		Cove cove = cove(level.getServer(), camp.id());
		if (cove == null || cove.ship().isEmpty() || !(level.getEntity(cove.ship().get()) instanceof Ship ship) || ship.crew == null) {
			return;
		}
		BlockPos off = sea(level, landing, COAST_REACH);
		if (off != null) {
			ship.crew.bound = off;
		}
	}

	/** Where a pirate landing party comes ashore on {@code target}'s coast: dry ground by the water nearest open sea, or null. */
	static @Nullable BlockPos landing(ServerLevel level, Faction target) {
		BlockPos water = sea(level, target.seat(), COAST_REACH);
		if (water == null) {
			return null;
		}
		// walk in from the sea toward the seat to the first dry ground
		double dx = target.seat().getX() - water.getX(), dz = target.seat().getZ() - water.getZ();
		double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		for (int d = 0; d < len; d += 2) {
			BlockPos column = water.offset(Mth.floor(dx / len * d), 0, Mth.floor(dz / len * d));
			if (!level.isPositionEntityTicking(column)) {
				return null;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getFluidState(top.below()).isEmpty() && level.getFluidState(top).isEmpty()) {
				return top;
			}
		}
		return null;
	}

	/**
	 * {@code crew}'s ship went down (sunk by {@code player}, or anyone): her cove builds another in a while, if it still
	 * stands. ((0.7.490) the prizes she was carrying spill where {@code ship} founders; with no ship, where she was last
	 * seen; if that's not loaded, they stay in the cove's record and the next ship home brings them.)
	 */
	static void shipLost(ServerLevel level, Crew crew, @Nullable Ship ship, @Nullable ServerPlayer player) {
		MinecraftServer server = level.getServer();
		Cove cove = cove(server, crew.faction);
		if (cove != null) {
			if (!cove.haul().isEmpty()) {
				net.minecraft.world.phys.Vec3 at = ship != null ? ship.position()
					: net.minecraft.world.phys.Vec3.atCenterOf(cove.seen().orElse(cove.mooring()));
				if (level.isLoaded(BlockPos.containing(at))) {
					Plunder.spill(level, at.add(0.0, 1.0, 0.0), cove.haul());
					cove = cove.withHaul(List.of());
				}
			}
			putCove(server, crew.faction, cove.withShip(null, null, level.getGameTime()));
		}
	}

	static void clear() {
		MISSING.clear();
		COAST.clear();
		STAGED.clear();
		TRIES.clear();
	}
}
