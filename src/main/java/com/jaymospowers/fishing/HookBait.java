package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.61, Andy: "you should have to manually and visually bait the hook") A hook has to be baited by hand before a fish
 * will come to it. The first use of a bare rod baits it (no sneaking) with bait from the other hand, else from the pack
 * (anything in {@code #jaymospowers:fishing_bait}: Jaymo's Kitchen's fish bait, bread, rotten flesh, a spider eye); the next
 * use casts. One goes on the hook ({@link #BAIT} on the rod),
 * and it hangs under the bobber when you cast (drawn by the client's hook renderer mixin). A fish that takes the hook
 * eats it, caught or not.
 */
public final class HookBait {
    /** What's on the hook: the bait item's id. */
    public static DataComponentType<Identifier> BAIT;
    public static final TagKey<Item> BAITS = TagKey.create(Registries.ITEM, JaymosPowers.id("fishing_bait"));

    private HookBait() {
    }

    static void init() {
        BAIT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("hook_bait"),
                DataComponentType.<Identifier>builder().persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC).build());
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player.fishing != null) {
                return InteractionResult.PASS;
            }
            ItemStack rod = player.getItemInHand(hand);
            if (!isRod(rod)) {
                return InteractionResult.PASS;
            }
            ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (rod.has(BAIT)) {
                // a baited hook: use casts it (sneak + use turns the drag); sneak + use with bait in the other hand says so
                if (!player.isShiftKeyDown() || !other.is(BAITS)) {
                    return InteractionResult.PASS;
                }
                if (player instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.already_baited").withStyle(ChatFormatting.GRAY));
                }
                return InteractionResult.FAIL;
            }
            // (Andy: "the bait should be applied to the hook with the first right click") a bare hook takes bait on the first
            // use, no sneaking: from the other hand first, else the first bait in the pack; the next use casts. Sneaking takes
            // it only from the other hand, so sneak + use still turns the drag.
            boolean wrongLure = other.is(BAITS) && !fits(rod, other);
            ItemStack bait = fits(rod, other) ? other : player.isShiftKeyDown() ? ItemStack.EMPTY : fromPack(player, rod);
            if (bait.isEmpty()) {
                if (wrongLure && player instanceof ServerPlayer sp) {
                    // (0.21.64) a lure needs a rod that can work it; cast bare rather than block the rod
                    sp.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.lure_needs_rod").withStyle(ChatFormatting.GRAY));
                }
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                rod.set(BAIT, BuiltInRegistries.ITEM.getKey(bait.getItem()));
                Component name = bait.getHoverName();
                if (!player.hasInfiniteMaterials()) {
                    bait.shrink(1);
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 1.4F);
                if (player instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.baited", name).withStyle(ChatFormatting.GREEN));
                }
            }
            return InteractionResult.SUCCESS;
        });
    }

    /** {@code bait} can go on {@code rod}'s hook: it's bait, and a lure only on a rod that can work it. */
    private static boolean fits(ItemStack rod, ItemStack bait) {
        return !bait.isEmpty() && bait.is(BAITS) && (!Tackle.isLure(bait.getItem()) || Tackle.rodOf(rod).lures);
    }

    /** The first bait in {@code player}'s pack (hotbar first) that fits {@code rod}, or empty. */
    private static ItemStack fromPack(Player player, ItemStack rod) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack != rod && fits(rod, stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** A fishing rod: vanilla's or the metal rod (or any other mod's rod that is a FishingRodItem). */
    public static boolean isRod(ItemStack stack) {
        return stack.getItem() instanceof FishingRodItem || stack.getItem() instanceof MetalFishingRodItem;
    }

    /** The rod {@code player} fishes with (main hand first), or empty. */
    public static ItemStack rod(Player player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (isRod(main)) {
            return main;
        }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        return isRod(off) ? off : ItemStack.EMPTY;
    }

    /** The bait on {@code player}'s hook, or null if it's bare. */
    public static @Nullable Item bait(Player player) {
        Identifier id = BAIT == null ? null : rod(player).get(BAIT);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /** A fish took the hook: the bait is gone. */
    public static void eaten(Player player) {
        ItemStack rod = rod(player);
        if (!rod.isEmpty()) {
            rod.remove(BAIT);
        }
    }
}
