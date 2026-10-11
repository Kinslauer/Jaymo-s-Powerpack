package com.jaymoskingdoms;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/** The half of {@link McaCompat} that names MCA's classes: only called while MCA is loaded. */
final class McaBridge {
	/** An MCA village counts as a faction's when its border (plus this) takes in the faction's seat. */
	static final int SEAT_MARGIN = 32;

	private McaBridge() {
	}

	/** {@code player}'s reputation (summed hearts) with the MCA village round {@code seat}, or null if there's none. */
	static @Nullable Integer villageHearts(ServerLevel level, BlockPos seat, ServerPlayer player) {
		return VillageManager.get(level).findNearestVillage(seat, SEAT_MARGIN).map(v -> v.getReputation(player)).orElse(null);
	}

	static boolean isMca(Villager villager) {
		return villager instanceof VillagerEntityMCA;
	}
}
