package com.jaymospowers.client.hud;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.client.mixin.ContainerScreenAccessor;
import com.jaymospowers.core.PackWeight;
import java.util.Locale;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * What things weigh ({@link PackWeight}), where you'd look for it: a line on every item's tooltip (one of it, and the
 * whole stack with what's inside it), and over the inventory what you carry against what you bear freely, gold as it
 * nears the limit and red past it.
 */
public final class PackWeightClient {
	/** The readout turns gold at this share of what's borne freely. */
	static final float NEAR = 0.9F;
	/** The survival inventory's width (the readout sits over its right edge). */
	static final int INVENTORY_W = 176;

	private PackWeightClient() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.isEmpty()) {
				return;
			}
			float one = PackWeight.kilograms(stack);
			float all = PackWeight.stackKilograms(stack);
			Component line = Math.abs(all - one) < 0.005F
				? Component.translatable("tooltip.jaymospowers.pack_weight.one", kg(one))
				: Component.translatable("tooltip.jaymospowers.pack_weight.all", kg(one), kg(all));
			lines.add(line.copy().withStyle(ChatFormatting.DARK_GRAY));
		});
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
					LocalPlayer player = Minecraft.getInstance().player;
					PackWeight.Load load = player == null ? null : PackWeight.load(player);
					if (load == null) {
						return;
					}
					ContainerScreenAccessor at = (ContainerScreenAccessor)s;
					float share = load.bearable() <= 0 ? 0.0F : load.carried() / (float)load.bearable();
					int color = share > 1.0F ? UiStyle.BAD : share >= NEAR ? UiStyle.GOLD : UiStyle.TEXT;
					Component text = Component.translatable("screen.jaymospowers.pack_weight", kg(load.carriedKg()), kg(load.bearableKg()));
					var font = Minecraft.getInstance().font;
					int right = at.jaymospowers$leftPos() + INVENTORY_W;
					graphics.text(font, text.getString(), right - font.width(text.getString()), at.jaymospowers$topPos() - 10, color);
				});
			}
		});
	}

	/** A weight as people say it: grams under a kilogram, tenths up to ten, whole kilograms past. */
	static String kg(float kg) {
		if (kg < 1.0F) {
			return Math.round(kg * 1000.0F) + " g";
		}
		return (kg < 10.0F ? String.format(Locale.ROOT, "%.1f", kg) : Integer.toString(Math.round(kg))) + " kg";
	}
}
