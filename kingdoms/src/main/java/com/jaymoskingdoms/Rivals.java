package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.jaymoskingdoms.nether.Hornkin;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.614) Rivals patch 0 (docs/Rivals-Plan.md; ideas from Shadow of War, a commercial game: our own names, code and lines,
 * never called by that game's name). The captains' ladders ({@link Captains}) were the goblin and orc tribes' alone; this is
 * what any side needs to keep one, so every side that fights you has its own named captains who remember you, climb when they
 * kill you and come back scarred.
 * <ul>
 * <li><b>Sides with camps</b> keep a ladder per region ({@link Tribes#REGION}) and post their captains to their camps: the
 * goblin and orc tribes (unchanged: their ladder key is still the tribe's), the barbarians' warbands, the illagers' hosts
 * and the pirates' coves. A ladder's key is {@code <side>@<region x>,<region z>}.</li>
 * <li><b>Sides without</b> raise their captains from what walks their land: the risen dead of the Overworld (zombies and
 * skeletons, the raised, all of them Deathless) and the Hornkin of the Nether. A fitting one who comes near a player may rise
 * as a captain while the region's ladder is short ({@link #ROAM_CHANCE}); a fallen captain due back takes the next one
 * ({@link Captains#waiting}). Nothing new is built for them: no new realm.</li>
 * <li><b>The world's tier:</b> as the Aether's Giants raise the world's floor ({@link WorldTiers#floor}), ladders grow
 * longer, captains rise higher and start stronger ({@link #ladder}, {@link #rankMax}, {@link #tierHealth}).</li>
 * <li><b>Temperament:</b> each captain keeps the temperament he first had ({@link #TEMPERS}), whatever body he's in after;
 * it picks some of his lines and some of what he does (in {@link Captains}).</li>
 * <li><b>Their lines:</b> the new sides speak a plain tongue ({@code dialect.jaymoskingdoms.common.*}), each in its own voice
 * for the lines that carry it ({@link #VOICED}); orcs and goblins keep their dialects ({@link Dialects}).</li>
 * </ul>
 * Turning captains (Valor, In-balming) is left to those who'd make a man's oath: orcs, goblins, barbarians and pirates
 * ({@link Side#swears}). Illagers, the raised and the Hornkin refuse.
 */
public final class Rivals {
	// ================================================================================================================
	// Tuning
	/** A fitting monster near a player rises as a captain at this chance when its region's ladder is short; how near. */
	static final float ROAM_CHANCE = 0.03F;
	static final double ROAM_NEAR = 64.0;
	/** The world's floor (0 Hearth to 4 Legend): every this many floors, one more captain on a ladder, one rank higher at most and at the start, one more strength. */
	static final int TIER_STEP = 2;
	/** And on every floor: health times one more this much, blows this much harder. */
	static final double TIER_HEALTH = 0.2, TIER_DAMAGE = 1.0;
	/** How often a captain says his temperament's line instead of the usual one (first meetings, gloats). */
	static final float TEMPER_LINE = 0.5F;
	// ================================================================================================================

	/** On a monster that's been weighed once to rise as a captain. */
	static final String ROLLED_TAG = "jaymoskingdoms.rival_rolled";
	/** The dialect the new sides share. */
	static final String COMMON = "common";
	/** The lines each side says in its own voice (the rest are {@link #COMMON}): the last part of the key before its number. */
	static final List<String> VOICED = List.of("first", "gloat", "rally", "cheer", "climbed", "risen", "threat");

	/** On the Overworld: captain id → his temperament's key. Saved. */
	static final AttachmentType<Map<String, String>> TEMPERS = AttachmentRegistry.create(JaymosKingdoms.id("captain_tempers"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	/** A side that keeps a ladder of captains. */
	enum Side {
		GOBLIN(Tribes.GOBLIN, true, true),
		ORC(Tribes.ORC, true, true),
		BARBARIAN("barbarian", true, true),
		ILLAGER("illager", true, false),
		PIRATE("pirate", true, true),
		RAISED("raised", false, false),
		HORNKIN("hornkin", false, false),
		// ((0.7.619, patch 4) a player's Bane, one ladder each: bane_<kind>@<their id>, Banes)
		BANE_KNIGHT("bane_knight", false, false),
		BANE_WARLORD("bane_warlord", false, false),
		BANE_DEMON("bane_demon", false, false);

		/** Its ladder keys start with this (and {@code @}); its dialect's name. */
		final String key;
		/** Its captains are posted to camps (else they rise from what walks the land). */
		final boolean camps;
		/** Its captains can be turned. */
		final boolean swears;

		Side(String key, boolean camps, boolean swears) {
			this.key = key;
			this.camps = camps;
			this.swears = swears;
		}

		boolean tribe() {
			return this == GOBLIN || this == ORC;
		}

		/** (0.7.619) One player's Bane ({@link Banes}). */
		boolean bane() {
			return this == BANE_KNIGHT || this == BANE_WARLORD || this == BANE_DEMON;
		}

		/** (0.7.619) The side whose voice its captain lines use: a Bane's nearest kin (the raised, the barbarians, the Hornkin). */
		Side voice() {
			return switch (this) {
				case BANE_KNIGHT -> RAISED;
				case BANE_WARLORD -> BARBARIAN;
				case BANE_DEMON -> HORNKIN;
				default -> this;
			};
		}

		static @Nullable Side byKey(@Nullable String key) {
			for (Side s : values()) {
				if (s.key.equals(key)) {
					return s;
				}
			}
			return null;
		}
	}

	private static final String[] ORC_BYNAMES = {"the Cleaver", "Gut-Ripper", "the Hound", "Flesh-Eater", "the Slaver", "Black-Tongue", "the Drowner",
		"Ear-Taker", "the Lash", "Stone-Fist", "the Mad", "Rot-Breath", "the Tracker", "Neck-Snapper", "the Proud", "Pit-Born"};
	private static final String[] GOBLIN_BYNAMES = {"the Sneak", "Knife-Licker", "the Rat", "Bone-Picker", "the Twitch", "Snot-Nose", "the Poisoner",
		"Purse-Cutter", "the Gnawer", "Wobble-Eye", "the Cackler", "Trap-Maker"};
	private static final String[] BARBARIAN_NAMES = {"Brannoc", "Hask", "Ulfgar", "Torvik", "Gorm", "Rask", "Vigar", "Hrolf", "Ketil", "Sorn", "Bodvar",
		"Agnar", "Tharn", "Drest", "Calder"};
	private static final String[] BARBARIAN_BYNAMES = {"the Red", "Skull-Splitter", "the Wolf", "Bone-Breaker", "the Burner", "Iron-Hand", "the Howler",
		"Crow-Feeder", "the Grim", "Oath-Breaker", "the Tall", "Ash-Beard"};
	private static final String[] ILLAGER_NAMES = {"Varrow", "Mordekin", "Hesk", "Ilgrim", "Corvane", "Talmus", "Ressik", "Orvel", "Grimbald", "Saltan"};
	private static final String[] ILLAGER_BYNAMES = {"the Banner-Bearer", "the Taxman", "Chain-Maker", "the Pale", "Village-Burner", "the Quiet",
		"Bolt-Thrower", "the Collector", "Grey-Cloak", "the Hangman"};
	private static final String[] PIRATE_NAMES = {"Silas", "Jory", "Brack", "Dunstan", "Ezra", "Corwin", "Gideon", "Tobiah", "Morrow", "Quill", "Hadley",
		"Rourke"};
	private static final String[] PIRATE_BYNAMES = {"Saltjaw", "the Gull", "Ninefingers", "Black-Tooth", "the Drowned", "Powder-Hand", "the Hook",
		"the Shark", "Rum-Eye", "Keelhauler", "the Gentleman", "Cannon-Belly"};
	private static final String[] RAISED_NAMES = {"Sir Aldous", "Sir Bertrand", "Sir Cedric", "Sir Edmund", "Sir Godric", "Sir Osric", "Sir Wystan",
		"Sir Leofric", "Sir Hereward", "Sir Ansel"};
	private static final String[] RAISED_BYNAMES = {"the Hollow", "Grave-Born", "the Unburied", "Rot-Knight", "the Pale", "Worm-Crowned", "the Weeping",
		"Bone-Lord", "the Cold", "Ash-Eyed"};
	private static final String[] HORNKIN_NAMES = {"Gorrmak", "Brumm", "Tavrok", "Ozzug", "Kragh", "Murn", "Thurl", "Vorgash", "Hrund", "Baldok"};
	private static final String[] HORNKIN_BYNAMES = {"the Ash-Treader", "Gold-Horn", "the Gorer", "Cinder-Hoof", "the Bellower", "Iron-Ring",
		"the Trampler", "Ember-Hide"};
	/** The two halves of a ladder's name, by side (barbarian, illager, pirate, raised, hornkin). */
	private static final String[][] FIRST = {{"Red", "Black", "Iron", "Ash", "Wolf", "Crow", "Bone", "Storm"}, {"Grey", "Pale", "Ashen", "Bitter", "Hollow"},
		{"Salt", "Black", "Red", "Brine", "Gull"}, {"Barrow", "Grave", "Mourn", "Cold", "Ash"}, {"Cinder", "Ash", "Gold", "Ember", "Brim"}};
	private static final String[][] SECOND = {{"hand", "axe", "fang", "skull", "fire", "shield", "tooth"}, {"banner", "tithe", "chain", "crown", "shroud"},
		{"jack", "flag", "hook", "tide", "anchor"}, {"march", "field", "wood", "hill", "mere"}, {"horn", "hoof", "hide", "ring"}};

	private Rivals() {
	}

	// ---- sides and ladders ---------------------------------------------------------------------------------------------

	/** The side whose ladder {@code ladder} is (the goblins' for anything unknown). */
	static Side side(String ladder) {
		int at = ladder.indexOf('@');
		Side s = Side.byKey(at < 0 ? ladder : ladder.substring(0, at));
		return s == null ? Side.GOBLIN : s;
	}

	/** {@code ladder} is a goblin or orc tribe's. */
	static boolean tribe(String ladder) {
		return side(ladder).tribe();
	}

	/** The ladder of {@code side} at {@code at}. */
	static String key(Side side, BlockPos at) {
		return side.tribe() ? Tribes.key(side.key, at) : side.key + "@" + Math.floorDiv(at.getX(), Tribes.REGION) + "," + Math.floorDiv(at.getZ(), Tribes.REGION);
	}

	/** Camp {@code camp}'s ladder: its tribe, or a barbarian camp's, an illager camp's or a pirate cove's; null for any other or a gone one. */
	public static @Nullable String of(MinecraftServer server, int camp) {
		String tribe = Tribes.of(server, camp);
		if (tribe != null) {
			return tribe;
		}
		Faction f = camp < 0 ? null : Factions.get(server, camp);
		Camps.CampData data = f == null || f.type() != FactionType.BARBARIAN ? null : Camps.data(server, camp);
		if (data == null) {
			return null;
		}
		Side s = switch (data.kind()) {
			case Camps.BARBARIAN -> Side.BARBARIAN;
			case IllagerCamps.KIND -> Side.ILLAGER;
			case PirateCoves.KIND -> Side.PIRATE;
			default -> null;
		};
		return s == null ? null : key(s, f.seat());
	}

	/** The ladder {@code e} belongs to: his own if he's a captain, else his camp's; null for anyone else. */
	public static @Nullable String of(Entity e) {
		MinecraftServer server = e.level().getServer();
		if (server == null) {
			return null;
		}
		String tribe = Tribes.of(e);
		if (tribe != null) {
			return tribe;
		}
		if (Captains.is(e)) {
			Captain c = Captains.of(e);
			if (c != null) {
				return c.tribe();
			}
		}
		String bane = Banes.followerOf(e);
		if (bane != null) {
			// ((0.7.619) one of a Bane's band)
			return bane;
		}
		int camp = faction(e);
		return camp < 0 ? null : of(server, camp);
	}

	/** The camp (faction id) {@code e} is one of: a worker's, a camp illager's; −1 for anything else. */
	static int faction(Entity e) {
		if (e instanceof Worker w) {
			return w.factionId();
		}
		Illagers.Tag tag = Illagers.tag(e);
		return tag == null ? -1 : tag.camp();
	}

	/** The side {@code e} would rise for if it rose as a captain from the land: the risen dead, the Hornkin; null for anything else. */
	static @Nullable Side roamer(Entity e) {
		if (Banes.followerOf(e) != null) {
			// ((0.7.619) a Bane's own never rise for another ladder)
			return null;
		}
		if (e instanceof Hornkin h) {
			return h.kind() == Hornkin.Kind.WARRIOR || h.kind() == Hornkin.Kind.BRUTE ? Side.HORNKIN : null;
		}
		if ((e instanceof Zombie z && !z.isBaby() || e instanceof AbstractSkeleton) && e.level().dimension() == Level.OVERWORLD && !Thralls.isThrall(e)) {
			return Side.RAISED;
		}
		return null;
	}

	/** {@code ladder}'s standing camps, oldest first (the first is its seat); none for a side without camps. */
	static List<Faction> camps(MinecraftServer server, String ladder) {
		Side s = side(ladder);
		if (s.tribe()) {
			return Tribes.camps(server, ladder);
		}
		List<Faction> out = new ArrayList<>();
		if (!s.camps) {
			return out;
		}
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = f.fallen() ? null : Camps.data(server, f.id());
			if (data != null && data.clearedAt() < 0 && ladder.equals(of(server, f.id()))) {
				out.add(f);
			}
		}
		out.sort(java.util.Comparator.comparingLong(f -> {
			Camps.CampData d = Camps.data(server, f.id());
			return d == null ? Long.MAX_VALUE : d.founded();
		}));
		return out;
	}

	/** {@code ladder}'s seat: its oldest standing camp, or null. */
	static @Nullable Faction seat(MinecraftServer server, String ladder) {
		List<Faction> camps = camps(server, ladder);
		return camps.isEmpty() ? null : camps.getFirst();
	}

	/** The ladder's name: the tribe's, or "the Crowfang warband", "the Greybanner host", "the Saltjack crew", "the Gravefield dead", "the Cinderhorn herd". */
	static Component name(String ladder) {
		Side s = side(ladder);
		if (s.tribe()) {
			return Tribes.name(ladder);
		}
		if (s.bane()) {
			// ((0.7.619) a Bane's band)
			return Component.translatable("rival.jaymoskingdoms.name." + s.key);
		}
		int i = s.ordinal() - Side.BARBARIAN.ordinal();
		long h = ladder.hashCode() * 0x9E3779B97F4A7C15L;
		String word = FIRST[i][(int) Math.floorMod(h, (long) FIRST[i].length)] + SECOND[i][(int) Math.floorMod(h >>> 20, (long) SECOND[i].length)];
		return Component.translatable("rival.jaymoskingdoms.name." + s.key, word);
	}

	/** A byname for one of {@code ladder}'s. */
	static String byname(String ladder, RandomSource random) {
		String[] bynames = switch (side(ladder)) {
			case ORC -> ORC_BYNAMES;
			case GOBLIN -> GOBLIN_BYNAMES;
			case BARBARIAN -> BARBARIAN_BYNAMES;
			case ILLAGER -> ILLAGER_BYNAMES;
			case PIRATE -> PIRATE_BYNAMES;
			case RAISED -> RAISED_BYNAMES;
			case HORNKIN -> HORNKIN_BYNAMES;
			case BANE_KNIGHT, BANE_WARLORD, BANE_DEMON -> Banes.bynames(side(ladder));
		};
		return bynames[random.nextInt(bynames.length)];
	}

	/** A new captain's name: one of his kind's names and a byname. */
	static String rollName(String ladder, RandomSource random) {
		String[] names = switch (side(ladder)) {
			case ORC -> null;
			case GOBLIN -> null;
			case BARBARIAN -> BARBARIAN_NAMES;
			case ILLAGER -> ILLAGER_NAMES;
			case PIRATE -> PIRATE_NAMES;
			case RAISED -> RAISED_NAMES;
			case HORNKIN -> HORNKIN_NAMES;
			case BANE_KNIGHT, BANE_WARLORD, BANE_DEMON -> Banes.names(side(ladder));
		};
		String first = names == null ? Greenskins.name(side(ladder) == Side.ORC ? Heritage.Kind.ORC : Heritage.Kind.GOBLIN, random.nextLong())
			: names[random.nextInt(names.length)];
		return first + " " + byname(ladder, random);
	}

	// ---- their bodies -----------------------------------------------------------------------------------------------------

	/**
	 * {@code c} as one of {@code camp}'s, at it: a goblin, a grunt, a barbarian or a deckhand as an MCA villager (our Folk without
	 * MCA), an illager camp's as a vindicator; dressed for his rank. Null if nobody could be had (or a side without camps).
	 */
	static @Nullable PathfinderMob muster(ServerLevel level, Captain c, Faction camp, BlockPos center, BlockPos home, int radius) {
		Side s = side(c.tribe());
		RandomSource random = level.getRandom();
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
		if (s == Side.ILLAGER) {
			Mob mob = Illagers.spawn(level, camp, Illagers.VINDICATOR, at, home, radius, false);
			if (!(mob instanceof PathfinderMob p)) {
				return null;
			}
			gear(p, c.rank(), s);
			p.setCustomName(Component.literal(c.name()));
			return p;
		}
		if (!s.camps) {
			return null;
		}
		int role = switch (s) {
			case ORC -> Orcs.GRUNT;
			case GOBLIN -> Goblins.GOBLIN;
			case PIRATE -> Pirates.DECKHAND;
			default -> Folk.BARBARIAN;
		};
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return null;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		folk.setup(role, camp, home, radius);
		gear(folk, c.rank(), s);
		folk.setCustomName(Component.literal(c.name()));
		level.addFreshEntity(folk);
		PathfinderMob mob = McaCompat.raider(level, folk);
		return mob == null ? folk : mob;
	}

	/**
	 * A captain's gear by rank: his side's weapon (better from 3), mail from 2, iron from 4, iron legs at the top. The risen
	 * dead and the Hornkin keep the weapon they rose with; the dead wear a helmet from the first rank (the sun), the Hornkin no
	 * armour (their hide).
	 */
	static void gear(Mob mob, int rank, Side s) {
		if (s.bane()) {
			// ((0.7.619) a Bane's arms are its own, better at each rank)
			Banes.gear(mob, rank, s);
			return;
		}
		ItemStack weapon = switch (s) {
			case ORC, BARBARIAN, ILLAGER -> new ItemStack(rank >= 3 ? Items.IRON_AXE : Items.STONE_AXE);
			case GOBLIN, PIRATE -> new ItemStack(rank >= 3 ? Items.IRON_SWORD : Items.STONE_SWORD);
			default -> ItemStack.EMPTY;
		};
		if (!weapon.isEmpty()) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}
		if (s == Side.HORNKIN) {
			return;
		}
		if (rank >= 2) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(rank >= 4 ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET));
			mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(rank >= 4 ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
		} else if (s == Side.RAISED) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		}
		if (rank >= Captains.RANK_MAX) {
			mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		}
		if (s == Side.RAISED || s == Side.ILLAGER) {
			for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS)) {
				mob.setDropChance(slot, 0.0F);
			}
		}
	}

	/** {@code mob}'s own near: the same camp's (workers, its illagers), or for a side without camps, more of its kind. */
	static List<Mob> allies(ServerLevel level, Mob mob, double range) {
		String band = Banes.bandOf(mob);
		if (band != null) {
			// ((0.7.619) a Bane and its band)
			return level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(range), e -> e != mob && e.isAlive() && band.equals(Banes.bandOf(e)));
		}
		int camp = faction(mob);
		Side roams = camp < 0 ? roamer(mob) : null;
		return level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(range), e -> e != mob && e.isAlive()
			&& (camp >= 0 ? faction(e) == camp : roams != null && roamer(e) == roams));
	}

	/**
	 * (From {@code ENTITY_LOAD}) {@code e}, weighed once to rise as a captain from the land: a fallen captain of its region who is
	 * due back takes it; else, now and then while the ladder is short, a new one rises in it.
	 */
	static void loaded(Entity e, ServerLevel level) {
		Side s = roamer(e);
		if (s == null || !(e instanceof PathfinderMob mob) || Captains.is(e) || e.entityTags().contains(ROLLED_TAG)) {
			return;
		}
		if (level.getNearestPlayer(e, ROAM_NEAR) == null) {
			// (weighed only near a player: one far off is left to be weighed when one comes)
			return;
		}
		e.addTag(ROLLED_TAG);
		MinecraftServer server = level.getServer();
		String ladder = key(s, e.blockPosition());
		Captain waiting = Captains.waiting(server, ladder);
		if (waiting != null) {
			Captains.embodyIn(level, waiting, mob);
		} else if (level.getRandom().nextFloat() < ROAM_CHANCE && Captains.of(server, ladder).size() < ladder(server)) {
			Captains.riseFrom(level, ladder, mob);
		}
	}

	// ---- the world's tier ---------------------------------------------------------------------------------------------------

	private static int steps(MinecraftServer server) {
		return WorldTiers.floor(server) / TIER_STEP;
	}

	/** How many captains a ladder keeps, at the world's floor. */
	static int ladder(MinecraftServer server) {
		return Captains.LADDER + steps(server);
	}

	/** The most a ladder may have (kills make more). */
	static int ladderMax(MinecraftServer server) {
		return Captains.LADDER_MAX + steps(server);
	}

	/** The highest rank, at the world's floor. */
	static int rankMax(MinecraftServer server) {
		return Captains.RANK_MAX + steps(server);
	}

	/** A new captain's rank: 1 to 3, higher on a higher floor. */
	static int startRank(MinecraftServer server, RandomSource random) {
		return Math.min(rankMax(server), 1 + random.nextInt(3) + steps(server));
	}

	/** Strengths a new captain gets on top of his roll. */
	static int extraStrengths(MinecraftServer server) {
		return steps(server);
	}

	/** His health, times this, at the world's floor. */
	static double tierHealth(@Nullable MinecraftServer server) {
		return server == null ? 1.0 : 1.0 + TIER_HEALTH * WorldTiers.floor(server);
	}

	/** His blows, this much harder, at the world's floor. */
	static double tierDamage(@Nullable MinecraftServer server) {
		return server == null ? 0.0 : TIER_DAMAGE * WorldTiers.floor(server);
	}

	// ---- temperament ---------------------------------------------------------------------------------------------------------

	/** Captain {@code id}'s temperament (kept from the first body he had), or null if he hasn't been seen yet. */
	static Temperaments.@Nullable Temperament temper(MinecraftServer server, String id) {
		return Temperaments.Temperament.byKey(Factions.level(server).getAttachedOrElse(TEMPERS, Map.of()).get(id));
	}

	/** (From {@code Captains.bind}) {@code mob} is {@code c}: he keeps his temperament (or takes this body's, or one at random). */
	static void temper(ServerLevel level, Captain c, Entity mob) {
		MinecraftServer server = level.getServer();
		Temperaments.Temperament t = temper(server, c.id());
		if (t != null) {
			if (Temperaments.humanoid(mob)) {
				mob.setAttached(Temperaments.TEMPERAMENT, t.key());
			}
			return;
		}
		t = Temperaments.of(mob);
		if (t == null) {
			Temperaments.Temperament[] all = Temperaments.Temperament.values();
			t = all[level.getRandom().nextInt(all.length)];
		}
		Map<String, String> tempers = new HashMap<>(Factions.level(server).getAttachedOrElse(TEMPERS, Map.of()));
		tempers.put(c.id(), t.key());
		Factions.level(server).setAttached(TEMPERS, Map.copyOf(tempers));
	}

	/** {@code c} is {@code t}. */
	static boolean is(MinecraftServer server, Captain c, Temperaments.Temperament t) {
		return temper(server, c.id()) == t;
	}

	/** The key of {@code c}'s {@code what} line ({@code first}, {@code gloat}): now and then his temperament's own, else one of the usual. */
	static String line(MinecraftServer server, Captain c, String what, RandomSource random) {
		Temperaments.Temperament t = temper(server, c.id());
		String base = "talk.jaymoskingdoms.captain." + what + ".";
		return t != null && random.nextFloat() < TEMPER_LINE ? base + t.key() : base + random.nextInt(Captains.LINES);
	}

	// ---- their lines ---------------------------------------------------------------------------------------------------------

	/** The dialect a captain's line is in for {@code brood} ({@link Dialects#key}): a side's own voice, the common tongue, or as asked. */
	static String dialect(String brood, String key) {
		Side s = Side.byKey(brood);
		if (s == null || s.tribe()) {
			return brood;
		}
		// ((0.7.619) a Bane speaks in its kin's voice)
		s = s.voice();
		if (!key.contains("captain")) {
			// (only captains' lines are kept in the common tongue: anything else stays as it was)
			return Dialects.ORC;
		}
		int last = key.lastIndexOf('.');
		String number = last < 0 ? "" : key.substring(last + 1);
		if (!number.isEmpty() && number.chars().allMatch(Character::isDigit)) {
			String before = key.substring(0, last);
			String what = before.substring(before.lastIndexOf('.') + 1);
			if (VOICED.contains(what)) {
				return s.key;
			}
		}
		return COMMON;
	}

	/** The dialect of {@code e}'s side if it keeps a ladder and isn't a tribe, else null. */
	static @Nullable String dialectOf(@Nullable Entity e) {
		if (e == null) {
			return null;
		}
		String ladder = of(e);
		if (ladder == null && roamer(e) != null) {
			return roamer(e).key;
		}
		return ladder == null || tribe(ladder) ? null : side(ladder).key;
	}
}
