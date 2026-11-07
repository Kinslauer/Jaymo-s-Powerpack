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
import net.minecraft.core.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
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

    /** The metal fishing rod, forged through Tinkering: stronger line, bigger fish. Set in init(). */
    private static Item METAL_ROD;
    /** Base ticks a hooked fish must wait before it can be caught (gives the player time to react). */
    private static final int MIN_FIGHT_TICKS = 60;
    /** Progress a fresh fight starts with, so the fish cannot get away on the first tick. */
    private static final float START_PROGRESS = 0.3F;
    /** Max fight duration before the line breaks from exhaustion (ticks). Shorter for a shark. */
    private static final int MAX_FIGHT_TICKS = 1800;
    private static final int MAX_FIGHT_TICKS_SHARK = 400;
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
    /** Tension at which a wooden rod's line snaps; the metal rod's stronger line takes more. */
    private static final float LINE_BREAK = 1.0F;
    private static final float LINE_BREAK_METAL = 1.25F;
    /** Chance a hooked tick produces a bite (checked 4 times a second). */
    private static final float BITE_CHANCE = 0.03F;
    /** How hard each fish kind fights (0..1): tension added per tick while reeling, line taken back while easing. */
    private static final float STRENGTH_COD = 0.35F;
    private static final float STRENGTH_SALMON = 0.5F;
    private static final float STRENGTH_PUFFERFISH = 0.75F;
    private static final float STRENGTH_TROPICAL = 0.6F;
    private static final float STRENGTH_SHARK = 1.0F;
    /** Fish kinds: cod, salmon, pufferfish, tropical fish, and shark (metal rod only). */
    public static final int FISH_COD = 0, FISH_SALMON = 1, FISH_PUFFERFISH = 2, FISH_TROPICAL = 3, FISH_SHARK = 4;
    /** Out of 100: chance a bite on the metal rod is a shark instead of an ordinary fish. */
    public static final int CHANCE_SHARK = 15;
    /** How hard a hooked shark pulls the player toward it while they reel (blocks per tick). */
    public static final double PULL_PLAYER = 0.04;
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

        if (reeling) {
            // reeling: tension rises (pulling hard), fish comes in
            tension = Math.min(breakLimit, tension + state.strength() * TENSION_REEL);
            if (tension < TOO_TENSE) {
                progress = Math.min(1.0F, progress + PROGRESS_REEL);
            }
            if (metalRod && fish != null && state.fishKind() == FISH_SHARK) {
                // a shark on the line pulls the player toward it
                pullPlayer(player, fish);
            }
        } else {
            // easing off: tension falls, fish takes line back
            tension = Math.max(0.0F, tension - TENSION_EASE);
            progress = Math.max(0.0F, progress - state.strength() * PROGRESS_FISH_BACK);
        }

        if (tension >= breakLimit) {
            endFight(player, false);
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.line_broke").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        if (progress >= 1.0F && now - state.startTime() >= MIN_FIGHT_TICKS) {
            if (state.fishKind() == FISH_SHARK) {
                // too big to land: it breaks free
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
        player.setAttached(FIGHTING, state.withTension(tension).withProgress(progress));
    }

    /** Start a fight: hook a live fish on the player's line. Called by the mixin when a bite happens. */
    public static void startFight(ServerPlayer player, int fishKind, LivingEntity fish, FishingHook hook) {
        float strength = switch (fishKind) {
            case 0 -> STRENGTH_COD; // cod: steady
            case 1 -> STRENGTH_SALMON; // salmon: stronger
            case 2 -> STRENGTH_PUFFERFISH; // pufferfish: erratic, strong
            default -> STRENGTH_TROPICAL; // tropical: varied
        };
        player.setAttached(FIGHTING, new FishFight(fishKind, strength, 0.0F, START_PROGRESS, player.level().getGameTime()));
        HOOKED.put(player.getUUID(), fish);
        HOOKS.put(player.getUUID(), hook);
        player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.fish_on").withStyle(net.minecraft.ChatFormatting.GOLD));
        if (fishKind == FISH_SHARK) {
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.shark_hooked").withStyle(net.minecraft.ChatFormatting.RED));
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BUCKET_FILL_FISH, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    /** End the fight: if caught, drop the fish item; otherwise let it swim off. */
    public static void endFight(ServerPlayer player, boolean caught) {
        player.setAttached(FIGHTING, FishFight.NONE);
        REELING.remove(player.getUUID());
        LivingEntity fish = HOOKED.remove(player.getUUID());
        FishingHook hook = HOOKS.remove(player.getUUID());
        if (fish != null) {
            if (caught) {
                String kind = BuiltInRegistries.ENTITY_TYPE.getKey(fish.getType()).getPath();
                ItemStack loot = switch (kind) {
                    case "cod" -> new ItemStack(Items.COD);
                    case "salmon" -> new ItemStack(Items.SALMON);
                    case "pufferfish" -> new ItemStack(Items.PUFFERFISH);
                    case "tropical_fish" -> new ItemStack(Items.TROPICAL_FISH);
                    default -> ItemStack.EMPTY;
                };
                if (!loot.isEmpty()) {
                    player.addItem(loot);
                    player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.caught").withStyle(net.minecraft.ChatFormatting.GREEN));
                }
            }
            fish.discard();
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
        if (dist > KEEP_NEAR_DIST) {
            double pull = KEEP_NEAR_PULL;
            fish.setDeltaMovement(fish.getDeltaMovement().add(dx / dist * pull, KEEP_NEAR_LIFT, dz / dist * pull));
        }
    }

    /** A shark on a metal rod pulls the player toward it while they reel. */
    private static void pullPlayer(ServerPlayer player, LivingEntity fish) {
        Vec3 pull = new Vec3(fish.getX() - player.getX(), 0.0D, fish.getZ() - player.getZ());
        if (pull.lengthSqr() > 1.0E-6D) {
            player.addDeltaMovement(pull.normalize().scale(PULL_PLAYER));
        }
    }

    /** The longest a fight can run before exhaustion snaps the line. */
    private static int fightLimit(FishFight state) {
        return state.fishKind() == FISH_SHARK ? MAX_FIGHT_TICKS_SHARK : MAX_FIGHT_TICKS;
    }

    /** True while the player's main hand holds the metal fishing rod. */
    public static boolean isMetalRod(ServerPlayer player) {
        return METAL_ROD != null && player.getItemInHand(InteractionHand.MAIN_HAND).is(METAL_ROD);
    }

    /** Whether {@code stack} is the metal fishing rod. */
    public static boolean isMetalRodItem(ItemStack stack) {
        return METAL_ROD != null && stack.is(METAL_ROD);
    }

    /** Whether a bite should happen for a hook at {@code (x, y, z)} in {@code level} (used by the mixin). */
    public static boolean shouldBite(Level level, double x, double y, double z) {
        if (level.isClientSide() || level.getGameTime() % 4L != 0L) {
            return false;
        }
        return RANDOM.nextFloat() < BITE_CHANCE;
    }
}
