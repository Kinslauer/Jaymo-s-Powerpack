package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * Where the old dead rose (docs/Blight-Hordes-Plan.md, patch 5: own-skins, etf-place). The first time a zombie of the
 * present day stands in a world, the ground it rose from is written on it: the {@link Place} (dried in the deserts,
 * badlands and savannas, bloated by swamps, rivers and shores, pale from the caves and the deep dark or anywhere deep
 * and sunless, frostbitten in the snowy and frozen lands, else plain) and the {@link Land} whose villager it was (the
 * game's own villager types, so its clothes are its land's). Saved and synced, so it keeps its look wherever it walks;
 * client/OldDeadSkins dresses it from these and its id. The future's dead (Jaymo's Powers) are left alone.
 */
public final class RisenLooks {
	/** Pale: risen at least this far below sea level, out of sight of the sky. */
	static final int PALE_DEPTH = 8;

	/** Jaymo's Powers' ruined future, where the dead are its own (Kingdoms never compiles against Powers). */
	public static final ResourceKey<Level> FUTURE = ResourceKey.create(Registries.DIMENSION,
		Identifier.fromNamespaceAndPath("jaymospowers", "future"));

	/** The lands, after the game's villager types (their names are the skins' file names). */
	public enum Land {
		PLAINS, TAIGA, SNOW, SWAMP, JUNGLE, SAVANNA, DESERT;

		public final String id = this.name().toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * Where it rose, and the lands each look is drawn in (kingdoms/tools/gen_old_dead.py's PLACE_LANDS; keep the two in
	 * step). A land a look isn't drawn in wears the look's first.
	 */
	public enum Place {
		PLAIN(Land.values()),
		DRIED(Land.DESERT, Land.SAVANNA),
		BLOATED(Land.PLAINS, Land.SWAMP),
		PALE(Land.values()),
		FROSTBITTEN(Land.SNOW, Land.TAIGA, Land.PLAINS);

		public final String id = this.name().toLowerCase(java.util.Locale.ROOT);
		public final java.util.List<Land> lands;

		Place(Land... lands) {
			this.lands = java.util.List.of(lands);
		}

		/** The land it's drawn in for one of {@code land}. */
		public Land drawn(Land land) {
			return this.lands.contains(land) ? land : this.lands.getFirst();
		}
	}

	/** On a zombie: {@code place * 8 + land} (ordinals). Saved, synced to everyone. */
	public static final AttachmentType<Integer> ROSE = AttachmentRegistry.create(JaymosKingdoms.id("risen_look"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private RisenLooks() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(RisenLooks::loaded);
	}

	private static void loaded(Entity entity, ServerLevel level) {
		if (entity.getType() != EntityTypes.ZOMBIE || entity.hasAttached(ROSE) || level.dimension() == FUTURE) {
			return;
		}
		BlockPos at = entity.blockPosition();
		Holder<Biome> biome = level.getBiome(at);
		entity.setAttached(ROSE, place(level, at, biome).ordinal() * 8 + land(biome).ordinal());
	}

	/** Where it rose: the dark below first, then the cold, then the dry, then the wet. */
	static Place place(ServerLevel level, BlockPos at, Holder<Biome> biome) {
		if (biome.is(ConventionalBiomeTags.IS_UNDERGROUND) || biome.is(ConventionalBiomeTags.IS_CAVE) || biome.is(Biomes.DEEP_DARK)
			|| !level.canSeeSky(at) && at.getY() < level.getSeaLevel() - PALE_DEPTH) {
			return Place.PALE;
		}
		if (biome.is(ConventionalBiomeTags.IS_SNOWY) || biome.is(ConventionalBiomeTags.IS_ICY) || biome.is(ConventionalBiomeTags.IS_AQUATIC_ICY)
			|| biome.value().coldEnoughToSnow(at, level.getSeaLevel())) {
			return Place.FROSTBITTEN;
		}
		if (biome.is(ConventionalBiomeTags.IS_DESERT) || biome.is(ConventionalBiomeTags.IS_BADLANDS) || biome.is(ConventionalBiomeTags.IS_SAVANNA)) {
			return Place.DRIED;
		}
		if (biome.is(ConventionalBiomeTags.IS_SWAMP) || biome.is(ConventionalBiomeTags.IS_RIVER) || biome.is(ConventionalBiomeTags.IS_BEACH)) {
			return Place.BLOATED;
		}
		return Place.PLAIN;
	}

	/** Whose villager it was: the game's villager type for the biome. */
	static Land land(Holder<Biome> biome) {
		ResourceKey<VillagerType> type = VillagerType.byBiome(biome);
		if (type == VillagerType.TAIGA) {
			return Land.TAIGA;
		} else if (type == VillagerType.SNOW) {
			return Land.SNOW;
		} else if (type == VillagerType.SWAMP) {
			return Land.SWAMP;
		} else if (type == VillagerType.JUNGLE) {
			return Land.JUNGLE;
		} else if (type == VillagerType.SAVANNA) {
			return Land.SAVANNA;
		} else if (type == VillagerType.DESERT) {
			return Land.DESERT;
		}
		return Land.PLAINS;
	}

	/** A stored look's place and land. */
	public static Place place(int look) {
		Place[] places = Place.values();
		return places[Math.floorMod(look >> 3, places.length)];
	}

	public static Land land(int look) {
		Land[] lands = Land.values();
		return lands[Math.floorMod(look & 7, lands.length)];
	}
}
