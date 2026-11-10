package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.money.PurseContents;
import com.jaymoskingdoms.remnants.Monoliths;
import com.jaymoskingdoms.whaling.Whaling;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.216) Andy: "take inspiration from Bannerlord's intro asking about your life into adulthood and use the answers to
 * craft your character's heritage, spawn scenario, power affinity and class". A new player's first steps after MCA's
 * look and name: six questions about the life they led before the game ({@link Question}), every word our own. Where
 * they were born decides their heritage; who raised them and what they did as a child give a point in an attribute each
 * (and the child leans their heritage's look and, if fate is left to choose, their power); what they did growing up is
 * their class; "then one day..." is the power that will awaken in them (Jaymo's Powers' affinity, still after its usual
 * wait); and what sets them on the road now is where they wake ({@link Start}). The story page reads it all back
 * before it's sealed, for life, like the heritage and class it replaces at the start.
 *
 * <p>A start that echoes the power (a caving trip gone wrong and the spider bite, a shipwreck and the deep's song, a
 * monolith and the storm; (0.7.223) a fall from the sky and Gravity, a burned home and Smoke, a frozen pass and Cryo, a
 * captive's escape and Z Fighter) counts as that power's calling: it wakes a day sooner. Lost in time wakes Chrono at once. The heritage and class screens still
 * open for a Writ of Rebirth and for old players who never had a story. MCA's own "where do you start" page is skipped
 * ({@code client/mixin/mca/McaDestinySkipMixin}): the last question is where you start.
 *
 * <p>(0.7.349) Six starts wake you on Jaymo's Powers' other worlds ({@link #OTHER_WORLDS}, made by its {@code ZFighterStarts}):
 * stranded on the Pale Moon, raised under the Verdant World's green sky (the Verdant-born's alone), escaped from Rustfall's
 * mines (+1 Might), a salvager at the Maw, raised by a demon master in the Nether (the Emberborn's alone), and back from the afterlife (which makes
 * you Divine-born). Till they first leave that world they respawn where they landed ({@link #STRANDED}).
 */
public final class LifeStory {
	/** A capital start: how far from the realm's seat (blocks). */
	static final int CITY_MIN = 10, CITY_MAX = 28;
	/** A banished start: how far from the realm's seat you wake (blocks), and what they think of you after. */
	static final int BANISHED_MIN = 320, BANISHED_MAX = 480, BANISHED_REPUTATION = -60, RIVAL_REPUTATION = 40;
	/** A noble family's realm, and a sworn start's: their goodwill to begin with (at least this). */
	static final int NOBLE_REPUTATION = 50, SWORN_REPUTATION = 60;
	/** Alone in the wild: how far from the world's spawn (blocks). */
	static final int WILD_MIN = 600, WILD_MAX = 1200;
	/** Drawn to a monolith: how far from its foot you wake (blocks). */
	static final int MONOLITH_MIN = 36, MONOLITH_MAX = 56;
	/** How far a village or a wreck is looked for (chunks), and how close to it you wake (blocks). */
	static final int STRUCTURE_SEARCH = 100, STRUCTURE_NEAR = 14;
	/** A caving trip gone wrong: columns tried, how deep a hollow must lie (at most this Y), and the bite (ticks of Poison). */
	static final int CAVE_TRIES = 64, CAVE_MAX_Y = 40, CAVE_RADIUS = 96, BITE_TICKS = 140;
	/** (Andy: "poisoned, wounded, and barely breathing") the health you wake with, and how long the Slowness and Weakness last (ticks). */
	static final float CAVE_HEALTH = 10.0F;
	static final int CAVE_WOUND_TICKS = 1200;
	/** (0.7.228) Unhurtable this long after a story puts you somewhere (a cave's spiders, a fall carried over). */
	static final int ARRIVAL_GRACE_TICKS = 100;
	/** Spots tried for a dry, open place to stand before giving up on one. */
	static final int GROUND_TRIES = 16;
	/** (0.7.607) New chunks one search for a place to stand may make (see {@link #reach}); made ones are free. */
	static final int NEW_CHUNKS = 6;
	/** (0.7.584) How far under a roof (blocks) a spot to stand indoors is looked for. */
	static final int INDOOR_DEPTH = 8;
	/**
	 * Tiv: "Every player should have their own start, it should search for another if they are already taken." A place a
	 * start puts you in (a village, a realm's seat, a camp, a wreck, a milestone, a monolith) is taken once someone else
	 * started within PLACE_APART blocks of it (a settlement's land), and nobody wakes within START_APART blocks of where
	 * someone else woke. Horizontal blocks.
	 */
	static final int PLACE_APART = 96, START_APART = 32;
	/**
	 * A taken wreck or farmhouse is looked past at most FREE_TRIES times, each from FREE_STEP blocks further off; with no
	 * free spot to wake near a start's place, one is looked for up to FREE_WIDEN blocks further out.
	 */
	static final int FREE_TRIES = 3, FREE_STEP = 480, FREE_WIDEN = 64;
	/** "Let fate decide" on the power: how often the childhood's power gets the affinity anyway. */
	static final float FATE_LEANS = 0.5F;
	/** Every start's kit: bread; a capital's few coins; a merchant's child's purse; a caver's torches. */
	// (0.7.498) the start purses are silver coins in a filled coin purse (1 old emerald = 1 silver)
	static final int KIT_BREAD = 4, CITY_SILVER = 2, MERCHANT_SILVER = 8, CAVE_TORCHES = 8;
	/** (0.7.381) The village start, the easiest: more bread on top of the kit's, and a few silver coins. */
	static final int VILLAGE_BREAD = 4, VILLAGE_SILVER = 4;
	/**
	 * (Andy) the starts that come with their class's gear, armour worn ("it wouldn't make sense to start as an unarmed royal
	 * guardsman"). (0.7.412) The evicted keep the tools of their trade.
	 */
	static final Set<Start> GEARED = Set.of(Start.SWORN, Start.BANISHED, Start.CITY, Start.CAVE, Start.VILLAGE, Start.EVICTED);
	/** The unarmed start's limb, one of these Jaymo's Powers part bits (right arm, left arm, right leg, left leg). */
	static final int[] LIMBS = {2, 4, 8, 16};
	/** (0.7.223) Fell from the sky: how high over the crater you wake (blocks), how long you drift (ticks of Slow Falling), the crater's size. */
	static final int SKYFALL_HEIGHT = 24, SKYFALL_TICKS = 600, CRATER_RADIUS = 4, CRATER_DEPTH = 2, SKYFALL_SHARDS = 3;
	/** (0.7.223) Snowed in on a pass: how far the frozen peaks are looked for (blocks) and in what steps, and the Slowness (ticks). */
	static final int PEAKS_SEARCH = 3200, PEAKS_STEP = 64, PEAKS_VERTICAL_STEP = 32, SNOWED_SLOW_TICKS = 900;
	/** (0.7.223) Your village burned: the ruin's half-width (blocks), and how many saved things you carry out. */
	static final int RUIN_HALF = 3, RUIN_SAVED = 3;
	/**
	 * (0.7.501) The burning village: how far off the ruin it stands (blocks), how deep its jigsaw runs (a vanilla village's is 6),
	 * and how far round its centre cobwebs are looked for and set alight (across, below, above).
	 */
	static final int BURNING_AWAY = 10, BURNING_DEPTH = 6, BURNING_REACH = 72, BURNING_BELOW = 12, BURNING_ABOVE = 24;
	/** (0.7.501) How long the smoke's nausea lasts on waking in the burning village (ticks). */
	static final int BURNED_NAUSEA_TICKS = 400;
	/** (0.7.223) Escaped from barbarians: how far from the camp's fire you wake (blocks), how long it hunts you (ticks: a day), and how far it sends its raiders after you (blocks). */
	static final int CAPTIVE_MIN = 24, CAPTIVE_MAX = 40, HUNT_TICKS = 24000, HUNT_RADIUS = 96, HUNT_EVERY = 40;
	/**
	 * (0.7.412) The hard roads. Still in chains: how far from the camp's fire you wake (blocks). Rock bottom: how far from the
	 * outlaw camp you wake (blocks, in sight but past its raiders' eyes), and the food you wake with (of 20). Sentenced: how cold
	 * the realm is to you (at most this). Hideout: how far from a camp's fire you wake (blocks). Cast out: how far outside a
	 * haven's gates you wake (blocks).
	 */
	static final int CHAINED_MIN = 3, CHAINED_MAX = 9;
	static final int ROCK_CAMP_MIN = 34, ROCK_CAMP_MAX = 48, ROCK_BOTTOM_FOOD = 3;
	static final int SENTENCED_REPUTATION = -30;
	static final int HIDEOUT_CAMP_MIN = 2, HIDEOUT_CAMP_MAX = 8;
	static final int CAST_OUT_MIN = 40, CAST_OUT_MAX = 64;
	/** (0.7.412) The purses (silver coins, in a coin purse) the hard roads come with: a sentenced prisoner's few coins, an evicted one's fuller purse, a hideout's cut. */
	static final int SENTENCED_SILVER = 4, EVICTED_SILVER = 6, HIDEOUT_SILVER = 3;
	/** (0.7.414) The kit roads: a trader's pocket money (silver coins), a pup's bones and a sworn recruit's manacles. */
	static final int TRADER_SILVER = 6, PUP_BONES = 4;
	/** (0.7.414) A trader wakes this far from the milestone's post (blocks). */
	static final int TRADER_NEAR_MIN = 2, TRADER_NEAR_MAX = 5;
	/** (0.7.228) A whaler's harpooner: the irons they sign on with. */
	static final int WHALER_HARPOONS = 8;
	/** (0.7.349) The starts on Jaymo's Powers' other worlds: the Pale Moon, the Verdant World, Rustfall, the Maw, the Nether's demon biomes, the afterlife. */
	static final Set<Start> OTHER_WORLDS = Set.of(Start.MOON, Start.VERDANT, Start.RUSTFALL, Start.MAW, Start.DEMON, Start.AFTERLIFE);
	/** (0.7.349) Rustfall's escapee grew up under its weight: a point in this. */
	static final Stats.Stat RUSTFALL_STAT = Stats.Stat.MIGHT;
	/**
	 * (0.7.417, Start Together) A party member who chose the same road (and realm) landed within this many ticks (10 minutes) is
	 * the one you wake beside; you wake this far (min, max, blocks) from where they woke.
	 */
	static final long TOGETHER_WINDOW_TICKS = 12000L;
	/** (0.7.417) How near a mate's landing must be to a den's floor for you to wake in that den beside them. */
	static final double DEN_MATE_RANGE = 12.0;
	static final int TOGETHER_MIN = 2, TOGETHER_MAX = 5, TOGETHER_TRIES = 24;
	/** (0.7.417) A moon-cursed road's landing: the wild, or the edge of a village (blocks out from it, min and max). */
	static final int MOON_CURSED_VILLAGE_MIN = 24, MOON_CURSED_VILLAGE_MAX = 40;
	/** (0.7.417, Andy) The heritages that can wake in the alchemist's lab. */
	static final Set<Heritage.Kind> LAB_KINDS = java.util.Collections.unmodifiableSet(java.util.EnumSet.of(Heritage.Kind.BIOBUILT,
		Heritage.Kind.RUBBERFOLK, Heritage.Kind.EMBERBORN, Heritage.Kind.STONEBORN, Heritage.Kind.SYLVAN));
	/** (0.7.443) The heritages that can take the greenskin road: those that pass for human (not the Tideborn, Stoneborn, Warrior-born, Sylvan, Bio-built, (0.7.490) Beastfolk or Hollow). */
	static final Set<Heritage.Kind> GREENSKIN_KINDS = java.util.Collections.unmodifiableSet(java.util.EnumSet.complementOf(java.util.EnumSet.of(
		Heritage.Kind.TIDEBORN, Heritage.Kind.STONEBORN, Heritage.Kind.WARRIORBORN, Heritage.Kind.SYLVAN, Heritage.Kind.BIOBUILT, Heritage.Kind.GOBLIN,
		Heritage.Kind.ORC, Heritage.Kind.BEASTFOLK, Heritage.Kind.HOLLOW)));
	/** A matching start takes this off the power's wait (ticks): a day. */
	static final int ECHO_TICKS = 24000;
	/** (0.7.451) "Let fate decide": how its pick is shown across the screen on waking (ticks to fade in, to stay, to fade out). */
	static final int FATE_TITLE_IN = 20, FATE_TITLE_STAY = 200, FATE_TITLE_OUT = 40;

	public static final String FATE = "fate";

	/** Where a story can start. {@code realm}: you name a realm for it. */
	public enum Start {
		CITY(false), BANISHED(true), CAVE(false), SHIPWRECK(false), SWORN(true), VILLAGE(false), MONOLITH(false), WILD(false), SKYFALL(false),
		TIMELOST(false), BURNED(false), SNOWED(false), CAPTIVE(false), WHALERS(false), UNARMED(false),
		// (0.7.412, Andy's picks from the Kenshi and Streets of Rogue study) the hard roads: chained among a camp's captives, starving and
		// maimed beside an outlaw camp, in a realm's cells, put out of a realm's streets, in an outlaw hideout, turned away by your own kind
		CHAINED(false), ROCK_BOTTOM(false), SENTENCED(true), EVICTED(true), HIDEOUT(false), CAST_OUT(false),
		// (0.7.443, Havens patch 7b) raised in an orc camp or a goblin village (GreenskinLife)
		GREENSKIN(false),
		// (0.7.414, the kit roads) a family blade someone wants back, a pack mule on a King's Road, a pup at heel, three poor hired hands
		HEIRLOOM(false), TRADER(false), PUP(false), NOBODIES(false),
		// (0.7.417, the powered roads) a hermit alchemist's lab (Powers), the Mad Tinkerer's bench (Powers), the moon's curse
		LAB(false), ANDROID(false), MOON_CURSED(false),
		// (0.7.349, Andy: "new starts, involving the other worlds") woken off the overworld by Jaymo's Powers ({@link #OTHER_WORLDS})
		MOON(false), VERDANT(false), RUSTFALL(false), MAW(false), DEMON(false), AFTERLIFE(false), FATE(false);

		public final boolean realm;

		Start(boolean realm) {
			this.realm = realm;
		}
	}

	/** (0.7.412) How hard a start is, shown on its card in the carousel. {@code VARIES}: fate's, which could be any. */
	public enum Mark {
		EASY, FAIR, HARD, VERY_HARD, CRUEL, VARIES;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** (0.7.412) A start's difficulty mark and the silver coins it starts with (not counting a merchant child's extra purse). */
	public record Terms(Mark mark, int purse) {
	}

	/**
	 * (0.7.412, Andy's picks: "difficulty and purse on every road") Every start's difficulty and starting purse, in one
	 * table: the village the easiest, rock bottom the hardest. The purse is what {@link #kit} gives in silver coins.
	 */
	static final java.util.Map<Start, Terms> TERMS = new java.util.EnumMap<>(Start.class);

	static {
		TERMS.put(Start.VILLAGE, new Terms(Mark.EASY, VILLAGE_SILVER));
		TERMS.put(Start.SWORN, new Terms(Mark.EASY, 0));
		TERMS.put(Start.CITY, new Terms(Mark.FAIR, CITY_SILVER));
		TERMS.put(Start.WILD, new Terms(Mark.FAIR, 0));
		TERMS.put(Start.MONOLITH, new Terms(Mark.FAIR, 0));
		TERMS.put(Start.SHIPWRECK, new Terms(Mark.FAIR, 0));
		TERMS.put(Start.SKYFALL, new Terms(Mark.FAIR, 0));
		TERMS.put(Start.WHALERS, new Terms(Mark.FAIR, 0));
		TERMS.put(Start.VERDANT, new Terms(Mark.FAIR, 0));
		TERMS.put(Start.BANISHED, new Terms(Mark.HARD, 0));
		TERMS.put(Start.CAVE, new Terms(Mark.HARD, 0));
		TERMS.put(Start.BURNED, new Terms(Mark.HARD, 0));
		TERMS.put(Start.SNOWED, new Terms(Mark.HARD, 0));
		TERMS.put(Start.UNARMED, new Terms(Mark.HARD, 0));
		TERMS.put(Start.DEMON, new Terms(Mark.HARD, 0));
		TERMS.put(Start.AFTERLIFE, new Terms(Mark.HARD, 0));
		TERMS.put(Start.EVICTED, new Terms(Mark.HARD, EVICTED_SILVER));
		TERMS.put(Start.SENTENCED, new Terms(Mark.HARD, SENTENCED_SILVER));
		TERMS.put(Start.HIDEOUT, new Terms(Mark.HARD, HIDEOUT_SILVER));
		TERMS.put(Start.CAPTIVE, new Terms(Mark.VERY_HARD, 0));
		TERMS.put(Start.TIMELOST, new Terms(Mark.VERY_HARD, 0));
		TERMS.put(Start.MOON, new Terms(Mark.VERY_HARD, 0));
		TERMS.put(Start.RUSTFALL, new Terms(Mark.VERY_HARD, 0));
		TERMS.put(Start.MAW, new Terms(Mark.VERY_HARD, 0));
		TERMS.put(Start.CHAINED, new Terms(Mark.VERY_HARD, 0));
		TERMS.put(Start.CAST_OUT, new Terms(Mark.VERY_HARD, 0));
		TERMS.put(Start.GREENSKIN, new Terms(Mark.HARD, 0));
		TERMS.put(Start.ROCK_BOTTOM, new Terms(Mark.CRUEL, 0));
		// (0.7.414) the kit roads
		TERMS.put(Start.HEIRLOOM, new Terms(Mark.HARD, 0));
		TERMS.put(Start.TRADER, new Terms(Mark.FAIR, TRADER_SILVER));
		TERMS.put(Start.PUP, new Terms(Mark.FAIR, 0));
		TERMS.put(Start.NOBODIES, new Terms(Mark.FAIR, 0));
		// (0.7.417) the powered roads
		TERMS.put(Start.LAB, new Terms(Mark.VERY_HARD, 0));
		TERMS.put(Start.ANDROID, new Terms(Mark.HARD, 0));
		TERMS.put(Start.MOON_CURSED, new Terms(Mark.HARD, 0));
		TERMS.put(Start.FATE, new Terms(Mark.VARIES, 0));
	}

	/**
	 * (0.7.605) Andy: "Let fate decide (random even from non unlocked! percentage table)". Fate's odds for every start, in
	 * percent (they add up to 100); a start fate can't give this player is left out and the rest share its odds.
	 */
	static final java.util.Map<Start, Integer> FATE_ODDS = new java.util.EnumMap<>(Start.class);

	static {
		FATE_ODDS.put(Start.VILLAGE, 8);
		FATE_ODDS.put(Start.CITY, 6);
		FATE_ODDS.put(Start.WILD, 6);
		FATE_ODDS.put(Start.SHIPWRECK, 6);
		FATE_ODDS.put(Start.BANISHED, 5);
		FATE_ODDS.put(Start.SWORN, 4);
		FATE_ODDS.put(Start.CAVE, 3);
		FATE_ODDS.put(Start.MONOLITH, 3);
		FATE_ODDS.put(Start.SKYFALL, 3);
		FATE_ODDS.put(Start.WHALERS, 3);
		FATE_ODDS.put(Start.TRADER, 3);
		FATE_ODDS.put(Start.PUP, 3);
		FATE_ODDS.put(Start.NOBODIES, 3);
		FATE_ODDS.put(Start.BURNED, 3);
		FATE_ODDS.put(Start.SNOWED, 3);
		FATE_ODDS.put(Start.HEIRLOOM, 3);
		FATE_ODDS.put(Start.SENTENCED, 2);
		FATE_ODDS.put(Start.EVICTED, 2);
		FATE_ODDS.put(Start.HIDEOUT, 2);
		FATE_ODDS.put(Start.UNARMED, 2);
		FATE_ODDS.put(Start.ROCK_BOTTOM, 2);
		FATE_ODDS.put(Start.CAPTIVE, 2);
		FATE_ODDS.put(Start.CHAINED, 2);
		FATE_ODDS.put(Start.TIMELOST, 2);
		FATE_ODDS.put(Start.MOON_CURSED, 2);
		FATE_ODDS.put(Start.ANDROID, 2);
		FATE_ODDS.put(Start.LAB, 2);
		FATE_ODDS.put(Start.GREENSKIN, 2);
		FATE_ODDS.put(Start.CAST_OUT, 2);
		FATE_ODDS.put(Start.VERDANT, 2);
		FATE_ODDS.put(Start.DEMON, 2);
		FATE_ODDS.put(Start.AFTERLIFE, 2);
		FATE_ODDS.put(Start.MOON, 1);
		FATE_ODDS.put(Start.RUSTFALL, 1);
		FATE_ODDS.put(Start.MAW, 1);
	}

	/** (0.7.412) {@code start}'s difficulty and purse. */
	public static Terms terms(Start start) {
		return TERMS.getOrDefault(start, new Terms(Mark.FAIR, 0));
	}

	/**
	 * One answer: its key, its picture, and what it does. {@code heritage}: question 1's; {@code stat}: a point in it
	 * (null on a "points" answer: a free point instead, the foundling's); {@code cls}: question 4's; {@code power}: an id
	 * path of Jaymo's Powers (question 5's, or the childhood's lean); {@code looks}: the heritage looks it leans to (the
	 * first that is the born heritage's is pre-picked); {@code start}: question 6's; {@code realm}: you name a realm for it.
	 */
	public record Answer(String key, Item icon, Heritage.@Nullable Kind heritage, Stats.@Nullable Stat stat, boolean freePoint,
		Classes.@Nullable PlayerClass cls, String power, List<String> looks, @Nullable Start start, boolean realm,
		Set<Heritage.Kind> only, Set<Heritage.Kind> never) {

		Answer(String key, Item icon, Heritage.@Nullable Kind heritage, Stats.@Nullable Stat stat, boolean freePoint,
			Classes.@Nullable PlayerClass cls, String power, List<String> looks, @Nullable Start start, boolean realm) {
			this(key, icon, heritage, stat, freePoint, cls, power, looks, start, realm, Set.of(), Set.of());
		}

		/**
		 * (0.7.448) Andy: "this screen should recontextualize dependent on which heritage you pick. bio built wouldnt have
		 * anyone who raised them". An answer only those born to {@code kinds} are asked (the vat-born's Tinkerer, the fair's
		 * acrobats).
		 */
		Answer onlyBorn(Heritage.Kind... kinds) {
			return new Answer(this.key, this.icon, this.heritage, this.stat, this.freePoint, this.cls, this.power, this.looks, this.start, this.realm,
				Set.of(kinds), this.never);
		}

		/** (0.7.448) An answer that makes no sense for those born to {@code kinds} (a vat-born's noble family): not asked of them. */
		Answer notBorn(Heritage.Kind... kinds) {
			return new Answer(this.key, this.icon, this.heritage, this.stat, this.freePoint, this.cls, this.power, this.looks, this.start, this.realm,
				this.only, Set.of(kinds));
		}

		static Answer born(Heritage.Kind kind, String key) {
			return new Answer(key, kind.icon, kind, null, false, null, "", List.of(), null, false);
		}

		static Answer raised(String key, Item icon, Stats.@Nullable Stat stat, boolean realm) {
			return new Answer(key, icon, null, stat, stat == null, null, "", List.of(), null, realm);
		}

		static Answer child(String key, Item icon, Stats.Stat stat, String power, String... looks) {
			return new Answer(key, icon, null, stat, false, null, power, List.of(looks), null, false);
		}

		static Answer youth(String key, Item icon, Classes.PlayerClass cls) {
			return new Answer(key, icon, null, null, false, cls, "", List.of(), null, false);
		}

		static Answer turn(String key, Item icon, String power) {
			return new Answer(key, icon, null, null, false, null, power, List.of(), null, false);
		}

		static Answer road(Start start, Item icon) {
			return new Answer(start.name().toLowerCase(Locale.ROOT), icon, null, null, false, null, "", List.of(), start, start.realm);
		}

		/**
		 * Whether it can be picked here: the unarmed and lost-in-time starts need Jaymo's Powers (its lost limbs, its
		 * future), and (0.7.349) so do the other worlds' starts. (0.7.354) Only road answers have a start; the others
		 * are always offered (and {@code Set.of} throws on {@code contains(null)}: every life-story page crashed).
		 */
		public boolean offered() {
			// (0.7.414) the Nobodies are MCA's villagers, hired: they need MCA
			if (this.start == Start.NOBODIES) {
				return McaCompat.LOADED;
			}
			return this.start == null || this.start != Start.UNARMED && this.start != Start.TIMELOST && this.start != Start.ROCK_BOTTOM
				&& this.start != Start.LAB && this.start != Start.ANDROID && !OTHER_WORLDS.contains(this.start) || powers();
		}

		/**
		 * (0.7.349, Andy: "Verdant only") The heritages a start is only for: the Verdant World's, the Verdant-born's; (0.7.355,
		 * Andy: "should only be for emberborn") a demon master's hall, the Emberborn's; (0.7.412, Andy: "Only bio built can choose
		 * this") being cast out by your own, the Bio-built's; (0.7.417, Andy) the alchemist's lab, for the Bio-built, Rubber-folk,
		 * Emberborn, Stoneborn or Sylvan. Empty for any.
		 */
		public Set<Heritage.Kind> onlyFor() {
			if (!this.only.isEmpty()) {
				return this.only;
			}
			return switch (this.start) {
				case VERDANT -> Set.of(Heritage.Kind.VERDANT);
				case DEMON -> Set.of(Heritage.Kind.EMBERBORN);
				case CAST_OUT -> Set.of(Heritage.Kind.BIOBUILT);
				// (0.7.443) a half-orc or a goblin-raised human: one who passes for human
				case GREENSKIN -> GREENSKIN_KINDS;
				case LAB -> LAB_KINDS;
				case null, default -> Set.of();
			};
		}

		/** (0.7.417) Whether {@code kind} may take this start: any (the start is for no one heritage alone), or one of {@link #onlyFor}. */
		public boolean fits(Heritage.@Nullable Kind kind) {
			if (kind != null && this.never.contains(kind)) {
				return false;
			}
			Set<Heritage.Kind> only = this.onlyFor();
			return only.isEmpty() || kind != null && only.contains(kind);
		}

		/** (0.7.349, Andy: "you can start as divine born again, but only if you pick that start") The heritage a start makes you, over your birth's; null for none. */
		public Heritage.@Nullable Kind makes() {
			return this.start == Start.AFTERLIFE ? Heritage.Kind.DIVINE : null;
		}
	}

	/** The six questions, in order, and their answers. */
	public enum Question {
		BORN, RAISED, CHILDHOOD, YOUTH, TURN, ROAD;

		private final List<Answer> answers = new ArrayList<>();

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public List<Answer> answers() {
			return this.answers;
		}

		/** Question 5 is only asked with Jaymo's Powers there. */
		public boolean asked() {
			return this != TURN || powers();
		}

		static {
			BORN.answers.addAll(List.of(
				Answer.born(Heritage.Kind.HEARTLANDER, "plains"),
				Answer.born(Heritage.Kind.STONEBORN, "mountain"),
				Answer.born(Heritage.Kind.TIDEBORN, "coast"),
				Answer.born(Heritage.Kind.SYLVAN, "forest"),
				Answer.born(Heritage.Kind.VERDANT, "valley"),
				Answer.born(Heritage.Kind.EMBERBORN, "forge"),
				Answer.born(Heritage.Kind.FROSTBORN, "north"),
				Answer.born(Heritage.Kind.NIGHTBORN, "moors"),
				Answer.born(Heritage.Kind.WARRIORBORN, "warband"),
				Answer.born(Heritage.Kind.EARTHLING, "scholars"),
				Answer.born(Heritage.Kind.DAWNBORN, "temple"),
				// (0.7.262, Andy's NOEA picks) the new bloods: a workshop's vats, a travelling fair ((0.7.320) the Divine-born's
				// hilltop shrine is gone: that blood is earned now, by dying again in the afterlife)
				Answer.born(Heritage.Kind.BIOBUILT, "vats"),
				Answer.born(Heritage.Kind.RUBBERFOLK, "fair"),
				// (0.7.490) the beastfolk are picked at the start (a den in the wild); the Hollow are earned and have no birthplace
				Answer.born(Heritage.Kind.BEASTFOLK, "den")));
			RAISED.answers.addAll(List.of(
				Answer.raised("farmers", Items.WHEAT_SEEDS, Stats.Stat.ENDURANCE, false),
				Answer.raised("smiths", Items.ANVIL, Stats.Stat.MIGHT, false),
				Answer.raised("fisherfolk", Items.FISHING_ROD, Stats.Stat.AGILITY, false),
				Answer.raised("hunters", Items.BOW, Stats.Stat.FINESSE, false),
				Answer.raised("merchants", net.minecraft.world.item.Items.EMERALD, Stats.Stat.WITS, false),
				Answer.raised("soldiers", Items.IRON_SWORD, Stats.Stat.MIGHT, false),
				Answer.raised("healers", Items.GLISTERING_MELON_SLICE, Stats.Stat.WITS, false),
				Answer.raised("wizard", Items.ENCHANTED_BOOK, Stats.Stat.WITS, false),
				Answer.raised("outlaws", Items.TRIPWIRE_HOOK, Stats.Stat.AGILITY, false),
				// (0.7.448) no noble house claims a vat-born, and the warband has no titles to give
				Answer.raised("nobles", Items.GOLDEN_HELMET, Stats.Stat.WITS, true).notBorn(Heritage.Kind.BIOBUILT, Heritage.Kind.WARRIORBORN,
					Heritage.Kind.VERDANT),
				// (0.7.448) who only some bloods could have been raised by (the rest of the answers are told their way: lang
				// story.jaymoskingdoms.as.<heritage>.<question>...)
				Answer.raised("tinkerer", Items.REDSTONE, Stats.Stat.GRIT, false).onlyBorn(Heritage.Kind.BIOBUILT),
				Answer.raised("vatkin", Items.GLASS_BOTTLE, Stats.Stat.AGILITY, false).onlyBorn(Heritage.Kind.BIOBUILT),
				Answer.raised("acrobats", Items.FEATHER, Stats.Stat.AGILITY, false).onlyBorn(Heritage.Kind.RUBBERFOLK),
				Answer.raised("ringmaster", Items.NAME_TAG, Stats.Stat.WITS, false).onlyBorn(Heritage.Kind.RUBBERFOLK),
				Answer.raised("keepers", Items.PRISMARINE_SHARD, Stats.Stat.WITS, false).onlyBorn(Heritage.Kind.TIDEBORN),
				Answer.raised("grovekeepers", Items.FERN, Stats.Stat.WITS, false).onlyBorn(Heritage.Kind.SYLVAN),
				Answer.raised("elders", Items.GLOW_BERRIES, Stats.Stat.ENDURANCE, false).onlyBorn(Heritage.Kind.VERDANT),
				Answer.raised("hornkin", Items.GOLD_INGOT, Stats.Stat.MIGHT, false).onlyBorn(Heritage.Kind.EMBERBORN),
				Answer.raised("wolves", Items.BONE, Stats.Stat.FINESSE, false).onlyBorn(Heritage.Kind.FROSTBORN),
				Answer.raised("barrows", Items.SOUL_LANTERN, Stats.Stat.FINESSE, false).onlyBorn(Heritage.Kind.NIGHTBORN),
				Answer.raised("chieftain", Items.GOAT_HORN, Stats.Stat.MIGHT, false).onlyBorn(Heritage.Kind.WARRIORBORN),
				Answer.raised("master", Items.STICK, Stats.Stat.ENDURANCE, false).onlyBorn(Heritage.Kind.EARTHLING),
				Answer.raised("sunpriests", Items.GLOWSTONE_DUST, Stats.Stat.GRIT, false).onlyBorn(Heritage.Kind.DAWNBORN),
				// (0.7.490) the pack that raised a beastfolk
				Answer.raised("pack", Items.BONE, Stats.Stat.AGILITY, false).onlyBorn(Heritage.Kind.BEASTFOLK),
				Answer.raised("foundling", Items.STRING, null, false)));
			CHILDHOOD.answers.addAll(List.of(
				Answer.child("roofs", Items.FEATHER, Stats.Stat.AGILITY, "gravity", "skymarked", "blossomkin", "moonpale", "starlit", "avian", "sprite", "wraith"),
				Answer.child("caves", Items.COBWEB, Stats.Stat.ENDURANCE, "web", "obsidian", "hollow", "arachnid", "changeling", "skeleton"),
				Answer.child("fights", Items.LEATHER, Stats.Stat.MIGHT, "zfighter", "battlescarred", "ironknuckle", "berrygloss", "canine", "bloodelf", "zombie"),
				Answer.child("books", Items.WRITABLE_BOOK, Stats.Stat.WITS, "chrono", "inkmarked", "opaline", "highelf"),
				Answer.child("swimming", Items.KELP, Stats.Stat.AGILITY, "darktide", "abyssal", "reefcrowned", "candystripe", "serpen", "nymph"),
				Answer.child("storms", Items.LIGHTNING_ROD.weathering().unaffected(), Stats.Stat.FINESSE, "wind", "skymarked", "galeborn", "dryad"),
				Answer.child("fire", Items.CAMPFIRE, Stats.Stat.GRIT, "smoke", "magmavein", "soulflame", "ashen", "kitsune", "reaper"),
				Answer.child("snow", Items.SNOW_BLOCK, Stats.Stat.GRIT, "cryo", "rimeglass", "aurora", "moonelf"),
				Answer.child("bells", Items.BELL, Stats.Stat.WITS, "resonance", "umbral", "twilight", "seraphic", "faerie"),
				Answer.child("blades", Items.BUNDLE, Stats.Stat.FINESSE, "arsenal", "warpainted", "brassbound", "feline")));
			YOUTH.answers.addAll(List.of(
				Answer.youth("cutpurses", Items.TRIPWIRE_HOOK, Classes.PlayerClass.THIEF),
				Answer.youth("levy", Items.IRON_AXE, Classes.PlayerClass.WARLORD),
				Answer.youth("apprentice", Items.BOOKSHELF, Classes.PlayerClass.MAGE),
				Answer.youth("hermit", Items.BLAZE_POWDER, Classes.PlayerClass.SORCERER),
				Answer.youth("hunted", Items.ARROW, Classes.PlayerClass.ARCHER),
				Answer.youth("winter", Items.STONE_AXE, Classes.PlayerClass.SURVIVALIST),
				Answer.youth("pits", Items.SHIELD, Classes.PlayerClass.BRUTE),
				Answer.youth("gunsmith", Items.GUNPOWDER, Classes.PlayerClass.MARKSMAN),
				Answer.youth("messenger", Items.SADDLE, Classes.PlayerClass.RIDER)));
			TURN.answers.addAll(List.of(
				Answer.turn("bite", Items.SPIDER_EYE, "web"),
				Answer.turn("weapon", Items.IRON_SWORD, "arsenal"),
				Answer.turn("fall", Items.FEATHER, "gravity"),
				Answer.turn("storm", Items.WIND_CHARGE, "wind"),
				Answer.turn("clock", Items.CLOCK, "chrono"),
				Answer.turn("bell", Items.BELL, "resonance"),
				Answer.turn("sea", Items.NAUTILUS_SHELL, "darktide"),
				Answer.turn("fists", Items.LEATHER, "zfighter"),
				Answer.turn("smoke", Items.CAMPFIRE, "smoke"),
				Answer.turn("blizzard", Items.POWDER_SNOW_BUCKET, "cryo"),
				Answer.turn(FATE, Items.ENDER_EYE, FATE)));
			ROAD.answers.addAll(List.of(
				Answer.road(Start.CITY, Items.GOLD_NUGGET),
				Answer.road(Start.BANISHED, Items.BARRIER),
				Answer.road(Start.CAVE, Items.COBWEB),
				Answer.road(Start.SHIPWRECK, Items.OAK_BOAT),
				Answer.road(Start.SWORN, Items.IRON_CHESTPLATE),
				Answer.road(Start.VILLAGE, Items.BELL),
				Answer.road(Start.MONOLITH, Items.CHISELED_STONE_BRICKS),
				Answer.road(Start.WILD, Items.COMPASS),
				Answer.road(Start.SKYFALL, Items.AMETHYST_SHARD),
				Answer.road(Start.TIMELOST, Items.RECOVERY_COMPASS),
				Answer.road(Start.BURNED, Items.CHARCOAL),
				Answer.road(Start.SNOWED, Items.LANTERN),
				Answer.road(Start.CAPTIVE, Items.LEAD),
				Answer.road(Start.WHALERS, Items.TRIDENT),
				Answer.road(Start.UNARMED, Items.BONE),
				Answer.road(Start.CHAINED, Items.IRON_CHAIN),
				Answer.road(Start.ROCK_BOTTOM, Items.DEAD_BUSH),
				Answer.road(Start.SENTENCED, Items.IRON_BARS),
				Answer.road(Start.EVICTED, Items.OAK_DOOR),
				Answer.road(Start.HIDEOUT, Items.BARREL),
				Answer.road(Start.CAST_OUT, Items.SLIME_BALL),
				Answer.road(Start.GREENSKIN, Items.ROTTEN_FLESH),
				Answer.road(Start.HEIRLOOM, Items.IRON_SWORD),
				Answer.road(Start.TRADER, Items.CHEST),
				Answer.road(Start.PUP, Items.BONE),
				Answer.road(Start.NOBODIES, Items.WOODEN_SWORD),
				Answer.road(Start.LAB, Items.BREWING_STAND),
				Answer.road(Start.ANDROID, Items.IRON_INGOT),
				Answer.road(Start.MOON_CURSED, Items.GHAST_TEAR),
				Answer.road(Start.MOON, Items.END_STONE),
				Answer.road(Start.VERDANT, Items.VINE),
				Answer.road(Start.RUSTFALL, Items.RAW_IRON),
				Answer.road(Start.MAW, Items.CRYING_OBSIDIAN),
				Answer.road(Start.DEMON, Items.SOUL_LANTERN),
				Answer.road(Start.AFTERLIFE, Items.TOTEM_OF_UNDYING),
				Answer.road(Start.FATE, Items.ENDER_EYE)));
		}
	}

	/**
	 * Each player's story: the key of their answer to each question, in order ("" for one not asked), sealed once lived.
	 * Saved, kept through death, synced to its own player (the attribute points it gave show on the stats screen).
	 */
	public static final AttachmentType<List<String>> STORY = AttachmentRegistry.create(JaymosKingdoms.id("life_story"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/**
	 * (0.7.304) The road a told story still owes its player: "START|realm id|calling", till they're put at that start.
	 * Saved, kept through death, synced to its own player (their screen stays black till they're there).
	 */
	public static final AttachmentType<String> ROAD = AttachmentRegistry.create(JaymosKingdoms.id("story_road"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));
	/**
	 * (0.7.304) Andy: "The first screen should be choosing your gender". Who the story's first page says you are
	 * ({@link #MALE} or {@link #FEMALE}): MCA's creator after the story starts on it. Saved, kept through death, synced
	 * to its own player.
	 */
	public static final AttachmentType<String> GENDER = AttachmentRegistry.create(JaymosKingdoms.id("story_gender"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));
	public static final String MALE = "male", FEMALE = "female";
	/**
	 * (0.7.349, Andy: "you respawn on the world, or where you spawned for the start initially") The other world a start set
	 * them down on (its dimension id), till they first leave it: their respawn stays where they landed till then. Saved,
	 * kept through death.
	 */
	public static final AttachmentType<String> STRANDED = AttachmentRegistry.create(JaymosKingdoms.id("story_stranded"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());
	/** (0.7.356) The calling (the power) a story told before MCA's creator keeps for its road. Saved, kept through death. */
	public static final AttachmentType<String> CALLING = AttachmentRegistry.create(JaymosKingdoms.id("story_calling"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());
	/**
	 * Where a player's start put them: the world, the place it chose ({@link #PLACE_APART}: a settlement's or camp's seat, a
	 * wreck, a farmhouse; else where they woke) and where they woke. Packed positions.
	 */
	record Born(String dimension, long place, long wake) {
		static final Codec<Born> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(Born::dimension),
			Codec.LONG.fieldOf("place").forGetter(Born::place),
			Codec.LONG.fieldOf("wake").forGetter(Born::wake)
		).apply(i, Born::new));
	}

	/** Player (UUID) → their start, saved on the Overworld: one each, a new life's replacing the last ({@link #unmake} frees it). */
	static final AttachmentType<java.util.Map<String, Born>> BORN = AttachmentRegistry.create(JaymosKingdoms.id("story_births"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Born.CODEC)));
	/**
	 * While a road is set out on ({@link #arrive}): everyone else's start, less the party mate's whose road you share, so
	 * the start looks past their places. Empty otherwise, and in single player.
	 */
	private static java.util.Map<String, Born> OTHERS = java.util.Map.of();
	/** (Tiv) Lost in time, not in Powers' future yet: their start is noted where they arrive there ({@link #leftWorld}). */
	private static final Set<java.util.UUID> TIMELOST_PENDING = new java.util.HashSet<>();
	/** (0.7.356) Players asked for their road since they joined. */
	private static final Set<java.util.UUID> ROAD_ASKED = new java.util.HashSet<>();
	/** (0.7.304) Players whose road is being made ready (a capital, a harbour), so it's set out on once. */
	private static final Set<java.util.UUID> DEPARTED = new java.util.HashSet<>();

	/** A realm a player can name in their story: its id and name. */
	public record Realm(int id, String name) {
	}

	/**
	 * Server → player: tell your story (the screen opens; {@code forced}: it can't be shut), with the realms there are to
	 * name. (0.7.356) {@code creatorAhead}: MCA's creator comes next, so the story stops short of the road ({@link Road}).
	 * (0.7.417) {@code mates}: the party members who took a road lately (Start Together). (0.7.419) {@code unlocked}: the keys of
	 * the locked roads this player can take whatever their past (a deed, ops, a road they took); {@code shut}: those ops have
	 * forced shut ({@link RoadLocks}).
	 */
	public record Open(boolean forced, List<Realm> realms, boolean creatorAhead, List<Mate> mates, List<String> unlocked, List<String> shut)
		implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("story_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBoolean(p.forced);
				buf.writeBoolean(p.creatorAhead);
				buf.writeVarInt(p.realms.size());
				for (Realm r : p.realms) {
					buf.writeVarInt(r.id);
					buf.writeUtf(r.name);
				}
				buf.writeVarInt(p.mates.size());
				for (Mate m : p.mates) {
					buf.writeUtf(m.name());
					buf.writeUtf(m.road());
					buf.writeUtf(m.realm());
				}
				buf.writeVarInt(p.unlocked.size());
				for (String key : p.unlocked) {
					buf.writeUtf(key);
				}
				buf.writeVarInt(p.shut.size());
				for (String key : p.shut) {
					buf.writeUtf(key);
				}
			}, buf -> {
				boolean forced = buf.readBoolean(), creatorAhead = buf.readBoolean();
				int n = buf.readVarInt();
				List<Realm> realms = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					realms.add(new Realm(buf.readVarInt(), buf.readUtf()));
				}
				int m = buf.readVarInt();
				List<Mate> mates = new ArrayList<>(m);
				for (int i = 0; i < m; i++) {
					mates.add(new Mate(buf.readUtf(), buf.readUtf(), buf.readUtf()));
				}
				int u = buf.readVarInt();
				List<String> unlocked = new ArrayList<>(u);
				for (int i = 0; i < u; i++) {
					unlocked.add(buf.readUtf());
				}
				int sh = buf.readVarInt();
				List<String> shut = new ArrayList<>(sh);
				for (int i = 0; i < sh; i++) {
					shut.add(buf.readUtf());
				}
				return new Open(forced, List.copyOf(realms), creatorAhead, List.copyOf(mates), List.copyOf(unlocked), List.copyOf(shut));
			});

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/**
	 * Player → server: live this life. {@code answers}: an index per question (-1 for one not asked); {@code raisedRealm}
	 * and {@code roadRealm}: the realm named for a noble family and for a banished or sworn start (-1: none);
	 * {@code look}: the heritage's look (0: its first); {@code option}: a Mage's first spell or a Sorcerer's element (-1);
	 * {@code heritage} and {@code cls}: (Andy: "allow a player override AFTER the result") a heritage or class ordinal
	 * picked over the story's on its last page (-1: the story's). (0.7.420) {@code flaws}: the flaws taken, a bit each
	 * ({@link Flaws.Flaw#bit}); {@code switches}: the hard-road options and start from nothing ({@link Flaws#WARY} and the rest).
	 */
	public record Live(List<Integer> answers, int raisedRealm, int roadRealm, int look, int option, int heritage, int cls, int gender, int flaws, int switches) implements CustomPacketPayload {
		public static final Type<Live> TYPE = new Type<>(JaymosKingdoms.id("story_live"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Live> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.answers.size());
				for (int a : p.answers) {
					buf.writeVarInt(a + 1);
				}
				buf.writeVarInt(p.raisedRealm + 1);
				buf.writeVarInt(p.roadRealm + 1);
				buf.writeVarInt(p.look);
				buf.writeVarInt(p.option + 1);
				buf.writeVarInt(p.heritage + 1);
				buf.writeVarInt(p.cls + 1);
				buf.writeVarInt(p.gender + 1);
				buf.writeVarInt(p.flaws);
				buf.writeVarInt(p.switches);
			}, buf -> {
				int n = Math.min(buf.readVarInt(), Question.values().length);
				List<Integer> answers = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					answers.add(buf.readVarInt() - 1);
				}
				return new Live(List.copyOf(answers), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt() - 1,
					buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt());
			});

		@Override
		public Type<Live> type() {
			return TYPE;
		}
	}

	/**
	 * (0.7.356) Player → server: the road, told after MCA's creator ({@link #roadOwed}). {@code road}: the answer's index;
	 * {@code roadRealm}: the realm named for a banished or sworn start (-1: none); {@code look}: the look of a heritage the
	 * road makes you (the afterlife's); {@code tone}: (0.7.474) a half-orc's skin on the greenskin road's slider
	 * ({@link GreenskinLife#TONE}).
	 */
	public record Road(int road, int roadRealm, int look, float tone) implements CustomPacketPayload {
		public static final Type<Road> TYPE = new Type<>(JaymosKingdoms.id("story_road_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Road> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.road + 1);
				buf.writeVarInt(p.roadRealm + 1);
				buf.writeVarInt(p.look);
				buf.writeFloat(p.tone);
			}, buf -> new Road(buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt(), buf.readFloat()));

		@Override
		public Type<Road> type() {
			return TYPE;
		}
	}

	private LifeStory() {
	}

	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(LifeStory::hunt);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HUNTS.clear();
			DEPARTED.clear();
			ROAD_ASKED.clear();
			LANDINGS.clear();
			TIMELOST_PENDING.clear();
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			DEPARTED.remove(handler.player.getUUID());
			ROAD_ASKED.remove(handler.player.getUUID());
			LANDINGS.remove(handler.player.getUUID());
		});
		// (0.7.304) a death before the story was told shut its screen: it opens again on the new body
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (owed(newPlayer)) {
				ask(newPlayer, true);
			}
		});
		StoryStasis.register();
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(LifeStory::leftWorld);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(LifeStory::leftBand);
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Live.TYPE, Live.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Live.TYPE, (payload, context) -> live(context.player(), payload));
		PayloadTypeRegistry.serverboundPlay().register(Road.TYPE, Road.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Road.TYPE, (payload, context) -> road(context.player(), payload));
	}

	/** Whether Jaymo's Powers is here (question 5 is asked, and its answer is an affinity). */
	public static boolean powers() {
		return FabricLoader.getInstance().isModLoaded("jaymospowers");
	}

	public static @Nullable List<String> of(Player player) {
		return player.getAttached(STORY);
	}

	/** {@code player}'s answer to {@code q}, or null (none, or not asked). */
	public static @Nullable Answer answer(Player player, Question q) {
		List<String> story = of(player);
		if (story == null || story.size() <= q.ordinal()) {
			return null;
		}
		String key = story.get(q.ordinal());
		for (Answer a : q.answers) {
			if (a.key.equals(key)) {
				return a;
			}
		}
		return null;
	}

	/** Free points in {@code stat} {@code player}'s story gave (who raised them, what they did as a child). */
	public static int points(Player player, Stats.Stat stat) {
		// (0.7.420) a life from nothing gave the story's up; a finished aim pays them back double
		int n = Flaws.gaveUp(player) ? Flaws.repaid(player, stat.ordinal()) : storyPoints(player, stat);
		// (0.7.418) the free point a finished aim gave
		n += LifeAims.bonus(player, stat);
		return n;
	}

	/** (0.7.420) What the story itself gave in {@code stat} (who raised them, what they did as a child, Rustfall's weight). */
	static int storyPoints(Player player, Stats.Stat stat) {
		int n = 0;
		for (Question q : List.of(Question.RAISED, Question.CHILDHOOD)) {
			Answer a = answer(player, q);
			if (a != null && a.stat == stat) {
				n++;
			}
		}
		// (0.7.349) years under Rustfall's weight
		if (stat == RUSTFALL_STAT && startedAs(player, Start.RUSTFALL)) {
			n++;
		}
		return n;
	}

	/** (0.7.349) Whether {@code player}'s story started as {@code start}. */
	static boolean startedAs(Player player, Start start) {
		Answer road = answer(player, Question.ROAD);
		return road != null && road.start == start;
	}

	/** Points to spend as they like that {@code player}'s story gave (a foundling's). */
	public static int freePoints(Player player) {
		// (0.7.420) the flaws taken pay a free point each; a life from nothing gave the foundling's up, to be paid back double
		return (Flaws.gaveUp(player) ? Flaws.repaid(player, Flaws.freeSlot()) : storyFreePoints(player)) + Flaws.points(player);
	}

	/** (0.7.420) The free point the story itself gave (a foundling's): 1 or 0. */
	static int storyFreePoints(Player player) {
		Answer a = answer(player, Question.RAISED);
		return a != null && a.freePoint ? 1 : 0;
	}

	/**
	 * Whether {@code player} still owes their story: a newcomer (no heritage, no class, no story yet) whose client has
	 * this mod. Anyone part-made before stories came finishes on the heritage and class screens instead.
	 */
	static boolean owed(ServerPlayer player) {
		return untold(player) || roadOwed(player);
	}

	/** (0.7.356) Whether nothing of {@code player}'s story is told yet. */
	static boolean untold(ServerPlayer player) {
		return of(player) == null && Heritage.of(player) == null && Classes.classOf(player) == null && ServerPlayNetworking.canSend(player, Open.TYPE);
	}

	/**
	 * (0.7.356) Andy: "I want this screen to come after the Creator". Whether {@code player} told their story up to the
	 * road before MCA's creator, and still owes the road (its answer "" till then).
	 */
	static boolean roadOwed(ServerPlayer player) {
		return roadOwed(of(player)) && ServerPlayNetworking.canSend(player, Open.TYPE);
	}

	/** (0.7.356) Once MCA's creator is done, the road is asked (once; again on a new body or a rejoin). From {@link StoryStasis}. */
	static void askRoad(ServerPlayer player) {
		if (roadOwed(player) && !McaCompat.destinyPending(player) && !ROAD_ASKED.contains(player.getUUID())) {
			ask(player, true);
		}
	}

	/** (0.7.356) Whether {@code story} is told up to the road, and the road not yet. */
	public static boolean roadOwed(@Nullable List<String> story) {
		return story != null && story.size() > Question.ROAD.ordinal() && story.get(Question.ROAD.ordinal()).isEmpty();
	}

	/**
	 * Opens the story screen for {@code player}, with the realms of the world to name. (0.7.356) With MCA's creator still
	 * to come, the story stops short of the road, which is asked once the creator is done.
	 */
	static void ask(ServerPlayer player, boolean forced) {
		if (!ServerPlayNetworking.canSend(player, Open.TYPE) || roadOwed(player) && McaCompat.destinyPending(player)) {
			return;
		}
		if (roadOwed(player)) {
			ROAD_ASKED.add(player.getUUID());
		}
		// (0.7.228) a new world has no realm till someone walks up to a bell: one is raised first, so every page has realms to name
		MinecraftServer server = player.level().getServer();
		if (StoryGround.realmless(server)) {
			StoryGround.capital(server, player, () -> {
				if (!player.isRemoved() && owed(player)) {
					ask(player, forced);
				}
			});
			return;
		}
		List<Realm> realms = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen()) {
				realms.add(new Realm(f.id(), f.name()));
			}
		}
		ServerPlayNetworking.send(player, new Open(forced, List.copyOf(realms), untold(player) && McaCompat.destinyPending(player), mates(player),
			RoadLocks.opened(server, player), RoadLocks.shutKeys(server)));
	}

	/** The answer at {@code index} of {@code q}, or null (out of range, or not asked). */
	private static @Nullable Answer at(Question q, List<Integer> answers) {
		if (!q.asked() || q.ordinal() >= answers.size()) {
			return null;
		}
		int i = answers.get(q.ordinal());
		return i >= 0 && i < q.answers.size() ? q.answers.get(i) : null;
	}

	/** A realm to name in a story: there, standing, and a realm. */
	private static @Nullable Faction realm(MinecraftServer server, int id) {
		Faction f = id < 0 ? null : Factions.get(server, id);
		return f != null && f.type().isRealm() && !f.fallen() ? f : null;
	}

	/** The story's look of {@code kind}: the first of the childhood's leanings that is one of its looks (0: its first). */
	public static int lookFor(Heritage.Kind kind, @Nullable Answer childhood) {
		if (childhood == null) {
			return 0;
		}
		for (String key : childhood.looks) {
			HeritageVariants.Variant v = HeritageVariants.Variant.byKey(key);
			if (v != null && v.heritage == kind) {
				return v.index();
			}
		}
		return 0;
	}

	/** The power a story's start echoes (its calling), or "". */
	public static String echo(Start start) {
		return switch (start) {
			case CAVE -> "web";
			case SHIPWRECK -> "darktide";
			case MONOLITH -> "wind";
			case SKYFALL -> "gravity";
			case BURNED -> "smoke";
			case SNOWED -> "cryo";
			case CAPTIVE, CHAINED -> "zfighter";
			// (0.7.417) a bench-made android is a Z Fighter's kind of body, the lab's mark is a thing a bite or a tinker did
			case ANDROID -> "zfighter";
			case WHALERS -> "darktide";
			// (0.7.349) the other worlds: a fall under the moon's light weight, the Verdant World's warriors, years in Rustfall's
			// mines, the black hole's pull, a demon master's hall, the dead's own master
			case MOON, MAW -> "gravity";
			case VERDANT, RUSTFALL, DEMON, AFTERLIFE -> "zfighter";
			default -> "";
		};
	}

	/** From the story screen: live this life, all of it at once. */
	private static void live(ServerPlayer player, Live live) {
		if (!untold(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		Answer born = at(Question.BORN, live.answers()), raised = at(Question.RAISED, live.answers()), child = at(Question.CHILDHOOD, live.answers()),
			youth = at(Question.YOUTH, live.answers()), turn = at(Question.TURN, live.answers()), road = at(Question.ROAD, live.answers());
		// (0.7.356) with MCA's creator still to come, the road is told after it ({@link #road})
		boolean later = road == null && McaCompat.destinyPending(player);
		if (born == null || raised == null || child == null || youth == null || road == null && !later || powers() && turn == null
			|| road != null && !road.offered()) {
			return;
		}
		// (0.7.420) at most two flaws, known ids only: the client is not trusted with that
		if (!Flaws.valid(live.flaws(), live.switches())) {
			return;
		}
		// (0.7.419) a locked road is only for a deed done in this world or a past that opens it: the client is not trusted with that
		if (road != null && !RoadLocks.allowed(server, player, road.start, q -> {
			Answer a = at(q, live.answers());
			return a == null ? null : a.key;
		})) {
			return;
		}
		// the heritage and class picked over the story's, if any: a heritage open to them, any class
		Heritage.Kind heritage = born.heritage;
		if (live.heritage() >= 0) {
			Heritage.Kind[] kinds = Heritage.Kind.values();
			if (live.heritage() >= kinds.length || !Heritage.unlocked(player, kinds[live.heritage()])) {
				return;
			}
			heritage = kinds[live.heritage()];
		}
		// (0.7.448) the questions asked were the birthplace's: an answer for another blood (a vat-born's Tinkerer) is refused
		for (Answer a : new Answer[] {raised, child, youth, turn}) {
			if (a != null && !a.fits(born.heritage)) {
				return;
			}
		}
		// (0.7.349) the Verdant World's start is the Verdant-born's alone (0.7.355: the demon master's, the Emberborn's); the afterlife's makes you Divine-born
		if (road != null && !road.fits(heritage)) {
			return;
		}
		// (0.7.501) an earned blood (the Bio-built's vats) is only born to one it has woken in, unless the road makes another
		if ((road == null || road.makes() == null) && !Heritage.unlocked(player, heritage)) {
			return;
		}
		if (road != null && road.makes() != null) {
			heritage = road.makes();
			Heritage.unlock(player, heritage);
		}
		Faction nobleRealm = raised.realm ? realm(server, live.raisedRealm()) : null;
		Faction roadRealm = road != null && road.realm ? realm(server, live.roadRealm()) : null;
		if (raised.realm && nobleRealm == null || road != null && road.realm && roadRealm == null) {
			return;
		}
		Classes.PlayerClass[] classes = Classes.PlayerClass.values();
		Classes.PlayerClass cls = live.cls() >= 0 && live.cls() < classes.length ? classes[live.cls()] : youth.cls;
		String option = cls != null && live.option() >= 0 && live.option() < cls.options.size() ? cls.options.get(live.option()) : "";
		if (cls == null || !cls.options.isEmpty() && option.isEmpty()) {
			return;
		}

		if (live.gender() == 0 || live.gender() == 1) {
			player.setAttached(GENDER, live.gender() == 1 ? FEMALE : MALE);
		}
		// the story first, so the heritage's settling counts its points
		player.setAttached(STORY, List.of(born.key, raised.key, child.key, youth.key, turn == null ? "" : turn.key, road == null ? "" : road.key));
		// (Powers' Motion patch 1) the temperament the story makes, fixed for good; told once the road is chosen too
		PlayerTemperaments.settle(player, road != null);
		// (0.7.420) the harder life they chose: flaws, hard-road options, the points given up (counted off the story just saved)
		Flaws.choose(player, live.flaws(), live.switches());
		Heritage.set(player, heritage);
		// (0.7.509) the patron of their blood is known; a devout upbringing starts sworn
		Patrons.storyStart(player);
		HeritageVariants.choose(player, heritage, live.look());
		Classes.set(player, cls, option);
		Stats.attributes(player);

		RandomSource random = player.getRandom();
		String power = "";
		if (turn != null) {
			power = turn.power;
			if (FATE.equals(power) && random.nextFloat() < FATE_LEANS) {
				power = child.power;
			}
			PowersCompat.affinity(player, power);
		}
		if (nobleRealm != null) {
			atLeast(player, nobleRealm, NOBLE_REPUTATION);
		}

		if (road == null) {
			// (0.7.356) the calling kept for the road, told after MCA's creator
			player.setAttached(CALLING, power);
			return;
		}
		setOut(player, road, roadRealm, power);
	}

	/** (0.7.304) Sets out once MCA's creator is done: till then they're held in stasis over the world (StoryStasis). */
	private static void setOut(ServerPlayer player, Answer road, @Nullable Faction roadRealm, String power) {
		// (0.7.419) a road taken stays open to them for good, whatever happens to the deeds
		RoadLocks.note(player, road.start);
		player.setAttached(ROAD, road.start.name() + "|" + (roadRealm == null ? -1 : roadRealm.id()) + "|" + power);
		if (!McaCompat.destinyPending(player)) {
			depart(player);
		}
	}

	/**
	 * (0.7.356) From the road screen after MCA's creator: where the story starts. The Verdant World's road is still the
	 * Verdant-born's alone, and the afterlife's still makes you Divine-born.
	 */
	private static void road(ServerPlayer player, Road payload) {
		if (!roadOwed(player)) {
			return;
		}
		List<Answer> roads = Question.ROAD.answers;
		Answer road = payload.road() >= 0 && payload.road() < roads.size() ? roads.get(payload.road()) : null;
		Heritage.Kind heritage = Heritage.of(player);
		if (road == null || !road.offered() || !road.fits(heritage)) {
			return;
		}
		// (0.7.419) the same lock check as the whole story's: the past is the story told before the creator
		if (!RoadLocks.allowed(player.level().getServer(), player, road.start, q -> {
			Answer a = answer(player, q);
			return a == null ? null : a.key;
		})) {
			return;
		}
		Faction roadRealm = road.realm ? realm(player.level().getServer(), payload.roadRealm()) : null;
		if (road.realm && roadRealm == null) {
			return;
		}
		if (road.makes() != null && heritage != road.makes()) {
			Heritage.unlock(player, road.makes());
			Heritage.set(player, road.makes());
			HeritageVariants.choose(player, road.makes(), payload.look());
			Stats.attributes(player);
		}
		if (road.start == Start.GREENSKIN) {
			// (0.7.474) the skin they'd have as a half-orc (the slider on the greenskin road)
			player.setAttached(GreenskinLife.TONE, Mth.clamp(payload.tone(), 0.0F, 1.0F));
		}
		List<String> story = new ArrayList<>(of(player));
		story.set(Question.ROAD.ordinal(), road.key);
		player.setAttached(STORY, List.copyOf(story));
		PlayerTemperaments.settle(player, true);
		// (0.7.509) a devout road starts sworn; the road may have changed the blood and temperament, so start the story afresh
		Patrons.storyRestart(player);
		// (0.7.420) a road told after the creator may add story points of its own (Rustfall's), which a life from nothing gives up too
		if (road.start == Start.RUSTFALL) {
			Flaws.giveUpMore(player, RUSTFALL_STAT, 1);
		}
		String power = player.getAttached(CALLING);
		player.removeAttached(CALLING);
		setOut(player, road, roadRealm, power == null ? "" : power);
	}

	/** (0.7.304) Whether {@code player} has told their story and not yet been put where it starts. */
	static boolean departing(ServerPlayer player) {
		return player.hasAttached(ROAD);
	}

	/**
	 * (0.7.304) Sets {@code player} on the road their story ends with: what the start needs is made first (they wait in
	 * stasis), then they're put there. Done once; the road stays owed till they're really there, so a death, a
	 * disconnect or a lost start-up on the way can't cost them the start they chose.
	 */
	static void depart(ServerPlayer player) {
		String road = player.getAttached(ROAD);
		if (road == null || !DEPARTED.add(player.getUUID())) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		String[] parts = road.split("\\|", -1);
		Start chosen;
		try {
			chosen = Start.valueOf(parts[0]);
		} catch (IllegalArgumentException e) {
			chosen = Start.WILD;
		}
		int realmId = -1;
		if (parts.length > 1) {
			try {
				realmId = Integer.parseInt(parts[1]);
			} catch (NumberFormatException ignored) {
				// the wild, then
			}
		}
		String calling = parts.length > 2 ? parts[2] : "";
		Faction roadRealm = realmId >= 0 ? realm(server, realmId) : null;
		Answer raised = answer(player, Question.RAISED);
		Classes.PlayerClass cls = Classes.classOf(player);
		String option = Classes.of(player).option();
		Heritage.Kind bornAs = Heritage.of(player);
		java.util.UUID id = player.getUUID();
		if (raised == null || cls == null || bornAs == null) {
			// unmade since (/kingdom story reset): nothing to set out on
			player.removeAttached(ROAD);
			DEPARTED.remove(id);
			return;
		}
		// (Andy: "it should keep them in the loading screen so they can read the tips for as long as it takes to load,
		// rather than getting teleported") up now, before anything is searched for or made
		StoryGround.holdNow(player, StoryGround.ARRIVING);
		RandomSource random = player.getRandom();
		boolean byFate = chosen == Start.FATE;
		Start road0 = byFate ? fated(server, player, random) : chosen;
		// (0.7.605) fate may now give a realm's road (banished, sworn, the cells): a realm of its own choosing
		Faction realmFor = byFate && road0.realm && roadRealm == null ? capital(server, random) : roadRealm;
		// (0.7.304) whoever they are by the time it's ready: a death on the way makes a new body
		java.util.function.Consumer<Start> go = start -> {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || !departing(p)) {
				// gone meanwhile: the road is still owed, and set out on again when they're back
				DEPARTED.remove(id);
				return;
			}
			// (0.7.419) the road fate (or a fallback) set them on stays theirs as well
			RoadLocks.note(p, start);
			// (the loading screen up before they're let down out of stasis and moved: a new body may not have it yet)
			StoryGround.holdNow(p, StoryGround.ARRIVING);
			p.removeAttached(ROAD);
			DEPARTED.remove(id);
			StoryStasis.release(p);
			arrive(p, start, realmFor, bornAs, raised, cls, option, calling, byFate);
		};
		// (0.7.228) what the start needs and the world hasn't got is made first: a capital, a harbour (they wait a moment)
		if (road0 == Start.CITY && capital(server, random) == null) {
			StoryGround.capital(server, player, () -> go.accept(Start.CITY));
		} else if (road0 == Start.WHALERS) {
			// (0.7.235) always made ready first, even with a berth in port: her sea must be ticking for her to float
			// (Tiv) at a harbour nobody else's whaler story started from (a party mate's on this road is shared)
			Landing crewMate = together(player, Start.WHALERS, null);
			StoryGround.harbour(player, freeFor(player, crewMate == null ? null : crewMate.who, PLACE_APART), () -> go.accept(Start.WHALERS), () -> go.accept(Start.WILD));
		} else if (road0 == Start.TRADER) {
			// (0.7.414) a milestone to wake beside: a capital, a second town and the road between them are raised if the world has none
			StoryGround.milestone(player, () -> go.accept(Start.TRADER), () -> go.accept(Start.WILD));
		} else {
			go.accept(road0);
		}
	}

	/** Puts {@code player} at {@code start}, kits them out and tells them where their story has brought them. */
	private static void arrive(ServerPlayer player, Start start, @Nullable Faction roadRealm, Heritage.Kind bornAs, Answer raised,
		Classes.PlayerClass cls, String option, String calling, boolean fated) {
		RandomSource random = player.getRandom();
		Start landed;
		// (Tiv) everyone else's start, so this one looks past their places
		OTHERS = others(player);
		Faction asked = null;
		try {
			// (Tiv) a realm's road whose seat is already someone else's start goes to the nearest realm whose isn't (a party mate
			// on the same road and realm shares it); with every realm taken, the one named, and a spot of their own there
			if (start.realm && roadRealm != null && together(player, start, roadRealm) == null && !freePlace(Factions.level(player.level().getServer()), roadRealm.seat())) {
				Faction free = freeRealm(player.level().getServer(), roadRealm.seat());
				if (free != null) {
					asked = roadRealm;
					roadRealm = free;
				}
			}
			landed = begin(player, start, roadRealm, bornAs, random);
		} finally {
			OTHERS = java.util.Map.of();
		}
		if (asked != null && landed == start) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story.realm_taken", asked.name(), roadRealm.name())
				.withStyle(ChatFormatting.GRAY));
		}
		if (landed == Start.UNARMED || landed == Start.ROCK_BOTTOM) {
			// an old loss, long healed over: one arm or one leg
			int limb = LIMBS[random.nextInt(LIMBS.length)];
			if (PowersCompat.bornWithout(player, limb)) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story.limb",
					Component.translatable("story.jaymoskingdoms.limb." + limb)).withStyle(ChatFormatting.GRAY));
			}
		}
		// (0.7.420) nothing but rags: no kit lines at all (the aim's Journal page below still comes)
		RAGS_ONLY = Flaws.rags(player);
		try {
			kit(player, start, landed, raised, cls, option, random);
			// (0.7.649) Andy: "everyone spawns with a journal now", whatever the start (rags, chains and all)
			Journal.handOut(player);
			// (0.7.503) and the medicine that road would have left them (StoryMeds: a full satchel for the sworn, none at rock bottom)
			for (ItemStack meds : StoryMeds.of(player, start, landed, cls)) {
				give(player, meds);
			}
		} finally {
			RAGS_ONLY = false;
		}
		// (0.7.246) where it set them down, for the folk about it to talk of (StoryTalk)
		Hunt hunt = HUNTS.get(player.getUUID());
		StoryTalk.landed(player, landed, landed == Start.CAPTIVE && hunt != null ? hunt.camp()
			: landed == Start.CHAINED ? StoryHard.shackleCamp(player)
			: (landed == Start.BANISHED || landed == Start.SWORN || landed == Start.SENTENCED || landed == Start.EVICTED) && roadRealm != null ? roadRealm.id() : -1);
		// (0.7.418) and the one thing they mean to do (a page in their pack, LifeAims)
		LifeAims.begin(player, landed, roadRealm);
		// (Andy) a farmhand wakes in the family's bed, everything they set out with in a chest beside it (FarmhandWake)
		FarmhandWake.stow(player);
		// (0.7.420) everyone is wary, a price on their head: once, as the road lands
		Flaws.landed(player);
		// (0.7.236) behind the loading screen a few seconds more, while the land where they woke loads
		StoryGround.settle(player);
		// (0.7.452) and the land round them made ahead in the background, wherever the start put them (ArrivalGround)
		ArrivalGround.around(player);
		if (start == Start.TIMELOST) {
			// (Andy: "Lost in time starts you with chrono level 1") awake already, whatever the turn was, wherever they woke
			PowersCompat.awaken(player, "chrono");
		} else if (landed == Start.MOON && !spaceborn(Heritage.of(player), cls)) {
			// (0.7.364) Andy: "this should start you as z fighter, sorcerer, or mage, as they can hold their breath / use ki long
			// enough to survive in space without help ... stoneborn too": anyone else wakes on the moon with ki already
			PowersCompat.awaken(player, "zfighter");
		} else if (!calling.isEmpty() && calling.equals(echo(landed))) {
			PowersCompat.call(player, calling, ECHO_TICKS);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		String road = "story.jaymoskingdoms.road." + landed.name().toLowerCase(Locale.ROOT);
		player.sendSystemMessage(Component.translatable(fated ? "message.jaymoskingdoms.story.fated" : "message.jaymoskingdoms.story.begun",
			Component.translatable(road)).withStyle(ChatFormatting.GOLD));
		if (fated) {
			// (0.7.451) Andy: "there is nothing that tells you the start you got if you choose the random start": fate's pick
			// across the screen as they wake, held past the few seconds the land takes to load
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(FATE_TITLE_IN, FATE_TITLE_STAY, FATE_TITLE_OUT));
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable(road + ".short", "").withStyle(ChatFormatting.GRAY)));
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.jaymoskingdoms.story.fated.title", Component.translatable(road)).withStyle(ChatFormatting.GOLD)));
		}
	}

	/**
	 * (0.7.364) Whether one of {@code heritage} and {@code cls} lives on the Pale Moon unaided: a Mage's or Sorcerer's
	 * breath held by magic, a Stoneborn's stone lungs. Anyone else is woken there as a Z Fighter, whose ki keeps them.
	 */
	public static boolean spaceborn(Heritage.@Nullable Kind heritage, Classes.@Nullable PlayerClass cls) {
		return heritage == Heritage.Kind.STONEBORN || cls == Classes.PlayerClass.MAGE || cls == Classes.PlayerClass.SORCERER;
	}

	/** "Let fate decide" on the start (0.7.419: among the roads {@code player} can take): any start that needs no realm named (a capital only if there's one). */
	private static Start fated(MinecraftServer server, ServerPlayer player, RandomSource random) {
		// (0.7.605) Andy: "Let fate decide (random even from non unlocked! percentage table)": any start, locked or not,
		// by FATE_ODDS. Only those that can't be: one their blood can't take, one whose mod isn't loaded, one that remakes
		// their heritage, and a realm's road with no realm in the world.
		Heritage.Kind kind = Heritage.of(player);
		boolean realms = capital(server, random) != null;
		List<Start> starts = new ArrayList<>();
		List<Integer> odds = new ArrayList<>();
		int total = 0;
		for (Answer a : Question.ROAD.answers) {
			Start s = a.start;
			int weight = s == null ? 0 : FATE_ODDS.getOrDefault(s, 0);
			if (weight <= 0 || !a.offered() || !a.fits(kind) || a.makes() != null || s.realm && !realms) {
				continue;
			}
			starts.add(s);
			odds.add(weight);
			total += weight;
		}
		if (total <= 0) {
			return Start.WILD;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < starts.size(); i++) {
			roll -= odds.get(i);
			if (roll < 0) {
				return starts.get(i);
			}
		}
		return starts.get(starts.size() - 1);
	}

	/** A realm's capital to wake in: a kingdom's if there is one, else any realm's (null with none). */
	private static @Nullable Faction capital(MinecraftServer server, RandomSource random) {
		return capital(server, random, f -> true);
	}

	/** (Tiv) The standing realm whose seat is nearest {@code from} and nobody else's start ({@link #freePlace}), or null. */
	private static @Nullable Faction freeRealm(MinecraftServer server, BlockPos from) {
		ServerLevel level = Factions.level(server);
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen() && f.seat() != null && freePlace(level, f.seat())) {
				double d = f.seat().distSqr(from);
				if (d < bestD) {
					bestD = d;
					best = f;
				}
			}
		}
		return best;
	}

	/** The same, among the realms that are {@code free} (nobody else's start). */
	private static @Nullable Faction capital(MinecraftServer server, RandomSource random, java.util.function.Predicate<Faction> free) {
		List<Faction> kingdoms = new ArrayList<>(), realms = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen() && free.test(f)) {
				realms.add(f);
				if (f.is(FactionType.KINGDOM)) {
					kingdoms.add(f);
				}
			}
		}
		List<Faction> from = kingdoms.isEmpty() ? realms : kingdoms;
		return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
	}

	/**
	 * Puts {@code player} where their story starts and does what it means to the realms; returns the start that came
	 * to pass (one that can't be found falls back to the wild, and that to where they stand).
	 */
	private static Start begin(ServerPlayer player, Start start, @Nullable Faction named, Heritage.Kind heritage, RandomSource random) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos to = null;
		// (0.7.417, Start Together) a party mate who took this road a few minutes ago wakes you beside them, and you share its trouble
		Landing mate = together(player, start, named);
		if (mate != null && !mate.dimension.equals(level.dimension()) && !OTHER_WORLDS.contains(start)) {
			// (a mate woken in another world is only followed where Powers puts you in one itself)
			mate = null;
		}
		if (mate != null) {
			// (the mate's place is shared by choice: it isn't taken from you)
			OTHERS.remove(mate.who.toString());
		}
		// (Tiv) the place the start chose (a seat, a camp, a wreck...), noted as yours once you're there; null: where you woke
		BlockPos place = null;
		switch (start) {
			case CITY -> {
				// a capital nobody else started in; with every one taken, the wild
				Faction city = capital(server, random, f -> freePlace(level, f.seat()));
				if (city != null) {
					place = city.seat();
					to = spot(level, mate, city.seat(), CITY_MIN, CITY_MAX, random);
				}
			}
			case BANISHED -> {
				if (named != null) {
					// (Tiv) the realm you were driven out of is your start's place, as a realm's other roads' is
					place = named.seat();
					to = spot(level, mate, named.seat(), BANISHED_MIN, BANISHED_MAX, random);
					if (to != null) {
						atMost(player, named, BANISHED_REPUTATION);
						for (Faction other : Factions.all(server)) {
							if (other.id() != named.id() && other.type().isRealm() && !other.fallen()
								&& (named.relation(other.id()) == Relation.RIVAL || named.relation(other.id()) == Relation.WAR)) {
								Reputation.change(player, other, RIVAL_REPUTATION);
							}
						}
					}
				}
			}
			case SWORN -> {
				if (named != null) {
					place = named.seat();
					to = spot(level, mate, named.seat(), CITY_MIN, CITY_MAX, random);
					if (to != null) {
						atLeast(player, named, SWORN_REPUTATION);
						if (Factions.of(server, player.getUUID()) == null) {
							Factions.update(server, named.id(), f -> f.withMember(player.getUUID(), new Faction.Member(Rank.PEASANT.ordinal(), 0)));
						}
					}
				}
			}
			case CAVE -> {
				BlockPos by = mate == null ? null : beside(level, mate.pos, random);
				to = by != null ? by : cave(level, spawn, random);
			}
			case SHIPWRECK -> {
				// (Tiv) a wreck nobody else washed up at: a beached one first, else any; looked past a few taken ones
				BlockPos wreck = lookPast(spawn, from -> {
					BlockPos beached = structure(level, BuiltinStructures.SHIPWRECK_BEACHED, from);
					return beached != null && freePlace(level, beached) ? beached
						: level.findNearestMapStructure(StructureTags.SHIPWRECK, from, STRUCTURE_SEARCH, false);
				}, w -> freePlace(level, w), random);
				if (wreck != null) {
					place = wreck;
					to = spot(level, mate, wreck, 0, STRUCTURE_NEAR, random);
				}
			}
			case VILLAGE -> {
				// (0.7.605, Andy) the Family farmhand: inside a lone farmhouse, taken in by the family there (FarmhandHome).
				// A party mate's farmhouse is yours too; with no farmhouse in reach, the old village start below.
				// (Tiv) one nobody else was taken in at, looked past a few taken ones
				FarmhandHome.House house = lookPast(mate != null ? mate.pos : spawn, from -> FarmhandHome.find(level, from, random),
					h -> freePlace(level, h.box().getCenter()), random);
				if (house != null) {
					place = house.box().getCenter();
					to = house.wake();
					FarmhandHome.takeIn(player, level, house, heritage);
					break;
				}
				// (0.7.381, Andy) a haven of your own people, else one that takes your blood in; never shunned there (StoryHomes)
				Faction standing = StoryHomes.standing(server, player, heritage, spawn, f -> freePlace(level, f.seat()));
				Faction mates = mate == null ? null : Factions.around(server, level, mate.pos, StoryHomes.PENDING_RANGE);
				// (a mate's village that would shun your blood is no home: you go where your own would, as alone)
				BlockPos by = mate == null || mates != null && !StoryHomes.suits(server, mates, heritage, player) ? null : beside(level, mate.pos, random);
				if (by != null) {
					// (0.7.417) beside the mate: the village they woke in takes you in as it is, else it waits for you as it did for them
					to = by;
					if (mates != null) {
						StoryHomes.settleIn(player, mates);
					} else {
						StoryHomes.await(player, mate.pos);
					}
				} else if (standing != null) {
					to = ground(level, standing.seat(), 0, STRUCTURE_NEAR, random);
					if (to != null) {
						place = standing.seat();
						StoryHomes.settleIn(player, standing);
					}
				}
				if (to == null) {
					BlockPos village = freshVillage(level, heritage, spawn, random);
					if (village != null) {
						to = ground(level, village, 0, STRUCTURE_NEAR, random);
						if (to != null) {
							place = village;
							Faction there = Factions.around(server, level, village, StoryHomes.PENDING_RANGE);
							if (there != null) {
								// (no village free of a settlement: the one there takes them in as it is)
								StoryHomes.settleIn(player, there);
							} else {
								StoryHomes.await(player, village);
							}
						}
					}
				}
			}
			case MONOLITH -> {
				// (Tiv) one nobody else was drawn to; with every one taken, a new one is raised as for a world with none
				BlockPos foot = Monoliths.nearestFoot(level, spawn, f -> freePlace(level, f));
				if (foot == null) {
					foot = StoryGround.monolith(level, spawn, random);
				}
				if (foot != null) {
					place = foot;
					to = spot(level, mate, foot, MONOLITH_MIN, MONOLITH_MAX, random);
				}
			}
			case SKYFALL -> {
				to = spot(level, mate, spawn, WILD_MIN, WILD_MAX, random);
				if (to != null) {
					to = crater(level, to, random);
				}
			}
			case TIMELOST -> {
				// Powers puts them in its future itself (or holds them till it's in place); the wild only without Powers.
				// (Tiv) At an arrival spot nobody else's, noted as theirs once they're there (leftWorld)
				TIMELOST_PENDING.add(player.getUUID());
				if (PowersCompat.strand(player, freeFor(player, mate == null ? null : mate.who, START_APART))) {
					return Start.TIMELOST;
				}
				TIMELOST_PENDING.remove(player.getUUID());
			}
			case BURNED -> {
				to = spot(level, mate, spawn, WILD_MIN, WILD_MAX, random);
				if (to != null) {
					// (0.7.501, Andy: "the player should be IN the burning village") the village first, round the landing spot,
					// then the ruin you wake in (it only takes ground and plants, so it never cuts into the village's houses)
					burningVillage(level, to, random);
					burnedRuin(level, to, random);
				}
			}
			case SNOWED -> {
				com.mojang.datafixers.util.Pair<BlockPos, Holder<net.minecraft.world.level.biome.Biome>> peaks = level.findClosestBiome3d(
					b -> b.is(net.minecraft.world.level.biome.Biomes.FROZEN_PEAKS) || b.is(net.minecraft.world.level.biome.Biomes.JAGGED_PEAKS)
						|| b.is(net.minecraft.world.level.biome.Biomes.SNOWY_SLOPES),
					spawn, PEAKS_SEARCH, PEAKS_STEP, PEAKS_VERTICAL_STEP);
				if (peaks != null) {
					to = spot(level, mate, peaks.getFirst(), 0, STRUCTURE_NEAR, random);
				}
			}
			case CAPTIVE -> {
				Faction camp = mateCamp(server, mate);
				Landing campMate = camp == null ? null : mate;
				if (camp == null) {
					camp = nearestCamp(server, spawn);
				}
				if (camp == null) {
					camp = StoryGround.camp(level, spawn, random);
				}
				if (camp != null) {
					place = camp.seat();
					to = spot(level, campMate, camp.seat(), CAPTIVE_MIN, CAPTIVE_MAX, random);
					if (to != null) {
						HUNTS.put(player.getUUID(), new Hunt(camp.id(), server.getTickCount() + HUNT_TICKS));
					}
				}
			}
			case CHAINED -> {
				// (0.7.412) shackled in a barbarian camp, among its captives (a camp is raised if the world has none, as for the escaped captive)
				Faction camp = mateCamp(server, mate);
				Landing campMate = camp == null ? null : mate;
				if (camp == null) {
					camp = nearestCamp(server, spawn, false);
				}
				if (camp == null) {
					camp = StoryGround.camp(level, spawn, random);
				}
				if (camp != null) {
					place = camp.seat();
					// (0.7.417) with a mate, in the same camp's row of captives, next to theirs
					to = spot(level, campMate, camp.seat(), CHAINED_MIN, CHAINED_MAX, random);
					if (to != null) {
						StoryHard.shackle(player, to, camp.id());
					}
				}
			}
			case ROCK_BOTTOM -> {
				// (0.7.412) in sight of an outlaw camp, past its raiders' eyes (a camp is raised if the world has none)
				Faction camp = nearestCamp(server, spawn, false);
				if (camp == null) {
					camp = StoryGround.camp(level, spawn, random);
				}
				if (camp != null) {
					place = camp.seat();
					to = spot(level, mate, camp.seat(), ROCK_CAMP_MIN, ROCK_CAMP_MAX, random);
				}
			}
			case SENTENCED -> {
				// (0.7.412) in the cells under the named realm's seat, with days to serve; the realm starts cold
				if (named != null) {
					atMost(player, named, SENTENCED_REPUTATION);
					// (0.7.417) with a mate, in the same cell, a step from where they stand
					StoryHard.imprison(player, named, StoryHard.SENTENCE_DAYS * StoryHard.DAY_TICKS, mate != null);
					// (they respawn by the seat, where they are let out, not down in the pit)
					home(player, level, BlockPos.containing(Speakeasy.release(level, named)));
					landSafely(player);
					landedAt(player, Start.SENTENCED, named.id(), player.blockPosition(), -1, named.seat());
					return Start.SENTENCED;
				}
			}
			case EVICTED -> {
				// (0.7.412) the capital road for the named realm, with a fuller purse and no bed; its watch moves them on at night
				if (named != null) {
					place = named.seat();
					to = spot(level, mate, named.seat(), CITY_MIN, CITY_MAX, random);
					if (to != null) {
						StoryHard.evict(player, named);
					}
				}
			}
			case HIDEOUT -> {
				// (0.7.412) a speakeasy's floor (the nearest built den, its realm hates you), else a barbarian camp that spares outlaws
				// (Tiv) a den nobody else woke in, else a camp nobody else did
				Faction denRealm = StoryHard.denRealm(server, spawn, d -> freePlace(level, d.hatch()));
				Underworld.Den den = denRealm == null ? null : Underworld.builtDen(server, denRealm);
				if (den != null) {
					net.minecraft.world.phys.Vec3 in = Speakeasy.wakeSpot(den.hatch());
					atMost(player, denRealm, BANISHED_REPUTATION);
					List<Integer> found = new ArrayList<>(player.getAttachedOrElse(Underworld.FOUND, List.of()));
					if (!found.contains(denRealm.id())) {
						found.add(denRealm.id());
						player.setAttached(Underworld.FOUND, List.copyOf(found));
					}
					// (0.7.417) with a mate in the same den, a step from the middle of the floor
					net.minecraft.world.phys.Vec3 at = in;
					if (mate != null && mate.dimension == level.dimension() && mate.pos.closerThan(BlockPos.containing(in), DEN_MATE_RANGE)) {
						for (int i = 0; i < TOGETHER_TRIES; i++) {
							net.minecraft.world.phys.Vec3 step = in.add(random.nextInt(3) - 1, 0.0, random.nextInt(3) - 1);
							if (!step.equals(in) && standable(level, BlockPos.containing(step))) {
								at = step;
								break;
							}
						}
					}
					player.teleportTo(level, at.x, at.y, at.z, Set.<net.minecraft.world.entity.Relative>of(), random.nextFloat() * 360.0F, 0.0F, false);
					landSafely(player);
					home(player, level, BlockPos.containing(in));
					landedAt(player, Start.HIDEOUT, -1, player.blockPosition(), -1, den.hatch());
					return Start.HIDEOUT;
				}
				Faction camp = nearestCamp(server, spawn, true);
				if (camp == null) {
					camp = StoryGround.camp(level, spawn, random);
				}
				if (camp != null) {
					place = camp.seat();
					to = spot(level, mate, camp.seat(), HIDEOUT_CAMP_MIN, HIDEOUT_CAMP_MAX, random);
					Faction hates = to == null ? null : StoryHard.nearestSettlement(server, camp.seat());
					if (hates != null) {
						// (an outlaw to the settlement nearest the camp, and the camp spares them: Barbarians.outlaw)
						atMost(player, hates, BANISHED_REPUTATION);
					}
				}
			}
			case HEIRLOOM, PUP, NOBODIES -> {
				// (0.7.414) the kit roads that set out from the wild: a family blade, a pup, three hired hands
				to = spot(level, mate, spawn, WILD_MIN, WILD_MAX, random);
			}
			case MOON_CURSED -> {
				// (0.7.417) the wild, or the edge of a village if one stands near (with a mate, beside them)
				BlockPos village = mate == null && random.nextBoolean() ? level.findNearestMapStructure(StructureTags.VILLAGE, spawn, STRUCTURE_SEARCH, false) : null;
				to = village != null ? spot(level, null, village, MOON_CURSED_VILLAGE_MIN, MOON_CURSED_VILLAGE_MAX, random) : null;
				if (to == null) {
					to = spot(level, mate, spawn, WILD_MIN, WILD_MAX, random);
				}
				if (to != null) {
					MoonCurse.curse(player);
				}
			}
			case LAB, ANDROID -> {
				// (0.7.417) Powers builds the lab or the workshop a walk from where the player stands and wakes them in it: with a mate,
				// stand them by the mate first, so the new one is raised in the same stretch of country (it builds its own room)
				if (mate != null) {
					BlockPos by = beside(level, mate.pos, random);
					if (by != null) {
						player.teleportTo(level, by.getX() + 0.5, by.getY(), by.getZ() + 0.5, Set.<net.minecraft.world.entity.Relative>of(), player.getYRot(), 0.0F, false);
					}
				}
				// (Tiv) raised where nobody else started
				java.util.function.BiPredicate<ServerLevel, BlockPos> free = freeFor(player, mate == null ? null : mate.who, PLACE_APART);
				if (start == Start.LAB ? PowersCompat.labEscape(player, free) : PowersCompat.androidStart(player, free)) {
					landSafely(player);
					home(player, player.level(), player.blockPosition());
					landedAt(player, start, -1, player.blockPosition(), -1, player.blockPosition());
					return start;
				}
			}
			case TRADER -> {
				// (0.7.414) beside a King's Road milestone (StoryGround made the road if the world had none), by the stone's own road
				Roads.Stone stone = StoryGround.stone(player);
				if (stone != null && !freePlace(level, stone.sign())) {
					// (Tiv) a milestone nobody else set out from; with every one taken, a free spot further along from this one
					Roads.Stone free = Roads.stoneNear(level, spawn, s -> freePlace(level, s));
					if (free != null) {
						stone = free;
					}
				}
				if (stone != null) {
					place = stone.sign();
					to = spot(level, mate, stone.sign(), TRADER_NEAR_MIN, TRADER_NEAR_MAX, random);
					if (to != null) {
						StoryKit.trade(player, stone.next());
					}
				}
			}
			case GREENSKIN -> {
				// (0.7.443) just outside the orc camp or goblin village that raised you (GreenskinLife)
				// (Tiv) one nobody else was raised in; with every one taken, the wild
				Faction camp = GreenskinLife.home(server, spawn, f -> freePlace(level, f.seat()));
				if (camp != null) {
					place = camp.seat();
					to = spot(level, mate, camp.seat(), GreenskinLife.WAKE_MIN, GreenskinLife.WAKE_MAX, random);
					if (to != null) {
						GreenskinLife.raise(player, camp);
					}
				}
			}
			case CAST_OUT -> {
				// (0.7.412) outside the gates of a haven that would have taken them in (the wild without one): their own turn them away for three days
				Faction haven = StoryHomes.standing(server, player, heritage, spawn, f -> freePlace(level, f.seat()));
				place = haven != null ? haven.seat() : null;
				to = haven != null ? spot(level, mate, haven.seat(), CAST_OUT_MIN, CAST_OUT_MAX, random) : spot(level, mate, spawn, WILD_MIN, WILD_MAX, random);
				if (to != null) {
					StoryHard.castOut(player);
				}
			}
			case WHALERS -> {
				// (0.7.228) signed on a realm's whaler fitting out, standing on her deck (she sails when she's fitted out)
				net.minecraft.world.phys.Vec3 deck = Whalers.signOn(player, spawn, freeFor(player, mate == null ? null : mate.who, PLACE_APART));
				if (deck != null) {
					player.teleportTo(level, deck.x, deck.y, deck.z, Set.<net.minecraft.world.entity.Relative>of(), random.nextFloat() * 360.0F, 0.0F, false);
					landSafely(player);
					// (Tiv) her harbour is their start's place: the next whaler's story fits out at another (StoryGround.harbour)
					landedAt(player, Start.WHALERS, -1, player.blockPosition(), -1, player.blockPosition());
					return Start.WHALERS;
				}
			}
			case MOON, VERDANT, RUSTFALL, MAW, DEMON, AFTERLIFE -> {
				// (0.7.349) Powers puts them there and makes what the start needs; the wild if it can't. (Tiv) Where nobody else
				// started there, and the way back (the afterlife's) a spot nobody else woke at
				BlockPos back = spot(level, null, spawn, WILD_MIN, WILD_MAX, random);
				if (back == null) {
					back = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
				}
				// (the Maw and the afterlife are each one small place: a spot of your own there is START_APART from anyone else's)
				int apart = start == Start.MAW || start == Start.AFTERLIFE ? START_APART : PLACE_APART;
				if (PowersCompat.otherWorld(player, start.name().toLowerCase(Locale.ROOT), back, freeFor(player, mate == null ? null : mate.who, apart))) {
					landSafely(player);
					if (mate != null && mate.dimension.equals(player.level().dimension())) {
						// (0.7.417) the same world as the mate, wherever Powers set you down: by them
						BlockPos by = beside(player.level(), mate.pos, random);
						if (by != null) {
							player.teleportTo(player.level(), by.getX() + 0.5, by.getY(), by.getZ() + 0.5, Set.<net.minecraft.world.entity.Relative>of(), player.getYRot(), 0.0F, false);
							landSafely(player);
						}
					}
					landedAt(player, start, -1, player.blockPosition(), -1, player.blockPosition());
					if (start == Start.AFTERLIFE) {
						// among the dead, the afterlife keeps them till it lets them go; then they wake at {@code back}
						home(player, level, back);
					} else {
						ServerLevel there = player.level();
						home(player, there, player.blockPosition());
						// (0.7.528) the realm by height: in a One Dimension world the Nether or Aether is a band of the Overworld
						player.setAttached(STRANDED, Stacked.realm(player).identifier().toString());
					}
					return start;
				}
			}
			case WILD, UNARMED, FATE -> {
				// the unarmed start sets out from the wild too
				if (start == Start.UNARMED) {
					to = spot(level, mate, spawn, WILD_MIN, WILD_MAX, random);
				}
			}
		}
		Start landed = start;
		if (to == null) {
			landed = Start.WILD;
			to = spot(level, start == Start.WILD ? mate : null, spawn, WILD_MIN, WILD_MAX, random);
		}
		if (to == null) {
			return landed;
		}
		// (fallen from the sky: you drift down into your own crater)
		double y = landed == Start.SKYFALL ? to.getY() + SKYFALL_HEIGHT : to.getY();
		player.teleportTo(level, to.getX() + 0.5, y, to.getZ() + 0.5, Set.<net.minecraft.world.entity.Relative>of(), random.nextFloat() * 360.0F, 0.0F, false);
		landSafely(player);
		home(player, level, to);
		// (0.7.417, Start Together) where a party mate who takes this road after you is put (the camp that holds a captive is theirs too)
		Hunt held = HUNTS.get(player.getUUID());
		// (Tiv) and the place that's yours now: the mate's if you woke beside them, the one the start chose, else where you woke
		BlockPos yours = mate != null && flat(to, mate.pos) <= (TOGETHER_MAX + 1) * (TOGETHER_MAX + 1) ? mate.place
			: landed == start && place != null ? place : to;
		landedAt(player, landed, landed.realm && named != null ? named.id() : -1, to,
			landed == Start.CHAINED ? StoryHard.shackleCamp(player) : landed == Start.CAPTIVE && held != null ? held.camp() : -1, yours);
		if (landed == Start.SKYFALL) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SKYFALL_TICKS, 0));
		}
		if (landed == Start.BURNED) {
			// (0.7.501, Andy) smoke-sick from the fire you woke in
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, BURNED_NAUSEA_TICKS, 0));
		}
		if (landed == Start.SNOWED) {
			// half-frozen
			player.setTicksFrozen(player.getTicksRequiredToFreeze());
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SNOWED_SLOW_TICKS, 0));
		}
		if (landed == Start.ROCK_BOTTOM) {
			// (0.7.412) starving: hardly a bite left in you
			player.getFoodData().setFoodLevel(ROCK_BOTTOM_FOOD);
			player.getFoodData().setSaturation(0.0F);
		}
		if (landed == Start.CAVE) {
			// the bite you shrugged off, as you always do, and the fall that came after: poisoned, wounded, barely breathing
			// (0.7.228: Andy died at once at 2 hearts; now half health, and the poison stops short of killing as poison does)
			player.setHealth(Math.min(player.getHealth(), CAVE_HEALTH));
			player.addEffect(new MobEffectInstance(MobEffects.POISON, BITE_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CAVE_WOUND_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CAVE_WOUND_TICKS, 0));
		}
		return landed;
	}

	/** (0.7.229) MCA's creator, after the story, is done: the arrival's grace again, if they've a story (it may have run out). */
	static void arrived(ServerPlayer player) {
		if (of(player) != null) {
			landSafely(player);
		}
	}

	/**
	 * (0.7.253, Andy: "keep the default spawn of your start") Where they wake after dying, till a bed says otherwise:
	 * where their story began. (A whaler's hand is put back aboard her by {@link Whalers}, else wakes at her harbour.)
	 */
	static void home(ServerPlayer player, ServerLevel level, BlockPos at) {
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(
			net.minecraft.world.level.storage.LevelData.RespawnData.of(level.dimension(), at, player.getYRot(), 0.0F), true), false);
	}

	/**
	 * (0.7.349) {@code player} went from {@code from} to another world: if a start had set them down on {@code from}, they've
	 * left it now, and their respawn there (if it's still that) goes back to the world's spawn (or a bed they sleep in).
	 */
	private static void leftWorld(ServerPlayer player, ServerLevel from, ServerLevel to) {
		if (from != to && TIMELOST_PENDING.remove(player.getUUID())) {
			// (Tiv) the lost in time arrive in the future: that's their start
			born(player, to, player.blockPosition(), player.blockPosition());
		}
		String stranded = player.getAttached(STRANDED);
		if (stranded == null || from == to || !stranded.equals(from.dimension().identifier().toString())) {
			return;
		}
		player.removeAttached(STRANDED);
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn != null && respawn.respawnData().dimension() == from.dimension()) {
			player.setRespawnPosition(null, false);
		}
	}

	/**
	 * (0.7.528) The same for a One Dimension world, where the Nether (or the Aether) is a band of the stacked Overworld and
	 * climbing out of it changes no level: once a second, a player set down in a band who now stands in another has left it.
	 */
	private static void leftBand(net.minecraft.server.MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		for (ServerPlayer player : overworld.players()) {
			String stranded = player.getAttached(STRANDED);
			if (stranded == null || stranded.equals(overworld.dimension().identifier().toString())) {
				continue;
			}
			net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> realm = Stacked.realm(player);
			if (!Stacked.joined(server, realm) && realm != net.minecraft.world.level.Level.OVERWORLD || realm.identifier().toString().equals(stranded)) {
				continue;
			}
			player.removeAttached(STRANDED);
			ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
			if (respawn != null && respawn.respawnData().dimension() == overworld.dimension()
				&& Stacked.realm(overworld, respawn.respawnData().pos().getY()).identifier().toString().equals(stranded)) {
				player.setRespawnPosition(null, false);
			}
		}
	}

	/** (0.7.228) Arrived: no fall carried over from where they stood, and a few seconds before anything can hurt them. */
	private static void landSafely(ServerPlayer player) {
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ARRIVAL_GRACE_TICKS, 4, false, false));
	}

	/**
	 * (0.7.381) A village no settlement holds yet near {@code spawn} (one of the land {@code heritage} comes from first),
	 * looked past a few that are taken; else the nearest at all (null with none). (Tiv) One someone else started in is
	 * never chosen.
	 */
	private static @Nullable BlockPos freshVillage(ServerLevel level, Heritage.Kind heritage, BlockPos spawn, RandomSource random) {
		BlockPos first = null, inland = null;
		BlockPos from = spawn;
		// (0.7.486) a Tideborn's is by the shore: a free one inland is kept only if no shore one turns up
		int tries = heritage == Heritage.Kind.TIDEBORN ? StoryHomes.FRESH_TRIES * 2 : StoryHomes.FRESH_TRIES;
		for (int i = 0; i <= tries; i++) {
			BlockPos village = level.findNearestMapStructure(villages(heritage), from, STRUCTURE_SEARCH, false);
			if (village == null) {
				village = level.findNearestMapStructure(StructureTags.VILLAGE, from, STRUCTURE_SEARCH, false);
			}
			if (village == null) {
				break;
			}
			boolean free = freePlace(level, village);
			if (free && !StoryHomes.taken(level, village)) {
				if (heritage != Heritage.Kind.TIDEBORN || Havens.shore(level, village)) {
					return village;
				}
				if (inland == null) {
					inland = village;
				}
			}
			if (free && first == null) {
				first = village;
			}
			// (look again from further off, away from spawn and the taken one)
			double angle = random.nextDouble() * Math.PI * 2.0;
			from = spawn.offset((int)(Math.cos(angle) * StoryHomes.FRESH_STEP * (i + 1)), 0, (int)(Math.sin(angle) * StoryHomes.FRESH_STEP * (i + 1)));
		}
		return inland != null ? inland : first;
	}

	/** The villages of the land a heritage comes from. */
	private static TagKey<Structure> villages(Heritage.Kind heritage) {
		return switch (heritage) {
			case STONEBORN, SYLVAN, NIGHTBORN, HOLLOW -> StructureTags.ON_TAIGA_VILLAGE_MAPS;
			case BEASTFOLK -> StructureTags.ON_SAVANNA_VILLAGE_MAPS;
			case FROSTBORN -> StructureTags.ON_SNOWY_VILLAGE_MAPS;
			case EMBERBORN, WARRIORBORN -> StructureTags.ON_SAVANNA_VILLAGE_MAPS;
			case DAWNBORN -> StructureTags.ON_DESERT_VILLAGE_MAPS;
			default -> StructureTags.ON_PLAINS_VILLAGE_MAPS;
		};
	}

	/** The nearest {@code key} structure to {@code from}, or null. */
	private static @Nullable BlockPos structure(ServerLevel level, ResourceKey<Structure> key, BlockPos from) {
		java.util.Optional<Holder.Reference<Structure>> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(key);
		return holder.isEmpty() ? null : level.findNearestMapStructure(HolderSet.direct(holder.get()), from, STRUCTURE_SEARCH, false);
	}

	/** A dry, open place to stand {@code min}-{@code max} blocks from {@code center} (horizontally), or null. */
	static @Nullable BlockPos ground(ServerLevel level, BlockPos center, int min, int max, RandomSource random) {
		return ground(level, center, min, max, random, at -> true, new int[] {NEW_CHUNKS});
	}

	/** The same, only where {@code ok} (a column, its y unused), making at most {@code made[0]} new chunks ({@link #reach}). */
	private static @Nullable BlockPos ground(ServerLevel level, BlockPos center, int min, int max, RandomSource random,
		java.util.function.Predicate<BlockPos> ok, int[] made) {
		// (0.7.584) Andy, woken in chains on a camp hut's roof: open ground first (earth, sand, stone), then anywhere to stand
		for (int i = 0; i < GROUND_TRIES * 2; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int d = min + random.nextInt(Math.max(1, max - min + 1));
			int x = center.getX() + Mth.floor(Math.cos(angle) * d), z = center.getZ() + Mth.floor(Math.sin(angle) * d);
			if (!ok.test(new BlockPos(x, 0, z)) || !reach(level, x, z, made)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (standable(level, top) && openGround(level.getBlockState(top.below()))) {
				return top;
			}
		}
		// (0.7.584, Andy: "spawning inside is fine too, as long as they have room") then a floor under a roof, room to stand
		for (int i = 0; i < GROUND_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int d = min + random.nextInt(Math.max(1, max - min + 1));
			int x = center.getX() + Mth.floor(Math.cos(angle) * d), z = center.getZ() + Mth.floor(Math.sin(angle) * d);
			if (!ok.test(new BlockPos(x, 0, z)) || !reach(level, x, z, made)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			for (int y = top.getY() - 2; y >= top.getY() - INDOOR_DEPTH; y--) {
				BlockPos in = new BlockPos(x, y, z);
				if (standable(level, in)) {
					return in;
				}
			}
		}
		for (int i = 0; i < GROUND_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int d = min + random.nextInt(Math.max(1, max - min + 1));
			int x = center.getX() + Mth.floor(Math.cos(angle) * d), z = center.getZ() + Mth.floor(Math.sin(angle) * d);
			if (!ok.test(new BlockPos(x, 0, z)) || !reach(level, x, z, made)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (standable(level, top)) {
				return top;
			}
		}
		return null;
	}

	/**
	 * (0.7.607) Whether {@code (x, z)}'s chunk is there to look at. One already made always is; one that isn't is made now
	 * only while {@code made[0]} lasts ({@link #NEW_CHUNKS} per search). Making one stops the whole server till it's done,
	 * which on this pack can be seconds a chunk: a search that made dozens (ground() tried up to 64 spots) held the server
	 * past its watchdog (crash 2026-11-05 15:00, a road being set out on).
	 */
	private static boolean reach(ServerLevel level, int x, int z, int[] made) {
		if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
			return true;
		}
		if (made[0] <= 0) {
			return false;
		}
		made[0]--;
		level.getChunk(x >> 4, z >> 4);
		return true;
	}

	/** A hollow underground near {@code center}, deep and roofed, with room to stand, (Tiv) nowhere near where anyone else woke; or null. */
	private static @Nullable BlockPos cave(ServerLevel level, BlockPos center, RandomSource random) {
		int[] made = {NEW_CHUNKS};
		for (int i = 0; i < CAVE_TRIES; i++) {
			int x = center.getX() + random.nextInt(CAVE_RADIUS * 2 + 1) - CAVE_RADIUS, z = center.getZ() + random.nextInt(CAVE_RADIUS * 2 + 1) - CAVE_RADIUS;
			if (!freeSpot(level, new BlockPos(x, 0, z)) || !reach(level, x, z, made)) {
				continue;
			}
			int top = Math.min(CAVE_MAX_Y, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 12);
			for (int y = top; y > level.getMinY() + 8; y--) {
				BlockPos pos = new BlockPos(x, y, z);
				if (standable(level, pos) && !level.canSeeSky(pos)) {
					return pos;
				}
			}
		}
		return null;
	}

	/** (0.7.584) A floor that's the land itself, not something built (a roof, a deck, a wall's top). */
	private static boolean openGround(BlockState floor) {
		return floor.is(net.minecraft.tags.BlockTags.DIRT) || floor.is(net.minecraft.tags.BlockTags.SAND)
			|| floor.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || floor.is(net.minecraft.world.level.block.Blocks.GRAVEL)
			|| floor.is(net.minecraft.world.level.block.Blocks.DIRT_PATH) || floor.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
			|| floor.is(net.minecraft.world.level.block.Blocks.CLAY) || floor.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
	}

	/** Room to stand at {@code feet}: two clear blocks on a sturdy floor, no water or lava. */
	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockState floor = level.getBlockState(feet.below());
		BlockState body = level.getBlockState(feet), head = level.getBlockState(feet.above());
		return floor.isFaceSturdy(level, feet.below(), Direction.UP) && floor.getFluidState().isEmpty()
			&& body.isAir() && head.isAir();
	}

	/**
	 * Every start's small kit, and its own few things. {@code start} is the start the story chose (its gear comes even if
	 * the place couldn't be found), {@code landed} where they woke (its place's things).
	 */
	private static void kit(ServerPlayer player, Start start, Start landed, Answer raised, Classes.PlayerClass cls, String option, RandomSource random) {
		if (start == Start.SWORN) {
			// a realm's sworn guard: iron head to foot, a sword and a shield
			for (Item item : List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.IRON_SWORD, Items.SHIELD)) {
				equip(player, new ItemStack(item));
			}
		}
		if (GEARED.contains(start)) {
			for (ItemStack stack : Classes.gear(cls, option)) {
				equip(player, stack.copy());
			}
		}
		if (HARD_MESSAGES.contains(landed)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story." + landed.name().toLowerCase(Locale.ROOT))
				.withStyle(ChatFormatting.GRAY));
		}
		if (landed == Start.CAPTIVE || landed == Start.ROCK_BOTTOM) {
			// nothing but your fists (and, at rock bottom, not a crumb)
			return;
		}
		if (landed == Start.CHAINED) {
			// nothing but what you hid in your sleeve: lockpicks for the shackle
			give(player, new ItemStack(Lockpicking.LOCKPICK, StoryHard.CHAIN_PICKS));
			return;
		}
		if (landed == Start.SENTENCED) {
			// bread through the bars, a few coins they let you keep, a pick in your boot
			give(player, new ItemStack(Items.BREAD, KIT_BREAD / 2));
			give(player, purse(player, SENTENCED_SILVER));
			give(player, new ItemStack(Lockpicking.LOCKPICK, StoryHard.CELL_PICKS));
			return;
		}
		give(player, new ItemStack(Items.BREAD, KIT_BREAD));
		if (landed == Start.SKYFALL) {
			give(player, any(SKYFALL_SHARDS, "aether:ambrosium_shard", "aether_ii:ambrosium_shard", "minecraft:amethyst_shard"));
		}
		if (landed == Start.WHALERS) {
			// her harpooner: irons, a knife for the lines, oilskins
			give(player, new ItemStack(Whaling.HARPOON_ITEM, WHALER_HARPOONS));
			give(player, new ItemStack(Items.SHEARS));
			equip(player, new ItemStack(Items.LEATHER_CHESTPLATE));
			equip(player, new ItemStack(Items.LEATHER_BOOTS));
		}
		if (landed == Start.SNOWED) {
			equip(player, new ItemStack(Items.LEATHER_CHESTPLATE));
			give(player, new ItemStack(Items.LANTERN));
		}
		if (landed == Start.BURNED) {
			List<ItemStack> saved = new ArrayList<>(List.of(new ItemStack(Items.CANDLE, 2), new ItemStack(Items.BOOK), new ItemStack(Items.CLOCK),
				new ItemStack(Items.GOLD_NUGGET, 4), new ItemStack(Items.LEATHER, 3), new ItemStack(Items.STRING, 4), new ItemStack(Items.POTATO, 3),
				new ItemStack(Items.IRON_NUGGET, 6), new ItemStack(Items.BOWL)));
			for (int i = 0; i < RUIN_SAVED && !saved.isEmpty(); i++) {
				give(player, saved.remove(random.nextInt(saved.size())));
			}
		}
		if (landed == Start.CITY) {
			give(player, purse(player, CITY_SILVER));
		}
		if (landed == Start.EVICTED) {
			give(player, purse(player, EVICTED_SILVER));
		}
		if (landed == Start.HIDEOUT) {
			give(player, purse(player, HIDEOUT_SILVER));
		}
		if (landed == Start.GREENSKIN) {
			// (0.7.443) what the camp gave you: hide on your back, a stone axe, and a goblin's mask (theirs know you by it)
			equip(player, new ItemStack(Items.LEATHER_CHESTPLATE));
			give(player, new ItemStack(Items.STONE_AXE));
			give(player, new ItemStack(Items.ROTTEN_FLESH, 3));
			if (!GreenskinLife.halfOrc(player) && Disguise.MASK != null) {
				give(player, new ItemStack(Disguise.MASK));
			}
		}
		if (landed == Start.CAVE) {
			give(player, new ItemStack(Items.IRON_PICKAXE));
			give(player, new ItemStack(Items.TORCH, CAVE_TORCHES));
		}
		if (landed == Start.VILLAGE) {
			// (0.7.220) a real job now ({@link Careers}); (0.7.605) the Family farmhand's is the farm's
			String job = Careers.Job.FARMER.key;
			List<String> story = new ArrayList<>(of(player) == null ? List.of() : of(player));
			story.add(job);
			player.setAttached(STORY, List.copyOf(story));
			Careers.Job kind = Careers.Job.of(job);
			for (ItemStack stack : kind == null ? List.<ItemStack>of() : Careers.kit(kind)) {
				give(player, stack);
			}
			Careers.storyJob(player, job);
			// (0.7.381) the easiest start: more to eat, a little to spend (0.7.605: and a bed under the family's roof, not in your pack)
			give(player, new ItemStack(Items.BREAD, VILLAGE_BREAD));
			give(player, purse(player, VILLAGE_SILVER));
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story.job",
				Component.translatable("story.jaymoskingdoms.job." + job)).withStyle(ChatFormatting.GRAY));
		}
		if (landed == Start.HEIRLOOM) {
			// (0.7.414) a fine blade named for your family, no armour, bread only: and someone will want it back
			StoryKit.heirloom(player);
		}
		if (landed == Start.TRADER) {
			// (0.7.414) a pack mule and its goods (StoryKit), and a little money
			give(player, purse(player, TRADER_SILVER));
			StoryKit.pack(player);
		}
		if (landed == Start.PUP) {
			// (0.7.414) a pup at heel, and bones to feed it
			give(player, new ItemStack(Items.BONE, PUP_BONES));
			StoryKit.pup(player);
		}
		if (landed == Start.NOBODIES) {
			// (0.7.414) three poor hands, hired to you from the first minute
			StoryKit.nobodies(player);
		}
		if (start == Start.SWORN) {
			// (0.7.414) a watch recruit's manacles, for walking a downed outlaw to the cells
			give(player, new ItemStack(Manacles.ITEM, Manacles.RECRUIT_PAIRS));
		}
		if (raised.key.equals("merchants")) {
			give(player, purse(player, MERCHANT_SILVER));
		}
	}

	/** Puts on armour (and a shield in the off hand) where that slot is free; anything else goes in the pack. */
	private static void equip(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty() || RAGS_ONLY) {
			return;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		EquipmentSlot slot = equippable != null && equippable.slot().isArmor() ? equippable.slot() : stack.is(Items.SHIELD) ? EquipmentSlot.OFFHAND : null;
		if (slot != null && player.getItemBySlot(slot).isEmpty()) {
			player.setItemSlot(slot, stack);
		} else {
			give(player, stack);
		}
	}

	/** {@code n} of the first of {@code ids} that is an item here. */
	private static ItemStack any(int n, String... ids) {
		for (String id : ids) {
			java.util.Optional<Holder.Reference<Item>> item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(id));
			if (item.isPresent()) {
				return new ItemStack(item.get().value(), n);
			}
		}
		return ItemStack.EMPTY;
	}

	/** Ground a start may dig or build on: the land's own dirt, stone and sand, or a plant. */
	private static boolean natural(BlockState state) {
		return state.isAir() || state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
			|| state.is(net.minecraft.tags.BlockTags.SAND) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL);
	}

	/** A fallen-from-the-sky crater round {@code center} (natural ground only): a shallow bowl lined with coarse dirt and gravel. Returns its floor. */
	private static BlockPos crater(ServerLevel level, BlockPos center, RandomSource random) {
		BlockPos floor = center;
		for (int dx = -CRATER_RADIUS; dx <= CRATER_RADIUS; dx++) {
			for (int dz = -CRATER_RADIUS; dz <= CRATER_RADIUS; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > CRATER_RADIUS + 0.3) {
					continue;
				}
				int depth = Math.max(0, (int)Math.round(CRATER_DEPTH * (1.0 - d / (CRATER_RADIUS + 1.0)) + 0.4));
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz)).below();
				boolean dug = true;
				for (int k = 0; k < depth; k++) {
					BlockPos at = top.below(k);
					if (!natural(level.getBlockState(at)) || !natural(level.getBlockState(at.above()))) {
						dug = false;
						break;
					}
					level.setBlock(at.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
					level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				}
				BlockPos bottom = top.below(dug ? depth : 0);
				if (natural(level.getBlockState(bottom)) && !level.getBlockState(bottom).isAir()) {
					level.setBlock(bottom, (random.nextInt(3) == 0 ? net.minecraft.world.level.block.Blocks.GRAVEL : net.minecraft.world.level.block.Blocks.COARSE_DIRT)
						.defaultBlockState(), 3);
				}
				if (dx == 0 && dz == 0) {
					floor = bottom.above();
				}
			}
		}
		return floor;
	}

	/**
	 * What's left of your home after the fire, round {@code center} (on natural ground only): low broken walls of charred
	 * timber and blackened stone, ash underfoot and a cold hearth.
	 */
	/**
	 * (0.7.501, Andy: "The burned village should spawn an abandoned village structure from vanilla, but replace the cobwebs with fire,
	 * and obviously the chaos will ensue from there") a vanilla abandoned plains village ({@code burned_village/town_centers}: the
	 * zombie town centres, which lead on into the zombie streets, houses and zombie villagers) raised {@link #BURNING_AWAY} blocks off
	 * the ruin you wake in (so you wake inside it), and every cobweb in it set alight. The fire spreads as fire does.
	 */
	private static void burningVillage(ServerLevel level, BlockPos ruin, RandomSource random) {
		var pool = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.TEMPLATE_POOL)
			.get(JaymosKingdoms.id("burned_village/town_centers")).orElse(null);
		if (pool == null) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2.0;
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			ruin.offset((int)Math.round(Math.cos(angle) * BURNING_AWAY), 0, (int)Math.round(Math.sin(angle) * BURNING_AWAY)));
		try {
			if (!net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement.generateJigsaw(level, pool,
				net.minecraft.resources.Identifier.withDefaultNamespace("bottom"), BURNING_DEPTH, at, false)) {
				return;
			}
		} catch (RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't raise the burning village at {}", at, e);
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -BURNING_REACH; dx <= BURNING_REACH; dx++) {
			for (int dz = -BURNING_REACH; dz <= BURNING_REACH; dz++) {
				for (int dy = -BURNING_BELOW; dy <= BURNING_ABOVE; dy++) {
					pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.COBWEB)) {
						level.setBlock(pos, net.minecraft.world.level.block.BaseFireBlock.getState(level, pos), 3);
					}
				}
			}
		}
	}

	private static void burnedRuin(ServerLevel level, BlockPos center, RandomSource random) {
		net.minecraft.world.level.block.Block[] wall = {net.minecraft.world.level.block.Blocks.STRIPPED_DARK_OAK_LOG, net.minecraft.world.level.block.Blocks.BLACKSTONE,
			net.minecraft.world.level.block.Blocks.COBBLESTONE, net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS, net.minecraft.world.level.block.Blocks.COAL_BLOCK};
		for (int dx = -RUIN_HALF; dx <= RUIN_HALF; dx++) {
			for (int dz = -RUIN_HALF; dz <= RUIN_HALF; dz++) {
				BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));
				BlockState under = level.getBlockState(feet.below());
				if (!natural(under) || under.isAir()) {
					continue;
				}
				boolean edge = Math.abs(dx) == RUIN_HALF || Math.abs(dz) == RUIN_HALF;
				if (edge) {
					// broken walls: some gone to the ground, some a block or two still standing
					int height = random.nextInt(4) == 0 ? 0 : 1 + random.nextInt(2);
					for (int k = 0; k < height; k++) {
						if (natural(level.getBlockState(feet.above(k)))) {
							level.setBlock(feet.above(k), wall[random.nextInt(wall.length)].defaultBlockState(), 3);
						}
					}
				} else {
					// ash and charred floorboards
					level.setBlock(feet.below(), (random.nextInt(3) == 0 ? net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS
						: net.minecraft.world.level.block.Blocks.COARSE_DIRT).defaultBlockState(), 3);
					if (natural(level.getBlockState(feet)) && !level.getBlockState(feet).isAir()) {
						level.setBlock(feet, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
		// the cold hearth, out
		BlockPos hearth = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(1, 0, 1));
		if (level.getBlockState(hearth).isAir()) {
			level.setBlock(hearth, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState()
				.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false), 3);
		}
	}

	/** The standing barbarian camp nearest {@code from}, or null. */
	private static @Nullable Faction nearestCamp(MinecraftServer server, BlockPos from) {
		return nearestCamp(server, from, false);
	}

	/**
	 * (0.7.412) The same; {@code sparing}: only a plain barbarian camp (or a pirate cove) that takes in outlaws ({@link Barbarians#sparingCamp}).
	 * (Tiv) Only a camp nobody else started at: with none free, the story raises a new one as for a world with none.
	 */
	private static @Nullable Faction nearestCamp(MinecraftServer server, BlockPos from, boolean sparing) {
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		ServerLevel level = Factions.level(server);
		for (Faction f : Factions.all(server)) {
			if (f.is(FactionType.BARBARIAN) && !f.fallen() && f.seat() != null && (!sparing || Barbarians.sparingCamp(server, f.id()))
				&& freePlace(level, f.seat())) {
				double d = f.seat().distSqr(from);
				if (d < bestD) {
					bestD = d;
					best = f;
				}
			}
		}
		return best;
	}

	/** (0.7.412) The starts that tell you a line on waking (lang {@code message.jaymoskingdoms.story.<start>}). */
	static final Set<Start> HARD_MESSAGES = Set.of(Start.CAPTIVE, Start.TIMELOST, Start.CHAINED, Start.ROCK_BOTTOM, Start.SENTENCED, Start.EVICTED,
		Start.HIDEOUT, Start.CAST_OUT, Start.GREENSKIN, Start.HEIRLOOM, Start.TRADER, Start.PUP, Start.NOBODIES, Start.LAB, Start.ANDROID, Start.MOON_CURSED);

	/**
	 * (0.7.417, Start Together) Where a player's road set them down: the start that came to pass, the realm it named (-1: none),
	 * the world and spot, the server tick, and the camp that holds them ({@code camp}, -1: none; a chained player's). (Tiv) Whose
	 * landing it is ({@code who}) and the place it was in ({@code place}, {@link Born#place}), shared with a mate who wakes beside them.
	 */
	private record Landing(Start start, int realm, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos pos, long tick, int camp,
		java.util.UUID who, BlockPos place) {
	}

	/** (0.7.417) Player → where their road last set them down (kept only while the server runs: the window is minutes). */
	private static final java.util.Map<java.util.UUID, Landing> LANDINGS = new java.util.HashMap<>();

	/**
	 * (0.7.417, Start Together) The landing of the party mate whose road {@code player} shares: the latest, within
	 * {@link #TOGETHER_WINDOW_TICKS}, of the same start (and the same realm if the road names one), whose world is loaded; null for none.
	 */
	private static @Nullable Landing together(ServerPlayer player, Start start, @Nullable Faction named) {
		MinecraftServer server = player.level().getServer();
		long now = server.getTickCount();
		int realm = named == null ? -1 : named.id();
		Landing best = null;
		for (ServerPlayer other : Parties.members(player)) {
			Landing l = other == player ? null : LANDINGS.get(other.getUUID());
			if (l != null && l.start == start && (!start.realm || l.realm == realm) && l.tick <= now && now - l.tick <= TOGETHER_WINDOW_TICKS
				&& server.getLevel(l.dimension) != null && (best == null || l.tick > best.tick)) {
				best = l;
			}
		}
		return best;
	}

	/**
	 * (0.7.417) Records where {@code player}'s road set them down, for the party mates who choose it after; (Tiv) and, for good,
	 * that {@code place} is theirs ({@link #BORN}).
	 */
	private static void landedAt(ServerPlayer player, Start start, int realm, BlockPos pos, int camp, BlockPos place) {
		LANDINGS.put(player.getUUID(), new Landing(start, realm, player.level().dimension(), pos.immutable(), player.level().getServer().getTickCount(), camp,
			player.getUUID(), place.immutable()));
		born(player, player.level(), place, pos);
	}

	/** (Tiv) Notes for good that {@code player}'s start was {@code place} on {@code level}, where they woke at {@code wake} ({@link #BORN}). */
	private static void born(ServerPlayer player, ServerLevel level, BlockPos place, BlockPos wake) {
		ServerLevel overworld = player.level().getServer().overworld();
		java.util.Map<String, Born> all = new java.util.HashMap<>(overworld.getAttachedOrElse(BORN, java.util.Map.of()));
		all.put(player.getUUID().toString(), new Born(level.dimension().identifier().toString(), place.asLong(), wake.asLong()));
		overworld.setAttached(BORN, java.util.Map.copyOf(all));
	}

	/**
	 * (Tiv) For the starts placed elsewhere (Jaymo's Powers' other worlds, its future, lab and workshop; a whaler's harbour):
	 * whether a place on a level is free of everyone's start but {@code player}'s own and their mate's ({@code mate}, null:
	 * none), none within {@code apart} blocks. It reads the starts each time it's asked, so one placed after a wait (the
	 * future's, a whaler's) sees those made meanwhile.
	 */
	private static java.util.function.BiPredicate<ServerLevel, BlockPos> freeFor(ServerPlayer player, java.util.@Nullable UUID mate, int apart) {
		MinecraftServer server = player.level().getServer();
		String self = player.getUUID().toString(), shared = mate == null ? "" : mate.toString();
		return (level, at) -> {
			java.util.Map<String, Born> all = server.overworld().getAttachedOrElse(BORN, java.util.Map.of());
			String dimension = level.dimension().identifier().toString();
			for (java.util.Map.Entry<String, Born> e : all.entrySet()) {
				Born b = e.getValue();
				if (!e.getKey().equals(self) && !e.getKey().equals(shared) && b.dimension.equals(dimension)
					&& flat(BlockPos.of(b.place), at) < (double) apart * apart) {
					return false;
				}
			}
			return true;
		};
	}

	/** Everyone's start but {@code player}'s own (a copy to take a mate's out of). */
	private static java.util.Map<String, Born> others(ServerPlayer player) {
		java.util.Map<String, Born> all = new java.util.HashMap<>(player.level().getServer().overworld().getAttachedOrElse(BORN, java.util.Map.of()));
		all.remove(player.getUUID().toString());
		return all;
	}

	/** (Tiv) Whether a start's place at {@code at} on {@code level} is free: nobody else's start was within {@link #PLACE_APART} of it. */
	private static boolean freePlace(ServerLevel level, BlockPos at) {
		return clear(level, at, PLACE_APART, true);
	}

	/** (Tiv) Whether {@code at} on {@code level} is free to wake at: nobody else woke within {@link #START_APART} of it. */
	private static boolean freeSpot(ServerLevel level, BlockPos at) {
		return clear(level, at, START_APART, false);
	}

	/** No one else's start ({@link #OTHERS}) within {@code apart} blocks of {@code at}: their place ({@code place}), else where they woke. */
	private static boolean clear(ServerLevel level, BlockPos at, int apart, boolean place) {
		String dimension = level.dimension().identifier().toString();
		for (Born b : OTHERS.values()) {
			if (b.dimension.equals(dimension) && flat(BlockPos.of(place ? b.place : b.wake), at) < (double) apart * apart) {
				return false;
			}
		}
		return true;
	}

	/** The horizontal distance between {@code a} and {@code b}, squared. */
	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/**
	 * (Tiv) What {@code find} finds nearest {@code from}, if {@code free}; past a taken one, looked for again from
	 * {@link #FREE_STEP} blocks further off each time (a random way), at most {@link #FREE_TRIES} times. Null if none free turned up.
	 */
	private static <T> @Nullable T lookPast(BlockPos from, java.util.function.Function<BlockPos, @Nullable T> find, java.util.function.Predicate<T> free,
		RandomSource random) {
		BlockPos at = from;
		for (int i = 0; i <= FREE_TRIES; i++) {
			T found = find.apply(at);
			if (found == null) {
				return null;
			}
			if (free.test(found)) {
				return found;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			at = from.offset(Mth.floor(Math.cos(angle) * FREE_STEP * (i + 1)), 0, Mth.floor(Math.sin(angle) * FREE_STEP * (i + 1)));
		}
		return null;
	}

	/**
	 * (0.7.417) A dry, open place {@link #TOGETHER_MIN}-{@link #TOGETHER_MAX} blocks from where a party mate woke (a few blocks
	 * up and down from their spot, so a cave or a cell works as well as open ground); null if none.
	 */
	private static @Nullable BlockPos beside(ServerLevel level, BlockPos at, RandomSource random) {
		int[] made = {NEW_CHUNKS};
		for (int i = 0; i < TOGETHER_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int d = TOGETHER_MIN + random.nextInt(TOGETHER_MAX - TOGETHER_MIN + 1);
			int x = at.getX() + Mth.floor(Math.cos(angle) * d), z = at.getZ() + Mth.floor(Math.sin(angle) * d);
			if (!reach(level, x, z, made)) {
				continue;
			}
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos feet = new BlockPos(x, at.getY() + dy, z);
				if (standable(level, feet)) {
					return feet;
				}
			}
		}
		return null;
	}

	/**
	 * (0.7.417) Where a start's place is: beside the party mate who woke here first ({@code mate}), else {@link #ground}
	 * {@code min}-{@code max} blocks from {@code center}. (Tiv) Never where someone else woke ({@link #freeSpot}): with no free
	 * spot that near, up to {@link #FREE_WIDEN} blocks further out (from the same few new chunks).
	 */
	private static @Nullable BlockPos spot(ServerLevel level, @Nullable Landing mate, BlockPos center, int min, int max, RandomSource random) {
		BlockPos by = mate == null ? null : beside(level, mate.pos, random);
		if (by != null) {
			return by;
		}
		int[] made = {NEW_CHUNKS};
		BlockPos at = ground(level, center, min, max, random, p -> freeSpot(level, p), made);
		return at != null ? at : ground(level, center, max, max + FREE_WIDEN, random, p -> freeSpot(level, p), made);
	}

	/** (0.7.417) The camp a chained mate is held by, if it still stands. */
	private static @Nullable Faction mateCamp(MinecraftServer server, @Nullable Landing mate) {
		Faction camp = mate == null || mate.camp < 0 ? null : Factions.get(server, mate.camp);
		return camp != null && !camp.fallen() ? camp : null;
	}

	/**
	 * (0.7.417) A party mate who took a road lately, as the road page shows it ("took this road; you'll wake beside them"): their
	 * name, the road's key, and the realm's name if it named one ("" for none).
	 */
	public record Mate(String name, String road, String realm) {
	}

	/** (0.7.417) The party mates of {@code player} who landed on a road within the window. */
	static List<Mate> mates(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		long now = server.getTickCount();
		List<Mate> out = new ArrayList<>();
		for (ServerPlayer other : Parties.members(player)) {
			Landing l = other == player ? null : LANDINGS.get(other.getUUID());
			if (l != null && l.tick <= now && now - l.tick <= TOGETHER_WINDOW_TICKS) {
				Faction realm = l.realm >= 0 ? Factions.get(server, l.realm) : null;
				out.add(new Mate(other.getGameProfile().name(), l.start.name().toLowerCase(Locale.ROOT), realm == null ? "" : realm.name()));
			}
		}
		return List.copyOf(out);
	}

	/** (0.7.223) An escaped captive's camp, out to get them back until {@code until} (server tick). */
	private record Hunt(int camp, long until) {
	}

	/** Who's being hunted by the camp they escaped (kept only while the server runs: a day is short). */
	private static final java.util.Map<java.util.UUID, Hunt> HUNTS = new java.util.HashMap<>();

	/** (0.7.412) {@code player} slipped their shackles: camp {@code camp} hunts them from now for the day. */
	static void startHunt(MinecraftServer server, ServerPlayer player, int camp) {
		HUNTS.put(player.getUUID(), new Hunt(camp, server.getTickCount() + HUNT_TICKS));
	}

	/** The camp's raiders near a hunted captive come after them, till the day is out (or the camp spares them). */
	private static void hunt(MinecraftServer server) {
		if (HUNTS.isEmpty() || server.getTickCount() % HUNT_EVERY != 0) {
			return;
		}
		long now = server.getTickCount();
		HUNTS.entrySet().removeIf(e -> e.getValue().until <= now);
		for (java.util.Map.Entry<java.util.UUID, Hunt> e : HUNTS.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || !Barbarians.prey(e.getValue().camp, player)) {
				continue;
			}
			for (Folk folk : player.level().getEntitiesOfClass(Folk.class, player.getBoundingBox().inflate(HUNT_RADIUS))) {
				if (folk.factionId() == e.getValue().camp && Barbarians.isRaider(folk) && folk.getTarget() == null) {
					folk.setTarget(player);
				}
			}
		}
	}

	/** (0.7.420) Set while the road's kit is made for someone who chose nothing but rags: nothing is given or put on. */
	private static boolean RAGS_ONLY;

	/** (0.7.498) A start's little purse: {@code worth} emeralds' worth in the coin of the place they woke in (silver in the wild). */
	private static ItemStack purse(ServerPlayer player, int worth) {
		return PurseContents.filled(Currencies.at(player.level(), player.blockPosition()), Money.worth(worth));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (RAGS_ONLY) {
			return;
		}
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	/** Raises {@code player}'s reputation with {@code realm} to at least {@code rep}. */
	private static void atLeast(ServerPlayer player, Faction realm, int rep) {
		int now = Reputation.get(player, realm.id());
		if (now < rep) {
			Reputation.change(player, realm, rep - now);
		}
	}

	/** Lowers {@code player}'s reputation with {@code realm} to at most {@code rep}. */
	private static void atMost(ServerPlayer player, Faction realm, int rep) {
		int now = Reputation.get(player, realm.id());
		if (now > rep) {
			Reputation.change(player, realm, rep - now);
		}
	}

	/**
	 * Unmakes {@code player}'s heritage, class and story (not the roads they took: {@link RoadLocks#TOOK}), so the whole life
	 * story can be told again. From {@code /kingdom story reset} and (0.7.419) the Writ of Rebirth.
	 */
	static void unmake(ServerPlayer p) {
		p.removeAttached(STORY);
		p.removeAttached(ROAD);
		p.removeAttached(GENDER);
		p.removeAttached(CALLING);
		p.removeAttached(STRANDED);
		StoryHomes.clear(p);
		StoryHard.clear(p);
		StoryKit.clear(p);
		LifeAims.clear(p);
		Flaws.clear(p);
		DEPARTED.remove(p.getUUID());
		ROAD_ASKED.remove(p.getUUID());
		LANDINGS.remove(p.getUUID());
		TIMELOST_PENDING.remove(p.getUUID());
		// (Tiv) the old life's start is free for someone else
		ServerLevel overworld = p.level().getServer().overworld();
		java.util.Map<String, Born> born = overworld.getAttachedOrElse(BORN, java.util.Map.of());
		if (born.containsKey(p.getUUID().toString())) {
			java.util.Map<String, Born> left = new java.util.HashMap<>(born);
			left.remove(p.getUUID().toString());
			overworld.setAttached(BORN, java.util.Map.copyOf(left));
		}
		Classes.clear(p);
		Heritage.clear(p);
	}

	/**
	 * (0.7.466) Whether {@code player} can be born again (their client shows the story screen). Called by Jaymo's Powers
	 * (reflection) before the Other World offers it.
	 */
	public static boolean canReincarnate(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, Open.TYPE);
	}

	/**
	 * (0.7.466) Andy: "when respawning from heaven or leaving the aether you are given the option to start over, or
	 * reincarnate, giving you access to all the different starts you may have unlocked." Called by Jaymo's Powers
	 * (reflection) when one of the dead chooses to be born again (the Judge, the fortune teller, or the moment they are let
	 * go): as a Writ of Rebirth does, their heritage, class and story are unmade and the whole life story is told again,
	 * with every road they or their group have opened ({@link RoadLocks}). False if their client has no story screen.
	 */
	public static boolean reincarnate(ServerPlayer player) {
		if (!canReincarnate(player)) {
			return false;
		}
		unmake(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story.reborn").withStyle(ChatFormatting.LIGHT_PURPLE));
		Classes.next(player);
		return true;
	}

	/**
	 * (0.7.583) Whether {@code player} is still making their character: the story, MCA's creator, the heritage or the class
	 * still to choose, or still held in stasis. Called by Jaymo's Powers (reflection) to keep a new life's screen black.
	 */
	public static boolean making(ServerPlayer player) {
		return StoryStasis.owed(player) || StoryStasis.holding(player) || Classes.unmade(player);
	}

	/**
	 * (0.7.573) Andy, on the third death: "you should get reprompted completely, like with a new character creator, new
	 * quests skills, etc. EVERYTHING resets." Jaymo's Powers has already wiped the old life's progress (skills, quests,
	 * reputation, tiers...); here MCA's creator is owed anew (a new look and name, single again) and the whole life story is
	 * told again, as {@link #reincarnate} does. False if their client has no story screen.
	 */
	public static boolean reincarnateAnew(ServerPlayer player) {
		if (!canReincarnate(player)) {
			return false;
		}
		Skills.refresh(player);
		// Tiv: "EVERYTHING should be reset on a reincarnate". MCA's villagers forget the player (hearts, memories,
		// relationships), the player leaves their party, and no captain remembers having met them. This runs before
		// MCA's creator opens, so the reset can't wipe the new character being made in it.
		if (McaCompat.LOADED) {
			McaBridge.forgetPlayer(player);
		}
		Parties.reborn(player);
		Captains.forgetPlayer(player.level().getServer(), player);
		McaCompat.newDestiny(player);
		return reincarnate(player);
	}

	/** {@code /kingdom story reset <players>} (ops): unmakes their heritage, class and story, and asks for their story again. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("story").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("reset").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					unmake(p);
					ask(p, true);
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.story.reset", count), true);
				return n;
			})));
	}
}
