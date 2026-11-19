package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A sighted amethyst shard: sneak-use on a block to mark a place. The shard stores the dimension and position,
 * shows a faint glow, and the anvil name is shown over the picture in the orb. Never used up; sneak-use again to move it.
 */
public final class SightedShard {
    static final String TARGET = "jaymoskingdoms_sighted_target", NAME = "jaymoskingdoms_sighted_name";
    public static Item ITEM;

    private SightedShard() {
    }

    static void register() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("sighted_shard"));
        ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON).stacksTo(1)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.AMETHYST_SHARD, ITEM));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer p && p.isCrouching() && p.getItemInHand(hand).is(Items.AMETHYST_SHARD)) {
                return mark(server, p, hand, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer p && p.isCrouching() && p.getItemInHand(hand).is(ITEM)) {
                ItemStack stack = p.getItemInHand(hand);
                if (SightedShard.targetOf(stack) != null) {
                    // Clear the target (shift-right-click in air to reset)
                    CustomData.update(stack.getComponentsPatch(), stack, tag -> {
                        tag.remove(TARGET);
                        tag.remove(NAME);
                    });
                    p.sendSystemMessage(Component.translatable("item.jaymoskingdoms.sighted_shard.cleared").withStyle(ChatFormatting.GRAY));
                    p.swing(InteractionHand.MAIN_HAND, true);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }

    /** A sighted shard storing {@code target}. */
    public static ItemStack of(ScryingOrb.Target target) {
        ItemStack stack = new ItemStack(ITEM);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.put(TARGET, ScryingOrb.Target.CODEC, target);
            tag.putString(NAME, target.name());
        });
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.sighted_shard.of", target.name()).withStyle(s -> s.withItalic(false)));
        return stack;
    }

    /** The target stored in {@code stack}, or null. */
    public static @Nullable ScryingOrb.Target targetOf(ItemStack stack) {
        if (!stack.is(ITEM)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        Optional<ScryingOrb.Target> target = data.copyTag().read(TARGET, ScryingOrb.Target.CODEC);
        return target.orElse(null);
    }

    private static boolean mark(ServerLevel level, ServerPlayer player, InteractionHand hand, BlockPos pos) {
        ItemStack stack = player.getItemInHand(hand);
        String dim = level.dimension().location().toString();
        String name = Component.translatable("item.jaymoskingdoms.sighted_shard.default_name",
                Component.translatable(level.dimensionType().effects().location().getPath()),
                pos.getX(), pos.getY(), pos.getZ()).getString();
        ItemStack sighted = of(ScryingOrb.Target.place(dim, pos.immutable(), name));
        if (!stack.isEmpty()) {
            stack.shrink(1);
        }
        if (!player.getInventory().add(sighted)) {
            player.drop(sighted, false);
        }
        level.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6F, 1.2F);
        level.sendParticles(Fx.mote(0x8A5CFF, 0.6F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.02);
        player.sendSystemMessage(Component.translatable("item.jaymoskingdoms.sighted_shard.marked", name).withStyle(ChatFormatting.AQUA));
        return true;
    }
}