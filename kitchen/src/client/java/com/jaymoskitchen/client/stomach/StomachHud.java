package com.jaymoskitchen.client.stomach;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.client.hud.HudZones;
import com.jaymoskitchen.stomach.FoodGroups;
import com.jaymoskitchen.stomach.Stomach;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Hearty meals ({@link Stomach}): what's in your stomach beside the hotbar, and what a food is worth in its tooltip. */
public final class StomachHud {
	/** Gap between the hotbar's right edge and the first meal. */
	static final int GAP = 8;
	static final int SLOT = 18;

	private StomachHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosKitchen.id("stomach"), StomachHud::render);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Stomach.Meal meal = Stomach.mealOf(stack, Stomach.isDrink(stack));
			if (meal == null) {
				return;
			}
			lines.add(Component.translatable("tooltip.jaymoskitchen.stomach.meal",
				String.format("%.1f", meal.health() / 2.0F), Math.max(1, meal.total() / 1200), String.format("%.2f", meal.regen()))
				.withStyle(meal.drink() ? ChatFormatting.AQUA : ChatFormatting.GOLD));
			java.util.Set<FoodGroups.Group> groups = FoodGroups.of(stack.getItem());
			if (!groups.isEmpty()) {
				net.minecraft.network.chat.MutableComponent names = Component.empty();
				for (FoodGroups.Group group : groups) {
					if (!names.getSiblings().isEmpty()) {
						names.append(", ");
					}
					names.append(Component.translatable("food_group.jaymoskitchen." + group.name().toLowerCase(java.util.Locale.ROOT)));
				}
				lines.add(Component.translatable("tooltip.jaymoskitchen.food_groups", names).withStyle(ChatFormatting.GRAY));
			}
		});
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null || player.isSpectator() || player.getAbilities().instabuild) {
			return;
		}
		List<Stomach.Meal> meals = Stomach.meals(player);
		int slots = Stomach.slots(player);
		// (HUD rework) left dock, row 10 (food slots left to right, then the drink slot after a 4 px gap); 17x19 each
		int[] at = HudZones.place(HudZones.DOCK_LEFT, "jaymoskitchen:stomach", 10, slots * SLOT + 4 + 17 + 3 + 3, 19);
		if (at == null) {
			return;
		}
		int x = at[0];
		int y = at[1];
		int food = 0;
		for (Stomach.Meal meal : meals) {
			if (!meal.drink()) {
				slot(graphics, minecraft, meal, x + food++ * SLOT, y, 0xFFE0A030);
			}
		}
		for (int i = food; i < slots; i++) {
			empty(graphics, x + i * SLOT, y);
		}
		int drinkX = x + slots * SLOT + 4;
		Stomach.Meal drink = meals.stream().filter(Stomach.Meal::drink).findFirst().orElse(null);
		if (drink != null) {
			slot(graphics, minecraft, drink, drinkX, y, 0xFF40A0E0);
		} else {
			empty(graphics, drinkX, y);
		}
		// (Hardrock picks) the food groups in the stomach, one lit pip each: grain, fruit, vegetables, protein, dairy
		java.util.Set<FoodGroups.Group> eaten = FoodGroups.eaten(meals);
		int pipX = drinkX + 17 + 3;
		for (FoodGroups.Group group : FoodGroups.Group.values()) {
			int pipY = y + group.ordinal() * 4;
			graphics.fill(pipX, pipY, pipX + 3, pipY + 3, eaten.contains(group) ? GROUP_COLOURS[group.ordinal()] : 0x40FFFFFF);
		}
	}

	private static final int[] GROUP_COLOURS = {0xFFE8C060, 0xFFE04848, 0xFF60C050, 0xFFB06040, 0xFFF4F0E8};

	private static void slot(GuiGraphicsExtractor graphics, Minecraft minecraft, Stomach.Meal meal, int x, int y, int colour) {
		graphics.fill(x, y, x + 17, y + 19, 0x60000000);
		ItemStack icon = new ItemStack(meal.item());
		graphics.item(icon, x, y);
		int minutes = (meal.left() + 1199) / 1200;
		graphics.itemDecorations(minecraft.font, icon, x, y, minutes + "m");
		graphics.fill(x, y + 17, x + 17, y + 19, 0xFF000000);
		graphics.fill(x, y + 17, x + Math.max(1, Math.round(17 * meal.fraction())), y + 19, meal.fraction() <= Stomach.EAT_AGAIN ? 0xFFE04040 : colour);
	}

	private static void empty(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y, x + 17, y + 19, 0x30000000);
		graphics.fill(x + 7, y + 8, x + 10, y + 11, 0x40FFFFFF);
	}
}
