package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.509) The villagers' and folk's faiths (docs/Patrons-Plan.md section 10). Every villager and townsperson of flesh and
 * blood holds one patron, drawn once and kept: most humans The Truth (clerics always, Faith-creed realms' folk always),
 * non-human folk mostly their blood's patron. The Truth and the seven, and the named rival pairs, think less of each other:
 * a little dearer, slower to warm, more guard trouble, cooler neighbours and good name. It is added at each cost point
 * beside {@link Prejudice}, never inside it, so mutters about blood stay about blood.
 */
public final class Faiths {
	/** On a villager or folk: their faith (a Patron key). Drawn once, kept. Not synced. */
	static final AttachmentType<String> FAITH = AttachmentRegistry.create(JaymosKingdoms.id("faith"), b -> b.persistent(Codec.STRING));

	static final float TRUTH_SHARE_HUMAN = 0.94F, LEAN_SHARE = 0.7F, CONVERT_SHARE = 0.05F;
	static final float FAITH_RIVALRY = 0.75F, RIVAL_MINDS = 1.0F, FAITH_CREED_HARSH = 1.5F;
	/** Per degree, as {@link Prejudice}. */
	static final float PRICE_SHARE = 0.06F, HEARTS_CUT = 0.15F, REP_GAIN_CUT = 0.08F, REP_LOSS_ADD = 0.08F;
	/** (0.7.513) A shared faith: good name won and lost, clerics' coldness, a kin's heart. */
	static final float REP_SHARED_GAIN = 0.15F, REP_SHARED_EASE = 0.15F, CLERIC_COLD = 1.5F, KIN_HEART_CHANCE = 0.5F, TRIAL_MINDS_REP = Turning.TRIAL_MINDS;
	static final int KIN_HEARTS = 1;
	static final float HEARTS_KEPT_MIN = 0.2F, REP_KEPT_MIN = 0.25F;
	static final int NEIGHBOUR_STEP = 8, GUARD_SEARCH_W = 1;
	static final float GUARD_HALT_SOONER = 0.5F;
	/** (0.7.509) Andy: Dawnborn "start crowned sun" and "say praise the sun". Most Dawnborn hold the Crowned Sun; at sunrise
	 *  some of the Sun's folk near a player call out to it. */
	static final float DAWNBORN_SUN_SHARE = 0.85F, SUNRISE_PRAISE_CHANCE = 0.5F;
	static final int SUNRISE_WINDOW = 1200, SUNRISE_PRAISE_LINES = 3;
	static final double SUNRISE_PRAISE_RANGE = 32.0;
	private static long praisedDay = Long.MIN_VALUE;

	/** What a realm holds: its state patron ({@link RealmFaiths}), and how hard it takes a clash. */
	record RealmFaith(Patron patron, boolean seven, float weight) {
	}

	private Faiths() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (holds(entity)) {
				of(entity);
			}
		});
	}

	/** Who holds a faith: a villager (MCA's included) or a townsperson of flesh and blood. */
	static boolean holds(Entity e) {
		return e instanceof Villager || e instanceof Folk folk && VillagerHeritage.humanFolk(folk.role());
	}

	/**
	 * {@code kind} passes for human here: no blood, or a blood no further from human than a Heartlander, but not the Verdants or
	 * the broods. (0.7.545) That is the Heartlander, Nightborn, Earthling, Stormborn and Dawnborn (Andy: the rest "are
	 * actually all not human"); HeritageLooks reads it too, for whose eyes never glow.
	 */
	public static boolean human(Heritage.@Nullable Kind kind) {
		return kind == null || Prejudice.degree(kind) == 0 && kind != Heritage.Kind.VERDANT && !kind.brood;
	}

	/** The patron a blood leans to (a human blood: the Hearthkeeper, only used for a rare human of the seven without a trade lean). */
	static Patron lean(Heritage.@Nullable Kind kind) {
		if (kind == null) {
			return Patron.HEARTHKEEPER;
		}
		return switch (kind) {
			case TIDEBORN -> Patron.DEEP_MOTHER;
			case STONEBORN, BIOBUILT, ORC -> Patron.ANVIL_FATHER;
			case SYLVAN, BEASTFOLK -> Patron.GREEN_HUNTSMAN;
			case VERDANT -> Patron.HEARTHKEEPER;
			case WARRIORBORN -> Patron.CROWNED_SUN;
			case HOLLOW, ENDBORN -> Patron.LANTERN_WARDEN;
			// ((0.7.545) the bloods Andy said aren't human: fire to the Ash Whisperer, the cold wilds to the Huntsman, the
			// deep stone to the Anvil-Father, a dragon's to the war god, the Divine-born and the Rubberfolk to the Hearthkeeper)
			case EMBERBORN -> Patron.ASH_WHISPERER;
			case FROSTBORN -> Patron.GREEN_HUNTSMAN;
			case DEEPBORN -> Patron.ANVIL_FATHER;
			case DRAGONBLOOD -> Patron.CROWNED_SUN;
			case GOBLIN -> Patron.ASH_WHISPERER;
			default -> Patron.HEARTHKEEPER;
		};
	}

	/** (0.7.509) The patron a player's blood starts with: Dawnborn the Crowned Sun, other humans The Truth, the rest their lean. */
	static Patron startOf(Heritage.Kind kind) {
		if (kind == Heritage.Kind.DAWNBORN) {
			return Patron.CROWNED_SUN;
		}
		return human(kind) ? Patron.TRUTH : lean(kind);
	}

	/** (0.7.509) Once a day at sunrise, some villagers and townsfolk of the Crowned Sun near a player praise the sun aloud. */
	static void sunrise(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long clock = level.getOverworldClockTime();
		long day = Math.floorDiv(clock, 24000L);
		if (Math.floorMod(clock, 24000L) >= SUNRISE_WINDOW || day == praisedDay) {
			return;
		}
		praisedDay = day;
		java.util.Set<Entity> spoken = new java.util.HashSet<>();
		for (ServerPlayer sp : level.players()) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(SUNRISE_PRAISE_RANGE),
				x -> x.isAlive() && holds(x) && level.canSeeSky(x.blockPosition().above()))) {
				if (spoken.add(e) && of(e) == Patron.CROWNED_SUN && e.getRandom().nextFloat() < SUNRISE_PRAISE_CHANCE) {
					Speech.say(level, e, Component.translatable("speech.jaymoskingdoms.praise_the_sun." + e.getRandom().nextInt(SUNRISE_PRAISE_LINES)),
						Speech.GOLD, SUNRISE_PRAISE_RANGE);
				}
			}
		}
	}

	// ---- whose faith -----------------------------------------------------------------------------------------------------

	/** {@code e}'s faith (drawn now, on the server, if it hasn't one); a priest is always The Truth. Null for what holds none. */
	public static @Nullable Patron of(Entity e) {
		if (!holds(e)) {
			return null;
		}
		boolean server = !e.level().isClientSide();
		if (server && unseeing(e)) {
			// Andy (2026-11-11): the Unseeing's belief is "the faith barbarians follow": none of the eight
			return null;
		}
		Patron stored = Patron.byKey(e.getAttached(FAITH));
		if (server && Accusations.priest(e) && stored != Patron.TRUTH) {
			e.setAttached(FAITH, Patron.TRUTH.key);
			return Patron.TRUTH;
		}
		if (stored == null && server && e instanceof LivingEntity living) {
			stored = draw(living);
			e.setAttached(FAITH, stored.key);
		}
		return stored;
	}

	/**
	 * A barbarian camp's own (its raiders, chief, casters and workers; not the illager, goblin, orc or pirate camps): they
	 * keep the Unseeing's belief (Andy, 2026-11-11: "these can be the faith barbarians follow"), so they hold no patron.
	 * The Unseeing themselves, the cult's shunned and blinded, are Powers' ({@code power/blindness/Unseeing}).
	 */
	static boolean unseeing(Entity e) {
		MinecraftServer server = e.level().getServer();
		if (server == null || !(e instanceof Worker w) || !(e instanceof Folk folk ? Barbarians.isBarbarian(folk) : Barbarians.isCampVillager(e))) {
			return false;
		}
		Faction f = Factions.get(server, w.factionId());
		return f != null && f.type() == FactionType.BARBARIAN && Camps.BARBARIAN.equals(Camps.kind(server, f.id()));
	}

	private static Patron draw(LivingEntity who) {
		RandomSource random = who.getRandom();
		MinecraftServer server = who.level().getServer();
		Faction f = realm(who);
		if (server != null && f != null && GreatWars.creed(server, f) == GreatWars.Creed.FAITH) {
			return Patron.TRUTH;
		}
		Heritage.Kind kind = VillagerHeritage.of(who);
		if (kind == Heritage.Kind.DAWNBORN) {
			return random.nextFloat() < DAWNBORN_SUN_SHARE ? Patron.CROWNED_SUN : Patron.TRUTH;
		}
		if (human(kind)) {
			if (random.nextFloat() < TRUTH_SHARE_HUMAN) {
				return Patron.TRUTH;
			}
			Patron trade = byTrade(who);
			return trade != null ? trade : seven(random);
		}
		float roll = random.nextFloat();
		if (roll < LEAN_SHARE) {
			return lean(kind);
		}
		return roll < 1.0F - CONVERT_SHARE ? seven(random) : Patron.TRUTH;
	}

	private static Patron seven(RandomSource random) {
		Patron[] all = Patron.values();
		return all[random.nextInt(all.length - 1)];
	}

	/** The patron a trade leans to, or null. */
	private static @Nullable Patron byTrade(LivingEntity who) {
		if (who instanceof Folk folk) {
			return switch (folk.role()) {
				case Folk.MINER -> Patron.ANVIL_FATHER;
				case Folk.WOODCUTTER -> Patron.GREEN_HUNTSMAN;
				case Folk.FARMER -> Patron.HEARTHKEEPER;
				case Folk.GUARD, Folk.LORD, Folk.KING -> Patron.CROWNED_SUN;
				default -> null;
			};
		}
		if (who instanceof Villager v) {
			var p = v.getVillagerData().profession();
			if (p.is(VillagerProfession.FISHERMAN)) {
				return Patron.DEEP_MOTHER;
			}
			if (p.is(VillagerProfession.ARMORER) || p.is(VillagerProfession.TOOLSMITH) || p.is(VillagerProfession.WEAPONSMITH)) {
				return Patron.ANVIL_FATHER;
			}
			if (p.is(VillagerProfession.BUTCHER) || p.is(VillagerProfession.LEATHERWORKER) || p.is(VillagerProfession.SHEPHERD)) {
				return Patron.GREEN_HUNTSMAN;
			}
			if (p.is(VillagerProfession.FARMER)) {
				return Patron.HEARTHKEEPER;
			}
		}
		return null;
	}

	/** A new blood draws its faith again. */
	static void forget(LivingEntity villager) {
		villager.removeAttached(FAITH);
	}

	/** A child holds its mother's faith, else its father's. */
	static void inherit(LivingEntity child, @Nullable Entity mother, @Nullable Entity father) {
		Patron p = mother == null ? null : of(mother);
		if (p == null && father != null) {
			p = of(father);
		}
		if (p != null) {
			child.setAttached(FAITH, p.key);
		}
	}

	/** The realm {@code who} belongs to: a folk's own, or the banner over where they stand. */
	private static @Nullable Faction realm(LivingEntity who) {
		if (!(who.level() instanceof ServerLevel level)) {
			return null;
		}
		MinecraftServer server = level.getServer();
		if (who instanceof Folk folk) {
			return Factions.get(server, folk.factionId());
		}
		return Banners.owner(server, ChunkPos.containing(who.blockPosition()));
	}

	/** What a realm holds: null for a barbarian camp, else its state patron ({@link RealmFaiths}). */
	static @Nullable RealmFaith realmFaith(MinecraftServer server, @Nullable Faction f) {
		Patron p = RealmFaiths.patron(server, f);
		if (p == null) {
			return null;
		}
		return new RealmFaith(p, p.seven, GreatWars.creed(server, f) == GreatWars.Creed.FAITH ? FAITH_CREED_HARSH : 1.0F);
	}

	// ---- who minds ---------------------------------------------------------------------------------------------------------

	/**
	 * How much {@code who} (a villager or folk) holds {@code whom}'s faith against them, in {@link Prejudice}'s degree units:
	 * 0 for the same faith or none. {@code whom} is a sworn player or another villager.
	 */
	static float minds(LivingEntity who, Entity whom) {
		Patron mine = of(who);
		if (mine == null || who == whom) {
			return 0.0F;
		}
		if (whom instanceof Player p && Prejudice.masked(p)) {
			return 0.0F;
		}
		float weight = 1.0F;
		if (who.level() instanceof ServerLevel level) {
			RealmFaith rf = realmFaith(level.getServer(), realm(who));
			weight = rf == null ? 1.0F : rf.weight();
		}
		// the vampires' dark: the Lantern Warden's villagers mind an exposed vampire, a vampire villager minds the Lantern Warden's sworn
		if (mine == Patron.LANTERN_WARDEN && whom instanceof Player p && (Vampires.exposed(p) || Accusations.exposed(p) && Vampires.isVampire(p))) {
			return RIVAL_MINDS;
		}
		Patron theirs = whom instanceof Player p ? Patrons.of(p) : of(whom);
		if (theirs == null || theirs == mine) {
			return 0.0F;
		}
		if (Vampires.isVampire(who) && theirs == Patron.LANTERN_WARDEN) {
			return RIVAL_MINDS;
		}
		float m = mine.rival == theirs ? RIVAL_MINDS : mine.rivals(theirs) ? FAITH_RIVALRY * weight : 0.0F;
		if (whom instanceof Player p) {
			// (0.7.513) the old patron's faithful know an apostate; the Truth's scar
			m = Math.max(m, Turning.trial(p, mine) * Turning.TRIAL_MINDS * weight);
			if (mine == Patron.TRUTH) {
				m += Turning.scars(p, Patron.TRUTH) * Turning.SCAR_TRUTH_MINDS;
			}
		}
		// (0.7.513) clerics are colder to the seven's sworn players, not to villagers
		if (whom instanceof Player && Accusations.priest(who) && theirs.seven) {
			m *= CLERIC_COLD;
		}
		return m;
	}

	// ---- what it costs -------------------------------------------------------------------------------------------------------------

	/** {@code villager}'s offers to {@code player} (from {@code VillagerPriceMixin}, after blood's): dearer for a faith they distrust, cheaper for the Truth's flock. */
	public static void prices(AbstractVillager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel)) {
			return;
		}
		float m = minds(villager, player);
		boolean flock = Blessings.flock(villager, player);
		if (m <= 0.0F && !flock) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			int cost = offer.getBaseCostA().getCount();
			if (m > 0.0F) {
				offer.addToSpecialPriceDiff(Math.max(1, (int)Math.ceil(cost * PRICE_SHARE * m)));
			}
			if (flock) {
				offer.addToSpecialPriceDiff(-Math.max(1, (int)Math.floor(cost * Blessings.FLOCK_PRICE)));
			}
		}
	}

	/** Of {@code gained} hearts, how many {@code villager} takes back for {@code player}'s faith. */
	public static int hearts(Villager villager, ServerPlayer player, int gained) {
		if (gained <= 0) {
			return 0;
		}
		float m = minds(villager, player);
		if (m <= 0.0F) {
			return 0;
		}
		float kept = Math.max(HEARTS_KEPT_MIN, 1.0F - HEARTS_CUT * m);
		return gained - Math.max(1, Math.round(gained * kept));
	}

	/** Good name won or lost with {@code kingdom}: a sworn player whose faith clashes with the realm's wins it slower and loses it faster. */
	static int reputation(Faction kingdom, ServerPlayer player, int by) {
		if (by == 0 || Identities.hidden(player, kingdom.id())) {
			return by;
		}
		Patron mine = Patrons.of(player);
		RealmFaith rf = realmFaith(player.level().getServer(), kingdom);
		if (mine == null || rf == null) {
			return by;
		}
		Patron theirs = rf.patron();
		float m = 0.0F;
		if (Turning.trial(player, theirs) > 0.0F) {
			// the realm's faith you left
			m = TRIAL_MINDS_REP * Turning.trial(player, theirs);
		} else if (theirs == mine) {
			// (0.7.513) Andy: those who share your patron like you more
			return by > 0 ? Math.max(1, Math.round(by * (1.0F + REP_SHARED_GAIN * rf.weight()))) : Math.round(by * (1.0F - REP_SHARED_EASE));
		} else if (mine.rivals(theirs)) {
			m = mine.rival == theirs || theirs.rival == mine ? RIVAL_MINDS : FAITH_RIVALRY;
		}
		if (theirs == Patron.TRUTH) {
			m += Turning.scars(player, Patron.TRUTH) * Turning.SCAR_TRUTH_MINDS;
		}
		if (m <= 0.0F) {
			return by;
		}
		m *= rf.weight();
		if (by > 0) {
			return Math.max(1, Math.round(by * Math.max(REP_KEPT_MIN, 1.0F - REP_GAIN_CUT * m)));
		}
		return Math.round(by * (1.0F + REP_LOSS_ADD * m));
	}

	/** (0.7.513) Of {@code gained} hearts, one more now and then when {@code villager} holds the faith {@code player} is sworn to. */
	public static int kinHearts(Villager villager, ServerPlayer player, int gained) {
		if (gained <= 0 || Prejudice.masked(player)) {
			return 0;
		}
		Patron mine = Patrons.of(player);
		return mine != null && of(villager) == mine && player.getRandom().nextFloat() < KIN_HEART_CHANCE ? KIN_HEARTS : 0;
	}

	/** A guard's extra keenness to search {@code player} for their faith. */
	static int guard(Mob guard, ServerPlayer player) {
		return Math.round(minds(guard, player) * GUARD_SEARCH_W);
	}

	/** How long a guard waits between halting {@code player}: sooner, the more he minds their faith. */
	static long haltGap(Mob guard, ServerPlayer player, long ticks) {
		float m = minds(guard, player);
		return m <= 0.0F ? ticks : (long)(ticks / (1.0F + GUARD_HALT_SOONER * m));
	}

	/** How much cooler two villagers start out towards each other for their faiths. */
	static int neighbourLean(Villager a, Villager b) {
		return Math.round(NEIGHBOUR_STEP * Math.max(minds(a, b), minds(b, a)));
	}

	/** For MCA's chat AI: what {@code villager} holds, and what they think of {@code player}'s faith, or "". */
	static String prompt(Villager villager, ServerPlayer player) {
		Patron mine = of(villager);
		if (mine == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(Component.translatable("prompt.jaymoskingdoms.faith.own", mine.named()).getString());
		Patron theirs = Patrons.of(player);
		if (theirs != null && !Prejudice.masked(player)) {
			if (theirs == mine) {
				sb.append(' ').append(Component.translatable("prompt.jaymoskingdoms.faith.same", theirs.named()).getString());
			} else if (Turning.trial(player, mine) > 0.0F) {
				sb.append(' ').append(Component.translatable("prompt.jaymoskingdoms.faith.apostate", mine.named()).getString());
			} else if (minds(villager, player) > 0.0F) {
				sb.append(' ').append(Component.translatable("prompt.jaymoskingdoms.faith.rival", theirs.named()).getString());
			}
		} else if (theirs == null && Turning.trial(player, mine) > 0.0F && !Prejudice.masked(player)) {
			sb.append(' ').append(Component.translatable("prompt.jaymoskingdoms.faith.apostate", mine.named()).getString());
		}
		return sb.toString();
	}
}
