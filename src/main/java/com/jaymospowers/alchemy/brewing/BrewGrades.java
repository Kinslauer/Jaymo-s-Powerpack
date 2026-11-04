package com.jaymospowers.alchemy.brewing;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.13) Grades on potion stacks. The potion itself is never rebuilt: the grade scales vanilla's
 * {@code potion_duration_scale} (so splash and lingering keep it) and the stack's own {@code consumable} (drink time).
 */
public final class BrewGrades {
	private BrewGrades() {
	}

	/** The grade on this stack, or null for an ungraded (vanilla) potion. */
	public static @Nullable BrewGrade of(ItemStack stack) {
		String id = stack.get(Brewing.BREW_GRADE);
		return id == null ? null : BrewGrade.byId(id);
	}

	/** The grade, with an ungraded potion counting as Plain. */
	public static BrewGrade orPlain(ItemStack stack) {
		BrewGrade grade = of(stack);
		return grade == null ? BrewGrade.PLAIN : grade;
	}

	/** Sets the grade, always from the prototype's numbers, so setting it twice never compounds. */
	public static void set(ItemStack stack, BrewGrade grade) {
		stack.set(Brewing.BREW_GRADE, grade.id);
		float base = stack.getPrototype().getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
		stack.set(DataComponents.POTION_DURATION_SCALE, base * grade.duration);
		Consumable drink = stack.getPrototype().get(DataComponents.CONSUMABLE);
		if (drink != null) {
			stack.set(DataComponents.CONSUMABLE, new Consumable(drink.consumeSeconds() * grade.drink, drink.animation(), drink.sound(),
				drink.hasConsumeParticles(), drink.onConsumeEffects()));
		}
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, grade == BrewGrade.MASTERWORK ? Boolean.TRUE : null);
	}

	public static boolean distilled(ItemStack stack) {
		return stack.has(Brewing.BREW_DISTILLED);
	}

	public static void markDistilled(ItemStack stack) {
		stack.set(Brewing.BREW_DISTILLED, Unit.INSTANCE);
	}

	/** A bottled potion (drinking, splash or lingering) that has effects. */
	public static boolean isBrew(ItemStack stack) {
		return (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))
			&& stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).hasEffects();
	}

	/** How finely this ingredient was ground (1 Coarse to 3 Dust), 0 if not at all. */
	public static int ground(ItemStack stack) {
		return stack.getOrDefault(Brewing.GROUND, 0);
	}
}
