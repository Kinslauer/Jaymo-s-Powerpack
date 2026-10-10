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
import net.minecraft.world.damagesource.DamageSource;
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
	 * grew (a day number), when it was cleared (-1: standing), the blocks still to rot, when it last rotted, and loot
	 * waiting for its pile to be loaded (item id → count).
	 */
	public record CampData(BlockPos center, Direction gate, long founded, long lastRaid, long lastDay, long clearedAt, List<Long> rot,
		long lastRot, Map<String, Integer> pending) {
		public static final Codec<CampData> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("center").forGetter(CampData::center),
				Direction.CODEC.optionalFieldOf("gate", Direction.NORTH).forGetter(CampData::gate),
				Codec.LONG.optionalFieldOf("founded", 0L).forGetter(CampData::founded),
				Codec.LONG.optionalFieldOf("last_raid", 0L).forGetter(CampData::lastRaid),
				Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(CampData::lastDay),
				Codec.LONG.optionalFieldOf("cleared_at", -1L).forGetter(CampData::clearedAt),
				Codec.LONG.listOf().optionalFieldOf("rot", List.of()).forGetter(CampData::rot),
				Codec.LONG.optionalFieldOf("last_rot", 0L).forGetter(CampData::lastRot),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("pending", Map.of()).forGetter(CampData::pending))
			.apply(i, CampData::new));

		CampData withLastRaid(long at) {
			return new CampData(this.center, this.gate, this.founded, at, this.lastDay, this.clearedAt, this.rot, this.lastRot, this.pending);
		}

		CampData withLastDay(long day) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, day, this.clearedAt, this.rot, this.lastRot, this.pending);
		}

		CampData withCleared(long at, List<Long> rot) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, this.lastDay, at, List.copyOf(rot), at, this.pending);
		}

		CampData withRot(List<Long> rot, long at) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, this.lastDay, this.clearedAt, List.copyOf(rot), at, this.pending);
		}

		CampData withPending(Map<String, Integer> pending) {
			return new CampData(this.center, this.gate, this.founded, this.lastRaid, this.lastDay, this.clearedAt, this.rot, this.lastRot,
				Map.copyOf(pending));
		}
	}

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
		CampBuildings.registerCatalogue();
		ServerTickEvents.END_SERVER_TICK.register(Camps::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Raids.clear());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Raids.loaded(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Folk folk && folk.level() instanceof ServerLevel level) {
				if (folk.role() == Folk.CHIEF) {
					chiefSlain(level, folk, source);
				} else if (Raids.isRaider(folk)) {
					Raids.raiderDied(level, folk, source);
				}
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
		Faction stocked = Factions.get(server, id);
		Palette p = CampBuildings.palette(level, stocked);
		Storage.put(level, stocked, p.log().getBlock().asItem(), START_LOGS);
		Storage.put(level, stocked, hide(stocked), START_HIDES);
		Storage.put(level, stocked, Items.COOKED_MUTTON, START_MEAT);
		Storage.put(level, stocked, Items.EMERALD, START_EMERALDS);
		// camps stand together
		for (Faction other : Factions.all(server, FactionType.BARBARIAN)) {
			if (other.id() != id && !other.fallen()) {
				Relations.set(server, id, other.id(), Relation.ALLIED);
			}
		}
		Faction crowned = KingdomEvents.crown(level, Factions.get(server, id));
		if (crowned.leader().isPresent() && level.getEntity(crowned.leader().get()) instanceof Folk chief) {
			BlockPos spot = layout.chiefSpot();
			chief.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, gate.toYRot(), 0.0F);
			chief.setHomeTo(spot, CHIEF_HOME_RADIUS);
		}
		for (int i = 0; i < START_BARBARIANS; i++) {
			KingdomEvents.spawn(level, Factions.get(server, id), Folk.BARBARIAN);
		}
		Builders.spawnCrew(level, Factions.get(server, id));
		long now = level.getGameTime();
		putData(server, id, new CampData(fire, gate, now, now, Production.day(server), -1L, List.of(), 0L, Map.of()));
		JaymosKingdoms.LOGGER.info("A barbarian camp, {}, rose at {}", camp.name(), fire);
		return Factions.get(server, id);
	}

	private static String name(MinecraftServer server, RandomSource random) {
		String name = "";
		for (int tries = 0; tries < 20; tries++) {
			name = NAME_FIRST[random.nextInt(NAME_FIRST.length)] + NAME_SECOND[random.nextInt(NAME_SECOND.length)];
			if (Factions.byName(server, name) == null) {
				return name;
			}
		}
		return name + (Factions.nextId(server));
	}

	/** Every so often a kingdom with room for a camp may get one, out in the wild near a player passing through. */
	private static void spawnCamps(MinecraftServer server, ServerLevel level) {
		RandomSource random = level.getRandom();
		for (Faction realm : Factions.all(server)) {
			if (!realm.type().isRealm() || realm.fallen()) {
				continue;
			}
			long near = Factions.all(server, FactionType.BARBARIAN).stream()
				.filter(c -> !c.fallen() && Raids.horizontal(c.seat(), realm.seat()) <= CAMP_AREA * CAMP_AREA).count();
			if (near >= CAMPS_PER_KINGDOM || random.nextFloat() >= CAMP_CHANCE) {
				continue;
			}
			List<ServerPlayer> players = Raids.players(level, realm.seat(), MAX_DISTANCE + SITE_SPREAD + HIDE_RANGE);
			if (players.isEmpty()) {
				continue;
			}
			for (int tries = 0; tries < SITE_TRIES; tries++) {
				ServerPlayer player = players.get(random.nextInt(players.size()));
				double angle = random.nextDouble() * Math.PI * 2.0;
				int r = HIDE_RANGE + random.nextInt(SITE_SPREAD);
				BlockPos at = player.blockPosition().offset((int)Math.round(Math.cos(angle) * r), 0, (int)Math.round(Math.sin(angle) * r));
				double d = Math.sqrt(Raids.horizontal(at, realm.seat()));
				if (d < MIN_DISTANCE || d > MAX_DISTANCE || !open(server, level, at)) {
					continue;
				}
				int floor = CampBuildings.floor(level, at, MAX_SPREAD);
				if (floor == Integer.MIN_VALUE) {
					continue;
				}
				Direction gate = Direction.getApproximateNearest(realm.seat().getX() - at.getX(), 0, realm.seat().getZ() - at.getZ());
				found(level, at, floor, gate);
				return;
			}
		}
	}

	/** {@code at} is free for a camp: loaded, nobody's land, far from every settlement and camp, out of every player's sight, no bell near. */
	private static boolean open(MinecraftServer server, ServerLevel level, BlockPos at) {
		if (!level.isPositionEntityTicking(at)) {
			return false;
		}
		int reach = CampBuildings.CLEAR_RADIUS;
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
		}
	}

	/** {@code days}' growth: what the camp takes in by its own hands, and (as it ages) another raider. */
	static void grow(ServerLevel level, Faction camp, int days) {
		MinecraftServer server = level.getServer();
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
	static void chiefSlain(ServerLevel level, Folk chief, DamageSource source) {
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, chief.factionId());
		if (camp == null || camp.fallen() || camp.type() != FactionType.BARBARIAN) {
			return;
		}
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.camp.cleared", camp.name(),
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
			killer.sendSystemMessage(Component.translatable("message.jaymoskingdoms.camp.thanks").withStyle(ChatFormatting.GREEN));
			level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
		Raids.scatter(level, camp.id());
		for (Folk folk : level.getEntitiesOfClass(Folk.class, new AABB(camp.seat()).inflate(SCATTER_RANGE),
			f -> f.factionId() == camp.id() && f != chief && f.isAlive())) {
			if (folk.isCarrying()) {
				folk.spawnAtLocation(level, folk.takeCarried());
			}
			level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			folk.discard();
		}
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
		int reach = CampBuildings.CLEAR_RADIUS;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -1, -reach), center.offset(reach, 8, reach))) {
			if (level.isLoaded(pos) && CampBuildings.perishable(level.getBlockState(pos))) {
				rot.add(pos.asLong());
			}
		}
		long now = level.getGameTime();
		if (data == null) {
			putData(server, camp.id(), new CampData(center, Direction.NORTH, now, now, -1L, now, List.copyOf(rot), now, Map.of()));
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
			if (CampBuildings.perishable(state)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		putData(level.getServer(), id, data.withRot(left, now));
	}

	// ---- commands (operators; debug: plain text) -----------------------------------------------------------------

	/** {@code /faction camp found | raid | grow}. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("camp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("found").executes(Camps::foundHere))
			.then(Commands.literal("raid").executes(Camps::raidNow))
			.then(Commands.literal("grow").executes(Camps::growNow));
	}

	private static int foundHere(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = Factions.level(c.getSource().getServer());
		if (player.level() != level) {
			c.getSource().sendFailure(Component.literal("Camps are only in the Overworld"));
			return 0;
		}
		BlockPos at = player.blockPosition();
		Faction camp = found(level, at, at.getY(), player.getDirection());
		c.getSource().sendSuccess(() -> Component.literal("Founded the camp of " + camp.name() + " (gate " + player.getDirection().getName() + ")"), true);
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
		grow(Factions.level(c.getSource().getServer()), camp, 1);
		Faction after = Factions.get(c.getSource().getServer(), camp.id());
		c.getSource().sendSuccess(() -> Component.literal(camp.name() + " grew a day (" + after.holdings().count(Folk.BARBARIAN) + " raiders)"), true);
		return 1;
	}
}
