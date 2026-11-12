package com.AdamNew.dynamicwaters.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<Block> ORES = block("ores");

    public static final TagKey<Biome> IS_BADLANDS = biome("is_badlands");
    public static final TagKey<Biome> IS_DESERT = biome("is_desert");
    public static final TagKey<Biome> IS_DRY_OVERWORLD = biome("is_dry/overworld");
    public static final TagKey<Biome> IS_ICY = biome("is_icy");
    public static final TagKey<Biome> IS_JUNGLE = biome("is_jungle");
    public static final TagKey<Biome> IS_LUSH = biome("is_lush");
    public static final TagKey<Biome> IS_SAVANNA = biome("is_savanna");
    public static final TagKey<Biome> IS_SNOWY = biome("is_snowy");
    public static final TagKey<Biome> IS_SWAMP = biome("is_swamp");
    public static final TagKey<Biome> IS_UNDERGROUND = biome("is_underground");

    private ModTags() {
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", path));
    }

    private static TagKey<Biome> biome(String path) {
        return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", path));
    }
}
