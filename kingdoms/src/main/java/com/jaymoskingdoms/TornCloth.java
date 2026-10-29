package com.jaymoskingdoms;

import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.332) Torn clothes as evidence. When a werewolf player's change tears out of them, a scrap of what they wore lands at
 * their feet: a {@link #ITEM torn cloth}, dyed the colour of the rags their beast wears ({@link Lycans#clothes}) and
 * marked with whose it is. The cloth gives nothing away on its own; hold it and use it on a player and, if it is theirs,
 * you are told "This cloth matches X's" (the one place the name is given).
 */
public final class TornCloth {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The cloth can't be picked up for this long (ticks), so it lies where it fell. */
	static final int PICKUP_DELAY = 100;
	/** The tag key holding whose it is. */
	static final String OWNER = "jaymoskingdoms_cloth_of";

	public static Item ITEM;

	private TornCloth() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("torn_cloth"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16)));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer holder) || !(entity instanceof ServerPlayer target)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = holder.getItemInHand(hand);
			if (!stack.is(ITEM)) {
				return InteractionResult.PASS;
			}
			UUID owner = ownerOf(stack);
			if (owner != null && owner.equals(target.getUUID())) {
				holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.cloth.match", target.getName()).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
			} else {
				holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.cloth.none").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Whose cloth {@code stack} is, or null. */
	static @Nullable UUID ownerOf(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		try {
			return data.copyTag().getString(OWNER).map(UUID::fromString).orElse(null);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** A scrap of {@code player}'s clothes, dyed like their beast's rags. */
	static ItemStack scrap(ServerPlayer player) {
		ItemStack stack = new ItemStack(ITEM);
		CompoundTag tag = new CompoundTag();
		tag.putString(OWNER, player.getUUID().toString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(Lycans.clothes(player) & 0xFFFFFF));
		return stack;
	}

	/** The change tore their clothes: a scrap falls at their feet. */
	static void drop(ServerLevel level, ServerPlayer player) {
		ItemEntity cloth = new ItemEntity(level, player.getX(), player.getY() + 0.2, player.getZ(), scrap(player));
		cloth.setPickUpDelay(PICKUP_DELAY);
		cloth.setDeltaMovement(0.0, 0.1, 0.0);
		level.addFreshEntity(cloth);
	}
}
