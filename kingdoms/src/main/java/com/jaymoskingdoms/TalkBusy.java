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
 */
public final class TalkBusy {
	/** One busy line to a player every this many ticks; clicks between get nothing. */
	static final int GAP = 40;
	/** How many variants each line has. */
	static final int LINES = 2;

	/** What keeps them, in the order it is checked. */
	enum Why {
		FIGHT, TALK, TEND, DEAD, PRISONER, LOOT;

		String key() {
			return "talk_busy.jaymoskingdoms." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** player → the game time of the last busy line they got. */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private TalkBusy() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
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
		Long last = LAST.get(player.getUUID());
		if (last == null || now - last >= GAP || now < last) {
			LAST.put(player.getUUID(), now);
			Speech.tell(level, mob, player, Component.translatable(why.key() + "." + level.getRandom().nextInt(LINES), player.getDisplayName()), Speech.WHITE);
		}
		return true;
	}
}
