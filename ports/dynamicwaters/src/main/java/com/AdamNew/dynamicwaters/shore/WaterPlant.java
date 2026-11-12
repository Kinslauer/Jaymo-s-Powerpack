package com.AdamNew.dynamicwaters.shore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FrogspawnBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;

import java.util.Locale;

public final class WaterPlant {
    private static final Logger LOG = LogUtils.getLogger();

    public enum At {
        BED,

        SURFACE,

        SHORE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static At parse(String s) {
            for (At a : values()) {
                if (a.id().equals(s)) return a;
            }
            return null;
        }
    }

    final String id;
    final BlockState state;

    final boolean ownState;
    final At at;

    final double chance;

    final int minDepth, maxDepth;

    final boolean calm;

    final int patchSize;
    final double patchShare;
    final boolean alone;

    final int minHeight, maxHeight;

    final int salt;

    private WaterPlant(String id, BlockState state, boolean ownState, At at, double chance, int minDepth, int maxDepth,
            boolean calm, int patchSize, double patchShare, boolean alone, int minHeight, int maxHeight, int salt) {
        this.id = id;
        this.state = state;
        this.ownState = ownState;
        this.at = at;
        this.chance = chance;
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
        this.calm = calm;
        this.patchSize = patchSize;
        this.patchShare = patchShare;
        this.alone = alone;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.salt = salt;
    }

    boolean isKelp() {
        return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }

    boolean isDouble() {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    boolean isPickles() {
        return state.hasProperty(SeaPickleBlock.PICKLES) && !ownState;
    }

    public static At usualPlace(BlockState s) {
        if (s.getBlock() instanceof LilyPadBlock || s.getBlock() instanceof FrogspawnBlock) return At.SURFACE;
        if (s.getBlock() instanceof SugarCaneBlock) return At.SHORE;
        return At.BED;
    }

    @Override
    public String toString() {
        return "WaterPlant[" + id + " " + at.id() + "]";
    }

    static WaterPlant parse(String source, String list, JsonElement e) {
        final JsonObject o;
        if (e != null && e.isJsonPrimitive()) {
            o = new JsonObject();
            o.addProperty("block", e.getAsString());
        } else if (e != null && e.isJsonObject()) {
            o = e.getAsJsonObject();
        } else {
            LOG.warn("Dynamic Waters: water plants {}: a plant in \"{}\" is neither a block id nor an object", source, list);
            return null;
        }
        final String raw = o.has("block") && o.get("block").isJsonPrimitive() ? o.get("block").getAsString().trim() : "";
        if (raw.isEmpty()) {
            LOG.warn("Dynamic Waters: water plants {}: a plant in \"{}\" has no block", source, list);
            return null;
        }
        final BlockState state;
        final boolean ownState = raw.contains("[");
        try {
            state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, raw, false).blockState();
        } catch (Exception ex) {
            LOG.warn("Dynamic Waters: water plants {}: \"{}\" in \"{}\" is not a block: {}", source, raw, list, ex.getMessage());
            return null;
        }
        if (state.isAir()) return null;
        At at = usualPlace(state);
        if (o.has("at")) {
            final At given = At.parse(o.get("at").getAsString().trim().toLowerCase(Locale.ROOT));
            if (given != null) at = given;
            else LOG.warn("Dynamic Waters: water plants {}: \"at\" of {} should be bed, surface or shore", source, raw);
        }
        final int[] depth = range(o.get("depth"), 1, 64);
        final int[] height = range(o.get("height"), 1, 1);
        final int salt = o.has("salt") ? BiomeMatch.whole(o, "salt", 0) : defaultSalt(raw, list);
        return new WaterPlant(raw, state, ownState, at,
                clamp(BiomeMatch.number(o, "chance", 0.05), 0.0, 1.0),
                Math.max(1, Math.min(depth[0], depth[1])), Math.max(1, Math.max(depth[0], depth[1])),
                BiomeMatch.flag(o, "calm", false),
                Math.max(0, Math.min(32, BiomeMatch.whole(o, "patch_size", 0))),
                clamp(BiomeMatch.number(o, "patch_share", 0.3), 0.0, 1.0),
                BiomeMatch.flag(o, "alone", false),
                Math.max(1, Math.min(height[0], height[1])), Math.min(16, Math.max(1, Math.max(height[0], height[1]))),
                salt);
    }

    static int defaultSalt(String block, String list) {
        return (block.hashCode() * 0x9E3779B1) ^ (list.hashCode() * 0x85EBCA77);
    }

    private static int[] range(JsonElement e, int from, int to) {
        try {
            if (e != null && e.isJsonArray()) {
                final JsonArray a = e.getAsJsonArray();
                return new int[] { a.get(0).getAsInt(), a.get(a.size() > 1 ? 1 : 0).getAsInt() };
            }
            if (e != null && e.isJsonPrimitive()) {
                final int v = e.getAsInt();
                return new int[] { v, v };
            }
        } catch (RuntimeException ignored) {
        }
        return new int[] { from, to };
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
