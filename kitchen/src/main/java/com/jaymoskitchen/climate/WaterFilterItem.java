package com.jaymoskitchen.climate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

/**
 * (Andy: "you should also have to purify the water inside") The other way to make water safe besides boiling it
 * ({@link Climate#boil}): a cloth, charcoal and sand filter. Hold it in one hand and unpurified water in the other (a
 * water bottle or a {@link WaterskinItem} of river water) and use the filter: the water runs through it and comes out
 * clean. Each bottle, or each sip in a waterskin, wears the filter by one; worn through, it falls apart. Filtered water is
 * clean but not hot, so it doesn't warm you as boiled waterskin water does.
 */
public final class WaterFilterItem extends Item {
	/** Bottles (or waterskin sips) one filter cleans before it's spent. */
	public static final int USES = 24;
	/** Ticks before the filter can be used again: the water takes a moment to run through. */
	static final int COOLDOWN = 20;

	public WaterFilterItem(Item.Properties properties) {
		super(properties);
	}

	/** Water a filter can clean: a bottle of water, or a waterskin with river water in it. */
	static boolean filters(ItemStack stack) {
		if (stack.is(Items.POTION)) {
			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
			return contents != null && contents.is(Potions.WATER);
		}
		if (stack.getItem() instanceof WaterskinItem) {
			WaterskinItem.Water water = WaterskinItem.water(stack);
			return water.sips() > 0 && !water.clean();
		}
		return false;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack other = player.getItemInHand(otherHand);
		if (!filters(other)) {
			if (player instanceof ServerPlayer server) {
				server.sendOverlayMessage(Component.translatable("message.jaymoskitchen.water_filter.nothing").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack filter = player.getItemInHand(hand);
		int wear;
		if (other.getItem() instanceof WaterskinItem) {
			WaterskinItem.Water water = WaterskinItem.water(other);
			wear = water.sips();
			other.set(Climate.WATERSKIN_WATER, new WaterskinItem.Water(water.sips(), true, water.temp(), water.until()));
		} else {
			wear = 1;
			// water bottles don't stack, so the bottle in that hand simply becomes clean water
			player.setItemInHand(otherHand, new ItemStack(Climate.CLEAN_WATER));
		}
		filter.hurtAndBreak(wear, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		player.getCooldowns().addCooldown(filter, COOLDOWN);
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.7F, 0.8F);
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.7F, 1.1F);
		server.sendParticles(ParticleTypes.DRIPPING_WATER, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.2, 0.1, 0.2, 0.0);
		return InteractionResult.SUCCESS;
	}
}
