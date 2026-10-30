package com.jaymoskingdoms;

import com.jaymoskingdoms.LifeStory.Start;
import com.jaymoskingdoms.whaling.GreatWhale;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.418) A life's aim. Every road of the life story ({@link Start}; fate is one of them once it has chosen) gives its
 * traveller one aim, a plain thing to set out to do, written on a one-page Journal in their kit (no lore, only what they
 * mean to do). The aim is measured by something that already exists (captives freed, merchant orders, a wolf grown, a
 * bed in a realm's capital, ...); the table is {@link #AIMS}, one row a road.
 *
 * <ul>
 * <li><b>Finishing</b>: a title that goes by the traveller's name (over the head and in the tab list, beside a job's title:
 * {@link Careers#title}) and one small perk, a free point in one attribute ({@link #bonus}, counted by
 * {@link LifeStory#points}). Said in the next Tidings, and by whoever stands near; not in chat.</li>
 * <li><b>Rise up</b>: finishing the aim of a Very Hard or Cruel road records the deed {@code rise.<road>} for the whole world
 * ({@link RoadDeeds}), which a later patch uses to open a richer road.</li>
 * <li><b>Turning your back on it</b>: throwing the aim over (losing the heirloom, a companion lost while far from them,
 * the pup dead, breaking out of the cells instead of serving) marks it broken: no title, no perk. The folk of the realm
 * where the story began think less of the traveller (reputation), and the next few of them to meet the traveller say so,
 * as their temper takes it: the kindhearted forgive and give a little back, the cold and proud do not.</li>
 * </ul>
 */
public final class LifeAims {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** How often the aims are looked at (ticks), and how often the folk who know a broken aim speak (ticks). */
	static final int CHECK = 200, SCORN_CHECK = 100;
	static final long DAY_TICKS = 24000L;
	/** How far (blocks) a realm's land is searched for, at the start, when the road names none. */
	static final double REALM_SEARCH = 160.0;
	/** A bed counts as a home in a realm when it stands within this many blocks of the realm's seat or on its land. */
	static final double BED_RANGE = 96.0;
	/** The reputation lost with the realm of the start for turning your back on the aim, and the folk who say so. */
	static final int BETRAY_REP = 150, SCORN_LINES = 3;
	/** What a forgiving villager gives back each time they speak. */
	static final int FORGIVE_REP = 30;
	/** How near (blocks) a villager must be, to the traveller, to say their piece. How far from the land of the start a villager can be and still know the story. */
	static final double SCORN_RANGE = 8.0, KNOW_NEAR = 112.0;
	/** The heirloom: how long it can be gone from the pack, ender chest and boxes (online ticks: five days) before it counts as lost. */
	static final int HEIRLOOM_LOST_TICKS = 5 * 24000;
	/** A companion dying farther than this from the traveller (blocks) died because they were left. */
	static final double ABANDON_RANGE = 64.0;
	/** A whale taken counts for a traveller within this many blocks of it dying (their crew may strike the blow). */
	static final double WHALE_NEAR = 64.0;
	/** The entity tag on a nobody, followed by the UUID of the traveller they were hired to. */
	public static final String NOBODY_TAG = "jaymoskingdoms_nobody_of_";
	/** Lines each of the speech groups has. */
	static final int LINES = 2;
	/** Days to live through when the road's aim is about a realm and there is none (a survive aim instead). */
	static final int FALLBACK_DAYS = 10;
	/** The most dead pups remembered, and slack on the sentence (ticks) when telling the story's own from a later one. */
	static final int MOST_FALLEN = 200, SENTENCE_SLACK = 2400;

	/** What an aim asks for. */
	enum Kind {
		/** Captives freed (counted when the chains break). */
		FREE,
		/** Spiders killed, and monsters of any kind killed. */
		SPIDERS, MONSTERS,
		/** Days with a bed or a job in the realm (any realm if none is named). */
		HOME,
		/** A reputation with the realm (the best of any realm if none is named); a rank in it. */
		REP, RANK,
		/** Merchant orders sent on to the buyer. */
		ORDERS,
		/** The pup grown up and alive. */
		WOLF,
		/** Days with all the hired nobodies alive. */
		BAND,
		/** The heirloom's assassin killed, or days with the blade still in the pack. */
		BLADE,
		/** A great whale taken. */
		WHALE,
		/** A settlement joined or founded (a faction of any kind with the traveller in it). */
		SETTLE,
		/** Days lived through. */
		SURVIVE
	}

	/** One road's aim: its goal (a count, days, a reputation or a rank), the attribute point it gives, and what it counts as a rise. */
	record Aim(Kind kind, int goal, Stats.Stat perk) {
	}

	/** Every road's aim, in one table. Lang: {@code aim.jaymoskingdoms.<road>.page} (the Journal) and {@code .title}. */
	static final Map<Start, Aim> AIMS = new EnumMap<>(Start.class);

	static {
		AIMS.put(Start.CITY, new Aim(Kind.REP, 200, Stats.Stat.WITS));
		AIMS.put(Start.VILLAGE, new Aim(Kind.REP, 150, Stats.Stat.ENDURANCE));
		AIMS.put(Start.SWORN, new Aim(Kind.RANK, 2, Stats.Stat.GRIT));
		AIMS.put(Start.BANISHED, new Aim(Kind.REP, 0, Stats.Stat.AGILITY));
		AIMS.put(Start.WILD, new Aim(Kind.SETTLE, 1, Stats.Stat.MIGHT));
		AIMS.put(Start.CAVE, new Aim(Kind.SPIDERS, 15, Stats.Stat.FINESSE));
		AIMS.put(Start.SHIPWRECK, new Aim(Kind.SURVIVE, 10, Stats.Stat.ENDURANCE));
		AIMS.put(Start.MONOLITH, new Aim(Kind.MONSTERS, 25, Stats.Stat.MIGHT));
		AIMS.put(Start.SKYFALL, new Aim(Kind.SETTLE, 1, Stats.Stat.AGILITY));
		AIMS.put(Start.TIMELOST, new Aim(Kind.SURVIVE, 10, Stats.Stat.WITS));
		AIMS.put(Start.BURNED, new Aim(Kind.HOME, 3, Stats.Stat.GRIT));
		AIMS.put(Start.SNOWED, new Aim(Kind.HOME, 3, Stats.Stat.ENDURANCE));
		AIMS.put(Start.CAPTIVE, new Aim(Kind.FREE, 3, Stats.Stat.AGILITY));
		AIMS.put(Start.WHALERS, new Aim(Kind.WHALE, 1, Stats.Stat.MIGHT));
		AIMS.put(Start.UNARMED, new Aim(Kind.MONSTERS, 10, Stats.Stat.FINESSE));
		AIMS.put(Start.CHAINED, new Aim(Kind.FREE, 5, Stats.Stat.MIGHT));
		AIMS.put(Start.ROCK_BOTTOM, new Aim(Kind.HOME, 3, Stats.Stat.ENDURANCE));
		AIMS.put(Start.SENTENCED, new Aim(Kind.HOME, 3, Stats.Stat.WITS));
		AIMS.put(Start.EVICTED, new Aim(Kind.HOME, 3, Stats.Stat.AGILITY));
		AIMS.put(Start.HIDEOUT, new Aim(Kind.REP, 0, Stats.Stat.FINESSE));
		AIMS.put(Start.CAST_OUT, new Aim(Kind.SETTLE, 1, Stats.Stat.WITS));
		AIMS.put(Start.HEIRLOOM, new Aim(Kind.BLADE, 10, Stats.Stat.MIGHT));
		AIMS.put(Start.TRADER, new Aim(Kind.ORDERS, 5, Stats.Stat.WITS));
		AIMS.put(Start.PUP, new Aim(Kind.WOLF, 1, Stats.Stat.AGILITY));
		AIMS.put(Start.NOBODIES, new Aim(Kind.BAND, 7, Stats.Stat.GRIT));
		AIMS.put(Start.MOON, new Aim(Kind.SURVIVE, 10, Stats.Stat.ENDURANCE));
		AIMS.put(Start.VERDANT, new Aim(Kind.SETTLE, 1, Stats.Stat.ENDURANCE));
		AIMS.put(Start.RUSTFALL, new Aim(Kind.SURVIVE, 10, Stats.Stat.GRIT));
		AIMS.put(Start.MAW, new Aim(Kind.SURVIVE, 7, Stats.Stat.MIGHT));
		AIMS.put(Start.DEMON, new Aim(Kind.SURVIVE, 10, Stats.Stat.FINESSE));
		AIMS.put(Start.AFTERLIFE, new Aim(Kind.SURVIVE, 7, Stats.Stat.WITS));
		// (0.7.420) the roads of patch 3 (the lab, the bench, the moon's mark), so every road has an aim to pay back a start from nothing
		AIMS.put(Start.LAB, new Aim(Kind.SETTLE, 1, Stats.Stat.FINESSE));
		AIMS.put(Start.ANDROID, new Aim(Kind.MONSTERS, 15, Stats.Stat.MIGHT));
		AIMS.put(Start.MOON_CURSED, new Aim(Kind.SURVIVE, 10, Stats.Stat.GRIT));
	}

	// ---- state ---------------------------------------------------------------------------------------------------------

	/**
	 * A traveller's aim: the road's key, what's counted so far, whether it is done, broken (turned their back on it) or lost
	 * (made impossible with no one to blame: a companion fell at their side), the game time it began, the full days spent
	 * online so far and the online ticks they come from, the time the heirloom has been out of reach (ticks), the realm of the
	 * start (-1: none), the folk still to say their piece about a broken aim and who has already (villager UUIDs).
	 */
	record Life(String aim, int progress, boolean done, boolean broken, boolean lost, long since, long day, long online, int missed, int realm,
		int lines, List<UUID> spoken) {
		static final Codec<Life> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("aim").forGetter(Life::aim),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(Life::progress),
			Codec.BOOL.optionalFieldOf("done", false).forGetter(Life::done),
			Codec.BOOL.optionalFieldOf("broken", false).forGetter(Life::broken),
			Codec.BOOL.optionalFieldOf("lost", false).forGetter(Life::lost),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Life::since),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Life::day),
			Codec.LONG.optionalFieldOf("online", 0L).forGetter(Life::online),
			Codec.INT.optionalFieldOf("missed", 0).forGetter(Life::missed),
			Codec.INT.optionalFieldOf("realm", -1).forGetter(Life::realm),
			Codec.INT.optionalFieldOf("lines", 0).forGetter(Life::lines),
			UUIDUtil.CODEC.listOf().optionalFieldOf("spoken", List.of()).forGetter(Life::spoken)).apply(i, Life::new));

		boolean active() {
			return !this.done && !this.broken && !this.lost;
		}

		Life withProgress(int progress) {
			return new Life(this.aim, progress, this.done, this.broken, this.lost, this.since, this.day, this.online, this.missed, this.realm, this.lines, this.spoken);
		}

		Life withTime(long online, long day, int progress) {
			return new Life(this.aim, progress, this.done, this.broken, this.lost, this.since, day, online, this.missed, this.realm, this.lines, this.spoken);
		}

		Life withMissed(int missed) {
			return new Life(this.aim, this.progress, this.done, this.broken, this.lost, this.since, this.day, this.online, missed, this.realm, this.lines, this.spoken);
		}

		Life finished() {
			return new Life(this.aim, this.progress, true, false, false, this.since, this.day, this.online, 0, this.realm, 0, List.of());
		}

		Life asLost() {
			return new Life(this.aim, this.progress, false, false, true, this.since, this.day, this.online, 0, this.realm, 0, List.of());
		}

		Life asBroken(int lines) {
			return new Life(this.aim, this.progress, false, true, false, this.since, this.day, this.online, 0, this.realm, lines, List.of());
		}

		Life spokeTo(UUID villager) {
			List<UUID> more = new java.util.ArrayList<>(this.spoken);
			more.add(villager);
			return new Life(this.aim, this.progress, this.done, this.broken, this.lost, this.since, this.day, this.online, this.missed, this.realm,
				this.lines - 1, List.copyOf(more));
		}

		@Nullable Start road() {
			try {
				return Start.valueOf(this.aim.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	/** The aim {@code life} is really after: the road's, but a reputation or rank aim with no realm to win it from is a survive aim. */
	static @Nullable Aim aimOf(Life life) {
		Start road = life.road();
		Aim aim = road == null ? null : AIMS.get(road);
		if (aim != null && (aim.kind() == Kind.REP || aim.kind() == Kind.RANK) && life.realm() < 0) {
			return new Aim(Kind.SURVIVE, FALLBACK_DAYS, aim.perk());
		}
		return aim;
	}

	/** On a traveller: their aim and how it stands. Saved, kept through death. */
	static final AttachmentType<Life> LIFE_AIM = AttachmentRegistry.create(JaymosKingdoms.id("life_aim"),
		builder -> builder.persistent(Life.CODEC).copyOnDeath());

	/** On the Overworld: tamed wolves that have died (UUIDs, the last few), so a pup's end is known whether or not its owner was there. Saved. */
	static final AttachmentType<List<String>> FALLEN_WOLVES = AttachmentRegistry.create(JaymosKingdoms.id("fallen_wolves"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	private LifeAims() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("life aim attachments: {} {}", LIFE_AIM, RoadDeeds.DONE);
		ServerTickEvents.END_SERVER_TICK.register(LifeAims::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(LifeAims::died);
	}

	/** The /kingdom story reset: no aim any more. */
	static void clear(ServerPlayer player) {
		player.removeAttached(LIFE_AIM);
	}

	// ---- the start -----------------------------------------------------------------------------------------------------

	/**
	 * (From {@link LifeStory}, once {@code player} has landed on {@code road}) Gives them their aim and a one-page Journal
	 * of it. {@code named}: the realm the road names, if it does.
	 */
	static void begin(ServerPlayer player, Start road, @Nullable Faction named) {
		if (AIMS.get(road) == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = Factions.level(server);
		long now = overworld.getGameTime();
		Life life = new Life(road.name().toLowerCase(Locale.ROOT), 0, false, false, false, now, 0L, 0L, 0, realmOf(server, player, road, named), 0, List.of());
		player.setAttached(LIFE_AIM, life);
		// (no realm to win a name with: the page asks for ten days instead)
		boolean fallback = aimOf(life).kind() != AIMS.get(road).kind();
		String page = fallback ? "aim.jaymoskingdoms.page.alt" : "aim.jaymoskingdoms." + life.aim() + ".page";
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = List.of(Filterable.passThrough(Component.translatable(page)));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Journal"), player.getGameProfile().name(), 0, pages, true));
		player.getInventory().placeItemBackInInventory(book, Prediction.SERVER_ONLY);
	}

	/** The realm (id) the aim is about: the one the road names, the den's for the hideout, else whoever holds the land they woke on; -1 for none. */
	private static int realmOf(MinecraftServer server, ServerPlayer player, Start road, @Nullable Faction named) {
		if (named != null) {
			return named.id();
		}
		if (road == Start.HIDEOUT) {
			Faction den = StoryHard.denRealm(server, player.blockPosition());
			return den != null && den.type().isRealm() ? den.id() : -1;
		}
		StoryTalk.Landing landing = player.getAttached(StoryTalk.LANDING);
		if (landing != null && landing.realm() >= 0) {
			Faction f = Factions.get(server, landing.realm());
			if (f != null && f.type().isRealm()) {
				return f.id();
			}
		}
		if (player.level().dimension() == Level.OVERWORLD) {
			Faction f = Factions.realmNear(server, player.blockPosition(), REALM_SEARCH);
			if (f != null) {
				return f.id();
			}
		}
		return -1;
	}

	// ---- reading -------------------------------------------------------------------------------------------------------

	private static @Nullable Life of(ServerPlayer player) {
		return player.getAttached(LIFE_AIM);
	}

	/** The title {@code player} has earned by finishing their aim, or null. */
	public static @Nullable Component title(net.minecraft.world.entity.player.Player player) {
		Life life = player.getAttached(LIFE_AIM);
		return life != null && life.done() ? Component.translatable("aim.jaymoskingdoms." + life.aim() + ".title") : null;
	}

	/** (From {@link LifeStory#points}) The free point {@code player}'s finished aim gave in {@code stat}: 1 or 0. */
	public static int bonus(net.minecraft.world.entity.player.Player player, Stats.Stat stat) {
		Life life = player.getAttached(LIFE_AIM);
		if (life == null || !life.done()) {
			return 0;
		}
		Aim aim = aimOf(life);
		return aim != null && aim.perk() == stat ? 1 : 0;
	}

	/** (0.7.420) Whether {@code player} has finished their life's aim (what pays back the points a life from nothing gave up). */
	public static boolean finished(net.minecraft.world.entity.player.Player player) {
		Life life = player.getAttached(LIFE_AIM);
		return life != null && life.done();
	}

	// ---- counting ------------------------------------------------------------------------------------------------------

	/** Adds {@code n} to {@code player}'s count if their aim is of {@code kind}; finishes it at the goal. */
	private static void count(ServerPlayer player, Kind kind, int n) {
		Life life = of(player);
		if (life == null || !life.active()) {
			return;
		}
		Aim aim = aimOf(life);
		if (aim == null || aim.kind() != kind) {
			return;
		}
		int now = Math.min(aim.goal(), life.progress() + n);
		player.setAttached(LIFE_AIM, life.withProgress(now));
		if (now >= aim.goal()) {
			finish(player);
		}
	}

	/** (Captives) {@code player} broke a captive's chains. */
	static void freed(ServerPlayer player) {
		count(player, Kind.FREE, 1);
	}

	/** (MerchantOrders) {@code n} of {@code buyer}'s orders have been sent on to them. */
	static void ordersSent(MinecraftServer server, UUID buyer, int n) {
		// (0.7.419) a road deed: a merchant order carried through
		Deeds.done(server, "orders.sent");
		ServerPlayer player = server.getPlayerList().getPlayer(buyer);
		if (player != null) {
			count(player, Kind.ORDERS, n);
		}
	}

	/** (Assassins) The assassin sent for {@code player} is dead by their hand. */
	static void assassinKilled(ServerPlayer player) {
		// (0.7.419) a road deed: a hired blade met and beaten
		Deeds.done(player.level().getServer(), "assassin.killed");
		count(player, Kind.BLADE, aimGoal(player, Kind.BLADE));
	}

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (source.getEntity() instanceof ServerPlayer killer) {
			if (entity instanceof Spider) {
				count(killer, Kind.SPIDERS, 1);
			}
			// (only hostile mobs that were after them, and not the pieces a slime splits into)
			if (entity instanceof Enemy && !(entity instanceof Slime) && entity instanceof Mob mob && mob.getTarget() == killer) {
				count(killer, Kind.MONSTERS, 1);
			}
		}
		if (entity instanceof GreatWhale) {
			boolean taken = source.getEntity() instanceof ServerPlayer;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.level() == level && p.distanceToSqr(entity) <= WHALE_NEAR * WHALE_NEAR) {
					taken = true;
					count(p, Kind.WHALE, 1);
				}
			}
			if (taken) {
				// (0.7.419) a road deed: a great whale taken (by a traveller or their crew)
				Deeds.done(server, "whale.taken");
			}
		}
		if (entity instanceof Wolf wolf && wolf.isTame()) {
			List<String> fallen = new java.util.ArrayList<>(Factions.level(server).getAttachedOrElse(FALLEN_WOLVES, List.of()));
			fallen.add(wolf.getStringUUID());
			while (fallen.size() > MOST_FALLEN) {
				fallen.removeFirst();
			}
			Factions.level(server).setAttached(FALLEN_WOLVES, List.copyOf(fallen));
		}
		for (String tag : new java.util.ArrayList<>(entity.entityTags())) {
			if (tag.startsWith(NOBODY_TAG)) {
				nobodyFell(server, level, entity, tag);
			}
		}
	}

	/** How many {@code kind} asks for, for {@code player}'s aim (0 if theirs is another). */
	private static int aimGoal(ServerPlayer player, Kind kind) {
		Life life = of(player);
		Aim aim = life == null ? null : aimOf(life);
		return aim != null && aim.kind() == kind ? aim.goal() : 0;
	}

	/**
	 * One of a traveller's hired nobodies ({@code tag}) died where {@code body} fell. Only while the contract still runs, and
	 * only with the traveller there in the same world to be told: left behind, it is a betrayal; at their side, only lost.
	 * (A nobody whose contract has ended loses the tag.)
	 */
	private static void nobodyFell(MinecraftServer server, ServerLevel level, Entity body, String tag) {
		if (!McaCompat.contractRunning(body)) {
			body.removeTag(tag);
			return;
		}
		UUID id;
		try {
			id = UUID.fromString(tag.substring(NOBODY_TAG.length()));
		} catch (IllegalArgumentException e) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		Life life = player == null ? null : of(player);
		if (player == null || player.level() != level || life == null || !life.active() || life.road() != Start.NOBODIES) {
			return;
		}
		if (player.distanceToSqr(body) <= ABANDON_RANGE * ABANDON_RANGE) {
			player.setAttached(LIFE_AIM, life.asLost());
			Post.tidings(player.getUUID(), Component.translatable("aim.jaymoskingdoms.tidings.lost"));
		} else {
			betray(player, Start.NOBODIES);
		}
	}

	/** Nobodies near {@code player} whose contract has run out stop being counted as theirs. */
	private static void sweepNobodies(ServerLevel level, ServerPlayer player) {
		String tag = NOBODY_TAG + player.getStringUUID();
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(ABANDON_RANGE), e -> e.entityTags().contains(tag))) {
			if (!McaCompat.contractRunning(v)) {
				v.removeTag(tag);
			}
		}
	}

	// ---- the days ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int ticks = server.getTickCount();
		boolean look = ticks % CHECK == 0, scorn = ticks % SCORN_CHECK == 0;
		if (!look && !scorn) {
			return;
		}
		ServerLevel overworld = Factions.level(server);
		long now = overworld.getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Life life = of(player);
			if (life == null) {
				continue;
			}
			if (look && life.active()) {
				check(server, player, life, now);
			}
			if (scorn && life.broken() && life.lines() > 0) {
				scorn(server, player, life);
			}
		}
	}

	/** One look at {@code player}'s aim: the days they have been about are counted (whole days online only), and what can be checked at a glance is. */
	private static void check(MinecraftServer server, ServerPlayer player, Life life, long now) {
		Aim aim = aimOf(life);
		if (aim == null) {
			return;
		}
		long online = life.online() + CHECK;
		long days = online / DAY_TICKS;
		int gained = (int)(days - life.day());
		int progress = life.progress();
		switch (aim.kind()) {
			case SURVIVE -> progress += gained;
			case BAND -> {
				progress += gained;
				sweepNobodies(player.level(), player);
			}
			case HOME -> {
				if (gained > 0 && housed(server, player, life.realm())) {
					progress += gained;
				}
			}
			case BLADE -> {
				if (carriesHeirloom(player)) {
					life = life.withMissed(0);
					progress += gained;
				} else {
					// (sold, burnt, lost: the time out of reach runs, and a long enough time is a betrayal)
					int missed = life.missed() + CHECK;
					if (missed >= HEIRLOOM_LOST_TICKS) {
						betray(player, Start.HEIRLOOM);
						return;
					}
					life = life.withMissed(missed);
				}
			}
			case REP -> {
				if (reputation(server, player, life.realm()) >= aim.goal()) {
					progress = aim.goal();
				}
			}
			case RANK -> {
				Faction f = Factions.of(server, player.getUUID());
				Faction.Member m = f == null || (life.realm() >= 0 && f.id() != life.realm()) ? null : f.member(player.getUUID());
				if (m != null && m.rank() >= aim.goal()) {
					progress = aim.goal();
				}
			}
			case SETTLE -> {
				if (Factions.of(server, player.getUUID()) != null) {
					progress = aim.goal();
				}
			}
			case WOLF -> {
				StoryKit.Pup pup = player.getAttached(StoryKit.PUP);
				if (pup != null && Factions.level(server).getAttachedOrElse(FALLEN_WOLVES, List.of()).contains(pup.wolf().toString())) {
					betray(player, Start.PUP);
					return;
				}
				if (pupGrown(server, player)) {
					progress = aim.goal();
				}
			}
			default -> {
			}
		}
		int goal = aim.goal();
		Life next = life.withTime(online, days, Math.min(goal, progress));
		if (!next.equals(of(player))) {
			player.setAttached(LIFE_AIM, next);
		}
		if (progress >= goal) {
			finish(player);
		}
	}

	/** Whether {@code player} still has the family blade: in their pack, their ender chest, or a shulker box in either. */
	private static boolean carriesHeirloom(ServerPlayer player) {
		String id = player.getStringUUID();
		if (StoryKit.holdsHeirloom(player) || inBox(player.getInventory().getNonEquipmentItems(), id)) {
			return true;
		}
		net.minecraft.world.inventory.PlayerEnderChestContainer chest = player.getEnderChestInventory();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			if (StoryKit.isHeirloom(stack, id) || inBox(List.of(stack), id)) {
				return true;
			}
		}
		return false;
	}

	/** Whether any of {@code stacks} is a box holding the blade of the player whose UUID is {@code id}. */
	private static boolean inBox(List<ItemStack> stacks, String id) {
		for (ItemStack stack : stacks) {
			ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
			if (contents != null && contents.nonEmptyItemCopyStream().anyMatch(inner -> StoryKit.isHeirloom(inner, id))) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code player} has a bed of their own, or a job, in realm {@code realm} (any realm, if it is -1). */
	private static boolean housed(MinecraftServer server, ServerPlayer player, int realm) {
		for (Careers.Post post : Careers.of(player).posts()) {
			if (realm < 0 || post.faction() == realm) {
				return true;
			}
		}
		ServerPlayer.RespawnConfig bed = player.getRespawnConfig();
		if (bed == null || bed.forced() || bed.respawnData().dimension() != Level.OVERWORLD) {
			return false;
		}
		ServerLevel overworld = server.overworld();
		BlockPos at = bed.respawnData().pos();
		if (!(overworld.getBlockState(at).getBlock() instanceof AbstractBedBlock)) {
			return false;
		}
		Faction f = Factions.around(server, overworld, at, BED_RANGE);
		return f != null && !f.fallen() && f.type() != FactionType.BARBARIAN && (realm < 0 || f.id() == realm);
	}

	/** The best reputation {@code player} has with realm {@code realm}, or with any realm if it is -1. */
	private static int reputation(MinecraftServer server, ServerPlayer player, int realm) {
		if (realm >= 0) {
			return Reputation.get(player, realm);
		}
		int best = Reputation.MIN;
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen()) {
				best = Math.max(best, Reputation.get(player, f.id()));
			}
		}
		return best;
	}

	/** Whether {@code player}'s pup is loaded, alive and no longer a pup. */
	private static boolean pupGrown(MinecraftServer server, ServerPlayer player) {
		StoryKit.Pup pup = player.getAttached(StoryKit.PUP);
		if (pup == null) {
			return false;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(pup.wolf()) instanceof Wolf wolf) {
				return wolf.isAlive() && !wolf.isBaby();
			}
		}
		return false;
	}

	// ---- done ----------------------------------------------------------------------------------------------------------

	/** {@code player} has done what they set out to do. */
	private static void finish(ServerPlayer player) {
		Life life = of(player);
		Start road = life == null ? null : life.road();
		if (life == null || road == null || !life.active()) {
			return;
		}
		player.setAttached(LIFE_AIM, life.finished());
		MinecraftServer server = player.level().getServer();
		Stats.attributes(player);
		Component title = Component.translatable("aim.jaymoskingdoms." + life.aim() + ".title");
		Post.tidings(player.getUUID(), Component.translatable("aim.jaymoskingdoms.tidings.done", title));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.1F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.5, 0.6, 0.5, 0.0);
		Villager near = nearestVillager(level, player);
		if (near != null) {
			Speech.sayTo(level, near, player, Component.translatable("aim.jaymoskingdoms.cheer." + level.getRandom().nextInt(LINES), title), Speech.GOLD);
		}
		// (0.7.419) a road deed: someone saw their aim through
		RoadDeeds.done(server, "aim.done");
		LifeStory.Terms terms = LifeStory.terms(road);
		if (terms.mark() == LifeStory.Mark.VERY_HARD || terms.mark() == LifeStory.Mark.CRUEL) {
			RoadDeeds.done(server, "rise." + life.aim());
		}
	}

	private static @Nullable Villager nearestVillager(ServerLevel level, ServerPlayer player) {
		return level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SCORN_RANGE), v -> v.isAlive() && !v.isBaby()).stream()
			.min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
	}

	// ---- turning your back on it ---------------------------------------------------------------------------------------

	/** (StoryHard) {@code player} broke out of a cell whose door they picked, the jail ending at {@code jailUntil}: only the story's own sentence counts. */
	static void brokeOut(ServerPlayer player, long jailUntil) {
		Life life = of(player);
		if (life != null && jailUntil <= life.since() + StoryHard.SENTENCE_DAYS * StoryHard.DAY_TICKS + SENTENCE_SLACK) {
			betray(player, Start.SENTENCED);
		}
	}

	/** {@code player} threw over the aim of {@code road} (if it is theirs and still open): no title, no perk, and the folk who know it think less of them. */
	static void betray(ServerPlayer player, Start road) {
		Life life = of(player);
		if (life == null || !life.active() || life.road() != road) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		player.setAttached(LIFE_AIM, life.asBroken(SCORN_LINES));
		Faction realm = life.realm() >= 0 ? Factions.get(server, life.realm()) : null;
		if (realm != null && !realm.fallen()) {
			Reputation.change(player, realm, -BETRAY_REP);
		}
		Post.tidings(player.getUUID(), Component.translatable("aim.jaymoskingdoms.tidings.broken"));
	}

	/** The next villager who knows the story to be near {@code player} says what they think of them, as their temper takes it. */
	private static void scorn(MinecraftServer server, ServerPlayer player, Life life) {
		ServerLevel level = player.level();
		StoryTalk.Landing landing = player.getAttached(StoryTalk.LANDING);
		Villager who = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SCORN_RANGE),
				v -> v.isAlive() && !v.isBaby() && !life.spoken().contains(v.getUUID()) && player.hasLineOfSight(v) && knows(server, level, life, landing, v)).stream()
			.min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
		if (who == null) {
			return;
		}
		Temperaments.Temperament t = Temperaments.of(who);
		String group = "plain";
		int colour = Speech.WHITE;
		if (t == Temperaments.Temperament.KINDHEARTED || t == Temperaments.Temperament.CHEERFUL || t == Temperaments.Temperament.DEVOUT) {
			group = "kind";
			colour = Speech.YELLOW;
		} else if (t == Temperaments.Temperament.SPITEFUL || t == Temperaments.Temperament.PROUD || t == Temperaments.Temperament.SUSPICIOUS
			|| t == Temperaments.Temperament.GREEDY || t == Temperaments.Temperament.HOTHEADED) {
			group = "cold";
			colour = Speech.RED;
		}
		Speech.sayTo(level, who, player, Component.translatable("aim.jaymoskingdoms.scorn." + group + "." + level.getRandom().nextInt(LINES)), colour);
		player.setAttached(LIFE_AIM, life.spokeTo(who.getUUID()));
		if (group.equals("kind") && life.realm() >= 0) {
			// (the kind forgive: some of it comes back)
			Faction realm = Factions.get(server, life.realm());
			if (realm != null && !realm.fallen()) {
				Reputation.change(player, realm, FORGIVE_REP);
			}
		}
	}

	/** Whether {@code v} would know the story: they live on the land of the realm it began in, or near where it began. */
	private static boolean knows(MinecraftServer server, ServerLevel level, Life life, StoryTalk.@Nullable Landing landing, Villager v) {
		if (landing != null && level.dimension().identifier().toString().equals(landing.dimension()) && v.blockPosition().closerThan(landing.at(), KNOW_NEAR)) {
			return true;
		}
		if (life.realm() < 0) {
			return false;
		}
		Faction holds = Banners.owner(server, ChunkPos.containing(v.blockPosition()));
		return holds != null && holds.id() == life.realm();
	}
}
