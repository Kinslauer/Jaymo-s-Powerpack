package com.jaymoskingdoms;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Barbarian camps ({@link FactionType#BARBARIAN} factions): they rise in the wild {@link #MIN_DISTANCE}-{@link
 * #MAX_DISTANCE} blocks from a kingdom, on open natural ground out of a player's sight, a few to a kingdom
 * ({@link #CAMPS_PER_KINGDOM}); each is built at once ({@link CampBuildings}: tents, a palisade, a fire, the chief's
 * tent with his hoard, the loot pile that is its storage), with a chief, raiders and a builder crew. Each day a camp
 * grows: it takes in hides, wood and food of its own (a little: camps live on their {@link Raids}' loot) and another
 * raider joins it now and then; its builders put up what the catalogue holds. Killing its chief clears it: every
 * settlement is grateful, its people scatter, it falls, and its tents rot away over the next days, leaving the
 * palisade standing as a ruin.
 */
public final class Camps {
	/** How often camps are looked at (ticks), and how often a new one may rise (ticks). */
	static final int CHECK_TICKS = 200, SPAWN_CHECK_TICKS = 1200;
	/** A camp rises this far from a kingdom (or fiefdom), and at least {@code MIN_DISTANCE} from any settlement. */
	static final double MIN_DISTANCE = 150.0, MAX_DISTANCE = 300.0;
	/** At most this many standing camps within {@code CAMP_AREA} of a kingdom; camps keep {@code CAMP_SPACING} apart. */
	static final int CAMPS_PER_KINGDOM = 2;
	static final double CAMP_AREA = 360.0, CAMP_SPACING = 160.0;
	/** The chance a kingdom with room for a camp gets one, each spawn check. */
	static final float CAMP_CHANCE = 0.2F;
	/** Sites are tried round players near the kingdom, out of their sight: this far from any player, up to this much farther. */
	static final int HIDE_RANGE = 56, SITE_SPREAD = 72, SITE_TRIES = 10;
	/** No village bell this near a site (it would be taken for part of the village). */
	static final int BELL_CLEARANCE = 96;
	/** The most the ground may rise and fall across a camp. */
	static final int MAX_SPREAD = 4;
	/** Its land: chunks out each way from its fire. */
	static final int CLAIM_RADIUS = 1;
	/** A new camp's raiders, and how its band grows: one more every so many days, up to a most. */
	static final int START_BARBARIANS = 4, DAYS_PER_BARBARIAN = 2, MAX_BARBARIANS = 12;
	/** The chief keeps this near his tent. */
	static final int CHIEF_HOME_RADIUS = 10;
	/** Missed days made up at most. */
	static final int MAX_CATCH_UP = 3;
	/** What a camp takes in each day by its own hands (hunting, herding, felling, a little crafting). */
	static final int DAY_HIDES = 8, DAY_DARK_HIDES = 4, DAY_PAINTED = 2, DAY_FENCES = 6, DAY_PLANKS = 8, DAY_LADDERS = 3, DAY_GATES = 1,
		DAY_HAY = 2, DAY_BONE = 1, DAY_MEAT = 6, DAY_LEATHER = 2;
	/** The loot pile a new camp starts with. */
	static final int START_LOGS = 32, START_HIDES = 16, START_MEAT = 16, START_EMERALDS = 3;
	/** Clearing a camp: reputation with every settlement for the one who killed its chief, and for those who fought beside them. */
	static final int REP_CLEAR = 100, REP_CLEAR_HELPER = 40;
	static final double HELPER_RANGE = 48.0;
	/** When its chief dies, the camp's people this near its fire scatter. */
	static final double SCATTER_RANGE = 160.0;
	/** Rot: every this many ticks (while loaded) this many perishable blocks go. */
	static final long ROT_STEP_TICKS = 600L;
	static final int ROT_PER_STEP = 4;
	/** Clock ticks in a day. */
	static final long DAY_TICKS = 24000L;

	/**
	 * What's known of a camp beyond its {@link Faction}: its fire and gate, when it was founded, last raided and last
	 * grew (a day number), when it was cleared (-1: standing), the blocks still to rot, when it last rotted, loot
	 * waiting for its pile to be loaded (item id → count), what kind of camp it is ({@link Camps#BARBARIAN} or
	 * {@link IllagerCamps#KIND}), and (illager camps) how many of its growth buildings it has raised by its own hands and
	 * how many days it has grown.
	 */
	public record CampData(BlockPos center, Direction gate, long founded, long lastRaid, long lastDay, long clearedAt, List<Long> rot,
		long lastRot, Map<String, Integer> pending, String kind, int built, int grown) {
		public static final Codec<CampData> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("center").forGetter(CampData::center),
				Direction.CODEC.optionalFieldOf("gate", Direction.NORTH).forGetter(CampData::gate),
				Codec.LONG.optionalFieldOf("founded", 0L).forGetter(CampData::founded),
				Codec.LONG.optionalFieldOf("last_raid", 0L).forGetter(CampData::lastRaid),
				Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(CampData::lastDay),
				Codec.LONG.optionalFieldOf("cleared_at", -1L).forGetter(CampData::clearedAt),
				Codec.LONG.listOf().optionalFieldOf("rot", List.of()).forGetter(CampData::rot),
				Codec.LONG.optionalFieldOf("last_rot", 0L).forGetter(CampData::lastRot),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("pending", Map.of()).forGetter(CampData::pending),
				Codec.STRING.optionalFieldOf("kind", BARBARIAN).forGetter(CampData::kind),
				Codec.INT.optionalFieldOf("built", 0).forGetter(CampData::built),
				Codec.INT.optionalFieldOf("grown", 0).forGetter(CampData::grown))
			.apply(i, CampData::new));

		boolean illager() {
			return IllagerCamps.KIND.equals(this.kind);
		}

		boolean goblin() {
			return GoblinVillages.KIND.equals(this.kind);
		}

		boolean orc() {
			return OrcCamps.KIND.equals(this.kind);
		}

		boolean pirate() {
			return PirateCoves.KIND.equals(this.kind);
		}

		CampData withLastRaid(long at) {
			return new CampData(this.center, this.gate, this.founded, at, this.lastDay, this.clearedAt, this.rot, this.lastRot, this.pending,
				this.kind, this.built, this.grown);
		}

		CampData withLastDay(long day) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, day, this.clearedAt, this.rot, this.lastRot, this.pending,
				this.kind, this.built, this.grown);
		}

		CampData withCleared(long at, List<Long> rot) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, this.lastDay, at, List.copyOf(rot), at, this.pending,
				this.kind, this.built, this.grown);
		}

		CampData withRot(List<Long> rot, long at) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, this.lastDay, this.clearedAt, List.copyOf(rot), at, this.pending,
				this.kind, this.built, this.grown);
		}

		CampData withPending(Map<String, Integer> pending) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, this.lastDay, this.clearedAt, this.rot, this.lastRot,
				Map.copyOf(pending), this.kind, this.built, this.grown);
		}

		CampData withBuilt(int built) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, this.lastDay, this.clearedAt, this.rot, this.lastRot, this.pending,
				this.kind, built, this.grown);
		}

		CampData withGrown(int grown) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, this.lastDay, this.clearedAt, this.rot, this.lastRot, this.pending,
				this.kind, this.built, grown);
		}
	}

	/** {@link CampData#kind} of a barbarian camp. */
	static final String BARBARIAN = "barbarian";

	/** Every camp's data (faction id → it), kept on the Overworld. */
	static final AttachmentType<Map<String, CampData>> CAMPS = AttachmentRegistry.create(JaymosKingdoms.id("camps"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, CampData.CODEC)));

	private static final String[] NAME_FIRST = {"Skar", "Grim", "Vork", "Ulf", "Brak", "Thur", "Krag", "Hrod", "Gorm", "Rag", "Drek", "Ozz",
		"Murg", "Sval", "Tarn", "Yrsk", "Kol", "Harg"};
	private static final String[] NAME_SECOND = {"fang", "maw", "horn", "skull", "tusk", "claw", "hide", "blood", "ash", "bone", "rot", "spear",
		"howl", "scar"};

	private Camps() {
	}

	/** Roles, the catalogue and the events (call once at init). */
	static void register() {
		Barbarians.init();
		Illagers.init();
		Goblins.init();
		// 0.5.45: orc war camps
		Orcs.init();
		// 0.7.42: pirate coves and the sailors of the kingdoms
		Pirates.init();
		CampBuildings.registerCatalogue();
		ServerTickEvents.END_SERVER_TICK.register(Camps::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Raids.clear();
			IllagerCamps.clear();
			GoblinVillages.clear();
			OrcCamps.clear();
			Orcs.clear();
			PirateCoves.clear();
			Pirates.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Raids.loaded(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Folk folk && folk.level() instanceof ServerLevel level) {
				if (folk.role() == Folk.CHIEF || folk.role() == Goblins.KING || folk.role() == Orcs.WARCHIEF || Pirates.isCoveCaptain(folk)) {
					chiefSlain(level, folk, folk.factionId(), source);
				} else if (Goblins.isGoblin(folk)) {
					Goblins.died(level, folk, source);
				} else if (Raids.isRaider(folk)) {
					Raids.raiderDied(level, folk, folk.factionId(), source, true);
				}
			} else if (entity instanceof Worker w && entity.level() instanceof ServerLevel level && Raids.isRaider(entity)) {
				// (0.7.295) a raider who's an MCA villager
				Raids.raiderDied(level, entity, w.factionId(), source, true);
			} else if (entity.level() instanceof ServerLevel level && Illagers.isCampIllager(entity)) {
				Illagers.died(level, entity, source);
			}
		});
	}

	// ---- data ----------------------------------------------------------------------------------------------------

	static Map<String, CampData> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(CAMPS, Map.of());
	}

	static @Nullable CampData data(MinecraftServer server, int id) {
		return all(server).get(Integer.toString(id));
	}

	static void putData(MinecraftServer server, int id, @Nullable CampData data) {
		Map<String, CampData> map = new HashMap<>(all(server));
		if (data == null) {
			map.remove(Integer.toString(id));
		} else {
			map.put(Integer.toString(id), data);
		}
		Factions.level(server).setAttached(CAMPS, Map.copyOf(map));
	}

	static void updateData(MinecraftServer server, int id, UnaryOperator<CampData> change) {
		CampData data = data(server, id);
		if (data != null) {
			putData(server, id, change.apply(data));
		}
	}

	/** What kind of camp {@code id} is ({@link #BARBARIAN} for anything unknown). */
	static String kind(MinecraftServer server, int id) {
		CampData data = data(server, id);
		return data == null ? BARBARIAN : data.kind();
	}

	static boolean illager(MinecraftServer server, int id) {
		return IllagerCamps.KIND.equals(kind(server, id));
	}

	static boolean goblin(MinecraftServer server, int id) {
		return GoblinVillages.KIND.equals(kind(server, id));
	}

	static boolean orc(MinecraftServer server, int id) {
		return OrcCamps.KIND.equals(kind(server, id));
	}

	static boolean pirate(MinecraftServer server, int id) {
		return PirateCoves.KIND.equals(kind(server, id));
	}

	static void noteRaid(MinecraftServer server, int id, long at) {
		updateData(server, id, d -> d.withLastRaid(at));
	}

	// ---- founding ------------------------------------------------------------------------------------------------

	/** A new camp at {@code center} (its fire on {@code floor}), its gate facing {@code gate}: built, crowned, manned. */
	static Faction found(ServerLevel level, BlockPos center, int floor, Direction gate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		DyeColor color = DyeColor.values()[random.nextInt(DyeColor.values().length)];
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		Faction camp = Faction.create(Factions.nextId(server), FactionType.BARBARIAN, name(server, random), color, fire,
			KingdomEvents.claim(server, fire, CLAIM_RADIUS));
		Factions.put(server, camp);
		int id = camp.id();
		CampBuildings.Layout layout = CampBuildings.build(level, camp, center, floor, gate);
		Storage.register(server, id, layout.storage(), layout.depot());
		stock(level, Factions.get(server, id));
		putData(server, id, new CampData(fire, gate, level.getGameTime(), level.getGameTime(), Production.day(server), -1L, List.of(), 0L, Map.of(),
			BARBARIAN, 0, 0));
		takeSides(server, id, BARBARIAN);
		Faction crowned = KingdomEvents.crown(level, Factions.get(server, id));
		if (crowned.leader().isPresent() && level.getEntity(crowned.leader().get()) instanceof Folk chief) {
			BlockPos spot = layout.chiefSpot();
			chief.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, gate.toYRot(), 0.0F);
			chief.setHomeTo(spot, CHIEF_HOME_RADIUS);
		}
		for (int i = 0; i < START_BARBARIANS; i++) {
			KingdomEvents.spawn(level, Factions.get(server, id), Folk.BARBARIAN);
		}
		// (0.7.329) and a pair of scalebane sellswords
		ScalebaneArcher.hire(level, id, fire, ScalebaneArcher.START);
		Builders.spawnCrew(level, Factions.get(server, id));
		JaymosKingdoms.LOGGER.info("A barbarian camp, {}, rose at {}", camp.name(), fire);
		return Factions.get(server, id);
	}

	/** What a new camp's storage starts with (its land's logs, hides, meat, a few emeralds). */
	static void stock(ServerLevel level, @Nullable Faction camp) {
		if (camp == null || camp.holdings().storage().isEmpty()) {
			return;
		}
		Palette p = CampBuildings.palette(level, camp);
		Storage.put(level, camp, p.log().getBlock().asItem(), START_LOGS);
		Storage.put(level, camp, hide(camp), START_HIDES);
		Storage.put(level, camp, Items.COOKED_MUTTON, START_MEAT);
		Storage.put(level, camp, Items.EMERALD, START_EMERALDS);
	}

	/** Camps of a kind stand together (barbarians help barbarians, illagers illagers), and the two kinds are at war. */
	static void takeSides(MinecraftServer server, int id, String kind) {
		for (Faction other : Factions.all(server, FactionType.BARBARIAN)) {
			if (other.id() != id && !other.fallen()) {
				Relations.set(server, id, other.id(), kind(server, other.id()).equals(kind) ? Relation.ALLIED : Relation.WAR);
			}
		}
	}

	static String name(MinecraftServer server, RandomSource random) {
		String name = "";
		for (int tries = 0; tries < 20; tries++) {
			name = NAME_FIRST[random.nextInt(NAME_FIRST.length)] + NAME_SECOND[random.nextInt(NAME_SECOND.length)];
			if (Factions.byName(server, name) == null) {
				return name;
			}
		}
		return name + (Factions.nextId(server));
	}

	/**
	 * Every so often a kingdom with room for a camp may get one, out in the wild near a player passing through: a
	 * barbarian camp anywhere, an illager camp in pillager country ({@link IllagerCamps#land}).
	 */
	private static void spawnCamps(MinecraftServer server, ServerLevel level) {
		RandomSource random = level.getRandom();
		for (Faction realm : Factions.all(server)) {
			if (!realm.type().isRealm() || realm.fallen()) {
				continue;
			}
			List<ServerPlayer> players = Raids.players(level, realm.seat(), MAX_DISTANCE + SITE_SPREAD + HIDE_RANGE);
			if (players.isEmpty()) {
				continue;
			}
			// (with Grim Kingdoms, a barbarian camp waits for a Grim ruin in reach rather than pitching tents in the wild)
			if (campsNear(server, realm, BARBARIAN) < CAMPS_PER_KINGDOM && random.nextFloat() < CAMP_CHANCE && !GrimSites.prefersGrim(server, realm)
				&& trySite(server, level, realm, players, BARBARIAN)) {
				return;
			}
			if (level.getDifficulty() != Difficulty.PEACEFUL && campsNear(server, realm, IllagerCamps.KIND) < IllagerCamps.CAMPS_PER_KINGDOM
				&& random.nextFloat() < IllagerCamps.CAMP_CHANCE && trySite(server, level, realm, players, IllagerCamps.KIND)) {
				return;
			}
			if (level.getDifficulty() != Difficulty.PEACEFUL && campsNear(server, realm, GoblinVillages.KIND) < GoblinVillages.VILLAGES_PER_KINGDOM
				&& random.nextFloat() < GoblinVillages.VILLAGE_CHANCE && trySite(server, level, realm, players, GoblinVillages.KIND)) {
				return;
			}
			if (level.getDifficulty() != Difficulty.PEACEFUL && campsNear(server, realm, OrcCamps.KIND) < OrcCamps.CAMPS_PER_KINGDOM
				&& random.nextFloat() < OrcCamps.CAMP_CHANCE && trySite(server, level, realm, players, OrcCamps.KIND)) {
				return;
			}
			// (0.7.42) a pirate cove, on the coast near a coastal kingdom
			if (level.getDifficulty() != Difficulty.PEACEFUL && campsNear(server, realm, PirateCoves.KIND) < PirateCoves.COVES_PER_KINGDOM
				&& random.nextFloat() < PirateCoves.COVE_CHANCE && PirateCoves.coastal(level, realm)
				&& trySite(server, level, realm, players, PirateCoves.KIND)) {
				return;
			}
		}
	}

	/** Standing camps of {@code kind} within {@link #CAMP_AREA} of {@code realm}'s seat. */
	private static long campsNear(MinecraftServer server, Faction realm, String kind) {
		return Factions.all(server, FactionType.BARBARIAN).stream()
			.filter(c -> !c.fallen() && kind(server, c.id()).equals(kind) && Raids.horizontal(c.seat(), realm.seat()) <= CAMP_AREA * CAMP_AREA).count();
	}

	/** Looks for a site for a camp round the players near {@code realm}, and founds one there. */
	private static boolean trySite(MinecraftServer server, ServerLevel level, Faction realm, List<ServerPlayer> players, String kind) {
		boolean illager = IllagerCamps.KIND.equals(kind), goblin = GoblinVillages.KIND.equals(kind), orc = OrcCamps.KIND.equals(kind);
		boolean pirate = PirateCoves.KIND.equals(kind);
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < SITE_TRIES; tries++) {
			ServerPlayer player = players.get(random.nextInt(players.size()));
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = HIDE_RANGE + random.nextInt(SITE_SPREAD);
			BlockPos at = player.blockPosition().offset((int)Math.round(Math.cos(angle) * r), 0, (int)Math.round(Math.sin(angle) * r));
			double d = Math.sqrt(Raids.horizontal(at, realm.seat()));
			if (d < MIN_DISTANCE || d > MAX_DISTANCE || !open(server, level, at) || (illager && !IllagerCamps.land(level, at))
				|| (goblin && !GoblinVillages.land(level, at)) || (orc && !OrcCamps.land(level, at)) || (pirate && !PirateCoves.land(level, at))) {
				continue;
			}
			int floor = pirate ? IllagerBuildings.floor(level, at, PirateCoves.MAX_SPREAD, PirateCoves.RADIUS) : orc ? IllagerBuildings.floor(level, at, OrcCamps.MAX_SPREAD, OrcBuildings.CLEAR_RADIUS)
				: goblin ? IllagerBuildings.floor(level, at, GoblinVillages.MAX_SPREAD, GoblinBuildings.CLEAR_RADIUS)
				: illager ? IllagerBuildings.floor(level, at, IllagerCamps.MAX_SPREAD) : CampBuildings.floor(level, at, MAX_SPREAD);
			if (floor == Integer.MIN_VALUE) {
				continue;
			}
			Direction gate = Direction.getApproximateNearest(realm.seat().getX() - at.getX(), 0, realm.seat().getZ() - at.getZ());
			if (pirate) {
				if (PirateCoves.found(level, at, floor) == null) {
					continue;
				}
			} else if (orc) {
				OrcCamps.found(level, at, floor, gate);
			} else if (goblin) {
				GoblinVillages.found(level, at, floor, gate);
			} else if (illager) {
				IllagerCamps.found(level, at, floor, gate);
			} else {
				found(level, at, floor, gate);
			}
			return true;
		}
		return false;
	}

	/** {@code at} is free for a camp: loaded, nobody's land, far from every settlement and camp, out of every player's sight, no bell near. */
	private static boolean open(MinecraftServer server, ServerLevel level, BlockPos at) {
		if (!level.isPositionEntityTicking(at)) {
			return false;
		}
		int reach = Math.max(IllagerBuildings.CLEAR_RADIUS, Math.max(GoblinBuildings.CLEAR_RADIUS, OrcBuildings.CLEAR_RADIUS));
		for (BlockPos corner : new BlockPos[] {at, at.offset(-reach, 0, -reach), at.offset(reach, 0, -reach), at.offset(-reach, 0, reach),
			at.offset(reach, 0, reach)}) {
			if (Factions.at(server, level, corner) != null) {
				return false;
			}
		}
		for (Faction f : Factions.all(server)) {
			double d = Raids.horizontal(f.seat(), at);
			if (f.type() == FactionType.BARBARIAN ? d < CAMP_SPACING * CAMP_SPACING : !f.fallen() && d < MIN_DISTANCE * MIN_DISTANCE) {
				return false;
			}
		}
		if (Raids.playerNear(level, at, HIDE_RANGE)) {
			return false;
		}
		return level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), at, BELL_CLEARANCE, PoiManager.Occupancy.ANY).isEmpty();
	}

	// ---- growing -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		Raids.tick(server);
		long now = level.getGameTime();
		if (now % SPAWN_CHECK_TICKS == 0) {
			spawnCamps(server, level);
		}
		if (now % CHECK_TICKS != 0) {
			return;
		}
		long today = Production.day(server);
		for (var entry : all(server).entrySet()) {
			int id = Integer.parseInt(entry.getKey());
			CampData data = entry.getValue();
			Faction camp = Factions.get(server, id);
			if (camp == null) {
				putData(server, id, null);
				continue;
			}
			if (data.clearedAt() >= 0) {
				rot(level, id, data, now);
				continue;
			}
			if (camp.fallen() || !level.isPositionEntityTicking(camp.seat())) {
				continue;
			}
			if (!data.pending().isEmpty() && Storage.loaded(level, camp)) {
				flush(level, camp, data);
			}
			if (data.lastDay() < 0 || data.lastDay() > today) {
				updateData(server, id, d -> d.withLastDay(today));
			} else if (data.lastDay() < today) {
				grow(level, camp, (int)Math.min(MAX_CATCH_UP, today - data.lastDay()));
				updateData(server, id, d -> d.withLastDay(today));
			}
			if (data.illager()) {
				Faction grown = Factions.get(server, id);
				CampData fresh = data(server, id);
				if (grown != null && fresh != null) {
					IllagerCamps.tick(level, grown, fresh, now, false);
				}
			} else if (data.goblin()) {
				Faction grown = Factions.get(server, id);
				CampData fresh = data(server, id);
				if (grown != null && fresh != null) {
					GoblinVillages.tick(level, grown, fresh, false);
				}
			} else if (data.orc()) {
				Faction grown = Factions.get(server, id);
				CampData fresh = data(server, id);
				if (grown != null && fresh != null) {
					OrcCamps.tick(level, grown, fresh, false);
				}
			} else if (data.pirate()) {
				Faction grown = Factions.get(server, id);
				CampData fresh = data(server, id);
				if (grown != null && fresh != null) {
					PirateCoves.tick(level, grown, fresh, false);
				}
			}
		}
	}

	/** {@code days}' growth: what the camp takes in by its own hands, and (as it ages) another raider. */
	static void grow(ServerLevel level, Faction camp, int days) {
		MinecraftServer server = level.getServer();
		if (illager(server, camp.id())) {
			IllagerCamps.grow(level, camp, days);
			return;
		}
		if (goblin(server, camp.id())) {
			GoblinVillages.grow(level, camp, days);
			CampData goblins = data(server, camp.id());
			if (goblins != null) {
				ScalebaneArcher.grow(level, camp.id(), goblins.center(), days);
			}
			return;
		}
		if (orc(server, camp.id())) {
			OrcCamps.grow(level, camp, days);
			return;
		}
		if (pirate(server, camp.id())) {
			PirateCoves.grow(level, camp, days);
			return;
		}
		Palette p = CampBuildings.palette(level, camp);
		Map<Item, Integer> takings = new LinkedHashMap<>();
		takings.put(hide(camp), DAY_HIDES);
		takings.put(item(camp.id() % 2 == 0 ? "white_wool" : "brown_wool"), DAY_DARK_HIDES);
		takings.put(item(camp.color().getName() + "_wool"), DAY_PAINTED);
		takings.put(p.fence().getBlock().asItem(), DAY_FENCES);
		takings.put(p.planks().getBlock().asItem(), DAY_PLANKS);
		takings.put(Items.LADDER, DAY_LADDERS);
		takings.put(CampBuildings.gate(p).getBlock().asItem(), DAY_GATES);
		takings.put(Items.HAY_BLOCK, DAY_HAY);
		takings.put(Items.BONE_BLOCK, DAY_BONE);
		takings.put(Items.COOKED_MUTTON, DAY_MEAT);
		takings.put(Items.LEATHER, DAY_LEATHER);
		if (Storage.loaded(level, camp)) {
			for (var e : takings.entrySet()) {
				Storage.put(level, camp, e.getKey(), e.getValue() * days);
			}
		}
		CampData data = data(server, camp.id());
		long age = data == null ? 0 : (level.getGameTime() - data.founded()) / DAY_TICKS;
		if (data != null) {
			// (0.7.329) sellswords
			ScalebaneArcher.grow(level, camp.id(), data.center(), days);
		}
		int cap = (int)Math.min(MAX_BARBARIANS, START_BARBARIANS + age / DAYS_PER_BARBARIAN);
		for (int i = 0; i < days; i++) {
			Faction now = Factions.get(server, camp.id());
			if (now == null || now.holdings().count(Folk.BARBARIAN) + now.holdings().vacancies().getOrDefault(Folk.roleName(Folk.BARBARIAN), 0) >= cap) {
				break;
			}
			KingdomEvents.spawn(level, now, Folk.BARBARIAN);
		}
	}

	/** The hide its tents are made of, as an item. */
	private static Item hide(Faction camp) {
		return item(CampBuildings.hideColour(camp) + "_wool");
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}

	/** Loot brought home: into the loot pile if it's loaded (and has room), else kept for it. */
	static void deposit(ServerLevel level, Faction camp, ItemStack load) {
		ItemStack left = Storage.loaded(level, camp) ? Storage.put(level, camp, load) : load;
		if (!left.isEmpty()) {
			MinecraftServer server = level.getServer();
			String key = BuiltInRegistries.ITEM.getKey(left.getItem()).toString();
			int count = left.getCount();
			updateData(server, camp.id(), d -> {
				Map<String, Integer> pending = new HashMap<>(d.pending());
				pending.merge(key, count, Integer::sum);
				return d.withPending(pending);
			});
		}
	}

	private static void flush(ServerLevel level, Faction camp, CampData data) {
		Map<String, Integer> left = new HashMap<>();
		for (var e : data.pending().entrySet()) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(e.getKey()));
			int lost = Storage.put(level, camp, item, e.getValue());
			if (lost > 0) {
				left.put(e.getKey(), lost);
			}
		}
		updateData(level.getServer(), camp.id(), d -> d.withPending(left));
	}

	// ---- clearing ------------------------------------------------------------------------------------------------

	/**
	 * A chief is dead: his camp is cleared. Whoever killed him is thanked by every settlement (and so are those who fought
	 * beside them); his people scatter and his raiders flee; the camp falls (its land, roster and storage gone: the loot
	 * is anyone's), and its tents start to rot.
	 */
	static void chiefSlain(ServerLevel level, LivingEntity chief, int campId, DamageSource source) {
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, campId);
		if (camp == null || camp.fallen() || camp.type() != FactionType.BARBARIAN) {
			return;
		}
		boolean illagers = illager(server, camp.id()), goblins = goblin(server, camp.id()), orcs = orc(server, camp.id());
		boolean pirates = pirate(server, camp.id());
		String cleared = pirates ? "pirate_cove" : orcs ? "orc_camp" : goblins ? "goblin_village" : illagers ? "illager_camp" : "camp";
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms." + cleared + ".cleared",
			camp.name(),
			killer == null ? Component.translatable("message.jaymoskingdoms.someone") : killer.getDisplayName()).withStyle(ChatFormatting.GOLD));
		if (killer != null) {
			List<ServerPlayer> heroes = new ArrayList<>(Raids.players(level, chief.blockPosition(), HELPER_RANGE));
			heroes.remove(killer);
			for (Faction f : Factions.all(server)) {
				if (f.fallen() || f.type() == FactionType.BARBARIAN) {
					continue;
				}
				Reputation.change(killer, f, REP_CLEAR);
				for (ServerPlayer helper : heroes) {
					Reputation.change(helper, f, REP_CLEAR_HELPER);
				}
			}
			killer.sendSystemMessage(Component.translatable("message.jaymoskingdoms." + cleared + ".thanks")
				.withStyle(ChatFormatting.GREEN));
			level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
		Raids.scatter(level, camp.id());
		for (Folk folk : level.getEntitiesOfClass(Folk.class, new AABB(camp.seat()).inflate(SCATTER_RANGE),
			f -> f.factionId() == camp.id() && f != chief && f.isAlive() && !Corsairs.isCrew(f))) {
			if (folk.isCarrying()) {
				folk.spawnAtLocation(level, folk.takeCarried());
			}
			level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			folk.discard();
		}
		McaCompat.scatterRaiders(level, camp.id(), new AABB(camp.seat()).inflate(SCATTER_RANGE));
		Illagers.scatter(level, camp.id(), camp.seat(), SCATTER_RANGE, chief);
		Factions.update(server, camp.id(), f -> f.withFallen(true).withLeader(Optional.empty())
			.withHoldings(f.holdings().withRoster(Map.of()).withVacancies(Map.of()).withStorage(List.of(), Optional.empty())));
		for (Faction other : Factions.all(server, FactionType.BARBARIAN)) {
			if (other.id() != camp.id()) {
				Relations.set(server, camp.id(), other.id(), Relation.NEUTRAL);
			}
		}
		CampData data = data(server, camp.id());
		BlockPos center = data != null ? data.center() : camp.seat();
		List<Long> rot = new ArrayList<>();
		int reach = pirates ? PirateCoves.RADIUS + 1 : orcs ? OrcBuildings.CLEAR_RADIUS : goblins ? GoblinBuildings.CLEAR_RADIUS : illagers ? IllagerBuildings.CLEAR_RADIUS : CampBuildings.CLEAR_RADIUS;
		int high = pirates ? PirateCoves.CLEAR_HEIGHT : orcs ? OrcBuildings.CLEAR_HEIGHT : goblins ? GoblinBuildings.CLEAR_HEIGHT : illagers ? IllagerBuildings.CLEAR_HEIGHT : 8;
		// (a camp in a Grim ruin leaves the ruin as it stands: nothing of it rots)
		Holds.Hold held = Holds.get(server, camp.id());
		boolean grim = held != null && held.isGrim();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -1, -reach), center.offset(reach, high, reach))) {
			if (!grim && level.isLoaded(pos) && (pirates ? PirateCoves.perishable(level.getBlockState(pos)) : perishable(goblins, orcs, level.getBlockState(pos)))) {
				rot.add(pos.asLong());
			}
		}
		long now = level.getGameTime();
		if (data == null) {
			putData(server, camp.id(), new CampData(center, Direction.NORTH, now, now, -1L, now, List.copyOf(rot), now, Map.of(), BARBARIAN, 0, 0));
		} else {
			updateData(server, camp.id(), d -> d.withCleared(now, rot));
		}
		JaymosKingdoms.LOGGER.info("The camp of {} is cleared ({} blocks to rot)", camp.name(), rot.size());
	}

	/** A cleared camp rots a little more (while it's loaded; time away counts). The fire goes out first; the palisade stays. */
	private static void rot(ServerLevel level, int id, CampData data, long now) {
		if (data.rot().isEmpty() || now - data.lastRot() < ROT_STEP_TICKS || !level.isPositionEntityTicking(data.center())) {
			return;
		}
		RandomSource random = level.getRandom();
		List<Long> left = new ArrayList<>(data.rot());
		long steps = Math.max(1, (now - data.lastRot()) / ROT_STEP_TICKS);
		int count = (int)Math.min(left.size(), steps * ROT_PER_STEP);
		for (int i = 0; i < count && !left.isEmpty(); i++) {
			int pick = random.nextInt(left.size());
			BlockPos pos = BlockPos.of(left.get(pick));
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
				level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
				continue;
			}
			left.remove(pick);
			if (data.pirate() ? PirateCoves.perishable(state) : perishable(data.goblin(), data.orc(), state)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		putData(level.getServer(), id, data.withRot(left, now));
	}

	/** What rots away once a camp of its kind is cleared (a goblin village loses its mushroom roofs too, an orc camp its bones). */
	private static boolean perishable(boolean goblin, boolean orc, BlockState state) {
		return goblin ? GoblinBuildings.perishable(state) : orc ? OrcBuildings.perishable(state) : CampBuildings.perishable(state);
	}

	// ---- commands (operators; debug: plain text) -----------------------------------------------------------------

	/** {@code /faction camp found [barbarian | illager | goblin | orc] | raid | grow | info}. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("camp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("found").executes(c -> foundHere(c, BARBARIAN))
				.then(Commands.literal("barbarian").executes(c -> foundHere(c, BARBARIAN)))
				.then(Commands.literal("illager").executes(c -> foundHere(c, IllagerCamps.KIND)))
				.then(Commands.literal("goblin").executes(c -> foundHere(c, GoblinVillages.KIND)))
				.then(Commands.literal("orc").executes(c -> foundHere(c, OrcCamps.KIND)))
				.then(Commands.literal("pirate").executes(c -> foundHere(c, PirateCoves.KIND))))
			.then(Commands.literal("raid").executes(Camps::raidNow))
			.then(Commands.literal("grow").executes(Camps::growNow))
			.then(Commands.literal("info").executes(Camps::info));
	}

	private static int foundHere(CommandContext<CommandSourceStack> c, String kind) throws CommandSyntaxException {
		boolean illager = IllagerCamps.KIND.equals(kind);
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = Factions.level(c.getSource().getServer());
		if (player.level() != level) {
			c.getSource().sendFailure(Component.literal("Camps are only in the Overworld"));
			return 0;
		}
		BlockPos at = player.blockPosition();
		if (PirateCoves.KIND.equals(kind)) {
			Faction cove = PirateCoves.found(level, at, at.getY());
			if (cove == null) {
				c.getSource().sendFailure(Component.literal("No open sea within " + PirateCoves.SEA_REACH + " blocks for a pirate cove's ship"));
				return 0;
			}
			c.getSource().sendSuccess(() -> Component.literal("Founded the pirate cove of " + cove.name()), true);
			return 1;
		}
		Faction camp = OrcCamps.KIND.equals(kind) ? OrcCamps.found(level, at, at.getY(), player.getDirection())
			: GoblinVillages.KIND.equals(kind) ? GoblinVillages.found(level, at, at.getY(), player.getDirection())
			: illager ? IllagerCamps.found(level, at, at.getY(), player.getDirection()) : found(level, at, at.getY(), player.getDirection());
		c.getSource().sendSuccess(() -> Component.literal("Founded the " + kind + " camp of " + camp.name() + " (gate "
			+ player.getDirection().getName() + ")" + (illager && level.getDifficulty() == Difficulty.PEACEFUL ? "; on peaceful its illagers vanish" : "")), true);
		return 1;
	}

	private static @Nullable Faction nearestCamp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction best = null;
		double bestDist = Double.MAX_VALUE;
		for (Faction f : Factions.all(c.getSource().getServer(), FactionType.BARBARIAN)) {
			double d = Raids.horizontal(f.seat(), player.blockPosition());
			if (!f.fallen() && d < bestDist) {
				best = f;
				bestDist = d;
			}
		}
		if (best == null) {
			c.getSource().sendFailure(Component.literal("No standing camp"));
		}
		return best;
	}

	private static int raidNow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Faction camp = nearestCamp(c);
		if (camp == null) {
			return 0;
		}
		MinecraftServer server = c.getSource().getServer();
		if (goblin(server, camp.id())) {
			c.getSource().sendFailure(Component.literal(camp.name() + " is a goblin village: goblins don't raid"));
			return 0;
		}
		Faction target = Raids.nearestTarget(server, camp);
		if (target == null) {
			c.getSource().sendFailure(Component.literal("No settlement for " + camp.name() + " to raid"));
			return 0;
		}
		boolean started = Raids.start(Factions.level(server), camp, target);
		c.getSource().sendSuccess(() -> Component.literal(started ? camp.name() + " raids " + target.name()
			: camp.name() + " can't raid " + target.name() + " (already raiding, or no loaded ground near its seat)"), true);
		return started ? 1 : 0;
	}

	private static int growNow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Faction camp = nearestCamp(c);
		if (camp == null) {
			return 0;
		}
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		grow(level, camp, 1);
		boolean illagers = illager(server, camp.id());
		if (illagers) {
			// (and its next building, if it has the makings and its age allows)
			CampData data = data(server, camp.id());
			if (data != null) {
				IllagerCamps.tick(level, Factions.get(server, camp.id()), data, level.getGameTime(), true);
			}
		}
		if (goblin(server, camp.id())) {
			CampData data = data(server, camp.id());
			if (data != null) {
				GoblinVillages.tick(level, Factions.get(server, camp.id()), data, true);
			}
		}
		if (orc(server, camp.id())) {
			CampData data = data(server, camp.id());
			if (data != null) {
				OrcCamps.tick(level, Factions.get(server, camp.id()), data, true);
			}
		}
		if (pirate(server, camp.id())) {
			// (and its ship put out again now, if she's lost)
			CampData data = data(server, camp.id());
			if (data != null) {
				PirateCoves.tick(level, Factions.get(server, camp.id()), data, true);
			}
		}
		Faction after = Factions.get(server, camp.id());
		int fighters = illagers ? Illagers.fighters(after) : goblin(server, camp.id()) ? GoblinVillages.goblins(after)
			: orc(server, camp.id()) ? OrcCamps.fighters(after) : pirate(server, camp.id()) ? PirateCoves.fighters(after) : after.holdings().count(Folk.BARBARIAN);
		c.getSource().sendSuccess(() -> Component.literal(camp.name() + " grew a day (" + fighters + (illagers ? " illagers)" : " raiders)")), true);
		return 1;
	}

	/** {@code /faction camp info}: the nearest standing camp, what kind it is, its roster, age, growth and raids. */
	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Faction camp = nearestCamp(c);
		if (camp == null) {
			return 0;
		}
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		CampData data = data(server, camp.id());
		long now = level.getGameTime();
		StringBuilder out = new StringBuilder();
		out.append(camp.name()).append(" (").append(data == null ? BARBARIAN : data.kind()).append(" camp, id ").append(camp.id()).append(") at ")
			.append(camp.seat().toShortString());
		if (data != null) {
			out.append(", ").append((now - data.founded()) / DAY_TICKS).append(" days old");
			if (data.illager()) {
				out.append(", ").append(data.built()).append('/').append(IllagerBuildings.GROWTH.size()).append(" buildings raised, grown ")
					.append(data.grown()).append(" days");
			} else if (data.goblin()) {
				out.append(", ").append(data.built()).append('/').append(GoblinBuildings.GROWTH.size()).append(" huts raised, grown ")
					.append(data.grown()).append(" days");
			} else if (data.orc()) {
				out.append(", ").append(data.built()).append('/').append(OrcBuildings.GROWTH.size()).append(" tents raised, grown ")
					.append(data.grown()).append(" days");
			}
			out.append(", last raid ").append((now - data.lastRaid()) / 20).append(" s ago");
		}
		out.append("\nroster: ").append(camp.holdings().roster());
		boolean leaderHere = camp.leader().isPresent() && level.getEntity(camp.leader().get()) != null;
		out.append("\nleader: ").append(camp.leader().isEmpty() ? "none" : leaderHere ? "here" : "not loaded");
		out.append(Raids.raiding(camp.id()) ? ", raiding now" : "");
		Faction target = Raids.target(server, level, camp);
		out.append("\nwould raid: ").append(target == null ? "nobody now (no settlement in reach with a player near it)" : target.name());
		c.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
		return 1;
	}
}
