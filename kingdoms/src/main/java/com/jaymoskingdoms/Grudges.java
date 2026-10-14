package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.21) Villagers set each other up. When a quarrel ({@link Neighbours}) leaves someone hating another (regard at or
 * below {@link Neighbours#HATE}), they may mutter about it and look for someone to settle it: they get the gold mark and
 * walk up to a player ({@link Approaches}) with a {@link Quest#GRUDGE} quest, one of two:
 * <ul>
 * <li><b>a beating:</b> land so many blows on their enemy (an MCA villager remembers who hit them; the guards and golems
 * may see; a kill counts too, and is murder if it's seen);</li>
 * <li><b>a lure:</b> ask their enemy to follow you (MCA's Follow in their talk menu; without MCA, walk up to them) and
 * they come along ("you wanted to show me something?"); lead them to the
 * spot on the outskirts where the giver lies in wait, and the giver falls on them. Go too far ahead and they give up.</li>
 * </ul>
 * Either way the enemy runs off afterwards, and the two think even less of each other.
 */
final class Grudges {
	/** The chance a sour meeting that leaves a hater sets them looking for someone to settle it; then once a day at most each. */
	static final float BREW_CHANCE = 0.3F;
	/** Blows a beating takes; the chance a grudge is a beating (else a lure). */
	static final int BLOWS = 3;
	static final float BEAT_SHARE = 0.5F;
	/** What it pays: emeralds (and a few more), MCA hearts with the giver. */
	static final int EMERALDS = 6, EMERALDS_MORE = 7, HEARTS = 6;
	/** The lure: how near you have to come to the enemy to draw them along; how far behind they'll follow; how long; their pace. */
	static final double DRAW = 3.0, FOLLOW = 18.0;  // (DRAW: without MCA only)
	static final int LURE_TICKS = 20 * 60 * 4;
	static final float LURE_WALK = 0.6F;
	/** The hiding spot: this far from the seat, at a random bearing; how near the enemy must come to it, and the giver to it. */
	static final int SPOT_FROM = 26, SPOT_MORE = 10;
	static final double SPRUNG = 4.0, WAITING = 6.0;
	/** How often lures are thought about (ticks). */
	static final int EVERY = 5;
	/** Lines each (lang {@code neighbours.jaymoskingdoms.grudge|lure|lure.lost|ambush|mercy.N}); quest stories per kind. */
	static final int STORIES = 4;

	/** The enemies being led along: enemy → who's leading them, which quest, until when. */
	private record Lure(ServerPlayer player, String quest, UUID giver, long until) {
	}

	private static final Map<UUID, Lure> LURED = new HashMap<>();
	/** Givers waiting at their spot for a lure: giver → the spot. */
	private static final Map<UUID, BlockPos> WAITING_AT = new HashMap<>();
	/** When each hater last went looking (game day). */
	private static final Map<UUID, Long> BREWED = new HashMap<>();

	private Grudges() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Grudges::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, dealt, taken, blocked) -> {
			if (!blocked && taken > 0.0F) {
				blow(entity, source, false);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> blow(entity, source, true));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LURED.clear();
			WAITING_AT.clear();
			BREWED.clear();
		});
	}

	/** {@code v} is being led along, or lying in wait: their routines and meetings leave them be. */
	static boolean busy(Villager v) {
		return LURED.containsKey(v.getUUID()) || WAITING_AT.containsKey(v.getUUID());
	}

	// ---- a grudge comes to a head --------------------------------------------------------------------------------------

	/** After a sour meeting of {@code a} and {@code b}: either who hates the other may look for someone to settle it. */
	static void brew(ServerLevel level, Villager a, Villager b, int faction) {
		RandomSource random = level.getRandom();
		if (Neighbours.regard(a, b) > Neighbours.HATE || random.nextFloat() >= BREW_CHANCE) {
			return;
		}
		Villager hater = random.nextBoolean() ? a : b, enemy = hater == a ? b : a;
		Faction f = Factions.get(level.getServer(), faction);
		if (f != null) {
			offer(level, hater, enemy, f, random.nextFloat() < BEAT_SHARE, false);
		}
	}

	/** {@code hater} wants {@code enemy} dealt with: a quest on offer (gold mark), and a mutter for anyone near. False if not now. */
	static boolean offer(ServerLevel level, Villager hater, Villager enemy, Faction f, boolean beating, boolean forced) {
		long day = Quests.day(level);
		if (!forced && (hater.isBaby() || Quests.offering(hater) || BREWED.getOrDefault(hater.getUUID(), -1L) >= day)) {
			return false;
		}
		RandomSource random = level.getRandom();
		BREWED.put(hater.getUUID(), day);
		String job = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(hater.getVillagerData().profession().value()).getPath();
		Quest.Giver who = new Quest.Giver(hater.getUUID(), hater.getName().getString(), f.id(), false, job);
		Optional<BlockPos> spot = beating ? Optional.empty() : Optional.of(spot(level, f.seat(), random));
		Quest.Objective o = new Quest.Objective("grudge", beating ? BLOWS : 1, 0, spot, Optional.of(enemy.getUUID()), enemy.getName().getString(),
			f.name(), false);
		Quest.Reward r = new Quest.Reward(EMERALDS + random.nextInt(EMERALDS_MORE), List.of(), Optional.empty(), Optional.empty(), HEARTS, 0, 0);
		String template = "grudge." + (beating ? "beat" : "lure") + "." + random.nextInt(STORIES);
		Quests.offerNow(level, hater, new Quest(UUID.randomUUID().toString(), Quest.GRUDGE, template, who, o, r, 0L));
		Neighbours.say(level, hater, "grudge", Speech.YELLOW, enemy);
		return true;
	}

	/** Somewhere quiet on the outskirts, out from {@code seat}. */
	private static BlockPos spot(ServerLevel level, BlockPos seat, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2;
		int r = SPOT_FROM + random.nextInt(SPOT_MORE);
		BlockPos edge = seat.offset((int)(Math.cos(angle) * r), 0, (int)(Math.sin(angle) * r));
		return Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, edge), 3);
	}

	// ---- a beating ---------------------------------------------------------------------------------------------------

	/** {@code entity} was hit (or killed): a blow for each player whose grudge quest it was the enemy of. */
	private static void blow(LivingEntity entity, DamageSource source, boolean died) {
		if (!(entity instanceof Villager v) || !(source.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		for (Quest q : Quests.log(player)) {
			Quest.Objective o = q.objective();
			if (q.kind() != Quest.GRUDGE || q.counted() || !o.other().map(v.getUUID()::equals).orElse(false)) {
				continue;
			}
			if (o.pos().isPresent() && !died) {
				// (a lure is done by the ambush, not by your fists; but a dead enemy settles it all the same)
				continue;
			}
			Quest next = q.withProgress(died ? o.count() : o.progress() + 1);
			Quests.put(player, next);
			player.sendOverlayMessage(Quests.objective(level, player, next).copy().withStyle(next.counted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			if (next.counted() && !died) {
				// (enough: they beg off and run)
				Neighbours.say(level, v, "mercy", Speech.RED, player);
				Frights.scare(level, v, player.position());
				LivingEntity giver = giver(level, q);
				if (giver instanceof Villager g) {
					Neighbours.shift(g, v, -10);
				}
			}
			Quests.sync(player, true);
		}
	}

	private static @Nullable LivingEntity giver(ServerLevel level, Quest q) {
		return level.getEntity(q.giver().uuid()) instanceof LivingEntity e && e.isAlive() ? e : null;
	}

	// ---- a lure ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		// whom each player could draw along now
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level() != level || p.isSpectator()) {
				continue;
			}
			for (Quest q : Quests.log(p)) {
				Quest.Objective o = q.objective();
				if (q.kind() != Quest.GRUDGE || q.counted() || o.pos().isEmpty() || o.other().isEmpty() || LURED.containsKey(o.other().get())) {
					continue;
				}
				if (level.getEntity(o.other().get()) instanceof Villager enemy && enemy.isAlive() && drawn(enemy, p) && !enemy.isSleeping()
					&& !Neighbours.meeting(enemy) && !Frights.afraid(enemy.getUUID())) {
					LURED.put(enemy.getUUID(), new Lure(p, q.id(), q.giver().uuid(), now + LURE_TICKS));
					Neighbours.say(level, enemy, "lure", Speech.WHITE, p);
					Quests.put(p, q.withSpawned(Optional.empty()));
					Quests.sync(p, true);
				}
			}
		}
		WAITING_AT.clear();
		for (Map.Entry<UUID, Lure> e : new ArrayList<>(LURED.entrySet())) {
			if (!lead(level, e.getKey(), e.getValue(), now)) {
				LURED.remove(e.getKey());
			}
		}
	}

	/**
	 * {@code enemy} has been drawn along by {@code p}: asked to follow in their talk menu (MCA's Follow); without MCA, just
	 * walked up to.
	 */
	private static boolean drawn(Villager enemy, ServerPlayer p) {
		return McaCompat.LOADED ? p.getUUID().equals(McaCompat.following(enemy)) : enemy.distanceTo(p) <= DRAW;
	}

	/** One think of a lure: the enemy follows, the giver waits at the spot, and when both are there it's sprung. False once over. */
	private static boolean lead(ServerLevel level, UUID enemyId, Lure lure, long now) {
		ServerPlayer p = lure.player();
		Quest q = p.isRemoved() ? null : Quests.quest(p, lure.quest());
		if (q == null || q.counted() || !(level.getEntity(enemyId) instanceof Villager enemy) || !enemy.isAlive() || p.level() != level) {
			return false;
		}
		BlockPos spot = q.objective().pos().orElseThrow();
		boolean told = McaCompat.LOADED && !p.getUUID().equals(McaCompat.following(enemy));
		if (now > lure.until() || enemy.distanceTo(p) > FOLLOW || Frights.afraid(enemy.getUUID()) || enemy.isSleeping() || told) {
			// (left behind, told to stay, or they've had enough of the walk)
			Neighbours.say(level, enemy, "lure.lost", Speech.YELLOW, p);
			McaCompat.stopFollowing(enemy);
			unspawn(p, q);
			return false;
		}
		if (!McaCompat.LOADED) {
			BehaviorUtils.setWalkAndLookTargetMemories(enemy, p, LURE_WALK, 2);
		}
		LivingEntity giver = giver(level, q);
		if (giver instanceof Villager g && !Neighbours.meeting(g)) {
			// (the giver goes to the spot and keeps out of sight there)
			WAITING_AT.put(g.getUUID(), spot);
			if (!g.blockPosition().closerThan(spot, 2)) {
				BehaviorUtils.setWalkAndLookTargetMemories(g, spot, LURE_WALK, 1);
			} else {
				g.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				g.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(enemy, true));
			}
			if (enemy.position().closerThan(Vec3.atCenterOf(spot), SPRUNG) && g.position().closerThan(Vec3.atCenterOf(spot), WAITING)) {
				spring(level, p, q, g, enemy, now);
				return false;
			}
		}
		return true;
	}

	/** The giver falls on the enemy: done, and they hate each other the more for it. */
	private static void spring(ServerLevel level, ServerPlayer p, Quest q, Villager giver, Villager enemy, long now) {
		WAITING_AT.remove(giver.getUUID());
		McaCompat.stopFollowing(enemy);
		Neighbours.say(level, giver, "ambush", Speech.RED, enemy);
		Neighbours.ambush(level, q.giver().faction(), giver, enemy, now);
		Neighbours.shift(giver, enemy, -10);
		Quest done = q.withProgress(q.objective().count());
		Quests.put(p, done);
		p.sendOverlayMessage(Quests.objective(level, p, done).copy().withStyle(ChatFormatting.GREEN));
		Quests.sync(p, true);
	}

	/** The lure fell through: they can be drawn along again. */
	private static void unspawn(ServerPlayer p, Quest q) {
		Quest.Objective o = q.objective();
		Quests.put(p, q.withObjective(new Quest.Objective(o.target(), o.count(), o.progress(), o.pos(), o.other(), o.otherName(), o.place(), false)));
		Quests.sync(p, true);
	}

	/** (ops) {@code a} starts a grudge against {@code b}, as a beating or a lure, on offer now. */
	static boolean force(ServerLevel level, Villager a, Villager b, boolean beating) {
		Faction f = Factions.around(level.getServer(), level, a.blockPosition(), Villages.VILLAGE_RADIUS);
		if (f == null) {
			return false;
		}
		Neighbours.shift(a, b, Neighbours.HATE - Neighbours.regard(a, b));
		return offer(level, a, b, f, beating, true);
	}
}
