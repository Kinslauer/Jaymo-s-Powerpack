package com.jaymoskingdoms;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
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

	/** Grown, single, nobody's child among the players, and no player's friend. */
	static boolean spareable(Villager villager) {
		if (!(villager instanceof VillagerEntityMCA mca)) {
			return true;
		}
		if (mca.getAgeState() != AgeState.ADULT) {
			return false;
		}
		FamilyTreeNode self = mca.getRelationships().getFamilyEntry();
		if (FamilyTreeNode.isValid(self.partner())) {
			return false;
		}
		FamilyTree tree = FamilyTree.get((ServerLevel)mca.level());
		if (self.streamParents().anyMatch(parent -> tree.getOrEmpty(parent).map(FamilyTreeNode::isPlayer).orElse(false))) {
			return false;
		}
		int friend = Config.getInstance().heartsToBeConsideredAsFriend;
		for (Memories memory : mca.getVillagerBrain().getMemories().values()) {
			if (memory.getHearts() >= friend) {
				return false;
			}
		}
		return true;
	}
}
