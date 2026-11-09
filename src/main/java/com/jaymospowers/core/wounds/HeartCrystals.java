package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ore Hearts (Scaling Health's crystal hearts, MIT): rare heart crystal ore in deep deepslate drops heart crystals.
 * Using one raises max health by one permanent half-heart, up to a cap (10 half-hearts = 5 full hearts above base),
 * shown as a faint extra run on the health bar.
 * <p>
 * A found heart is LOST on death, but each grants an extra sure wake: a death burns one heart and is turned away
 * (left for dead, as Mortality's SURE_WAKES wakes do) instead of rolling. With no hearts left the cause roll runs
 * as normal. Reincarnation (final death) clears the hearts and the extra max health.
 */
public final class HeartCrystals {
    /** How many permanent half-hearts a player holds. Saved, kept through death (copyOnDeath), synced to its owner. */
    public static final AttachmentType<Integer> HEARTS = AttachmentRegistry.create(JaymosPowers.id("hearts"),
            builder -> builder.persistent(Codec.INT).copyOnDeath()
                    .syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

    /** Maximum permanent half-hearts above base (5 full hearts = 10 half-hearts). */
    public static final int MAX_HEARTS = 10;
    /** Each heart is a half-heart (1 health point). */
    public static final float HEART_VALUE = 1.0F;

    /** The attribute modifier ID for the extra max health from heart crystals. */
    private static final Identifier EXTRA_HEALTH_ID = JaymosPowers.id("heart_crystals_extra_health");

    private static final List<Item> ALL = new ArrayList<>();
    public static Item HEART_CRYSTAL;

    private HeartCrystals() {
    }

    static void register() {
        HEART_CRYSTAL = item("heart_crystal", p -> new HeartCrystalItem(p.stacksTo(16)));

        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("hearts"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.jaymospowers.hearts"))
                .icon(() -> new ItemStack(HEART_CRYSTAL))
                .build());
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> ALL.forEach(output::accept));
    }

    /** An item with its one line of lore (from lang {@code tooltip.jaymospowers.<id>}). */
    public static Item item(String id, java.util.function.Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
        Item.Properties properties = new Item.Properties().setId(key).component(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("tooltip.jaymospowers." + id).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties));
        ALL.add(item);
        return item;
    }

    /** Heart crystal item: consumed to grant a permanent half-heart. */
    public static final class HeartCrystalItem extends Item {
        HeartCrystalItem(Item.Properties properties) {
            super(properties.component(DataComponents.CONSUMABLE, Consumables.builder()
                    .consumeSeconds(1.6F)
                    .sound(SoundEvents.AMETHYST_BLOCK_RESONATE)
                    .build()));
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemStack left = super.finishUsingItem(stack, level, entity);
            if (!level.isClientSide() && entity instanceof ServerPlayer player) {
                grantHeart(player);
            }
            return left;
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity user) {
            return 32; // 1.6 seconds at 20 ticks/second
        }

        @Override
        public ItemUseAnimation getUseAnimation(ItemStack stack) {
            return ItemUseAnimation.EAT;
        }
    }

    /** Grant a permanent half-heart to the player if under cap. */
    public static boolean grantHeart(ServerPlayer player) {
        int current = player.getAttachedOrElse(HEARTS, 0);
        if (current >= MAX_HEARTS) {
            player.sendSystemMessage(Component.translatable("message.jaymospowers.hearts.full").withStyle(ChatFormatting.GRAY));
            return false;
        }
        player.setAttached(HEARTS, current + 1);
        applyExtraHealth(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.sendSystemMessage(Component.translatable("message.jaymospowers.hearts.granted", current + 2).withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /** Get the number of permanent half-hearts the player holds. */
    public static int getHearts(Player player) {
        return player.getAttachedOrElse(HEARTS, 0);
    }

    /** Get the extra max health from heart crystals (in health points, half-hearts). */
    public static float getExtraHealth(Player player) {
        return getHearts(player) * HEART_VALUE;
    }

    /** Apply the extra max health attribute modifier. */
    public static void applyExtraHealth(Player player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        AttributeModifier current = health.getModifier(EXTRA_HEALTH_ID);
        float extra = getExtraHealth(player);
        if (current != null) {
            health.removeModifier(EXTRA_HEALTH_ID);
        }
        if (extra > 0) {
            health.addTransientModifier(new AttributeModifier(EXTRA_HEALTH_ID, extra, AttributeModifier.Operation.ADD_VALUE));
        }
        // Ensure current health doesn't exceed new max
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Remove the extra max health (on final death/reincarnation). */
    public static void clearExtraHealth(Player player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        health.removeModifier(EXTRA_HEALTH_ID);
        player.removeAttached(HEARTS);
    }

    /**
     * Called when a player dies (not final death): if they have heart crystals, burn one to turn the death away
     * (left for dead, as a sure wake). Returns true if a heart was burned and the death should be turned away.
     */
    public static boolean tryBurnHeartOnDeath(ServerPlayer player) {
        int current = player.getAttachedOrElse(HEARTS, 0);
        if (current > 0) {
            // Burn one heart
            player.setAttached(HEARTS, current - 1);
            applyExtraHealth(player);
            // Increment Mortality's woken counter so this counts as one of the SURE_WAKES
            player.setAttached(Mortality.WOKEN, player.getAttachedOrElse(Mortality.WOKEN, 0) + 1);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HEART_EXTRA_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
            player.sendSystemMessage(Component.translatable("message.jaymospowers.hearts.burned", current - 1).withStyle(ChatFormatting.RED));
            return true;
        }
        return false;
    }

    /** Called on final death (reincarnation): clear all hearts and extra health. */
    public static void onFinalDeath(ServerPlayer player) {
        clearExtraHealth(player);
    }

    /** Re-apply extra health on respawn (after non-final death). */
    public static void onRespawn(ServerPlayer player) {
        applyExtraHealth(player);
    }
}