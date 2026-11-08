package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * (0.21.61, Andy: "The goal is more realistic fishing") Where and when the fish are, our own take on the ideas of Au
 * Naturel (public domain: nothing of it is copied). Which fish come to the hook depends on the water: salmon run in
 * rivers and cold seas, cod in cold and open seas, tropical fish and pufferfish in warm seas, and a tuna or a shark only
 * out on the ocean. They bite more at dawn and dusk and in the rain, never in a pool high above the sea, and a spot gets
 * fished out: each fish landed thins its stock ({@link #STOCK}), which comes back over a few days.
 * Jaymo's Kitchen's fish bait (held in the other hand) brings them on sooner, and one is used per fish landed.
 */
public final class FishWaters {
    /** The water under the hook. */
    public enum Water { RIVER, LAKE, COLD_SEA, SEA, WARM_SEA }

    /** Bites are this many times as likely at dawn and dusk, and in the rain. */
    static final float DAWN_DUSK_BITES = 1.6F;
    static final float RAIN_BITES = 1.3F;
    /** Dawn and dusk, in the day's ticks (0 is sunrise, 12000 sunset). */
    static final int DAWN_FROM = 23000, DAWN_TO = 1500, DUSK_FROM = 11000, DUSK_TO = 13500;
    /** A pool this many blocks above sea level holds no fish (a puddle up a mountain). */
    public static final int HIGH_POOL = 24;
    /** Bites with Kitchen's fish bait in the other hand. */
    static final float BAIT_BITES = 1.5F;
    /** A spot is this many blocks across (a square of water fished as one). */
    static final int SPOT_SHIFT = 5;
    /** Fish a spot holds before it's fished out. */
    static final int STOCK = 10;
    /** One fish landed comes back to its spot after this long (ticks: a full spot refills in 3 days). */
    static final long REFILL_TICKS = 24000L * 3 / STOCK;
    /** A fished-out spot still bites this often (share of a full one). */
    static final float FISHED_OUT_BITES = 0.1F;
    /** Warn the angler once a spot is down to this share of its stock. */
    static final float THIN_WARNING = 0.3F;
    /** Weights (out of 100) of cod, salmon, pufferfish and tropical fish in each water, wooden rod or metal. */
    static final int[] RIVER_FISH = {25, 75, 0, 0};
    static final int[] LAKE_FISH = {45, 55, 0, 0};
    static final int[] COLD_SEA_FISH = {60, 40, 0, 0};
    static final int[] SEA_FISH = {55, 25, 8, 12};
    static final int[] WARM_SEA_FISH = {15, 5, 25, 55};
    /** Out of 100: a bite on the metal rod out on the sea is a shark, and a tuna (more of both in warm water). */
    static final int SHARK_SEA = 12, TUNA_SEA = 18;
    static final int SHARK_WARM = 18, TUNA_WARM = 22;
    static final int SHARK_COLD = 6, TUNA_COLD = 10;

    /** Spot "x,z" → when it's full again (game time); gone once full. On each level. */
    public static final AttachmentType<Map<String, Long>> FISHED = AttachmentRegistry.create(JaymosPowers.id("fished_spots"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

    private FishWaters() {
    }

    /** The water at {@code pos}. */
    public static Water water(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_RIVER)) {
            return Water.RIVER;
        }
        if (biome.is(Biomes.WARM_OCEAN) || biome.is(Biomes.LUKEWARM_OCEAN) || biome.is(Biomes.DEEP_LUKEWARM_OCEAN)) {
            return Water.WARM_SEA;
        }
        if (biome.is(Biomes.COLD_OCEAN) || biome.is(Biomes.DEEP_COLD_OCEAN) || biome.is(Biomes.FROZEN_OCEAN)
                || biome.is(Biomes.DEEP_FROZEN_OCEAN)) {
            return Water.COLD_SEA;
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)) {
            return Water.SEA;
        }
        return Water.LAKE;
    }

    /** The kind of fish ({@code FishingReal.FISH_*}) that takes the hook at {@code pos}. */
    public static int pick(ServerLevel level, BlockPos pos, boolean metalRod, RandomSource random) {
        Water water = water(level, pos);
        int r = random.nextInt(100);
        if (metalRod && water != Water.RIVER && water != Water.LAKE) {
            int shark = water == Water.WARM_SEA ? SHARK_WARM : water == Water.COLD_SEA ? SHARK_COLD : SHARK_SEA;
            int tuna = water == Water.WARM_SEA ? TUNA_WARM : water == Water.COLD_SEA ? TUNA_COLD : TUNA_SEA;
            if (r < shark) {
                return FishingReal.FISH_SHARK;
            }
            if (r < shark + tuna) {
                return FishingReal.FISH_TUNA;
            }
            r = random.nextInt(100);
        }
        int[] table = switch (water) {
            case RIVER -> RIVER_FISH;
            case LAKE -> LAKE_FISH;
            case COLD_SEA -> COLD_SEA_FISH;
            case SEA -> SEA_FISH;
            case WARM_SEA -> WARM_SEA_FISH;
        };
        int[] kinds = {FishingReal.FISH_COD, FishingReal.FISH_SALMON, FishingReal.FISH_PUFFERFISH, FishingReal.FISH_TROPICAL};
        int total = 0;
        for (int w : table) {
            total += w;
        }
        int roll = r * total / 100;
        for (int i = 0; i < table.length; i++) {
            roll -= table[i];
            if (roll < 0) {
                return kinds[i];
            }
        }
        return FishingReal.FISH_COD;
    }

    /** How much likelier (or rarer) a bite is at {@code pos} just now for {@code player}: time, weather, height, stock, bait. */
    public static float bites(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (pos.getY() > level.getSeaLevel() + HIGH_POOL) {
            return 0.0F;
        }
        float f = 1.0F;
        long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (day >= DAWN_FROM || day < DAWN_TO || day >= DUSK_FROM && day < DUSK_TO) {
            f *= DAWN_DUSK_BITES;
        }
        if (level.isRainingAt(pos.above())) {
            f *= RAIN_BITES;
        }
        if (!bait(player).isEmpty()) {
            f *= BAIT_BITES;
        }
        float stock = stock(level, pos);
        return f * (FISHED_OUT_BITES + (1.0F - FISHED_OUT_BITES) * stock);
    }

    /** The share of its fish the spot at {@code pos} still holds (1 full, 0 fished out). */
    public static float stock(ServerLevel level, BlockPos pos) {
        Long full = level.getAttachedOrElse(FISHED, Map.of()).get(spot(pos));
        if (full == null) {
            return 1.0F;
        }
        long left = full - level.getGameTime();
        if (left <= 0) {
            return 1.0F;
        }
        float taken = (float) left / REFILL_TICKS;
        return Math.max(0.0F, 1.0F - taken / STOCK);
    }

    /** A fish was landed at {@code pos}: the spot holds one fewer. True if it's now thin (worth warning the angler). */
    public static boolean landed(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        Map<String, Long> next = new HashMap<>();
        for (Map.Entry<String, Long> e : level.getAttachedOrElse(FISHED, Map.of()).entrySet()) {
            if (e.getValue() > now) {
                next.put(e.getKey(), e.getValue());
            }
        }
        String key = spot(pos);
        long full = Math.max(now, next.getOrDefault(key, now));
        next.put(key, Math.min(now + REFILL_TICKS * STOCK, full + REFILL_TICKS));
        level.setAttached(FISHED, Map.copyOf(next));
        return stock(level, pos) <= THIN_WARNING;
    }

    private static String spot(BlockPos pos) {
        return (pos.getX() >> SPOT_SHIFT) + "," + (pos.getZ() >> SPOT_SHIFT);
    }

    /** Jaymo's Kitchen's fish bait in the hand that isn't holding the rod, or empty (always, without Kitchen). */
    public static ItemStack bait(ServerPlayer player) {
        Item bait = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymoskitchen", "fish_bait")).orElse(null);
        if (bait == null) {
            return ItemStack.EMPTY;
        }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        return off.is(bait) ? off : main.is(bait) ? main : ItemStack.EMPTY;
    }

    /** A fish landed baited: one bait goes. */
    public static void useBait(ServerPlayer player) {
        ItemStack bait = bait(player);
        if (!bait.isEmpty() && !player.hasInfiniteMaterials()) {
            bait.shrink(1);
        }
    }
}
