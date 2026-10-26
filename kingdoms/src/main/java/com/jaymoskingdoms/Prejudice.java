package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.219) Andy (2026-10-26): "The more non-human the heritage the less well it is regarded amongst most. and everyone
 * knows it. the tide born, stone, warrior and sylvan fall into this". Each heritage is some way from human
 * ({@link #degree}): Tideborn the furthest, then Stoneborn, Warrior-born and Sylvan; the rest pass for human. The
 * heritage screen says so on the card.
 * <p>
 * Most townsfolk ({@link #minds}) hold it against a player (and against a neighbour) of such blood, the further from
 * human the more. Those who don't: anyone of the same blood, anyone not plainly human themselves, the kindhearted,
 * cheerful and devout, and a share of the rest (more of MCA's warm personalities, none of the crabby). A purist ruler's
 * own people ({@link Purists}) always mind, and harder. A full mask hides your blood from them.
 * <p>
 * The rivalry (Andy, the same day: "raising a sort of rivalry, between human and nonhumans across the entire world"):
 * many who were ridiculed left for villages of their own ({@link Havens}), and non-humans mind humans back, a little
 * anywhere ({@link #RIVALRY}) and hard in a haven ({@link #HAVEN_RIVALRY}), where a human is the one muttered at,
 * charged more and trusted slowly, and any non-human is welcome.
 * <ul>
 * <li><b>Talk</b> ({@link #tick}): they stare and mutter, children point, and the worst-regarded find folk edging away
 * from them. One who doesn't mind has a kind word now and then, and one of your own blood a knowing one.</li>
 * <li><b>Prices</b> ({@link #prices}): a trader who minds charges more.</li>
 * <li><b>Hearts</b> ({@link #hearts}): a chat, joke or hug with one who minds wins fewer of MCA's hearts.</li>
 * <li><b>Good name</b> ({@link #reputation}): won slower and lost faster with every realm.</li>
 * <li><b>Guards</b> ({@link #guard}): halt and search you more often.</li>
 * <li><b>Neighbours</b> ({@link #neighbourLean}): villagers start out cooler towards a neighbour of such blood.</li>
 * <li><b>MCA's chat AI</b> ({@link #prompt}) is told what they think of your blood.</li>
 * </ul>
 */
public final class Prejudice {
	/** How far from human each heritage is, by {@link Heritage.Kind} (0: passes for human). Andy's order: Tideborn, Stoneborn, Warrior-born, Sylvan. */
	static final int TIDEBORN = 4, STONEBORN = 3, WARRIORBORN = 2, SYLVAN = 1;
	/**
	 * (Havens) The rivalry: how much a non-human minds a human (as a degree), living in a haven, and anywhere else; a
	 * haven's good name for a human, likewise.
	 */
	static final float HAVEN_RIVALRY = 3.0F, RIVALRY = 1.0F;
	/** (Havens) Gossip of why their folk left the humans (a haven's), or of the rivalry (anyone), this often. */
	static final float HAVEN_GOSSIP_CHANCE = 0.3F, RIVALRY_GOSSIP_CHANCE = 0.06F;
	/** A purist's own people mind this many times as hard. */
	static final float PURIST_HARSH = 1.5F;
	/**
	 * Out of 100, how many of the rest don't mind (by MCA's personality: warm, plain, sour; crabby never). Drawn from who
	 * they are, so the same villager always feels the same.
	 */
	static final int TOLERANT_WARM = 55, TOLERANT_PLAIN = 25, TOLERANT_SOUR = 8;
	/** Prices: a share more per degree minded (at least one more). */
	static final float PRICE_SHARE = 0.06F;
	/** Hearts won with one who minds: a share off per degree (never below {@code HEARTS_KEPT_MIN} of them). */
	static final float HEARTS_CUT = 0.15F, HEARTS_KEPT_MIN = 0.2F;
	/** Good name: a share off gains and on to losses per degree (gains never below {@code REP_KEPT_MIN}). */
	static final float REP_GAIN_CUT = 0.08F, REP_LOSS_ADD = 0.08F, REP_KEPT_MIN = 0.25F;
	/** Guards: search and toll weight added per degree minded; halts come this many times sooner per degree. */
	static final int GUARD_SEARCH_W = 1;
	static final float GUARD_HALT_SOONER = 0.5F;
	/** Neighbours: a starting regard this much cooler per degree minded. */
	static final int NEIGHBOUR_STEP = 8;
	/** How often players are looked at (ticks); how far townsfolk notice; how close is too close for the worst-regarded. */
	static final int TICK = 40;
	static final double NOTICE_RANGE = 9.0, TOO_CLOSE = 2.5, SPEECH_RANGE = 12.0;
	/** Edging away only from this degree up. */
	static final int SHRINK_DEGREE = 3;
	/** A mutter: chance per look per degree, and at most once in this long per player (ticks). */
	static final float MUTTER_CHANCE = 0.05F;
	static final int MUTTER_TICKS = 20 * 50, SHRINK_TICKS = 20 * 6;
	/** A kind (or knowing) word: chance per look, at most once in this long per player. */
	static final float KIND_CHANCE = 0.04F;
	static final int KIND_TICKS = 20 * 240;
	/** Lines ({@code prejudice.jaymoskingdoms.<kind>.<n>}, from 0): mutters by how far from human (low 1-2, high 3-4). */
	static final int MUTTER_LOW_LINES = 5, MUTTER_HIGH_LINES = 5, CHILD_LINES = 4, SHRINK_LINES = 3, KIND_LINES = 4, KIN_LINES = 3;
	/** (Havens) The rivalry's lines: a non-human's mutter at a human (a haven's, elsewhere, a child's), a fair one's word, a haven's welcome, gossip. */
	static final int HAVEN_LINES = 5, RIVAL_LINES = 4, RIVAL_CHILD_LINES = 3, FAIR_LINES = 3, WELCOME_LINES = 4, HAVEN_GOSSIP_LINES = 5,
		RIVALRY_GOSSIP_LINES = 4;

	/** "player" → when they were last muttered at; last edged away from; last given a kind word. */
	private static final Map<UUID, Long> MUTTERED = new HashMap<>(), SHRUNK = new HashMap<>(), KIND = new HashMap<>();

	private Prejudice() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Prejudice::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MUTTERED.clear();
			SHRUNK.clear();
			KIND.clear();
		});
	}

	// ---- who minds -----------------------------------------------------------------------------------------------------

	/** How far from human {@code kind} is (0: passes for human). */
	public static int degree(Heritage.@Nullable Kind kind) {
		if (kind == null) {
			return 0;
		}
		return switch (kind) {
			case TIDEBORN -> TIDEBORN;
			case STONEBORN -> STONEBORN;
			case WARRIORBORN -> WARRIORBORN;
			case SYLVAN -> SYLVAN;
			default -> 0;
		};
	}

	/** How far from human {@code e} is seen to be: a player's or villager's blood (a full mask hides a player's). */
	static int degree(Entity e) {
		if (e instanceof Player p) {
			return Identities.masked(p) ? 0 : degree(Heritage.of(p));
		}
		return degree(VillagerHeritage.of(e));
	}

	/**
	 * How much {@code who} (a townsperson) holds {@code whom}'s blood against them: 0 not at all, else the degree (more
	 * for a purist's own).
	 */
	static float minds(LivingEntity who, Entity whom) {
		if (who == whom || !VillagerHeritage.eligible(who) || whom instanceof Player p && Identities.masked(p)) {
			return 0.0F;
		}
		Heritage.Kind mine = VillagerHeritage.of(who), theirs = Purists.of(whom);
		if (mine == null || theirs == null && !(whom instanceof Player) || mine == theirs) {
			// (no blood to mind, or their own)
			return 0.0F;
		}
		int d = degree(theirs);
		Faction f = faction(who);
		boolean purist = Purists.is(f) && Purists.kin(f, who);
		// ((0.7.219) less where the ruler has decreed tolerance: Reforms)
		float edict = who.level() instanceof ServerLevel sl ? Reforms.tolerated(sl.getServer(), Emigrants.ruler(sl.getServer(), f)) : 1.0F;
		if (d == 0) {
			// (the rivalry, Havens) a non-human minds a human: hard in a haven, a little anywhere else
			if (degree(mine) == 0) {
				return 0.0F;
			}
			float m = Havens.home(who) != null ? HAVEN_RIVALRY : RIVALRY;
			return edict * (purist ? m * PURIST_HARSH : tolerant(who) ? 0.0F : m);
		}
		if (degree(mine) > 0) {
			// (not plainly human themselves: they know how it is)
			return 0.0F;
		}
		// (a purist's own: always, and harder)
		return edict * (purist ? d * PURIST_HARSH : tolerant(who) ? 0.0F : d);
	}

	/** {@code who} doesn't mind blood at all, by nature: kindhearted, cheerful or devout, or the luck of who they are. */
	static boolean tolerant(LivingEntity who) {
		Temperaments.Temperament t = Temperaments.of(who);
		if (t == Temperaments.Temperament.KINDHEARTED || t == Temperaments.Temperament.CHEERFUL || t == Temperaments.Temperament.DEVOUT) {
			return true;
		}
		if (t == Temperaments.Temperament.PROUD || t == Temperaments.Temperament.SUSPICIOUS || t == Temperaments.Temperament.SPITEFUL) {
			return false;
		}
		int temper = who instanceof Villager v ? McaCompat.temper(v) : 0;
		int share = temper > 0 ? TOLERANT_WARM : temper == 0 ? TOLERANT_PLAIN : temper == -1 ? TOLERANT_SOUR : 0;
		UUID id = who.getUUID();
		long mix = (id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 21)) * 0x9E3779B97F4A7C15L;
		return Math.floorMod(mix >>> 29, 100L) < share;
	}

	/** The realm {@code who} belongs to: a folk's own, or the banner over where they stand. */
	private static @Nullable Faction faction(LivingEntity who) {
		if (!(who.level() instanceof ServerLevel level)) {
			return null;
		}
		MinecraftServer server = level.getServer();
		if (who instanceof Folk folk) {
			return Factions.get(server, folk.factionId());
		}
		return Banners.owner(server, ChunkPos.containing(who.blockPosition()));
	}

	// ---- what it costs -------------------------------------------------------------------------------------------------

	/** {@code villager}'s offers to {@code player} (from {@code VillagerPriceMixin}, after vanilla's own): dearer if they mind. */
	public static void prices(AbstractVillager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel)) {
			return;
		}
		float m = minds(villager, player);
		if (m <= 0.0F) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			int cost = offer.getBaseCostA().getCount();
			offer.addToSpecialPriceDiff(Math.max(1, (int)Math.ceil(cost * PRICE_SHARE * m)));
		}
	}

	/** Of {@code gained} hearts {@code villager} just warmed to {@code player} by, how many they take back for {@code player}'s blood. */
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

	/** Good name won or lost with {@code kingdom} by {@code player}, after their blood ({@link Reputation#change}). */
	static int reputation(Faction kingdom, ServerPlayer player, int by) {
		if (by == 0 || Identities.hidden(player, kingdom.id())) {
			return by;
		}
		int d = degree(player);
		if (Purists.kin(kingdom, player)) {
			return by;
		}
		float m;
		if (Havens.is(player.level().getServer(), kingdom)) {
			// (a haven: a human is the outsider, and their own kind are welcome)
			if (d > 0) {
				return by;
			}
			m = HAVEN_RIVALRY;
		} else if (d == 0) {
			return by;
		} else {
			m = Purists.is(kingdom) ? d * PURIST_HARSH : d;
		}
		if (by > 0) {
			return Math.max(1, Math.round(by * Math.max(REP_KEPT_MIN, 1.0F - REP_GAIN_CUT * m)));
		}
		return Math.round(by * (1.0F + REP_LOSS_ADD * m));
	}

	/** A guard's extra keenness to search (or toll) {@code player}, and 0 if he doesn't mind them. */
	static int guard(Mob guard, ServerPlayer player) {
		return Math.round(minds(guard, player) * GUARD_SEARCH_W);
	}

	/** How long a guard waits between halting {@code player} (from the usual {@code ticks}): sooner, the more he minds. */
	static long haltGap(Mob guard, ServerPlayer player, long ticks) {
		float m = minds(guard, player);
		return m <= 0.0F ? ticks : (long)(ticks / (1.0F + GUARD_HALT_SOONER * m));
	}

	/** How much cooler two villagers start out towards each other for one's blood (in regard; from {@code Neighbours}). */
	static int neighbourLean(Villager a, Villager b) {
		return Math.round(NEIGHBOUR_STEP * Math.max(minds(a, b), minds(b, a)));
	}

	/** For MCA's chat AI: what {@code villager} thinks of {@code player}'s blood, or "". */
	static String prompt(Villager villager, ServerPlayer player) {
		Heritage.Kind kind = Heritage.of(player);
		int d = degree(player);
		if (Identities.masked(player)) {
			return "";
		}
		if (d == 0) {
			// (the rivalry) a non-human to a human
			if (degree(VillagerHeritage.of(villager)) == 0) {
				return "";
			}
			boolean haven = Havens.home(villager) != null;
			if (minds(villager, player) <= 0.0F) {
				return "They are human. Many of your kind resent humans for their ridicule, but you judge people as they are.";
			}
			return haven ? "They are human. Your village was made by folk who left the human towns, sick of being ridiculed there; you don't trust humans and"
				+ " don't want them here. Let it show." : "They are human, and humans look down on your kind. You're wary and a little bitter with them.";
		}
		if (kind == null) {
			return "";
		}
		String name = Component.translatable("heritage.jaymoskingdoms." + kind.key()).getString();
		if (VillagerHeritage.of(villager) == kind) {
			return "They are " + name + " like you; you know how most folk look down on your kind.";
		}
		float m = minds(villager, player);
		if (m <= 0.0F) {
			return "They are " + name + ". Most folk look down on that blood as less than human, but you don't hold it against them.";
		}
		return "They are " + name + (m >= SHRINK_DEGREE ? ", which you find unnatural and unsettling, barely human" : ", which you think less of, as most folk do")
			+ ". Let it show, coolly.";
	}

	/**
	 * (Havens, coming over to gossip) A haven's people tell why their folk left the humans; now and then anyone talks of
	 * the rivalry, from their side. Null if not this time.
	 */
	static @Nullable Component gossip(ServerLevel level, LivingEntity villager, ServerPlayer player) {
		RandomSource random = level.getRandom();
		Heritage.Kind mine = VillagerHeritage.of(villager);
		if (mine == null) {
			return null;
		}
		Component name = Identities.shownName(player), blood = Purists.name(mine);
		if (Havens.home(villager) != null && degree(mine) > 0 && random.nextFloat() < HAVEN_GOSSIP_CHANCE) {
			return Component.translatable("prejudice.jaymoskingdoms.gossip.haven." + random.nextInt(HAVEN_GOSSIP_LINES), blood, name);
		}
		if (random.nextFloat() < RIVALRY_GOSSIP_CHANCE) {
			String side = degree(mine) > 0 ? "nonhuman" : "human";
			return Component.translatable("prejudice.jaymoskingdoms.gossip." + side + "." + random.nextInt(RIVALRY_GOSSIP_LINES), blood, name);
		}
		return null;
	}

	// ---- the townsfolk -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % TICK != 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			int d = degree(p);
			if (Identities.masked(p) || p.isSpectator() || p.isCreative() || !p.isAlive()) {
				continue;
			}
			List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(NOTICE_RANGE),
				x -> x.isAlive() && !x.isSleeping() && VillagerHeritage.eligible(x) && !(x instanceof Mob m && m.getTarget() != null)
					&& !(x instanceof Mob m2 && Approaches.talking(m2)) && x.hasLineOfSight(p));
			if (near.isEmpty()) {
				continue;
			}
			LivingEntity by = near.get(0);
			for (LivingEntity x : near) {
				if (x.distanceToSqr(p) < by.distanceToSqr(p)) {
					by = x;
				}
			}
			react(level, by, p, d, now);
		}
	}

	/** {@code by}, the nearest townsperson to {@code p}, shows what they think of their blood (or doesn't). */
	private static void react(ServerLevel level, LivingEntity by, ServerPlayer p, int d, long now) {
		RandomSource random = level.getRandom();
		Component blood = Purists.name(Heritage.of(p));
		float m = minds(by, p);
		boolean haven = Havens.home(by) != null, nonHuman = degree(VillagerHeritage.of(by)) > 0;
		if (m <= 0.0F) {
			if (d == 0 && !nonHuman) {
				// (a human among humans: nothing to say)
				return;
			}
			if (now - KIND.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < KIND_TICKS || random.nextFloat() >= KIND_CHANCE || by.isBaby()) {
				return;
			}
			KIND.put(p.getUUID(), now);
			look(by, p);
			boolean kin = VillagerHeritage.of(by) == Heritage.of(p);
			// (a fair-minded non-human to a human; one of your own; a haven's welcome to any non-human; a kind human)
			String key = d == 0 ? "fair." + random.nextInt(FAIR_LINES) : kin ? "kin." + random.nextInt(KIN_LINES)
				: haven ? "welcome." + random.nextInt(WELCOME_LINES) : "kind." + random.nextInt(KIND_LINES);
			Speech.say(level, by, Component.translatable("prejudice.jaymoskingdoms." + key, blood, Identities.shownName(p)), Speech.GOLD, SPEECH_RANGE);
			return;
		}
		if (d >= SHRINK_DEGREE && m >= SHRINK_DEGREE && !by.isBaby() && by.distanceTo(p) <= TOO_CLOSE && now - SHRUNK.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) >= SHRINK_TICKS) {
			// (the worst-regarded: folk edge away from them)
			SHRUNK.put(p.getUUID(), now);
			look(by, p);
			Purists.slink(by, p);
			if (random.nextFloat() < 0.5F) {
				Speech.say(level, by, Component.translatable("prejudice.jaymoskingdoms.shrink." + random.nextInt(SHRINK_LINES), blood), Speech.WHITE,
					SPEECH_RANGE);
			}
			return;
		}
		if (now - MUTTERED.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < MUTTER_TICKS || random.nextFloat() >= MUTTER_CHANCE * m) {
			return;
		}
		if (Purists.outsider(faction(by), p)) {
			// (a purist's people jeer at them their own way: Purists)
			return;
		}
		MUTTERED.put(p.getUUID(), now);
		look(by, p);
		String key = d == 0 ? (by.isBaby() ? "rival_child." + random.nextInt(RIVAL_CHILD_LINES)
			: haven ? "haven." + random.nextInt(HAVEN_LINES) : "rival." + random.nextInt(RIVAL_LINES))
			: by.isBaby() ? "child." + random.nextInt(CHILD_LINES)
			: d >= SHRINK_DEGREE ? "high." + random.nextInt(MUTTER_HIGH_LINES) : "low." + random.nextInt(MUTTER_LOW_LINES);
		Speech.say(level, by, Component.translatable("prejudice.jaymoskingdoms." + key, blood, Identities.shownName(p)), Speech.YELLOW, SPEECH_RANGE);
		if (!by.isBaby() && random.nextFloat() < 0.4F) {
			level.playSound(null, by.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.5F, 1.0F);
		}
	}

	private static void look(LivingEntity by, Entity at) {
		if (by instanceof Mob m) {
			m.getLookControl().setLookAt(at, 30.0F, 30.0F);
		}
	}
}
