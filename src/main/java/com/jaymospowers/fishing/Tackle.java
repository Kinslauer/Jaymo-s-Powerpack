package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.63, Track B of docs/Plan-fishing.md: tackle; ideas from Reel to Fish, Advanced Fishing, Legendary Fishing and True
 * Fishing, all rights reserved, nothing copied; Andy: their rods are "too realistic looking, the function is all i want")
 * Which rod and which bait draw which fish. Every rod is a plain Minecraft-looking rod:
 * <ul>
 * <li>vanilla's fishing rod: any small fish;</li>
 * <li>the cane pole: small fish only, but they come to it quickly;</li>
 * <li>the spinning rod: the one for lures, and the predators that chase them;</li>
 * <li>the ledger rod: bait on the bottom, drawing fish from farther off;</li>
 * <li>the metal rod (the sea rod): anything, tuna and sharks too.</li>
 * </ul>
 * Natural baits are eaten by the fish that takes them; lures stay on the line, but may snap off on a strike
 * ({@link Bait#snap}). Each bait draws some fish more than others ({@link Bait#draws}), and with Track C's new species
 * these tables grow.
 */
public final class Tackle {
    /** A rod: which fish come to it, how far it draws them from and how quickly, and whether lures work on it. */
    public enum Rod {
        VANILLA(1.0, 1.0F, false, FishingReal.FISH_COD, FishingReal.FISH_SALMON, FishingReal.FISH_PUFFERFISH, FishingReal.FISH_TROPICAL),
        POLE(0.7, 1.5F, false, FishingReal.FISH_COD, FishingReal.FISH_PUFFERFISH, FishingReal.FISH_TROPICAL),
        SPINNING(1.2, 1.0F, true, FishingReal.FISH_COD, FishingReal.FISH_SALMON, FishingReal.FISH_PUFFERFISH, FishingReal.FISH_TROPICAL),
        LEDGER(1.5, 0.9F, false, FishingReal.FISH_COD, FishingReal.FISH_SALMON, FishingReal.FISH_PUFFERFISH, FishingReal.FISH_TROPICAL),
        SEA(1.3, 1.0F, true, FishingReal.FISH_COD, FishingReal.FISH_SALMON, FishingReal.FISH_PUFFERFISH, FishingReal.FISH_TROPICAL,
            FishingReal.FISH_TUNA, FishingReal.FISH_SHARK);

        /** How far it draws fish (times FishBites.DRAW_RANGE), and how quickly they notice the bait. */
        public final double range;
        public final float keen;
        /** Lures work on it (on the others a lure draws nothing). */
        public final boolean lures;
        final int[] kinds;

        Rod(double range, float keen, boolean lures, int... kinds) {
            this.range = range;
            this.keen = keen;
            this.lures = lures;
            this.kinds = kinds;
        }

        /**
         * A fish of {@code kind} will come to this rod. The first six kinds keep their lists; (Track C) the new species go
         * by size: the sea rod takes anything a hook can, the others anything that isn't big, and the cane pole only the
         * small ones (as it never took a salmon).
         */
        public boolean takes(int kind) {
            if (kind <= FishingReal.FISH_TUNA) {
                for (int k : this.kinds) {
                    if (k == kind) {
                        return true;
                    }
                }
                return false;
            }
            Species sp = Species.of(kind);
            if (sp == null || !sp.hookable()) {
                return false;
            }
            return switch (this) {
                case SEA -> true;
                case POLE -> !sp.big() && sp.usualKg() <= POLE_MOST_KG;
                default -> !sp.big();
            };
        }

        /** (Track B leftovers) Bait on this rod lies on the bottom (the ledger rod) instead of hanging under a float. */
        public boolean bottom() {
            return this == LEDGER;
        }
    }

    /** The cane pole takes fish that usually weigh this much or less (kg). */
    static final float POLE_MOST_KG = 2.5F;

    /** (Track C) What kind of bait something is, for the new species' tastes ({@link Species#likes}). */
    public enum Cat { WORM, PLANT, MEAT, LIVE, LURE, HEAVY }

    /**
     * A bait or lure: how much it draws each kind of fish ({@code draws[kind]}, by {@code FishingReal.FISH_*}: cod, salmon,
     * pufferfish, tropical, shark, tuna; 0 = not at all), whether it's a lure (stays on, needs a spinning or sea rod),
     * and a lure's chance to snap off on a strike.
     */
    public record Bait(int[] draws, boolean lure, float snap, Cat cat) {
        /** How much it draws a fish of {@code kind}: the first six by their own table, (Track C) the rest by their tastes. */
        public int draw(int kind) {
            if (kind >= 0 && kind < this.draws.length) {
                return this.draws[kind];
            }
            Species sp = Species.of(kind);
            return sp == null ? 0 : sp.likes(this.cat);
        }
    }

    /** Bait an angler put on that isn't in the table (another mod's, through the tag): any small fish, evenly. */
    static final Bait ANY = new Bait(new int[]{2, 2, 1, 1, 0, 0}, false, 0.0F, Cat.WORM);
    /** A lure snaps off on a strike this often. */
    static final float LURE_SNAP = 0.08F, HEAVY_LURE_SNAP = 0.05F;

    public static Item CANE_POLE, SPINNING_ROD, LEDGER_ROD;
    public static Item EARTHWORM, MAGGOTS, DOUGH_BAIT, BOILIE, LUGWORM;
    public static Item SPINNER, WOBBLER, SOFT_SHAD, PIRK, TROLLING_SQUID;
    private static final Map<Item, Bait> BAITS = new HashMap<>();
    private static final Map<Item, Rod> RODS = new HashMap<>();
    static final List<Item> ALL = new ArrayList<>();

    private Tackle() {
    }

    static void init() {
        CANE_POLE = rod("cane_pole", Rod.POLE, 48);
        SPINNING_ROD = rod("spinning_rod", Rod.SPINNING, 96);
        LEDGER_ROD = rod("ledger_rod", Rod.LEDGER, 96);
        // natural baits:                   cod salmon puffer tropical shark tuna
        EARTHWORM = bait("earthworm", new int[]{3, 3, 1, 1, 0, 0}, Cat.WORM);
        MAGGOTS = bait("maggots", new int[]{2, 3, 0, 2, 0, 0}, Cat.WORM);
        DOUGH_BAIT = bait("dough_bait", new int[]{2, 3, 0, 1, 0, 0}, Cat.PLANT);
        BOILIE = bait("boilie", new int[]{1, 3, 0, 0, 0, 0}, Cat.PLANT);
        LUGWORM = bait("lugworm", new int[]{4, 1, 2, 2, 0, 1}, Cat.WORM);
        // lures (spinning or sea rod only)
        SPINNER = lure("spinner", new int[]{1, 4, 0, 0, 0, 1}, LURE_SNAP, Cat.LURE);
        WOBBLER = lure("wobbler", new int[]{2, 3, 0, 0, 0, 1}, LURE_SNAP, Cat.LURE);
        SOFT_SHAD = lure("soft_shad", new int[]{3, 2, 1, 1, 0, 0}, LURE_SNAP, Cat.LURE);
        PIRK = lure("pirk", new int[]{4, 0, 0, 0, 1, 2}, HEAVY_LURE_SNAP, Cat.HEAVY);
        TROLLING_SQUID = lure("trolling_squid", new int[]{0, 0, 0, 0, 2, 4}, LURE_SNAP, Cat.HEAVY);
        // what was already bait, and live bait: a small fish on the hook draws the big ones
        BAITS.put(Items.BREAD, new Bait(new int[]{1, 2, 0, 1, 0, 0}, false, 0.0F, Cat.PLANT));
        BAITS.put(Items.ROTTEN_FLESH, new Bait(new int[]{1, 0, 1, 0, 2, 0}, false, 0.0F, Cat.MEAT));
        BAITS.put(Items.SPIDER_EYE, new Bait(new int[]{0, 2, 1, 0, 0, 0}, false, 0.0F, Cat.MEAT));
        BAITS.put(Items.TROPICAL_FISH, new Bait(new int[]{0, 1, 0, 0, 4, 3}, false, 0.0F, Cat.LIVE));
        RODS.put(Items.FISHING_ROD, Rod.VANILLA);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            for (Item item : ALL) {
                output.accept(item);
            }
        });
    }

    /** (Server start) Kitchen's fish bait, if it's there, draws as it always has: cod and salmon, some small sea fish. */
    static void kitchen() {
        BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymoskitchen", "fish_bait"))
            .ifPresent(item -> BAITS.putIfAbsent(item, new Bait(new int[]{2, 2, 1, 1, 0, 0}, false, 0.0F, Cat.MEAT)));
        // (Track C) Kitchen's corn on the hook, the carp angler's sweetcorn
        BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymoskitchen", "corn"))
            .ifPresent(item -> BAITS.putIfAbsent(item, new Bait(new int[]{1, 3, 0, 0, 0, 0}, false, 0.0F, Cat.PLANT)));
    }

    private static Item rod(String id, Rod kind, int durability) {
        Item item = register(id, new FishingRodItem(props(id).durability(durability).enchantable(1)));
        RODS.put(item, kind);
        return item;
    }

    private static Item bait(String id, int[] draws, Cat cat) {
        Item item = register(id, new Item(props(id)));
        BAITS.put(item, new Bait(draws, false, 0.0F, cat));
        return item;
    }

    private static Item lure(String id, int[] draws, float snap, Cat cat) {
        Item item = register(id, new Item(props(id).stacksTo(16)));
        BAITS.put(item, new Bait(draws, true, snap, cat));
        return item;
    }

    /**
     * (Track C) Live bait: a small fish of ours on the hook draws the predators and the big fish, as the tropical fish
     * always did; a shrimp is bait for most sea fish. Called once the species' items are registered.
     */
    static void liveBait(Item item, Species sp) {
        if (sp == Species.SHRIMP) {
            BAITS.put(item, new Bait(new int[]{3, 1, 1, 1, 1, 1}, false, 0.0F, Cat.MEAT));
        } else if (!sp.predator() && sp.kgMax <= 2.0F && sp.hookable()) {
            BAITS.put(item, new Bait(new int[]{0, 1, 0, 0, 4, 3}, false, 0.0F, Cat.LIVE));
        }
    }

    static Item.Properties props(String id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, JaymosPowers.id(id)));
    }

    static Item register(String id, Item item) {
        Registry.register(BuiltInRegistries.ITEM, JaymosPowers.id(id), item);
        ALL.add(item);
        return item;
    }

    /** The kind of rod {@code stack} is (the metal rod is the sea rod; another mod's rod counts as vanilla's). */
    public static Rod rodOf(ItemStack stack) {
        if (FishingReal.isMetalRodItem(stack)) {
            return Rod.SEA;
        }
        return RODS.getOrDefault(stack.getItem(), Rod.VANILLA);
    }

    /** The rod {@code player} fishes with. */
    public static Rod rodOf(Player player) {
        return rodOf(HookBait.rod(player));
    }

    /** What {@code item} is as bait, or null if it isn't. */
    public static @Nullable Bait baitOf(@Nullable Item item) {
        if (item == null) {
            return null;
        }
        Bait b = BAITS.get(item);
        return b != null ? b : new ItemStack(item).is(HookBait.BAITS) ? ANY : null;
    }

    /** {@code item} is a lure (stays on the line). */
    public static boolean isLure(@Nullable Item item) {
        Bait b = baitOf(item);
        return b != null && b.lure();
    }
}
