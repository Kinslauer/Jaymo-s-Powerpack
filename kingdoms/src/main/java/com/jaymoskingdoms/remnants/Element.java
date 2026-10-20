package com.jaymoskingdoms.remnants;

import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The four elements the Aether's remnants carry down to the Overworld, one per highland the Aether II has: the Highfields
 * (sky-meadow wind and growth), the Magnetic lands (lodestone pull), the Arctic (frost) and the Irradiated lands
 * (Ambrosium glow, a sickness on the air). Each gives its Giant ({@link AetherGiant}) its powers, its monolith
 * ({@link Monoliths}) its stone, and which biomes it rises in.
 */
public enum Element {
	HIGHFIELD(BossEvent.BossBarColor.GREEN, ParticleTypes.CLOUD, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, Blocks.MOSS_BLOCK, Blocks.PEARLESCENT_FROGLIGHT),
	MAGNETIC(BossEvent.BossBarColor.PURPLE, ParticleTypes.ELECTRIC_SPARK, Blocks.DEEPSLATE_TILES, Blocks.RAW_IRON_BLOCK, Blocks.TUFF, Blocks.LODESTONE),
	ARCTIC(BossEvent.BossBarColor.BLUE, ParticleTypes.SNOWFLAKE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK, Blocks.SEA_LANTERN),
	IRRADIATED(BossEvent.BossBarColor.YELLOW, ParticleTypes.COMPOSTER, Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS, Blocks.TUFF, Blocks.VERDANT_FROGLIGHT);

	/** Biomes colder than this (base temperature) raise Arctic monoliths. */
	static final float ARCTIC_BELOW = 0.2F;

	/** The Giant's boss bar, and the motes round it and its monolith. */
	public final BossEvent.BossBarColor bar;
	public final ParticleOptions mote;
	/** The monolith's stone: its body, its bands, the ground round its foot, and the glowing core at its crown. */
	public final Block stone, band, ground, core;

	Element(BossEvent.BossBarColor bar, ParticleOptions mote, Block stone, Block band, Block ground, Block core) {
		this.bar = bar;
		this.mote = mote;
		this.stone = stone;
		this.band = band;
		this.ground = ground;
		this.core = core;
	}

	public String key() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public Component title() {
		return Component.translatable("remnant.jaymoskingdoms." + key());
	}

	public static Element byOrdinal(int i) {
		Element[] all = values();
		return all[Math.floorMod(i, all.length)];
	}

	/**
	 * The element a monolith in {@code biome} carries: the cold lands are Arctic, the mountains, hills and
	 * badlands Magnetic, the swamps and jungles Irradiated, and everywhere else the Highfields.
	 */
	public static Element of(Holder<Biome> biome) {
		if (biome.value().getBaseTemperature() < ARCTIC_BELOW) {
			return ARCTIC;
		}
		if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || biome.is(BiomeTags.IS_BADLANDS)) {
			return MAGNETIC;
		}
		if (biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
			return IRRADIATED;
		}
		return HIGHFIELD;
	}
}
