package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.9) Townsfolk notice the abnormal. Jaymo's Powers tags whatever is strange ({@code jaymospowers_odd:<kind>}),
 * and the people of the world (vanilla and MCA villagers, a realm's folk) react to it:
 * <ul>
 * <li>a meteor or a falling star in the sky: those out of doors stop and stare up at it, and some cry out; where it
 * comes down, everyone near runs from the spot;</li>
 * <li>a power used in front of them: they stare, and say what they saw (the children want it again);</li>
 * <li>the symbiote's blob, or a mob it has poured itself into: a monster, so they run ({@link Frights}) and the guards
 * hunt it ({@link #hunted}; not a villager it's got into, whom they only shout about);</li>
 * <li>a player in the symbiote's suit, walking as a shade, or changed into an alien: a monster, unless they saw the
 * change ({@link Aliens});</li>
 * <li>a strange moon: those out at night remark on it ({@link Moons});</li>
 * <li>a changed player they know (they saw the change): now and then someone remarks on it, unafraid.</li>
 * </ul>
 * And it gets talked about: for days after, a villager's gossip may be of the star that fell to the north, or of the
 * traveller who called the wind down on the square ({@link #gossip}).
 */
final class Oddities {
	/** Powers' tag prefix, and the kinds it marks. */
	static final String PREFIX = "jaymospowers_odd:";
	static final String METEOR = "meteor", STAR = "star", SYMBIOTE = "symbiote", SHADE = "shade", POWER = "power/";
	/** The symbiote without a host (an entity type, not a tag). */
	static final String BLOB = "jaymospowers:web_symbiote_blob";
	/** The powers with lines of their own; any other gets the plain ones. */
	static final Set<String> POWERS = Set.of("wind", "web", "gravity", "chrono", "arsenal", "resonance", "darktide", "xenoform");

	// ---- tuning ----
	/** How often townsfolk look about them for strange things (ticks). */
	static final int EVERY = 10;
	/** A rock in the sky is seen by those out of doors this far off (blocks, across the ground); this many cry out. */
	static final double SKY_SEEN = 112.0;
	static final int SKY_CRIERS = 2;
	/** Where one comes down, those this near run from it; one this near says so after. */
	static final double IMPACT_FRIGHT = 36.0, IMPACT_TALK = 72.0;
	/** A power in use is seen this far off (blocks, in sight); its user is remarked on at most this often (ticks). */
	static final double POWER_SEEN = 24.0;
	static final int POWER_TALK_GAP = 400;
	/** No one person says something strange more often than this (ticks). */
	static final int TALK_GAP = 600;
	/** A player changed in front of people who know it's them: now and then one says so, unafraid. */
	private static void knownSeen(MinecraftServer server, ServerLevel level, ServerPlayer player, long now) {
		if (REMARKED.containsKey(player.getUUID())) {
			return;
		}
		for (String kind : Aliens.KINDS) {
			if (!Aliens.is(player, kind)) {
				continue;
			}
			for (LivingEntity p : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(POWER_SEEN),
				e -> person(server, e) && e.hasLineOfSight(player))) {
				Faction f = Aliens.factionOf(server, p);
				if (f == null || !Aliens.known(player, f.id(), kind) || !free(p, now) || level.getRandom().nextFloat() >= KNOWN_TALK) {
					continue;
				}
				REMARKED.put(player.getUUID(), now + KNOWN_TALK_GAP);
				say(level, p, p.isBaby() ? "child" : "known." + kind, Speech.WHITE, now);
				return;
			}
		}
	}

	/** Under a strange moon, each person out at night remarks on it with this chance per look. */
	static final float MOON_TALK = 0.002F;
	/** Those who know a changed player (they saw the change) remark on it with this chance per look, at most this often. */
	static final float KNOWN_TALK = 0.05F;
	static final int KNOWN_TALK_GAP = 1200;
	/** How many lines there are for each sight. */
	static final int LINES = 6;
	/** How long the strange is talked about (ticks; three days), how many are remembered, and from how far. */
	static final long NEWS_TICKS = 72000L;
	static final int MAX_NEWS = 24;
	static final double NEWS_RANGE = 1000.0;
	/** How often a gossip's talk is of something strange, when there's news near. */
	static final float GOSSIP_CHANCE = 0.45F;

	/** A rock coming down: what it is, where it was last, and who has cried out already. */
	private static final class Rock {
		final String kind;
		Vec3 at;
		long seen;
		final Set<UUID> criers = new HashSet<>();

		Rock(String kind, Vec3 at) {
			this.kind = kind;
			this.at = at;
		}
	}

	/** Something strange that happened: what, where, when, and (a power) whose. */
	record News(String kind, BlockPos pos, long time, @Nullable Component who) {
	}

	private static final Map<UUID, Rock> ROCKS = new HashMap<>();
	private static final Map<UUID, Long> SPOKE = new HashMap<>();
	private static final Map<UUID, Long> REMARKED = new HashMap<>();
	private static final List<News> NEWS = new ArrayList<>();

	private Oddities() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Oddities::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ROCKS.clear();
			SPOKE.clear();
			REMARKED.clear();
			NEWS.clear();
		});
	}

	// ---- what's strange ------------------------------------------------------------------------------------------------

	static boolean has(Entity e, String kind) {
		return e.entityTags().contains(PREFIX + kind);
	}

	/** The power {@code e} (a player) just used, by id path, or null. */
	static @Nullable String power(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(PREFIX + POWER)) {
				return tag.substring(PREFIX.length() + POWER.length());
			}
		}
		return null;
	}

	/** A symbiote that isn't a player's: its blob, or a mob it's poured itself into (a behemoth, an inhabited beast). */
	static boolean monstrous(Entity e) {
		return !(e instanceof Player) && (has(e, SYMBIOTE) || BLOB.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()));
	}

	/** A guard goes for it: a {@link #monstrous} thing, unless it's one of the villagers it's got into. */
	static boolean hunted(Entity e) {
		return e.isAlive() && monstrous(e) && !(e instanceof Villager);
	}

	/** One of the world's people, who'd notice: a villager (MCA's too), or a realm's folk. */
	private static boolean person(MinecraftServer server, LivingEntity e) {
		if (!e.isAlive() || e.isSleeping()) {
			return false;
		}
		if (e instanceof Villager) {
			return true;
		}
		if (e instanceof Folk folk) {
			Faction f = Factions.get(server, folk.factionId());
			return f != null && !f.fallen() && f.type().isRealm() && !Assault.attacker(folk);
		}
		return false;
	}

	// ---- looking about -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		NEWS.removeIf(n -> now - n.time() > NEWS_TICKS);
		SPOKE.values().removeIf(t -> t < now);
		REMARKED.values().removeIf(t -> t < now);
		sky(server, level, now);
		for (ServerPlayer player : level.players()) {
			String power = power(player);
			if (power != null && !player.isSpectator()) {
				powerSeen(server, level, player, power, now);
			}
			if (!player.isSpectator()) {
				knownSeen(server, level, player, now);
			}
		}
		moon(server, level);
	}

	/** The rocks in the sky: stared at while they fall, run from where they land. */
	private static void sky(MinecraftServer server, ServerLevel level, long now) {
		for (Entity e : level.getAllEntities()) {
			String kind = has(e, METEOR) ? METEOR : has(e, STAR) ? STAR : null;
			if (kind != null && e.isAlive()) {
				Rock rock = ROCKS.computeIfAbsent(e.getUUID(), id -> new Rock(kind, e.position()));
				rock.at = e.position();
				rock.seen = now;
				watchSky(server, level, e, rock, now);
			}
		}
		for (var it = ROCKS.entrySet().iterator(); it.hasNext(); ) {
			Rock rock = it.next().getValue();
			if (rock.seen < now) {
				it.remove();
				landed(server, level, rock, now);
			}
		}
	}

	private static void watchSky(MinecraftServer server, ServerLevel level, Entity rock, Rock seen, long now) {
		AABB box = new AABB(rock.getX() - SKY_SEEN, level.getMinY(), rock.getZ() - SKY_SEEN, rock.getX() + SKY_SEEN, level.getMaxY(), rock.getZ() + SKY_SEEN);
		for (LivingEntity p : level.getEntitiesOfClass(LivingEntity.class, box, e -> person(server, e))) {
			if (!level.canSeeSky(BlockPos.containing(p.getEyePosition()))) {
				continue;
			}
			lookAt(p, rock);
			if (seen.criers.size() < SKY_CRIERS && !seen.criers.contains(p.getUUID()) && free(p, now)) {
				seen.criers.add(p.getUUID());
				say(level, p, p.isBaby() ? "child" : seen.kind, Speech.YELLOW, now);
			}
		}
	}

	/** It came down: those near run from the spot, one says where, and it's talked about. */
	private static void landed(MinecraftServer server, ServerLevel level, Rock rock, long now) {
		news(new News(rock.kind, BlockPos.containing(rock.at), now, null));
		LivingEntity talker = null;
		for (LivingEntity p : level.getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(rock.at)).inflate(IMPACT_TALK), e -> person(server, e))) {
			double d = p.position().distanceTo(rock.at);
			if (d < IMPACT_FRIGHT && p instanceof Villager v) {
				Frights.scare(level, v, rock.at);
			}
			if (talker == null && free(p, now)) {
				talker = p;
			}
		}
		if (talker != null) {
			say(level, talker, "landed", Speech.YELLOW, now);
		}
	}

	/** A power used where people can see it: they stare, and someone says what they saw. */
	private static void powerSeen(MinecraftServer server, ServerLevel level, ServerPlayer player, String power, long now) {
		LivingEntity talker = null;
		for (LivingEntity p : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(POWER_SEEN),
			e -> person(server, e) && e.hasLineOfSight(player))) {
			lookAt(p, player);
			if (talker == null && free(p, now) && !(p instanceof Folk folk && folk.combatant() && folk.getTarget() != null)) {
				talker = p;
			}
		}
		if (talker == null || REMARKED.containsKey(player.getUUID())) {
			return;
		}
		REMARKED.put(player.getUUID(), now + POWER_TALK_GAP);
		String line = talker.isBaby() ? "child" : POWERS.contains(power) ? "power." + power : "power";
		say(level, talker, line, Speech.WHITE, now);
		news(new News(POWER + power, player.blockPosition(), now, player.getDisplayName()));
	}

	/** Under a strange moon, those out at night remark on it now and then. */
	private static void moon(MinecraftServer server, ServerLevel level) {
		Moons.Moon moon = Moons.now(server);
		if (moon == Moons.Moon.NONE || !level.isDarkOutside()) {
			return;
		}
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			for (LivingEntity p : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32.0), e -> person(server, e))) {
				if (random.nextFloat() < MOON_TALK && free(p, now) && level.canSeeSky(BlockPos.containing(p.getEyePosition()))) {
					say(level, p, "moon." + moon.id(), Speech.WHITE, now);
				}
			}
		}
	}

	/**
	 * A villager just took fright at {@code monster}: if it's one of the strange ones (a symbiote, a shade, an alien), it
	 * cries out what it is, and that's talked about.
	 */
	static void cry(ServerLevel level, Villager v, LivingEntity monster, Faction f) {
		long now = level.getGameTime();
		if (!free(v, now)) {
			return;
		}
		String kind = null;
		if (monster instanceof ServerPlayer player) {
			kind = Aliens.fearedAs(player, f.id());
		} else if (monstrous(monster)) {
			kind = monster instanceof Villager ? "host" : SYMBIOTE;
		}
		if (kind == null) {
			return;
		}
		if ("host".equals(kind)) {
			Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.odd.fear.host." + level.getRandom().nextInt(LINES), monster.getDisplayName()), Speech.RED, 24.0);
			SPOKE.put(v.getUUID(), now + TALK_GAP);
		} else {
			say(level, v, "fear." + kind, Speech.RED, now);
		}
		if (!Aliens.ALIEN.equals(kind)) {
			news(new News(SYMBIOTE.equals(kind) || "host".equals(kind) ? SYMBIOTE : kind, monster.blockPosition(), now, null));
		}
	}

	// ---- talk ----------------------------------------------------------------------------------------------------------

	/** A line of gossip about the nearest strange thing to {@code villager}, or null if nothing strange is near. */
	static @Nullable Component gossip(ServerLevel level, LivingEntity villager) {
		if (level.getRandom().nextFloat() >= GOSSIP_CHANCE) {
			return null;
		}
		News best = null;
		double bestD = NEWS_RANGE * NEWS_RANGE;
		for (News n : NEWS) {
			double d = n.pos().distSqr(villager.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = n;
			}
		}
		if (best == null) {
			return null;
		}
		int blocks = Math.max(50, Math.round((float)Math.sqrt(bestD) / 50.0F) * 50);
		Component way = Sightings.direction(villager.blockPosition(), best.pos());
		int line = level.getRandom().nextInt(LINES);
		if (best.kind().startsWith(POWER)) {
			String power = best.kind().substring(POWER.length());
			String key = POWERS.contains(power) ? "talk.jaymoskingdoms.gossip.odd.power." + power + "." + line : "talk.jaymoskingdoms.gossip.odd.power." + line;
			return Component.translatable(key, best.who() == null ? Component.literal("?") : best.who());
		}
		return Component.translatable("talk.jaymoskingdoms.gossip.odd." + best.kind() + "." + line, blocks, way);
	}

	private static void news(News news) {
		// one bit of news of a kind for each place: a second meteor in the same crater is the same story
		NEWS.removeIf(n -> n.kind().equals(news.kind()) && n.pos().closerThan(news.pos(), 48.0));
		NEWS.add(news);
		while (NEWS.size() > MAX_NEWS) {
			NEWS.removeFirst();
		}
	}

	private static boolean free(LivingEntity p, long now) {
		return !SPOKE.containsKey(p.getUUID()) || SPOKE.get(p.getUUID()) < now;
	}

	private static void say(ServerLevel level, LivingEntity p, String what, int color, long now) {
		SPOKE.put(p.getUUID(), now + TALK_GAP);
		Speech.say(level, p, Component.translatable("talk.jaymoskingdoms.odd." + what + "." + level.getRandom().nextInt(LINES)), color, 32.0);
	}

	/** {@code p} turns to stare at {@code at}. */
	private static void lookAt(LivingEntity p, Entity at) {
		if (p instanceof Villager v) {
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(at, true));
		} else if (p instanceof Mob mob) {
			mob.getLookControl().setLookAt(at, 30.0F, 30.0F);
		}
	}
}
