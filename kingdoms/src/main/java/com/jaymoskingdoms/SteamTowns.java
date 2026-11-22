package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The steam towns (GT New Horizons picks, patch 9; Andy on the boiler row: "there should be a steampunk village"). The
 * craftsmen who built for the Mad Tinkerer stayed together after he vanished, and their towns are the only places that
 * still build steam: a smoky town of brick, iron and creosoted timber round a boiler house, a windmill and a coke yard,
 * a smoking chimney on every house, its tinkers and engineers at their benches ({@link SteamTrades}) and its airship on
 * the mast at the airfield ({@link SteamAirships}).
 * <p>
 * Like a Blossom village it rises by itself, out of sight of the player passing ({@link #tryRise}), and from then on it
 * is an ordinary {@link FactionType#VILLAGE} (an elder, a shed, a builder crew, its own growth and coin). It rises only
 * over a player who has opened {@link WorldTiers.Tier#BORDERLANDS} (the iron age; the way {@link IllagerSites} move in
 * only once a tier is open), and rarely: every steam town is at least {@link #STEAM_SPACING} from the next. Every steam
 * town ever raised is kept on the Overworld ({@link #TOWNS}), with its airfield and its airship's comings and goings.
 * <p>
 * (Andy, 2026-11-19: "i completely intended for these to be steampunk towns in the cloud sea, please remove them from the
 * overworld") They rise only in the <b>Cloud Sea</b> ({@link Stacked#CLOUD_SEA}), on a stone deck laid over the overcast
 * ({@link #deck}) with cloud banked up under it and an iron rail round its edge, never under an Aether island, and their
 * airships fly between them over the clouds. A world without the Cloud Sea (not stacked) raises none. Steam towns
 * already standing on the ground in an older world are left as they are.
 */
public final class SteamTowns {
	// ---- tuning ----
	/** How often (ticks) a steam town may rise near a player, and the chance each time. */
	static final int RISE_TICKS = 20 * 60 * 3;
	static final float RISE_CHANCE = 0.12F;
	/** The world tier a player near the site must have opened. */
	static final WorldTiers.Tier TIER = WorldTiers.Tier.BORDERLANDS;
	/** How far from a player it rises (at least, plus up to), and how near no player may be. */
	static final int SITE_MIN = 96, SITE_SPREAD = 48;
	static final double HIDE_RANGE = 64.0;
	/** No settlement's seat this near (blocks), no bell this near, and no other steam town this near. */
	static final int SPACING = 400, BELL_CLEARANCE = 160, STEAM_SPACING = 1400;
	/** The town's ground: its radius, how high it's cleared; tries for a site. */
	static final int RADIUS = 40, CLEAR_HEIGHT = 24, SITE_TRIES = 6;
	/** Its deck lies this high over the overcast's middle (in the Cloud Sea's own heights: just over the overcast's top). */
	static final int DECK_OVER = 20;
	/** The middle of the overcast in the Cloud Sea's own heights (a copy of Jaymo's Powers' {@code CloudSea.OVERCAST}). */
	static final int OVERCAST = 184;
	/** How deep cloud is banked up under the deck at most, and how far round the site no Aether island may hang. */
	static final int BANK_DEPTH = 24, OPEN_SKY = 72;
	/** Who moves in: villagers in each house and each workshop, and its iron golems. */
	static final int PER_HOUSE = 2, PER_SHOP = 1, GOLEMS = 1;
	/** How far round its bell a steam town's people live (blocks): its stalls are kept within this ({@link SteamTrades}). */
	static final double TOWN_REACH = 56.0;

	/** The town's colours (its banner, its airship's bag). */
	private static final DyeColor[] COLOURS = {DyeColor.GRAY, DyeColor.BROWN, DyeColor.BLACK, DyeColor.ORANGE, DyeColor.RED};
	/** Names: two halves, like Sootwick or Kettleford. */
	private static final String[] NAME_FIRST = {"Soot", "Kettle", "Cinder", "Rivet", "Brass", "Copper", "Bellow", "Piston", "Coal", "Smoke",
		"Spindle", "Clinker", "Gear", "Flue", "Boiler", "Anvil"};
	private static final String[] NAME_SECOND = {"wick", "ford", "worth", "stead", "haven", "bury", "field", "gate", "ton", "moor", "holt", "mouth"};

	/**
	 * A steam town: its settlement, its airfield's frame (the corner its blueprint is laid from, and the way it faces),
	 * and its airship's state: the town she lies at away from home and since when, when she last left a berth
	 * (-1: not flying), since when her home berth has stood empty with nothing to account for it (-1: it hasn't), where
	 * her wheel stands if she came down short of any berth and since when, and her captain while he is stowed aboard her
	 * as she goes home unseen ({@link SteamAirships#upkeep}).
	 */
	record Town(int id, BlockPos field, Direction facing, Optional<Integer> awayAt, long awaySince, long left, long gone, Optional<BlockPos> lost,
		long lostSince, Optional<CompoundTag> captain) {
		static final Codec<Town> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("id").forGetter(Town::id),
				BlockPos.CODEC.fieldOf("field").forGetter(Town::field),
				Direction.CODEC.fieldOf("facing").forGetter(Town::facing),
				Codec.INT.optionalFieldOf("away_at").forGetter(Town::awayAt),
				Codec.LONG.optionalFieldOf("away_since", -1L).forGetter(Town::awaySince),
				Codec.LONG.optionalFieldOf("left", -1L).forGetter(Town::left),
				Codec.LONG.optionalFieldOf("gone", -1L).forGetter(Town::gone),
				BlockPos.CODEC.optionalFieldOf("lost").forGetter(Town::lost),
				Codec.LONG.optionalFieldOf("lost_since", -1L).forGetter(Town::lostSince),
				CompoundTag.CODEC.optionalFieldOf("captain").forGetter(Town::captain))
			.apply(i, Town::new));

		CampBuildings.Placer placer() {
			return new CampBuildings.Placer(this.field, this.facing, SteamBuildings.FIELD_WIDTH);
		}

		Town away(Optional<Integer> at, long since) {
			return new Town(this.id, this.field, this.facing, at, since, this.left, this.gone, this.lost, this.lostSince, this.captain);
		}

		Town leaving(long left) {
			return new Town(this.id, this.field, this.facing, this.awayAt, this.awaySince, left, this.gone, this.lost, this.lostSince, this.captain);
		}

		Town missing(long gone) {
			return new Town(this.id, this.field, this.facing, this.awayAt, this.awaySince, this.left, gone, this.lost, this.lostSince, this.captain);
		}

		/** She came down at {@code wheel} (her helm), short of any berth, at {@code since}; empty: she isn't lying anywhere but a berth. */
		Town stranded(Optional<BlockPos> wheel, long since) {
			return new Town(this.id, this.field, this.facing, this.awayAt, this.awaySince, this.left, this.gone, wheel, since, this.captain);
		}

		Town stowed(Optional<CompoundTag> body) {
			return new Town(this.id, this.field, this.facing, this.awayAt, this.awaySince, this.left, this.gone, this.lost, this.lostSince, body);
		}

		/** She is accounted for nowhere but her home berth: no flight, no stay, no stranding, no empty berth. */
		Town home() {
			return new Town(this.id, this.field, this.facing, Optional.empty(), -1L, -1L, -1L, Optional.empty(), -1L, this.captain);
		}
	}

	/** Every steam town ever raised, saved on the Overworld. */
	static final AttachmentType<List<Town>> TOWNS = AttachmentRegistry.create(JaymosKingdoms.id("steam_towns"),
		builder -> builder.persistent(Town.CODEC.listOf()));

	/** A town's spot for a building: its front's middle in the bell's frame (the gate side is -z), and its turns clockwise from the gate. */
	private record Spot(int x, int z, int turns) {
	}

	private static final List<Spot> HOUSES = List.of(new Spot(-4, -24, 1), new Spot(-4, -14, 1), new Spot(-4, 18, 1), new Spot(4, -24, 3),
		new Spot(4, -14, 3), new Spot(4, 18, 3));
	private static final Spot TINKERS = new Spot(-4, 8, 1), ENGINEERS = new Spot(4, 8, 3), BOILERS = new Spot(0, 24, 0);
	/** The windmill at the west end of the cross street, its sails facing the town; the coke yard at the east end. */
	private static final Spot WINDMILL = new Spot(-20, 0, 1), COKE_YARD = new Spot(20, 0, 3);
	/** The airfield behind the west row, facing the gate like the town. */
	private static final Spot AIRFIELD = new Spot(-19, 14, 0);
	/** The streets (x0, z0, x1, z1): the high street from the gate to the boiler house, and the cross street. */
	private static final int[][] STREETS = {{-1, -32, 1, 23}, {-19, -1, 19, 1}, {-11, 13, -2, 13}};

	private SteamTowns() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("steam town attachment: {}", TOWNS);
		ServerTickEvents.END_SERVER_TICK.register(SteamTowns::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (Stacked.joined(server, Stacked.CLOUD_SEA) && level.getGameTime() % RISE_TICKS == 0 && level.getRandom().nextFloat() < RISE_CHANCE) {
			tryRise(level);
		}
	}

	// ---- the register --------------------------------------------------------------------------------------------------

	/** Every steam town that still stands (its settlement not fallen). */
	static List<Town> all(ServerLevel level) {
		List<Town> out = new ArrayList<>();
		for (Town t : level.getAttachedOrElse(TOWNS, List.of())) {
			Faction f = Factions.get(level.getServer(), t.id());
			if (f != null && !f.fallen()) {
				out.add(t);
			}
		}
		return out;
	}

	static @Nullable Town get(ServerLevel level, int id) {
		for (Town t : level.getAttachedOrElse(TOWNS, List.of())) {
			if (t.id() == id) {
				return t;
			}
		}
		return null;
	}

	/** Saves {@code town} in place of the one of its id. */
	static void put(ServerLevel level, Town town) {
		List<Town> next = new ArrayList<>(level.getAttachedOrElse(TOWNS, List.of()));
		next.removeIf(t -> t.id() == town.id());
		next.add(town);
		level.setAttached(TOWNS, List.copyOf(next));
	}

	/** The steam town {@code pos} lies in (within {@link #TOWN_REACH} of its bell), or null. */
	static @Nullable Town at(ServerLevel level, BlockPos pos) {
		for (Town t : all(level)) {
			Faction f = Factions.get(level.getServer(), t.id());
			if (f != null && Raids.horizontal(f.seat(), pos) <= TOWN_REACH * TOWN_REACH) {
				return t;
			}
		}
		return null;
	}

	static String name(ServerLevel level, Town town) {
		Faction f = Factions.get(level.getServer(), town.id());
		return f != null ? f.name() : "";
	}

	// ---- rising --------------------------------------------------------------------------------------------------------

	/** Looks round a player of the right tier for a site, and raises a steam town there. */
	private static void tryRise(ServerLevel level) {
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator() && !p.isCreative() && WorldTiers.open(p, TIER)).toList();
		if (players.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < SITE_TRIES; tries++) {
			ServerPlayer player = players.get(random.nextInt(players.size()));
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = SITE_MIN + random.nextInt(SITE_SPREAD);
			int floor = deckHeight(level.getServer());
			BlockPos at = new BlockPos(player.getBlockX() + (int)Math.round(Math.cos(angle) * r), floor, player.getBlockZ() + (int)Math.round(Math.sin(angle) * r));
			if (!open(level, at)) {
				continue;
			}
			// its gate toward the way the player came
			Direction gate = Direction.getApproximateNearest(player.getX() - at.getX(), 0, player.getZ() - at.getZ());
			found(level, at, floor, gate);
			return;
		}
	}

	/** The height of a steam town's deck in the level: over the Cloud Sea's overcast. */
	static int deckHeight(MinecraftServer server) {
		return Stacked.shift(server, Stacked.CLOUD_SEA) + OVERCAST + DECK_OVER;
	}

	/**
	 * {@code at} (on the deck's height) is free for a steam town: in the Cloud Sea, all its land loaded, no Aether island
	 * hanging over it or near it, nobody's land, no settlement, bell or steam town near, out of sight.
	 */
	private static boolean open(ServerLevel level, BlockPos at) {
		MinecraftServer server = level.getServer();
		if (Stacked.band(server, at.getY()) != Stacked.CLOUD || at.getY() + CLEAR_HEIGHT > Stacked.maxY(server, Stacked.CLOUD_SEA)
			|| !level.isPositionEntityTicking(at)
			|| !Loaded.made(level, at.getX() - OPEN_SKY, at.getZ() - OPEN_SKY, at.getX() + OPEN_SKY, at.getZ() + OPEN_SKY)) {
			return false;
		}
		// nothing of the Aether over its land: the level's heightmap reaches no higher than the Cloud Sea's top
		int sky = Stacked.maxY(server, Stacked.CLOUD_SEA) + 1;
		for (int dx = -OPEN_SKY; dx <= OPEN_SKY; dx += 8) {
			for (int dz = -OPEN_SKY; dz <= OPEN_SKY; dz += 8) {
				if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX() + dx, at.getZ() + dz) > sky) {
					return false;
				}
			}
		}
		for (BlockPos corner : new BlockPos[] {at, at.offset(-RADIUS, 0, -RADIUS), at.offset(RADIUS, 0, -RADIUS), at.offset(-RADIUS, 0, RADIUS),
			at.offset(RADIUS, 0, RADIUS)}) {
			if (Factions.at(server, level, corner) != null) {
				return false;
			}
		}
		for (Faction f : Factions.all(server)) {
			if (Raids.horizontal(f.seat(), at) < (double)SPACING * SPACING) {
				return false;
			}
		}
		for (Town t : level.getAttachedOrElse(TOWNS, List.of())) {
			Faction f = Factions.get(server, t.id());
			if (f != null && Raids.horizontal(f.seat(), at) < (double)STEAM_SPACING * STEAM_SPACING) {
				return false;
			}
		}
		if (Raids.playerNear(level, at, HIDE_RANGE) || built(level, at)) {
			return false;
		}
		return level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), at, BELL_CLEARANCE, PoiManager.Occupancy.ANY).isEmpty();
	}

	/** Some player has set a block anywhere on the town's ground in the Cloud Sea ({@link Placed}): somebody's, not ours to level. */
	private static boolean built(ServerLevel level, BlockPos at) {
		int low = Stacked.minY(level.getServer(), Stacked.CLOUD_SEA);
		int x0 = at.getX() - RADIUS, x1 = at.getX() + RADIUS, z0 = at.getZ() - RADIUS, z1 = at.getZ() + RADIUS;
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
				net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					return true;
				}
				java.util.Map<String, Placed.Mark> marks = chunk.getAttached(Placed.MARKS);
				if (marks == null) {
					continue;
				}
				for (String key : marks.keySet()) {
					BlockPos p = BlockPos.of(Long.parseLong(key));
					if (p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1 && p.getY() >= low) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** A new steam town round {@code center} on {@code floor}, its gate toward {@code gate}: built, named, peopled, its airship on her berth. */
	static Faction found(ServerLevel level, BlockPos center, int floor, Direction gate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos bell = new BlockPos(center.getX(), floor, center.getZ());
		deck(level, bell);
		CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
		// the streets in andesite and cobble (no gravel: it would fall through the cloud), the square in brick, the bell on it
		for (int[] street : STREETS) {
			for (int x = street[0]; x <= street[2]; x++) {
				for (int z = street[1]; z <= street[3]; z++) {
					BlockState road = Math.floorMod(x * 7 + z * 13, 5) == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
					level.setBlock(frame.at(x, -1, z), road, Block.UPDATE_ALL);
				}
			}
		}
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				level.setBlock(frame.at(x, -1, z), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
		DyeColor colour = COLOURS[random.nextInt(COLOURS.length)];
		Faction town = Faction.create(Factions.nextId(server), FactionType.VILLAGE, name(server, random), colour, bell,
			KingdomEvents.claim(server, bell, Villages.CLAIM_RADIUS));
		Factions.put(server, town);
		int id = town.id();
		List<Works.Built> built = new ArrayList<>();
		List<BlockPos> homes = new ArrayList<>(), shops = new ArrayList<>();
		for (Spot s : HOUSES) {
			homes.add(place(level, SteamBuildings.house(random), s, bell, gate, built));
		}
		shops.add(place(level, SteamBuildings.workshop(true), TINKERS, bell, gate, built));
		shops.add(place(level, SteamBuildings.workshop(false), ENGINEERS, bell, gate, built));
		shops.add(place(level, SteamBuildings.boilerHouse(), BOILERS, bell, gate, built));
		place(level, SteamBuildings.windmill(), WINDMILL, bell, gate, built);
		place(level, SteamBuildings.cokeYard(), COKE_YARD, bell, gate, built);
		place(level, SteamBuildings.airfield(), AIRFIELD, bell, gate, built);
		Works.update(server, id, w -> {
			List<Works.Built> all = new ArrayList<>(w.built());
			all.addAll(built);
			return w.withBuilt(all);
		});
		// the airfield's frame, as place() laid it, and the town's airship on her berth
		Direction fieldFacing = turned(gate, AIRFIELD.turns());
		Town record = new Town(id, frame.at(AIRFIELD.x(), -1, AIRFIELD.z()), fieldFacing, Optional.empty(), -1L, -1L, -1L, Optional.empty(), -1L, Optional.empty());
		put(level, record);
		SteamAirships.lay(level, record);
		Storehouse.build(level, Factions.get(server, id));
		for (BlockPos home : homes) {
			for (int i = 0; i < PER_HOUSE; i++) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, home, 3), EntitySpawnReason.STRUCTURE);
			}
		}
		for (BlockPos shop : shops) {
			for (int i = 0; i < PER_SHOP; i++) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, shop, 3), EntitySpawnReason.STRUCTURE);
			}
		}
		for (int i = 0; i < GOLEMS; i++) {
			EntityTypes.IRON_GOLEM.spawn(level, Construction.standable(level, frame.at(0, 0, -6), 3), EntitySpawnReason.STRUCTURE);
		}
		Faction stored = Factions.get(server, id);
		if (stored != null) {
			Villages.chooseElder(level, stored);
		}
		WalledTowns.front(server, id, gate);
		JaymosKingdoms.LOGGER.info("A steam town, {}, rose at {}", town.name(), bell);
		Faction out = Factions.get(server, id);
		return out != null ? out : town;
	}

	/**
	 * The town's deck round {@code bell} (on its floor): everything over it cleared, a floor of stone bricks (a few cracked,
	 * a few mossy where the damp gets in), cloud banked up under it down onto the overcast so it sits in the clouds rather than
	 * over them, and an iron rail round its edge so nobody walks off it.
	 */
	private static void deck(ServerLevel level, BlockPos bell) {
		int floor = bell.getY();
		Block cloudBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
			net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymospowers", "cloudbank"));
		BlockState cloud = cloudBlock == Blocks.AIR ? wool(DyeColor.WHITE) : cloudBlock.defaultBlockState();
		int inner = (RADIUS - 1) * (RADIUS - 1), outer = RADIUS * RADIUS;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				int d = dx * dx + dz * dz;
				if (d > outer) {
					continue;
				}
				BlockPos column = bell.offset(dx, 0, dz);
				for (int y = floor; y <= floor + CLEAR_HEIGHT; y++) {
					if (!level.getBlockState(column.atY(y)).isAir()) {
						level.setBlock(column.atY(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				int mix = Math.floorMod(dx * 31 + dz * 17, 23);
				BlockState stone = mix == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
					: mix == 1 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
				level.setBlock(column.atY(floor - 1), stone, Block.UPDATE_CLIENTS);
				for (int y = floor - 2; y >= floor - 1 - BANK_DEPTH; y--) {
					if (!level.getBlockState(column.atY(y)).isAir()) {
						break;
					}
					level.setBlock(column.atY(y), cloud, Block.UPDATE_CLIENTS);
				}
				if (d > inner) {
					level.setBlock(column.atY(floor), Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
	}

	/** {@code gate} turned clockwise {@code turns} times. */
	private static Direction turned(Direction gate, int turns) {
		Direction facing = gate;
		for (int i = 0; i < turns; i++) {
			facing = facing.getClockWise();
		}
		return facing;
	}

	/**
	 * Places {@code blueprint} at once at {@code spot} (its y = 0 the floor, laid in place of the ground), turned to face
	 * its way, and adds it to {@code built} as one of the town's buildings. Returns the middle of its floor.
	 */
	private static BlockPos place(ServerLevel level, Blueprint blueprint, Spot spot, BlockPos bell, Direction gate, List<Works.Built> built) {
		CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
		CampBuildings.Placer placer = new CampBuildings.Placer(frame.at(spot.x(), -1, spot.z()), turned(gate, spot.turns()), blueprint.width());
		for (Blueprint.Placement p : blueprint.blocks()) {
			level.setBlock(placer.at(p.offset()), placer.turn(p.state()), Block.UPDATE_ALL);
		}
		BlockPos a = placer.at(0, 0, 0), b = placer.at(blueprint.width() - 1, blueprint.height() - 1, blueprint.depth() - 1);
		built.add(new Works.Built(blueprint.id(), new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ())),
			new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()))));
		return placer.at(blueprint.width() / 2, 1, blueprint.depth() / 2);
	}

	/** The wool of {@code colour}. */
	static BlockState wool(DyeColor colour) {
		return SteamGoods.vanilla(colour.getSerializedName() + "_wool", Blocks.AIR);
	}

	private static String name(MinecraftServer server, RandomSource random) {
		String name = "";
		for (int tries = 0; tries < 20; tries++) {
			name = NAME_FIRST[random.nextInt(NAME_FIRST.length)] + NAME_SECOND[random.nextInt(NAME_SECOND.length)];
			if (Factions.byName(server, name) == null) {
				return name;
			}
		}
		return name + Factions.nextId(server);
	}
}
