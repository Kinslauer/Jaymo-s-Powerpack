package com.jaymoskingdoms;

import com.jaymoskingdoms.seas.Mohknee;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Word of Mohknee on the home continent (docs/BlossomLands-Plan.md, patch 2; Andy: "a seperate continent spoken about
 * by villagers"). Nobody here has a map of it (Lore): folk speak of it sideways, as gossip ({@link Approaches}), the
 * more the nearer they live to the sea, and sailors most of all. What comes from it, the ships of strange build, who
 * sailed east after them. Never a place on a map, never a bearing: a sailor who has made the crossing is the way
 * there ({@link Helmsmen}).
 */
final class MohkneeTalk {
	/** How often someone's gossip is of the land in the east: inland, by the shore, and one of a ship's crew. */
	static final float INLAND = 0.02F, SHORE = 0.1F, SAILOR = 0.3F;
	static final int LINES = 12, SAILOR_LINES = 6;

	private MohkneeTalk() {
	}

	/** {@code speaker}'s gossip of the land in the east, or null (most times; always on Mohknee itself and on its sea). */
	static @Nullable Component gossip(ServerLevel level, LivingEntity speaker) {
		if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD
			|| Mohknee.distance(speaker.getX(), speaker.getZ()) < Mohknee.edge()) {
			return null;
		}
		boolean sailor = Crewmen.tagged(speaker);
		float chance = sailor ? SAILOR : Havens.shore(level, speaker.blockPosition()) ? SHORE : INLAND;
		if (level.getRandom().nextFloat() >= chance) {
			return null;
		}
		return sailor && level.getRandom().nextBoolean()
			? Component.translatable("talk.jaymoskingdoms.gossip.east.sailor." + level.getRandom().nextInt(SAILOR_LINES))
			: Component.translatable("talk.jaymoskingdoms.gossip.east." + level.getRandom().nextInt(LINES));
	}
}
