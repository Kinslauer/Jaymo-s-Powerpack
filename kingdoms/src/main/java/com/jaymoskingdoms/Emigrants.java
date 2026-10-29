package com.jaymoskingdoms;

import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.221) Andy (2026-10-26): "allow villagers to up and leave on their own volition, packing their things and carts to
 * depart for another civilization. maybe that explains the settlements you can find of people living on their own away
 * from any kingdom, they should tell you as much when you speak with them."
 * <p>
 * Now and then an MCA villager who's had enough ({@link #decide}) packs up and goes, with their partner and children:
 * ridiculed for their blood ({@link Prejudice}), not of a purist king's blood, under a tyrant, at odds with too many
 * neighbours, unhappy, or just restless. The ridiculed make for a haven that will have them ({@link Havens}); a
 * purist's outsider for a realm of their own blood; the rest, as often as not, for a settlement away from any kingdom,
 * else another village or realm. They take their leave aloud if anyone's about, spend a little while packing (a horse
 * and cart, or a pack donkey where no realm lends a cart), and set off; the cart goes with the household's head, led on
 * a rope.
 * <p>The road is the {@link Elders}' kind: in a record ({@link Road}) while nobody's near, walked for real (with the cart)
 * where a player is, along a {@link Roads King's Road} where one runs their way. Arrived, they unpack, MCA finds them a
 * home there, and they remember where they came from and why ({@link #STORY}): in a chat they tell you ({@link #tell}).
 * Folk of a settlement away from any kingdom who came before this (no story of their own) tell of their family's leaving
 * instead, a generation or two back.
 * <p>Never: one who's a player's family, a ruler, an elder of a lineage, a child on their own, one following anyone.
 * Nothing holds a place's people (Andy: "If they lose more than that then it is what it is, the kingdom will either
 * improve it's qol for villagers or it will fall to ruin with no peasants"): a realm left with nobody for
 * {@link #EMPTY_RUIN_TICKS} falls to ruin ({@link #surveyed}).
 */
public final class Emigrants {
	/** How often each villager is weighed (ticks): about twenty times a day. */
	static final int DECIDE_EVERY = 1200;
	static final float CHECKS_A_DAY = 20.0F;
	/** The chance a day of leaving: restless anyone; for each degree their neighbours mind their blood (up to the cap). */
	static final float RESTLESS = 0.003F, RIDICULE = 0.004F, RIDICULE_CAP = 0.06F;
	/**
	 * (0.7.357, Andy: the Bio-built are "repulsive to all human kingdoms. It is near impossible to live or find one living
	 * there") A Bio-built villager among humans who mind it is driven out this fast (the chance a day), whatever the cap.
	 */
	static final float BIO_DRIVEN = 0.35F;
	/** ...not of a purist king's blood; under a tyrant; at odds with this many neighbours; this unhappy (MCA mood). */
	static final float PURIST_OUTSIDER = 0.03F, TYRANT = 0.01F, GRUDGES = 0.015F, UNHAPPY = 0.01F;
	static final int GRUDGES_LEAST = 3, UNHAPPY_MOOD = -8;
	/** Neighbours who count: this close. */
	static final double NEIGHBOURS = 32.0;
	/** A place: this far about its seat. */
	static final double PLACE_RANGE = 64.0;
	/** A realm with no villagers left (by its survey) this long falls to ruin (ticks: three days). */
	static final long EMPTY_RUIN_TICKS = 72000L;
	/** ...counted empty at least this many times over it (one stray count while the town loads mustn't ruin it). */
	static final int EMPTY_COUNTS = 3;
	/** One who's moved stays at least this long (ticks: ten days) before they'd think of it again. */
	static final long SETTLED_TICKS = 240000L;
	/** Their household goes with them: partner and children this close. */
	static final double HOUSEHOLD = 48.0;
	/** How far they'll go (blocks); a haven or a realm of their blood, further. */
	static final int FAR_MIN = 200, FAR_MAX = 1500, KIN_FAR_MAX = 3000;
	/** The restless (and the rest) make for a settlement away from any kingdom this often, when there's one. */
	static final float ALONE_CHANCE = 0.5F;
	/** Away from any kingdom: no realm's seat this close. */
	static final double NO_REALM = 160.0;
	/** Packing, seen (ticks). */
	static final int PACKING_TICKS = 20 * 30;
	/** Nobody this close: they pack and go unseen, at once. */
	static final double UNSEEN = 48.0;
	/** On the road: put in the world this close to a player, taken out of it with nobody this close. */
	static final double APPEAR = 80.0, LEAVE = 112.0;
	/** Their pace unseen (blocks a tick); seen, this share of their speed; a waypoint this far ahead; arrived this close. */
	static final double ROAD_SPEED = 0.12, WALK_SPEED = 0.55, WAYPOINT = 16.0, ARRIVE = 12.0;
	/** Stuck this long (ticks), seen, and they're helped this far on. */
	static final int STUCK_TICKS = 240;
	static final double HOP = 6.0;
	/** How often the roads are seen to (ticks). */
	static final int CHECK_EVERY = 20;
	/** Chatting: the chance one who moved tells you why; one of a settlement away from any kingdom, of their family's leaving. */
	public static final float TELL_CHANCE = 0.35F, OLD_TELL_CHANCE = 0.25F;
	/** Why: the reasons, and the lines of each ({@code emigrant.jaymoskingdoms.<kind>.<reason>.<n>}). */
	static final String RIDICULED = "ridiculed", PURIST = "purist", TYRANNY = "tyrant", GRUDGE = "grudge", GLOOM = "unhappy", WANDER = "restless";
	static final String EXPELLED = "expelled";
	static final String[] REASONS = {RIDICULED, PURIST, TYRANNY, GRUDGE, GLOOM, WANDER, EXPELLED};
	/** (0.7.221) A purist who digs in drives those not of his blood out: this much more a day; stopped at the gate, they won't try again for this long. */
	static final float EXPEL = 0.12F;
	static final long CAUGHT_TICKS = 24000L;
	static final int CAUGHT_GUARD_LINES = 3, CAUGHT_LINES = 3, CAUGHT_GLOOM = 3;
	static final int FAREWELL_LINES = 3, STORY_LINES = 2, OLD_LINES = 2, PACKING_LINES = 3;
	/** On the cart and its horse (or donkey): these. */
	static final String CART_TAG = "jaymoskingdoms_emigrant_cart";

	static final int PACKING = 0, ROAD = 1;

	/** On a villager moving away: where from, where to and why; packing or on the road (and packing until when); the household's head. */
	public record Move(String from, BlockPos goal, String to, String reason, boolean lead, int phase, long until) {
		static final Codec<Move> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("from").forGetter(Move::from),
			BlockPos.CODEC.fieldOf("goal").forGetter(Move::goal),
			Codec.STRING.fieldOf("to").forGetter(Move::to),
			Codec.STRING.fieldOf("reason").forGetter(Move::reason),
			Codec.BOOL.fieldOf("lead").forGetter(Move::lead),
			Codec.INT.fieldOf("phase").forGetter(Move::phase),
			Codec.LONG.fieldOf("until").forGetter(Move::until)).apply(i, Move::new));

		Move phase(int phase) {
			return new Move(this.from, this.goal, this.to, this.reason, this.lead, phase, this.until);
		}
	}

	/** What one who moved remembers: where from, where to, why, and when they got there. */
	public record Story(String from, String to, String reason, long day) {
		static final Codec<Story> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("from").forGetter(Story::from),
			Codec.STRING.fieldOf("to").forGetter(Story::to),
			Codec.STRING.fieldOf("reason").forGetter(Story::reason),
			Codec.LONG.fieldOf("day").forGetter(Story::day)).apply(i, Story::new));
	}

	/** One on the road: where they've got to, making for {@code goal}; their whole saved self while out of the world. */
	record Road(UUID who, BlockPos at, BlockPos goal, CompoundTag body) {
		static final Codec<Road> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("who").forGetter(Road::who),
			BlockPos.CODEC.fieldOf("at").forGetter(Road::at),
			BlockPos.CODEC.fieldOf("goal").forGetter(Road::goal),
			CompoundTag.CODEC.fieldOf("body").forGetter(Road::body)).apply(i, Road::new));

		Road at(BlockPos at) {
			return new Road(this.who, at, this.goal, this.body);
		}

		Road body(CompoundTag body) {
			return new Road(this.who, this.at, this.goal, body);
		}

		boolean out() {
			return !this.body.isEmpty();
		}
	}

	public static final AttachmentType<Move> MOVE = AttachmentRegistry.create(JaymosKingdoms.id("emigrant_move"),
		builder -> builder.persistent(Move.CODEC));
	public static final AttachmentType<Story> STORY = AttachmentRegistry.create(JaymosKingdoms.id("emigrant_story"),
		builder -> builder.persistent(Story.CODEC));
	/** On the Overworld: everyone on the road, by id; and each realm's emptiness (-1 peopled, else when its last left). */
	static final AttachmentType<Map<String, Road>> ROADS = AttachmentRegistry.create(JaymosKingdoms.id("emigrant_roads"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Road.CODEC)));
	static final AttachmentType<Map<String, Long>> EMPTIED = AttachmentRegistry.create(JaymosKingdoms.id("emigrant_emptied"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	/** The MCA villagers loaded just now. */
	private static final Set<Villager> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Copies left behind in chunks, and strays of carts, to put away next tick. */
	private static final Set<Entity> STALE = Collections.newSetFromMap(new IdentityHashMap<>());
	/** A household head's cart in the world: its horse (or donkey) and its wagon (or null). */
	private static final Map<UUID, UUID[]> CARTS = new HashMap<>();
	private static final Map<UUID, double[]> PROGRESS = new HashMap<>();
	private static final Set<UUID> TAKING = new HashSet<>();
	/** (0.7.221) Stopped at the gate: when (not saved; a restart lets them try again). */
	private static final Map<UUID, Long> CAUGHT = new HashMap<>();

	private Emigrants() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(Emigrants::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(Emigrants::unloaded);
		ServerTickEvents.END_SERVER_TICK.register(Emigrants::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			STALE.clear();
			CARTS.clear();
			PROGRESS.clear();
			TAKING.clear();
			CAUGHT.clear();
		});
	}

	// ---- the record ---------------------------------------------------------------------------------------------------

	private static Map<String, Road> roads(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(ROADS, Map.of());
	}

	private static @Nullable Road road(MinecraftServer server, UUID who) {
		return roads(server).get(who.toString());
	}

	private static void put(MinecraftServer server, Road road) {
		Map<String, Road> all = new HashMap<>(roads(server));
		all.put(road.who().toString(), road);
		Factions.level(server).setAttached(ROADS, Map.copyOf(all));
	}

	private static void drop(MinecraftServer server, UUID who) {
		Map<String, Road> all = new HashMap<>(roads(server));
		if (all.remove(who.toString()) != null) {
			Factions.level(server).setAttached(ROADS, Map.copyOf(all));
		}
	}

	// ---- in and out of the world ----------------------------------------------------------------------------------------

	private static void loaded(Entity entity, ServerLevel level) {
		if (entity.entityTags().contains(CART_TAG)) {
			// (a cart left in a chunk: put away unless it's out with its household now)
			boolean live = false;
			for (UUID[] cart : CARTS.values()) {
				live |= entity.getUUID().equals(cart[0]) || entity.getUUID().equals(cart[1]);
			}
			if (!live) {
				STALE.add(entity);
			}
			return;
		}
		if (!(entity instanceof Villager v) || !McaCompat.isMca(v)) {
			return;
		}
		if (v.hasAttached(MOVE)) {
			Road road = road(level.getServer(), v.getUUID());
			if (road != null && road.out()) {
				STALE.add(v);
				return;
			}
		}
		LOADED.add(v);
	}

	private static void unloaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof Villager v) || !LOADED.remove(v) || TAKING.contains(v.getUUID()) || !v.hasAttached(MOVE)) {
			return;
		}
		Road road = road(level.getServer(), v.getUUID());
		if (road == null || road.out()) {
			return;
		}
		if (v.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
			CompoundTag body = save(level, v);
			if (body != null) {
				put(level.getServer(), road.at(v.blockPosition()).body(body));
			}
			dropCart(level, v.getUUID(), false);
		} else if (v.getRemovalReason() == Entity.RemovalReason.KILLED) {
			drop(level.getServer(), v.getUUID());
			dropCart(level, v.getUUID(), false);
		}
	}

	private static @Nullable CompoundTag save(ServerLevel level, Villager v) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		return v.saveAsPassenger(out) ? out.buildResult() : null;
	}

	private static void takeOut(ServerLevel level, Villager v, Road road) {
		v.stopRiding();
		v.ejectPassengers();
		CompoundTag body = save(level, v);
		if (body == null) {
			return;
		}
		put(level.getServer(), road.at(v.blockPosition()).body(body));
		dropCart(level, v.getUUID(), false);
		TAKING.add(v.getUUID());
		LOADED.remove(v);
		PROGRESS.remove(v.getUUID());
		v.discard();
		TAKING.remove(v.getUUID());
	}

	private static @Nullable Villager putBack(ServerLevel level, Road road, BlockPos at) {
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		Entity there = level.getEntity(road.who());
		Villager v;
		if (there instanceof Villager mob && !mob.isRemoved()) {
			STALE.remove(mob);
			// (0.7.281) back from the road: wherever a player last saw them
			Attention.carry(() -> mob.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5));
			v = mob;
		} else {
			Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), road.body()), level,
				EntitySpawnReason.LOAD, e -> {
					e.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, e.getYRot(), e.getXRot());
					return e;
				});
			if (!(loaded instanceof Villager mob)) {
				return null;
			}
			put(level.getServer(), road.at(ground).body(new CompoundTag()));
			if (!level.addFreshEntity(mob)) {
				put(level.getServer(), road);
				return null;
			}
			v = mob;
		}
		put(level.getServer(), road.at(ground).body(new CompoundTag()));
		LOADED.add(v);
		return v;
	}

	// ---- the cart -----------------------------------------------------------------------------------------------------

	/** The household head {@code v}'s cart, there behind them on a rope (made if it isn't). */
	private static void cart(ServerLevel level, Villager v) {
		UUID[] have = CARTS.get(v.getUUID());
		if (have != null && level.getEntity(have[0]) instanceof AbstractHorse h && !h.isRemoved()) {
			if (!h.isLeashed()) {
				h.setLeashedTo(v, true);
			}
			return;
		}
		dropCart(level, v.getUUID(), false);
		Vec3 back = Vec3.directionFromRotation(0.0F, v.getYRot()).scale(-3.0);
		BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(v.position().add(back)));
		Faction lends = Factions.around(level.getServer(), level, v.blockPosition(), PLACE_RANGE * 3);
		AbstractHorse horse;
		Wagon wagon = null;
		if (lends != null) {
			horse = Equestrian.create(level, Breed.DRAFT, spot, EntitySpawnReason.MOB_SUMMONED);
		} else {
			horse = EntityTypes.DONKEY.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (horse != null) {
				horse.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, v.getYRot(), 0.0F);
				if (horse instanceof AbstractChestedHorse donkey) {
					donkey.setChest(true);
				}
			}
		}
		if (horse == null) {
			return;
		}
		horse.setTamed(true);
		horse.addTag(CART_TAG);
		horse.setYRot(v.getYRot());
		if (!level.addFreshEntity(horse)) {
			return;
		}
		horse.setLeashedTo(v, true);
		if (lends != null) {
			wagon = Wagon.hitch(level, Wagon.CART, lends, horse, Wagon.NO_CARAVAN, 0);
			wagon.addTag(CART_TAG);
			wagon.setLoad(Wagon.GOODS);
		}
		CARTS.put(v.getUUID(), new UUID[] {horse.getUUID(), wagon == null ? null : wagon.getUUID()});
	}

	/** {@code head}'s cart gone (unpacked, with a puff, when {@code unpacked}). */
	private static void dropCart(ServerLevel level, UUID head, boolean unpacked) {
		UUID[] have = CARTS.remove(head);
		if (have == null) {
			return;
		}
		for (UUID id : have) {
			Entity e = id == null ? null : level.getEntity(id);
			if (e != null && !e.isRemoved()) {
				if (unpacked) {
					level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 8, 0.4, 0.5, 0.4, 0.02);
				}
				e.discard();
			}
		}
	}

	// ---- the days ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		for (Entity stale : new ArrayList<>(STALE)) {
			if (!stale.isRemoved()) {
				TAKING.add(stale.getUUID());
				stale.discard();
				TAKING.remove(stale.getUUID());
			}
		}
		STALE.clear();
		boolean weigh = server.getTickCount() % DECIDE_EVERY == 0;
		for (Villager v : new ArrayList<>(LOADED)) {
			if (v.isRemoved() || !v.isAlive()) {
				LOADED.remove(v);
				if (v.isRemoved() && v.getRemovalReason() != null && v.getRemovalReason().shouldDestroy()) {
					dropCart(level, v.getUUID(), false);
				}
				continue;
			}
			if (v.level() != level) {
				continue;
			}
			Move m = v.getAttached(MOVE);
			if (m != null) {
				step(level, v, m);
			} else if (weigh) {
				decide(level, v);
			}
		}
		for (Road road : List.copyOf(roads(server).values())) {
			if (road.out()) {
				travel(level, road);
			}
		}
	}

	/** Whether {@code v} has had enough, and if so, off they go (with their household). */
	private static void decide(ServerLevel level, Villager v) {
		if (v.isBaby() || Rulers.is(v) || Visitors.is(v) || v.hasAttached(Elders.JOURNEY) || McaCompat.following(v) != null || Downed.isDowned(v) || v.isSleeping()) {
			return;
		}
		long now = level.getGameTime();
		Story story = v.getAttached(STORY);
		if (story != null && now - story.day() < SETTLED_TICKS) {
			return;
		}
		if (McaCompat.playerFamily(v)) {
			return;
		}
		RandomSource random = v.getRandom();
		// (why: the biggest of their grievances)
		float[] why = new float[REASONS.length];
		why[5] = RESTLESS;
		float ridicule = 0.0F;
		int grudges = 0;
		for (LivingEntity x : level.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(NEIGHBOURS),
			x -> x != v && x.isAlive() && !x.isBaby() && VillagerHeritage.eligible(x))) {
			ridicule += Prejudice.minds(x, v);
			if (x instanceof Villager n && Neighbours.regard(v, n) < Neighbours.DISLIKE) {
				grudges++;
			}
		}
		why[0] = Math.min(RIDICULE_CAP, ridicule * RIDICULE);
		if (ridicule > 0.0F && VillagerHeritage.of(v) == Heritage.Kind.BIOBUILT) {
			why[0] = BIO_DRIVEN;
		}
		Faction f = Factions.around(level.getServer(), level, v.blockPosition(), PLACE_RANGE);
		Faction ruler = ruler(level.getServer(), f);
		if (Purists.outsider(ruler, v)) {
			why[1] = Reforms.ease(level.getServer(), ruler, PURIST_OUTSIDER);
		}
		if (Tempers.of(ruler) == Tempers.Temper.TYRANT) {
			why[2] = Reforms.ease(level.getServer(), ruler, TYRANT);
		}
		if (grudges >= GRUDGES_LEAST) {
			why[3] = GRUDGES;
		}
		if (Purists.outsider(ruler, v) && Reforms.stubborn(level.getServer(), ruler)) {
			// ((0.7.221) a purist who dug in drives them out: Reforms)
			why[6] = EXPEL;
		}
		if (McaCompat.mood(v) <= UNHAPPY_MOOD) {
			// ((0.7.221) a ruler's reforms ease all three: Reforms)
			why[4] = Reforms.ease(level.getServer(), ruler, UNHAPPY);
		}
		float daily = 0.0F;
		int most = 5;
		for (int i = 0; i < why.length; i++) {
			daily += why[i];
			if (why[i] > why[most]) {
				most = i;
			}
		}
		if (random.nextFloat() >= daily / CHECKS_A_DAY || now - CAUGHT.getOrDefault(v.getUUID(), Long.MIN_VALUE / 2) < CAUGHT_TICKS) {
			return;
		}
		List<Villager> household = household(level, v);
		String reason = REASONS[most];
		Place to = destination(level, v, f, reason);
		if (to == null) {
			return;
		}
		String from = f != null ? f.name() : town(level, v.blockPosition());
		// (0.7.221) the realm feels it, and its ruler may answer (Reforms)
		Reforms.left(level, ruler, household.size(), reason);
		boolean seen = near(level, v.position(), UNSEEN);
		if (!EXPELLED.equals(reason) && Reforms.caught(level.getServer(), ruler, random)) {
			// (0.7.221) a ruler who dug in: his guards turn them back at the gate
			caught(level, v, household, seen, now);
			return;
		}
		long until = seen ? now + PACKING_TICKS : now;
		for (Villager h : household) {
			h.setAttached(MOVE, new Move(from, to.pos(), to.name(), reason, h == v, PACKING, until));
		}
		if (seen) {
			Speech.say(level, v, Component.translatable("emigrant.jaymoskingdoms.farewell." + reason + "." + random.nextInt(FAREWELL_LINES), to.name(), from),
				Speech.WHITE, 16.0);
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.6F, 0.9F);
		}
	}

	/** {@code v}'s household, stopped at the gate by a stubborn ruler's guards: back home, and the sorrier for it. */
	private static void caught(ServerLevel level, Villager v, List<Villager> household, boolean seen, long now) {
		RandomSource random = v.getRandom();
		for (Villager h : household) {
			CAUGHT.put(h.getUUID(), now);
			McaCompat.cheer(h, -CAUGHT_GLOOM);
		}
		if (!seen) {
			return;
		}
		Folk guard = null;
		for (Folk x : level.getEntitiesOfClass(Folk.class, v.getBoundingBox().inflate(24.0), x -> x.isAlive() && x.role() == Folk.GUARD)) {
			if (guard == null || x.distanceToSqr(v) < guard.distanceToSqr(v)) {
				guard = x;
			}
		}
		if (guard != null) {
			guard.getLookControl().setLookAt(v, 30.0F, 30.0F);
			Speech.say(level, guard, Component.translatable("emigrant.jaymoskingdoms.caught.guard." + random.nextInt(CAUGHT_GUARD_LINES)), Speech.RED, 16.0);
		}
		Speech.say(level, v, Component.translatable("emigrant.jaymoskingdoms.caught." + random.nextInt(CAUGHT_LINES)), Speech.WHITE, 16.0);
		level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.7F, 0.8F);
	}

	/** {@code v} and their partner and children about. */
	private static List<Villager> household(ServerLevel level, Villager v) {
		List<Villager> out = new ArrayList<>();
		out.add(v);
		for (Villager x : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(HOUSEHOLD), x -> x != v && x.isAlive())) {
			int kin = McaCompat.kin(v, x);
			if ((kin == McaCompat.PARTNER || kin == McaCompat.KIN && x.isBaby()) && !x.hasAttached(MOVE) && !x.hasAttached(Elders.JOURNEY)
				&& McaCompat.following(x) == null && !Rulers.is(x)
				&& !McaCompat.playerFamily(x)) {
				out.add(x);
			}
		}
		return out;
	}

	static @Nullable Faction ruler(MinecraftServer server, @Nullable Faction f) {
		if (f == null || Tempers.of(f) != null) {
			return f;
		}
		Faction top = Wars.top(server, f);
		return top != null ? top : f;
	}

	/** Packing, on the road, or arrived. */
	private static void step(ServerLevel level, Villager v, Move m) {
		long now = level.getGameTime();
		if (m.phase() == PACKING) {
			if (m.lead() && near(level, v.position(), APPEAR)) {
				cart(level, v);
			}
			if (now < m.until()) {
				v.getNavigation().stop();
				if (m.lead() && (m.until() - now) % 200 < CHECK_EVERY && near(level, v.position(), 16.0)) {
					Speech.say(level, v, Component.translatable("emigrant.jaymoskingdoms.packing." + v.getRandom().nextInt(PACKING_LINES)), Speech.WHITE, 12.0);
				}
				return;
			}
			McaCompat.leaveVillage(v);
			v.setAttached(MOVE, m.phase(ROAD));
			put(level.getServer(), new Road(v.getUUID(), v.blockPosition(), m.goal(), new CompoundTag()));
			return;
		}
		Road road = road(level.getServer(), v.getUUID());
		if (road == null) {
			arrived(level, v, m);
			return;
		}
		if (!near(level, v.position(), LEAVE)) {
			takeOut(level, v, road);
			return;
		}
		if (m.lead()) {
			cart(level, v);
		}
		put(level.getServer(), road.at(v.blockPosition()));
		Vec3 goal = Vec3.atBottomCenterOf(road.goal());
		double d = Math.hypot(v.getX() - goal.x, v.getZ() - goal.z);
		if (d <= ARRIVE) {
			arrived(level, v, m);
			return;
		}
		if (Downed.isDowned(v) || v.isSleeping() || v.isPassenger() || McaCompat.following(v) != null) {
			return;
		}
		double[] best = PROGRESS.computeIfAbsent(v.getUUID(), id -> new double[] {Double.MAX_VALUE, now});
		if (d < best[0] - 1.0) {
			best[0] = d;
			best[1] = now;
		}
		Vec3 here = v.position();
		Vec3 ahead = Roads.ahead(level, here, road.goal(), WAYPOINT);
		Vec3 next = ahead != null ? ahead : here.add(goal.subtract(here).multiply(1.0, 0.0, 1.0).scale(Math.min(1.0, WAYPOINT / d)));
		if (now - (long)best[1] > STUCK_TICKS) {
			Vec3 hop = here.add(next.subtract(here).multiply(1.0, 0.0, 1.0).normalize().scale(HOP));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(hop));
			v.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
			best[1] = now;
			return;
		}
		v.getNavigation().moveTo(next.x, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(next)).getY(), next.z,
			WALK_SPEED);
	}

	/** One on the road in the record: on along it, back in the world near a player, or arrived. */
	private static void travel(ServerLevel level, Road road) {
		if (level.getEntity(road.who()) instanceof Villager copy && !copy.isRemoved()) {
			STALE.add(copy);
		}
		Vec3 at = Vec3.atBottomCenterOf(road.at());
		Vec3 goal = Vec3.atBottomCenterOf(road.goal());
		double d = Math.hypot(at.x - goal.x, at.z - goal.z);
		if (d <= ARRIVE) {
			Villager v = putBack(level, road, road.goal());
			Move m = v == null ? null : v.getAttached(MOVE);
			if (v != null && m != null) {
				arrived(level, v, m);
			}
			return;
		}
		if (near(level, at, APPEAR) && level.isPositionEntityTicking(road.at())) {
			putBack(level, road, road.at());
			return;
		}
		double stepLength = ROAD_SPEED * CHECK_EVERY;
		Vec3 ahead = Roads.ahead(level, at, road.goal(), stepLength);
		Vec3 next = ahead != null ? ahead : at.add(goal.subtract(at).multiply(1.0, 0.0, 1.0).scale(Math.min(1.0, stepLength / d)));
		put(level.getServer(), road.at(BlockPos.containing(next)));
	}

	/** Got there: the cart unpacked, a home found (MCA), and where they came from remembered. */
	private static void arrived(ServerLevel level, Villager v, Move m) {
		drop(level.getServer(), v.getUUID());
		PROGRESS.remove(v.getUUID());
		dropCart(level, v.getUUID(), true);
		v.getNavigation().stop();
		v.removeAttached(MOVE);
		v.setAttached(STORY, new Story(m.from(), m.to(), m.reason(), level.getGameTime()));
		McaCompat.leaveVillage(v);
	}

	/**
	 * (From {@link Production#survey}) {@code f} was just counted: a realm that had people and now has none is emptied, and
	 * after {@link #EMPTY_RUIN_TICKS} of it (and {@link #EMPTY_COUNTS} counts) falls to ruin, its folk scattered.
	 */
	static void surveyed(ServerLevel level, Faction f) {
		// (only a count made with the town's people loaded about it is believed)
		if (!f.type().isRealm() || f.fallen() || !level.isPositionEntityTicking(f.seat())) {
			return;
		}
		MinecraftServer server = level.getServer();
		Map<String, Long> all = level.getAttachedOrElse(EMPTIED, Map.of());
		String key = Integer.toString(f.id());
		Long was = all.get(key);
		long now = level.getGameTime();
		Map<String, Long> next = new HashMap<>(all);
		if (f.holdings().population() > 0) {
			if (was != null && was == -1L) {
				return;
			}
			next.put(key, -1L);
		} else if (was == null) {
			// (never seen peopled: nothing to lose)
			return;
		} else if (was == -1L) {
			// (first found empty: when, and one count; packed as time << 4 | counts)
			next.put(key, now << 4 | 1L);
		} else {
			long since = was >> 4;
			int counts = (int)(was & 15L);
			if (now - since < EMPTY_RUIN_TICKS || counts + 1 < EMPTY_COUNTS) {
				next.put(key, since << 4 | Math.min(15, counts + 1));
			} else {
				next.remove(key);
				Factions.update(server, f.id(), x -> x.withFallen(true).withHoldings(x.holdings().withRoster(Map.of()).withVacancies(Map.of())));
				for (Folk other : level.getEntitiesOfClass(Folk.class, new AABB(f.seat()).inflate(KingdomEvents.RUIN_SCATTER), x -> x.factionId() == f.id())) {
					other.discard();
				}
				KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.emigrants.ruin", f.name()).withStyle(ChatFormatting.DARK_RED));
			}
		}
		level.setAttached(EMPTIED, Map.copyOf(next));
	}

	// ---- where to -------------------------------------------------------------------------------------------------------

	private record Place(BlockPos pos, String name, boolean alone) {
	}

	/** Somewhere for {@code v} to go, for {@code reason}: a haven, a realm of their blood, a place away from any kingdom, or anywhere. */
	private static @Nullable Place destination(ServerLevel level, Villager v, @Nullable Faction home, String reason) {
		MinecraftServer server = level.getServer();
		RandomSource random = v.getRandom();
		BlockPos here = v.blockPosition();
		Heritage.Kind blood = VillagerHeritage.of(v);
		List<Place> places = new ArrayList<>(), kinPlaces = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f == home || f.type() == FactionType.BARBARIAN || home != null && f.id() == home.id()) {
				continue;
			}
			double d = Math.sqrt(f.seat().distSqr(here));
			if (d < FAR_MIN || d > KIN_FAR_MAX) {
				continue;
			}
			boolean alone = alone(server, f.seat());
			Place p = new Place(f.seat(), f.name(), alone);
			boolean kin = Havens.is(server, f) ? Havens.fits(server, f, blood) : Purists.is(f) && Purists.blood(f) == blood;
			if (kin && (RIDICULED.equals(reason) || PURIST.equals(reason) || EXPELLED.equals(reason))) {
				kinPlaces.add(p);
			} else if (d <= FAR_MAX && !Purists.outsider(f, v) && (!Havens.is(server, f) || Havens.fits(server, f, blood))) {
				places.add(p);
				// ((0.7.221) a realm that reformed to keep its people draws settlers too: Reforms)
				for (int i = Reforms.level(server, f); i > 0; i--) {
					places.add(p);
				}
			}
		}
		for (McaCompat.Town town : McaCompat.towns(level)) {
			double d = Math.sqrt(town.center().distSqr(here));
			if (d >= FAR_MIN && d <= FAR_MAX && Factions.around(server, level, town.center(), PLACE_RANGE) == null) {
				places.add(new Place(town.center(), town.name(), alone(server, town.center())));
			}
		}
		Place p;
		if (!kinPlaces.isEmpty()) {
			p = kinPlaces.get(random.nextInt(kinPlaces.size()));
		} else {
			List<Place> alone = places.stream().filter(Place::alone).toList();
			List<Place> from = !alone.isEmpty() && random.nextFloat() < ALONE_CHANCE ? alone : places;
			if (from.isEmpty()) {
				return null;
			}
			p = from.get(random.nextInt(from.size()));
		}
		return new Place(p.pos().offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8), p.name(), p.alone());
	}

	/** {@code pos} is away from any kingdom: no realm's seat near, not on a realm's land, nor a vassal's. */
	static boolean alone(MinecraftServer server, BlockPos pos) {
		ServerLevel level = Factions.level(server);
		if (Factions.realmNear(server, pos, NO_REALM) != null) {
			return false;
		}
		Faction at = Factions.at(server, level, pos);
		if (at != null && at.type().isRealm()) {
			return false;
		}
		Faction f = Factions.around(server, level, pos, PLACE_RANGE);
		return f == null || f.overlord() < 0 && !f.type().isRealm();
	}

	/** The name of the MCA village about {@code pos}, or a plain word. */
	private static String town(ServerLevel level, BlockPos pos) {
		McaCompat.Town best = null;
		for (McaCompat.Town t : McaCompat.towns(level)) {
			if (t.center().distSqr(pos) < PLACE_RANGE * PLACE_RANGE * 4 && (best == null || t.center().distSqr(pos) < best.center().distSqr(pos))) {
				best = t;
			}
		}
		return best != null ? best.name() : Component.translatable("emigrant.jaymoskingdoms.somewhere").getString();
	}

	private static boolean near(ServerLevel level, Vec3 at, double range) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && Math.hypot(p.getX() - at.x, p.getZ() - at.z) < range) {
				return true;
			}
		}
		return false;
	}

	// ---- talk ---------------------------------------------------------------------------------------------------------

	/**
	 * (A chat, from {@code McaDialogueMixin}) Why they came: one who moved tells where from and why; one of a settlement
	 * away from any kingdom with no story of their own, of their family's leaving long ago. Null if not this time.
	 */
	public static @Nullable Component tell(Villager v, ServerPlayer player) {
		RandomSource random = v.getRandom();
		Story story = v.getAttached(STORY);
		if (story != null) {
			if (random.nextFloat() >= TELL_CHANCE) {
				return null;
			}
			return Component.translatable("emigrant.jaymoskingdoms.story." + story.reason() + "." + random.nextInt(STORY_LINES), story.from(), story.to(),
				Identities.shownName(player));
		}
		if (!(v.level() instanceof ServerLevel level) || v.isBaby() || !alone(level.getServer(), v.blockPosition()) || random.nextFloat() >= OLD_TELL_CHANCE) {
			return null;
		}
		// (their family's own reason, always the same for them)
		UUID id = v.getUUID();
		String reason = REASONS[(int)Math.floorMod(id.getMostSignificantBits() ^ id.getLeastSignificantBits(), (long)REASONS.length)];
		if (RIDICULED.equals(reason) && Prejudice.degree(VillagerHeritage.of(v)) == 0) {
			reason = WANDER;
		}
		Faction old = nearestRealm(level, v.blockPosition());
		Component from = old != null ? Component.literal(old.name()) : Component.translatable("emigrant.jaymoskingdoms.the_kingdoms");
		return Component.translatable("emigrant.jaymoskingdoms.old." + reason + "." + random.nextInt(OLD_LINES), from, Identities.shownName(player));
	}

	private static @Nullable Faction nearestRealm(ServerLevel level, BlockPos pos) {
		Faction best = null;
		for (Faction f : Factions.all(level.getServer())) {
			if (f.type().isRealm() && !f.fallen() && (best == null || f.seat().distSqr(pos) < best.seat().distSqr(pos))) {
				best = f;
			}
		}
		return best;
	}
}
