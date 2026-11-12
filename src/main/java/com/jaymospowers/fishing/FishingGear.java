package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * (0.21.63, Track B of docs/Plan-fishing.md) The rest of the tackle box, our own takes on ideas from Reel to Fish, Advanced
 * Fishing, Legendary Fishing and True Fishing (all rights reserved: nothing copied):
 * <ul>
 * <li><b>groundbait</b>: thrown on water, fish round there notice bait {@link #GROUNDBAIT_KEEN} times as often for
 * {@link #GROUNDBAIT_TICKS};</li>
 * <li><b>trolling</b>: a line out behind a moving boat draws fish from farther off, and a trolling lure works best so;</li>
 * <li><b>the fishing magnet</b>: put on the hook like bait, it draws no fish, but now and then snags scrap off the bottom
 * ({@code jaymospowers:gameplay/magnet_fishing});</li>
 * <li><b>the landing net</b>: scoop the fish you're fighting once it's close, or a small fish (tropical, pufferfish)
 * straight out of the water.</li>
 * </ul>
 */
public final class FishingGear {
    /** Groundbait: how much keener the fish get, for how long, how far round where it landed, how far you can throw it. */
    static final float GROUNDBAIT_KEEN = 2.0F;
    static final long GROUNDBAIT_TICKS = 20L * 120;
    static final double GROUNDBAIT_RANGE = 8.0;
    /** Trolling: the boat must move this fast (blocks per tick); fish are drawn from this much farther, and this much keener (more with a trolling lure). */
    static final double TROLL_SPEED = 0.05;
    static final double TROLL_RANGE = 1.5;
    static final float TROLL_KEEN = 1.5F, TROLL_LURE_KEEN = 2.5F;
    /** The magnet: chance each check (4 a second) it snags something. */
    static final float MAGNET_SNAG = 0.006F;
    /** The net reaches the fish you're fighting when it's this close to you; and scoops a free small fish within reach. */
    static final double NET_REACH = 4.0;

    public static final ResourceKey<LootTable> MAGNET_LOOT = ResourceKey.create(Registries.LOOT_TABLE, JaymosPowers.id("gameplay/magnet_fishing"));

    public static Item GROUNDBAIT, FISHING_MAGNET, LANDING_NET;

    private record Spot(ServerLevel level, BlockPos pos, long until) {
    }

    private static final List<Spot> SPOTS = new ArrayList<>();

    private FishingGear() {
    }

    static void init() {
        GROUNDBAIT = Tackle.register("groundbait", new Groundbait(Tackle.props("groundbait")));
        FISHING_MAGNET = Tackle.register("fishing_magnet", new Item(Tackle.props("fishing_magnet").stacksTo(1)));
        LANDING_NET = Tackle.register("landing_net", new LandingNet(Tackle.props("landing_net").durability(128)));
    }

    /** How much keener fish are at {@code pos} for groundbait thrown there. */
    static float groundbait(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        SPOTS.removeIf(s -> s.until < now);
        for (Spot s : SPOTS) {
            if (s.level == level && s.pos.closerThan(pos, GROUNDBAIT_RANGE)) {
                return GROUNDBAIT_KEEN;
            }
        }
        return 1.0F;
    }

    /** {@code player} is trolling: in a boat that's moving. */
    static boolean trolling(Player player) {
        return player.getVehicle() instanceof AbstractBoat boat && boat.getDeltaMovement().horizontalDistance() > TROLL_SPEED;
    }

    /** A magnet on {@code player}'s hook: maybe it snags something this check. True if it did (the hook comes in). */
    static boolean magnet(ServerLevel level, FishingHook hook, ServerPlayer player) {
        if (level.getRandom().nextFloat() >= MAGNET_SNAG) {
            return false;
        }
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, hook.position())
            .withParameter(LootContextParams.TOOL, HookBait.rod(player))
            .withParameter(LootContextParams.THIS_ENTITY, hook)
            .withLuck(player.getLuck())
            .create(LootContextParamSets.FISHING);
        List<ItemStack> items = level.getServer().reloadableRegistries().getLootTable(MAGNET_LOOT).getRandomItems(params);
        for (ItemStack item : items) {
            if (!player.addItem(item)) {
                player.spawnAtLocation(level, item, 1.0F);
            }
        }
        level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 0.8F, 0.8F);
        player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.magnet_snag").withStyle(ChatFormatting.GOLD));
        hook.discard();
        player.fishing = null;
        return true;
    }

    /** Groundbait: thrown at water, it marks the spot. */
    static final class Groundbait extends Item {
        Groundbait(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel server) {
                BlockPos pos = hit.getBlockPos();
                SPOTS.add(new Spot(server, pos, server.getGameTime() + GROUNDBAIT_TICKS));
                server.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.6, 0.1, 0.6, 0.1);
                server.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6F, 1.2F);
                if (player instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.groundbait").withStyle(ChatFormatting.GREEN));
                }
                player.getItemInHand(hand).consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** The landing net. */
    static final class LandingNet extends Item {
        LandingNet(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
            if (!(player instanceof ServerPlayer sp) || !(player.level() instanceof ServerLevel level) || target.distanceTo(player) > NET_REACH) {
                return InteractionResult.PASS;
            }
            int kind = FishBites.kindOf(target);
            if (FishingReal.hooked(sp) == target) {
                // the fish on your own line, close enough to net: landed
                FishingReal.endFight(sp, true);
            } else if (Species.of(kind) != null && Species.of(kind).nettable()
                    || kind == FishingReal.FISH_TROPICAL || kind == FishingReal.FISH_PUFFERFISH) {
                // (Track C) small things a net scoops up: a crab off the bed, a shrimp, a little fish; alive, to keep or put back
                if (FishingReal.onLine(target)) {
                    return InteractionResult.PASS;
                }
                Species kindOf = Species.of(kind);
                float kg = target instanceof WildFish w ? w.weight() : target instanceof Critter c ? c.weight()
                        : FishCatch.roll(kind, level.getRandom());
                ItemStack fish = FishCatch.loot(kind, kg);
                if (fish.isEmpty()) {
                    return InteractionResult.PASS;
                }
                FishCatch.tag(fish, kindOf, kg, player.getName().getString(), level, "");
                FishLog.landed(sp, kindOf, kg, null, level, target.blockPosition(), "");
                if (!player.addItem(fish)) {
                    player.spawnAtLocation(level, fish, 1.0F);
                }
                target.discard();
            } else {
                return InteractionResult.PASS;
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BUCKET_FILL_FISH, SoundSource.PLAYERS, 0.8F, 1.0F);
            stack.hurtAndBreak(1, sp, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            return InteractionResult.SUCCESS;
        }
    }
}
