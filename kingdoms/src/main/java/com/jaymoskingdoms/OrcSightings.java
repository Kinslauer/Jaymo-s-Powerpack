package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.562) Andy (2026-11-05): "everyone should have orc specific lines when they encounter them, some friendly,
 * adversarial, and fighting". Anyone who isn't an orc (an MCA villager, a guard or other worker, a goblin) remarks on an
 * orc they see: an orc villager, a camp's orc, or a half-orc player who isn't masked.
 * <ul>
 * <li><b>Fighting</b>: they and the orc are at blows (either has the other as its target, or the orc just hurt them).</li>
 * <li><b>Adversarial</b>: an orc of a tribe that isn't theirs (unless they live in a haven alongside the tribes), or one
 * whose blood they mind ({@link Prejudice#minds}: the distaste between peoples, {@link Peoples}).</li>
 * <li><b>Friendly</b>: they don't mind it (the tolerant, a greenskin haven's folk, a ruler's edicts), or a half-orc
 * player they're fond of ({@link Bearings#fond}).</li>
 * </ul>
 * A goblin says it in its own dialect ({@code dialect.jaymoskingdoms.goblin.talk.jaymoskingdoms.orc_seen.<mood>.<n>}).
 * Each pair remarks once in a while; a fight is shouted more often.
 */
public final class OrcSightings {
	// ================================================================================================================
	// Tuning
	/** How often each player's surroundings are looked at (ticks); orcs this near a player; speakers this near an orc; heard this far. */
	static final int TICK = 40;
	static final double ORC_RANGE = 16.0, SEE_RANGE = 10.0, HEARD = 16.0;
	/** The chance per check that a speaker who hasn't remarked on this orc lately does so; a fight's chance per check. */
	static final float CALM_CHANCE = 0.35F, FIGHT_CHANCE = 0.3F;
	/** How long one speaker waits between any two remarks (ticks), between fight shouts, and before remarking on the same orc again. */
	static final int SPEAK_REST = 20 * 45, FIGHT_REST = 20 * 10, PAIR_REST = 20 * 60 * 10;
	/** How recently the orc must have hurt them to count as a fight (ticks). */
	static final int HURT_RECENT = 100;
	/** Minding below this (Prejudice's degrees) is friendly. */
	static final float FRIENDLY_BELOW = 1.0F;
	/** Lines per mood: the common tongue, and the goblins' dialect. */
	static final int LINES = 10, GOBLIN_LINES = 8;
	// ================================================================================================================

	enum Mood {
		FRIENDLY("friendly", Speech.GOLD), WARY("wary", Speech.YELLOW), FIGHT("fight", Speech.RED);

		final String key;
		final int color;

		Mood(String key, int color) {
			this.key = key;
			this.color = color;
		}
	}

	static final String KEY = "talk.jaymoskingdoms.orc_seen.";

	private static final Map<UUID, Long> SPOKE = new HashMap<>();
	private static final Map<String, Long> PAIRS = new HashMap<>();

	private OrcSightings() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(OrcSightings::tickAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SPOKE.clear();
			PAIRS.clear();
		});
	}

	private static void tickAll(MinecraftServer server) {
		if (server.getTickCount() % TICK != 0) {
			return;
		}
		for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
			if (!p.isSpectator() && p.isAlive() && p.level() instanceof ServerLevel level) {
				look(level, p, level.getGameTime());
			}
		}
		if (PAIRS.size() > 2048) {
			long now = Factions.level(server).getGameTime();
			PAIRS.values().removeIf(t -> now - t >= PAIR_REST);
			SPOKE.values().removeIf(t -> now - t >= SPEAK_REST);
		}
	}

	/** {@code e} is an orc to the eye: an orc (villager, camp folk), or a half-orc player not hiding behind a full mask. */
	static boolean isOrc(LivingEntity e) {
		if (e instanceof Player p) {
			return GreenskinLife.halfOrc(p) && !Prejudice.masked(p) && !p.isSpectator();
		}
		return Orcs.isOrc(e) || Dialects.ORC.equals(Dialects.of(e));
	}

	/** {@code e} could remark on an orc: a grown, awake MCA villager or one of the folk (a guard, a worker), not an orc itself. */
	private static boolean speaker(LivingEntity e) {
		if (!e.isAlive() || e.isBaby() || e.isSleeping() || e instanceof IronGolem || Downed.isDowned(e) || isOrc(e)) {
			return false;
		}
		return e instanceof Folk || e instanceof Villager v && McaCompat.isMca(v);
	}

	/** One remark near {@code p}, at most: someone who sees an orc says what they think of it. */
	private static void look(ServerLevel level, ServerPlayer p, long now) {
		RandomSource random = level.getRandom();
		List<LivingEntity> orcs = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ORC_RANGE), OrcSightings::isOrc);
		if (orcs.isEmpty()) {
			return;
		}
		LivingEntity orc = orcs.get(random.nextInt(orcs.size()));
		List<PathfinderMob> near = level.getEntitiesOfClass(PathfinderMob.class, orc.getBoundingBox().inflate(SEE_RANGE),
			e -> e != orc && speaker(e) && e.hasLineOfSight(orc));
		if (near.isEmpty()) {
			return;
		}
		PathfinderMob who = near.get(random.nextInt(near.size()));
		Mood mood = mood(level, who, orc);
		Long spoke = SPOKE.get(who.getUUID());
		String pair = who.getUUID() + "|" + orc.getUUID();
		if (mood == Mood.FIGHT) {
			if (spoke != null && now - spoke < FIGHT_REST || random.nextFloat() >= FIGHT_CHANCE) {
				return;
			}
		} else if (spoke != null && now - spoke < SPEAK_REST || now - PAIRS.getOrDefault(pair, Long.MIN_VALUE / 2) < PAIR_REST
			|| random.nextFloat() >= CALM_CHANCE) {
			return;
		}
		SPOKE.put(who.getUUID(), now);
		PAIRS.put(pair, now);
		if (mood != Mood.FIGHT) {
			who.getLookControl().setLookAt(orc, 30.0F, 30.0F);
		}
		Speech.say(level, who, line(who, mood, random, orc.getName()), mood.color, HEARD);
	}

	/** How {@code who} takes the orc {@code orc}. */
	static Mood mood(ServerLevel level, PathfinderMob who, LivingEntity orc) {
		if (who.getTarget() == orc || orc instanceof Mob m && m.getTarget() == who
			|| who.getLastHurtByMob() == orc && who.tickCount - who.getLastHurtByMobTimestamp() < HURT_RECENT) {
			return Mood.FIGHT;
		}
		if (orc instanceof ServerPlayer sp) {
			return Bearings.fond(who, sp) || Prejudice.minds(who, sp) < FRIENDLY_BELOW ? Mood.FRIENDLY : Mood.WARY;
		}
		boolean haven = Havens.greenskin(level.getServer(), Havens.home(who));
		String tribe = Tribes.of(orc);
		if (tribe != null && !tribe.equals(Tribes.of(who)) && !haven) {
			// (a tribe's orc, not theirs: raiders, or neighbours who might be)
			return Mood.WARY;
		}
		float m = VillagerHeritage.eligible(who) ? Prejudice.minds(who, orc)
			: haven ? 0.0F : Peoples.minds(who, null, Heritage.Kind.ORC, Prejudice.edict(who));
		return m < FRIENDLY_BELOW ? Mood.FRIENDLY : Mood.WARY;
	}

	/** A line for {@code mood} in {@code who}'s voice: a goblin's dialect, else the common tongue. */
	private static Component line(LivingEntity who, Mood mood, RandomSource random, Component orcName) {
		String key = KEY + mood.key + ".";
		if (Objects.equals(Dialects.of(who), Dialects.GOBLIN)) {
			return Dialects.trBrood(Dialects.GOBLIN, key + random.nextInt(GOBLIN_LINES), orcName);
		}
		return Component.translatable(key + random.nextInt(LINES), orcName);
	}
}
