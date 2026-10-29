package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.323) A vampire's favourite scent: the one mortal player they've spent the most time near (within
 * {@link BloodFever#PLAYER_RANGE}). Each vampire keeps a count of seconds per player ({@link #TIME}, kept); once one
 * player has {@link #MIN_SECONDS} the blood fixates on them ({@link #FAVOURITE}). That player's heartbeat plays louder
 * for the vampire (a raised floor, still capped by the usual maximum: {@code BloodVision}) and spikes the fever twice as
 * hard ({@link BloodFever}).
 */
public final class Favourites {
	/** Seconds near a player before the blood picks them. */
	static final int MIN_SECONDS = 120;
	/** Their heartbeat reads this much closer than they are (the heartbeat's value, capped at 1). */
	static final float HEARTBEAT_BOOST = 1.5F;

	/** Seconds spent within range of each mortal player, by their UUID; kept. */
	static final AttachmentType<Map<String, Integer>> TIME = AttachmentRegistry.create(JaymosKingdoms.id("blood_scent_time"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
	/** The favourite's UUID; synced to the vampire alone (their ears), not kept: worked out again each second. */
	static final AttachmentType<String> FAVOURITE = AttachmentRegistry.create(JaymosKingdoms.id("blood_favourite"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));
	/** Set while the favourite is in their sight; synced to the vampire alone, not kept. */
	static final AttachmentType<Boolean> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("blood_favourite_seen"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	private Favourites() {
	}

	/** Their favourite's UUID, or null (on either side, for the vampire). */
	public static @Nullable String favourite(Player vampire) {
		return vampire.getAttached(FAVOURITE);
	}

	/** Whether their favourite is in their sight now (on either side). */
	public static boolean seen(Player vampire) {
		return Boolean.TRUE.equals(vampire.getAttached(SEEN));
	}

	/** Another second near each of {@code near}; picks the favourite afresh. */
	static void tally(ServerPlayer vampire, Iterable<ServerPlayer> near) {
		Map<String, Integer> times = new HashMap<>(vampire.getAttachedOrElse(TIME, Map.of()));
		boolean any = false;
		for (ServerPlayer other : near) {
			times.merge(other.getUUID().toString(), 1, Integer::sum);
			any = true;
		}
		if (any) {
			vampire.setAttached(TIME, times);
		}
		String best = null;
		int bestSeconds = MIN_SECONDS - 1;
		for (Map.Entry<String, Integer> e : times.entrySet()) {
			if (e.getValue() > bestSeconds) {
				bestSeconds = e.getValue();
				best = e.getKey();
			}
		}
		if (best == null) {
			vampire.removeAttached(FAVOURITE);
		} else if (!best.equals(vampire.getAttached(FAVOURITE))) {
			vampire.setAttached(FAVOURITE, best);
		}
	}

	/** Whether {@code other} is {@code vampire}'s favourite. */
	static boolean is(ServerPlayer vampire, ServerPlayer other) {
		return other.getUUID().toString().equals(vampire.getAttached(FAVOURITE));
	}

	static void setSeen(ServerPlayer vampire, boolean seen) {
		if (seen != seen(vampire)) {
			if (seen) {
				vampire.setAttached(SEEN, true);
			} else {
				vampire.removeAttached(SEEN);
			}
		}
	}

	/** They're not a vampire any more (or never fevered): nothing to keep. */
	static void clear(ServerPlayer vampire) {
		vampire.removeAttached(FAVOURITE);
		vampire.removeAttached(SEEN);
		vampire.removeAttached(TIME);
	}
}
