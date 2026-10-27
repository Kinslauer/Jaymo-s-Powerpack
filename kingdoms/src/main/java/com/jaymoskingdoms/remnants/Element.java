package com.jaymoskingdoms.remnants;

import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The four elements the Aether's remnants carry down to the Overworld, one per highland the Aether II has: the Highfields
 * (sky-meadow wind and growth), the Magnetic lands (lodestone pull), the Arctic (frost) and the Irradiated lands
 * (Ambrosium glow, a sickness on the air). Each gives its Giant ({@link AetherGiant}) its powers, its monolith
 * ({@link Monoliths}) its stone, and which biomes it rises in.
 */
public enum Element {
	HIGHFIELD(BossEvent.BossBarColor.GREEN, ParticleTypes.CLOUD, "holystone", "holystone_bricks", "aether_grass_block", "zanite_block",
		Blocks.CALCITE, Blocks.SMOOTH_QUARTZ, Blocks.MOSS_BLOCK, Blocks.PEARLESCENT_FROGLIGHT),
	MAGNETIC(BossEvent.BossBarColor.PURPLE, ParticleTypes.ELECTRIC_SPARK, "ferrosite", "undershale_bricks", "ferrosite_sand", "gravitite_block",
		Blocks.DEEPSLATE_TILES, Blocks.RAW_IRON_BLOCK, Blocks.TUFF, Blocks.LODESTONE),
	ARCTIC(BossEvent.BossBarColor.BLUE, ParticleTypes.SNOWFLAKE, "icestone", "icestone_bricks", "arctic_snow_block", "arkenium_block",
		Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK, Blocks.SEA_LANTERN),
	IRRADIATED(BossEvent.BossBarColor.YELLOW, ParticleTypes.COMPOSTER, "irradiated_holystone", "agiosite_bricks", "coarse_aether_dirt", "ambrosium_block",
		Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS, Blocks.TUFF, Blocks.VERDANT_FROGLIGHT);

	/** Biomes colder than this (base temperature) raise Arctic monoliths. */
	static final float ARCTIC_BELOW = 0.2F;

	/** The Giant's boss bar, and the motes round it and its monolith. */
	public final BossEvent.BossBarColor bar;
	public final ParticleOptions mote;
	/**
	 * The monolith's stone: its body, its bands, the ground round its foot, and the core floating at its crown.
	 * (0.7.263) Andy: "built from ... their respective aether materials": Jaymo's Powers' Aether blocks (the Aether II's
	 * stone of each highland, its gem for the core), by id so Kingdoms needs no Powers classes; the old vanilla blocks
	 * without Powers (and on monoliths raised before).
	 */
	private final Identifier[] aether;
	public final Block[] vanilla;
	private Block @Nullable [] blocks;

	static final int STONE = 0, BAND = 1, GROUND = 2, CORE = 3;

	Element(BossEvent.BossBarColor bar, ParticleOptions mote, String stone, String band, String ground, String core, Block vanillaStone,
		Block vanillaBand, Block vanillaGround, Block vanillaCore) {
		this.bar = bar;
		this.mote = mote;
		this.aether = new Identifier[] {aether(stone), aether(band), aether(ground), aether(core)};
		this.vanilla = new Block[] {vanillaStone, vanillaBand, vanillaGround, vanillaCore};
	}

	private static Identifier aether(String path) {
		return Identifier.fromNamespaceAndPath("jaymospowers", path);
	}

	private Block block(int which) {
		if (this.blocks == null) {
			Block[] found = new Block[4];
			for (int i = 0; i < 4; i++) {
				found[i] = BuiltInRegistries.BLOCK.getOptional(this.aether[i]).orElse(this.vanilla[i]);
			}
			this.blocks = found;
		}
		return this.blocks[which];
	}

	public Block stone() {
		return this.block(STONE);
	}

	public Block band() {
		return this.block(BAND);
	}

	public Block ground() {
		return this.block(GROUND);
	}

	public Block core() {
		return this.block(CORE);
	}

	/** Whether {@code state} is one of this monolith's own blocks (its stone, bands or core, Aether or the old vanilla). */
	public boolean builtOf(BlockState state) {
		for (int which : new int[] {STONE, BAND, CORE}) {
			if (state.is(this.block(which)) || state.is(this.vanilla[which])) {
				return true;
			}
		}
		return false;
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
