package com.jaymoskitchen.cooking;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;

public enum Quality {
	BURNT(ChatFormatting.DARK_GRAY),
	GOOD(ChatFormatting.WHITE),
	PERFECT(ChatFormatting.GOLD),
	// grill-only outcomes
	UNDERCOOKED(ChatFormatting.LIGHT_PURPLE),
	CHARRED(ChatFormatting.DARK_RED);

	/** Mistakes that ruin a dish outright. */
	public static final int MAX_MISTAKES = 3;

	private final ChatFormatting color;

	Quality(ChatFormatting color) {
		this.color = color;
	}

	public ChatFormatting color() {
		return color;
	}

	/** The quality stamped on a cooked dish (from its name), or null for anything else. */
	public static @org.jspecify.annotations.Nullable Quality of(ItemStack stack) {
		Component name = stack.get(DataComponents.ITEM_NAME);
		if (name != null && name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents) {
			for (Quality quality : values()) {
				if (quality.translationKey().equals(contents.getKey())) {
					return quality;
				}
			}
		}
		return null;
	}

	public String translationKey() {
		return "quality.jaymoskitchen." + name().toLowerCase();
	}

	public static Quality judge(int mistakes, int elapsedTicks, int parTicks, boolean timedOut) {
		if (timedOut || mistakes >= MAX_MISTAKES) {
			return BURNT;
		}
		return mistakes == 0 && elapsedTicks <= parTicks ? PERFECT : GOOD;
	}

	/** Oven result: one bake, judged like a grill side, but it only chars outright once well past burnt. */
	public static Quality judgeOven(float done) {
		if (done > Grill.OVEN_CHARRED) {
			return CHARRED;
		}
		if (done > Grill.BURNT) {
			return BURNT;
		}
		if (done < Grill.COOKED) {
			return UNDERCOOKED;
		}
		return done >= Grill.GOLDEN ? PERFECT : GOOD;
	}

	/** Grill result from how done each side is (1.0 = just cooked through, above that it's burning). */
	public static Quality judgeGrill(float sideA, float sideB) {
		boolean burntA = sideA > Grill.BURNT;
		boolean burntB = sideB > Grill.BURNT;
		if (burntA && burntB) {
			return CHARRED;
		}
		if (burntA || burntB) {
			return BURNT;
		}
		if (sideA < Grill.COOKED || sideB < Grill.COOKED) {
			return UNDERCOOKED;
		}
		return sideA >= Grill.GOLDEN && sideB >= Grill.GOLDEN ? PERFECT : GOOD;
	}

	/** Stamps food stats, name and tooltip for this quality onto a freshly made dish. */
	public void apply(ItemStack stack, Dish dish) {
		if (dish.feast()) {
			// (0.5.28) a feast isn't eaten as it is: only its name says how it went (its servings carry it, see FeastItem)
			name(stack, switch (this) {
				case BURNT -> List.of(Component.translatable("tooltip.jaymoskitchen.burnt").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				case CHARRED -> List.of(Component.translatable("tooltip.jaymoskitchen.charred").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
				case UNDERCOOKED -> List.of(Component.translatable("tooltip.jaymoskitchen.undercooked").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
				case PERFECT -> List.of(Component.translatable("tooltip.jaymoskitchen.perfect").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
				default -> List.of();
			});
			return;
		}
		apply(stack, dish.nutrition(), dish.saturation(), dish.perfectBuffs());
	}

	/** As {@link #apply(ItemStack, Dish)}, for food that isn't a dish of its own (a feast's servings). */
	public void apply(ItemStack stack, int baseNutrition, float baseSaturation, List<Dish.Buff> perfectBuffs) {
		int nutrition;
		float saturation;
		Consumable.Builder consumable = Consumables.defaultFood();
		List<Component> lore = new ArrayList<>();
		switch (this) {
			case BURNT -> {
				nutrition = 3;
				saturation = 0.2F;
				consumable.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HUNGER, 30 * 20, 0)));
				lore.add(Component.translatable("tooltip.jaymoskitchen.burnt").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
			case CHARRED -> {
				nutrition = 1;
				saturation = 0.1F;
				consumable.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
					new MobEffectInstance(MobEffects.HUNGER, 60 * 20, 1),
					new MobEffectInstance(MobEffects.NAUSEA, 10 * 20, 0))));
				lore.add(Component.translatable("tooltip.jaymoskitchen.charred").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			}
			case UNDERCOOKED -> {
				nutrition = Math.max(2, baseNutrition - 4);
				saturation = 0.3F;
				// its sickness chance is RawFood's (half raw meat's)
				lore.add(Component.translatable("tooltip.jaymoskitchen.undercooked").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
			}
			case PERFECT -> {
				nutrition = baseNutrition + 3;
				saturation = baseSaturation + 0.2F;
				List<MobEffectInstance> effects = perfectBuffs.stream().map(Dish.Buff::instance).toList();
				consumable.onConsume(new ApplyStatusEffectsConsumeEffect(effects));
				lore.add(Component.translatable("tooltip.jaymoskitchen.perfect").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
			}
			default -> {
				nutrition = baseNutrition;
				saturation = baseSaturation;
			}
		}
		stack.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build());
		stack.set(DataComponents.CONSUMABLE, consumable.build());
		name(stack, lore);
	}

	private void name(ItemStack stack, List<Component> lore) {
		Component baseName = Component.translatable(stack.getItem().getDescriptionId());
		stack.set(DataComponents.ITEM_NAME, Component.translatable(translationKey(), baseName).withStyle(color));
		if (!lore.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(lore));
		}
	}
}
