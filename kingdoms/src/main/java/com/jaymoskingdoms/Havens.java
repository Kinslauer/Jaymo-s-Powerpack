package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.221) Andy (2026-10-26): "you will often see villages of solely non humans because of this fact, as the ones who
 * lived among the humans got tired of the ridicule. raising a sort of rivalry, between human and nonhumans across the
 * entire world."
 * <p>
 * A haven is a settlement of non-humans only ({@link Prejudice#degree}): of one blood (the one its land suits: Tideborn
 * by water (0.7.486: anywhere by the shore, and never inland), Stoneborn in the hills, Sylvan in the woods, else any of
 * the other three) or a mix of all four. Every one born
 * there is of it ({@link #lean}, from {@link VillagerHeritage#roll}), and so are its own folk ({@link #folk}). Whether a
 * settlement is a haven is drawn once, when it's first looked at, and kept ({@link #HAVENS}). Settlements that were
 * already there when this came in are never havens (their folk are who they are); new ones are, this often:
 * {@link #VILLAGE_SHARE} of villages, {@link #REALM_SHARE} of realms (never a purist's, never a barbarian camp).
 * Folk who first load within {@link #SETTLE_TICKS} of a haven being drawn take its blood, so a village found with its
 * people already standing is wholly of it too.
 * <p>
 * The rivalry is {@link Prejudice}'s: a haven's people mind humans hard, and non-humans anywhere mind them a little.
 */
public final class Havens {
	/** How often a new settlement is a haven: a village, and a kingdom or fiefdom. */
	static final float VILLAGE_SHARE = 0.25F, REALM_SHARE = 0.1F;
	/** A haven of all four bloods mixed this often (the rest are of one). */
	static final float MIXED_SHARE = 0.35F;
	/** A haven's land, round its seat. */
	static final double LAND_RANGE = 96.0;
	/** Folk first loading this soon after a haven is drawn take its blood (ticks: two days). */
	static final long SETTLE_TICKS = 48000L;
	/** A mixed haven's bloods, by weight: Tideborn, Stoneborn, Warrior-born, Sylvan. */
	static final int MIX_TIDE = 3, MIX_STONE = 3, MIX_WARRIOR = 2, MIX_SYLVAN = 3;
	/**
	 * (0.7.441, Andy: "some havens live in unity with goblins and orcs or maybe under an orc king") This share of havens
	 * live alongside goblins and orcs: that share of those born there are goblins and orcs, and the tribes' war bands
	 * leave them be. This share of those sit under an orc king: more of their folk are orcs, and their ruler goes by an
	 * orc's title.
	 */
	static final float GREENSKIN_SHARE = 0.25F, ORC_KING_SHARE = 0.35F;
	static final float GREENSKIN_BIRTHS = 0.25F, ORC_RULED_BIRTHS = 0.35F;

	static final String MIXED = "mixed", NONE = "", MIGRATED = "_migrated";
	/** Faction id → its haven ("" none, "mixed", or a heritage key), "|" the game time it was drawn. On the Overworld. */
	static final AttachmentType<Map<String, String>> HAVENS = AttachmentRegistry.create(JaymosKingdoms.id("havens"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	private Havens() {
	}

	static void register() {
		// the settlements already standing in an old world stay as they are
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel level = Factions.level(server);
			Map<String, String> map = level.getAttachedOrElse(HAVENS, Map.of());
			if (map.containsKey(MIGRATED)) {
				return;
			}
			Map<String, String> next = new HashMap<>(map);
			for (Faction f : Factions.all(server)) {
				next.putIfAbsent(Integer.toString(f.id()), NONE + "|0");
			}
			next.put(MIGRATED, "1");
			level.setAttached(HAVENS, Map.copyOf(next));
		});
	}

	/** {@code f}'s haven: "" none, {@link #MIXED}, or a heritage key (drawn and kept the first time it's asked). */
	static String of(MinecraftServer server, @Nullable Faction f) {
		if (f == null) {
			return NONE;
		}
		String entry = entry(server, f);
		return entry.substring(0, entry.indexOf('|'));
	}

	private static String entry(MinecraftServer server, Faction f) {
		ServerLevel level = Factions.level(server);
		Map<String, String> map = level.getAttachedOrElse(HAVENS, Map.of());
		String key = Integer.toString(f.id());
		String entry = map.get(key);
		if (entry != null && entry.indexOf('|') >= 0) {
			return entry;
		}
		entry = draw(level, f) + "|" + level.getGameTime();
		Map<String, String> next = new HashMap<>(map);
		next.put(key, entry);
		level.setAttached(HAVENS, Map.copyOf(next));
		return entry;
	}

	/** Whether {@code f} is a haven, and of what (from who it is, so the draw is always the same). */
	private static String draw(ServerLevel level, Faction f) {
		return draw(level, f, false);
	}

	/** ... and {@code always}: a haven whatever the share (a ChoiceTheorem village's, {@link #ctov}). */
	private static String draw(ServerLevel level, Faction f, boolean always) {
		if (f.fallen() || f.type() == FactionType.BARBARIAN || Tempers.of(f) == Tempers.Temper.PURIST) {
			return NONE;
		}
		long mix = (f.seat().asLong() * 0x9E3779B97F4A7C15L) ^ ((long)f.id() << 21);
		mix ^= mix >>> 29;
		float share = f.type().isRealm() ? REALM_SHARE : VILLAGE_SHARE;
		if (!always && Math.floorMod(mix, 1000L) >= (long)(share * 1000)) {
			return NONE;
		}
		mix = Long.rotateLeft(mix, 17) * 0xBF58476D1CE4E5B9L;
		if (Math.floorMod(mix >>> 7, 1000L) < (long)(MIXED_SHARE * 1000)) {
			return MIXED;
		}
		Holder<Biome> biome = level.getBiome(f.seat());
		// (0.7.486, Andy: "it just makes sense for their havens or settlements to be by the shore") a seat with the sea,
		// a river or a beach anywhere round it is the Tideborn's; and inland, never theirs
		if (shore(level, f.seat())) {
			return Heritage.Kind.TIDEBORN.key();
		}
		if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || biome.is(BiomeTags.IS_BADLANDS)) {
			return Heritage.Kind.STONEBORN.key();
		}
		if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)) {
			return Heritage.Kind.SYLVAN.key();
		}
		Heritage.Kind[] three = {Heritage.Kind.STONEBORN, Heritage.Kind.WARRIORBORN, Heritage.Kind.SYLVAN};
		return three[(int)Math.floorMod(mix >>> 23, 3L)].key();
	}

	/** (0.7.486) How far round a place the shore is looked for (blocks), in rings this far apart, this many points each. */
	static final int SHORE_REACH = 72, SHORE_STEP = 24, SHORE_POINTS = 8;

	/** (0.7.486) Whether {@code pos} is by the shore: the sea, a river or a beach at it or within {@link #SHORE_REACH}. */
	public static boolean shore(ServerLevel level, BlockPos pos) {
		for (int r = 0; r <= SHORE_REACH; r += SHORE_STEP) {
			for (int i = 0; i < (r == 0 ? 1 : SHORE_POINTS); i++) {
				double a = i * Math.PI * 2.0 / SHORE_POINTS;
				Holder<Biome> biome = level.getBiome(new BlockPos(pos.getX() + (int)(Math.cos(a) * r), level.getSeaLevel(), pos.getZ() + (int)(Math.sin(a) * r)));
				if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * (0.7.381) {@code f} (just founded round a life story's village start, {@link StoryHomes}) is made {@code haven} ("" none,
	 * {@link #MIXED} or a heritage key), drawn now: its townsfolk already standing are made of its blood, and those who load
	 * in the next days take it ({@link #settle}).
	 */
	static void make(ServerLevel level, Faction f, String haven) {
		MinecraftServer server = level.getServer();
		ServerLevel store = Factions.level(server);
		Map<String, String> next = new HashMap<>(store.getAttachedOrElse(HAVENS, Map.of()));
		next.put(Integer.toString(f.id()), haven + "|" + store.getGameTime());
		store.setAttached(HAVENS, Map.copyOf(next));
		if (haven.isEmpty()) {
			return;
		}
		for (LivingEntity x : level.getEntitiesOfClass(LivingEntity.class, new AABB(f.seat()).inflate(LAND_RANGE),
			x -> x.isAlive() && VillagerHeritage.eligible(x) && (!(x instanceof Folk folk) || folk.factionId() == f.id()))) {
			if (!fits(server, f, VillagerHeritage.of(x)) && !keptHuman(server, f, x, VillagerHeritage.of(x))) {
				Heritage.Kind kind = blood(server, f, x.getRandom());
				if (kind != null) {
					VillagerHeritage.born(x, kind, null);
				}
			}
		}
	}

	/**
	 * (0.7.521) Andy (2026-11-04), of ChoiceTheorem's Overhauled Village's villages: "those should function like the
	 * havens, there should rarely be a human there and if there is theres a REASON." A settlement founded in one of its
	 * villages (structure ids {@code ctov:*}) is a haven always (of the blood its land suits, or mixed, as any haven;
	 * never a purist's or a barbarian camp), kept as one in {@link #HAVENS} under {@code ctov:<faction id>}. Its people
	 * standing when it's found take its blood, but for about one human in {@link #KEPT_HUMAN_IN}, who stays and has a
	 * reason to ({@link #humanReason}), given when asked their name ({@link Introductions#askName}).
	 */
	static void ctov(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		ServerLevel store = Factions.level(server);
		Map<String, String> next = new HashMap<>(store.getAttachedOrElse(HAVENS, Map.of()));
		next.put(CTOV + f.id(), "1");
		store.setAttached(HAVENS, Map.copyOf(next));
		String haven = draw(store, f, true);
		make(level, f, haven);
		JaymosKingdoms.LOGGER.info("{} stands in one of ChoiceTheorem's villages: a haven ({})", f.name(), haven.isEmpty() ? "none: purist" : haven);
	}

	/** (0.7.521) The key prefix of a ChoiceTheorem village's haven in {@link #HAVENS}, and its structures' namespace. */
	static final String CTOV = "ctov:";
	/** (0.7.521) About one human in this many in a ChoiceTheorem village's haven stays there, for a reason. */
	static final int KEPT_HUMAN_IN = 25;
	/** (0.7.521) Why: wed into it, cast out of the human towns, a foundling raised there, its healer, working off a debt, a deserter it hid. */
	static final String[] HUMAN_REASONS = {"wed", "outcast", "foundling", "healer", "debt", "deserter"};

	/** (0.7.521) {@code f} was founded in one of ChoiceTheorem's villages ({@link #ctov}). */
	static boolean ctovHaven(MinecraftServer server, @Nullable Faction f) {
		return f != null && Factions.level(server).getAttachedOrElse(HAVENS, Map.of()).containsKey(CTOV + f.id()) && is(server, f);
	}

	/** (0.7.521) {@code who}, human, stays human in ChoiceTheorem haven {@code f} (about one in {@link #KEPT_HUMAN_IN}, by who they are). */
	static boolean keptHuman(MinecraftServer server, @Nullable Faction f, LivingEntity who, Heritage.@Nullable Kind kind) {
		if (kind == null || Prejudice.degree(kind) > 0 || kind.brood || !ctovHaven(server, f)) {
			return false;
		}
		return Math.floorMod(lotOf(who), (long)KEPT_HUMAN_IN) == 0L;
	}

	/** (0.7.521) Why {@code who} is a human living in a ChoiceTheorem haven (a {@link #HUMAN_REASONS} key), or null if they aren't one. */
	static @Nullable String humanReason(LivingEntity who) {
		if (!(who.level() instanceof ServerLevel level)) {
			return null;
		}
		Faction f = home(who);
		if (!keptHuman(level.getServer(), f, who, VillagerHeritage.of(who))) {
			return null;
		}
		return HUMAN_REASONS[(int)Math.floorMod(lotOf(who) >>> 17, (long)HUMAN_REASONS.length)];
	}

	private static long lotOf(LivingEntity who) {
		long mix = who.getUUID().getLeastSignificantBits() * 0x9E3779B97F4A7C15L ^ who.getUUID().getMostSignificantBits();
		return mix ^ (mix >>> 31);
	}

	/** {@code f} is a haven. */
	static boolean is(MinecraftServer server, @Nullable Faction f) {
		return !of(server, f).isEmpty();
	}

	/** The haven whose land {@code pos} is on, or null. */
	static @Nullable Faction at(ServerLevel level, BlockPos pos) {
		Faction f = Factions.around(level.getServer(), level, pos, LAND_RANGE);
		return is(level.getServer(), f) ? f : null;
	}

	/** One born at {@code pos}: on a haven's land, of its blood (null: the usual roll). */
	static Heritage.@Nullable Kind lean(ServerLevel level, BlockPos pos, RandomSource random) {
		return blood(level.getServer(), at(level, pos), random);
	}

	/** A folk set up for faction {@code id}: of the haven's blood if it's one (null: the usual roll). */
	static Heritage.@Nullable Kind folk(MinecraftServer server, int id, RandomSource random) {
		return blood(server, Factions.get(server, id), random);
	}

	/** A blood for one of haven {@code f}'s people, or null if it isn't one. */
	private static Heritage.@Nullable Kind blood(MinecraftServer server, @Nullable Faction f, RandomSource random) {
		String haven = of(server, f);
		if (haven.isEmpty()) {
			return null;
		}
		if (greenskin(server, f) && random.nextFloat() < (orcRuled(server, f) ? ORC_RULED_BIRTHS : GREENSKIN_BIRTHS)) {
			// (0.7.441) one of the goblins and orcs who live there
			return orcRuled(server, f) || random.nextBoolean() ? Heritage.Kind.ORC : Heritage.Kind.GOBLIN;
		}
		if (!MIXED.equals(haven)) {
			return Heritage.Kind.byKey(haven);
		}
		int pick = random.nextInt(MIX_TIDE + MIX_STONE + MIX_WARRIOR + MIX_SYLVAN);
		return (pick -= MIX_TIDE) < 0 ? Heritage.Kind.TIDEBORN : (pick -= MIX_STONE) < 0 ? Heritage.Kind.STONEBORN
			: pick - MIX_WARRIOR < 0 ? Heritage.Kind.WARRIORBORN : Heritage.Kind.SYLVAN;
	}

	/** (0.7.441) Haven {@code f} lives alongside goblins and orcs (drawn from who it is, so always the same). */
	static boolean greenskin(MinecraftServer server, @Nullable Faction f) {
		return is(server, f) && Math.floorMod(lot(f) >>> 11, 1000L) < (long)(GREENSKIN_SHARE * 1000);
	}

	/** (0.7.441) Haven {@code f} sits under an orc king (one of those that live alongside the tribes). */
	static boolean orcRuled(MinecraftServer server, @Nullable Faction f) {
		return greenskin(server, f) && Math.floorMod(lot(f) >>> 37, 1000L) < (long)(ORC_KING_SHARE * 1000);
	}

	private static long lot(Faction f) {
		long mix = (f.seat().asLong() ^ 0x5851F42D4C957F2DL) * 0x9E3779B97F4A7C15L + f.id();
		return mix ^ (mix >>> 29);
	}

	/** {@code kind} belongs in haven {@code f}. */
	static boolean fits(MinecraftServer server, @Nullable Faction f, Heritage.@Nullable Kind kind) {
		String haven = of(server, f);
		if (kind != null && kind.brood && greenskin(server, f)) {
			// (0.7.441) its goblins and orcs
			return true;
		}
		return haven.isEmpty() || (MIXED.equals(haven) ? Prejudice.degree(kind) > 0 : kind != null && haven.equals(kind.key()));
	}

	/**
	 * A villager (already of a blood) first loads: in a haven drawn only lately, one not of its blood takes it (the village
	 * was found with its people standing). Null leaves them be.
	 */
	static Heritage.@Nullable Kind settle(ServerLevel level, LivingEntity villager, Heritage.@Nullable Kind kind) {
		Faction f = villager instanceof Folk folk ? Factions.get(level.getServer(), folk.factionId()) : at(level, villager.blockPosition());
		if (f == null || fits(level.getServer(), f, kind) || keptHuman(level.getServer(), f, villager, kind)) {
			return null;
		}
		if (kind != null && kind.brood) {
			// (0.7.490) an orc or goblin here came as a Settler (whatever the haven's blood): it keeps its brood
			return null;
		}
		String entry = entry(level.getServer(), f);
		long drawn = Long.parseLong(entry.substring(entry.indexOf('|') + 1));
		if (level.getGameTime() - drawn > SETTLE_TICKS) {
			return null;
		}
		return blood(level.getServer(), f, villager.getRandom());
	}

	/** The haven {@code who} lives in, or null. */
	static @Nullable Faction home(LivingEntity who) {
		if (!(who.level() instanceof ServerLevel level)) {
			return null;
		}
		if (who instanceof Folk folk) {
			Faction f = Factions.get(level.getServer(), folk.factionId());
			return is(level.getServer(), f) ? f : null;
		}
		return at(level, who.blockPosition());
	}
}
