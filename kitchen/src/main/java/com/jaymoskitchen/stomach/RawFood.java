package com.jaymoskitchen.stomach;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.cooking.Quality;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;

/**
 * Food poisoning (after Better With Mods): raw meat and fish ({@link #RAW_MEATS}) can make you sick, an Undercooked
 * Kitchen dish less often. Sickness is plain Hunger: it drains the hunger bar but never stops you eating, so the
 * {@link Stomach} keeps working, and anything that clears Hunger (milk, Kingdoms' Iron Stomach) clears it.
 * Raw chicken's own vanilla Hunger chance is taken off so the two don't stack.
 */
public final class RawFood {
	/** Raw meat and fish (data/jaymoskitchen/tags/item/raw_meats.json). */
	public static final TagKey<Item> RAW_MEATS = TagKey.create(Registries.ITEM, JaymosKitchen.id("raw_meats"));
	/** Chance that raw meat makes you sick, and an Undercooked dish (half as likely). */
	public static final float RAW_SICK_CHANCE = 0.3F;
	public static final float UNDERCOOKED_SICK_CHANCE = RAW_SICK_CHANCE / 2.0F;
	/** How long the sickness lasts, and how bad (1 = Hunger II). */
	static final int SICK_TICKS = 30 * 20;
	static final int SICK_AMPLIFIER = 1;

	private RawFood() {
	}

	public static void register() {
		// raw chicken: plain food, our sickness replaces its Hunger chance
		DefaultItemComponentEvents.MODIFY.register(context ->
			context.modify(Items.CHICKEN, builder -> builder.set(DataComponents.CONSUMABLE, Consumables.DEFAULT_FOOD)));
	}

	/** Server: {@code player} has just eaten {@code stack} (still whole). */
	public static void eaten(ServerPlayer player, ItemStack stack) {
		float chance = stack.is(RAW_MEATS) ? RAW_SICK_CHANCE : Quality.of(stack) == Quality.UNDERCOOKED ? UNDERCOOKED_SICK_CHANCE : 0.0F;
		if (chance > 0.0F && player.getRandom().nextFloat() < chance) {
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, SICK_TICKS, SICK_AMPLIFIER));
		}
	}
}
