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
	/** How far (chunks) the nearest place is looked for; the distance told is rounded to this many blocks. */
	static final int SEARCH_CHUNKS = 40, ROUND = 50;

	private Rumours() {
	}

	/** The real place a rumour would point at from {@code from}, or null if the land has none near. */
	static @Nullable BlockPos place(ServerLevel level, BlockPos from) {
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
