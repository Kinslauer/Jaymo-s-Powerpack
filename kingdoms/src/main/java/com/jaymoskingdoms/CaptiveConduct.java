package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.584) A captive who keeps their place is left alone. Andy (2026-11-05), woken in chains at a camp and set on at once:
 * "they shouldnt do that until you try to escape or steal or do something slaves are not allowed".
 * <p>
 * A player in a camp's shackle ({@link StoryHard#shackled}) is spared by their captors (anyone who takes captives,
 * {@link Captors#sentient}, and the holding camp's own people): they don't pick them as a target, and any already on them
 * let go. That ends the moment the captive acts up ({@link #actUp}): striking anyone, breaking a block, rifling a chest or
 * barrel (stealing), or working at the shackle with a lockpick where a captor can see. Then the camp beats them for
 * {@link #UNRULY_TICKS} and the chains' Resistance is off while it lasts. A captive who gets free is hunted as before
 * ({@link LifeStory#startHunt}).
 * <p>
 * Andy (2026-11-14), beaten down again and again in a camp: "after i am beaten i shouldnt continue to be downed, and they
 * should help you up after being downed. you can only break the rules so many times before they end it though". A beating
 * ends once the captive is down: the anger is spent ({@link #UNRULY} cleared), the captors let go and calm, and after
 * {@link #HAUL_DELAY} the nearest of them walks over and hauls them back up ({@link #haul}). Each time a captive acts up
 * is a strike ({@link #STRIKES}); at {@link #STRIKE_LIMIT} the camp has had enough ({@link #doomed}): they beat them down
 * and one of them puts them to death (Powers' executions, which spare a held captive only while they aren't doomed). One
 * they end that way loses the shackle and isn't taken captive again by the death ({@link #executed}): they wake as
 * anyone left for dead does, or their life is over.
 */
public final class CaptiveConduct {
	/** How long (ticks) the camp turns on a captive who acts up. */
	static final long UNRULY_TICKS = 1200L;
	/** How far (blocks) captors look for a captive working at their shackle, and let go of one keeping their place. */
	static final double WATCH_RANGE = 16.0, CALM_RANGE = 40.0;
	/** How often (ticks) captors about a well-behaved captive are made to let go. */
	static final int CALM_EVERY = 10;

	/** How many times a captive may act up; at this many the camp kills them. */
	static final int STRIKE_LIMIT = 3;
	/** How long (ticks) a beaten captive lies before a captor comes to haul them up, how long that takes at their side, how close that is. */
	static final int HAUL_DELAY = 100, HAUL_TICKS = 80;
	static final double HAUL_RANGE = 24.0, HAUL_REACH = 1.8;
	/** How long (ticks) after the killing blow a captive's death keeps them from being taken captive again. */
	static final long EXECUTED_TICKS = 2400L;

	/** On a captive who acted up: the game time the camp's anger ends. Saved. */
	static final AttachmentType<Long> UNRULY = AttachmentRegistry.create(JaymosKingdoms.id("captive_unruly"),
		builder -> builder.persistent(Codec.LONG));
	/** On a captive: how many times they've acted up while held. Saved, kept through death as the shackle is. */
	static final AttachmentType<Integer> STRIKES = AttachmentRegistry.create(JaymosKingdoms.id("captive_strikes"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** A captor hauling a beaten captive up: who, and how long they've been at their side (ticks). Not saved. */
	private static final class Haul {
		final Mob captor;
		int ticks;

		Haul(Mob captor) {
			this.captor = captor;
		}
	}

	/** Captive → the game time they were first seen down this time. */
	private static final Map<UUID, Long> DOWN_SINCE = new HashMap<>();
	/** Captive → who's hauling them up. */
	private static final Map<UUID, Haul> HAULS = new HashMap<>();
	/** Captive put to death by their captors → the game time that stops counting ({@link #executed}). */
	private static final Map<UUID, Long> EXECUTED = new HashMap<>();

	private CaptiveConduct() {
	}

	static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && StoryHard.shackled(sp)) {
				actUp(sp, "struck");
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (player instanceof ServerPlayer sp && StoryHard.shackled(sp)) {
				actUp(sp, "broke");
			}
			return true;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && StoryHard.shackled(sp) && level.getBlockEntity(hit.getBlockPos()) instanceof Container) {
				actUp(sp, "stole");
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(CaptiveConduct::tick);
		// (Andy, 2026-11-14) "before they end it": the killing blow on a doomed captive, already down, takes the shackle off
		// with them; their death isn't a capture (Captors#take asks executed)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && doomed(player) && Downed.isDowned(player)) {
				EXECUTED.put(player.getUUID(), player.level().getGameTime() + EXECUTED_TICKS);
				StoryHard.release(player);
				forget(player);
			}
			return true;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DOWN_SINCE.clear();
			HAULS.clear();
			EXECUTED.clear();
		});
	}

	/** How many times {@code player} has acted up in this shackle. */
	static int strikes(Entity player) {
		Integer n = player.getAttached(STRIKES);
		return n == null ? 0 : n;
	}

	/** {@code player} has acted up {@link #STRIKE_LIMIT} times in their shackle: the camp means to kill them. */
	static boolean doomed(Entity player) {
		return StoryHard.shackled(player) && strikes(player) >= STRIKE_LIMIT;
	}

	/** {@code player} was just put to death by their captors (so not taken captive by it); asked once, by {@link Captors#take}. */
	static boolean executed(ServerPlayer player) {
		Long until = EXECUTED.remove(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	private static void forget(ServerPlayer player) {
		player.removeAttached(STRIKES);
		player.removeAttached(UNRULY);
		DOWN_SINCE.remove(player.getUUID());
		Haul haul = HAULS.remove(player.getUUID());
		if (haul != null) {
			haul.captor.setShiftKeyDown(false);
		}
	}

	/** Whether {@code target} is a captive still keeping their place (shackled, not acting up). */
	static boolean behaving(Entity target) {
		if (!StoryHard.shackled(target) || doomed(target)) {
			return false;
		}
		Long until = target.getAttached(UNRULY);
		return until == null || target.level().getGameTime() >= until;
	}

	/** Whether {@code attacker} leaves {@code target} be: a captor, and a captive keeping their place (asked by every {@code canAttack}). */
	public static boolean spares(LivingEntity attacker, LivingEntity target) {
		if (!(target instanceof Player) || !(attacker.level() instanceof ServerLevel) || attacker == target || !behaving(target)) {
			return false;
		}
		return captor(attacker, StoryHard.shackleCamp((ServerPlayer)target));
	}

	/** One who takes captives, or one of camp {@code camp}'s people. */
	static boolean captor(LivingEntity who, int camp) {
		if (Captors.sentient(who)) {
			return true;
		}
		if (camp < 0 || !(who instanceof Mob mob)) {
			return false;
		}
		Faction f = Factions.get(who.level().getServer(), camp);
		return f != null && Captors.ofCamp(mob, f);
	}

	/** Whether a captor within {@link #WATCH_RANGE} can see {@code player} (working at a shackle where they'd be caught). */
	static boolean watched(ServerPlayer player) {
		int camp = StoryHard.shackleCamp(player);
		return !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(WATCH_RANGE),
			m -> m.isAlive() && captor(m, camp) && m.hasLineOfSight(player)).isEmpty();
	}

	/** {@code player} did what a captive mustn't ({@code what}: struck, broke, stole, pick): the camp turns on them for a while. */
	static void actUp(ServerPlayer player, String what) {
		boolean was = behaving(player);
		ServerLevel level = player.level();
		player.setAttached(UNRULY, level.getGameTime() + UNRULY_TICKS);
		if (!was) {
			return;
		}
		// (Andy, 2026-11-14) each time is a strike; the last one they won't forgive
		int strikes = strikes(player) + 1;
		player.setAttached(STRIKES, strikes);
		player.removeEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
		Component line = Component.translatable("message.jaymoskingdoms.captive.unruly." + what);
		if (strikes >= STRIKE_LIMIT) {
			line = Component.translatable("message.jaymoskingdoms.captive.doomed", line);
		} else if (strikes == STRIKE_LIMIT - 1) {
			line = Component.translatable("message.jaymoskingdoms.captive.warned", line);
		}
		player.sendOverlayMessage(line.copy().withStyle(strikes >= STRIKE_LIMIT ? ChatFormatting.DARK_RED : ChatFormatting.RED));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
		int camp = StoryHard.shackleCamp(player);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(WATCH_RANGE), m -> m.isAlive() && captor(m, camp))) {
			if (mob.getTarget() == null) {
				mob.setTarget(player);
			}
		}
	}

	/**
	 * Each tick: captors on a captive keeping their place let go; a spent anger is cleared; (Andy, 2026-11-14) a beaten
	 * captive's beating ends and they're hauled up, and a doomed one is finished.
	 */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!StoryHard.shackled(player)) {
				if (player.hasAttached(STRIKES) || player.hasAttached(UNRULY) || DOWN_SINCE.containsKey(player.getUUID())) {
					forget(player);
				}
				continue;
			}
			Long until = player.getAttached(UNRULY);
			if (until != null && player.level().getGameTime() >= until) {
				player.removeAttached(UNRULY);
			}
			beaten(player);
			if (player.level().getGameTime() % CALM_EVERY != 0L || !behaving(player) || !player.isAlive()) {
				continue;
			}
			AABB around = player.getBoundingBox().inflate(CALM_RANGE);
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, around, m -> m.getTarget() == player)) {
				if (spares(mob, player)) {
					mob.setTarget(null);
				}
			}
		}
	}

	/**
	 * A captive lying beaten: the beating is over (the anger spent; the calming in {@link #tick} has the captors let go) and
	 * after {@link #HAUL_DELAY} a captor hauls them up. A doomed one is kept as the camp's target, for an execution.
	 */
	private static void beaten(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!player.isAlive() || !Downed.isDowned(player)) {
			DOWN_SINCE.remove(id);
			Haul haul = HAULS.remove(id);
			if (haul != null) {
				haul.captor.setShiftKeyDown(false);
			}
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int camp = StoryHard.shackleCamp(player);
		if (doomed(player)) {
			if (now % CALM_EVERY == 0L && level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(WATCH_RANGE),
				m -> m.isAlive() && m.getTarget() == player).isEmpty()) {
				// (whoever is nearest comes to do it: Powers' executions take one fighting them)
				Mob nearest = nearest(player, WATCH_RANGE, m -> m.isAlive() && !Downed.isDowned(m) && captor(m, camp) && m.hasLineOfSight(player));
				if (nearest != null) {
					nearest.setTarget(player);
				}
			}
			return;
		}
		if (player.hasAttached(UNRULY)) {
			player.removeAttached(UNRULY);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captive.beaten").withStyle(ChatFormatting.GRAY));
		}
		long since = DOWN_SINCE.computeIfAbsent(id, k -> now);
		if (now - since < HAUL_DELAY || player.isPassenger() || PowersCompat.isCarried(player) || player.entityTags().contains(Downed.REVIVE_TAG)) {
			return;
		}
		Haul haul = HAULS.get(id);
		if (haul != null && !canHaul(haul.captor, player, camp)) {
			haul.captor.setShiftKeyDown(false);
			HAULS.remove(id);
			haul = null;
		}
		if (haul == null) {
			if (now % CALM_EVERY != 0L) {
				return;
			}
			Mob nearest = nearest(player, HAUL_RANGE, m -> canHaul(m, player, camp) && HAULS.values().stream().noneMatch(h -> h.captor == m));
			if (nearest == null) {
				return;
			}
			haul = new Haul(nearest);
			HAULS.put(id, haul);
		}
		if (haul(level, player, haul)) {
			HAULS.remove(id);
			DOWN_SINCE.remove(id);
		}
	}

	/** The nearest mob within {@code range} of {@code player} that {@code which} allows, or null. */
	private static @org.jspecify.annotations.Nullable Mob nearest(ServerPlayer player, double range, java.util.function.Predicate<Mob> which) {
		Mob best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Mob m : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range), which)) {
			double d = m.distanceToSqr(player);
			if (d < bestDistance) {
				best = m;
				bestDistance = d;
			}
		}
		return best;
	}

	/** {@code m} may come and haul captive {@code player} up: one of their captors, on its feet, fighting nobody, near enough. */
	private static boolean canHaul(Mob m, ServerPlayer player, int camp) {
		return m.isAlive() && !m.isNoAi() && !m.isBaby() && !m.isPassenger() && !Downed.isDowned(m) && m.getTarget() == null
			&& m.level() == player.level() && m.distanceToSqr(player) < HAUL_RANGE * HAUL_RANGE * 1.5 && captor(m, camp);
	}

	/** The captor walks over, stoops and drags them to their feet. True once they're up (Powers raises them on the tag). */
	private static boolean haul(ServerLevel level, ServerPlayer player, Haul haul) {
		Mob captor = haul.captor;
		if (captor.distanceToSqr(player) > HAUL_REACH * HAUL_REACH) {
			captor.setShiftKeyDown(false);
			if (player.tickCount % 10 == 0) {
				captor.getNavigation().moveTo(player, 1.0);
			}
			return false;
		}
		captor.getNavigation().stop();
		captor.getLookControl().setLookAt(player.getX(), player.getY() + 0.2, player.getZ());
		captor.setShiftKeyDown(true);
		if (++haul.ticks % 25 == 0) {
			captor.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		}
		if (haul.ticks < HAUL_TICKS) {
			return false;
		}
		captor.setShiftKeyDown(false);
		player.addTag(Downed.REVIVE_TAG);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.8F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captive.hauled").withStyle(ChatFormatting.GRAY));
		return true;
	}
}
