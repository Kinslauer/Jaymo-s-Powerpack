package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The places Illager Structures built (GNU GPL v3, by the Dawn of Time team; its templates are carried in this mod under
 * {@code illager_sites/}, see {@code licenses/illager-structures.txt}), and who lives in them: Andy's picks, one people per
 * place ({@link #PLANS}). The templates come without their mobs; where each mob stood is a post ({@link SitePosts}).
 * <ul>
 * <li><b>Empty until its tier.</b> A place stands empty, and its chests hold only junk, until a player near it has opened
 * its world tier ({@link WorldTiers#open}) and nobody is close enough to watch. Then its people move in at the old posts
 * and its unopened chests get their people's loot ({@link #LOOT}; netherite only in Dread places).</li>
 * <li><b>Camps</b> ({@link Mode#CAMP}): a camp of their kind ({@link Camps}) seated in the place: they raid, keep
 * captives, sit down to supper, and are cleared by killing their leader. The place is never torn down or added to
 * ({@link Holds}).</li>
 * <li><b>Garrisons</b> ({@link Mode#GARRISON}): the same, but they hold their place and never raid. A tower whose pick is
 * "bandits or a realm's garrison" is the nearest realm's within {@link #GARRISON_REACH} (its own guards and archers, as
 * lawful as the realm's), else a bandit hold.</li>
 * <li><b>Smugglers</b> ({@link #SMUGGLER}) let a player be unless they're struck or their goods are touched
 * ({@link #provoke}), and keep a fence ({@link #FENCE_TAG}) who deals in contraband, papers and sealed bundles.</li>
 * <li><b>Bandits</b> ({@link #BANDIT}): masked outlaws, a camp of their own.</li>
 * <li><b>Monks</b> and the <b>recluse</b>: MCA villagers who live there in peace, greet visitors once a day, and (the
 * herbalist) see to their wounds.</li>
 * <li><b>Abandoned</b>: nobody; only the loot.</li>
 * </ul>
 */
final class IllagerSites {
	static final String PREFIX = "illager_sites/";
	/** {@link Camps.CampData#kind} of a bandit hold and of a smugglers' den. */
	static final String BANDIT = "bandit", SMUGGLER = "smuggler";
	/** The peaceful folk's roles (MCA villagers of no settlement). */
	static final int MONK = 110, PRIOR = 111, HERBALIST = 112, RECLUSE = 113;
	/** On a smugglers' fence; on a realm's soldier holding a tower. */
	static final String FENCE_TAG = "jaymoskingdoms.site_fence", GARRISON_TAG = "jaymoskingdoms.site_garrison";

	/** How often the places round players are looked over (ticks), and how far round (chunks). */
	static final int SCAN_TICKS = 600, SCAN_CHUNKS = 6;
	/** A player this near (blocks) with the place's tier open lets its people move in. */
	static final double TIER_RANGE = 160.0;
	/** Nobody this near the place's edge when its people move in (they'd see them appear). */
	static final double HIDE_RANGE = 32.0;
	/** A realm's seat this near (blocks) holds a "bandits or garrison" tower. */
	static final double GARRISON_REACH = 600.0;
	/** Land a camp claims round its seat (chunks out each way). */
	static final int CLAIM = 2;
	/** How far each of its people keeps from their post. */
	static final int ROAM = 8, LEADER_ROAM = 4, PEACE_ROAM = 10;
	/** The most people a place takes on (its posts beyond this stay empty), and at least this many in a hold. */
	static final int MOST_CREW = 40, LEAST_CREW = 2;
	/** Places that take on more than {@link #MOST_CREW} (Andy: the citadel "back to 60"). */
	static final Map<String, Integer> MOST_CREW_AT = Map.of("ocean_citadel", 60);
	/** Captives where a pick says so but the template penned nobody: this many at most. */
	static final int CAPTIVES_MOST = 3;
	/** A leader missing (the place loaded, him nowhere) this long (ticks) is replaced. */
	static final long RESEAT_TICKS = 20 * 120;
	/** Its people come back one a day, at most this many days' worth at once. */
	static final int REFILL_MOST = 3;
	/** A smuggler's grudge against a player who struck one of them or touched their goods (ticks). */
	static final long PROVOKED_TICKS = 24000L;
	/** Peaceful folk greet each player once in this long (ticks); this near, in sight; their words carry this far. */
	static final long GREET_TICKS = 24000L;
	static final double GREET_RANGE = 9.0, SPEECH_RANGE = 14.0;
	static final int GREETINGS = 3;
	/** The herbalist mends a visitor below this share of their health: regeneration this long (ticks). */
	static final float MEND_BELOW = 0.75F;
	static final int MEND_TICKS = 200;
	/** The recluse's pasts (lang {@code talk.jaymoskingdoms.illager_sites.recluse.<past>.<n>}). */
	static final String[] PASTS = {"soldier", "widow", "exile", "deserter"};
	/** A soft green for the peaceful folk's words. */
	static final int COLOR = 0xA8D8A0;
	/** The fence's prices (emeralds, set in the local coin when the trade opens as any fence's are) and stock. */
	static final int FENCE_SHASH = 14, FENCE_SHASH_BUY = 8, FENCE_PMEH = 10, FENCE_STOCK = 4, FENCE_BUNDLE = 4, FENCE_DENS = 2;

	/** How a place's people live there. */
	enum Mode {
		/** A camp: raids, captives, a leader to kill. */
		CAMP,
		/** A hold that is defended but never raids. */
		GARRISON,
		/** People living there in peace. */
		PEACE,
		/** Nobody: only the loot. */
		EMPTY
	}

	/**
	 * Who lives in a place ({@code a|b}: one or the other, a realm's garrison if one is in reach, else rolled once per
	 * place), from which tier, how, whether they hold captives, and at least how many of them there are.
	 */
	record Plan(String dwellers, WorldTiers.Tier tier, Mode mode, boolean captives, int least) {
	}

	/** Andy's picks (2026-11-13), and Claude's suggestions for those he left (he chose to use them). The Mohai Illager is left out. */
	static final Map<String, Plan> PLANS = Map.ofEntries(
		Map.entry("illager_fort", new Plan("illagers", WorldTiers.Tier.BORDERLANDS, Mode.CAMP, true, 8)),
		Map.entry("illager_tower", new Plan("garrison|bandits", WorldTiers.Tier.BORDERLANDS, Mode.GARRISON, false, 3)),
		Map.entry("illager_tower2", new Plan("garrison|bandits", WorldTiers.Tier.BORDERLANDS, Mode.GARRISON, false, 3)),
		Map.entry("illager_tower3", new Plan("illagers", WorldTiers.Tier.BORDERLANDS, Mode.GARRISON, false, 3)),
		Map.entry("illager_camp", new Plan("bandits", WorldTiers.Tier.BORDERLANDS, Mode.CAMP, false, 5)),
		Map.entry("illager_stable", new Plan("smugglers", WorldTiers.Tier.HEARTH, Mode.GARRISON, false, 5)),
		Map.entry("illager_tipi_camp", new Plan("barbarians", WorldTiers.Tier.BORDERLANDS, Mode.CAMP, true, 6)),
		Map.entry("tipi", new Plan("barbarians", WorldTiers.Tier.HEARTH, Mode.GARRISON, false, 2)),
		Map.entry("balloon_tower", new Plan("illagers", WorldTiers.Tier.WILDLANDS, Mode.GARRISON, false, 4)),
		Map.entry("illager_balloon", new Plan("pirates", WorldTiers.Tier.BORDERLANDS, Mode.GARRISON, false, 2)),
		Map.entry("lava_extractor", new Plan("orcs", WorldTiers.Tier.WILDLANDS, Mode.GARRISON, false, 5)),
		Map.entry("smeltery", new Plan("orcs", WorldTiers.Tier.WILDLANDS, Mode.CAMP, true, 8)),
		Map.entry("illager_mole_surface", new Plan("goblins", WorldTiers.Tier.BORDERLANDS, Mode.GARRISON, false, 5)),
		Map.entry("illager_mole_maintenance", new Plan("goblins", WorldTiers.Tier.HEARTH, Mode.GARRISON, false, 5)),
		Map.entry("illager_mole_underground", new Plan("goblins", WorldTiers.Tier.BORDERLANDS, Mode.GARRISON, false, 5)),
		Map.entry("lone_house", new Plan("recluse", WorldTiers.Tier.HEARTH, Mode.PEACE, false, 1)),
		Map.entry("simple_well", new Plan("abandoned", WorldTiers.Tier.HEARTH, Mode.EMPTY, false, 0)),
		Map.entry("simple_cart", new Plan("bandits|abandoned", WorldTiers.Tier.HEARTH, Mode.GARRISON, false, 2)),
		Map.entry("totem", new Plan("abandoned", WorldTiers.Tier.HEARTH, Mode.EMPTY, false, 0)),
		Map.entry("well", new Plan("abandoned", WorldTiers.Tier.HEARTH, Mode.EMPTY, false, 0)),
		Map.entry("monastery", new Plan("monks", WorldTiers.Tier.BORDERLANDS, Mode.PEACE, false, 4)),
		Map.entry("monk_tower", new Plan("monks", WorldTiers.Tier.BORDERLANDS, Mode.PEACE, false, 2)),
		Map.entry("herbalist_house", new Plan("monks", WorldTiers.Tier.HEARTH, Mode.PEACE, false, 1)),
		Map.entry("illager_galleon", new Plan("pirates", WorldTiers.Tier.WILDLANDS, Mode.CAMP, false, 8)),
		Map.entry("ocean_citadel", new Plan("pirates", WorldTiers.Tier.DREAD, Mode.CAMP, true, 12)),
		Map.entry("illager_floating_outpost", new Plan("smugglers", WorldTiers.Tier.WILDLANDS, Mode.GARRISON, true, 5)),
		Map.entry("submarine", new Plan("smugglers", WorldTiers.Tier.DREAD, Mode.GARRISON, false, 4)),
		Map.entry("illager_small_sub", new Plan("smugglers", WorldTiers.Tier.BORDERLANDS, Mode.GARRISON, false, 2)),
		Map.entry("iceberg", new Plan("abandoned", WorldTiers.Tier.HEARTH, Mode.EMPTY, false, 0)),
		Map.entry("iceberg_medium", new Plan("abandoned", WorldTiers.Tier.HEARTH, Mode.EMPTY, false, 0)),
		Map.entry("iceberg_outpost", new Plan("pirates", WorldTiers.Tier.WILDLANDS, Mode.CAMP, true, 6)),
		Map.entry("iceberg_shipwreck", new Plan("abandoned|pirates", WorldTiers.Tier.WILDLANDS, Mode.GARRISON, false, 4)),
		Map.entry("iceberg_shipyard", new Plan("smugglers", WorldTiers.Tier.DREAD, Mode.GARRISON, false, 6)),
		Map.entry("iceberg_site", new Plan("illagers", WorldTiers.Tier.DREAD, Mode.CAMP, false, 8)),
		Map.entry("tower_ruin", new Plan("abandoned", WorldTiers.Tier.BORDERLANDS, Mode.EMPTY, false, 0)),
		Map.entry("ruined_church", new Plan("abandoned", WorldTiers.Tier.BORDERLANDS, Mode.EMPTY, false, 0)));

	/** The stable's crew: smugglers, with masked bandits hired as their muscle (Andy's "smugglers + bandits"). */
	static final Set<String> BANDIT_MUSCLE = Set.of("illager_stable");

	/** The junk tables a place's chests hold until it's held. */
	static final String JUNK = "jaymoskingdoms:chests/illager_sites/unheld_";

	/**
	 * A place whose people have moved in: its structure's path, its leader's post (its seat), its bounds, who lives
	 * there, their faction (-1: none of their own), the realm holding it (-1: none), how many hands it keeps, its posts
	 * (packed) and what stood on each ({@link SitePosts.Post#kind}), and when it was settled.
	 */
	record Site(String path, BlockPos seat, BoundingBox box, String dweller, int faction, int realm, int crew, List<Long> posts, String kinds,
		long settled, long lastDay) {
		static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("path").forGetter(Site::path),
				BlockPos.CODEC.fieldOf("seat").forGetter(Site::seat),
				BoundingBox.CODEC.fieldOf("box").forGetter(Site::box),
				Codec.STRING.fieldOf("dweller").forGetter(Site::dweller),
				Codec.INT.optionalFieldOf("faction", -1).forGetter(Site::faction),
				Codec.INT.optionalFieldOf("realm", -1).forGetter(Site::realm),
				Codec.INT.optionalFieldOf("crew", 0).forGetter(Site::crew),
				Codec.LONG.listOf().optionalFieldOf("posts", List.of()).forGetter(Site::posts),
				Codec.STRING.optionalFieldOf("kinds", "").forGetter(Site::kinds),
				Codec.LONG.optionalFieldOf("settled", 0L).forGetter(Site::settled),
				Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Site::lastDay))
			.apply(i, Site::new));

		Site withLastDay(long day) {
			return new Site(this.path, this.seat, this.box, this.dweller, this.faction, this.realm, this.crew, this.posts, this.kinds, this.settled, day);
		}
	}

	/** On the Overworld: every settled place (its start chunk, packed → it). Saved. */
	static final AttachmentType<Map<String, Site>> SITES = AttachmentRegistry.create(JaymosKingdoms.id("illager_sites"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Site.CODEC)));

	/** Camp id → its place (built from {@link #SITES} on first use). */
	private static @Nullable Map<Integer, Site> byCamp;
	/** Camp id → player → game time their provocation runs out. */
	private static final Map<Integer, Map<UUID, Long>> PROVOKED = new HashMap<>();
	/** Camp id → ticks its leader has been missing. */
	private static final Map<Integer, Long> MISSING = new HashMap<>();
	/** "who/player" → when they were last greeted. */
	private static final Map<String, Long> GREETED = new HashMap<>();
	/** A fence → the day his stock was last laid out. */
	private static final Map<UUID, Long> STOCKED = new HashMap<>();

	private IllagerSites() {
	}

	static void register() {
		for (int role : new int[] {MONK, PRIOR, HERBALIST, RECLUSE}) {
			Folk.defineRole(new Folk.Role(role, roleName(role), false, false, null, (folk, faction) -> {
			}));
		}
		ServerTickEvents.END_SERVER_TICK.register(IllagerSites::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			byCamp = null;
			PROVOKED.clear();
			MISSING.clear();
			GREETED.clear();
			STOCKED.clear();
		});
		// a smuggler struck: the striker is a foe of the whole crew for a day
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer player && entity instanceof Worker w && entity.level().getServer() != null
				&& wary(entity.level().getServer(), w.factionId())) {
				provoke(w.factionId(), player);
			}
		});
		// their goods touched: the same, and a word of warning from the nearest of them
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && world instanceof ServerLevel level && player instanceof ServerPlayer sp && !sp.isSpectator()) {
				touched(level, sp, hit);
			}
			return InteractionResult.PASS;
		});
		// a smugglers' fence lays out his stock before the trading screen opens
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && world instanceof ServerLevel level && entity instanceof Villager v && v.entityTags().contains(FENCE_TAG)
				&& v.isAlive() && v.getTradingPlayer() == null) {
				stock(level, v);
			}
			return InteractionResult.PASS;
		});
	}

	static String roleName(int role) {
		return switch (role) {
			case MONK -> "site_monk";
			case PRIOR -> "site_prior";
			case HERBALIST -> "site_herbalist";
			default -> "site_recluse";
		};
	}

	// ---- the records ---------------------------------------------------------------------------------------------------

	static Map<String, Site> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(SITES, Map.of());
	}

	private static void put(MinecraftServer server, long ref, Site site) {
		Map<String, Site> map = new HashMap<>(all(server));
		map.put(Long.toString(ref), site);
		Factions.level(server).setAttached(SITES, Map.copyOf(map));
		byCamp = null;
	}

	private static Map<Integer, Site> byCamp(MinecraftServer server) {
		if (byCamp == null) {
			Map<Integer, Site> map = new HashMap<>();
			for (Site s : all(server).values()) {
				if (s.faction() >= 0) {
					map.put(s.faction(), s);
				}
			}
			byCamp = map;
		}
		return byCamp;
	}

	/** {@code camp} is seated in one of these places (its own buildings and growth are this class's, not its kind's). */
	static boolean is(MinecraftServer server, int camp) {
		return byCamp(server).containsKey(camp);
	}

	static @Nullable Site site(MinecraftServer server, int camp) {
		return byCamp(server).get(camp);
	}

	/** {@code camp} holds its place and never raids (a garrison, a smugglers' den). */
	static boolean noRaids(MinecraftServer server, int camp) {
		Site s = site(server, camp);
		if (s == null) {
			return false;
		}
		Plan plan = PLANS.get(s.path());
		return plan == null || plan.mode() != Mode.CAMP || SMUGGLER.equals(Camps.kind(server, camp));
	}

	/** {@code camp} is a smugglers' den: it lets players be unless provoked. */
	static boolean wary(MinecraftServer server, int camp) {
		return SMUGGLER.equals(Camps.kind(server, camp)) && is(server, camp);
	}

	/** A wary camp's people go for {@code target}: a player who struck one of them or touched their goods lately. */
	static boolean provoked(int camp, LivingEntity target) {
		if (!(target instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
			return false;
		}
		Long until = PROVOKED.getOrDefault(camp, Map.of()).get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	static void provoke(int camp, ServerPlayer player) {
		PROVOKED.computeIfAbsent(camp, c -> new HashMap<>()).put(player.getUUID(), player.level().getGameTime() + PROVOKED_TICKS);
	}

	/** The key of a cleared hold's message, for the kinds that are new here (null: the kind's own). */
	static @Nullable String clearedKey(MinecraftServer server, int camp) {
		String kind = Camps.kind(server, camp);
		return BANDIT.equals(kind) ? "bandit_camp" : SMUGGLER.equals(kind) ? "smuggler_den" : null;
	}

	/** Kinds whose camp raiders become MCA villagers at home, besides the barbarians' own. */
	static boolean villagerKind(String kind) {
		return BANDIT.equals(kind) || SMUGGLER.equals(kind);
	}

	// ---- looking for places --------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % SCAN_TICKS != 0) {
			return;
		}
		scan(level, now);
		garrisons(level, now);
	}

	private static void scan(ServerLevel level, long now) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Map<String, Site> settled = all(level.getServer());
		Set<Long> looked = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			ChunkPos middle = player.chunkPosition();
			for (int dx = -SCAN_CHUNKS; dx <= SCAN_CHUNKS; dx++) {
				for (int dz = -SCAN_CHUNKS; dz <= SCAN_CHUNKS; dz++) {
					ChunkAccess chunk = level.getChunkSource().getChunkNow(middle.x() + dx, middle.z() + dz);
					if (chunk == null) {
						continue;
					}
					for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
						Identifier id = registry.getKey(e.getKey());
						if (id == null || !id.getNamespace().equals(JaymosKingdoms.MOD_ID) || !id.getPath().startsWith(PREFIX)) {
							continue;
						}
						String path = id.getPath().substring(PREFIX.length());
						Plan plan = PLANS.get(path);
						if (plan == null) {
							continue;
						}
						for (long ref : e.getValue()) {
							if (!looked.add(ref) || settled.containsKey(Long.toString(ref))) {
								continue;
							}
							ChunkPos from = ChunkPos.unpack(ref);
							ChunkAccess startChunk = level.getChunkSource().getChunkNow(from.x(), from.z());
							StructureStart start = startChunk == null ? null : startChunk.getStartForStructure(e.getKey());
							if (start != null && start.isValid()) {
								consider(level, start, ref, path, plan, now);
							}
						}
					}
				}
			}
		}
	}

	/** (Dev games only, {@link DevCommands}) the place whose start is in the chunk at {@code x z} is moved into now. Returns 1 if one was. */
	static int devSettle(ServerLevel level, int x, int z) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
		for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
			Identifier id = registry.getKey(e.getKey());
			if (id != null && id.getNamespace().equals(JaymosKingdoms.MOD_ID) && id.getPath().startsWith(PREFIX) && e.getValue().isValid()) {
				String path = id.getPath().substring(PREFIX.length());
				Plan plan = PLANS.get(path);
				if (plan != null) {
					settle(level, e.getValue(), ChunkPos.pack(x >> 4, z >> 4), path, plan, level.getGameTime());
					return 1;
				}
			}
		}
		return 0;
	}

	/** A place nobody has moved into yet: they do if its tier is open to a player near it and nobody's watching. */
	private static void consider(ServerLevel level, StructureStart start, long ref, String path, Plan plan, long now) {
		BoundingBox box = start.getBoundingBox();
		BlockPos center = box.getCenter();
		double hide = HIDE_RANGE + Math.max(box.getXSpan(), box.getZSpan()) / 2.0;
		if (Raids.playerNear(level, center, hide) || !level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
			return;
		}
		boolean open = false;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().closerThan(center, TIER_RANGE) && WorldTiers.open(p, plan.tier())) {
				open = true;
				break;
			}
		}
		if (open) {
			settle(level, start, ref, path, plan, now);
		}
	}

	// ---- moving in ------------------------------------------------------------------------------------------------------

	private static void settle(ServerLevel level, StructureStart start, long ref, String path, Plan plan, long now) {
		MinecraftServer server = level.getServer();
		RandomSource random = RandomSource.create(ref ^ 0x5EED5L);
		BoundingBox box = start.getBoundingBox();
		BlockPos center = box.getCenter();
		// the posts, in the world
		List<BlockPos> posts = new ArrayList<>();
		StringBuilder kinds = new StringBuilder();
		StructurePiece first = start.getPieces().isEmpty() ? null : start.getPieces().get(0);
		SitePosts.Post[] template = SitePosts.POSTS.getOrDefault(path, new SitePosts.Post[0]);
		if (first instanceof PoolElementStructurePiece piece) {
			Rotation rotation = piece.getRotation();
			for (SitePosts.Post p : template) {
				BlockPos at = piece.getPosition().offset(StructureTemplate.transform(new BlockPos(p.x(), p.y(), p.z()), Mirror.NONE, rotation, BlockPos.ZERO));
				posts.add(standOn(level, at));
				kinds.append(p.kind());
			}
		}
		// who moves in
		Faction realm = null;
		String dweller = plan.dwellers();
		if (dweller.contains("|")) {
			String[] either = dweller.split("\\|");
			if (either[0].equals("garrison")) {
				realm = nearestRealm(server, center);
				dweller = realm != null ? "garrison" : either[1];
			} else {
				dweller = either[random.nextInt(either.length)];
			}
		}
		Mode mode = dweller.equals("abandoned") ? Mode.EMPTY : plan.mode();
		// their loot in the chests nobody has opened
		relabel(level, box, loot(path, dweller, plan.tier()), random);
		BlockPos seat = seat(level, posts, kinds, center);
		int faction = -1, crew = 0;
		switch (mode) {
			case EMPTY -> {
			}
			case PEACE -> peace(level, path, dweller, posts, kinds, seat);
			case CAMP, GARRISON -> {
				if (dweller.equals("garrison") && realm != null) {
					crew = garrison(level, realm, posts, kinds, seat, plan, random);
				} else {
					Faction camp = found(level, path, dweller, plan, box, seat, random);
					faction = camp.id();
					crew = man(level, camp, dweller, path, posts, kinds, seat, plan, random);
					if (plan.captives()) {
						captives(level, posts, kinds, seat, random);
					}
				}
			}
		}
		List<Long> packed = posts.stream().map(BlockPos::asLong).toList();
		put(server, ref, new Site(path, seat, box, dweller, faction, realm == null ? -1 : realm.id(), crew, packed, kinds.toString(), now, Production.day(server)));
		JaymosKingdoms.LOGGER.info("{} moved into the {} at {} ({} of them)", dweller, path, center, crew);
	}

	/** Where someone standing at {@code at} in the template stands now: there if there's room, else the nearest room. */
	private static BlockPos standOn(ServerLevel level, BlockPos at) {
		if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() || level.getBlockState(at).getCollisionShape(level, at).isEmpty()
			&& level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
			return at;
		}
		return Construction.standable(level, at, 3);
	}

	/** The leader's post: where the raid leader stood, else a caster, else a fighter, else the middle of the place. */
	private static BlockPos seat(ServerLevel level, List<BlockPos> posts, CharSequence kinds, BlockPos center) {
		for (char want : new char[] {'L', 'C', 'M', 'R', 'W'}) {
			for (int i = 0; i < posts.size(); i++) {
				if (kinds.charAt(i) == want) {
					return posts.get(i);
				}
			}
		}
		return Construction.standable(level, center, 8);
	}

	private static @Nullable Faction nearestRealm(MinecraftServer server, BlockPos at) {
		Faction best = null;
		double bestDist = GARRISON_REACH * GARRISON_REACH;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm()) {
				continue;
			}
			double d = f.seat().distSqr(at);
			if (d < bestDist) {
				bestDist = d;
				best = f;
			}
		}
		return best;
	}

	// ---- loot -------------------------------------------------------------------------------------------------------------

	/** The loot table a place's chests get once its people are in. */
	static String loot(String path, String dweller, WorldTiers.Tier tier) {
		String table = switch (dweller) {
			case "goblins" -> path.equals("illager_mole_underground") ? "goblin_mine" : "goblins";
			default -> dweller;
		};
		if (tier.ordinal() >= WorldTiers.Tier.DREAD.ordinal() && (table.equals("illagers") || table.equals("pirates") || table.equals("smugglers"))) {
			table += "_dread";
		}
		return "jaymoskingdoms:chests/illager_sites/" + table;
	}

	/** Every chest in {@code box} still holding its junk table (nobody has opened it) gets {@code table}. */
	private static void relabel(ServerLevel level, BoundingBox box, String table, RandomSource random) {
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table));
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
					if (be instanceof RandomizableContainerBlockEntity chest && box.isInside(be.getBlockPos()) && chest.getLootTable() != null
						&& chest.getLootTable().identifier().toString().startsWith(JUNK)) {
						chest.setLootTable(key, random.nextLong());
						be.setChanged();
					}
				}
			}
		}
	}

	/** The containers in {@code box} (for a camp's loot pile), nearest {@code seat} first. */
	private static List<BlockPos> containers(ServerLevel level, BoundingBox box, BlockPos seat) {
		List<BlockPos> out = new ArrayList<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof RandomizableContainerBlockEntity && box.isInside(be.getBlockPos())) {
						out.add(be.getBlockPos().immutable());
					}
				}
			}
		}
		out.sort((a, b) -> Double.compare(a.distSqr(seat), b.distSqr(seat)));
		return out;
	}

	// ---- holds ------------------------------------------------------------------------------------------------------------

	/** Its kind of camp. */
	static String kind(String dweller) {
		return switch (dweller) {
			case "illagers" -> IllagerCamps.KIND;
			case "pirates" -> PirateCoves.KIND;
			case "orcs" -> OrcCamps.KIND;
			case "goblins" -> GoblinVillages.KIND;
			case "bandits" -> BANDIT;
			case "smugglers" -> SMUGGLER;
			default -> Camps.BARBARIAN;
		};
	}

	/** A camp of {@code dweller}'s kind seated in the place: its land, its loot pile, its hold (never torn down or added to). */
	private static Faction found(ServerLevel level, String path, String dweller, Plan plan, BoundingBox box, BlockPos seat, RandomSource random) {
		MinecraftServer server = level.getServer();
		String kind = kind(dweller);
		Faction camp = Faction.create(Factions.nextId(server), FactionType.BARBARIAN, name(server, dweller, random),
			DyeColor.values()[random.nextInt(DyeColor.values().length)], seat, KingdomEvents.claim(server, seat, CLAIM));
		Factions.put(server, camp);
		int id = camp.id();
		long now = level.getGameTime();
		// (its data first: everything after asks what kind of camp it is)
		Camps.putData(server, id, new Camps.CampData(seat, Direction.NORTH, now, now, Production.day(server), -1L, List.of(), 0L, Map.of(), kind, 0, 0));
		Holds.update(server, id, h -> h.withGrim(JaymosKingdoms.MOD_ID + ":" + PREFIX + path, List.of()).withErected(true).withGuarded(box));
		List<BlockPos> chests = containers(level, box, seat);
		if (!chests.isEmpty()) {
			Storage.register(server, id, chests.subList(0, Math.min(4, chests.size())), chests.get(0));
		}
		if (!kind.equals(SMUGGLER)) {
			Camps.takeSides(server, id, kind);
		}
		byCamp = null;
		return Factions.get(server, id);
	}

	/** Its leader and its people, at the old posts; returns how many hands it keeps. */
	private static int man(ServerLevel level, Faction camp, String dweller, String path, List<BlockPos> posts, CharSequence kinds, BlockPos seat,
		Plan plan, RandomSource random) {
		crown(level, camp, dweller, seat);
		int hands = 0, most = MOST_CREW_AT.getOrDefault(path, MOST_CREW);
		for (int i = 0; i < posts.size() && hands < most; i++) {
			char k = kinds.charAt(i);
			if (k == 'P' || k == 'W' || posts.get(i).equals(seat) && k != 'R') {
				continue;
			}
			if (hand(level, camp, dweller, path, k, posts.get(i), hands) != null) {
				hands++;
			}
		}
		// too few posts (the stable, the cart, the tipis): the rest about the seat
		int least = Math.max(LEAST_CREW, plan.least());
		for (int tries = 0; hands < least && tries < least * 3; tries++) {
			BlockPos at = Construction.standable(level, seat.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), 4);
			if (hand(level, camp, dweller, path, hands % 3 == 0 ? 'R' : 'M', at, hands) != null) {
				hands++;
			}
		}
		if (dweller.equals("orcs")) {
			// every orc camp has its band of ogres and a Bogre
			OgreHolds.joinCamp(level, Factions.get(level.getServer(), camp.id()), OgreHolds.region(seat));
		}
		if (dweller.equals("smugglers")) {
			Villager fence = Underworld.hire(level, Vec3.atBottomCenterOf(Construction.standable(level, seat.offset(2, 0, 1), 4)), FENCE_TAG);
			if (fence != null) {
				fence.setHomeTo(seat, ROAM);
			}
		}
		return hands;
	}

	/** Seats {@code camp}'s leader at {@code seat}. */
	private static void crown(ServerLevel level, Faction camp, String dweller, BlockPos seat) {
		MinecraftServer server = level.getServer();
		switch (dweller) {
			case "illagers" -> Illagers.crown(level, camp, seat);
			case "orcs" -> OrcCamps.crown(level, camp, seat, Direction.NORTH);
			case "goblins" -> GoblinVillages.crown(level, camp, seat, Direction.NORTH);
			case "pirates" -> {
				Folk captain = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
				if (captain != null) {
					captain.snapTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, 0.0F, 0.0F);
					captain.setup(Pirates.CAPTAIN, camp, seat, LEADER_ROAM);
					level.addFreshEntity(captain);
					Factions.update(server, camp.id(), f -> f.withLeader(Optional.of(captain.getUUID())));
				}
			}
			default -> {
				Faction crowned = KingdomEvents.crown(level, Factions.get(server, camp.id()));
				if (crowned.leader().isPresent() && level.getEntity(crowned.leader().get()) instanceof Folk chief) {
					chief.snapTo(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, chief.getYRot(), 0.0F);
					chief.setHomeTo(seat, LEADER_ROAM);
					dress(chief, camp.id(), false);
				}
			}
		}
	}

	/** One of {@code camp}'s people where a mob of kind {@code k} stood. */
	private static @Nullable Mob hand(ServerLevel level, Faction camp, String dweller, String path, char k, BlockPos at, int i) {
		MinecraftServer server = level.getServer();
		Faction now = Factions.get(server, camp.id());
		if (now == null) {
			return null;
		}
		return switch (dweller) {
			case "illagers" -> Illagers.spawn(level, now, k == 'C' ? Illagers.EVOKER : k == 'B' ? Illagers.RAVAGER : k == 'R' ? Illagers.PILLAGER : Illagers.VINDICATOR,
				at, at, ROAM, false);
			case "orcs" -> OrcCamps.spawn(level, camp.id(), k == 'C' ? Orcs.SHAMAN : k == 'R' ? Orcs.RANGER : Orcs.GRUNT, at, at, ROAM);
			case "goblins" -> GoblinVillages.spawn(level, camp.id(), k == 'R' && i % 2 == 0 ? Goblins.SLINGER : Goblins.GOBLIN, at, at, ROAM);
			case "pirates" -> PirateCoves.spawn(level, camp.id(), k == 'C' ? Pirates.BOMBARDIER : k == 'R' ? Pirates.GUNNER : Pirates.DECKHAND, at, at, ROAM);
			default -> {
				Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
				if (folk == null) {
					yield null;
				}
				folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
				folk.setup(Folk.BARBARIAN, now, at, ROAM);
				// (the stable's muscle: every other one a masked bandit)
				dress(folk, camp.id(), BANDIT_MUSCLE.contains(path) && i % 2 == 1);
				level.addFreshEntity(folk);
				Factions.update(server, camp.id(), f -> f.withHoldings(f.holdings().adjust(Folk.BARBARIAN, 1)));
				yield folk;
			}
		};
	}

	/**
	 * A bandit's or smuggler's look (from here, and for a bandit hold's raiders from {@link Raids#member}): a bandit in his
	 * mask, leather and a blade; a smuggler in a bandana and leather, a knife at his belt. {@code bandit}: a bandit whatever
	 * his camp (the stable's hired muscle, and a hold's chief). Anyone else: nothing.
	 */
	static void dress(Mob mob, int camp, boolean bandit) {
		MinecraftServer server = mob.level().getServer();
		if (server == null) {
			return;
		}
		String kind = Camps.kind(server, camp);
		if (!BANDIT.equals(kind) && !SMUGGLER.equals(kind)) {
			return;
		}
		boolean masked = bandit || BANDIT.equals(kind);
		Item head = masked ? Bandits.MASK : Bandits.BANDANA;
		if (head != null) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
		}
		mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(masked ? Items.IRON_SWORD : Items.IRON_AXE));
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.MAINHAND}) {
			mob.setDropChance(slot, 0.0F);
		}
	}

	/** Prisoners where the place penned someone (or its thralls stood), else a few about the seat. */
	private static void captives(ServerLevel level, List<BlockPos> posts, CharSequence kinds, BlockPos seat, RandomSource random) {
		int taken = 0;
		for (int i = 0; i < posts.size(); i++) {
			if (kinds.charAt(i) == 'P' || kinds.charAt(i) == 'W') {
				if (Captives.take(level, posts.get(i)) != null) {
					taken++;
				}
			}
		}
		int more = taken > 0 ? 0 : 1 + random.nextInt(CAPTIVES_MOST);
		for (int i = 0; i < more; i++) {
			Captives.take(level, Construction.standable(level, seat.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), 4));
		}
	}

	/** A realm's soldiers in the tower: archers where pillagers stood, guards elsewhere. Returns how many. */
	private static int garrison(ServerLevel level, Faction realm, List<BlockPos> posts, CharSequence kinds, BlockPos seat, Plan plan, RandomSource random) {
		int n = 0;
		for (int i = 0; i < posts.size() && n < MOST_CREW; i++) {
			char k = kinds.charAt(i);
			if (k != 'P' && k != 'W' && soldier(level, realm, k == 'R' ? Folk.ARCHER : Folk.GUARD, posts.get(i)) != null) {
				n++;
			}
		}
		for (int tries = 0; n < Math.max(LEAST_CREW, plan.least()) && tries < 8; tries++) {
			if (soldier(level, realm, Folk.GUARD, Construction.standable(level, seat.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3), 4)) != null) {
				n++;
			}
		}
		return n;
	}

	private static @Nullable Folk soldier(ServerLevel level, Faction realm, int role, BlockPos at) {
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
		if (folk == null) {
			return null;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		folk.setup(role, realm, at, ROAM);
		folk.addTag(GARRISON_TAG);
		folk.setPersistenceRequired();
		level.addFreshEntity(folk);
		return folk;
	}

	/** The monks (the first a prior; the herbalist's house its herbalist) or the recluse, as MCA villagers of no settlement. */
	private static void peace(ServerLevel level, String path, String dweller, List<BlockPos> posts, CharSequence kinds, BlockPos seat) {
		List<BlockPos> spots = new ArrayList<>(posts);
		if (spots.isEmpty()) {
			spots.add(seat);
		}
		if (dweller.equals("recluse")) {
			spots = spots.subList(0, 1);
		}
		for (int i = 0; i < spots.size() && i < MOST_CREW; i++) {
			int role = dweller.equals("recluse") ? RECLUSE : path.equals("herbalist_house") ? HERBALIST : i == 0 ? PRIOR : MONK;
			BlockPos at = spots.get(i);
			PathfinderMob mob = McaCompat.stranger(level, at, level.getRandom().nextFloat() * 360.0F, role, at, PEACE_ROAM);
			if (mob != null) {
				mob.setPersistenceRequired();
			}
		}
	}

	// ---- names ------------------------------------------------------------------------------------------------------------

	private static final Map<String, String[][]> NAMES = Map.of(
		"bandits", new String[][] {{"Red", "Black", "Grey", "Crooked", "Hollow", "Masked", "Gallows", "Mud", "Ash"}, {" Hands", " Riders", " Masks", " Knives", " Crows", " Gang"}},
		"smugglers", new String[][] {{"Quiet", "Low", "Night", "Salt", "Fog", "Back", "Lantern"}, {" Road", " Ring", " Runners", " Tide", " Lane"}},
		"illagers", new String[][] {{"Grim", "Raven", "Gallow", "Thorn", "Vex", "Dusk", "Gloom", "Crow", "Rook"}, {"hold", "watch", "spire", "reach", "mark", "gate"}},
		"pirates", new String[][] {{"Salt", "Bilge", "Black", "Drowned", "Storm", "Red", "Gull"}, {" Brethren", " Crew", " Company", " Blades"}},
		"orcs", new String[][] {{"Ash", "Slag", "Cinder", "Iron", "Soot", "Ember"}, {"maw", "forge", "pit", "works", "fang"}},
		"goblins", new String[][] {{"Copper", "Rust", "Mole", "Deep", "Grub", "Tin"}, {"diggers", "burrow", "shaft", "warren", "delvers"}});

	private static String name(MinecraftServer server, String dweller, RandomSource random) {
		String[][] parts = NAMES.get(dweller);
		if (parts == null) {
			return Camps.name(server, random);
		}
		String name = "";
		for (int tries = 0; tries < 20; tries++) {
			name = parts[0][random.nextInt(parts[0].length)] + parts[1][random.nextInt(parts[1].length)];
			if (Factions.byName(server, name) == null) {
				return name;
			}
		}
		return name + " " + Factions.nextId(server);
	}

	// ---- keeping them (from Camps) --------------------------------------------------------------------------------------

	/** Every {@link Camps#CHECK_TICKS} while a held camp is loaded: its leader replaced if he's gone. Never builds. */
	static void campTick(ServerLevel level, Faction camp, Camps.CampData data) {
		MinecraftServer server = level.getServer();
		Site s = site(server, camp.id());
		if (s == null) {
			return;
		}
		boolean present = camp.leader().isPresent() && level.getEntity(camp.leader().get()) != null;
		if (present || !loaded(level, s.box()) || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			MISSING.remove(camp.id());
		} else if (MISSING.merge(camp.id(), (long)Camps.CHECK_TICKS, Long::sum) >= RESEAT_TICKS && !Raids.playerNear(level, s.seat(), HIDE_RANGE)) {
			MISSING.remove(camp.id());
			crown(level, camp, s.dweller(), s.seat());
			JaymosKingdoms.LOGGER.info("{} has a new leader (the last was lost)", camp.name());
		}
	}

	/** {@code days}' growth of a held camp: what its people bring in, and its hands made up again, one a day. */
	static void grow(ServerLevel level, Faction camp, int days) {
		MinecraftServer server = level.getServer();
		Site s = site(server, camp.id());
		Camps.updateData(server, camp.id(), d -> d.withGrown(d.grown() + days));
		if (s == null) {
			return;
		}
		if (Storage.loaded(level, camp)) {
			for (Map.Entry<Item, Integer> e : takings(s).entrySet()) {
				Storage.put(level, camp, e.getKey(), e.getValue() * days);
			}
		}
		if (!loaded(level, s.box()) || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
			|| Raids.playerNear(level, s.seat(), HIDE_RANGE + Math.max(s.box().getXSpan(), s.box().getZSpan()) / 2.0)) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int i = 0; i < Math.min(days, REFILL_MOST); i++) {
			Faction now = Factions.get(server, camp.id());
			if (now == null || now.fallen() || hands(now, s.dweller()) >= s.crew() || s.posts().isEmpty()) {
				return;
			}
			int pick = random.nextInt(s.posts().size());
			char k = pick < s.kinds().length() ? s.kinds().charAt(pick) : 'M';
			hand(level, now, s.dweller(), s.path(), k == 'P' || k == 'W' ? 'M' : k, BlockPos.of(s.posts().get(pick)), random.nextInt(4));
		}
	}

	/** What a place's people bring in each day: the moles dig copper and iron (the deep one most), and so on. */
	private static Map<Item, Integer> takings(Site s) {
		return switch (s.dweller()) {
			case "goblins" -> s.path().equals("illager_mole_underground") ? Map.of(Items.RAW_COPPER, 8, Items.RAW_IRON, 4, Items.COAL, 2)
				: Map.of(Items.RAW_COPPER, 3, Items.RAW_IRON, 1);
			case "orcs" -> Map.of(Items.COAL, 4, Items.IRON_INGOT, 1);
			case "illagers" -> Map.of(Items.ARROW, 6, Items.BREAD, 2);
			case "pirates" -> Map.of(Items.COOKED_COD, 4, Items.GUNPOWDER, 2);
			case "bandits" -> Map.of(Items.BREAD, 2, Items.LEATHER, 2);
			case "smugglers" -> Map.of(Items.POTATO, 4, Items.WHEAT, 2);
			default -> Map.of(Items.COOKED_MUTTON, 3, Items.LEATHER, 2);
		};
	}

	/** Its hands on the roster (out on a raid or not). */
	private static int hands(Faction camp, String dweller) {
		int[] roles = switch (dweller) {
			case "illagers" -> new int[] {Illagers.PILLAGER, Illagers.VINDICATOR, Illagers.EVOKER, Illagers.RAVAGER};
			case "orcs" -> new int[] {Orcs.GRUNT, Orcs.RANGER, Orcs.SHAMAN};
			case "goblins" -> new int[] {Goblins.GOBLIN, Goblins.SLINGER};
			case "pirates" -> new int[] {Pirates.DECKHAND, Pirates.GUNNER, Pirates.BOMBARDIER};
			default -> new int[] {Folk.BARBARIAN};
		};
		int n = 0;
		for (int role : roles) {
			n += camp.holdings().count(role) + camp.holdings().vacancies().getOrDefault(Folk.roleName(role), 0);
		}
		return n;
	}

	private static boolean loaded(ServerLevel level, BoundingBox box) {
		return level.isPositionEntityTicking(new BlockPos(box.minX(), box.minY(), box.minZ())) && level.isPositionEntityTicking(new BlockPos(box.maxX(), box.minY(), box.maxZ()))
			&& level.isPositionEntityTicking(box.getCenter());
	}

	/** A realm's tower: a soldier made up again once a day while the realm stands and nobody is watching. */
	private static void garrisons(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		long today = Production.day(server);
		for (Map.Entry<String, Site> e : all(server).entrySet()) {
			Site s = e.getValue();
			if (s.realm() < 0 || s.lastDay() >= today || s.posts().isEmpty() || !loaded(level, s.box())) {
				continue;
			}
			Faction realm = Factions.get(server, s.realm());
			if (realm == null || realm.fallen()) {
				continue;
			}
			if (Raids.playerNear(level, s.seat(), HIDE_RANGE + Math.max(s.box().getXSpan(), s.box().getZSpan()) / 2.0)) {
				continue;
			}
			int there = Workers.near(level, AABB.of(s.box()).inflate(ROAM), w -> w.mob().isAlive() && w.factionId() == realm.id()
				&& (w.role() == Folk.GUARD || w.role() == Folk.ARCHER)).size();
			if (there < s.crew()) {
				int pick = level.getRandom().nextInt(s.posts().size());
				char k = pick < s.kinds().length() ? s.kinds().charAt(pick) : 'M';
				soldier(level, realm, k == 'R' ? Folk.ARCHER : Folk.GUARD, BlockPos.of(s.posts().get(pick)));
			}
			put(server, Long.parseLong(e.getKey()), s.withLastDay(today));
		}
	}

	// ---- smugglers --------------------------------------------------------------------------------------------------------

	/** {@code player} used a block: if it holds a smugglers' goods, the crew takes it ill. */
	private static void touched(ServerLevel level, ServerPlayer player, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity)) {
			return;
		}
		MinecraftServer server = level.getServer();
		for (Site s : byCamp(server).values()) {
			if (!s.box().isInside(pos) || !SMUGGLER.equals(Camps.kind(server, s.faction()))) {
				continue;
			}
			Faction camp = Factions.get(server, s.faction());
			if (camp == null || camp.fallen() || provoked(camp.id(), player)) {
				return;
			}
			provoke(camp.id(), player);
			List<Worker> crew = Workers.near(level, new AABB(pos).inflate(24.0), w -> w.mob().isAlive() && w.factionId() == camp.id());
			if (!crew.isEmpty()) {
				Speech.say(level, crew.get(0).mob(), Component.translatable("talk.jaymoskingdoms.illager_sites.smuggler.touched." + level.getRandom().nextInt(GREETINGS)),
					Speech.RED, SPEECH_RANGE);
			}
			return;
		}
	}

	/** A smugglers' fence lays out the day's stock: contraband both ways, forged papers, and bundles for the nearest dens. */
	private static void stock(ServerLevel level, Villager v) {
		long day = level.getGameTime() / 24000L;
		Long last = STOCKED.get(v.getUUID());
		if (last != null && last >= day) {
			return;
		}
		STOCKED.put(v.getUUID(), day);
		MerchantOffers offers = v.getOffers();
		offers.clear();
		if (Underworld.SHASH_BRICK != null) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, FENCE_SHASH), new ItemStack(Underworld.SHASH_BRICK), FENCE_STOCK, Underworld.XP, Underworld.PRICE_MULTIPLIER));
			offers.add(new MerchantOffer(new ItemCost(Underworld.SHASH_BRICK, 1), new ItemStack(Items.EMERALD, FENCE_SHASH_BUY), FENCE_STOCK, Underworld.XP, Underworld.PRICE_MULTIPLIER));
		}
		Item pmeh = Underworld.pmeh();
		if (pmeh != null) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, FENCE_PMEH), new ItemStack(pmeh), FENCE_STOCK, Underworld.XP, Underworld.PRICE_MULTIPLIER));
		}
		if (ForgedPapers.PASS != null) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, ForgedPapers.PASS_PRICE), new ItemStack(ForgedPapers.PASS), 2, Underworld.XP, Underworld.PRICE_MULTIPLIER));
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, ForgedPapers.PARDON_PRICE), new ItemStack(ForgedPapers.PARDON), 1, Underworld.XP, Underworld.PRICE_MULTIPLIER));
		}
		// sealed bundles to run to the nearest dens (each den's fence pays for its own)
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		List<Faction> dens = new ArrayList<>();
		for (Faction o : Factions.all(server)) {
			Underworld.Den d = !o.type().isRealm() || o.fallen() ? null : Underworld.builtDen(server, o);
			if (d != null && !d.shutAt(now) && o.seat().distSqr(v.blockPosition()) <= Smuggling.REACH * Smuggling.REACH) {
				dens.add(o);
			}
		}
		dens.sort((a, b) -> Double.compare(a.seat().distSqr(v.blockPosition()), b.seat().distSqr(v.blockPosition())));
		for (int i = 0; i < Math.min(FENCE_DENS, dens.size()); i++) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, FENCE_BUNDLE), Smuggling.bundle(dens.get(i)), 1, Underworld.XP, Underworld.PRICE_MULTIPLIER));
		}
	}

	// ---- the peaceful folk (from Workers#step) ---------------------------------------------------------------------------

	static boolean isPeaceRole(int role) {
		return role == MONK || role == PRIOR || role == HERBALIST || role == RECLUSE;
	}

	/** A monk's, herbalist's or recluse's own step: kept near home, visitors greeted once a day (the herbalist mends them). False: their brain runs. */
	static boolean peaceStep(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (mob.tickCount % 20 != 0 || mob.isSleeping() || McaCompat.busy(mob) || mob.getTarget() != null) {
			return false;
		}
		if (mob.hasHome() && !mob.getHomePosition().closerThan(mob.blockPosition(), mob.getHomeRadius() + 6.0)) {
			BlockPos home = mob.getHomePosition();
			mob.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.6);
			return true;
		}
		long now = level.getGameTime();
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(GREET_RANGE),
			p -> p.isAlive() && !p.isSpectator() && mob.hasLineOfSight(p))) {
			String key = mob.getUUID() + "/" + player.getUUID();
			Long met = GREETED.get(key);
			if (met != null && now - met < GREET_TICKS) {
				continue;
			}
			GREETED.put(key, now);
			greet(level, w, player);
			return false;
		}
		return false;
	}

	private static void greet(ServerLevel level, Worker w, ServerPlayer player) {
		PathfinderMob mob = w.mob();
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		String who = switch (w.role()) {
			case PRIOR -> "prior";
			case HERBALIST -> "herbalist";
			case RECLUSE -> "recluse." + PASTS[Math.floorMod(mob.getUUID().hashCode(), PASTS.length)];
			default -> "monk";
		};
		boolean hurt = player.getHealth() < player.getMaxHealth() * MEND_BELOW;
		if (w.role() == HERBALIST && hurt) {
			// the herbalist sees to a hurt visitor's wounds
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEND_TICKS, 0));
			Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.illager_sites.herbalist.mend." + level.getRandom().nextInt(GREETINGS)), COLOR, SPEECH_RANGE);
		} else {
			Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.illager_sites." + who + "." + level.getRandom().nextInt(GREETINGS)), COLOR, SPEECH_RANGE);
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.8F, 0.95F);
	}

	/** {@code e} is one of the places' peaceful folk. */
	static boolean isPeaceful(@Nullable Entity e) {
		return e instanceof Worker w && isPeaceRole(w.role());
	}
}
