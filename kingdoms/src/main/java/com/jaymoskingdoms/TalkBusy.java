package com.jaymoskingdoms;

import com.jaymoskingdoms.forms.Duels;
import com.jaymoskingdoms.forms.GuardDrills;
import com.jaymoskingdoms.spirits.SpiritBarrage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-19): "if a villager is busy period theyll give you a message instead of the chat openining, the openining
 * means they are stopping to speak with you". Right click on an MCA villager who is in the middle of something they
 * can't put down gets a line in a bubble saying so, and their talk menu stays shut ({@code McaTalkHoldMixin}). When the
 * menu does open, they have stopped for you ({@link Conversations}).
 *
 * <p>Busy is what can't wait: a fight (a foe, a duel, a drill, a barrage), a talk with someone else, a downed body they're
 * tending, the dead they're carrying or burying, a prisoner they're hauling, unmasking or pinning, loot they're after.
 * Everyday work (their routines, hunting, a job) is something they stop for, so it doesn't count.
 *
 * <p>Andy (2026-11-25): "asking them multiple times will force them to address you with a line about why they are ignoring
 * you, and you lose hearts". Asked {@link #ASKS_TO_SNAP} times within {@link #ASK_WINDOW} while still busy, they turn to you
 * and tell you plainly why you're being ignored, and it costs {@link #SNAP_HEARTS} hearts with them. Then the count starts
 * again. Clicks inside {@link #GAP} of the last ask are one ask, so a double click doesn't count twice.
 */
public final class TalkBusy {
	/** One busy line to a player every this many ticks; clicks between get nothing. */
	static final int GAP = 40;
	/** How many variants each line has. */
	static final int LINES = 2;
	/** Asks while they're busy before they snap at you, the time those asks must fall within, and the hearts it costs. */
	static final int ASKS_TO_SNAP = 3, ASK_WINDOW = 600, SNAP_HEARTS = 3;
	/** How many variants each snapped line has. */
	static final int SNAP_LINES = 2;

	/** What keeps them, in the order it is checked. */
	enum Why {
		FIGHT, TALK, TEND, DEAD, PRISONER, LOOT;

		String key() {
			return "talk_busy.jaymoskingdoms." + name().toLowerCase(java.util.Locale.ROOT);
		}

		String snapKey() {
			return "talk_busy.jaymoskingdoms.snap." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** player → the game time of the last busy line they got. */
	private static final Map<UUID, Long> LAST = new HashMap<>();
	/** "villager/player" → their asks while the villager was busy: how many, and when the first and the last were. */
	private static final Map<String, Asks> ASKS = new HashMap<>();

	private static final class Asks {
		int count;
		long first = -1L, last = -1L;
	}

	private TalkBusy() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			ASKS.clear();
		});
	}

	/** What keeps {@code mob} from talking to {@code player} just now, or null if they'll stop for them. */
	static @Nullable Why why(PathfinderMob mob, ServerPlayer player) {
		LivingEntity target = mob.getTarget();
		if (target != null && target.isAlive() || Duels.busy(mob.getUUID()) || GuardDrills.busy(mob.getUUID()) || SpiritBarrage.busy(mob)) {
			return Why.FIGHT;
		}
		if (Conversations.talkingWithOther(mob, player) || McaCompat.busy(mob) && !McaCompat.interactingWith(mob, player)) {
			return Why.TALK;
		}
		if (FieldMedics.busy(mob) || Downed.helping(mob)) {
			return Why.TEND;
		}
		if (Bearers.busy(mob) || Undertakers.busy(mob) || RoyalRemains.busy(mob)) {
			return Why.DEAD;
		}
		if (Arrests.busy(mob) || Unmasking.busy(mob) || Grapples.busy(mob)) {
			return Why.PRISONER;
		}
		if (Looters.busy(mob) || Scavengers.busy(mob)) {
			return Why.LOOT;
		}
		return null;
	}

	/** Whether {@code mob} is busy with something that can't wait (as {@link #why} has it, with anyone at all). */
	static boolean occupied(PathfinderMob mob) {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() || Duels.busy(mob.getUUID()) || GuardDrills.busy(mob.getUUID()) || SpiritBarrage.busy(mob)
			|| McaCompat.busy(mob) || FieldMedics.busy(mob) || Downed.helping(mob) || Bearers.busy(mob) || Undertakers.busy(mob)
			|| RoyalRemains.busy(mob) || Arrests.busy(mob) || Unmasking.busy(mob) || Grapples.busy(mob) || Looters.busy(mob)
			|| Scavengers.busy(mob);
	}

	/**
	 * Right click on {@code entity} to talk: if they're busy, they say so (now and then: not on every click) and true is
	 * returned, so the talk menu doesn't open.
	 */
	public static boolean refuse(ServerPlayer player, Entity entity) {
		if (!(entity instanceof PathfinderMob mob) || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		Why why = why(mob, player);
		if (why == null) {
			return false;
		}
		long now = level.getGameTime();
		if (asked(mob, player, now)) {
			// asked once too often: they turn to you and say why you're being ignored, and you've lost some of their good will
			LAST.put(player.getUUID(), now);
			mob.getLookControl().setLookAt(player);
			McaCompat.hearts(mob, player, -SNAP_HEARTS);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 0.85F);
			Speech.tell(level, mob, player, Component.translatable(why.snapKey() + "." + level.getRandom().nextInt(SNAP_LINES), player.getDisplayName()), Speech.WHITE);
			return true;
		}
		Long last = LAST.get(player.getUUID());
		if (last == null || now - last >= GAP || now < last) {
			LAST.put(player.getUUID(), now);
			Speech.tell(level, mob, player, Component.translatable(why.key() + "." + level.getRandom().nextInt(LINES), player.getDisplayName()), Speech.WHITE);
		}
		return true;
	}

	/**
	 * {@code player} asked busy {@code mob} again at {@code now}: counted (clicks within {@link #GAP} of the last ask are the
	 * same ask; asks spread wider than {@link #ASK_WINDOW} start over). True on the ask that makes {@link #ASKS_TO_SNAP},
	 * and the count starts again.
	 */
	private static boolean asked(PathfinderMob mob, ServerPlayer player, long now) {
		Asks a = ASKS.computeIfAbsent(mob.getUUID() + "/" + player.getUUID(), k -> new Asks());
		if (a.last >= 0 && now >= a.last && now - a.last < GAP) {
			return false;
		}
		if (a.first < 0 || now < a.first || now - a.first > ASK_WINDOW) {
			a.count = 0;
			a.first = now;
		}
		a.last = now;
		a.count++;
		if (a.count >= ASKS_TO_SNAP) {
			a.count = 0;
			a.first = -1L;
			return true;
		}
		return false;
	}
}
