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
	/** (0.18.125, Xeno-Band patch 8) The rival band: the only other band in the world, dark red, worn by the band hunter. */
	public static Item RIVAL;
	/** The aliens a fresh rival band holds, darkened. */
	public static final List<String> RIVAL_ALIENS = List.of("gyre", "cortex", "locust", "croaker");

	/** Whether this is the rival band. */
	public final boolean rival;

	private XenoBandItem(Properties properties, boolean rival) {
		super(properties);
		this.rival = rival;
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("xeno_band"));
		BAND = Registry.register(BuiltInRegistries.ITEM, key, new XenoBandItem(new Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC).fireResistant()
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.xeno_band.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)))), false));
		ResourceKey<Item> rivalKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("rival_band"));
		RIVAL = Registry.register(BuiltInRegistries.ITEM, rivalKey, new XenoBandItem(new Properties().setId(rivalKey).stacksTo(1).rarity(Rarity.EPIC).fireResistant()
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.rival_band.lore").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)))), true));
	}

	/** Whether {@code stack} is either band. */
	public static boolean isBand(ItemStack stack) {
		return stack.is(BAND) || stack.is(RIVAL);
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
		// (0.18.92) a band taken off someone still holds their aliens
		java.util.List<String> aliens = XenoBands.aliensOf(stack);
		if (aliens.isEmpty() && rival) {
			// (0.18.125) a rival band never worn holds its four darkened aliens
			aliens = RIVAL_ALIENS;
		}
		if (!aliens.isEmpty()) {
			server.setAttached(XenoformState.BAND, new XenoformState.Band(aliens, 0));
		}
		// (0.18.125) the band's reforge, modules and skills go with the band, not the wrist
		BandUpgrades.Upgrades upgrades = XenoBands.upgradesOf(stack);
		if (upgrades == null) {
			server.removeAttached(BandUpgrades.UPGRADES);
		} else {
			server.setAttached(BandUpgrades.UPGRADES, upgrades);
		}
		if (rival) {
			server.setAttached(XenoformState.RIVAL, true);
			server.addTag(XenoformState.RIVAL_TAG);
		} else {
			server.removeAttached(XenoformState.RIVAL);
			server.removeTag(XenoformState.RIVAL_TAG);
		}
		if (server.isCreative()) {
			Powers.set(server, XenoformPower.INSTANCE);
		} else {
			Callings.call(server, "xenoform", Integer.MAX_VALUE);
		}
		if (!XenoformPower.has(server)) {
			server.removeAttached(XenoformState.RIVAL);
			server.removeTag(XenoformState.RIVAL_TAG);
			return InteractionResult.FAIL;
		}
		stack.shrink(1);
		XenoBands.worn(server, true);
		server.sendSystemMessage(Component.translatable(rival ? "message.jaymospowers.xenoform.rival_closes" : "message.jaymospowers.xenoform.band_closes")
			.withStyle(rival ? ChatFormatting.DARK_RED : ChatFormatting.GREEN));
		serverLevel.playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		serverLevel.playSound(null, server.getX(), server.getY(), server.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
		serverLevel.sendParticles(ParticleTypes.END_ROD, server.getX(), server.getY() + 1.0, server.getZ(), 20, 0.3, 0.4, 0.3, 0.06);
		return InteractionResult.CONSUME;
	}
}
