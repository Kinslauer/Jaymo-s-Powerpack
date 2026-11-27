package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * On a far world the pod lands where you last took off from it, unless you ask for somewhere new. The spot you lifted off from is
 * kept for each world ({@link #MARKS}, by the world's key); the star map's toggle ({@link #NEW_SPOT}) picks the remembered spot or a
 * fresh landing for the next trip. When the remembered spot is no good any more (water, a cliff) the nearest dry land to it is
 * looked for the way a world's pad is ({@code ZFighterSpace.farLand}, out to {@code FAR_LAND} blocks). The Maw has decks, not
 * ground, so it keeps no mark.
 */
public final class PodMarks {
	/** Where each far world was last lifted off from, by world key. Kept through death. */
	public static final AttachmentType<Map<String, BlockPos>> MARKS = AttachmentRegistry.create(JaymosPowers.id("zfighter_pod_marks"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, BlockPos.CODEC)).copyOnDeath());
	/** Set when the next landing is to be somewhere new rather than at the mark. */
	public static final AttachmentType<Boolean> NEW_SPOT = AttachmentRegistry.create(JaymosPowers.id("zfighter_pod_new_spot"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private PodMarks() {
	}

	/** {@code player} lifted off from {@code at} on the far world {@code index}: the next landing there can be the same place. */
	static void lifted(ServerPlayer player, int index, BlockPos at) {
		if (index < 0 || index >= ZFighterSpace.DESTS.length || ZFighterSpace.DESTS[index].kind() == ZFighterSpace.Kind.WRECKS) {
			return;
		}
		Map<String, BlockPos> marks = new HashMap<>(player.getAttachedOrElse(MARKS, Map.of()));
		marks.put(ZFighterSpace.DESTS[index].key(), at.immutable());
		player.setAttached(MARKS, marks);
	}

	/** The spot {@code player} last lifted off from world {@code index}, or null. */
	public static @Nullable BlockPos mark(ServerPlayer player, int index) {
		return index < 0 || index >= ZFighterSpace.DESTS.length ? null : player.getAttachedOrElse(MARKS, Map.<String, BlockPos>of()).get(ZFighterSpace.DESTS[index].key());
	}

	/** Which worlds have a mark, a bit for each world index (for the star map). */
	static int marked(ServerPlayer player) {
		int bits = 0;
		for (int i = 0; i < ZFighterSpace.DESTS.length; i++) {
			if (mark(player, i) != null) {
				bits |= 1 << i;
			}
		}
		return bits;
	}

	/** Whether the next landing is to be somewhere new. */
	public static boolean wantsNew(ServerPlayer player) {
		return player.getAttachedOrElse(NEW_SPOT, false);
	}

	/** The star map's toggle: remembered spot, or somewhere new. */
	static void toggle(ServerPlayer player) {
		player.setAttached(NEW_SPOT, !wantsNew(player));
	}

	/**
	 * Where {@code player} comes down on {@code dest}'s world {@code level}: the remembered spot (or, if it is no good, the nearest
	 * dry land to it); null when there is no mark, they asked for somewhere new, or the world has none to go back to.
	 */
	static @Nullable BlockPos landing(ServerLevel level, ZFighterSpace.Dest dest, ServerPlayer player) {
		int index = ZFighterSpace.indexOf(dest.level());
		BlockPos mark = wantsNew(player) ? null : mark(player, index);
		if (mark == null || dest.kind() == ZFighterSpace.Kind.WRECKS) {
			return null;
		}
		// the pad's corners may lie in the next chunks over
		for (int dx = -ZFighterSpace.PAD_RADIUS; dx <= ZFighterSpace.PAD_RADIUS; dx += ZFighterSpace.PAD_RADIUS * 2) {
			for (int dz = -ZFighterSpace.PAD_RADIUS; dz <= ZFighterSpace.PAD_RADIUS; dz += ZFighterSpace.PAD_RADIUS * 2) {
				level.getChunk(mark.getX() + dx >> 4, mark.getZ() + dz >> 4);
			}
		}
		BlockPos spot = dest.kind() == ZFighterSpace.Kind.FORGE ? ZFighterSpace.forgeFloor(level, mark.getX(), mark.getZ()) : ZFighterSpace.surface(level, mark.getX(), mark.getZ());
		if (spot == null && dest.kind() != ZFighterSpace.Kind.FORGE) {
			spot = ZFighterSpace.farLand(level, mark.getX(), mark.getZ());
		}
		return spot;
	}
}
