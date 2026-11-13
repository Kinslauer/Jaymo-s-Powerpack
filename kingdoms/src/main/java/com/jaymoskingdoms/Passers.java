package com.jaymoskingdoms;

import java.util.ArrayDeque;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What passed by, as the animals saw it. Every few seconds, wherever a player is about, anyone who walks within
 * {@link #SEEN_FROM} blocks of an animal is jotted down against the stretch of land (a 16-block square) the animal is in: what
 * they were, what they carried (a player's hand), roughly which way they went and when. Speaking with an animal ({@link Rituals})
 * then sometimes has it tell one of these, in a bubble only the listener sees. The log is short and forgets after two days; it is
 * not saved over a restart (animals forget too).
 */
public final class Passers {
	/** How often (ticks) the land is looked at; how near an animal must be to see a passer; how many are kept per square; how long (ticks). */
	static final int EVERY = 40;
	static final double SEEN_FROM = 10.0, WATCHED = 48.0;
	static final int KEPT = 8;
	static final long FORGET = 2L * 24000L, SAME_PASSER = 600L;
	/** The chance an animal that is asked tells what passed rather than how it feels. */
	public static final float TELLS = 0.5F;

	private record Sighting(UUID who, String kind, Component what, long when, int way, Vec3 at) {
	}

	private static final Map<String, ArrayDeque<Sighting>> LOG = new HashMap<>();

	private Passers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Passers::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LOG.clear());
	}

	private static String cell(ServerLevel level, Vec3 at) {
		return level.dimension().identifier() + "/" + ((int) Math.floor(at.x) >> 4) + "/" + ((int) Math.floor(at.z) >> 4);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer watcher : server.getPlayerList().getPlayers()) {
			if (!(watcher.level() instanceof ServerLevel level) || watcher.isSpectator()) {
				continue;
			}
			var box = watcher.getBoundingBox().inflate(WATCHED);
			List<Animal> animals = level.getEntitiesOfClass(Animal.class, box, Animal::isAlive);
			if (animals.isEmpty()) {
				continue;
			}
			for (LivingEntity who : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !(e instanceof Animal) && !e.isSpectator()
				&& (e instanceof Player || e instanceof Villager || e instanceof Enemy || Temperaments.humanoid(e)))) {
				for (Animal a : animals) {
					if (a.distanceToSqr(who) <= SEEN_FROM * SEEN_FROM && (who != a)) {
						note(level, a, who);
						break;
					}
				}
			}
		}
	}

	private static void note(ServerLevel level, Animal seen, LivingEntity who) {
		long now = level.getGameTime();
		ArrayDeque<Sighting> log = LOG.computeIfAbsent(cell(level, seen.position()), k -> new ArrayDeque<>());
		log.removeIf(s -> now - s.when() > FORGET || s.who().equals(who.getUUID()) && now - s.when() < SAME_PASSER);
		Vec3 v = who.getDeltaMovement();
		Vec3 from = log.stream().filter(s -> s.who().equals(who.getUUID())).map(Sighting::at).findFirst().orElse(null);
		Vec3 heading = from != null ? who.position().subtract(from) : v;
		int way = Math.floorMod(Math.round((float) (net.minecraft.util.Mth.atan2(heading.z, heading.x) * net.minecraft.util.Mth.RAD_TO_DEG) / 90.0F), 4);
		String kind = who instanceof Player ? "walker" : who instanceof Villager ? "villager" : who instanceof Enemy ? "hunter" : "person";
		Component what = who instanceof Player p && !p.getMainHandItem().isEmpty()
			? Component.translatable("speech.jaymoskingdoms.passed.carrying", p.getMainHandItem().getHoverName())
			: who instanceof Enemy ? Component.translatable("speech.jaymoskingdoms.passed.named", who.getType().getDescription()) : Component.empty();
		log.addLast(new Sighting(who.getUUID(), kind, what, now, way, who.position()));
		while (log.size() > KEPT) {
			log.removeFirst();
		}
	}

	/** {@code animal} tells {@code listener} who passed its patch lately, if anyone did (never the listener themself). */
	static boolean tell(ServerLevel level, ServerPlayer listener, Animal animal) {
		ArrayDeque<Sighting> log = LOG.get(cell(level, animal.position()));
		if (log == null) {
			return false;
		}
		long now = level.getGameTime();
		List<Sighting> pool = log.stream().filter(s -> !s.who().equals(listener.getUUID()) && now - s.when() <= FORGET).toList();
		if (pool.isEmpty()) {
			return false;
		}
		Sighting s = pool.get(level.getRandom().nextInt(pool.size()));
		long ago = now - s.when();
		String when = ago < 1200 ? "now" : ago < 12000 ? "lately" : ago < 24000 ? "today" : "yesterday";
		String[] ways = {"east", "south", "west", "north"};
		String plain = s.what().getString().isEmpty() ? "plain." : "";
		Component line = Component.translatable("speech.jaymoskingdoms.animal.passed." + plain + level.getRandom().nextInt(2),
			Component.translatable("speech.jaymoskingdoms.passed.kind." + s.kind()), s.what(),
			Component.translatable("speech.jaymoskingdoms.passed.when." + when), Component.translatable("speech.jaymoskingdoms.passed.way." + ways[s.way()]));
		Speech.privately(animal, listener, line, Speech.GOLD);
		return true;
	}
}
