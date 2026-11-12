package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Realistic fishing: when a fish bites, a live fish entity is hooked instead of an item dropping, and the player
 * plays a tug-of-war to reel it in. Holding use raises tension and pulls the fish in; easing off lets tension fall
 * while the fish takes line back. Stronger fish surge harder and break the line sooner.
 */
public final class FishingReal {
    public static final Identifier ID = JaymosPowers.id("fishing_real");
    public static final AttachmentType<FishFight> FIGHTING = AttachmentRegistry.create(ID,
            builder -> builder.syncWith(FishFight.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private static final Random RANDOM = new Random();
    /** Server side: the fish entity each player is currently fighting, keyed by player uuid. */
    private static final Map<UUID, LivingEntity> HOOKED = new HashMap<>();
    private static final Map<UUID, FishingHook> HOOKS = new HashMap<>();
    /** Set true for one tick by the ReelInput packet when the player is holding use to reel. */
    private static final Map<UUID, Boolean> REELING = new HashMap<>();
    /** (0.21.62) The weight of the fish each player is fighting (kg, {@link FishCatch}). */
    private static final Map<UUID, Float> WEIGHTS = new HashMap<>();
    /** A hooked fish's own size, put back if it gets away. */
    private static final Map<UUID, Float> BASE_SCALE = new HashMap<>();

    /** The metal fishing rod, forged through Tinkering: stronger line, bigger fish. Set in init(). */
    private static Item METAL_ROD;
    /** Base ticks a hooked fish must wait before it can be caught (gives the player time to react). */
    private static final int MIN_FIGHT_TICKS = 60;
    /** Progress a fresh fight starts with, so the fish cannot get away on the first tick. */
    private static final float START_PROGRESS = 0.3F;
    /** Max fight duration before the line breaks from exhaustion (ticks). Shorter for a shark. */
    private static final int MAX_FIGHT_TICKS = 1800;
    private static final int MAX_FIGHT_TICKS_SHARK = 900;
    private static final int MAX_FIGHT_TICKS_TUNA = 1200;
    /** Tension added per tick while reeling, per unit of fish strength. */
    private static final float TENSION_REEL = 0.05F;
    /** Tension removed per tick while not reeling (the player eases off). */
    private static final float TENSION_EASE = 0.025F;
    /** Progress added per tick while reeling at safe tension. */
    private static final float PROGRESS_REEL = 0.004F;
    /** Progress removed per tick while not reeling, per unit of fish strength (fish takes line back). */
    private static final float PROGRESS_FISH_BACK = 0.0015F;
    /** Tension above which reeling makes no progress (the fish is pulling too hard). */
    private static final float TOO_TENSE = 0.8F;
    /** The metal rod's stronger line stays productive at a higher tension, and its reel brings a fish in faster. */
    private static final float TOO_TENSE_METAL = 1.05F;
    private static final float PROGRESS_REEL_METAL = 0.008F;
    /** Tension at which a wooden rod's line snaps; the metal rod's stronger line takes more. */
    private static final float LINE_BREAK = 1.0F;
    private static final float LINE_BREAK_METAL = 1.25F;
    /** How hard each fish kind fights (0..1): tension added per tick while reeling, line taken back while easing. */
    private static final float STRENGTH_COD = 0.35F;
    private static final float STRENGTH_SALMON = 0.5F;
    private static final float STRENGTH_PUFFERFISH = 0.75F;
    private static final float STRENGTH_TROPICAL = 0.6F;
    private static final float STRENGTH_TUNA = 0.8F;
    private static final float STRENGTH_SHARK = 1.0F;
    /** How big a hooked tuna is (a cod grown to this scale). */
    public static final double TUNA_SCALE = 2.4;
    /** Fish kinds: cod, salmon, pufferfish, tropical fish, shark and tuna (the last two on the metal rod only, at sea: {@link FishWaters}). */
    public static final int FISH_COD = 0, FISH_SALMON = 1, FISH_PUFFERFISH = 2, FISH_TROPICAL = 3, FISH_SHARK = 4, FISH_TUNA = 5;
    /** How hard a hooked shark and a hooked tuna pull the player toward them while they reel (blocks per tick). */
    public static final double PULL_PLAYER = 0.04;
    public static final double PULL_PLAYER_TUNA = 0.02;
    /** Keep the fish within this many blocks of the hook, tugging it back on the line. */
    private static final double KEEP_NEAR_DIST = 3.0;
    /** How hard the line pulls the fish back toward the hook. */
    private static final double KEEP_NEAR_PULL = 0.12;
    /** How much the line lifts the fish up toward the surface. */
    private static final double KEEP_NEAR_LIFT = 0.05;

    private FishingReal() {
    }

    public static void init() {
        METAL_ROD = Registry.register(BuiltInRegistries.ITEM, JaymosPowers.id("metal_fishing_rod"),
                new MetalFishingRodItem(new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, JaymosPowers.id("metal_fishing_rod")))
                        .durability(256)));
        FishCatch.init();
        HookBait.init();
        Skipjack.init();
        // (0.21.64) Track B: rods, baits, lures and the rest of the tackle box
        Tackle.init();
        FishingGear.init();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> Tackle.kitchen());
        FishingPayloads.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tick(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> endFight(handler.player, false));
        // reeling is driven by the client's ReelInput packet
        ServerPlayNetworking.registerGlobalReceiver(FishingPayloads.ReelInput.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (payload.reeling()) {
                REELING.put(player.getUUID(), true);
            }
        });
    }

    /** The fight state for {@code player}, or {@link FishFight#NONE} if they aren't fighting a fish. */
    public static FishFight fight(ServerPlayer player) {
        FishFight state = player.getAttached(FIGHTING);
        return state == null ? FishFight.NONE : state;
    }

    /** {@code fish} is on someone's line now. */
    public static boolean onLine(LivingEntity fish) {
        return HOOKED.containsValue(fish);
    }

    /** The fish entity hooked by {@code player}, or null. */
    @Nullable
    public static LivingEntity hooked(ServerPlayer player) {
        return HOOKED.get(player.getUUID());
    }

    private static void tick(ServerPlayer player) {
        FishFight state = fight(player);
        if (!state.fighting()) {
            return;
        }
        LivingEntity fish = HOOKED.get(player.getUUID());
        FishingHook hook = HOOKS.get(player.getUUID());
        long now = player.level().getGameTime();
        int fightLimit = fightLimit(state);

        // End the fight if the hook is gone (player switched item, hook removed).
        if (hook == null || hook.isRemoved()) {
            endFight(player, false);
            return;
        }

        if (fish == null || !fish.isAlive() || now - state.startTime() > fightLimit) {
            // fish died, despawned, or the fight ran too long: line breaks
            endFight(player, false);
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.line_broke").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        boolean reeling = REELING.remove(player.getUUID()) != null;
        float tension = state.tension();
        float progress = state.progress();
        boolean metalRod = isMetalRod(player);
        float breakLimit = metalRod ? LINE_BREAK_METAL : LINE_BREAK;
        float tooTense = metalRod ? TOO_TENSE_METAL : TOO_TENSE;
        // (Track A) the fish surges in runs, left or right, now and then jumping (FishRun)
        int run = FishRun.tick(player, fish, state, now);

        if (reeling) {
            // reeling: tension rises (pulling hard), fish comes in; reeling into a run spikes it and gains nothing
            tension += state.strength() * TENSION_REEL * (run != 0 ? FishRun.RUN_REEL_SPIKE : 1.0F);
            if (run == 0 && tension < tooTense) {
                progress = Math.min(1.0F, progress + (metalRod ? PROGRESS_REEL_METAL : PROGRESS_REEL) * FishRun.reelFactor(player));
            }
            if (metalRod && (state.fishKind() == FISH_SHARK || state.fishKind() == FISH_TUNA)) {
                // a big fish on the line pulls the player toward it
                pullPlayer(player, fish, state.fishKind() == FISH_SHARK ? PULL_PLAYER : PULL_PLAYER_TUNA);
            }
        } else if (run != 0) {
            // easing off in a run: the fish still pulls and takes line
            tension += state.strength() * FishRun.RUN_PULL;
            progress -= state.strength() * FishRun.RUN_LINE_OUT;
        } else {
            // easing off: tension falls, fish takes line back
            tension = Math.max(0.0F, tension - TENSION_EASE);
            progress -= state.strength() * PROGRESS_FISH_BACK;
        }

        // (Track A) the rod's drag lets line slip past its setting instead of the line taking the strain
        float[] line = {progress};
        tension = FishRun.slip(player, state, tension, breakLimit, line, now);
        progress = Math.max(0.0F, line[0]);

        if (tension >= breakLimit) {
            endFight(player, false);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.LEAD_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.3F);
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.line_broke").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        if (progress >= 1.0F && now - state.startTime() >= MIN_FIGHT_TICKS) {
            if ((state.fishKind() == FISH_SHARK || state.fishKind() == FISH_TUNA) && !metalRod) {
                // a big fish can only be landed on the metal rod (the rod was swapped mid-fight)
                endFight(player, false);
                player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.too_big").withStyle(net.minecraft.ChatFormatting.RED));
                return;
            }
            endFight(player, true);
            return;
        }

        if (progress <= 0.0F) {
            // fish got away
            endFight(player, false);
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.fish_got_away").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        // keep the fish near the hook/water, tugging against the line, so it is not dragged onto land
        keepNear(player, fish, hook);
        player.setAttached(FIGHTING, state.with(tension, progress, run, breakLimit));
    }

    /**
     * (Track A) A fish of {@code kind} has reached {@code player}'s hook: it nibbles, then takes it, and a strike in time
     * starts the fight ({@link FishBite}). Called by the hook mixin in place of {@link #startFight}.
     */
    public static void fishAtHook(ServerPlayer player, int kind, LivingEntity fish, FishingHook hook) {
        FishBite.begin(player, kind, fish, hook);
    }

    /** Start a fight: hook a live fish on the player's line. Called by {@link FishBite#strike} when the player strikes. */
    public static void startFight(ServerPlayer player, int fishKind, LivingEntity fish, FishingHook hook) {
        float strength = switch (fishKind) {
            case 0 -> STRENGTH_COD; // cod: steady
            case 1 -> STRENGTH_SALMON; // salmon: stronger
            case 2 -> STRENGTH_PUFFERFISH; // pufferfish: erratic, strong
            case FISH_SHARK -> STRENGTH_SHARK; // shark: hardest
            case FISH_TUNA -> STRENGTH_TUNA; // tuna: big and strong
            default -> STRENGTH_TROPICAL; // tropical: varied
        };
        if (fish instanceof net.minecraft.world.entity.Mob mob && fishKind == FISH_SHARK) {
            // a real shark, held on the line: no AI until it's landed or breaks free
            mob.setNoAi(true);
        }
        // a heavy fish fights harder, and looks it
        float kg = FishCatch.roll(fishKind, player.getRandom(), FishCatch.depth(player.level(), hook.blockPosition()));
        strength *= FishCatch.pull(fishKind, kg);
        if (fishKind != FISH_SHARK) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance scale =
                    fish.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            if (scale != null) {
                BASE_SCALE.put(fish.getUUID(), (float) scale.getBaseValue());
                scale.setBaseValue(scale.getBaseValue() * Math.cbrt(FishCatch.pull(fishKind, kg)));
            }
        }
        player.setAttached(FIGHTING, new FishFight(fishKind, strength, 0.0F, START_PROGRESS, player.level().getGameTime()));
        HOOKED.put(player.getUUID(), fish);
        HOOKS.put(player.getUUID(), hook);
        WEIGHTS.put(player.getUUID(), kg);
        // (0.21.61, Andy: "tideborn will see fishing as an attack, and doing in their havens is seen as a crime")
        com.jaymospowers.compat.KingdomsCompat.fished(player, hook.blockPosition());
        player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.fish_on").withStyle(net.minecraft.ChatFormatting.GOLD));
        if (fishKind == FISH_TUNA) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.tuna_hooked").withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        if (fishKind == FISH_SHARK) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.shark_hooked").withStyle(net.minecraft.ChatFormatting.RED));
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BUCKET_FILL_FISH, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    /** End the fight: if caught, drop the fish item; otherwise let it swim off. */
    public static void endFight(ServerPlayer player, boolean caught) {
        int kindFought = fight(player).fishKind();
        player.setAttached(FIGHTING, FishFight.NONE);
        REELING.remove(player.getUUID());
        Float weighed = WEIGHTS.remove(player.getUUID());
        LivingEntity fish = HOOKED.remove(player.getUUID());
        FishingHook hook = HOOKS.remove(player.getUUID());
        if (fish != null) {
            if (caught) {
                // (0.21.62) the catch is what was fought, at its weight: a whole tuna, a shark cut up for meat
                float kg = weighed != null ? weighed : FishCatch.roll(kindFought, player.getRandom());
                ItemStack loot = FishCatch.loot(kindFought, kg);
                if (!loot.isEmpty()) {
                    Component name = loot.getHoverName();
                    if (!player.addItem(loot)) {
                        player.spawnAtLocation(player.level(), loot, 1.0F);
                    }
                    player.sendOverlayMessage(Component.translatable(kindFought == FISH_SHARK
                                    ? "message.jaymospowers.fishing.shark_landed" : "message.jaymospowers.fishing.caught_weighed",
                            name, String.format(java.util.Locale.ROOT, "%.1f", kg)).withStyle(net.minecraft.ChatFormatting.GREEN));
                }
                // a landed fish is gone from the water
                fish.discard();
            } else if (fish instanceof net.minecraft.world.entity.Mob mob) {
                // one that got away is a real fish still: it swims off as it was
                if (kindFought == FISH_SHARK) {
                    mob.setNoAi(false);
                }
                net.minecraft.world.entity.ai.attributes.AttributeInstance scale =
                        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
                Float base = BASE_SCALE.get(fish.getUUID());
                if (scale != null && base != null) {
                    scale.setBaseValue(base);
                }
            }
            BASE_SCALE.remove(fish.getUUID());
        }
        if (hook != null) {
            hook.discard();
            player.fishing = null;
        }
    }

    /** Keep the hooked fish near the hook/water, tugging against the line, so it is not dragged onto land. */
    private static void keepNear(ServerPlayer player, LivingEntity fish, FishingHook hook) {
        double dx = hook.getX() - fish.getX();
        double dz = hook.getZ() - fish.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (fish instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi()) {
            // a shark held without its AI is drawn back by hand
            if (dist > KEEP_NEAR_DIST) {
                fish.setPos(fish.getX() + dx / dist * KEEP_NEAR_PULL, fish.getY(), fish.getZ() + dz / dist * KEEP_NEAR_PULL);
            }
            return;
        }
        if (dist > KEEP_NEAR_DIST) {
            double pull = KEEP_NEAR_PULL;
            fish.setDeltaMovement(fish.getDeltaMovement().add(dx / dist * pull, KEEP_NEAR_LIFT, dz / dist * pull));
        }
    }

    /** A big fish on a metal rod pulls the player toward it while they reel. */
    private static void pullPlayer(ServerPlayer player, LivingEntity fish, double force) {
        Vec3 pull = new Vec3(fish.getX() - player.getX(), 0.0D, fish.getZ() - player.getZ());
        if (pull.lengthSqr() > 1.0E-6D) {
            player.addDeltaMovement(pull.normalize().scale(force));
        }
    }

    /** The longest a fight can run before exhaustion snaps the line. */
    private static int fightLimit(FishFight state) {
        return switch (state.fishKind()) {
            case FISH_SHARK -> MAX_FIGHT_TICKS_SHARK;
            case FISH_TUNA -> MAX_FIGHT_TICKS_TUNA;
            default -> MAX_FIGHT_TICKS;
        };
    }

    /** True while the player's main hand holds the metal fishing rod. */
    public static boolean isMetalRod(ServerPlayer player) {
        return METAL_ROD != null && player.getItemInHand(InteractionHand.MAIN_HAND).is(METAL_ROD);
    }

    /** Whether {@code stack} is the metal fishing rod. */
    public static boolean isMetalRodItem(ItemStack stack) {
        return METAL_ROD != null && stack.is(METAL_ROD);
    }
}
