package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * (Awakening pick "Speedster": "its energy bar food comes with it") The Energy Bar: a food made for speedsters. Quick to
 * eat ({@link #EAT_SECONDS}) and filling for anyone; a speedster ({@link SpeedPrism#speedster}) also gets back some of the
 * hunger running costs ({@link ChronoGears#EXHAUSTION}), as {@link #RUN_REFUND} of saturation on top.
 */
public final class EnergyBarItem extends Item {
	// ---- tuning ----
	/** What it feeds (food points, and saturation as a share of them), how long it takes to eat (s), how many stack. */
	static final int NUTRITION = 6, STACK = 16;
	static final float SATURATION = 0.8F, EAT_SECONDS = 0.8F;
	/** For a speedster: the saturation it gives back on top (a point pays for 4 exhaustion: 3 points is 17 s of gear 5, over a minute of gear 3). */
	static final float RUN_REFUND = 3.0F;

	public static Item ENERGY_BAR;

	public EnergyBarItem(Item.Properties properties) {
		super(properties);
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("energy_bar"));
		ENERGY_BAR = Registry.register(BuiltInRegistries.ITEM, key, new EnergyBarItem(new Item.Properties().setId(key).stacksTo(STACK)
			.food(new FoodProperties.Builder().nutrition(NUTRITION).saturationModifier(SATURATION).build(),
				Consumables.defaultFood().consumeSeconds(EAT_SECONDS).build())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(ENERGY_BAR));
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack left = super.finishUsingItem(stack, level, entity);
		if (entity instanceof ServerPlayer player && SpeedPrism.speedster(player)) {
			FoodData food = player.getFoodData();
			food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + RUN_REFUND));
		}
		return left;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.energy_bar").withStyle(ChatFormatting.GRAY));
	}
}
