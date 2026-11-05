package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
 */
public final class CaptiveConduct {
	/** How long (ticks) the camp turns on a captive who acts up. */
	static final long UNRULY_TICKS = 1200L;
	/** How far (blocks) captors look for a captive working at their shackle, and let go of one keeping their place. */
	static final double WATCH_RANGE = 16.0, CALM_RANGE = 40.0;
	/** How often (ticks) captors about a well-behaved captive are made to let go. */
	static final int CALM_EVERY = 10;

	/** On a captive who acted up: the game time the camp's anger ends. Saved. */
	static final AttachmentType<Long> UNRULY = AttachmentRegistry.create(JaymosKingdoms.id("captive_unruly"),
		builder -> builder.persistent(Codec.LONG));

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
	}

	/** Whether {@code target} is a captive still keeping their place (shackled, not acting up). */
	static boolean behaving(Entity target) {
		if (!StoryHard.shackled(target)) {
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
		player.removeEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captive.unruly." + what).withStyle(ChatFormatting.RED));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
		int camp = StoryHard.shackleCamp(player);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(WATCH_RANGE), m -> m.isAlive() && captor(m, camp))) {
			if (mob.getTarget() == null) {
				mob.setTarget(player);
			}
		}
	}

	/** Each tick: captors on a captive keeping their place let go; a spent anger is cleared. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Long until = player.getAttached(UNRULY);
			if (until != null && (!StoryHard.shackled(player) || player.level().getGameTime() >= until)) {
				player.removeAttached(UNRULY);
			}
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
}
