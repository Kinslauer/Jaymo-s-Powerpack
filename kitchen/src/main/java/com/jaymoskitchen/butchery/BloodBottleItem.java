package com.jaymoskitchen.butchery;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.51) A bottle of blood from a {@link BloodGrateBlock}. Whoever has a use for it drinks it (a vampire's thirst, a
 * blood sorcerer's pool, through {@link Butchery#addOnDrink}); anyone else retches (Nausea, Hunger).
 * <p>
 * A bottle filled from someone (a vampire's grip on a throat, a wound held over it: Jaymo's Kingdoms) remembers whose blood
 * it is: their name, who they were, the taste of their people's blood and whether they were great ({@link #filled}). It is
 * named for them, and the hooks that hear a bottle drunk are given the stack, so what the blood tastes of is theirs to read
 * ({@link #donor}, {@link #taste}, {@link #great}). A bottle from a grate remembers no one.
 */
public class BloodBottleItem extends Item {
	/** What it does to someone with no use for it (ticks). */
	static final int NAUSEA_TICKS = 160, HUNGER_TICKS = 300;
	/** The keys of a bottle's memory, in its custom data. */
	static final String DONOR = "donor", DONOR_ID = "donor_id", TASTE = "taste", GREAT = "great";

	public BloodBottleItem(Item.Properties properties) {
		super(properties.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build()));
	}

	/**
	 * A bottle of {@code name}'s blood (whose is {@code id}), of {@code taste} (a key the drinker's mod reads; "" for none),
	 * {@code great} for a king's or lord's. Named for them, with the taste under its name.
	 */
	public static ItemStack filled(String name, @Nullable UUID id, String taste, boolean great) {
		ItemStack stack = new ItemStack(Butchery.BLOOD_BOTTLE);
		CompoundTag tag = new CompoundTag();
		tag.putString(DONOR, name);
		if (id != null) {
			tag.putString(DONOR_ID, id.toString());
		}
		tag.putString(TASTE, taste);
		tag.putBoolean(GREAT, great);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskitchen.blood_bottle.of", name));
		if (!taste.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymoskitchen.blood_bottle.taste." + taste).withStyle(ChatFormatting.DARK_RED))));
		}
		return stack;
	}

	private static @Nullable CompoundTag tag(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !stack.is(Butchery.BLOOD_BOTTLE)) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		return tag.contains(DONOR) ? tag : null;
	}

	/** Whose blood {@code stack} holds (their name), or "" for a bottle from a grate. */
	public static String donor(ItemStack stack) {
		CompoundTag tag = tag(stack);
		return tag == null ? "" : tag.getStringOr(DONOR, "");
	}

	/** Who the blood was (their UUID), or null. */
	public static @Nullable UUID donorId(ItemStack stack) {
		CompoundTag tag = tag(stack);
		String id = tag == null ? "" : tag.getStringOr(DONOR_ID, "");
		try {
			return id.isEmpty() ? null : UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The taste key {@code stack}'s blood was filled with, or "". */
	public static String taste(ItemStack stack) {
		CompoundTag tag = tag(stack);
		return tag == null ? "" : tag.getStringOr(TASTE, "");
	}

	/** Whether the blood in {@code stack} was a king's or lord's. */
	public static boolean great(ItemStack stack) {
		CompoundTag tag = tag(stack);
		return tag != null && tag.getBooleanOr(GREAT, false);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player && !Butchery.drunkBySomeone(player, stack)) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_TICKS, 0));
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.blood_bottle.retch"));
		}
		return super.finishUsingItem(stack, level, entity);
	}
}
