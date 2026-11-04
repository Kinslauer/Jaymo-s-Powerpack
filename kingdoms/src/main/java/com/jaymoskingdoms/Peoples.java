package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Hordes, "Living together" (Andy, 2026-11-03): orcs and goblins can live among humans, more easily among the
 * other heritages, and every people has some distaste for the others at its own strength. This is the table of it
 * ({@link #distaste}, 0 none to 3 the worst), and what it does:
 * <ul>
 * <li><b>Minds</b> ({@link #minds}): {@link Prejudice#minds} asks it whenever a goblin or an orc is on either side, for a
 * villager or a player (a half-orc or goblin-raised player meets it too: {@link GreenskinLife#minds}).</li>
 * <li><b>Hearts</b> ({@link #meet}): the first time a villager who minds your blood meets you, MCA's hearts start lower
 * by how much.</li>
 * <li><b>Glares and insults</b> ({@link #glare}): villagers of peoples that dislike each other mutter at one another; a
 * greenskin does it in broken English.</li>
 * <li><b>Brawls</b> ({@link #brawls}): a meeting between such neighbours turns to blows sooner ({@link Neighbours#choose}).</li>
 * <li><b>Homes</b> ({@link #apart}): two who despise each other won't share a bed; the greenskin looks for another.</li>
 * </ul>
 * Settling is {@link Settlers}'.
 */
public final class Peoples {
	// ================================================================================================================
	// Tuning
	/** Reading a distaste as Prejudice's degree: each point of the table is this many degrees of "minds". */
	static final float DEGREE_STEP = 1.0F;
	/** The tolerant (the kindhearted, cheerful, devout, and a share of the rest): lesser distaste vanishes, the worst (this high) is only halved. */
	static final int TOLERANT_KEEPS_AT = 3;
	static final float TOLERANT_SHARE = 0.5F;
	/** The first meeting: MCA hearts taken off per degree minded (MCA hearts run to 100 and beyond; 50 is trust). */
	static final int START_HEARTS_PER_DEGREE = 7;
	/** How often players are looked at (ticks), how far villagers are met, and the most players remembered per villager. */
	static final int TICK = 40;
	static final double MEET_RANGE = 8.0;
	static final int MET_MAX = 24;
	/** Glares: how often a player's surroundings are checked (ticks), the chance per check per point of distaste, how close, how far heard, how long between one villager's. */
	static final int GLARE_EVERY = 160;
	static final float GLARE_CHANCE = 0.12F;
	static final double GLARE_RANGE = 6.0, GLARE_HEARD = 16.0;
	static final int GLARE_REST = 20 * 60, GLARE_FROM = 2;
	/** Lines ({@code living.jaymoskingdoms.glare.<orc|goblin|folk>.<n>}, from 0). */
	static final int GLARE_LINES = 5;
	/** Brawls: the chance a sour meeting between two who despise each other (distaste at least BRAWL_FROM) goes straight to blows, per point of distaste. */
	static final float BRAWL_CHANCE = 0.12F;
	static final int BRAWL_FROM = 2;
	/** Homes: the distaste at which neither will sleep under the other's roof, how often it's checked (ticks), how long a greenskin waits to look again. */
	static final int APART_FROM = 3, APART_EVERY = 20 * 30, APART_REST = 20 * 60 * 5;
	/** (0.7.490) Two beds this near (same dimension) are one house: MCA's home is each villager's own bed, never shared. */
	static final double SAME_HOUSE = 7.0;
	/** (0.7.490) How far from a greenskin a free bed (under another roof) must be for it to leave home: MCA looks this near when it seeks one. */
	static final int APART_SEEK = 8;
	// ================================================================================================================

	/**
	 * The peoples the table speaks of: ((0.7.490) each blood its own row and column) {@code HUMAN} the common folk
	 * (Heartlanders, Earthlings, no blood); {@code OTHER} only a fallback for a blood not named here.
	 */
	enum People {
		HUMAN, TIDEBORN, STONEBORN, WARRIORBORN, SYLVAN, BIOBUILT, VERDANT, EMBERBORN, FROSTBORN, NIGHTBORN, DAWNBORN,
		STORMBORN, ENDBORN, DRAGONBLOOD, DEEPBORN, DIVINE, RUBBERFOLK, OTHER, ORC, GOBLIN, BEASTFOLK, HOLLOW
	}

	/**
	 * Row {@code who}, column {@code whom}: how much the one holds the other against them (0 to 3). Order as {@link People}:
	 * human, Tideborn, Stoneborn, Warrior-born, Sylvan, Bio-built, Verdant, Emberborn, Frostborn, Nightborn, Dawnborn,
	 * Stormborn, Endborn, Dragonblood, Deepborn, Divine-born, Rubberfolk, other, orc, goblin, (0.7.490) Beastfolk, Hollow. Only the greenskin rows and
	 * columns are used by {@link Prejudice#minds} (the rest of it keeps Prejudice's own degrees); glares and brawls use all
	 * of it. Every people minds every other at least a little (1); orcs despise goblins (3); goblins fear and resent orcs (3).
	 */
	static final int[][] TABLE = {
		// human: orcs 3, goblins 2 (the Tideborn to Bio-built columns are Prejudice's degrees)
		{0, 3, 3, 2, 1, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 2, 2, 3},
		// Tideborn: fire and sea
		{1, 0, 1, 1, 1, 2, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 1, 2},
		// Stoneborn: kin in craft to the orcs, scornful of goblins
		{1, 1, 0, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2},
		// Warrior-born: respect an orc's strength
		{1, 1, 1, 0, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2},
		// Sylvan: fear fire
		{1, 1, 1, 1, 0, 2, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 1, 2},
		// Bio-built
		{1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2},
		// Verdant: fear fire; a little kin to the goblins in their green
		{1, 1, 1, 1, 1, 2, 0, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2},
		// Emberborn: fire minds water and frost
		{1, 2, 1, 1, 1, 2, 1, 0, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2},
		// Frostborn: frost minds fire
		{1, 1, 1, 1, 1, 2, 1, 2, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2},
		// Nightborn: the dawn's opposite
		{1, 1, 1, 1, 1, 2, 1, 1, 1, 0, 2, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1},
		// Dawnborn: the night's opposite
		{1, 1, 1, 1, 1, 2, 1, 1, 1, 2, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 3},
		// Stormborn
		{1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2},
		// Endborn
		{1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 2, 1, 1},
		// Dragonblood: proud over the common folk, goblins vermin
		{2, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 3, 1, 2},
		// Deepborn
		{1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 2, 1, 1},
		// Divine-born: wary of the End's and the deep's, and of orcs
		{1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2, 1, 2, 0, 1, 1, 2, 2, 1, 3},
		// Rubberfolk
		{1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 2, 1, 2},
		// other (a fallback for a blood not named here: a little of everything)
		{1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 2},
		// orc: despises goblins; thinks humans soft, the pale and the strange worse; respects a dragon
		{1, 1, 1, 0, 2, 2, 1, 1, 1, 1, 1, 1, 2, 0, 1, 1, 2, 1, 0, 3, 1, 1},
		// goblin: fears and resents orcs, dreads dragons; the big folk are all to be wary of
		{1, 1, 1, 2, 1, 2, 1, 2, 1, 1, 1, 1, 1, 3, 1, 2, 1, 1, 3, 0, 1, 1},
		// (0.7.490) Beastfolk: the folk with ears and tails mind the pale dead a little more
		{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 2},
		// Hollow: the risen dead mind the sun's and the afterlife's folk
		{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 0}
	};

	/** On a villager: the players (UUIDs) whose first meeting has been felt, so hearts start lower only once. */
	static final AttachmentType<List<String>> MET = AttachmentRegistry.create(JaymosKingdoms.id("peoples_met"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	private static final Map<UUID, Long> GLARED = new HashMap<>(), APARTED = new HashMap<>();
	/** (0.7.490) The bed each greenskin last gave up: back under that roof again, there was no better, so it stays. */
	private static final Map<UUID, net.minecraft.core.GlobalPos> LEFT = new HashMap<>();

	private Peoples() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Peoples::tickAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GLARED.clear();
			APARTED.clear();
			LEFT.clear();
		});
		Settlers.register();
	}

	// ---- the table ------------------------------------------------------------------------------------------------

	/** The people {@code kind} is of (null: no blood, taken for human). */
	static People of(Heritage.@Nullable Kind kind) {
		if (kind == null || VillagerHeritage.common(kind)) {
			return People.HUMAN;
		}
		return switch (kind) {
			case TIDEBORN -> People.TIDEBORN;
			case STONEBORN -> People.STONEBORN;
			case WARRIORBORN -> People.WARRIORBORN;
			case SYLVAN -> People.SYLVAN;
			case BIOBUILT -> People.BIOBUILT;
			case VERDANT -> People.VERDANT;
			case EMBERBORN -> People.EMBERBORN;
			case FROSTBORN -> People.FROSTBORN;
			case NIGHTBORN -> People.NIGHTBORN;
			case DAWNBORN -> People.DAWNBORN;
			case STORMBORN -> People.STORMBORN;
			case ENDBORN -> People.ENDBORN;
			case DRAGONBLOOD -> People.DRAGONBLOOD;
			case DEEPBORN -> People.DEEPBORN;
			case DIVINE -> People.DIVINE;
			case RUBBERFOLK -> People.RUBBERFOLK;
			case ORC -> People.ORC;
			case GOBLIN -> People.GOBLIN;
			case BEASTFOLK -> People.BEASTFOLK;
			case HOLLOW -> People.HOLLOW;
			default -> People.OTHER;
		};
	}

	/** How much people {@code who} holds people {@code whom} against them (0 to 3). */
	static int distaste(People who, People whom) {
		return TABLE[who.ordinal()][whom.ordinal()];
	}

	/** How much a {@code who} holds a {@code whom} against them (0 to 3). */
	public static int distaste(Heritage.@Nullable Kind who, Heritage.@Nullable Kind whom) {
		return distaste(of(who), of(whom));
	}

	/** Either is a goblin or an orc. */
	static boolean greenskin(Heritage.@Nullable Kind a, Heritage.@Nullable Kind b) {
		return a != null && a.brood || b != null && b.brood;
	}

	/**
	 * (From {@link Prejudice#minds}, when a greenskin is on either side) how much {@code who} (of blood {@code mine}) holds
	 * {@code theirs} against them, as Prejudice's degrees: the table's distaste, the tolerant keeping only the worst of it,
	 * and a ruler's edict {@code edict} (a share) easing it.
	 */
	static float minds(LivingEntity who, Heritage.@Nullable Kind mine, Heritage.@Nullable Kind theirs, float edict) {
		int d = distaste(mine, theirs);
		if (d <= 0) {
			return 0.0F;
		}
		if (Prejudice.tolerant(who)) {
			return d < TOLERANT_KEEPS_AT ? 0.0F : d * DEGREE_STEP * TOLERANT_SHARE * edict;
		}
		return d * DEGREE_STEP * edict;
	}

	// ---- watching -------------------------------------------------------------------------------------------------

	private static void tickAll(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (!McaCompat.LOADED || now % TICK != 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !p.isAlive()) {
				continue;
			}
			meet(level, p);
			if (now % GLARE_EVERY == 0) {
				glare(level, p, now);
			}
			if (now % APART_EVERY == 0) {
				apart(level, p, now);
			}
		}
	}

	/** Villagers near {@code p} who haven't met them yet and mind their blood start with fewer hearts. */
	private static void meet(ServerLevel level, ServerPlayer p) {
		if (Prejudice.masked(p)) {
			return;
		}
		String id = p.getUUID().toString();
		for (Villager v : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(MEET_RANGE),
			x -> x.isAlive() && !x.isBaby() && McaCompat.isMca(x))) {
			List<String> met = v.getAttached(MET);
			// ((0.7.490) one who can't see who they are (a mask they don't see through) hasn't truly met them: MCA's hearts
			// wouldn't move, so leave the first meeting for when they do)
			if (met != null && met.contains(id) || Identities.hiddenFrom(p, v)) {
				continue;
			}
			// ((0.7.490) only a true first meeting: one who already has MCA memories of them (an old friend from before
			// this patch, or one pushed out of a full MET list) is only noted, never cooled)
			boolean known = McaCompat.knows(v, p);
			float m = known ? 0.0F : Prejudice.minds(v, p);
			if (m <= 0.0F && !known) {
				continue;
			}
			List<String> next = new ArrayList<>(met == null ? List.of() : met);
			next.add(id);
			while (next.size() > MET_MAX) {
				next.remove(0);
			}
			v.setAttached(MET, next);
			if (m > 0.0F) {
				McaCompat.hearts(v, p, -Math.round(START_HEARTS_PER_DEGREE * m));
			}
		}
	}

	// ---- glares and insults ---------------------------------------------------------------------------------------

	/** Two villagers near {@code p}, of peoples that dislike each other, mutter at one another. */
	private static void glare(ServerLevel level, ServerPlayer p, long now) {
		RandomSource random = level.getRandom();
		List<Villager> near = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(GLARE_HEARD),
			x -> x.isAlive() && !x.isBaby() && !x.isSleeping() && McaCompat.isMca(x) && VillagerHeritage.of(x) != null);
		if (near.size() < 2) {
			return;
		}
		Villager a = near.get(random.nextInt(near.size()));
		if (now - GLARED.getOrDefault(a.getUUID(), Long.MIN_VALUE / 2) < GLARE_REST || a.getTarget() != null) {
			return;
		}
		Heritage.Kind mine = VillagerHeritage.of(a);
		for (Villager b : near) {
			Heritage.Kind theirs = VillagerHeritage.of(b);
			if (b == a || b.distanceTo(a) > GLARE_RANGE || theirs == null || mine == theirs) {
				continue;
			}
			// ((0.7.490) as Prejudice sees it: the tolerant and a ruler's edicts ease it, and a haven that lives alongside
			// the tribes minds no greenskin)
			float d = glareMinds(level, a, mine, theirs);
			if (d < GLARE_FROM * DEGREE_STEP || random.nextFloat() >= GLARE_CHANCE * d / DEGREE_STEP) {
				continue;
			}
			GLARED.put(a.getUUID(), now);
			a.getLookControl().setLookAt(b, 30.0F, 30.0F);
			String voice = mine == Heritage.Kind.ORC ? "orc" : mine == Heritage.Kind.GOBLIN ? "goblin" : "folk";
			Speech.say(level, a, Component.translatable("living.jaymoskingdoms.glare." + voice + "." + random.nextInt(GLARE_LINES), Purists.name(theirs),
				b.getName()), Speech.YELLOW, GLARE_HEARD);
			return;
		}
	}

	/** (0.7.490) How much {@code a} (of blood {@code mine}) minds a neighbour of blood {@code theirs}, for a glare: {@link #minds}, with Prejudice's haven exemption. */
	private static float glareMinds(ServerLevel level, Villager a, Heritage.Kind mine, Heritage.Kind theirs) {
		if (!mine.brood && theirs.brood && Havens.greenskin(level.getServer(), Havens.home(a))) {
			return 0.0F;
		}
		return minds(a, mine, theirs, mine.brood ? 1.0F : Prejudice.edict(a));
	}

	// ---- brawls ---------------------------------------------------------------------------------------------------

	/**
	 * (From {@link Neighbours#choose}) whether {@code a} and {@code b}, meeting sourly, go straight to blows for what
	 * their peoples think of each other; hotheads sooner ({@link Temperaments#escalation}).
	 */
	static boolean brawls(Villager a, Villager b, RandomSource random) {
		Heritage.Kind x = VillagerHeritage.of(a), y = VillagerHeritage.of(b);
		int d = Math.max(distaste(x, y), distaste(y, x));
		if (x == y || d < BRAWL_FROM) {
			return false;
		}
		return random.nextFloat() < BRAWL_CHANCE * d * Math.max(Temperaments.escalation(a), Temperaments.escalation(b));
	}

	// ---- homes ----------------------------------------------------------------------------------------------------

	/** Greenskins near {@code p} who share a house with someone they despise (or are despised by) give it up and look for another. */
	private static void apart(ServerLevel level, ServerPlayer p, long now) {
		List<Villager> near = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(GLARE_HEARD * 2),
			x -> x.isAlive() && McaCompat.isMca(x) && McaCompat.home(x).isPresent());
		for (Villager g : near) {
			Heritage.Kind mine = Greenskins.of(g);
			if (mine == null || now - APARTED.getOrDefault(g.getUUID(), Long.MIN_VALUE / 2) < APART_REST) {
				continue;
			}
			java.util.Optional<net.minecraft.core.GlobalPos> home = McaCompat.home(g);
			// ((0.7.490) MCA gave them a bed under the roof they left: there was no other, so they put up with it)
			if (sameHouse(home, java.util.Optional.ofNullable(LEFT.get(g.getUUID())))) {
				continue;
			}
			for (Villager h : near) {
				// ((0.7.490) by house, not by bed: each bed holds one, so beds a few steps apart are one roof)
				if (h == g || !sameHouse(McaCompat.home(g), McaCompat.home(h)) || McaCompat.kin(g, h) != McaCompat.STRANGERS) {
					continue;
				}
				Heritage.Kind theirs = VillagerHeritage.of(h);
				if (mine != theirs && Math.max(distaste(mine, theirs), distaste(theirs, mine)) >= APART_FROM) {
					APARTED.put(g.getUUID(), now);
					// ((0.7.490) only for a free bed under another roof nearby, or MCA would just hand back the one let go)
					if (freeBedElsewhere(level, g, home)) {
						LEFT.put(g.getUUID(), home.get());
						McaCompat.leaveHome(g);
					}
					break;
				}
			}
		}
	}

	/** (0.7.490) A bed with room near {@code g}, in their home's dimension but not under its roof (within {@link #APART_SEEK}). */
	private static boolean freeBedElsewhere(ServerLevel level, Villager g, java.util.Optional<net.minecraft.core.GlobalPos> home) {
		if (home.isEmpty() || !home.get().dimension().equals(level.dimension())) {
			return false;
		}
		return level.getPoiManager().findAll(type -> type.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
			bed -> !bed.closerThan(home.get().pos(), SAME_HOUSE), g.blockPosition(), APART_SEEK,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE).findAny().isPresent();
	}

	/** (0.7.490) Beds {@code a} and {@code b} lie under one roof: the same dimension, within {@link #SAME_HOUSE}. */
	private static boolean sameHouse(java.util.Optional<net.minecraft.core.GlobalPos> a, java.util.Optional<net.minecraft.core.GlobalPos> b) {
		return a.isPresent() && b.isPresent() && a.get().dimension().equals(b.get().dimension())
			&& a.get().pos().closerThan(b.get().pos(), SAME_HOUSE);
	}
}
