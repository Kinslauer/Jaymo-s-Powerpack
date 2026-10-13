package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Callings;
import com.jaymospowers.core.Powers;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * The Xeno-Band: a wrist band of alien metal found in a broken {@link FallenStarBlock fallen star}. Right-click it on and
 * it closes round your wrist and never opens again: a powerless survival player is called to Xenoform at once
 * ({@link Callings#call}); a creative player simply gets it; anyone with another power finds it won't close.
 */
public final class XenoBandItem extends Item {
	public static Item BAND;

	private XenoBandItem(Properties properties) {
		super(properties);
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("xeno_band"));
		BAND = Registry.register(BuiltInRegistries.ITEM, key, new XenoBandItem(new Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC).fireResistant()
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.xeno_band.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))))));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (XenoformPower.has(server)) {
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.band_already").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		if (Powers.of(server) != null) {
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.band_refuses").withStyle(ChatFormatting.RED));
			serverLevel.playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
			return InteractionResult.FAIL;
		}
		if (server.isCreative()) {
			Powers.set(server, XenoformPower.INSTANCE);
		} else {
			Callings.call(server, "xenoform", Integer.MAX_VALUE);
		}
		if (!XenoformPower.has(server)) {
			return InteractionResult.FAIL;
		}
		stack.shrink(1);
		server.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.band_closes").withStyle(ChatFormatting.GREEN));
		serverLevel.playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		serverLevel.playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
		serverLevel.sendParticles(ParticleTypes.END_ROD, server.getX(), server.getY() + 1.0, server.getZ(), 20, 0.3, 0.4, 0.3, 0.06);
		return InteractionResult.CONSUME;
	}
}
