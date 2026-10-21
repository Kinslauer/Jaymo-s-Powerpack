package com.jaymoskitchen.corpse;

import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;

/**
 * (0.5.5) Minecraft Comes Alive lays a dead villager in a tombstone in its village's graveyard the moment it dies (so
 * its family mourns there, and the Staff of Life can raise it). One a zombie has eaten has nothing left to bury: its
 * tombstone is emptied again (the stone stays, free for the next), so there's no grave to mourn at and nobody to raise.
 * Only touches MCA when it's loaded.
 */
public final class McaGraves {
	private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");

	private McaGraves() {
	}

	/** Empties the grave of the villager {@code dead}, if MCA buried it. */
	public static void unbury(ServerLevel level, UUID dead) {
		if (MCA) {
			Mca.unbury(level, dead);
		}
	}

	/** Kept apart so MCA's classes load only when it's there. */
	private static final class Mca {
		static void unbury(ServerLevel level, UUID dead) {
			Optional<net.minecraft.core.GlobalPos> grave = net.conczin.mca.server.world.data.GraveyardManager.getGlobal(level)
				.getOccupiedGrave(dead);
			if (grave.isEmpty()) {
				return;
			}
			ServerLevel there = level.getServer().getLevel(grave.get().dimension());
			if (there == null) {
				return;
			}
			net.conczin.mca.block.TombstoneBlock.Data.of(there.getBlockEntity(grave.get().pos()))
				.filter(tombstone -> tombstone.getEntityUuid().filter(dead::equals).isPresent())
				// clears the grave's occupant in MCA's records too, and marks the stone empty for the next burial
				.ifPresent(tombstone -> tombstone.setEntity(null));
		}
	}
}
