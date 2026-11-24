package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * Cairn caches (village mods patch 6, row rc_cairn; Recurrent Complex's idea, our own designs): heaps of field stones on
 * the ways folk walk, each over a chest buried an arm's length down ({@code jaymoskingdoms:smalls/cairn}, four shapes drawn
 * by {@code tools/small_structures.py}; the chest is {@code chests/cairn}). They stand within {@link PlacementReasons}'
 * {@code cairns_by_ways} ring of a settlement, since they mark the ways out of it (Lore.md, "Cairns on the ways").
 *
 * <p>A rumour ({@link Rumours#tell}) now and then names the nearest one: found where the world would put it, without making
 * any chunk ({@link Scout}).
 */
final class Cairns {
	/** The cairns (the structure tag the rumour looks for). */
	static final TagKey<Structure> CAIRNS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("cairns"));
	/** How often a rumour is of a cairn, if one is near; how far out (rings of the cairns' spacing, about 400 blocks each). */
	static final float HINT_CHANCE = 0.25F;
	static final int HINT_RINGS = 2;
	/** Lines a cairn rumour has ({@code rumour.jaymoskingdoms.cairn.<n>}). */
	static final int HINT_LINES = 3;

	private Cairns() {
	}

	/**
	 * What {@code teller} says of the nearest cairn, or null (it doesn't come up, or none is near). Sets {@code where[0]} to
	 * the cairn.
	 */
	static @Nullable Component hint(ServerLevel level, Entity teller, BlockPos[] where) {
		if (level.getRandom().nextFloat() >= HINT_CHANCE) {
			return null;
		}
		BlockPos at = Scout.nearest(level, CAIRNS, teller.blockPosition(), HINT_RINGS);
		if (at == null) {
			return null;
		}
		where[0] = at;
		return Component.translatable("rumour.jaymoskingdoms.cairn." + level.getRandom().nextInt(HINT_LINES), Rumours.words(teller.position(), at));
	}
}
