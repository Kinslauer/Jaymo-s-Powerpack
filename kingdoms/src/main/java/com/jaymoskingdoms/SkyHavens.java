package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A sky village is a <b>haven</b> ({@link Havens}) of the Divine-born: the first time one is looked over
 * ({@link SkyCourt}), a village of its own is founded round its green and drawn a haven of the Divine-born, with the halfway
 * folk (the Dawnborn who walked up) among them ({@link Havens#fits}). It is the one haven that holds no human's blood against
 * them: nobody's blood is held against them in the Aether ({@link Prejudice#minds}), its gate does not stop a human
 * ({@link HavenGates}) and its name is not marked down for it ({@link Prejudice#reputation}). The village is allied to no kingdom
 * below ({@link Villages#ally} is not called): there is no road up.
 */
final class SkyHavens {
	/** A sky village's seat is looked for on its green, this far from the middle (blocks), and no settlement may already stand this near. */
	static final int SEAT_OFF_U = 4, SEAT_OFF_V = 3;
	static final double TAKEN = 64.0;

	private SkyHavens() {
	}

	/** Founds the village of the sky village whose pieces fill {@code box}, if none stands there yet. */
	static void found(ServerLevel level, BoundingBox box) {
		MinecraftServer server = level.getServer();
		BlockPos middle = box.getCenter();
		if (Factions.near(server, middle, TAKEN) != null) {
			return;
		}
		BlockPos seat = seat(level, box, middle);
		if (seat == null) {
			return;
		}
		Faction village = Villages.found(level, seat);
		if (village != null) {
			Havens.make(level, village, Heritage.Kind.DIVINE.key());
			JaymosKingdoms.LOGGER.info("The sky village of {} at {} is a haven of the Divine-born", village.name(), seat);
		}
	}

	/** The first open block over the green, a few steps from the well (whose roof stands over its middle). */
	private static @Nullable BlockPos seat(ServerLevel level, BoundingBox box, BlockPos middle) {
		int x = middle.getX() + SEAT_OFF_U, z = middle.getZ() + SEAT_OFF_V;
		for (int y = box.maxY(); y >= box.minY(); y--) {
			BlockPos at = new BlockPos(x, y, z);
			if (!level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
				return at.above();
			}
		}
		return null;
	}
}
