package com.jaymoskingdoms;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.184) The elders a {@link Lineage} leaves behind live lives of their own. Andy: "they can still choose to do what
 * they used to under your control, only autonomous now. So you could ask your father/mother to go on an adventure with
 * you for old times sake, or even find them on travels of their own." Then: "Is it possible to see them traveling along
 * the way?" / "Yes please".
 * Asked along, they follow you as any MCA family does. Left be, an elder stays home a few days, then sets off on foot
 * for a village or a realm's seat some way off ({@link #FAR_MIN} to {@link #FAR_MAX} blocks), stays a few days there,
 * and walks home again. They only set off while nobody's near enough to see them go, and not while following anyone.
 * <p>The road: like the {@link Caravans}, a journey goes on in a record ({@link Road}) while nobody's near, along a
 * {@link Roads King's Road} where one runs their way, else straight across the land, at {@link #ROAD_SPEED}. When a
 * player comes within {@link #APPEAR} of where they've got to, the elder is put back in the world there (their whole
 * self, saved: same villager, same family, same things) and walks on towards the town for real; meet them, talk, ask
 * them along. When everyone's gone {@link #LEAVE} off again, they go back into the record. Whoever they used to be
 * (their heir, the player) gets an MCA letter when they set off and when they're home.
 */
public final class Elders {
	/** Days at home between journeys, and days away at the far end. */
	static final int HOME_DAYS_MIN = 2, HOME_DAYS_MAX = 5, AWAY_DAYS_MIN = 1, AWAY_DAYS_MAX = 3;
	/** How far a journey goes (blocks from home). */
	static final int FAR_MIN = 250, FAR_MAX = 1500;
	/** Nobody within this far may see them set off (blocks). */
	static final double UNSEEN = 64.0;
	/** On the road: put in the world when a player is this close to where they've got to, taken out when none is this close. */
	static final double APPEAR = 80.0, LEAVE = 112.0;
	/** Their pace unseen (blocks a tick: an old walker's, slower than a caravan's); seen, they walk at this share of their speed. */
	static final double ROAD_SPEED = 0.1, WALK_SPEED = 0.6;
	/** Seen, they make for a point this far ahead on their road; arrived this close. */
	static final double WAYPOINT = 16.0, ARRIVE = 10.0;
	/** No nearer for this long (ticks), seen, and they're helped over whatever is in their way: set this far on. */
	static final int STUCK_TICKS = 240;
	static final double HOP = 6.0;
	/** With nowhere to go, they look again after this long (ticks). */
	static final long RETRY_TICKS = 6000L;
	/** How often the elders and their roads are seen to (ticks). */
	static final int CHECK_EVERY = 20;
	static final long DAY_TICKS = 24000L;
	/** A journey's phase: at home, at the far end, on the road. */
	public static final int HOME = 0, AWAY = 1, ROAD = 2;

	/**
	 * An elder's journeys: {@code heir} the player they used to be, {@code home} where they live, {@code away} and
	 * {@code place} where they're going or have gone, and the game time {@code until} they next set off.
	 */
	public record Journey(UUID heir, BlockPos home, BlockPos away, String place, int phase, long until) {
		static final Codec<Journey> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("heir").forGetter(Journey::heir),
			BlockPos.CODEC.fieldOf("home").forGetter(Journey::home),
			BlockPos.CODEC.fieldOf("away").forGetter(Journey::away),
			Codec.STRING.fieldOf("place").forGetter(Journey::place),
			Codec.INT.fieldOf("phase").forGetter(Journey::phase),
			Codec.LONG.fieldOf("until").forGetter(Journey::until)).apply(i, Journey::new));

		Journey phase(int phase, long until) {
			return new Journey(this.heir, this.home, this.away, this.place, phase, until);
		}
	}

	/**
	 * An elder on the road: {@code elder} their id, {@code at} where they've got to, making for {@code goal} ({@code back}:
	 * home); {@code body} their whole saved self while they're out of the world (empty while they walk in it).
	 */
	record Road(UUID elder, BlockPos at, BlockPos goal, boolean back, CompoundTag body) {
		static final Codec<Road> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("elder").forGetter(Road::elder),
			BlockPos.CODEC.fieldOf("at").forGetter(Road::at),
			BlockPos.CODEC.fieldOf("goal").forGetter(Road::goal),
			Codec.BOOL.fieldOf("back").forGetter(Road::back),
			CompoundTag.CODEC.fieldOf("body").forGetter(Road::body)).apply(i, Road::new));

		Road at(BlockPos at) {
			return new Road(this.elder, at, this.goal, this.back, this.body);
		}

		Road body(CompoundTag body) {
			return new Road(this.elder, this.at, this.goal, this.back, body);
		}

		boolean out() {
			return !this.body.isEmpty();
		}
	}

	/** On a lineage's elder: their journeys. Saved. */
	public static final AttachmentType<Journey> JOURNEY = AttachmentRegistry.create(JaymosKingdoms.id("elder_journey"),
		builder -> builder.persistent(Journey.CODEC));
	/** On the Overworld: every elder on the road, by their id. Saved. */
	static final AttachmentType<Map<String, Road>> ROADS = AttachmentRegistry.create(JaymosKingdoms.id("elder_roads"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Road.CODEC)));

	/** The elders loaded just now. */
	private static final Set<PathfinderMob> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Out-of-date copies of elders who are back in the record (left in a chunk when it unloaded): put away next tick. */
	private static final Set<Entity> STALE = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Seen walkers: their best distance to their goal so far, and when it last got better. */
	private static final Map<UUID, double[]> PROGRESS = new HashMap<>();
	/** Elders being taken out of the world by us just now (so their unloading isn't taken for the chunk's). */
	private static final Set<UUID> TAKING = new HashSet<>();

	private Elders() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(Elders::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(Elders::unloaded);
		ServerTickEvents.END_SERVER_TICK.register(Elders::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			STALE.clear();
			PROGRESS.clear();
			TAKING.clear();
		});
	}

	/** {@code elder} was just left behind by {@code heir}: home is where they stand, and they'll stay a while first. */
	static void start(PathfinderMob elder, ServerPlayer heir) {
		long now = elder.level().getGameTime();
		elder.setAttached(JOURNEY, new Journey(heir.getUUID(), elder.blockPosition(), elder.blockPosition(), "", HOME,
			now + days(elder, HOME_DAYS_MIN, HOME_DAYS_MAX)));
		LOADED.add(elder);
	}

	private static long days(Entity elder, int min, int max) {
		return (min + elder.getRandom().nextInt(max - min + 1)) * DAY_TICKS;
	}

	// ---- the record of roads ------------------------------------------------------------------------------------------

	private static Map<String, Road> roads(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(ROADS, Map.of());
	}

	private static @Nullable Road road(MinecraftServer server, UUID elder) {
		return roads(server).get(elder.toString());
	}

	private static void put(MinecraftServer server, Road road) {
		Map<String, Road> all = new HashMap<>(roads(server));
		all.put(road.elder().toString(), road);
		Factions.level(server).setAttached(ROADS, Map.copyOf(all));
	}

	private static void drop(MinecraftServer server, UUID elder) {
		Map<String, Road> all = new HashMap<>(roads(server));
		if (all.remove(elder.toString()) != null) {
			Factions.level(server).setAttached(ROADS, Map.copyOf(all));
		}
	}

	// ---- in and out of the world ----------------------------------------------------------------------------------------

	private static void loaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof PathfinderMob mob) || !mob.hasAttached(JOURNEY)) {
			return;
		}
		Road road = road(level.getServer(), mob.getUUID());
		if (road != null && road.out()) {
			// (a copy left behind in a chunk: the elder goes on in the record)
			STALE.add(mob);
			return;
		}
		LOADED.add(mob);
	}

	private static void unloaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof PathfinderMob mob) || !LOADED.remove(mob) || TAKING.contains(mob.getUUID())) {
			return;
		}
		Road road = road(level.getServer(), mob.getUUID());
		if (road == null || road.out()) {
			return;
		}
		if (mob.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
			// (their chunk went with them on the road: they go on in the record, and the chunk's copy is put away later)
			CompoundTag body = save(level, mob);
			if (body != null) {
				put(level.getServer(), road.at(mob.blockPosition()).body(body));
			}
		} else if (mob.getRemovalReason() == Entity.RemovalReason.KILLED) {
			// (they died on the road)
			drop(level.getServer(), mob.getUUID());
		}
	}

	/** {@code elder}'s whole self, saved, or null if it can't be. */
	private static @Nullable CompoundTag save(ServerLevel level, PathfinderMob elder) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		return elder.saveAsPassenger(out) ? out.buildResult() : null;
	}

	/** Takes {@code elder} out of the world into their road (or a new one). */
	private static void takeOut(ServerLevel level, PathfinderMob elder, Road road) {
		elder.stopRiding();
		elder.ejectPassengers();
		CompoundTag body = save(level, elder);
		if (body == null) {
			return;
		}
		put(level.getServer(), road.at(elder.blockPosition()).body(body));
		TAKING.add(elder.getUUID());
		LOADED.remove(elder);
		PROGRESS.remove(elder.getUUID());
		elder.discard();
		TAKING.remove(elder.getUUID());
	}

	/** Puts the elder on {@code road} back in the world, on the ground where they've got to; the walker, or null. */
	private static @Nullable PathfinderMob putBack(ServerLevel level, Road road, BlockPos at) {
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		Entity there = level.getEntity(road.elder());
		PathfinderMob elder;
		if (there instanceof PathfinderMob mob && !mob.isRemoved()) {
			// (their old copy is still about: it takes up the road)
			STALE.remove(mob);
			mob.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
			elder = mob;
		} else {
			Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), road.body()), level,
				EntitySpawnReason.LOAD, e -> {
					e.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, e.getYRot(), e.getXRot());
					return e;
				});
			if (!(loaded instanceof PathfinderMob mob)) {
				return null;
			}
			// (the record says they're in the world before they are, so their loading isn't taken for a stale copy's)
			put(level.getServer(), road.at(ground).body(new CompoundTag()));
			if (!level.addFreshEntity(mob)) {
				put(level.getServer(), road);
				return null;
			}
			elder = mob;
		}
		put(level.getServer(), road.at(ground).body(new CompoundTag()));
		LOADED.add(elder);
		return elder;
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
		for (PathfinderMob elder : new ArrayList<>(LOADED)) {
			if (elder.isRemoved() || !elder.isAlive()) {
				LOADED.remove(elder);
				continue;
			}
			if (elder.level() == level) {
				step(level, elder);
			}
		}
		for (Road road : List.copyOf(roads(server).values())) {
			if (road.out()) {
				travel(level, road);
			}
		}
	}

	/** An elder in the world: at home or away, perhaps off; on the road, walking on. */
	private static void step(ServerLevel level, PathfinderMob elder) {
		Journey j = elder.getAttached(JOURNEY);
		if (j == null) {
			return;
		}
		long now = level.getGameTime();
		if (j.phase() == ROAD) {
			walk(level, elder, j);
			return;
		}
		if (now < j.until() || Downed.isDowned(elder) || elder.isSleeping() || elder.isPassenger() || following(elder) || near(level, elder.position(), UNSEEN)) {
			return;
		}
		Component name = elder.getDisplayName();
		if (j.phase() == HOME) {
			Place to = destination(level, j.home(), elder);
			if (to == null) {
				elder.setAttached(JOURNEY, j.phase(HOME, now + RETRY_TICKS));
				return;
			}
			elder.setAttached(JOURNEY, new Journey(j.heir(), j.home(), to.pos(), to.name(), ROAD, 0L));
			put(level.getServer(), new Road(elder.getUUID(), elder.blockPosition(), to.pos(), false, new CompoundTag()));
			McaCompat.letter(level, j.heir(), Component.translatable("message.jaymoskingdoms.elder.set_off", name, to.name()));
		} else {
			elder.setAttached(JOURNEY, j.phase(ROAD, 0L));
			put(level.getServer(), new Road(elder.getUUID(), elder.blockPosition(), j.home(), true, new CompoundTag()));
		}
	}

	/** A walker in the world: on along their road, or out of it if nobody's about, or arrived. */
	private static void walk(ServerLevel level, PathfinderMob elder, Journey j) {
		Road road = road(level.getServer(), elder.getUUID());
		if (road == null) {
			// (the record of the road is lost: they make the best of where they are)
			elder.setAttached(JOURNEY, j.phase(AWAY, level.getGameTime() + days(elder, AWAY_DAYS_MIN, AWAY_DAYS_MAX)));
			return;
		}
		if (!near(level, elder.position(), LEAVE) && !following(elder)) {
			takeOut(level, elder, road);
			return;
		}
		put(level.getServer(), road.at(elder.blockPosition()));
		Vec3 goal = Vec3.atBottomCenterOf(road.goal());
		double d = Math.hypot(elder.getX() - goal.x, elder.getZ() - goal.z);
		if (d <= ARRIVE) {
			arrived(level, elder, j, road);
			return;
		}
		if (following(elder) || Downed.isDowned(elder) || elder.isSleeping() || elder.isPassenger()) {
			return;
		}
		long now = level.getGameTime();
		double[] best = PROGRESS.computeIfAbsent(elder.getUUID(), id -> new double[] {Double.MAX_VALUE, now});
		if (d < best[0] - 1.0) {
			best[0] = d;
			best[1] = now;
		}
		Vec3 here = elder.position();
		Vec3 ahead = Roads.ahead(level, here, road.goal(), WAYPOINT);
		Vec3 next = ahead != null ? ahead : here.add(goal.subtract(here).multiply(1.0, 0.0, 1.0).scale(Math.min(1.0, WAYPOINT / d)));
		if (now - (long)best[1] > STUCK_TICKS) {
			// (stuck on something: helped over it)
			Vec3 hop = here.add(next.subtract(here).multiply(1.0, 0.0, 1.0).normalize().scale(HOP));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(hop));
			elder.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
			best[1] = now;
			return;
		}
		elder.getNavigation().moveTo(next.x, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(next)).getY(),
			next.z, WALK_SPEED);
	}

	/** A road in the record: on along it, back in the world if someone's near, or arrived. */
	private static void travel(ServerLevel level, Road road) {
		if (level.getEntity(road.elder()) instanceof PathfinderMob copy && !copy.isRemoved()) {
			STALE.add(copy);
		}
		Vec3 at = Vec3.atBottomCenterOf(road.at());
		Vec3 goal = Vec3.atBottomCenterOf(road.goal());
		double d = Math.hypot(at.x - goal.x, at.z - goal.z);
		if (d <= ARRIVE) {
			PathfinderMob elder = putBack(level, road, road.goal());
			Journey j = elder == null ? null : elder.getAttached(JOURNEY);
			if (elder != null && j != null) {
				arrived(level, elder, j, road);
			}
			return;
		}
		if (near(level, at, APPEAR) && level.isPositionEntityTicking(road.at())) {
			putBack(level, road, road.at());
			return;
		}
		double step = ROAD_SPEED * CHECK_EVERY;
		Vec3 ahead = Roads.ahead(level, at, road.goal(), step);
		Vec3 next = ahead != null ? ahead : at.add(goal.subtract(at).multiply(1.0, 0.0, 1.0).scale(Math.min(1.0, step / d)));
		put(level.getServer(), road.at(BlockPos.containing(next)));
	}

	/** {@code elder} got where they were going: a stay there, or home again. */
	private static void arrived(ServerLevel level, PathfinderMob elder, Journey j, Road road) {
		long now = level.getGameTime();
		drop(level.getServer(), elder.getUUID());
		PROGRESS.remove(elder.getUUID());
		elder.getNavigation().stop();
		if (road.back()) {
			elder.setAttached(JOURNEY, new Journey(j.heir(), j.home(), j.home(), "", HOME, now + days(elder, HOME_DAYS_MIN, HOME_DAYS_MAX)));
			McaCompat.letter(level, j.heir(), Component.translatable("message.jaymoskingdoms.elder.home", elder.getDisplayName(), j.place()));
		} else {
			elder.setAttached(JOURNEY, j.phase(AWAY, now + days(elder, AWAY_DAYS_MIN, AWAY_DAYS_MAX)));
		}
	}

	private static boolean following(PathfinderMob elder) {
		return elder instanceof Villager v && McaCompat.following(v) != null;
	}

	/** Whether a player is within {@code range} of {@code at} (across the land). */
	private static boolean near(ServerLevel level, Vec3 at, double range) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && Math.hypot(p.getX() - at.x, p.getZ() - at.z) < range) {
				return true;
			}
		}
		return false;
	}

	/** Somewhere to go and its name. */
	private record Place(BlockPos pos, String name) {
	}

	/** A village (MCA's) or a standing realm's seat between {@link #FAR_MIN} and {@link #FAR_MAX} from {@code home}, or null. */
	private static @Nullable Place destination(ServerLevel level, BlockPos home, PathfinderMob elder) {
		List<Place> places = new ArrayList<>();
		for (McaCompat.Town town : McaCompat.towns(level)) {
			places.add(new Place(town.center(), town.name()));
		}
		for (Faction f : Factions.all(level.getServer())) {
			if (f.type().isRealm() && !f.fallen()) {
				places.add(new Place(f.seat(), f.name()));
			}
		}
		places.removeIf(p -> {
			double d = Math.sqrt(p.pos().distSqr(home));
			return d < FAR_MIN || d > FAR_MAX;
		});
		if (places.isEmpty()) {
			return null;
		}
		Place p = places.get(elder.getRandom().nextInt(places.size()));
		return new Place(p.pos().offset(elder.getRandom().nextInt(17) - 8, 0, elder.getRandom().nextInt(17) - 8), p.name());
	}
}
