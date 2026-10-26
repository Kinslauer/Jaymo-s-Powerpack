package com.jaymospowers.core.gifted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.3) How a gifted villager ({@link Gifted}) fights with their power. Their own AI still walks them (a guard closes in,
 * a farmer runs); this only picks a move now and then, while they have a foe: the one they're set on, the one their brain
 * remembers, or whoever hit them last. Moves have their own cooldowns, and there's a gap between any two, longer for a
 * wary villager. A bold one opens with the strongest move that's ready; a steady one takes any that fits; a wary one gets
 * away first when a foe comes close.
 * <p>
 * Long use wears on a power as it does on a player's: after {@link #STRAIN_MIN} moves, each one has a small chance
 * ({@link #CORRUPT_CHANCE}) of corrupting it, which opens the kit's corrupted moves (a Wind villager's lightning). Kingdoms
 * reads that (a storm-taken Wind wakes the Stormborn in them).
 * <p>
 * (0.17.9) <b>Masters</b> ({@link Gifted#MASTER}, Andy: "not all the villagers are weaker"): their moves cool down in
 * {@link #MASTER_COOLDOWN} of the time, with a shorter gap; they reach further; their signature move (their own way with the
 * power) comes first when it's ready and lands twice ({@link #ECHO_DELAY}); long use wears less on them; and badly hurt
 * ({@link #MASTER_ULTIMATE_AT}) they unleash their power's ultimate in any fight, ruler or not.
 * <p>
 * (0.17.9) <b>Hidden gifts</b>: most gifted keep it to a minimum and hide it from those close to them
 * ({@link Gifted#closeOnes}): in a fight they hold back while family or friends can see, until they're hurt
 * ({@link #HIDE_UNTIL}), and use it at twice the gap; their everyday use is rarer and only where none of their own, and no
 * player, sees. The {@link Gifted#OPEN} don't care who sees: their everyday use comes twice as often. A player who sees a
 * hidden gift at work learns of it ({@link Gifted#REVEALED}).
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
	/**
	 * A player who kills a gifted villager: their power's corruption grows this much (out of {@code Fate.MAX_CORRUPTION}),
	 * more if the gift was corrupted, and their mastery this many points (an ability is 60); or this much experience.
	 */
	static final int SLAIN_CORRUPTION = 15, SLAIN_CORRUPTION_TAKEN = 25, SLAIN_MASTERY = 30, SLAIN_XP = 60;
	/**
	 * Masters: their cooldowns and gaps are this share, their reach this far, their signature's echo this many ticks after,
	 * long use corrupts them at this share of the chance, and at or below this share of their health they unleash their
	 * ultimate in any fight.
	 */
	static final float MASTER_COOLDOWN = 0.5F, MASTER_CORRUPT = 0.33F, MASTER_ULTIMATE_AT = 0.5F;
	static final double MASTER_RANGE = 28.0;
	static final int ECHO_DELAY = 8;
	/** Hidden gifts: close ones this near (in sight) keep them from it; they let go at or below this share of their health. */
	static final double WATCH_RANGE = 16.0;
	static final float HIDE_UNTIL = 0.5F;
	/** A hidden gift's gap between moves is this many times as long, and its everyday use this rare. */
	static final int HIDDEN_GAP = 2;
	static final float HIDDEN_EVERYDAY_CHANCE = 0.25F;
	/** Powers' own tag on anyone downed (they don't fight). */
	static final String DOWNED = "jaymospowers_downed";

	private static final Set<PathfinderMob> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Each villager's moves cooling down (move name to the game time it's ready), and when they may next use any. */
	private static final Map<PathfinderMob, Map<String, Long>> COOLDOWNS = new WeakHashMap<>();
	private static final Map<PathfinderMob, Long> NEXT = new WeakHashMap<>();
	/** When each ruler may next unleash their ultimate (game time). */
	private static final Map<PathfinderMob, Long> ULTIMATE_READY = new WeakHashMap<>();
	/** A master's signature move, to land again: the move, on whom, and when (game time). */
	private record Echo(GiftKit.Move move, LivingEntity foe, long at) {
	}

	private static final Map<PathfinderMob, Echo> ECHOES = new WeakHashMap<>();
	/** Who a gifted villager hides their gift from ({@link Gifted#closeOnes}); any villager or player until Kingdoms says. */
	static BiPredicate<Entity, Entity> closeOnes = (gifted, other) -> other instanceof Player || other instanceof AbstractVillager;
	/** Told of each player who sees a gift at work ({@link Gifted#witnesses}); no one until Kingdoms says. */
	static BiConsumer<Entity, Entity> witnesses = (player, gifted) -> {
	};

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
			ECHOES.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(GiftedAI::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && Gifted.of(entity) != null) {
				slain(killer, Gifted.of(entity));
			}
		});
	}

	/**
	 * {@code killer} slew a gifted villager: something of their power seeps into them. Andy: "It should corrupt you, but
	 * also boost your mastery gain or xp." Their own power's corruption fills ({@link #SLAIN_CORRUPTION}, more for a
	 * corrupted gift) and their mastery leaps ({@link #SLAIN_MASTERY}); with nothing left to learn, or no power, it's
	 * experience instead.
	 */
	private static void slain(ServerPlayer killer, Gifted.Gift gift) {
		com.jaymospowers.core.Fate.addCorruption(killer, gift.corrupted() ? SLAIN_CORRUPTION_TAKEN : SLAIN_CORRUPTION);
		if (!com.jaymospowers.core.Mastery.grant(killer, SLAIN_MASTERY) && !killer.isCreative()) {
			killer.giveExperiencePoints(SLAIN_XP);
		}
		ServerLevel level = killer.level();
		level.sendParticles(ParticleTypes.SQUID_INK, killer.getX(), killer.getY(0.6), killer.getZ(), 16, 0.3, 0.5, 0.3, 0.03);
		level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		killer.sendSystemMessage(Component.translatable("gift.jaymospowers.slain"));
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
			kit.tick(level, mob, gift);
			int phase = mob.getId() & 0xFF;
			if ((mob.tickCount + phase) % PASSIVE_EVERY == 0) {
				kit.passive(level, mob, gift);
			}
			echo(level, mob, gift);
			if ((mob.tickCount + phase) % THINK_EVERY == 0) {
				think(level, mob, gift, kit);
			}
			int everyday = gift.open() ? EVERYDAY_EVERY / 2 : EVERYDAY_EVERY;
			if ((mob.tickCount + phase) % everyday == 0 && mob.getRandom().nextFloat() < everydayChance(gift) && foe(mob) == null
				&& (gift.open() || !watched(level, mob, null) && !seenByPlayer(level, mob))) {
				kit.everyday(level, mob, gift);
				if (gift.open()) {
					Gifted.set(mob, seen(level, mob, gift));
				}
			}
		}
	}

	private static float everydayChance(Gifted.Gift gift) {
		return gift.open() ? 1.0F : HIDDEN_EVERYDAY_CHANCE;
	}

	/** Whether any of {@code mob}'s close ones (but {@code foe}) are near and can see them. */
	static boolean watched(ServerLevel level, PathfinderMob mob, @Nullable LivingEntity foe) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(WATCH_RANGE),
			e -> e != mob && e != foe && e.isAlive() && !e.isSpectator())) {
			if (closeOnes.test(mob, e) && e.hasLineOfSight(mob)) {
				return true;
			}
		}
		return false;
	}

	private static boolean seenByPlayer(ServerLevel level, PathfinderMob mob) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceTo(mob) <= WATCH_RANGE && player.hasLineOfSight(mob)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * {@code mob}'s gift at work: every player near enough to see it is told of it ({@link Gifted#witnesses}: Kingdoms
	 * lets them ask about it), and a hidden gift a player has seen is known now ({@link Gifted#REVEALED}).
	 */
	static Gifted.Gift seen(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		boolean any = false;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceTo(mob) <= WATCH_RANGE && player.hasLineOfSight(mob)) {
				any = true;
				witnesses.accept(player, mob);
			}
		}
		return !any || gift.revealed() ? gift : gift.withWays(gift.ways() | Gifted.REVEALED);
	}

	private static void echo(ServerLevel level, PathfinderMob mob, Gifted.Gift gift) {
		Echo echo = ECHOES.get(mob);
		if (echo == null || echo.at > level.getGameTime()) {
			return;
		}
		ECHOES.remove(mob);
		if (fightable(mob, echo.foe)) {
			mob.getLookControl().setLookAt(echo.foe, 30.0F, 30.0F);
			if (echo.move.use().use(level, mob, echo.foe, gift)) {
				flourish(level, mob);
				Gifted.set(mob, seen(level, mob, gift));
			}
		}
	}

	/** A master's mark on their signature: a glint of light round them. */
	private static void flourish(ServerLevel level, PathfinderMob mob) {
		level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY(0.6), mob.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
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
		boolean hurt = mob.getHealth() <= mob.getMaxHealth() * HIDE_UNTIL;
		if (!gift.open() && !hurt && watched(level, mob, foe)) {
			return; // not in front of their own, not yet
		}
		if (gift.master() && mob.getHealth() <= mob.getMaxHealth() * MASTER_ULTIMATE_AT && unleash(mob, foe)) {
			NEXT.put(mob, now + gap(gift));
			return;
		}
		double distance = mob.distanceTo(foe);
		if (!gift.master() && distance > RANGE) {
			return;
		}
		boolean seen = mob.getSensing().hasLineOfSight(foe);
		Map<String, Long> cooling = COOLDOWNS.computeIfAbsent(mob, m -> new java.util.HashMap<>());
		List<GiftKit.Move> moves = kit.moves();
		GiftKit.Move signature = gift.master() && gift.signature() < moves.size() ? moves.get(gift.signature()) : null;
		List<GiftKit.Move> ready = new ArrayList<>();
		for (GiftKit.Move move : moves) {
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
		GiftKit.Move pick = signature != null && ready.contains(signature) ? signature : pick(mob, gift.style(), ready, distance);
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		if (!pick.use().use(level, mob, foe, gift)) {
			return;
		}
		cooling.put(pick.name(), now + (gift.master() ? (long)(pick.cooldown() * MASTER_COOLDOWN) : pick.cooldown()));
		NEXT.put(mob, now + gap(gift));
		if (pick == signature) {
			flourish(level, mob);
			if (pick.kind() == GiftKit.Kind.STRIKE || pick.kind() == GiftKit.Kind.CLOSE) {
				ECHOES.put(mob, new Echo(pick, foe, now + ECHO_DELAY));
			}
		}
		Gifted.Gift strained = seen(level, mob, gift.strained());
		Gifted.set(mob, strained);
		float chance = gift.master() ? CORRUPT_CHANCE * MASTER_CORRUPT : CORRUPT_CHANCE;
		if (!strained.corrupted() && strained.strain() >= STRAIN_MIN && mob.getRandom().nextFloat() < chance) {
			corrupt(mob);
		}
	}

	/** The gap after a move: by how they fight, shorter for a master, longer for a hidden gift. */
	private static int gap(Gifted.Gift gift) {
		int gap = GAP[Math.clamp(gift.style(), 0, GAP.length - 1)];
		if (gift.master()) {
			gap = (int)(gap * MASTER_COOLDOWN);
		}
		return gift.open() ? gap : gap * HIDDEN_GAP;
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
		if (foe == null || foe == mob || !foe.isAlive() || foe.isRemoved() || foe.level() != mob.level() || mob.distanceTo(foe) > MASTER_RANGE) {
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
		Gifted.set(mob, seen(level, mob, gift));
		ULTIMATE_READY.put(mob, now + ULTIMATE_COOLDOWN);
		com.jaymospowers.core.UltimateFilm.witnessed(mob, ultimate.shot(), ultimate.filmTicks(), foe.position(), foe, FILM_WITNESSES);
		return true;
	}

	/** Every move off cooldown for {@code mob} (for {@code /power gift}, to watch a move at once). */
	static void ready(PathfinderMob mob) {
		COOLDOWNS.remove(mob);
		NEXT.remove(mob);
		ULTIMATE_READY.remove(mob);
		ECHOES.remove(mob);
	}
}
