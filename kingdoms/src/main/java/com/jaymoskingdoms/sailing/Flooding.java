package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Salvage (the sailing plan: "A helm on a vanilla shipwreck that isn't buried casts it off half full of water. Pump it out
 * (sponges or buckets), patch the hull and sail it"): a ship's hold can hold water ({@link Ship#flooded()}), which weighs
 * her down until it's pumped out. A wreck raised off the bottom comes up with all the water in her hold; one anchored
 * still flooded leaves that water in her hold, and the helms of such ships are kept here (by the level) so she takes it
 * up again when she next casts off.
 */
public final class Flooding {
	/** How much water one empty bucket bails out of her hold, and one dry sponge soaks up. */
	static final int BUCKET = 1, SPONGE = 32;

	static final AttachmentType<List<Long>> HELMS = AttachmentRegistry.create(JaymosKingdoms.id("flooded_helms"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private Flooding() {
	}

	static void register() {
		// (class loaded at init, for its attachment)
	}

	/** Whether the ship whose helm is at {@code helm} was anchored with water still in her hold. */
	static boolean wasFlooded(ServerLevel level, BlockPos helm) {
		return level.getAttachedOrElse(HELMS, List.of()).contains(helm.asLong());
	}

	/** She anchored with her helm at {@code helm}, her hold still flooded. */
	static void remember(ServerLevel level, BlockPos helm) {
		Set<Long> helms = new HashSet<>(level.getAttachedOrElse(HELMS, List.of()));
		if (helms.add(helm.asLong())) {
			level.setAttached(HELMS, List.copyOf(helms));
		}
	}

	static void forget(ServerLevel level, BlockPos helm) {
		Set<Long> helms = new HashSet<>(level.getAttachedOrElse(HELMS, List.of()));
		if (helms.remove(helm.asLong())) {
			level.setAttached(HELMS, List.copyOf(helms));
		}
	}
}
