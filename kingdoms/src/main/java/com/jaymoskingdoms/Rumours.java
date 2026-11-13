package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A treasure rumour that points at a real place: the nearest buried treasure, or failing that a ruin, within
 * {@link #SEARCH_CHUNKS} chunks of the teller. What is said is vague on purpose (a direction and a distance rounded to
 * {@link #ROUND} blocks). Told by a drunk's whisper ({@link Carousing}) and by folk who pass on news ({@link StartPerks}).
 */
final class Rumours {
	/**
	 * How far the nearest place is looked for (rings of the structure's spacing, as findNearestMapStructure counts: 40
	 * meant thousands of chunks made on the server thread, and a tavern night asked up to a dozen times), and the
	 * distance told is rounded to this many blocks.
	 */
	static final int SEARCH_CHUNKS = LifeStory.STRUCTURE_SEARCH, ROUND = 50;
	/** How long (ticks) the last answer is reused for asks from the same few chunks (a night's draws ask again and again). */
	static final int REMEMBER_TICKS = 200, REMEMBER_CHUNKS = 4;

	/** The last search: where from (chunk), which level, when, and what it found (null: nothing). */
	private static @Nullable ServerLevel lastLevel;
	private static int lastX, lastZ;
	private static long lastAt = Long.MIN_VALUE;
	private static @Nullable BlockPos lastFound;

	private Rumours() {
	}

	/** The real place a rumour would point at from {@code from}, or null if the land has none near. */
	static @Nullable BlockPos place(ServerLevel level, BlockPos from) {
		int cx = from.getX() >> 4, cz = from.getZ() >> 4;
		long now = level.getGameTime();
		if (level == lastLevel && now - lastAt < REMEMBER_TICKS && Math.abs(cx - lastX) <= REMEMBER_CHUNKS && Math.abs(cz - lastZ) <= REMEMBER_CHUNKS) {
			return lastFound;
		}
		BlockPos found = search(level, from);
		lastLevel = level;
		lastX = cx;
		lastZ = cz;
		lastAt = now;
		lastFound = found;
		return found;
	}

	private static @Nullable BlockPos search(ServerLevel level, BlockPos from) {
		BlockPos at = level.findNearestMapStructure(StructureTags.ON_TREASURE_MAPS, from, SEARCH_CHUNKS, false);
		if (at == null) {
			at = level.findNearestMapStructure(StructureTags.ON_OCEAN_MONUMENT_MAPS, from, SEARCH_CHUNKS, false);
		}
		if (at == null) {
			at = level.findNearestMapStructure(StructureTags.ON_WOODLAND_MANSION_MAPS, from, SEARCH_CHUNKS, false);
		}
		return at;
	}

	/** What is said of {@code at} from {@code from}: where, roughly. */
	static Component words(Vec3 from, BlockPos at) {
		double far = Math.sqrt(at.distToCenterSqr(from));
		long rounded = Math.max(ROUND, Math.round(far / ROUND) * ROUND);
		return Component.translatable("rumour.jaymoskingdoms.where", rounded, Directions.heading(from, Vec3.atCenterOf(at)));
	}

	/** {@code teller} tells {@code player} a rumour in a speech bubble. Returns the place it points at, or null (they know of none). */
	static @Nullable BlockPos tell(ServerLevel level, Entity teller, ServerPlayer player) {
		BlockPos at = place(level, teller.blockPosition());
		if (at == null) {
			Speech.tell(level, teller, player, Component.translatable("rumour.jaymoskingdoms.none"), Speech.WHITE);
			return null;
		}
		Speech.tell(level, teller, player, Component.translatable("rumour.jaymoskingdoms.line." + level.getRandom().nextInt(3), words(teller.position(), at)), Speech.GOLD);
		return at;
	}
}
