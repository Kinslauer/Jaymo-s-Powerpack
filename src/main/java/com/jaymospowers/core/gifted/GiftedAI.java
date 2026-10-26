package com.jaymospowers.core.gifted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.1) How a gifted villager ({@link Gifted}) fights with their power. Their own AI still walks them (a guard closes in,
 * a farmer runs); this only picks a move now and then, while they have a foe: the one they're set on, the one their brain
 * remembers, or whoever hit them last. Moves have their own cooldowns, and there's a gap between any two, longer for a
 * wary villager. A bold one opens with the strongest move that's ready; a steady one takes any that fits; a wary one gets
 * away first when a foe comes close.
 * <p>
 * Long use wears on a power as it does on a player's: after {@link #STRAIN_MIN} moves, each one has a small chance
 * ({@link #CORRUPT_CHANCE}) of corrupting it, which opens the kit's corrupted moves (a Wind villager's lightning). Kingdoms
 * reads that (a storm-taken Wind wakes the Stormborn in them).
 */
public final class GiftedAI {
	/** How often each gifted villager thinks about a move (ticks), and how often their passive runs. */
	static final int THINK_EVERY = 10;
	public static final int PASSIVE_EVERY = 20;
	/** How far off a foe can be and still be fought with the power (blocks). */
	static final double RANGE = 20.0;
	/** A foe this near is close (a wary villager gets away first). */
	static final double CLOSE = 4.0;
	/** Whoever hit them counts as their foe this long after (ticks). */
	static final int HURT_MEMORY = 200;
	/** The gap between any two moves (ticks): bold, steady, wary. */
	static final int[] GAP = {20, 30, 45};
	/** Moves used before long use can corrupt the power, and the chance each move after that does. */
	static final int STRAIN_MIN = 300;
	static final float CORRUPT_CHANCE = 0.004F;
	/** Players this near hear of a power gone wrong. */
	static final double TELL_RANGE = 24.0;
	/** Out of a fight, their everyday use of the power is looked at this often (ticks), at this chance. */
	public static final int EVERYDAY_EVERY = 200;
	static final float EVERYDAY_CHANCE = 0.5F;
	/** A ruler's ultimate: at most this often (ticks); a last-resort one only at or below this share of their health. */
	static final int ULTIMATE_COOLDOWN = 12000;
	public static final float LAST_RESORT = 0.3F;
	/** Players this near see the bars of a ruler's ultimate. */
	static final double FILM_WITNESSES = 48.0;
	/** Powers' own tag on anyone downed (they don't fight). */
	static final String DOWNED = "jaymospowers_downed";

	private static final Set<PathfinderMob> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Each villager's moves cooling down (move name to the game time it's ready), and when they may next use any. */
	private static final Map<PathfinderMob, Map<String, Long>> COOLDOWNS = new WeakHashMap<>();
	private static final Map<PathfinderMob, Long> NEXT = new WeakHashMap<>();
	/** When each ruler may next unleash their ultimate (game time). */
	private static final Map<PathfinderMob, Long> ULTIMATE_READY = new WeakHashMap<>();

	private GiftedAI() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && mob.hasAttached(Gifted.GIFT)) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			COOLDOWNS.clear();
			NEXT.clear();
			ULTIMATE_READY.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(GiftedAI::tick);
	}

	static void track(PathfinderMob mob) {
		if (!mob.level().isClientSide()) {
			LOADED.add(mob);
		}
	}

	private static void tick(MinecraftServer server) {
		if (LOADED.isEmpty()) {
			return;
		}
		for (PathfinderMob mob : new ArrayList<>(LOADED)) {
			if (mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				LOADED.remove(mob);
				continue;
			}
			Gifted.Gift gift = Gifted.of(mob);
			GiftKit kit = gift == null ? null : Gifted.kit(gift.power());
			if (kit == null) {
				LOADED.remove(mob);
				continue;
			}
			if (!mob.isAlive() || mob.isBaby() || mob.entityTags().contains(DOWNED)) {
				continue;
			}
			int phase = mob.getId() & 0xFF;
			if ((mob.tickCount + phase) % PASSIVE_EVERY == 0) {
				kit.passive(level, mob, gift);
			}
			if ((mob.tickCount + phase) % THINK_EVERY == 0) {
				think(level, mob, gift, kit);
			}
			if ((mob.tickCount + phase) % EVERYDAY_EVERY == 0 && foe(mob) == null && mob.getRandom().nextFloat() < EVERYDAY_CHANCE) {
				kit.everyday(level, mob, gift);
			}
		}
	}

	private static void think(ServerLevel level, PathfinderMob mob, Gifted.Gift gift, GiftKit kit) {
		long now = level.getGameTime();
		if (NEXT.getOrDefault(mob, 0L) > now) {
			return;
		}
		LivingEntity foe = foe(mob);
		if (foe == null) {
			return;
		}
		double distance = mob.distanceTo(foe);
		boolean seen = mob.getSensing().hasLineOfSight(foe);
		Map<String, Long> cooling = COOLDOWNS.computeIfAbsent(mob, m -> new java.util.HashMap<>());
		List<GiftKit.Move> ready = new ArrayList<>();
		for (GiftKit.Move move : kit.moves()) {
			if ((move.corruptOnly() && !gift.corrupted()) || cooling.getOrDefault(move.name(), 0L) > now || !move.fits(distance)) {
				continue;
			}
			if (move.kind() == GiftKit.Kind.STRIKE && !seen) {
				continue;
			}
			ready.add(move);
		}
		if (ready.isEmpty()) {
			return;
		}
		GiftKit.Move pick = pick(mob, gift.style(), ready, distance);
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		if (!pick.use().use(level, mob, foe, gift)) {
			return;
		}
		cooling.put(pick.name(), now + pick.cooldown());
		NEXT.put(mob, now + GAP[Math.clamp(gift.style(), 0, GAP.length - 1)]);
		Gifted.Gift strained = gift.strained();
		Gifted.set(mob, strained);
		if (!strained.corrupted() && strained.strain() >= STRAIN_MIN && mob.getRandom().nextFloat() < CORRUPT_CHANCE) {
			corrupt(mob);
		}
	}

	private static GiftKit.Move pick(PathfinderMob mob, int style, List<GiftKit.Move> ready, double distance) {
		if (style == Gifted.WARY && distance <= CLOSE) {
			for (GiftKit.Move move : ready) {
				if (move.kind() == GiftKit.Kind.ESCAPE) {
					return move;
				}
			}
		}
		if (style == Gifted.BOLD) {
			for (GiftKit.Move move : ready) {
				if (move.kind() != GiftKit.Kind.ESCAPE) {
					return move;
				}
			}
		}
		return ready.get(mob.getRandom().nextInt(ready.size()));
	}

	/** The foe {@code mob} is fighting just now, or null. */
	static @Nullable LivingEntity foe(PathfinderMob mob) {
		LivingEntity foe = mob.getTarget();
		if (!fightable(mob, foe)) {
			foe = null;
			Brain<?> brain = mob.getBrain();
			if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
				foe = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			}
		}
		if (!fightable(mob, foe)) {
			foe = mob.getLastHurtByMob();
			if (foe != null && mob.tickCount - mob.getLastHurtByMobTimestamp() > HURT_MEMORY) {
				foe = null;
			}
		}
		return fightable(mob, foe) ? foe : null;
	}

	private static boolean fightable(PathfinderMob mob, @Nullable LivingEntity foe) {
		if (foe == null || foe == mob || !foe.isAlive() || foe.isRemoved() || foe.level() != mob.level() || mob.distanceTo(foe) > RANGE) {
			return false;
		}
		return !(foe instanceof Player player) || !player.isCreative() && !player.isSpectator();
	}

	/** Corrupts {@code mob}'s gift now: the kit's word, a dark burst, and those near hear of it. */
	static boolean corrupt(PathfinderMob mob) {
		Gifted.Gift gift = Gifted.of(mob);
		if (gift == null || gift.corrupted() || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		Gifted.set(mob, gift.corrupt());
		GiftKit kit = Gifted.kit(gift.power());
		if (kit != null) {
			kit.corrupted(level, mob);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY(0.6), mob.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.NEUTRAL, 0.6F, 0.6F);
		for (ServerPlayer player : level.players()) {
			if (player.distanceTo(mob) <= TELL_RANGE) {
				player.sendSystemMessage(Component.translatable("gift.jaymospowers.corrupted", mob.getDisplayName()));
			}
		}
		return true;
	}

	/**
	 * A gifted ruler's ultimate on {@code foe} (Kingdoms calls it when their throne room is attacked): at most once in
	 * {@link #ULTIMATE_COOLDOWN}, a last-resort one only once they're badly hurt. Nearby players see the film's bars.
	 */
	static boolean unleash(PathfinderMob mob, LivingEntity foe) {
		Gifted.Gift gift = Gifted.of(mob);
		GiftKit kit = gift == null ? null : Gifted.kit(gift.power());
		GiftKit.Ultimate ultimate = kit == null ? null : kit.ultimate();
		if (ultimate == null || !(mob.level() instanceof ServerLevel level) || !mob.isAlive() || mob.entityTags().contains(DOWNED)
			|| !fightable(mob, foe)) {
			return false;
		}
		long now = level.getGameTime();
		if (ULTIMATE_READY.getOrDefault(mob, 0L) > now || ultimate.lastResort() && mob.getHealth() > mob.getMaxHealth() * LAST_RESORT) {
			return false;
		}
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		if (!ultimate.use().use(level, mob, foe, gift)) {
			return false;
		}
		ULTIMATE_READY.put(mob, now + ULTIMATE_COOLDOWN);
		com.jaymospowers.core.UltimateFilm.witnessed(mob, ultimate.shot(), ultimate.filmTicks(), foe.position(), foe, FILM_WITNESSES);
		return true;
	}

	/** Every move off cooldown for {@code mob} (for {@code /power gift}, to watch a move at once). */
	static void ready(PathfinderMob mob) {
		COOLDOWNS.remove(mob);
		NEXT.remove(mob);
		ULTIMATE_READY.remove(mob);
	}
}
