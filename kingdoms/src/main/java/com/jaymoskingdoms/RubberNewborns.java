package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Newborn Rubber-folk (Andy, 2026-11-24, picking "Home ground + newborns"; Lore.md, "Where the Rubber-folk come from"): the
 * Mad Tinkerer's vats under the Candy Cavity still turn them out. Now and then, while a player is down in Alex's Candy
 * Cavity ({@link RubberFolk#CANDY_CAVITY}), a newborn Rubber-folk MCA villager stands up on the cave floor some way off
 * ({@link #COME_MIN} to {@link #COME_MAX} blocks). It has nowhere to be yet: it makes for the seat of the nearest
 * settlement ({@link Captives#nearestSettlement}), saved as {@link #BOUND_FOR}. Out of every player's sight it climbs up
 * to the surface above, and from there it walks a leg at a time ({@link Construction#leg}) until it is within
 * {@link #ARRIVE} of the seat, where the town's own life takes it over. With no settlement in reach it just stays.
 * <p>
 * Andy: "you should be able to escort the rubberfolk to a village when you meet them after born", then "you should have
 * to talk to it and convince it or answer its request. it should all be done through talk". A newborn that sees a player
 * ({@link #SEES}) comes over and asks for help, out loud. "{@value #ANSWER}" in MCA's talk menu opens its request on
 * the talk screen: take it with you ({@link #ESCORT}: it follows you), point it the way (it goes on alone) or not now.
 * Talked to again on the way, you can send it on alone. Bring it into any settlement ({@link Theft#lawAt}) and it stays
 * there, glad of you ({@link #THANKS} MCA hearts); leave it more than {@link #LOST_RANGE} behind for {@link #LOST_TICKS}
 * and it sets off alone again.
 */
public final class RubberNewborns {
	// ---- tuning ----
	/** How often (ticks) the cavity may send one up for each player down there, and the chance each time (about one an hour). */
	static final int COME_EVERY = 1200;
	static final float COME_CHANCE = 1.0F / 60.0F;
	/** Blocks from the player it stands up, and the most newborns near one player at a time. */
	static final int COME_MIN = 16, COME_MAX = 32, MAX_NEAR = 1;
	static final double NEAR = 96.0;
	/** How often (ticks) a newborn on its way picks its next leg, its walking speed, and how near the seat counts as there. */
	static final int WALK_EVERY = 40;
	static final float WALK_SPEED = 0.6F;
	static final double ARRIVE = 24.0;
	/** It only climbs to the surface with no player within this (blocks), so nobody sees it pass through rock. */
	static final double UNSEEN = 48.0;
	static final int TRIES = 16;
	/** Escorting: how near it keeps, its speed, how far behind counts as lost and for how long, and the hearts it gives you. */
	static final double FOLLOW_CLOSE = 3.0, LOST_RANGE = 48.0;
	static final float FOLLOW_SPEED = 0.7F;
	static final long LOST_TICKS = 2400L;
	static final int THANKS = 25;
	/** Pointed the way instead, it still thinks well of you for that much. */
	static final int POINTED = 5;
	/** It comes over to a player it sees within this (blocks), once per player. */
	static final double SEES = 12.0;
	/** The MCA talk-menu answer (main.json), shown only for a newborn. */
	public static final String ANSWER = "jaymoskingdoms_newborn";
	/** The players each newborn has already come over to (not saved: after a restart it may ask again). */
	private static final Map<UUID, Set<UUID>> ASKED = new HashMap<>();
	/** The players who have answered each newborn's request (not saved). */
	private static final Map<UUID, Set<UUID>> ANSWERED = new HashMap<>();

	/** Where a newborn is making for: the seat of the settlement nearest where it stood up. Saved. */
	static final AttachmentType<BlockPos> BOUND_FOR = AttachmentRegistry.create(JaymosKingdoms.id("rubber_newborn_bound_for"),
		builder -> builder.persistent(BlockPos.CODEC));
	/** The player escorting a newborn, if one is. Saved. */
	static final AttachmentType<UUID> ESCORT = AttachmentRegistry.create(JaymosKingdoms.id("rubber_newborn_escort"),
		builder -> builder.persistent(UUIDUtil.CODEC));
	/** Since when (game time) each escorted newborn has been left too far behind. */
	private static final Map<UUID, Long> LOST = new HashMap<>();

	private RubberNewborns() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RubberNewborns::tick);
	}

	/** Whether {@code v} is a newborn with a request to make (MCA's talk menu). */
	public static boolean canAsk(Villager v) {
		return newborn(v);
	}

	/** Its request on the talk screen: on the way with you already, or still lost. */
	public static void open(ServerPlayer player, Villager v) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		boolean mine = player.getUUID().equals(v.getAttached(ESCORT));
		if (mine) {
			ZFighterFolk.ask(player, v, Component.translatable("talk.jaymoskingdoms.newborn.on_the_way"),
				List.of(Component.translatable("talk.jaymoskingdoms.newborn.stay_close"), Component.translatable("talk.jaymoskingdoms.newborn.go_alone")),
				List.of(true, true), pick -> {
					if (pick == 1 && newborn(v)) {
						v.removeAttached(ESCORT);
						LOST.remove(v.getUUID());
						level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.2F);
					}
				});
			return;
		}
		ZFighterFolk.ask(player, v, Component.translatable("talk.jaymoskingdoms.newborn.request"),
			List.of(Component.translatable("talk.jaymoskingdoms.newborn.take"), Component.translatable("talk.jaymoskingdoms.newborn.point"),
				Component.translatable("talk.jaymoskingdoms.newborn.not_now")),
			List.of(true, true, true), pick -> {
				if (!newborn(v) || pick < 0) {
					return;
				}
				boolean first = !ANSWERED.getOrDefault(v.getUUID(), Set.of()).contains(player.getUUID());
				answered(v, player);
				if (pick == 2) {
					return;
				}
				if (pick == 0) {
					v.setAttached(ESCORT, player.getUUID());
					LOST.remove(v.getUUID());
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.2F);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.6, v.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
				} else {
					v.removeAttached(ESCORT);
					if (first) {
						McaCompat.hearts(v, player, POINTED);
					}
				}
			});
	}

	/**
	 * A newborn that sees a player who hasn't answered it yet comes over, calls out for help (once), and waits on them;
	 * true while it waits.
	 */
	private static boolean callOut(ServerLevel level, Villager v) {
		ServerPlayer near = level.getNearestPlayer(v, SEES) instanceof ServerPlayer p && !p.isSpectator() ? p : null;
		if (near == null || ANSWERED.getOrDefault(v.getUUID(), Set.of()).contains(near.getUUID())) {
			return false;
		}
		if (ASKED.computeIfAbsent(v.getUUID(), u -> new HashSet<>()).add(near.getUUID())) {
			Speech.say(level, v, Component.translatable("speech.jaymoskingdoms.newborn.call"), Speech.YELLOW, SEES + 4.0);
		}
		if (near.distanceTo(v) > FOLLOW_CLOSE) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(near, false), FOLLOW_SPEED, 2);
		}
		return true;
	}

	private static void answered(Villager v, ServerPlayer player) {
		ANSWERED.computeIfAbsent(v.getUUID(), u -> new HashSet<>()).add(player.getUUID());
	}

	private static void forget(Villager v) {
		ASKED.remove(v.getUUID());
		ANSWERED.remove(v.getUUID());
		LOST.remove(v.getUUID());
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % WALK_EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR), RubberNewborns::newborn)) {
				walk(level, v);
			}
			if (now % COME_EVERY == 0 && !player.isSpectator() && !player.isCreative()
				&& level.getBiome(player.blockPosition()).is(RubberFolk.CANDY_CAVITY) && random.nextFloat() < COME_CHANCE
				&& level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR), RubberNewborns::newborn).size() < MAX_NEAR) {
				come(level, player, random);
			}
		}
	}

	static boolean newborn(Villager v) {
		return v.hasAttached(BOUND_FOR) && v.isAlive();
	}

	/** One stands up on the cave floor some way off, and sets out for the nearest settlement. */
	private static void come(ServerLevel level, ServerPlayer player, RandomSource random) {
		Faction home = Captives.nearestSettlement(level.getServer(), player.blockPosition());
		if (home == null) {
			return;
		}
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = COME_MIN + random.nextInt(COME_MAX - COME_MIN + 1);
			BlockPos near = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(near) || !level.getBiome(near).is(RubberFolk.CANDY_CAVITY)) {
				continue;
			}
			BlockPos at = Construction.standable(level, near, 3);
			if (at.getY() > player.getY() + 8 || !level.getBiome(at).is(RubberFolk.CANDY_CAVITY)) {
				// (found only the surface: not down in the cavity)
				continue;
			}
			Vec3 pos = Vec3.atBottomCenterOf(at);
			Villager v = McaCompat.newVillager(level, pos);
			if (v == null) {
				return;
			}
			v.snapTo(pos.x, pos.y, pos.z, random.nextFloat() * 360.0F, 0.0F);
			// (set up as a fresh spawn would be: MCA rolls their genes and skin here)
			v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			v.setPersistenceRequired();
			level.addFreshEntity(v);
			VillagerHeritage.born(v, Heritage.Kind.RUBBERFOLK, null);
			v.setAttached(BOUND_FOR, home.seat());
			JaymosKingdoms.LOGGER.debug("a newborn Rubber-folk stood up in the Candy Cavity at {}, bound for {}", at, home.name());
			return;
		}
	}

	/** A newborn on its way: up out of the caves unseen, then a leg at a time to the seat; there, it's the town's. */
	private static void walk(ServerLevel level, Villager v) {
		if (v.hasAttached(ESCORT) && led(level, v)) {
			return;
		}
		if (callOut(level, v)) {
			// (it waits on whoever it called out to, till they answer it)
			return;
		}
		BlockPos seat = v.getAttached(BOUND_FOR);
		double dx = seat.getX() + 0.5 - v.getX(), dz = seat.getZ() + 0.5 - v.getZ();
		if (dx * dx + dz * dz <= ARRIVE * ARRIVE) {
			v.removeAttached(BOUND_FOR);
			forget(v);
			return;
		}
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, v.blockPosition());
		if (v.getY() < surface.getY() - 4) {
			if (level.getNearestPlayer(v, UNSEEN) == null) {
				BlockPos up = Construction.standable(level, surface, 4);
				v.getNavigation().stop();
				v.teleportTo(up.getX() + 0.5, up.getY(), up.getZ() + 0.5);
			}
			return;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(v, Construction.leg(level, v, seat), WALK_SPEED, 1);
	}

	/** An escorted newborn keeps up with its player; in a settlement it stays. False once it has been left behind too long. */
	private static boolean led(ServerLevel level, Villager v) {
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(v.getAttached(ESCORT));
		if (p != null && p.level() == level && Theft.lawAt(level, v.blockPosition()) != null) {
			v.removeAttached(ESCORT);
			v.removeAttached(BOUND_FOR);
			forget(v);
			McaCompat.hearts(v, p, THANKS);
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.2F);
			level.sendParticles(ParticleTypes.HEART, v.getX(), v.getY() + 1.8, v.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
			return true;
		}
		if (p == null || p.level() != level || p.distanceTo(v) > LOST_RANGE) {
			long now = level.getGameTime();
			if (now - LOST.computeIfAbsent(v.getUUID(), u -> now) >= LOST_TICKS) {
				v.removeAttached(ESCORT);
				LOST.remove(v.getUUID());
				return false;
			}
			return true;
		}
		LOST.remove(v.getUUID());
		if (p.distanceTo(v) > FOLLOW_CLOSE) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(p, false), FOLLOW_SPEED, 2);
		}
		return true;
	}
}
