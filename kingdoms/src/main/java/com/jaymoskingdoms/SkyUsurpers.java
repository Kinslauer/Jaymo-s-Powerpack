package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The sky castle can be seized (Andy, 2026-11-12: "there can be travelers with specific temperaments that make them vie for
 * power"). Now and then ({@link #BID_CHANCE} a look, no oftener than {@link #BID_GAP} ticks a castle) a traveller who is
 * proud, greedy, hotheaded or spiteful ({@link #HUNGRY}) climbs to a held castle with {@link #FOLLOWERS} sellswords, says
 * what they've come for, and goes for the lord, the sellswords for the wardens; the wardens fight back. If the lord goes
 * down or dies while the usurper still stands, the usurper takes the seat ({@link SkyCourt#LORD_TAG}), the sellswords
 * become the new lord's wardens and the old wardens put down their arms. If the usurper goes down first, the bid fails and
 * the sellswords scatter. A player who is there can side with either: anyone who strikes the castle's people is the
 * wardens' foe, as before. A bid with nobody left to finish it ends after {@link #BID_TICKS}.
 */
public final class SkyUsurpers {
	/** On a usurper, and on their sellswords. */
	public static final String USURPER_TAG = "jaymoskingdoms.sky_usurper", SELLSWORD_TAG = "jaymoskingdoms.sky_sellsword";
	/** The chance at each of the court's looks that a held castle draws a usurper, and the least time between two bids (ticks). */
	static final float BID_CHANCE = 0.03F;
	static final long BID_GAP = 72000L, BID_TICKS = 6000L;
	/** How many sellswords a usurper brings, and how far outside the walls they arrive (blocks). */
	static final int FOLLOWERS = 2, ARRIVE_OUT = 6;
	/** The temperaments that reach for a throne. */
	static final List<Temperaments.Temperament> HUNGRY = List.of(Temperaments.Temperament.PROUD, Temperaments.Temperament.GREEDY,
		Temperaments.Temperament.HOTHEADED, Temperaments.Temperament.SPITEFUL);
	/** How many lines of each kind there are in the language file. */
	static final int CLAIM_LINES = 3, ANSWER_LINES = 3, TAKEN_LINES = 2, FAILED_LINES = 2;

	/** A bid under way: who, with whom, against which castle (its keep), since when. */
	private record Bid(UUID usurper, List<UUID> sellswords, long since) {
	}

	/** By castle (the keep's packed position): the bid under way, and when the last one began. */
	private static final Map<Long, Bid> BIDS = new HashMap<>();
	private static final Map<Long, Long> LAST = new HashMap<>();

	private SkyUsurpers() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BIDS.clear();
			LAST.clear();
		});
	}

	/** Whether {@code v} came with a bid for a castle (the court never makes them a warden or a lord by itself). */
	static boolean party(Villager v) {
		return v.entityTags().contains(USURPER_TAG) || v.entityTags().contains(SELLSWORD_TAG);
	}

	/** Whether a bid for the castle in {@code box} is under way. */
	static boolean underway(BoundingBox box) {
		return BIDS.containsKey(box.getCenter().asLong());
	}

	/** From {@link SkyCourt}'s look at one held castle: a bid carried on, or a new one begun. */
	static void watch(ServerLevel level, BoundingBox box, @Nullable Villager lord, List<Villager> people) {
		long key = box.getCenter().asLong();
		long now = level.getGameTime();
		Bid bid = BIDS.get(key);
		if (bid != null) {
			carryOn(level, key, bid, lord, people, now);
			return;
		}
		if (lord == null || now - LAST.getOrDefault(key, -BID_GAP) < BID_GAP || level.getRandom().nextFloat() >= BID_CHANCE) {
			return;
		}
		begin(level, box, key, lord, now);
	}

	private static void begin(ServerLevel level, BoundingBox box, long key, Villager lord, long now) {
		RandomSource random = level.getRandom();
		// they come up over the wall's side away from the hall: a spot just outside the walls
		BlockPos edge = new BlockPos(random.nextBoolean() ? box.minX() - ARRIVE_OUT : box.maxX() + ARRIVE_OUT, box.minY() + 1,
			random.nextBoolean() ? box.minZ() : box.maxZ());
		BlockPos at = Construction.standable(level, edge, 6);
		if (at == null || !level.isPositionEntityTicking(at)) {
			return;
		}
		VillagerProfession nobody = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value();
		Villager usurper = McaCompat.visitor(level, Vec3.atBottomCenterOf(at), nobody, false);
		if (usurper == null) {
			return;
		}
		usurper.addTag(USURPER_TAG);
		usurper.setPersistenceRequired();
		usurper.setAttached(Temperaments.TEMPERAMENT, HUNGRY.get(random.nextInt(HUNGRY.size())).key());
		McaBridge.toGuard(usurper);
		List<UUID> sellswords = new ArrayList<>();
		for (int i = 0; i < FOLLOWERS; i++) {
			Villager sword = McaBridge.spawnGuard(level, Vec3.atBottomCenterOf(at).add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1));
			if (sword != null) {
				sword.addTag(SELLSWORD_TAG);
				sword.setPersistenceRequired();
				sellswords.add(sword.getUUID());
			}
		}
		BIDS.put(key, new Bid(usurper.getUUID(), sellswords, now));
		LAST.put(key, now);
		Speech.say(level, usurper, line("claim", CLAIM_LINES, random), Speech.RED, 32.0);
		Speech.say(level, lord, line("answer", ANSWER_LINES, random), Speech.GOLD, 32.0);
		JaymosKingdoms.LOGGER.info("A usurper comes for the sky castle at {}", box.getCenter());
	}

	private static void carryOn(ServerLevel level, long key, Bid bid, @Nullable Villager lord, List<Villager> people, long now) {
		Villager usurper = level.getEntity(bid.usurper()) instanceof Villager v && v.isAlive() ? v : null;
		RandomSource random = level.getRandom();
		if (usurper == null || Downed.isDowned(usurper)) {
			// the bid fails: the sellswords scatter
			if (usurper != null) {
				Speech.say(level, usurper, line("failed", FAILED_LINES, random), Speech.WHITE, 24.0);
				usurper.removeTag(USURPER_TAG);
			}
			for (UUID id : bid.sellswords()) {
				if (level.getEntity(id) instanceof Villager sword) {
					sword.removeTag(SELLSWORD_TAG);
					sword.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
				}
			}
			BIDS.remove(key);
			return;
		}
		if (lord == null || !lord.isAlive() || Downed.isDowned(lord)) {
			// the seat is taken
			if (lord != null) {
				lord.removeTag(SkyCourt.LORD_TAG);
			}
			for (Villager v : people) {
				if (v.entityTags().contains(SkyCourt.WARDEN_TAG)) {
					v.removeTag(SkyCourt.WARDEN_TAG);
					v.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
				}
			}
			usurper.removeTag(USURPER_TAG);
			usurper.addTag(SkyCourt.LORD_TAG);
			usurper.setHomeTo(usurper.blockPosition(), SkyCourt.LORD_ROAM);
			McaBridge.dress(usurper);
			for (UUID id : bid.sellswords()) {
				if (level.getEntity(id) instanceof Villager sword && sword.isAlive()) {
					sword.removeTag(SELLSWORD_TAG);
					sword.addTag(SkyCourt.WARDEN_TAG);
					sword.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
				}
			}
			Speech.say(level, usurper, line("taken", TAKEN_LINES, random), Speech.GOLD, 48.0);
			JaymosKingdoms.LOGGER.info("The sky castle at {} has a new lord by force: {}", BlockPos.of(key), usurper.getName().getString());
			BIDS.remove(key);
			return;
		}
		if (now - bid.since() > BID_TICKS) {
			BIDS.remove(key);
			return;
		}
		// the fight goes on: the usurper at the lord, the sellswords at the wardens (or the lord), the wardens at them
		Rulers.charge(usurper, lord);
		List<Villager> wardens = new ArrayList<>();
		for (Villager v : people) {
			if (v.entityTags().contains(SkyCourt.WARDEN_TAG) && v.isAlive() && !Downed.isDowned(v)) {
				wardens.add(v);
			}
		}
		for (UUID id : bid.sellswords()) {
			if (level.getEntity(id) instanceof Villager sword && sword.isAlive() && !Downed.isDowned(sword)) {
				Rulers.charge(sword, wardens.isEmpty() ? lord : nearest(sword, wardens));
			}
		}
		for (Villager warden : wardens) {
			Rulers.charge(warden, usurper);
		}
	}

	private static LivingEntity nearest(Entity from, List<Villager> among) {
		Villager best = among.get(0);
		for (Villager v : among) {
			if (v.distanceToSqr(from) < best.distanceToSqr(from)) {
				best = v;
			}
		}
		return best;
	}

	private static Component line(String kind, int count, RandomSource random) {
		return Component.translatable("talk.jaymoskingdoms.sky_usurper." + kind + "." + (1 + random.nextInt(count)));
	}
}
