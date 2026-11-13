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
    /**
     * (Track C, Reel to Fish's size curve) The curve every weight is drawn on: falling away like an exponential over the
     * kind's range, so half of all fish are in the bottom fifth of it and about one in a hundred in the top tenth.
     */
    static final double SIZE_CURVE = 3.8;
    /**
     * Deep water grows big fish (Andy's ocean pick-list, "Something worth sailing for"): a fish found over at least
     * {@link #DEEP_FROM} blocks of water is heavier, up to {@link #DEEP_MOST} times at {@link #DEEP_FULL} blocks, never past
     * its kind's most; so the heaviest are out at sea.
     */
    static final int DEEP_FROM = 12, DEEP_FULL = 40;
    static final float DEEP_MOST = 1.6F;
    /** A fish's pull on the line grows with its weight (weight / usual), held between these. */
    static final float PULL_LEAST = 0.7F, PULL_MOST = 1.6F;
    /** (Track D) A landed fish stays alive out of the water this long (ticks), to be put back or kept in a keepnet. */
    public static final long ALIVE_TICKS = 20L * 120;
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
    /** (Track D) Everything about a landed fish: what, how big, who caught it and when, and whether it's still alive. */
    public static DataComponentType<Info> CATCH;

    /**
     * A landed fish: its species ({@link Species#id}), weight (kg) and length (cm), the angler's name, the day it was caught,
     * the game time it stays alive until (0 once it's dead, or for one that never was), and a legend's name ("" if not one).
     */
    public record Info(String species, float kg, int cm, String angler, int day, long aliveUntil, String legend) {
        public static final Codec<Info> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("species").forGetter(Info::species),
            Codec.FLOAT.fieldOf("kg").forGetter(Info::kg),
            Codec.INT.fieldOf("cm").forGetter(Info::cm),
            Codec.STRING.optionalFieldOf("angler", "").forGetter(Info::angler),
            Codec.INT.optionalFieldOf("day", 0).forGetter(Info::day),
            Codec.LONG.optionalFieldOf("alive_until", 0L).forGetter(Info::aliveUntil),
            Codec.STRING.optionalFieldOf("legend", "").forGetter(Info::legend)).apply(i, Info::new));

        public @Nullable Species kind() {
            return Species.byId(species);
        }

        public boolean alive(long now) {
            return aliveUntil > now;
        }

        public Info withAliveUntil(long until) {
            return new Info(species, kg, cm, angler, day, until, legend);
        }
    }
    public static Item TUNA, TUNA_STEAK, COOKED_TUNA_STEAK, SHARK_MEAT, COOKED_SHARK_MEAT;

    private FishCatch() {
    }

    static void init() {
        WEIGHT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("fish_weight"),
                DataComponentType.<Float>builder().persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());
        CATCH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("fish_catch"),
                DataComponentType.<Info>builder().persistent(Info.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(Info.CODEC)).build());
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

    /** A weight for a fish of {@code kind}, in kg to a tenth (to a hundredth under a kilo), drawn on the size curve. */
    public static float roll(int kind, RandomSource random) {
        Species sp = Species.of(kind);
        return sp == null ? 1.0F : sp.rollKg(random, false);
    }

    /** A weight between {@code min} and {@code max} on the size curve ({@link #SIZE_CURVE}). */
    static float curve(float min, float max, RandomSource random) {
        double u = random.nextDouble();
        double t = -Math.log(1.0 - u * (1.0 - Math.exp(-SIZE_CURVE))) / SIZE_CURVE;
        float kg = (float) (min + (max - min) * t);
        return kg < 1.0F ? Math.round(kg * 100.0F) / 100.0F : Math.round(kg * 10.0F) / 10.0F;
    }

    /** {@code kg} as it runs over {@code depth} blocks of water ({@link #DEEP_MOST}), held to {@code most}. */
    public static float deeper(float kg, int depth, float most) {
        float deep = 1.0F + (DEEP_MOST - 1.0F) * Mth.clamp((depth - DEEP_FROM) / (float) (DEEP_FULL - DEEP_FROM), 0.0F, 1.0F);
        float grown = Math.min(Math.max(kg, most), kg * deep);
        return grown < 1.0F ? Math.round(grown * 100.0F) / 100.0F : Math.round(grown * 10.0F) / 10.0F;
    }

    /** How many blocks of water lie under {@code pos}, counted down to {@link #DEEP_FULL}. */
    public static int depth(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        net.minecraft.core.BlockPos.MutableBlockPos at = pos.mutable();
        if (!level.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)) {
            at.move(net.minecraft.core.Direction.DOWN); // a bobber floats just above the water
        }
        int n = 0;
        while (n < DEEP_FULL && level.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)) {
            n++;
            at.move(net.minecraft.core.Direction.DOWN);
        }
        return n;
    }

    /** How much harder (or softer) a fish of {@code kind} weighing {@code kg} pulls than one of its usual size. */
    public static float pull(int kind, float kg) {
        Species sp = Species.of(kind);
        return sp == null ? 1.0F : Mth.clamp(kg / sp.usualKg(), PULL_LEAST, PULL_MOST);
    }

    /** What landing a fish of {@code kind} weighing {@code kg} gives, or empty (another mod's fish gives its own drops). */
    public static ItemStack loot(int kind, float kg) {
        Species sp = Species.of(kind);
        ItemStack stack = switch (kind) {
            case FishingReal.FISH_COD -> new ItemStack(Items.COD);
            case FishingReal.FISH_SALMON -> new ItemStack(Items.SALMON);
            case FishingReal.FISH_PUFFERFISH -> new ItemStack(Items.PUFFERFISH);
            case FishingReal.FISH_TROPICAL -> new ItemStack(Items.TROPICAL_FISH);
            case FishingReal.FISH_TUNA -> new ItemStack(TUNA);
            case FishingReal.FISH_SHARK -> new ItemStack(SHARK_MEAT, sharkCuts(kg));
            default -> {
                if (sp != null && sp.sharkish() && sp.big()) {
                    yield new ItemStack(SHARK_MEAT, sharkCuts(kg));
                }
                Item raw = sp == null ? null : FishSpecies.raw(sp);
                yield raw == null ? ItemStack.EMPTY : new ItemStack(raw);
            }
        };
        if (!stack.isEmpty() && !stack.is(SHARK_MEAT)) {
            stack.set(WEIGHT, kg);
        }
        return stack;
    }

    private static int sharkCuts(float kg) {
        return Mth.clamp(Math.round(kg / SHARK_KG_PER_CUT), SHARK_CUTS_LEAST, SHARK_CUTS_MOST);
    }

    /** (Track D) Put the catch's story on {@code stack}: species, weight, length, angler, day, alive for a while, a legend's name. */
    public static void tag(ItemStack stack, Species sp, float kg, String angler, net.minecraft.server.level.ServerLevel level, String legend) {
        int day = (int) (level.getOverworldClockTime() / 24000L);
        stack.set(CATCH, new Info(sp.id, kg, sp.lengthCm(kg), angler, day, level.getGameTime() + ALIVE_TICKS, legend));
        if (!legend.isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(legend)
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }

    /** The catch on {@code stack}, or null. */
    public static @Nullable Info info(ItemStack stack) {
        return CATCH == null ? null : stack.get(CATCH);
    }

    /** {@code stack}'s weight in kg, or null if it has none. */
    public static @Nullable Float weight(ItemStack stack) {
        return WEIGHT == null ? null : stack.get(WEIGHT);
    }
}
