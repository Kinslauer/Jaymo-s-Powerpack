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
 * will come to it. Sneak and use the rod with bait in the other hand (anything in {@code #jaymospowers:fishing_bait}:
 * Jaymo's Kitchen's fish bait, bread, rotten flesh, a spider eye): one goes on the hook ({@link #BAIT} on the rod),
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
            if (!player.isShiftKeyDown() || player.fishing != null) {
                return InteractionResult.PASS;
            }
            ItemStack rod = player.getItemInHand(hand);
            if (!isRod(rod)) {
                return InteractionResult.PASS;
            }
            ItemStack bait = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!bait.is(BAITS)) {
                return InteractionResult.PASS;
            }
            if (Tackle.isLure(bait.getItem()) && !Tackle.rodOf(rod).lures) {
                // (0.21.63) a lure needs a rod that can work it
                if (player instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.lure_needs_rod").withStyle(ChatFormatting.GRAY));
                }
                return InteractionResult.FAIL;
            }
            if (rod.has(BAIT)) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.already_baited").withStyle(ChatFormatting.GRAY));
                }
                return InteractionResult.FAIL;
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

    /** A fishing rod: vanilla's or the metal rod (or any other mod's rod that is a FishingRodItem). */
    public static boolean isRod(ItemStack stack) {
        return stack.getItem() instanceof FishingRodItem || FishingReal.isMetalRodItem(stack);
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
