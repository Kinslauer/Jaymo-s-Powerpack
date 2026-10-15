package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * How factions come to be and what moves them: a village's bell becomes a capital the first time someone comes near
 * ({@link #FOUND_RANGE}) if no other capital is close ({@link #CAPITAL_SPACING}), with a king, guards, a banner, a
 * storehouse and land around it, its seat and storehouse in the village's own buildings ({@link Seats}); otherwise it
 * becomes a village ({@link Villages}). Only a bell inside a village the world generated founds anything. Deaths shift reputation and
 * standing, and leave places to fill; a fallen ruler's realm meets one of three fates (a successor, vassalage to the
 * victor's kingdom as a fiefdom, or ruin); crossing into a faction's land announces it. Also drives the other
 * systems' ticks (storehouses, production, hiring, villages).
 */
public final class KingdomEvents {
	/** A bell this close to a player becomes a capital, if no realm's seat is within {@code CAPITAL_SPACING}; else a village. */
	static final int FOUND_RANGE = 48, CAPITAL_SPACING = 320;
	/** A bell this close to any faction's seat is part of that settlement (a town with several bells), not a new one. */
	static final int TOWN_RADIUS = 64;
	/** Land claimed around a new capital: a square this many chunks out each way. */
	static final int CLAIM_RADIUS = 6;
	/** Guards on foot (two in the hall, the rest at the gate and about the town); an archer on every post. */
	static final int GUARDS = 8;
	/** Reputation changes. */
	static final int REP_KILL_GUARD = -40, REP_KILL_BUILDER = -30, REP_KILL_KING = -400, REP_KILL_LORD = -250, REP_KILL_KING_RIVALS = 60,
		REP_KILL_MONSTER = 2;
	/** Standing a member earns for a monster killed on their faction's land. */
	static final int STANDING_KILL_MONSTER = 3;
	/** A successor takes the throne this long after the ruler falls (game ticks: one day). */
	static final long SUCCESSION_TICKS = 24000L;
	/** When a realm falls to ruin, its folk this close to the one who died scatter. */
	static final double RUIN_SCATTER = 96.0;

	/** A bell found outside any village is looked at again only after this long (ticks). */
	static final long NOT_VILLAGE_TICKS = 6000L;
	/** A village is founded only when it's loaded this far round its bell (its buildings further out aren't considered). */
	static final int LOAD_REACH = 112;

	/** Per player: the faction whose land they were last in (for the "entering" message). */
	private static final Map<UUID, Integer> WHERE = new HashMap<>();
	/** Bells outside any village (packed position → game time to look again). */
	private static final Map<Long, Long> NOT_VILLAGE = new HashMap<>();

	private KingdomEvents() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(KingdomEvents::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(Factions::migrate);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WHERE.clear();
			NOT_VILLAGE.clear();
			Economy.clear();
			Storehouse.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> Economy.hurt(entity, source.getEntity()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
			// a monster cut down on a faction's land: they're grateful (members earn standing too)
			if (entity instanceof Enemy && player != null) {
				Faction faction = Factions.at(level.getServer(), level, entity.blockPosition());
				if (faction != null) {
					Reputation.change(player, faction, REP_KILL_MONSTER);
					Faction.Member member = faction.member(player.getUUID());
					if (member != null) {
						standing(level.getServer(), player, faction, member.standing() + STANDING_KILL_MONSTER);
					}
				}
			}
			if (entity instanceof Villager villager) {
				Villages.villagerDied(level, villager, player);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		if (now % 100 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.level() == overworld && !player.isSpectator()) {
					tryFound(overworld, player);
				}
			}
			for (Faction faction : Factions.all(server)) {
				if (faction.successionAt() >= 0 && now >= faction.successionAt() && !faction.fallen()) {
					crown(overworld, faction.withSuccessionAt(-1L));
					announce(server, Component.translatable(faction.type() == FactionType.FIEFDOM ? "message.jaymoskingdoms.succession.lord"
						: "message.jaymoskingdoms.succession", faction.name()).withStyle(ChatFormatting.GOLD));
				}
			}
			Storehouse.tick(server);
		}
		Production.tick(server);
		Economy.tick(server);
		Villages.tick(server);
		if (now % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Faction here = Factions.at(server, player.level(), player.blockPosition());
				int id = here == null ? -1 : here.id();
				Integer was = WHERE.put(player.getUUID(), id);
				if (was != null && was != id && here != null) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.entering", here.name(),
						Component.translatable("reputation.jaymoskingdoms." + Reputation.word(Reputation.get(player, here.id()))))
						.withStyle(here.color().getTextColor() == 0 ? ChatFormatting.WHITE : ChatFormatting.GOLD));
				}
			}
		}
	}

	/**
	 * A village's bell near {@code player} that belongs to no settlement yet becomes a capital, or (near another capital) a
	 * village; but only a bell inside a village the world generated (0.5): anywhere else it founds nothing. While the
	 * village's pieces can't all be read yet (chunks not loaded), it waits.
	 */
	private static void tryFound(ServerLevel level, ServerPlayer player) {
		Optional<BlockPos> bell = level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), player.blockPosition(), FOUND_RANGE,
			PoiManager.Occupancy.ANY);
		MinecraftServer server = level.getServer();
		if (bell.isEmpty() || Factions.near(server, bell.get(), TOWN_RADIUS) != null) {
			return;
		}
		long now = level.getGameTime();
		long key = bell.get().asLong();
		if (NOT_VILLAGE.getOrDefault(key, Long.MIN_VALUE) > now) {
			return;
		}
		Seats.Found village = Seats.structureAt(level, bell.get(), Seats::village);
		if (village.status() == Seats.Status.UNREADY) {
			return;
		}
		if (village.status() == Seats.Status.NONE || village.start() == null) {
			NOT_VILLAGE.put(key, now + NOT_VILLAGE_TICKS);
			return;
		}
		// (the village must be loaded round the bell to choose its buildings: a great town's far end may be left out)
		BoundingBox area = village.start().getBoundingBox();
		BlockPos b = bell.get();
		if (!level.hasChunksAt(Math.max(area.minX(), b.getX() - LOAD_REACH), Math.max(area.minZ(), b.getZ() - LOAD_REACH),
			Math.min(area.maxX(), b.getX() + LOAD_REACH), Math.min(area.maxZ(), b.getZ() + LOAD_REACH))) {
			return;
		}
		if (Factions.realmNear(server, bell.get(), CAPITAL_SPACING) == null) {
			if (foundIn(level, bell.get(), village.start()) == null) {
				// (0.5.1) no building there fit for a king: the kingdom raises its citadel beside the village
				found(level, bell.get(), Castle.Kind.CITADEL);
			}
		} else {
			Villages.found(level, bell.get());
		}
	}

	/** The chunks within {@code radius} of {@code center} that no standing faction holds (packed). */
	static List<Long> claim(MinecraftServer server, BlockPos center, int radius) {
		List<Long> claims = new ArrayList<>();
		ChunkPos middle = ChunkPos.containing(center);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				ChunkPos chunk = new ChunkPos(middle.x() + dx, middle.z() + dz);
				boolean taken = false;
				for (Faction other : Factions.all(server)) {
					if (other.claims(chunk)) {
						taken = true;
						break;
					}
				}
				if (!taken) {
					claims.add(chunk.pack());
				}
			}
		}
		return claims;
	}

	/** A new kingdom at {@code capital}: named, coloured, its land claimed, its banner raised, its king and guards. */
	static Faction found(ServerLevel level, BlockPos capital) {
		return found(level, capital, null);
	}

	/**
	 * (with {@code kind}, its seat is that kind of castle, built beside the village as before 0.5; without, it takes the
	 * village's own buildings if {@code capital} is in a village, else a castle of a random kind: operators only)
	 */
	static Faction found(ServerLevel level, BlockPos capital, Castle.@Nullable Kind kind) {
		Seats.Found village = Seats.structureAt(level, capital, Seats::village);
		StructureStart start = village.status() == Seats.Status.FOUND ? village.start() : null;
		if (kind == null && start != null) {
			Faction k = foundIn(level, capital, start);
			if (k != null) {
				return k;
			}
		}
		MinecraftServer server = level.getServer();
		Faction kingdom = create(level, capital);
		// the keep, beside the village, its gate toward the bell
		Castle.Keep keep = Castle.build(level, kingdom, capital, kind);
		Factions.putKeep(server, kingdom.id(), keep);
		kingdom = man(level, kingdom, keep);
		// its storehouse (and its builders), its farm and farmer
		Storehouse.build(level, Factions.get(server, kingdom.id()));
		settle(level, Factions.get(server, kingdom.id()), start, capital);
		JaymosKingdoms.LOGGER.info("Kingdom of {} founded at {}; its keep's throne at {}, panic room at {}", kingdom.name(), capital, keep.throne(),
			keep.cellar());
		return Factions.get(server, kingdom.id());
	}

	/**
	 * A new kingdom at {@code bell} in its village's own buildings (0.5): its church (or largest house) is the seat, with
	 * a throne, a panic room and archers on the roofs round it ({@link Seats#keep}); another building is the storehouse
	 * ({@link Storehouse#adopt}; open ground if none will do). Both are kept from the builders ({@link Holds}), who grow
	 * the town from there. Null (and nothing founded) if no building in the village has a floor under a roof.
	 */
	static @Nullable Faction foundIn(ServerLevel level, BlockPos bell, StructureStart village) {
		MinecraftServer server = level.getServer();
		// (0.5.1: the largest building is the king's, a church no more than any other)
		Seats.Pick pick = Seats.pick(level, Seats.buildings(village), bell, false);
		if (pick == null) {
			JaymosKingdoms.LOGGER.info("The village at {} has no building fit for a seat", bell);
			return null;
		}
		Faction kingdom = create(level, bell);
		Castle.Keep keep = Seats.keep(level, kingdom, pick.seat(), bell, true, true);
		Factions.putKeep(server, kingdom.id(), keep);
		Holds.guard(server, kingdom.id(), pick.seat().box());
		kingdom = man(level, kingdom, keep);
		Faction stored = Factions.get(server, kingdom.id());
		BlockPos depot = pick.store() != null ? Storehouse.adopt(level, stored, pick.store(), null) : null;
		if (depot == null) {
			depot = Storehouse.adopt(level, stored, pick.seat(), keep.throne());
		}
		if (depot == null) {
			Storehouse.build(level, stored);
		}
		settle(level, Factions.get(server, kingdom.id()), village, bell);
		JaymosKingdoms.LOGGER.info("Kingdom of {} founded at {} in its village's {} (throne {}, panic room {}, {} archers' posts)", kingdom.name(), bell,
			pick.seat().name(), keep.throne(), keep.cellar(), keep.posts().size());
		return Factions.get(server, kingdom.id());
	}

	/**
	 * (0.5.1) A new kingdom's farmer: he keeps the village's farm nearest the bell (a crop field first, else an animal
	 * pen), kept from the builders ({@link Holds}); with none, the builders put up a farm in the village's style there and
	 * then. Without a farm at all (nowhere to put one) there's no farmer yet: one is taken on when a farm goes up.
	 */
	static void settle(ServerLevel level, @Nullable Faction kingdom, @Nullable StructureStart village, BlockPos bell) {
		if (kingdom == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		List<BoundingBox> farms = village == null ? List.of() : Seats.farms(village, bell);
		if (!farms.isEmpty()) {
			BoundingBox b = farms.getFirst();
			Farmers.adopt(server, kingdom, new BlockPos(b.minX(), b.minY(), b.minZ()), new BlockPos(b.maxX(), b.maxY(), b.maxZ()));
			Holds.guard(server, kingdom.id(), b);
		} else {
			Works.Project p = Planner.plan(level, kingdom, true, "farm");
			Faction now = Factions.get(server, kingdom.id());
			if (p != null && now != null) {
				Construction.finishNow(level, now, p);
			}
		}
		Faction now = Factions.get(server, kingdom.id());
		// (a farm just built has its farmer already: {@link Gatherers#built})
		if (now != null && Farmers.wanted(server, now) && Farmers.spawn(level, now, null) != null) {
			JaymosKingdoms.LOGGER.info("{} has its farmer at {}", now.name(), farms.isEmpty() ? "a new farm" : farms.getFirst().getCenter());
		}
	}

	/** A new kingdom's faction at {@code capital}: named, coloured, its land claimed, its banner raised. */
	private static Faction create(ServerLevel level, BlockPos capital) {
		MinecraftServer server = level.getServer();
		var random = level.getRandom();
		DyeColor color = DyeColor.values()[random.nextInt(DyeColor.values().length)];
		Faction kingdom = Faction.create(Factions.nextId(server), FactionType.KINGDOM, Names.kingdom(random), color, capital,
			claim(server, capital, CLAIM_RADIUS)).withTreasury(Treasuries.start(random));
		Factions.put(server, kingdom);
		raiseBanner(level, kingdom);
		return kingdom;
	}

	/** Its king crowned, its guards and an archer on every post of {@code keep}. Returns it as stored. */
	private static Faction man(ServerLevel level, Faction kingdom, Castle.Keep keep) {
		MinecraftServer server = level.getServer();
		kingdom = crown(level, kingdom);
		for (int i = 0; i < GUARDS; i++) {
			spawn(level, kingdom, Folk.GUARD);
		}
		for (int i = 0; i < keep.posts().size(); i++) {
			spawnArcher(level, Factions.get(server, kingdom.id()));
		}
		return Factions.get(server, kingdom.id());
	}

	/** The role that leads a faction of {@code type} (-1: a village's elder is a villager, not folk). */
	public static int leaderRole(FactionType type) {
		return switch (type) {
			case KINGDOM -> Folk.KING;
			case FIEFDOM -> Folk.LORD;
			case BARBARIAN -> Folk.CHIEF;
			case VILLAGE -> -1;
		};
	}

	/**
	 * A ruler takes the seat (a new kingdom's first king, a successor, a fiefdom's lord; a village chooses an elder).
	 * Returns the faction as stored, with him in it.
	 */
	static Faction crown(ServerLevel level, Faction faction) {
		MinecraftServer server = level.getServer();
		Factions.put(server, faction);
		int role = leaderRole(faction.type());
		if (role < 0) {
			Villages.chooseElder(level, faction);
			return Factions.get(server, faction.id());
		}
		Folk ruler = spawn(level, faction, role);
		return Factions.update(server, faction.id(), f -> f.withLeader(ruler == null ? Optional.empty() : Optional.of(ruler.getUUID())));
	}

	/**
	 * One of the faction's people, where they belong (and counted in its roster, if not its ruler): the ruler on his
	 * throne (or, a third of the time, hiding in the panic room below it); guards two to the throne room, three at the
	 * gate, the rest about the town. Without a keep, by the seat.
	 */
	public static @Nullable Folk spawn(ServerLevel level, Faction faction, int role) {
		var random = level.getRandom();
		Castle.Keep keep = Factions.keep(level.getServer(), faction.id());
		Folk.Role spec = Folk.role(role);
		boolean ruler = spec != null && spec.leader();
		Folk folk = null;
		if (keep != null) {
			folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
			if (folk == null) {
				return null;
			}
			BlockPos at;
			int radius;
			if (ruler) {
				boolean hiding = random.nextInt(3) == 0;
				at = hiding ? keep.cellar() : keep.throne();
				radius = hiding ? 2 : 3;
			} else {
				long inside = level.getEntitiesOfClass(Folk.class, new AABB(keep.hall()).inflate(8.0),
					f -> f.factionId() == faction.id() && f.role() == Folk.GUARD).size();
				long atGate = level.getEntitiesOfClass(Folk.class, new AABB(keep.gate()).inflate(6.0),
					f -> f.factionId() == faction.id() && f.role() == Folk.GUARD).size();
				if (inside < 2) {
					at = keep.hall().offset(random.nextInt(5) - 2, 0, random.nextInt(3) - 1);
					radius = 5;
				} else if (atGate < 3) {
					at = keep.gate().offset(random.nextInt(5) - 2, 0, 0);
					radius = 8;
				} else {
					// about the town
					at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						faction.seat().offset(random.nextInt(41) - 20, 0, random.nextInt(41) - 20));
					radius = 32;
				}
			}
			folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			folk.setup(role, faction, at, radius);
			level.addFreshEntity(folk);
		} else {
			for (int tries = 0; tries < 12 && folk == null; tries++) {
				BlockPos at = faction.seat().offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
				at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
				if (!level.getBlockState(at.below()).isSolid() || !level.getFluidState(at.below()).isEmpty()) {
					continue;
				}
				folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
				if (folk == null) {
					return null;
				}
				folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
				folk.setup(role, faction, faction.seat(), Folk.HOME_RADIUS);
				level.addFreshEntity(folk);
			}
		}
		if (folk != null && !ruler) {
			Factions.update(level.getServer(), faction.id(), f -> f.withHoldings(f.holdings().adjust(role, 1)));
		}
		return folk;
	}

	/** An archer on one of the faction's posts (walls, towers, watchtowers) that has none, counted in its roster. */
	public static @Nullable Folk spawnArcher(ServerLevel level, @Nullable Faction faction) {
		if (faction == null) {
			return null;
		}
		Castle.Keep keep = Factions.keep(level.getServer(), faction.id());
		if (keep == null || keep.posts().isEmpty()) {
			return spawn(level, faction, Folk.GUARD);
		}
		BlockPos post = null;
		for (BlockPos candidate : keep.posts()) {
			if (level.getEntitiesOfClass(Folk.class, new AABB(candidate).inflate(2.0), f -> f.role() == Folk.ARCHER).isEmpty()) {
				post = candidate;
				break;
			}
		}
		if (post == null) {
			post = keep.posts().get(level.getRandom().nextInt(keep.posts().size()));
		}
		Folk archer = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
		if (archer == null) {
			return null;
		}
		archer.snapTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		archer.setup(Folk.ARCHER, faction, post, 1);
		level.addFreshEntity(archer);
		Factions.update(level.getServer(), faction.id(), f -> f.withHoldings(f.holdings().adjust(Folk.ARCHER, 1)));
		return archer;
	}

	/** The kingdom's banner, on the ground beside the bell. */
	private static void raiseBanner(ServerLevel level, Faction kingdom) {
		Block banner = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(kingdom.color().getName() + "_banner"));
		for (int tries = 0; tries < 10; tries++) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				kingdom.seat().offset(level.getRandom().nextInt(7) - 3, 0, level.getRandom().nextInt(7) - 3));
			if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolid()) {
				if (banner != Blocks.AIR) {
					level.setBlock(at, banner.defaultBlockState(), Block.UPDATE_ALL);
				}
				return;
			}
		}
	}

	/**
	 * One of a faction's people died: reputation for whoever did it, and a place to fill; a realm's ruler's death
	 * decides its fate.
	 */
	/** (0.5.21) One of a faction's MCA villagers in a job died ({@link Workers#died}). */
	static void workerDied(ServerLevel level, Worker w, DamageSource source) {
		MinecraftServer server = level.getServer();
		Faction faction = Factions.get(server, w.factionId());
		if (faction != null && w.role() >= 0) {
			lostHand(server, faction, w.role(), source.getEntity() instanceof ServerPlayer p ? p : null, true);
		}
	}

	/** One of {@code faction}'s people (not its leader) is dead: a place to fill, and their killer remembered. */
	private static void lostHand(MinecraftServer server, Faction faction, int role, @Nullable ServerPlayer killer, boolean rostered) {
		if (rostered) {
			Economy.lost(server, faction.id(), role);
		}
		// a camp's hatred of you changes nothing: barbarians are at war with everyone already
		if (killer != null && faction.type() != FactionType.BARBARIAN) {
			Reputation.change(killer, faction, Folk.isBuilder(role) ? REP_KILL_BUILDER : REP_KILL_GUARD);
		}
	}

	static void folkDied(ServerLevel level, Folk folk, DamageSource source) {
		MinecraftServer server = level.getServer();
		Faction faction = Factions.get(server, folk.factionId());
		if (faction == null) {
			return;
		}
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
		if (Corsairs.isCrew(folk) || Pirates.offRoster(folk.role())) {
			// (0.7.37) a ship's crew, a harbour's shipwright, a pirate captain ashore: never on a roster; a cove's captain is its
			// leader (his cove is cleared: Camps)
			if (faction.leader().isPresent() && faction.leader().get().equals(folk.getUUID())) {
				Factions.update(server, faction.id(), f -> f.withLeader(Optional.empty()));
			}
			lostHand(server, faction, folk.role(), killer, false);
			return;
		}
		if (!folk.leader()) {
			// (0.5.14) a caravan's carters and guards were never on its roster
			lostHand(server, faction, folk.role(), killer, !Caravans.isCaravanFolk(folk));
			return;
		}
		boolean wasLeader = faction.leader().isPresent() && faction.leader().get().equals(folk.getUUID());
		Factions.update(server, faction.id(), f -> f.withLeader(Optional.empty()));
		if (!faction.type().isRealm()) {
			// (a camp's chief: the barbarians' own business)
			return;
		}
		boolean lord = folk.role() == Folk.LORD;
		if (OldAge.is(source)) {
			// (0.5.54) died in his bed, of old age: the realm mourns, and his heir is crowned (no war, no fate decides it)
			if (wasLeader) {
				Factions.update(server, faction.id(), f -> f.withSuccessionAt(level.getGameTime() + SUCCESSION_TICKS));
				announce(server, Component.translatable(lord ? "message.jaymoskingdoms.old_age.lord" : "message.jaymoskingdoms.old_age.king",
					faction.name()).withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		if (wasLeader && Assassins.murdered(level, faction, folk, source)) {
			// (0.5.59) murdered by an assassin's blade or poison: mourned, and his heir crowned
			Wardens.rulerFell(level, faction);
			Factions.update(server, faction.id(), f -> f.withSuccessionAt(level.getGameTime() + SUCCESSION_TICKS));
			return;
		}
		if (killer != null) {
			Reputation.change(killer, faction, lord ? REP_KILL_LORD : REP_KILL_KING);
			// the realm's rivals are glad of it (every other standing realm that isn't its vassal or overlord)
			for (Faction other : Factions.all(server)) {
				if (other.id() != faction.id() && other.type().isRealm() && !other.fallen() && Relations.between(other, faction) != Relation.ALLIED) {
					Reputation.change(killer, other, REP_KILL_KING_RIVALS);
				}
			}
		}
		if (!wasLeader) {
			return;
		}
		// (0.5.1) his wardens failed him
		Wardens.rulerFell(level, faction);
		announce(server, Component.translatable(lord ? "message.jaymoskingdoms.lord_fallen" : "message.jaymoskingdoms.king_fallen", faction.name(),
			killer == null ? Component.translatable("message.jaymoskingdoms.someone") : killer.getDisplayName()).withStyle(ChatFormatting.RED));
		// a ruler killed by the other side of a war: the war decides
		if (Wars.rulerFell(level, faction, source)) {
			return;
		}
		// one of three fates, at random
		Faction victor = killer == null ? null : Factions.of(server, killer.getUUID());
		int fate = level.getRandom().nextInt(3);
		long heir = level.getGameTime() + SUCCESSION_TICKS;
		if (fate == 1 && victor != null && victor.id() != faction.id() && victor.type().isRealm() && victor.id() != faction.overlord()) {
			if (faction.overlord() >= 0) {
				Relations.set(server, faction.id(), faction.overlord(), Relation.NEUTRAL);
			}
			Relations.vassalize(server, faction.id(), victor.id());
			Factions.update(server, faction.id(), f -> f.withSuccessionAt(heir));
			announce(server, Component.translatable("message.jaymoskingdoms.fate.vassal", faction.name(), victor.name()).withStyle(ChatFormatting.GOLD));
		} else if (fate == 2) {
			Factions.update(server, faction.id(), f -> f.withFallen(true).withHoldings(f.holdings().withRoster(Map.of()).withVacancies(Map.of())));
			for (Folk other : level.getEntitiesOfClass(Folk.class, folk.getBoundingBox().inflate(RUIN_SCATTER), f -> f.factionId() == folk.factionId())) {
				// with nothing left to guard, they scatter
				other.discard();
			}
			announce(server, Component.translatable("message.jaymoskingdoms.fate.ruin", faction.name()).withStyle(ChatFormatting.DARK_RED));
		} else {
			Factions.update(server, faction.id(), f -> f.withSuccessionAt(heir));
			announce(server, Component.translatable(lord ? "message.jaymoskingdoms.fate.successor_lord" : "message.jaymoskingdoms.fate.successor",
				faction.name()).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** An audience with one of a faction's people (right click): what they think of you, and (its ruler) an offer to pledge. */
	static void audience(ServerPlayer player, Folk folk) {
		MinecraftServer server = player.level().getServer();
		Faction faction = Factions.get(server, folk.factionId());
		// barbarians don't hold court
		if (faction == null || faction.type() == FactionType.BARBARIAN) {
			return;
		}
		int rep = Reputation.get(player, faction.id());
		Faction.Member member = faction.member(player.getUUID());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.audience", faction.name(),
			Component.translatable("reputation.jaymoskingdoms." + Reputation.word(rep)), rep).withStyle(ChatFormatting.GOLD));
		if (member != null) {
			Rank rank = Rank.forStanding(member.standing());
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.audience.member", Component.translatable(rank.key()), member.standing())
				.withStyle(ChatFormatting.GRAY));
		} else if (folk.leader() && faction.type().isRealm() && rep >= Reputation.WELCOME && Factions.of(server, player.getUUID()) == null) {
			String command = "/kingdom pledge " + faction.name();
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.audience.offer").withStyle(style -> style.withColor(ChatFormatting.GREEN)
				.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
		}
		// (0.5.1) a ruler short of guards keeps to his hall, and may ask for a sworn protector
		Wardens.audience(player.level(), player, faction, folk);
		player.level().playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, 0.7F);
	}

	/** Sets a member's standing (and so their rank), telling them of a promotion. */
	static void standing(MinecraftServer server, ServerPlayer player, Faction faction, int standing) {
		Faction current = Factions.get(server, faction.id());
		Faction.Member member = current == null ? null : current.member(player.getUUID());
		if (member == null) {
			return;
		}
		Rank before = Rank.forStanding(member.standing());
		Rank after = Rank.forStanding(standing);
		Factions.update(server, faction.id(), f -> f.withMember(player.getUUID(), new Faction.Member(after.ordinal(), standing)));
		if (after.ordinal() > before.ordinal()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.promoted", Component.translatable(after.key()), faction.name())
				.withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
	}

	static void announce(MinecraftServer server, Component message) {
		server.getPlayerList().broadcastSystemMessage(message, false);
	}

	static void forget(ServerPlayer player) {
		WHERE.remove(player.getUUID());
	}
}
