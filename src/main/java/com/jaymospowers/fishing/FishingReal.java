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
 * plays a tug-of-war to reel it in. Line tension rises as the fish fights; reel it down (hold use) to land the fish,
 * or let it climb to 1 and the line breaks. Stronger fish fight harder and break the line sooner.
 */
public final class FishingReal {
    public static final Identifier ID = JaymosPowers.id("fishing_real");
    public static final AttachmentType<FishFight> FIGHTING = AttachmentRegistry.create(ID,
            builder -> builder.syncWith(FishFight.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private static final Random RANDOM = new Random();
    /** Server side: the fish entity each player is currently fighting, keyed by player uuid. */
    private static final Map<UUID, LivingEntity> HOOKED = new HashMap<>();
    private static final Map<UUID, FishingHook> HOOKS = new HashMap<>();

    /** Base ticks a hooked fish must wait before it can be caught (gives the player time to react). */
    private static final int MIN_FIGHT_TICKS = 60;
    /** Max fight duration before the line breaks from exhaustion (ticks). */
    private static final int MAX_FIGHT_TICKS = 900;
    /** How far tension falls per tick while the player reels. */
    private static final float REEL_RELEASE = 0.015F;
    /** How far progress rises per tick while reeling at safe tension. */
    private static final float REEL_PROGRESS = 0.002F;
    /** Tension above which reeling makes no progress (the fish is pulling too hard). */
    private static final float TOO_TENSE = 0.6F;

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
                reel(player);
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
        long now = player.level().getGameTime();
        if (fish == null || !fish.isAlive() || now - state.startTime() > MAX_FIGHT_TICKS) {
            // fish died, despawned, or the fight ran too long: line breaks
            endFight(player, false);
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.line_broke").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        // the fish pulls on the line every tick
        float tension = Math.min(1.0F, state.tension() + state.strength() * 0.05F);
        float progress = state.progress();
        if (tension >= 1.0F) {
            endFight(player, false);
            player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.line_broke").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        // keep the fish near the player, tugging against the line
        keepNear(player, fish);
        player.setAttached(FIGHTING, state.withTension(tension).withProgress(progress));
    }

    /** The player is reeling: ease tension down and, when the line isn't too tense, bring the fish in. */
    private static void reel(ServerPlayer player) {
        FishFight state = fight(player);
        if (!state.fighting()) {
            return;
        }
        LivingEntity fish = HOOKED.get(player.getUUID());
        if (fish == null || !fish.isAlive()) {
            endFight(player, false);
            return;
        }
        float tension = Math.max(0.0F, state.tension() - REEL_RELEASE);
        float progress = state.progress();
        if (tension < TOO_TENSE) {
            progress = Math.min(1.0F, progress + REEL_PROGRESS);
        }
        player.setAttached(FIGHTING, state.withTension(tension).withProgress(progress));
        if (progress >= 1.0F && player.level().getGameTime() - state.startTime() >= MIN_FIGHT_TICKS) {
            endFight(player, true);
        }
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
    /** Keep the hooked fish near the player, tugging against the line. */
    private static void keepNear(ServerPlayer player, LivingEntity fish) {
        double dx = player.getX() - fish.getX();
        double dz = player.getZ() - fish.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 4.0) {
            double pull = 0.15;
            fish.setDeltaMovement(fish.getDeltaMovement().add(dx / dist * pull, 0.05, dz / dist * pull));
        } else if (dist < 1.5) {
            double push = 0.1;
            fish.setDeltaMovement(fish.getDeltaMovement().add(-dx / Math.max(dist, 0.1) * push, 0.0, -dz / Math.max(dist, 0.1) * push));
        }
    }

    /** Whether a bite should happen for a hook at {@code (x, y, z)} in {@code level} (used by the mixin). */
    public static boolean shouldBite(Level level, double x, double y, double z) {
        if (level.isClientSide() || level.getGameTime() % 4L != 0L) {
            return false;
        }
        return RANDOM.nextFloat() < 0.03F;
    }
}
