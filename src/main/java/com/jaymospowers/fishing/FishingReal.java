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
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
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

    /** Base ticks a hooked fish must wait before it can be caught (gives the player time to react). */
    private static final int MIN_FIGHT_TICKS = 60;
    /** Max fight duration before the line breaks from exhaustion (ticks). */
    private static final int MAX_FIGHT_TICKS = 900;
    /** Tension added per tick while reeling, per unit of fish strength. */
    private static final float TENSION_REEL = 0.05F;
    /** Tension removed per tick while not reeling (the player eases off). */
    private static final float TENSION_EASE = 0.025F;
    /** Progress added per tick while reeling at safe tension. */
    private static final float PROGRESS_REEL = 0.004F;
    /** Progress removed per tick while not reeling, per unit of fish strength (fish takes line back). */
    private static final float PROGRESS_FISH_BACK = 0.003F;
    /** Tension above which reeling makes no progress (the fish is pulling too hard). */
    private static final float TOO_TENSE = 0.8F;
    /** Tension at which the line snaps. */
    private static final float LINE_BREAK = 1.0F;
    /** Chance a hooked tick produces a bite (checked 4 times a second). */
    private static final float BITE_CHANCE = 0.03F;

    private FishingReal() {
    }

    public static void init() {
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

        // End the fight if the hook is gone (player switched item, hook removed).
        if (hook == null || hook.isRemoved()) {
            endFight(player, false);
            return;
        }

        if (fish == null || !fish.isAlive() || now - state.startTime() > MAX_FIGHT_TICKS) {
            // fish died, despawned, or the fight ran too long: line breaks
            endFight(player, false);
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.line_broke").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        boolean reeling = REELING.remove(player.getUUID()) != null;
        float tension = state.tension();
        float progress = state.progress();

        if (reeling) {
            // reeling: tension rises (pulling hard), fish comes in
            tension = Math.min(1.0F, tension + state.strength() * TENSION_REEL);
            if (tension < TOO_TENSE) {
                progress = Math.min(1.0F, progress + PROGRESS_REEL);
            }
        } else {
            // easing off: tension falls, fish takes line back
            tension = Math.max(0.0F, tension - TENSION_EASE);
            progress = Math.max(0.0F, progress - state.strength() * PROGRESS_FISH_BACK);
        }

        if (tension >= LINE_BREAK) {
            endFight(player, false);
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.line_broke").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }

        if (progress >= 1.0F && now - state.startTime() >= MIN_FIGHT_TICKS) {
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
            case 0 -> 0.35F; // cod: steady
            case 1 -> 0.5F;  // salmon: stronger
            case 2 -> 0.75F; // pufferfish: erratic, strong
            default -> 0.6F; // tropical: varied
        };
        player.setAttached(FIGHTING, new FishFight(fishKind, strength, 0.0F, 0.0F, player.level().getGameTime()));
        HOOKED.put(player.getUUID(), fish);
        HOOKS.put(player.getUUID(), hook);
        player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.fish_on").withStyle(net.minecraft.ChatFormatting.GOLD));
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
        }
    }

    /** Keep the hooked fish near the hook/water, tugging against the line, so it is not dragged onto land. */
    private static void keepNear(ServerPlayer player, LivingEntity fish, FishingHook hook) {
        double dx = hook.getX() - fish.getX();
        double dz = hook.getZ() - fish.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 3.0) {
            double pull = 0.12;
            fish.setDeltaMovement(fish.getDeltaMovement().add(dx / dist * pull, 0.05, dz / dist * pull));
        }
    }

    /** Whether a bite should happen for a hook at {@code (x, y, z)} in {@code level} (used by the mixin). */
    public static boolean shouldBite(Level level, double x, double y, double z) {
        if (level.isClientSide() || level.getGameTime() % 4L != 0L) {
            return false;
        }
        return RANDOM.nextFloat() < BITE_CHANCE;
    }
}
