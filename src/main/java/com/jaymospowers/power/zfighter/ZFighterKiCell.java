package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.KingdomsMoney;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.chrono.future.FutureMachines;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;
import net.minecraft.core.Registry;

/**
 * Ki Cell: a Tinkerer craft that gives back a chunk of ki.
 * Consumable item crafted by the Tinkerer ({@link ZFighterTinkererGadgets}).
 */
public final class ZFighterKiCell {
    /** Ki restored on use. */
    public static final float KI_RESTORE = 40.0F;
    /** Cooldown between uses (seconds). */
    public static final int COOLDOWN_SECONDS = 8;
    /** The item. */
    public static Item KI_CELL;

    private ZFighterKiCell() {
    }

    static void init() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("ki_cell"));
        KI_CELL = Registry.register(BuiltInRegistries.ITEM, key, new KiCellItem(new Item.Properties().setId(key)
                .stacksTo(16).rarity(Rarity.UNCOMMON)
                .food(new FoodProperties.Builder().nutrition(0).saturationModifier(0.0F).alwaysEdible().build(),
                        Consumables.defaultFood().consumeSeconds(0.5F).build())
                .useCooldown(COOLDOWN_SECONDS)));
        CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(KI_CELL));
    }

    static void clear() {
    }

    /** The Ki Cell item: restores ki when eaten. */
    private static class KiCellItem extends Item {
        KiCellItem(Properties properties) {
            super(properties);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
                if (com.jaymospowers.core.Powers.has(player, ZFighterPower.INSTANCE)) {
                    ZFighterPower.Live live = ZFighterPower.live(player);
                    float before = live.ki;
                    live.ki = Math.min(ZFighterPower.maxKi(player), live.ki + KI_RESTORE);
                    float gained = live.ki - before;
                    if (gained > 0.0F) {
                        player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ki_cell.restored", (int)gained).withStyle(net.minecraft.ChatFormatting.AQUA));
                        server.sendParticles(MagicFx.wisp(ZFighterKiDrinkingBarrier.COLOR, 1.5F), true, false, player.getX(), player.getY() + 1.0, player.getZ(),
                                16, 0.3, 0.3, 0.3, 0.05);
                        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
                    } else {
                        player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ki_cell.full").withStyle(net.minecraft.ChatFormatting.GRAY));
                    }
                } else {
                    player.getFoodData().setFoodLevel(Math.min(20, player.getFoodData().getFoodLevel() + 4));
                    server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6F, 1.2F);
                }
            }
            return left;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer server) {
                if (com.jaymospowers.core.Powers.has(server, ZFighterPower.INSTANCE)) {
                    ZFighterPower.Live live = ZFighterPower.live(server);
                    if (live.ki < ZFighterPower.maxKi(server)) {
                        return super.use(level, player, hand);
                    }
                } else if (player.getFoodData().needsFood()) {
                    return super.use(level, player, hand);
                }
            }
            return InteractionResult.PASS;
        }
    }
}