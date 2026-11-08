package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.61, Andy: "The goal is more realistic fishing") What a landed fish is. Every catch has a weight ({@link #WEIGHT},
 * shown under its name): drawn round its kind's usual size, a heavy one fights harder ({@link #pull}) and, gutted on
 * Jaymo's Kitchen's cutting board, gives more fillets. A tuna comes in whole ({@link #TUNA}: eat it, cook it, or cut it
 * into steaks, three by hand or by its weight on the board); a shark is far too big to carry, so it's cut up on the
 * shore into {@link #SHARK_MEAT}, a piece for each {@link #SHARK_KG_PER_CUT} kg.
 */
public final class FishCatch {
    /** A fish's usual weight and how far either way it runs (kg): cod, salmon, pufferfish, tropical fish, shark, tuna. */
    static final float[] KG = {4.0F, 5.0F, 0.8F, 0.4F, 400.0F, 120.0F};
    static final float[] KG_SPREAD = {3.0F, 4.0F, 0.5F, 0.3F, 250.0F, 80.0F};
    /** No fish is lighter than this share of its usual weight. */
    static final float LIGHTEST = 0.2F;
    /** A fish's pull on the line grows with its weight (weight / usual), held between these. */
    static final float PULL_LEAST = 0.7F, PULL_MOST = 1.4F;
    /** A landed shark: a piece of meat for each this many kg, at least and at most. */
    static final float SHARK_KG_PER_CUT = 50.0F;
    static final int SHARK_CUTS_LEAST = 3, SHARK_CUTS_MOST = 12;
    /** Steaks from a whole tuna cut by hand (the board cuts by its weight). */
    public static final int TUNA_STEAKS_BY_HAND = 3;
    /** Food: a whole raw tuna, a raw and a cooked steak, raw and cooked shark meat. */
    static final int TUNA_FOOD = 6, TUNA_STEAK_FOOD = 2, COOKED_TUNA_STEAK_FOOD = 6, SHARK_FOOD = 3, COOKED_SHARK_FOOD = 7;
    static final float RAW_SATURATION = 0.1F, COOKED_SATURATION = 0.8F;

    /** A fish's weight in kg (on cod, salmon, pufferfish, tropical fish and a whole tuna landed from the real fishing). */
    public static DataComponentType<Float> WEIGHT;
    public static Item TUNA, TUNA_STEAK, COOKED_TUNA_STEAK, SHARK_MEAT, COOKED_SHARK_MEAT;

    private FishCatch() {
    }

    static void init() {
        WEIGHT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("fish_weight"),
                DataComponentType.<Float>builder().persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());
        TUNA = food("tuna", TUNA_FOOD, RAW_SATURATION);
        TUNA_STEAK = food("tuna_steak", TUNA_STEAK_FOOD, RAW_SATURATION);
        COOKED_TUNA_STEAK = food("cooked_tuna_steak", COOKED_TUNA_STEAK_FOOD, COOKED_SATURATION);
        SHARK_MEAT = food("shark_meat", SHARK_FOOD, RAW_SATURATION);
        COOKED_SHARK_MEAT = food("cooked_shark_meat", COOKED_SHARK_FOOD, COOKED_SATURATION);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
            output.accept(TUNA);
            output.accept(TUNA_STEAK);
            output.accept(COOKED_TUNA_STEAK);
            output.accept(SHARK_MEAT);
            output.accept(COOKED_SHARK_MEAT);
        });
    }

    private static Item food(String id, int nutrition, float saturation) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build(), Consumables.DEFAULT_FOOD)));
    }

    /** A weight for a fish of {@code kind}, in kg to a tenth (heavier ones rarer: a bell round the usual). */
    public static float roll(int kind, RandomSource random) {
        float usual = KG[kind], spread = KG_SPREAD[kind];
        float kg = usual + (float) random.nextGaussian() * spread / 2.0F;
        kg = Mth.clamp(kg, usual * LIGHTEST, usual + spread);
        return Math.round(kg * 10.0F) / 10.0F;
    }

    /** How much harder (or softer) a fish of {@code kind} weighing {@code kg} pulls than one of its usual size. */
    public static float pull(int kind, float kg) {
        return Mth.clamp(kg / KG[kind], PULL_LEAST, PULL_MOST);
    }

    /** What landing a fish of {@code kind} weighing {@code kg} gives, or empty. */
    public static ItemStack loot(int kind, float kg) {
        ItemStack stack = switch (kind) {
            case FishingReal.FISH_COD -> new ItemStack(Items.COD);
            case FishingReal.FISH_SALMON -> new ItemStack(Items.SALMON);
            case FishingReal.FISH_PUFFERFISH -> new ItemStack(Items.PUFFERFISH);
            case FishingReal.FISH_TROPICAL -> new ItemStack(Items.TROPICAL_FISH);
            case FishingReal.FISH_TUNA -> new ItemStack(TUNA);
            case FishingReal.FISH_SHARK -> new ItemStack(SHARK_MEAT,
                    Mth.clamp(Math.round(kg / SHARK_KG_PER_CUT), SHARK_CUTS_LEAST, SHARK_CUTS_MOST));
            default -> ItemStack.EMPTY;
        };
        if (!stack.isEmpty() && kind != FishingReal.FISH_SHARK) {
            stack.set(WEIGHT, kg);
        }
        return stack;
    }

    /** {@code stack}'s weight in kg, or null if it has none. */
    public static @Nullable Float weight(ItemStack stack) {
        return WEIGHT == null ? null : stack.get(WEIGHT);
    }
}
